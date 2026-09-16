<script setup>
/**
 * 用户管理：关键字/状态筛选 + 启用禁用 + 角色调整
 *
 * 保留 ID 列：用户 UUID 在排查问题（对日志、对评论作者、对机器人创建者）时
 * 经常要用，后台原来不显示，只能翻库。现在这一列点一下就能整串复制。
 */
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as adminApi from '@/api/admin'
import { formatDateTime } from '@/utils/format'
import { useAdminStore } from '@/stores/user'
import BnAvatar from '@/components/BnAvatar.vue'

const adminStore = useAdminStore()

const loading = ref(false)
const tableData = ref([])
const total = ref(0)
const pageInfo = reactive({ page: 1, pageSize: 10 })
const query = reactive({ keyword: '', status: undefined })

async function load() {
  loading.value = true
  try {
    const data = await adminApi.listUsers({
      keyword: query.keyword.trim() || undefined,
      status: query.status,
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

function onReset() {
  query.keyword = ''
  query.status = undefined
  onSearch()
}

async function toggleStatus(row) {
  const next = row.status === 1 ? 0 : 1
  const action = next === 1 ? '启用' : '禁用'
  try {
    await ElMessageBox.confirm(`确定${action}用户「${row.username || row.account}」吗？`, `${action}确认`, {
      type: 'warning',
      confirmButtonText: action,
      cancelButtonText: '取消'
    })
  } catch {
    return
  }
  await adminApi.updateUserStatus(row.id, next)
  ElMessage.success(`已${action}`)
  load()
}

async function toggleRole(row) {
  const next = row.role === 'ADMIN' ? 'USER' : 'ADMIN'
  const action = next === 'ADMIN' ? '提升为管理员' : '降为普通用户'
  try {
    await ElMessageBox.confirm(
      `确定将「${row.username || row.account}」${action}吗？`,
      '角色调整',
      { type: 'warning', confirmButtonText: '确定', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  await adminApi.updateUserRole(row.id, next)
  ElMessage.success('角色已更新')
  load()
}

/** 是否本人账号：避免误操作导致自锁 */
function isCurrentAccount(row) {
  return Boolean(adminStore.adminInfo?.id) && String(row.id) === String(adminStore.adminInfo.id)
}

/** 复制到剪贴板：UUID 太长，页面上只显示前 8 位 */
async function copyId(row) {
  const id = String(row.id || '')
  try {
    await navigator.clipboard.writeText(id)
    ElMessage.success('用户 ID 已复制')
  } catch {
    ElMessage.info(id)
  }
}

onMounted(load)
</script>

<template>
  <div class="admin-page">
    <div class="bn-card filter-bar">
      <el-input
        v-model="query.keyword"
        placeholder="账号 / 用户名 / 邮箱"
        clearable
        style="width: 230px"
        @keyup.enter="onSearch"
      >
        <template #prefix><el-icon><Search /></el-icon></template>
      </el-input>

      <el-select v-model="query.status" placeholder="状态" clearable style="width: 130px">
        <el-option label="正常" :value="1" />
        <el-option label="禁用" :value="0" />
      </el-select>

      <el-button type="primary" @click="onSearch">查询</el-button>
      <el-button @click="onReset">重置</el-button>
      <el-button @click="load">
        <el-icon><Refresh /></el-icon>
      </el-button>

      <span class="total-tip">共 {{ total }} 条</span>
    </div>

    <el-table v-loading="loading" :data="tableData" class="bn-card table" border stripe>
      <el-table-column label="用户" min-width="200">
        <template #default="{ row }">
          <div class="user-cell">
            <BnAvatar :src="row.avatar" :name="row.username || row.account" :size="34" :linkable="false" />
            <div class="user-cell-body">
              <p class="user-name">
                {{ row.username || '未设置' }}
                <el-tag v-if="row.role === 'ADMIN'" size="small" type="danger" effect="plain">
                  管理员
                </el-tag>
              </p>
              <p class="user-account">{{ row.account || row.id }}</p>
            </div>
          </div>
        </template>
      </el-table-column>

      <el-table-column label="用户 ID" width="140">
        <template #default="{ row }">
          <button type="button" class="uid" :title="`${row.id}（点击复制）`" @click="copyId(row)">
            {{ String(row.id || '').slice(0, 8) }}…
          </button>
        </template>
      </el-table-column>

      <el-table-column label="邮箱" min-width="170">
        <template #default="{ row }">
          <span class="bn-text-sub small">{{ row.email || '—' }}</span>
        </template>
      </el-table-column>

      <el-table-column label="手机号" width="130">
        <template #default="{ row }">
          <span class="bn-text-sub small">{{ row.phone || '—' }}</span>
        </template>
      </el-table-column>

      <el-table-column label="会员" width="90">
        <template #default="{ row }">
          <el-tag v-if="row.userLevel !== 0" size="small" type="warning" effect="plain">
            {{ row.userLevel === 1 ? '普通会员' : '高级会员' }}
          </el-tag>
          <span v-else class="bn-text-muted small">免费</span>
        </template>
      </el-table-column>

      <el-table-column label="状态" width="88">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">
            {{ row.status === 1 ? '正常' : '禁用' }}
          </el-tag>
        </template>
      </el-table-column>

      <el-table-column label="注册时间" width="150">
        <template #default="{ row }">
          <span class="bn-text-muted time">{{ formatDateTime(row.createTime) }}</span>
        </template>
      </el-table-column>

      <el-table-column label="操作" width="180" fixed="right">
        <template #default="{ row }">
          <el-button
            link
            size="small"
            :type="row.status === 1 ? 'danger' : 'success'"
            :disabled="isCurrentAccount(row)"
            @click="toggleStatus(row)"
          >
            {{ row.status === 1 ? '禁用' : '启用' }}
          </el-button>
          <el-button
            link
            size="small"
            :disabled="isCurrentAccount(row)"
            @click="toggleRole(row)"
          >
            {{ row.role === 'ADMIN' ? '降为用户' : '设为管理员' }}
          </el-button>
        </template>
      </el-table-column>

      <template #empty>
        <div class="bn-empty">暂无数据</div>
      </template>
    </el-table>

    <div class="pager">
      <el-pagination
        v-model:current-page="pageInfo.page"
        v-model:page-size="pageInfo.pageSize"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next"
        background
        @current-change="load"
        @size-change="onSearch"
      />
    </div>
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

.total-tip {
  margin-left: auto;
  font-size: 13px;
  color: var(--bn-text-muted);
}

.table {
  padding: 0;
  overflow: hidden;
}

.user-cell {
  display: flex;
  align-items: center;
  gap: 10px;
}

.user-name {
  font-size: 13.5px;
  font-weight: 600;
  display: flex;
  align-items: center;
  gap: 6px;
}

.user-account {
  font-size: 11.5px;
  color: var(--bn-text-muted);
  margin-top: 2px;
  word-break: break-all;
}

/* UUID 很长，列里只放前 8 位 + 省略号，完整值在 title 里，点击直接复制 */
.uid {
  border: none;
  background: #faf8f5;
  border-radius: 6px;
  padding: 2px 7px;
  font-family: 'JetBrains Mono', Menlo, Consolas, monospace;
  font-size: 12px;
  color: var(--bn-text-sub);
  cursor: pointer;
}

.uid:hover {
  background: var(--bn-primary-soft);
  color: var(--bn-primary);
}

.small {
  font-size: 12.5px;
}

.time {
  font-size: 12px;
}

.pager {
  display: flex;
  justify-content: flex-end;
}
</style>
