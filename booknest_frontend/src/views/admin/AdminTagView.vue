<script setup>
/**
 * 标签管理：分页查询 + 新增 / 重命名 / 启停 / 删除
 * 使用次数由发帖流程维护，后台只读展示
 */
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as adminApi from '@/api/admin'
import { formatDateTime } from '@/utils/format'

const loading = ref(false)
const tableData = ref([])
const total = ref(0)
const pageInfo = reactive({ page: 1, pageSize: 20 })
const keyword = ref('')

const dialogVisible = ref(false)
const dialogMode = ref('create')
const submitting = ref(false)
const formRef = ref(null)

const emptyForm = () => ({ id: '', name: '', status: 1 })
const form = reactive(emptyForm())

const rules = {
  name: [
    { required: true, message: '请输入标签名称', trigger: 'blur' },
    { max: 30, message: '名称不能超过 30 个字符', trigger: 'blur' }
  ]
}

async function load() {
  loading.value = true
  try {
    const data = await adminApi.listTagPage({
      keyword: keyword.value.trim() || undefined,
      page: pageInfo.page,
      pageSize: pageInfo.pageSize
    })
    tableData.value = data?.list || []
    total.value = Number(data?.total || 0)
  } catch {
    tableData.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function onSearch() {
  pageInfo.page = 1
  load()
}

function openCreate() {
  dialogMode.value = 'create'
  Object.assign(form, emptyForm())
  dialogVisible.value = true
}

function openEdit(row) {
  dialogMode.value = 'edit'
  Object.assign(form, emptyForm(), {
    id: row.id,
    name: row.name || '',
    status: row.status ?? 1
  })
  dialogVisible.value = true
}

async function onSubmit() {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  try {
    if (dialogMode.value === 'create') {
      await adminApi.createTag({ name: form.name.trim(), status: form.status })
      ElMessage.success('标签已创建')
    } else {
      await adminApi.updateTag(form.id, { name: form.name.trim(), status: form.status })
      ElMessage.success('标签已更新')
    }
    dialogVisible.value = false
    load()
  } catch {
    /* 拦截器已提示（如「标签已存在」） */
  } finally {
    submitting.value = false
  }
}

/** 启用 / 禁用切换 */
async function toggleStatus(row) {
  const nextStatus = row.status === 0 ? 1 : 0
  const actionText = nextStatus === 1 ? '启用' : '禁用'
  try {
    await ElMessageBox.confirm(`确定${actionText}标签「${row.name}」吗？`, `${actionText}确认`, {
      type: 'warning',
      confirmButtonText: actionText,
      cancelButtonText: '取消'
    })
  } catch {
    return
  }
  try {
    await adminApi.updateTag(row.id, { name: row.name, status: nextStatus })
    ElMessage.success(`已${actionText}`)
    load()
  } catch {
    /* 拦截器已提示 */
  }
}

async function onDelete(row) {
  try {
    await ElMessageBox.confirm(
      `确定删除标签「${row.name}」吗？若已被帖子使用将无法删除。`,
      '删除确认',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  try {
    await adminApi.deleteTag(row.id)
    ElMessage.success('已删除')
    load()
  } catch {
    /* 后端返回「已被 N 篇帖子使用」，拦截器已提示 */
  }
}

onMounted(load)
</script>

<template>
  <div class="admin-page">
    <div class="bn-card filter-bar">
      <el-input
        v-model="keyword"
        placeholder="按标签名搜索"
        clearable
        style="width: 220px"
        @keyup.enter="onSearch"
      >
        <template #prefix><el-icon><Search /></el-icon></template>
      </el-input>

      <el-button type="primary" @click="onSearch">查询</el-button>
      <el-button @click="load">
        <el-icon><Refresh /></el-icon>
      </el-button>

      <el-button type="primary" plain class="add-btn" @click="openCreate">
        <el-icon style="margin-right: 4px"><Plus /></el-icon>新增标签
      </el-button>

      <span class="total-tip">共 {{ total }} 条</span>
    </div>

    <el-table v-loading="loading" :data="tableData" class="bn-card table" border stripe>
      <el-table-column label="标签名称" min-width="200">
        <template #default="{ row }">
          <el-tag size="small" effect="plain">{{ row.name }}</el-tag>
        </template>
      </el-table-column>

      <el-table-column label="使用次数" width="110" align="center">
        <template #default="{ row }">
          <span class="count">{{ row.useCount ?? 0 }}</span>
        </template>
      </el-table-column>

      <el-table-column label="状态" width="100" align="center">
        <template #default="{ row }">
          <el-tag :type="row.status === 0 ? 'info' : 'success'" size="small" effect="light">
            {{ row.status === 0 ? '已禁用' : '启用' }}
          </el-tag>
        </template>
      </el-table-column>

      <el-table-column label="创建时间" width="160">
        <template #default="{ row }">
          <span class="bn-text-muted time">{{ formatDateTime(row.createTime) }}</span>
        </template>
      </el-table-column>

      <el-table-column label="操作" width="170" fixed="right">
        <template #default="{ row }">
          <el-button link size="small" @click="openEdit(row)">编辑</el-button>
          <el-button link size="small" @click="toggleStatus(row)">
            {{ row.status === 0 ? '启用' : '禁用' }}
          </el-button>
          <el-button link size="small" type="danger" @click="onDelete(row)">删除</el-button>
        </template>
      </el-table-column>

      <template #empty>
        <div class="bn-empty">暂无标签</div>
      </template>
    </el-table>

    <div class="pager">
      <el-pagination
        v-model:current-page="pageInfo.page"
        v-model:page-size="pageInfo.pageSize"
        :total="total"
        :page-sizes="[20, 50, 100]"
        layout="total, sizes, prev, pager, next"
        background
        @current-change="load"
        @size-change="onSearch"
      />
    </div>

    <el-dialog
      v-model="dialogVisible"
      :title="dialogMode === 'create' ? '新增标签' : '编辑标签'"
      width="440px"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
        <el-form-item label="标签名称" prop="name">
          <el-input v-model="form.name" placeholder="如：科幻" maxlength="30" show-word-limit />
        </el-form-item>

        <el-form-item label="状态">
          <el-switch
            v-model="form.status"
            :active-value="1"
            :inactive-value="0"
            active-text="启用"
            inactive-text="禁用"
          />
        </el-form-item>

        <p class="form-tip">
          使用次数由发帖流程自动维护，后台不可直接修改。禁用后标签不再出现在前台。
        </p>
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

.filter-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
  padding: 14px 16px;
}

.add-btn {
  margin-left: auto;
}

.total-tip {
  font-size: 13px;
  color: var(--bn-text-muted);
}

.table {
  padding: 0;
  overflow: hidden;
}

.count {
  font-weight: 600;
  color: #c8783c;
  font-size: 13px;
}

.time {
  font-size: 12px;
}

.pager {
  display: flex;
  justify-content: flex-end;
}

.form-tip {
  font-size: 12px;
  color: var(--bn-text-muted);
  line-height: 1.6;
}
</style>
