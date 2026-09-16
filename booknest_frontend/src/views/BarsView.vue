<script setup>
/**
 * 书吧广场：贴吧式的「吧」列表
 *
 * 一个「吧」在数据上就是一个一级分类（复用 category 表，见 CategoryServiceImpl），
 * 展示上补齐了帖子数、吧主与简介，让用户能按兴趣挑吧逛。
 *
 * 创吧走「申请 - 审批」：用户提交后是待审核状态，管理员通过后才出现在广场，
 * 因此本页同时展示「我的申请」进度，避免用户提交完就石沉大海。
 */
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import * as taxonomyApi from '@/api/taxonomy'
import { useUserStore } from '@/stores/user'
import BnAvatar from '@/components/BnAvatar.vue'

const router = useRouter()
const userStore = useUserStore()

const bars = ref([])
const myApplications = ref([])
const loading = ref(false)
const error = ref('')
const keyword = ref('')

const applyDialog = reactive({
  visible: false,
  name: '',
  description: '',
  /** 书吧图标 URL：选图即上传，提交时只把 URL 交上去 */
  icon: '',
  /** 图标上传中（与 submitting 分开：上传是选完就传，提交才是终态） */
  uploadingIcon: false,
  submitting: false
})

const filteredBars = computed(() => {
  const kw = keyword.value.trim().toLowerCase()
  if (!kw) return bars.value
  return bars.value.filter((bar) => (bar.name || '').toLowerCase().includes(kw))
})

const pendingCount = computed(
  () => myApplications.value.filter((item) => item.auditStatus === 0).length
)

/** 审核状态 → 展示文案 */
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

async function loadBars() {
  loading.value = true
  error.value = ''
  try {
    bars.value = (await taxonomyApi.listBars()) || []
  } catch (err) {
    error.value = err.message || '书吧列表加载失败'
    bars.value = []
  } finally {
    loading.value = false
  }
}

async function loadMyApplications() {
  if (!userStore.isLoggedIn) {
    myApplications.value = []
    return
  }
  try {
    myApplications.value = (await taxonomyApi.listMyBarApplications()) || []
  } catch {
    myApplications.value = []
  }
}

function openApply() {
  if (!userStore.isLoggedIn) {
    ElMessage.info('请先登录后再申请创建书吧')
    router.push({ name: 'login', query: { redirect: '/bars' } })
    return
  }
  applyDialog.visible = true
  applyDialog.name = ''
  applyDialog.description = ''
  applyDialog.icon = ''
}

/**
 * 书吧图标：选图即上传（复用 /common/upload 的 OSS 中转，服务端换 URL 回来）。
 * 上传失败必须把 icon 清掉 —— 否则用户以为图标生效了，提交上去却是空。
 */
async function onIconUpload(options) {
  const { file, onSuccess, onError } = options
  applyDialog.uploadingIcon = true
  try {
    const { uploadFile } = await import('@/api/user')
    const url = await uploadFile(file)
    applyDialog.icon = url
    onSuccess?.(url)
    ElMessage.success('图标上传成功')
  } catch (err) {
    applyDialog.icon = ''
    onError?.(err)
  } finally {
    applyDialog.uploadingIcon = false
  }
}

function clearIcon() {
  applyDialog.icon = ''
}

async function submitApply() {
  const name = applyDialog.name.trim()
  if (!name) {
    ElMessage.warning('请填写书吧名称')
    return
  }
  applyDialog.submitting = true
  try {
    await taxonomyApi.applyBar({
      name,
      description: applyDialog.description.trim() || undefined,
      icon: applyDialog.icon || undefined
    })
    ElMessage.success('申请已提交，等待管理员审核')
    applyDialog.visible = false
    await loadMyApplications()
  } catch {
    // 拦截器已提示
  } finally {
    applyDialog.submitting = false
  }
}

function goBar(bar) {
  router.push({ name: 'bar', params: { id: bar.id } })
}

onMounted(() => {
  loadBars()
  loadMyApplications()
})
</script>

<template>
  <div class="bn-container bars-page">
    <header class="bars-head">
      <div>
        <h1 class="bn-page-title">书吧广场</h1>
        <p class="bn-page-sub">按兴趣找到你的同好 —— 每个吧都是一个小圈子</p>
      </div>
      <el-button type="primary" @click="openApply">
        <el-icon style="margin-right: 4px"><Plus /></el-icon>申请创建书吧
      </el-button>
    </header>

    <!-- 我的申请进度：提交完不能没下文，这里给出明确状态 -->
    <el-card v-if="myApplications.length" shadow="never" class="apply-card">
      <template #header>
        <div class="apply-card-head">
          <span>我的创建申请</span>
          <el-tag v-if="pendingCount" size="small" type="warning" effect="plain">
            {{ pendingCount }} 个待审核
          </el-tag>
        </div>
      </template>
      <div class="apply-list">
        <div v-for="item in myApplications" :key="item.id" class="apply-row">
          <span class="apply-name bn-ellipsis-1">{{ item.name }}</span>
          <el-tag :type="auditType(item.auditStatus)" size="small" effect="plain">
            {{ auditText(item.auditStatus) }}
          </el-tag>
          <span v-if="item.rejectReason" class="apply-reason bn-ellipsis-1">
            原因：{{ item.rejectReason }}
          </span>
          <el-button
            v-if="item.auditStatus === 1"
            link
            size="small"
            @click="goBar(item)"
          >
            进入
          </el-button>
        </div>
      </div>
    </el-card>

    <div class="bars-toolbar">
      <el-input v-model="keyword" placeholder="搜索书吧" clearable class="bars-search">
        <template #prefix>
          <el-icon><Search /></el-icon>
        </template>
      </el-input>
      <span class="bars-count bn-text-muted">共 {{ filteredBars.length }} 个书吧</span>
    </div>

    <el-alert v-if="error" :title="error" type="error" show-icon :closable="false" class="bn-mt-12">
      <template #default>
        <el-button size="small" @click="loadBars">重新加载</el-button>
      </template>
    </el-alert>

    <el-skeleton v-if="loading" :rows="5" animated class="bn-mt-16" />

    <template v-else>
      <div v-if="filteredBars.length" class="bar-grid">
        <button
          v-for="bar in filteredBars"
          :key="bar.id"
          type="button"
          class="bar-card"
          @click="goBar(bar)"
        >
          <!-- 有图标就用图标，没上传则回退成吧名首字 -->
          <img v-if="bar.icon" :src="bar.icon" :alt="bar.name" class="bar-mark bar-mark-img" />
          <span v-else class="bar-mark">{{ (bar.name || '吧').slice(0, 1) }}</span>
          <span class="bar-body">
            <span class="bar-name bn-ellipsis-1">{{ bar.name }}</span>
            <span class="bar-desc bn-ellipsis-2">{{ bar.description || '这个吧还没有简介' }}</span>
            <span class="bar-meta">
              <span class="bar-posts">{{ bar.postCount || 0 }} 帖</span>
              <span v-if="bar.ownerName" class="bar-owner">
                <BnAvatar :src="bar.ownerAvatar" :name="bar.ownerName" :size="18" :linkable="false" />
                {{ bar.ownerName }}
              </span>
              <span v-else class="bar-owner">官方</span>
            </span>
          </span>
        </button>
      </div>

      <div v-else class="bn-empty">
        <el-icon :size="34" color="#c8bdb1"><Grid /></el-icon>
        <p class="bn-mt-12">{{ keyword ? '没有匹配的书吧' : '还没有书吧，来创建第一个吧' }}</p>
        <el-button type="primary" class="bn-mt-12" @click="openApply">申请创建书吧</el-button>
      </div>
    </template>

    <!-- 申请创建书吧 -->
    <el-dialog v-model="applyDialog.visible" title="申请创建书吧" width="440px">
      <el-form label-position="top">
        <!-- 书吧图标：选图即上传，提交时只带 URL -->
        <el-form-item label="书吧图标（可选）">
          <div class="icon-row">
            <img v-if="applyDialog.icon" :src="applyDialog.icon" class="icon-preview" alt="图标预览" />
            <div v-else class="icon-preview icon-placeholder">
              <el-icon :size="20"><Picture /></el-icon>
            </div>
            <div class="icon-actions">
              <div class="icon-btns">
                <el-upload
                  :show-file-list="false"
                  :http-request="onIconUpload"
                  accept="image/*"
                  :disabled="applyDialog.uploadingIcon"
                >
                  <el-button size="small" :loading="applyDialog.uploadingIcon">
                    {{ applyDialog.icon ? '更换图标' : '上传图标' }}
                  </el-button>
                </el-upload>
                <el-button v-if="applyDialog.icon" link size="small" @click="clearIcon">移除</el-button>
              </div>
              <p class="bn-text-muted icon-tip">不上传时，广场里用吧名首字当图标</p>
            </div>
          </div>
        </el-form-item>

        <el-form-item label="吧名">
          <el-input v-model="applyDialog.name" maxlength="50" show-word-limit placeholder="例如：科幻小说吧" />
        </el-form-item>
        <el-form-item label="吧简介（可选）">
          <el-input
            v-model="applyDialog.description"
            type="textarea"
            :rows="3"
            maxlength="200"
            show-word-limit
            placeholder="介绍一下这个吧准备聊什么"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="applyDialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="applyDialog.submitting" @click="submitApply">
          提交申请
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.bars-page {
  padding-top: 22px;
  padding-bottom: 40px;
}

.bars-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
}

.bars-head .bn-page-sub {
  margin-bottom: 0;
}

.apply-card {
  margin-bottom: 16px;
  border-radius: var(--bn-radius);
}

.apply-card-head {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  font-weight: 600;
}

.apply-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.apply-row {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 13px;
}

.apply-name {
  max-width: 180px;
  font-weight: 600;
}

.apply-reason {
  flex: 1;
  min-width: 0;
  color: var(--bn-text-muted);
  font-size: 12px;
}

.bars-toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
}

.bars-search {
  max-width: 280px;
}

.bars-count {
  font-size: 12.5px;
}

.bar-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  margin-top: 14px;
}

.bar-card {
  display: flex;
  align-items: flex-start;
  gap: 11px;
  padding: 14px;
  border: 1px solid var(--bn-border);
  border-radius: var(--bn-radius);
  background: var(--bn-surface);
  font-family: inherit;
  text-align: left;
  cursor: pointer;
  transition: all 0.16s ease;
}

.bar-card:hover {
  border-color: #ded4c8;
  transform: translateY(-2px);
  box-shadow: 0 6px 18px rgba(47, 42, 37, 0.08);
}

.bar-mark {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 38px;
  height: 38px;
  flex-shrink: 0;
  border-radius: 10px;
  background: var(--bn-primary-soft);
  color: var(--bn-primary);
  font-size: 17px;
  font-weight: 700;
}

.bar-body {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
  flex: 1;
}

.bar-name {
  font-size: 14px;
  font-weight: 600;
}

.bar-desc {
  font-size: 12px;
  color: var(--bn-text-muted);
  line-height: 1.5;
}

.bar-meta {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 2px;
  font-size: 11.5px;
  color: var(--bn-text-muted);
}

.bar-posts {
  color: var(--bn-primary);
  font-weight: 600;
}

.bar-owner {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  min-width: 0;
}

/* 有图标时，原来的单字方块换成图片（尺寸/圆角继承 .bar-mark） */
.bar-mark-img {
  object-fit: cover;
  background: #efe9e2;
}

/* ---------- 申请弹窗里的图标上传 ---------- */
.icon-row {
  display: flex;
  align-items: center;
  gap: 14px;
  width: 100%;
}

.icon-preview {
  width: 60px;
  height: 60px;
  flex-shrink: 0;
  border-radius: 14px;
  object-fit: cover;
  background: #efe9e2;
}

.icon-placeholder {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: var(--bn-text-muted);
  border: 1px dashed var(--bn-border);
  background: #faf8f6;
}

.icon-actions {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.icon-btns {
  display: flex;
  align-items: center;
  gap: 10px;
}

.icon-tip {
  font-size: 12px;
}

@media (max-width: 980px) {
  .bar-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 640px) {
  .bar-grid {
    grid-template-columns: 1fr;
  }
  .bars-head {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>
