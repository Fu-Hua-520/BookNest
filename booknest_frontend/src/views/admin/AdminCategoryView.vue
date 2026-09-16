<script setup>
/**
 * 书吧管理（原「分类管理」）：书吧的新增 / 编辑 / 删除 / 启停 + 创建申请审批
 *
 * 两块内容：
 *   bars         —— 已有的吧（数据上就是 category 表）
 *   applications —— 用户提交的「申请创建书吧」，待管理员通过后才出现在前台
 * 审批是书吧化的闭环：没有这个入口，用户申请完就石沉大海。
 *
 * ⚠️ 书吧自 2026-09-13 起**取消分级**：后台拿到的就是一层平级列表，
 * 界面上不再有「上级书吧 / 加子吧」，新增或编辑时 parentId 一律传 null。
 */
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as adminApi from '@/api/admin'
import { formatDateTime } from '@/utils/format'

const tab = ref('bars')

const loading = ref(false)
const treeData = ref([])

/* ---------------- 书吧创建申请 ---------------- */
const applications = ref([])
const appLoading = ref(false)
const appStatus = ref(0) // 0-待审核 1-已通过 2-已驳回
const appAuditing = ref('')

const dialogVisible = ref(false)
const dialogMode = ref('create')
const submitting = ref(false)
const formRef = ref(null)

const emptyForm = () => ({
  id: '',
  name: '',
  icon: '',
  sortOrder: 0,
  description: '',
  status: 1
})

const form = reactive(emptyForm())

const rules = {
  name: [
    { required: true, message: '请输入书吧名称', trigger: 'blur' },
    { max: 50, message: '名称不能超过 50 个字符', trigger: 'blur' }
  ],
  description: [{ max: 200, message: '描述不能超过 200 个字符', trigger: 'blur' }]
}

const dialogTitle = computed(() => (dialogMode.value === 'create' ? '新增书吧' : '编辑书吧'))

/** 「通过申请」弹窗：可指定吧主（默认申请人） */
const approveDialog = reactive({
  visible: false,
  barId: '',
  barName: '',
  ownerId: '',
  ownerOptions: [],
  ownerLoading: false
})

function auditText(status) {
  if (status === 0) return '待审核'
  if (status === 2) return '已驳回'
  return '已通过'
}

function auditType(status) {
  if (status === 0) return 'warning'
  if (status === 2) return 'danger'
  return 'success'
}

async function load() {
  loading.value = true
  try {
    treeData.value = (await adminApi.listCategoryTree()) || []
  } catch {
    treeData.value = []
  } finally {
    loading.value = false
  }
}

async function loadApplications() {
  appLoading.value = true
  try {
    applications.value = (await adminApi.listBarApplications(appStatus.value)) || []
  } catch {
    applications.value = []
  } finally {
    appLoading.value = false
  }
}

async function approveApplication(row) {
  // 打开「通过申请」弹窗：默认吧主 = 申请人，管理员可以在通过前改派给别人
  approveDialog.barId = row.id
  approveDialog.barName = row.name
  approveDialog.ownerId = row.ownerId || ''
  approveDialog.ownerOptions = row.ownerId
    ? [{ id: row.ownerId, username: row.ownerName || row.ownerId }]
    : []
  approveDialog.visible = true
}

/** 远程搜用户：管理员指定吧主 / 任命吧务时用（复用 /admin/user/list，只取第一页前 10 条） */
async function searchUsers(keyword) {
  const kw = (keyword || '').trim()
  if (!kw) {
    return []
  }
  const page = await adminApi.listUsers({ keyword: kw, page: 1, pageSize: 10 })
  const list = page?.list || page?.records || page || []
  return list.map((item) => ({ id: item.id, username: item.username }))
}

async function searchOwners(keyword) {
  approveDialog.ownerLoading = true
  try {
    approveDialog.ownerOptions = await searchUsers(keyword)
  } catch {
    approveDialog.ownerOptions = []
  } finally {
    approveDialog.ownerLoading = false
  }
}

/* ---------------- 吧务管理（吧主 / 管理员 / 等级称号） ----------------
 * 用户端 /bar 那套接口会校验「你是不是吧主」，管理员不是吧主 → 调不动。
 * 所以后台走 /admin/bar 这组专用接口，跳过吧主校验。
 * 三块内容放同一个弹窗：换吧主后管理员列表要跟着变，分三个弹窗容易出现中间态。 */
const MAX_LEVEL = 10

const manage = reactive({
  visible: false,
  loading: false,
  saving: false,
  barId: '',
  barName: '',
  memberCount: 0,
  ownerId: '',
  ownerOptions: [],
  ownerLoading: false,
  moderators: [],
  modUserId: '',
  modOptions: [],
  modLoading: false,
  titles: []
})

/** 后端只返回设过称号的等级，界面上要固定 10 行方便逐条填 */
function buildTitles(list) {
  const map = new Map()
  for (const item of list || []) {
    if (item && item.level) map.set(item.level, item.title || '')
  }
  return Array.from({ length: MAX_LEVEL }, (_, i) => ({
    level: i + 1,
    title: map.get(i + 1) || ''
  }))
}

async function openManage(row) {
  manage.barId = row.id
  manage.barName = row.name
  manage.ownerId = ''
  manage.ownerOptions = []
  manage.moderators = []
  manage.modUserId = ''
  manage.modOptions = []
  manage.titles = buildTitles([])
  manage.memberCount = 0
  manage.visible = true
  await loadManage()
}

async function loadManage() {
  manage.loading = true
  try {
    const info = await adminApi.getBarManage(manage.barId)
    manage.barName = info?.barName || manage.barName
    manage.memberCount = info?.memberCount || 0
    manage.ownerId = info?.ownerId || ''
    manage.ownerOptions =
      info?.ownerId && info?.ownerName
        ? [{ id: info.ownerId, username: info.ownerName }]
        : []
    manage.moderators = info?.moderators || []
    manage.titles = buildTitles(info?.titles)
  } catch {
    /* 拦截器已提示 */
  } finally {
    manage.loading = false
  }
}

async function searchOwnerCandidates(keyword) {
  manage.ownerLoading = true
  try {
    manage.ownerOptions = await searchUsers(keyword)
  } catch {
    manage.ownerOptions = []
  } finally {
    manage.ownerLoading = false
  }
}

async function searchModCandidates(keyword) {
  manage.modLoading = true
  try {
    manage.modOptions = await searchUsers(keyword)
  } catch {
    manage.modOptions = []
  } finally {
    manage.modLoading = false
  }
}

async function saveOwner() {
  manage.saving = true
  try {
    await adminApi.setBarOwner(manage.barId, manage.ownerId || null)
    ElMessage.success(manage.ownerId ? '吧主已设置' : '已收回吧主（该吧变为官方吧）')
    await loadManage()
    load()
  } catch {
    /* 拦截器已提示 */
  } finally {
    manage.saving = false
  }
}

async function addModerator() {
  if (!manage.modUserId) {
    ElMessage.warning('请先搜索并选择要任命的用户')
    return
  }
  manage.saving = true
  try {
    manage.moderators = (await adminApi.addBarModerator(manage.barId, manage.modUserId)) || []
    manage.modUserId = ''
    manage.modOptions = []
    ElMessage.success('已任命管理员')
  } catch {
    /* 拦截器已提示 */
  } finally {
    manage.saving = false
  }
}

async function removeModerator(userId) {
  manage.saving = true
  try {
    manage.moderators = (await adminApi.removeBarModerator(manage.barId, userId)) || []
    ElMessage.success('已撤销管理员')
  } catch {
    /* 拦截器已提示 */
  } finally {
    manage.saving = false
  }
}

async function saveTitles() {
  manage.saving = true
  try {
    const payload = manage.titles
      .filter((item) => item.title && item.title.trim())
      .map((item) => ({ level: item.level, title: item.title.trim() }))
    const saved = await adminApi.setBarTitles(manage.barId, payload)
    manage.titles = buildTitles(saved)
    ElMessage.success('等级称号已保存')
  } catch {
    /* 拦截器已提示 */
  } finally {
    manage.saving = false
  }
}

async function confirmApprove() {
  appAuditing.value = String(approveDialog.barId)
  try {
    await adminApi.auditBar(
      approveDialog.barId,
      true,
      undefined,
      approveDialog.ownerId || undefined
    )
    ElMessage.success('已通过')
    approveDialog.visible = false
    loadApplications()
    load()
  } catch {
    /* 拦截器已提示 */
  } finally {
    appAuditing.value = ''
  }
}

async function rejectApplication(row) {
  let reason = ''
  try {
    const result = await ElMessageBox.prompt('请填写驳回原因，申请人会看到这条说明', '驳回申请', {
      confirmButtonText: '驳回',
      cancelButtonText: '取消',
      inputPlaceholder: '例如：与已有书吧主题重复',
      inputValidator: (value) => (value && value.trim() ? true : '驳回原因不能为空')
    })
    reason = (result.value || '').trim()
  } catch {
    return
  }
  appAuditing.value = String(row.id)
  try {
    await adminApi.auditBar(row.id, false, reason)
    ElMessage.success('已驳回')
    loadApplications()
  } catch {
    /* 拦截器已提示 */
  } finally {
    appAuditing.value = ''
  }
}

function openCreateRoot() {
  dialogMode.value = 'create'
  Object.assign(form, emptyForm())
  dialogVisible.value = true
}

function openEdit(row) {
  dialogMode.value = 'edit'
  Object.assign(form, emptyForm(), {
    id: row.id,
    name: row.name || '',
    icon: row.icon || '',
    sortOrder: row.sortOrder ?? 0,
    description: row.description || '',
    status: row.status ?? 1
  })
  dialogVisible.value = true
}

async function onSubmit() {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  // 后端 update 为全量覆盖，故每次提交都带上完整字段
  const payload = {
    name: form.name.trim(),
    icon: form.icon.trim() || null,
    sortOrder: Number(form.sortOrder) || 0,
    description: form.description.trim() || null,
    status: form.status
  }

  submitting.value = true
  try {
    if (dialogMode.value === 'create') {
      await adminApi.createCategory(payload)
      ElMessage.success('书吧已创建')
    } else {
      await adminApi.updateCategory(form.id, payload)
      ElMessage.success('书吧已更新')
    }
    dialogVisible.value = false
    load()
  } catch {
    /* 拦截器已提示后端返回的具体原因 */
  } finally {
    submitting.value = false
  }
}

/** 启用 / 禁用切换（同样需要回传完整字段） */
async function toggleStatus(row) {
  const nextStatus = row.status === 0 ? 1 : 0
  const actionText = nextStatus === 1 ? '启用' : '禁用'
  try {
    await ElMessageBox.confirm(`确定${actionText}书吧「${row.name}」吗？`, `${actionText}确认`, {
      type: 'warning',
      confirmButtonText: actionText,
      cancelButtonText: '取消'
    })
  } catch {
    return
  }
  try {
    await adminApi.updateCategory(row.id, {
      name: row.name,
      icon: row.icon || null,
      sortOrder: row.sortOrder ?? 0,
      description: row.description || null,
      status: nextStatus
    })
    ElMessage.success(`已${actionText}`)
    load()
  } catch {
    /* 拦截器已提示 */
  }
}

async function onDelete(row) {
  try {
    await ElMessageBox.confirm(
      `确定删除书吧「${row.name}」吗？该操作不可恢复。`,
      '删除确认',
      {
        type: 'warning',
        confirmButtonText: '删除',
        cancelButtonText: '取消'
      }
    )
  } catch {
    return
  }
  try {
    await adminApi.deleteCategory(row.id)
    ElMessage.success('已删除')
    load()
  } catch {
    /* 后端返回「有子吧 / 有帖子引用」等原因，拦截器已提示 */
  }
}

onMounted(() => {
  load()
  loadApplications()
})
</script>

<template>
  <div class="admin-page">
    <!-- 两块内容用分段控件切换，避免一屏塞两张表 -->
    <div class="bn-card tab-bar">
      <el-radio-group v-model="tab">
        <el-radio-button value="bars">书吧列表</el-radio-button>
        <el-radio-button value="applications">
          创建申请{{ appStatus === 0 && applications.length ? ` (${applications.length})` : '' }}
        </el-radio-button>
      </el-radio-group>
    </div>

    <!-- ---------------- 书吧树 ---------------- -->
    <template v-if="tab === 'bars'">
      <div class="bn-card filter-bar">
        <p class="tip">书吧为平级结构，前台只展示「启用且已通过审批」的书吧。</p>
        <el-button @click="load">
          <el-icon><Refresh /></el-icon>
        </el-button>
        <el-button type="primary" plain class="add-btn" @click="openCreateRoot">
          <el-icon style="margin-right: 4px"><Plus /></el-icon>新增书吧
        </el-button>
      </div>

      <el-table v-loading="loading" :data="treeData" class="bn-card table" row-key="id" border>
        <el-table-column label="图标" width="72" align="center">
          <template #default="{ row }">
            <img v-if="row.icon" :src="row.icon" :alt="row.name" class="cell-icon" />
            <span v-else class="cell-icon cell-icon-text">{{ (row.name || '?').slice(0, 1) }}</span>
          </template>
        </el-table-column>

        <el-table-column label="书吧名称" min-width="200">
          <template #default="{ row }">
            <span class="cat-name">{{ row.name }}</span>
          </template>
        </el-table-column>

        <el-table-column label="吧主" width="150">
          <template #default="{ row }">
            <span v-if="row.ownerName" class="bn-text-sub">{{ row.ownerName }}</span>
            <span v-else class="bn-text-muted">官方吧</span>
          </template>
        </el-table-column>

        <el-table-column label="描述" min-width="180">
          <template #default="{ row }">
            <span class="bn-text-sub">{{ row.description || '—' }}</span>
          </template>
        </el-table-column>

        <el-table-column label="排序" width="70" align="center">
          <template #default="{ row }">{{ row.sortOrder ?? 0 }}</template>
        </el-table-column>

        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 0 ? 'info' : 'success'" size="small" effect="light">
              {{ row.status === 0 ? '已禁用' : '启用' }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column label="创建时间" width="150">
          <template #default="{ row }">
            <span class="bn-text-muted time">{{ formatDateTime(row.createTime) }}</span>
          </template>
        </el-table-column>

        <el-table-column label="操作" width="290" fixed="right">
          <template #default="{ row }">
            <el-button link size="small" @click="openEdit(row)">编辑</el-button>
            <el-button link size="small" type="primary" @click="openManage(row)">吧务</el-button>
            <el-button link size="small" @click="toggleStatus(row)">
              {{ row.status === 0 ? '启用' : '禁用' }}
            </el-button>
            <el-button link size="small" type="danger" @click="onDelete(row)">删除</el-button>
          </template>
        </el-table-column>

        <template #empty>
          <div class="bn-empty">暂无书吧</div>
        </template>
      </el-table>
    </template>

    <!-- ---------------- 创建申请 ---------------- -->
    <template v-else>
      <div class="bn-card filter-bar">
        <span class="tip">审批状态</span>
        <el-radio-group v-model="appStatus" @change="loadApplications">
          <el-radio-button :value="0">待审核</el-radio-button>
          <el-radio-button :value="1">已通过</el-radio-button>
          <el-radio-button :value="2">已驳回</el-radio-button>
        </el-radio-group>
        <el-button class="add-btn" @click="loadApplications">
          <el-icon><Refresh /></el-icon>
        </el-button>
      </div>

      <el-table v-loading="appLoading" :data="applications" class="bn-card table" border>
        <el-table-column label="图标" width="72" align="center">
          <template #default="{ row }">
            <img v-if="row.icon" :src="row.icon" :alt="row.name" class="cell-icon" />
            <span v-else class="cell-icon cell-icon-text">{{ (row.name || '?').slice(0, 1) }}</span>
          </template>
        </el-table-column>

        <el-table-column label="书吧名称" min-width="160">
          <template #default="{ row }">
            <span class="cat-name">{{ row.name }}</span>
          </template>
        </el-table-column>

        <el-table-column label="申请人" width="150">
          <template #default="{ row }">
            <span class="bn-text-sub">{{ row.ownerName || '—' }}</span>
          </template>
        </el-table-column>

        <el-table-column label="简介" min-width="220">
          <template #default="{ row }">
            <span class="bn-text-sub">{{ row.description || '—' }}</span>
          </template>
        </el-table-column>

        <el-table-column label="申请时间" width="150">
          <template #default="{ row }">
            <span class="bn-text-muted time">{{ formatDateTime(row.createTime) }}</span>
          </template>
        </el-table-column>

        <el-table-column label="状态" width="110" align="center">
          <template #default="{ row }">
            <el-tag :type="auditType(row.auditStatus)" size="small" effect="light">
              {{ auditText(row.auditStatus) }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column label="驳回原因" min-width="160">
          <template #default="{ row }">
            <span class="bn-text-muted">{{ row.rejectReason || '—' }}</span>
          </template>
        </el-table-column>

        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <template v-if="row.auditStatus === 0">
              <el-button
                link
                size="small"
                type="primary"
                :loading="appAuditing === String(row.id)"
                @click="approveApplication(row)"
              >
                通过
              </el-button>
              <el-button
                link
                size="small"
                type="danger"
                :loading="appAuditing === String(row.id)"
                @click="rejectApplication(row)"
              >
                驳回
              </el-button>
            </template>
            <span v-else class="bn-text-muted">已处理</span>
          </template>
        </el-table-column>

        <template #empty>
          <div class="bn-empty">没有符合条件的申请</div>
        </template>
      </el-table>
    </template>

    <!-- 通过申请弹窗：可改派吧主（默认申请人），通过后吧主自动关注本吧 -->
    <el-dialog v-model="approveDialog.visible" title="通过创建申请" width="460px">
      <p class="approve-tip">
        通过后「{{ approveDialog.barName }}」会立刻出现在前台书吧广场。
      </p>
      <el-form label-position="top" @submit.prevent>
        <el-form-item label="吧主">
          <el-select
            v-model="approveDialog.ownerId"
            filterable
            remote
            :remote-method="searchOwners"
            :loading="approveDialog.ownerLoading"
            placeholder="默认为申请人，输入用户名可改派"
            clearable
            style="width: 100%"
          >
            <el-option
              v-for="item in approveDialog.ownerOptions"
              :key="item.id"
              :label="item.username"
              :value="item.id"
            />
          </el-select>
          <p class="approve-tip">吧主会在通过后自动关注本吧；留空则维持申请人。</p>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="approveDialog.visible = false">取消</el-button>
        <el-button
          type="primary"
          :loading="Boolean(appAuditing)"
          @click="confirmApprove"
        >
          确定通过
        </el-button>
      </template>
    </el-dialog>

    <!-- 吧务管理：吧主 / 管理员 / 等级称号（走 /admin/bar，不受「必须是吧主」限制） -->
    <el-dialog v-model="manage.visible" :title="`吧务管理 · ${manage.barName}`" width="640px">
      <div v-loading="manage.loading" class="manage-body">
        <section class="mg-sec">
          <h4 class="mg-title">
            吧主
            <span v-if="manage.memberCount" class="mg-sub">吧内 {{ manage.memberCount }} 人</span>
          </h4>
          <div class="mg-row">
            <el-select
              v-model="manage.ownerId"
              filterable
              remote
              clearable
              :remote-method="searchOwnerCandidates"
              :loading="manage.ownerLoading"
              placeholder="输入用户名搜索；留空 = 官方吧（无吧主）"
              class="mg-grow"
            >
              <el-option
                v-for="item in manage.ownerOptions"
                :key="item.id"
                :label="item.username"
                :value="item.id"
              />
            </el-select>
            <el-button type="primary" :loading="manage.saving" @click="saveOwner">保存吧主</el-button>
          </div>
          <p class="mg-tip">
            吧主可隐藏吧内帖子、任命管理员、设置等级称号。任命后对方自动成为本吧成员；
            留空保存则由官方直管。
          </p>
        </section>

        <el-divider />

        <section class="mg-sec">
          <h4 class="mg-title">管理员（吧务）</h4>
          <div class="mg-tags">
            <el-tag
              v-for="item in manage.moderators"
              :key="item.userId"
              closable
              effect="plain"
              @close="removeModerator(item.userId)"
            >
              {{ item.username || item.userId }}
            </el-tag>
            <span v-if="!manage.moderators.length" class="mg-tip">暂无管理员</span>
          </div>
          <div class="mg-row">
            <el-select
              v-model="manage.modUserId"
              filterable
              remote
              clearable
              :remote-method="searchModCandidates"
              :loading="manage.modLoading"
              placeholder="输入用户名搜索并任命"
              class="mg-grow"
            >
              <el-option
                v-for="item in manage.modOptions"
                :key="item.id"
                :label="item.username"
                :value="item.id"
              />
            </el-select>
            <el-button type="primary" plain :loading="manage.saving" @click="addModerator">
              任命
            </el-button>
          </div>
        </section>

        <el-divider />

        <section class="mg-sec">
          <h4 class="mg-title">等级称号</h4>
          <div class="mg-levels">
            <div v-for="item in manage.titles" :key="item.level" class="mg-level">
              <span class="mg-level-tag">Lv.{{ item.level }}</span>
              <el-input
                v-model="item.title"
                maxlength="20"
                placeholder="未设置"
                clearable
              />
            </div>
          </div>
          <p class="mg-tip">留空的等级不设称号，前端显示 Lv.N。</p>
          <div class="mg-foot">
            <el-button type="primary" :loading="manage.saving" @click="saveTitles">
              保存称号
            </el-button>
          </div>
        </section>
      </div>

      <template #footer>
        <el-button @click="manage.visible = false">关闭</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="520px">
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
        <el-form-item label="书吧名称" prop="name">
          <el-input v-model="form.name" placeholder="如：科幻小说吧" maxlength="50" show-word-limit />
        </el-form-item>

        <el-form-item label="书吧图标">
          <div class="icon-row">
            <img v-if="form.icon" :src="form.icon" class="icon-preview" alt="图标预览" />
            <span v-else class="icon-preview cell-icon-text">{{ (form.name || '?').slice(0, 1) }}</span>
            <el-input
              v-model="form.icon"
              class="icon-input"
              placeholder="粘贴图片 URL，留空则用吧名首字当图标"
              clearable
            />
          </div>
        </el-form-item>

        <el-row :gutter="14">
          <el-col :span="12">
            <el-form-item label="排序序号">
              <el-input-number v-model="form.sortOrder" :min="0" :max="9999" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="状态">
              <el-switch
                v-model="form.status"
                :active-value="1"
                :inactive-value="0"
                active-text="启用"
                inactive-text="禁用"
              />
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item label="书吧简介" prop="description">
          <el-input
            v-model="form.description"
            type="textarea"
            :rows="3"
            maxlength="200"
            show-word-limit
            placeholder="选填，展示在书吧广场卡片上"
          />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="onSubmit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.admin-page {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.tab-bar {
  padding: 12px 16px;
}

.filter-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
  padding: 14px 16px;
}

.tip {
  font-size: 13px;
  color: var(--bn-text-muted);
}

.add-btn {
  margin-left: auto;
}

.table {
  padding: 0;
  overflow: hidden;
}

.cat-name {
  font-weight: 600;
  font-size: 13.5px;
}

.lv-tag {
  margin-left: 7px;
}

/* ---------- 书吧图标 ---------- */
.cell-icon,
.icon-preview {
  width: 30px;
  height: 30px;
  border-radius: 8px;
  object-fit: cover;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: var(--bn-primary-soft);
  flex: none;
}

.cell-icon-text {
  font-size: 13px;
  font-weight: 600;
  color: var(--bn-primary);
  background: var(--bn-primary-soft);
}

.icon-row {
  display: flex;
  align-items: center;
  gap: 12px;
  width: 100%;
}

.icon-input {
  flex: 1;
  min-width: 0;
}

.time {
  font-size: 12px;
}

.form-tip {
  font-size: 12px;
  color: #c8783c;
  margin-top: 4px;
  line-height: 1.5;
}

.approve-tip {
  font-size: 12.5px;
  color: var(--bn-text-muted);
  line-height: 1.6;
  margin: 0 0 8px;
}

/* ---------- 吧务管理弹窗 ---------- */
.manage-body {
  min-height: 120px;
}

.mg-sec + .mg-sec {
  margin-top: 4px;
}

.mg-title {
  font-size: 13.5px;
  font-weight: 600;
  margin-bottom: 10px;
  display: flex;
  align-items: center;
  gap: 8px;
}

.mg-sub {
  font-size: 12px;
  font-weight: 400;
  color: var(--bn-text-muted);
}

.mg-row {
  display: flex;
  align-items: center;
  gap: 10px;
}

.mg-grow {
  flex: 1;
  min-width: 0;
}

.mg-tip {
  font-size: 12px;
  color: var(--bn-text-muted);
  line-height: 1.7;
  margin-top: 8px;
}

/* 行内的说明文字不占额外的行距（按钮和输入框同一行时用） */
.mg-row .mg-tip {
  margin-top: 0;
  flex: 1;
}

/* 独占一行的收尾按钮：和上面的表单拉开距离，右对齐 */
.mg-foot {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
  padding-top: 14px;
  border-top: 1px dashed var(--bn-border);
}

.mg-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 7px;
  margin-bottom: 10px;
  min-height: 24px;
  align-items: center;
}

/* 10 个等级两列排，比一列到底更好扫 */
.mg-levels {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px 14px;
}

.mg-level {
  display: flex;
  align-items: center;
  gap: 8px;
}

.mg-level-tag {
  flex-shrink: 0;
  width: 46px;
  font-size: 12px;
  font-weight: 700;
  font-style: italic;
  color: #d4733f;
}
</style>
