<script setup>
/**
 * 分类管理：两级分类树的新增 / 编辑 / 删除 / 启停
 * 分类树固定两级——一级分类（parentId 为空）与其子分类
 */
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as adminApi from '@/api/admin'
import { formatDateTime } from '@/utils/format'

const loading = ref(false)
const treeData = ref([])

const dialogVisible = ref(false)
const dialogMode = ref('create')
const submitting = ref(false)
const formRef = ref(null)

const emptyForm = () => ({
  id: '',
  name: '',
  parentId: '',
  sortOrder: 0,
  description: '',
  status: 1
})

const form = reactive(emptyForm())

const rules = {
  name: [
    { required: true, message: '请输入分类名称', trigger: 'blur' },
    { max: 50, message: '名称不能超过 50 个字符', trigger: 'blur' }
  ],
  description: [{ max: 200, message: '描述不能超过 200 个字符', trigger: 'blur' }]
}

const dialogTitle = computed(() => (dialogMode.value === 'create' ? '新增分类' : '编辑分类'))

/** 上级分类候选：一级分类，且排除自身 */
const parentOptions = computed(() =>
  treeData.value
    .filter((item) => item.id !== form.id)
    .map((item) => ({ id: item.id, name: item.name }))
)

/** 编辑对象自身是否已有子分类：有则不允许降级为二级分类 */
const editingHasChildren = computed(() => {
  if (!form.id) return false
  const node = findNode(treeData.value, form.id)
  return Boolean(node?.children?.length)
})

function findNode(nodes, id) {
  for (const node of nodes) {
    if (node.id === id) return node
    const hit = node.children ? findNode(node.children, id) : null
    if (hit) return hit
  }
  return null
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

function openCreateRoot() {
  dialogMode.value = 'create'
  Object.assign(form, emptyForm())
  dialogVisible.value = true
}

function openCreateChild(row) {
  dialogMode.value = 'create'
  Object.assign(form, emptyForm(), { parentId: row.id })
  dialogVisible.value = true
}

function openEdit(row) {
  dialogMode.value = 'edit'
  Object.assign(form, emptyForm(), {
    id: row.id,
    name: row.name || '',
    parentId: row.parentId || '',
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
    parentId: form.parentId || null,
    sortOrder: Number(form.sortOrder) || 0,
    description: form.description.trim() || null,
    status: form.status
  }

  submitting.value = true
  try {
    if (dialogMode.value === 'create') {
      await adminApi.createCategory(payload)
      ElMessage.success('分类已创建')
    } else {
      await adminApi.updateCategory(form.id, payload)
      ElMessage.success('分类已更新')
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
    await ElMessageBox.confirm(`确定${actionText}分类「${row.name}」吗？`, `${actionText}确认`, {
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
      parentId: row.parentId || null,
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
  const hint = row.children?.length ? '该分类下仍有子分类，需先删除子分类。' : '该操作不可恢复。'
  try {
    await ElMessageBox.confirm(`确定删除分类「${row.name}」吗？${hint}`, '删除确认', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消'
    })
  } catch {
    return
  }
  try {
    await adminApi.deleteCategory(row.id)
    ElMessage.success('已删除')
    load()
  } catch {
    /* 后端返回「有子分类 / 有帖子引用」等原因，拦截器已提示 */
  }
}

onMounted(load)
</script>

<template>
  <div class="admin-page">
    <div class="bn-card filter-bar">
      <p class="tip">分类为两级结构，前台只展示「启用」状态的分类。</p>
      <el-button @click="load">
        <el-icon><Refresh /></el-icon>
      </el-button>
      <el-button type="primary" plain class="add-btn" @click="openCreateRoot">
        <el-icon style="margin-right: 4px"><Plus /></el-icon>新增一级分类
      </el-button>
    </div>

    <el-table
      v-loading="loading"
      :data="treeData"
      class="bn-card table"
      row-key="id"
      default-expand-all
      :tree-props="{ children: 'children' }"
      border
    >
      <el-table-column label="分类名称" min-width="200">
        <template #default="{ row }">
          <span class="cat-name">{{ row.name }}</span>
          <el-tag v-if="!row.parentId" size="small" effect="plain" type="info" class="lv-tag">一级</el-tag>
        </template>
      </el-table-column>

      <el-table-column label="描述" min-width="200">
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

      <el-table-column label="操作" width="230" fixed="right">
        <template #default="{ row }">
          <el-button v-if="!row.parentId" link size="small" @click="openCreateChild(row)">加子类</el-button>
          <el-button link size="small" @click="openEdit(row)">编辑</el-button>
          <el-button link size="small" @click="toggleStatus(row)">
            {{ row.status === 0 ? '启用' : '禁用' }}
          </el-button>
          <el-button link size="small" type="danger" @click="onDelete(row)">删除</el-button>
        </template>
      </el-table-column>

      <template #empty>
        <div class="bn-empty">暂无分类</div>
      </template>
    </el-table>

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="520px">
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
        <el-form-item label="分类名称" prop="name">
          <el-input v-model="form.name" placeholder="如：文学小说" maxlength="50" show-word-limit />
        </el-form-item>

        <el-form-item label="上级分类">
          <el-select
            v-model="form.parentId"
            placeholder="留空则为一级分类"
            clearable
            :disabled="editingHasChildren"
            style="width: 100%"
          >
            <el-option v-for="item in parentOptions" :key="item.id" :label="item.name" :value="item.id" />
          </el-select>
          <p v-if="editingHasChildren" class="form-tip">该分类下已有子分类，不能改为子分类。</p>
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

        <el-form-item label="分类描述" prop="description">
          <el-input
            v-model="form.description"
            type="textarea"
            :rows="3"
            maxlength="200"
            show-word-limit
            placeholder="选填"
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

.time {
  font-size: 12px;
}

.form-tip {
  font-size: 12px;
  color: #c8783c;
  margin-top: 4px;
  line-height: 1.5;
}
</style>
