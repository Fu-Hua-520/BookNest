<script setup>
/**
 * 帖子审核：分页列表 + 审核通过/驳回 + 置顶 + 上下架
 * 后端管理端返回 PageInfo（total / list）
 */
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as adminApi from '@/api/admin'
import { formatDateTime } from '@/utils/format'

const loading = ref(false)
const tableData = ref([])
const total = ref(0)
const pageInfo = reactive({ page: 1, pageSize: 10 })

const query = reactive({ auditStatus: undefined, status: undefined })

/** 审核状态：0 待审核 1 通过 2 驳回 */
const AUDIT_MAP = {
  0: { text: '待审核', type: 'warning' },
  1: { text: '已通过', type: 'success' },
  2: { text: '已驳回', type: 'danger' }
}

const auditOptions = [
  { label: '全部审核状态', value: undefined },
  { label: '待审核', value: 0 },
  { label: '已通过', value: 1 },
  { label: '已驳回', value: 2 }
]

const statusOptions = [
  { label: '全部上架状态', value: undefined },
  { label: '已上架', value: 1 },
  { label: '已下架', value: 0 }
]

const dialogVisible = ref(false)
const dialogMode = ref('approve')
const currentPost = ref(null)
const auditForm = reactive({ auditReason: '' })
const submitting = ref(false)

const dialogTitle = computed(() => (dialogMode.value === 'approve' ? '通过审核' : '驳回帖子'))

async function load() {
  loading.value = true
  try {
    const data = await adminApi.listPosts({
      page: pageInfo.page,
      pageSize: pageInfo.pageSize,
      auditStatus: query.auditStatus,
      status: query.status
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
  query.auditStatus = undefined
  query.status = undefined
  onSearch()
}

function openAudit(row, mode) {
  currentPost.value = row
  dialogMode.value = mode
  auditForm.auditReason = ''
  dialogVisible.value = true
}

async function submitAudit() {
  if (dialogMode.value === 'reject' && !auditForm.auditReason.trim()) {
    ElMessage.warning('请填写驳回原因')
    return
  }
  submitting.value = true
  try {
    await adminApi.auditPost(currentPost.value.id, {
      auditStatus: dialogMode.value === 'approve' ? 1 : 2,
      auditReason: auditForm.auditReason.trim() || undefined
    })
    ElMessage.success(dialogMode.value === 'approve' ? '已通过审核' : '已驳回')
    dialogVisible.value = false
    load()
  } finally {
    submitting.value = false
  }
}

async function toggleTop(row) {
  const next = row.isTop === 1 ? 0 : 1
  await adminApi.setPostTop(row.id, next)
  ElMessage.success(next === 1 ? '已置顶' : '已取消置顶')
  load()
}

async function toggleStatus(row) {
  const next = row.status === 1 ? 0 : 1
  const action = next === 1 ? '上架' : '下架'
  try {
    await ElMessageBox.confirm(`确定${action}这篇帖子吗？`, `${action}确认`, {
      type: 'warning',
      confirmButtonText: action,
      cancelButtonText: '取消'
    })
  } catch {
    return
  }
  await adminApi.setPostStatus(row.id, next)
  ElMessage.success(`已${action}`)
  load()
}

onMounted(load)
</script>

<template>
  <div class="admin-page">
    <!-- 筛选 -->
    <div class="bn-card filter-bar">
      <el-select v-model="query.auditStatus" placeholder="审核状态" clearable style="width: 160px">
        <el-option
          v-for="item in auditOptions.slice(1)"
          :key="String(item.value)"
          :label="item.label"
          :value="item.value"
        />
      </el-select>

      <el-select v-model="query.status" placeholder="上架状态" clearable style="width: 150px">
        <el-option
          v-for="item in statusOptions.slice(1)"
          :key="String(item.value)"
          :label="item.label"
          :value="item.value"
        />
      </el-select>

      <el-button type="primary" @click="onSearch">
        <el-icon style="margin-right: 4px"><Search /></el-icon>查询
      </el-button>
      <el-button @click="onReset">重置</el-button>
      <el-button text @click="load">
        <el-icon><Refresh /></el-icon>
      </el-button>

      <span class="total-tip">共 {{ total }} 条</span>
    </div>

    <el-table v-loading="loading" :data="tableData" class="bn-card table" border stripe>
      <el-table-column label="标题" min-width="230">
        <template #default="{ row }">
          <div class="title-cell">
            <span class="title-text">{{ row.title }}</span>
            <el-tag v-if="row.isTop === 1" size="small" type="danger" effect="plain">置顶</el-tag>
          </div>
          <p class="summary bn-ellipsis-1">{{ row.summary || '—' }}</p>
        </template>
      </el-table-column>

      <el-table-column label="作者" width="110">
        <template #default="{ row }">
          <span class="bn-text-sub">{{ row.userId ? String(row.userId).slice(0, 8) : '—' }}</span>
        </template>
      </el-table-column>

      <el-table-column label="统计" width="140">
        <template #default="{ row }">
          <div class="stat-cell">
            <span>阅 {{ row.viewCount || 0 }}</span>
            <span>赞 {{ row.likeCount || 0 }}</span>
            <span>评 {{ row.commentCount || 0 }}</span>
          </div>
        </template>
      </el-table-column>

      <el-table-column label="审核" width="96">
        <template #default="{ row }">
          <el-tag :type="AUDIT_MAP[row.auditStatus]?.type || 'info'" size="small">
            {{ AUDIT_MAP[row.auditStatus]?.text || '未知' }}
          </el-tag>
        </template>
      </el-table-column>

      <el-table-column label="上架" width="86">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small" effect="plain">
            {{ row.status === 1 ? '已上架' : '已下架' }}
          </el-tag>
        </template>
      </el-table-column>

      <el-table-column label="发布时间" width="150">
        <template #default="{ row }">
          <span class="bn-text-muted time">
            {{ formatDateTime(row.publishTime || row.createTime) }}
          </span>
        </template>
      </el-table-column>

      <el-table-column label="操作" width="220" fixed="right">
        <template #default="{ row }">
          <el-button v-if="row.auditStatus !== 1" link size="small" type="success" @click="openAudit(row, 'approve')">
            通过
          </el-button>
          <el-button v-if="row.auditStatus !== 2" link size="small" type="warning" @click="openAudit(row, 'reject')">
            驳回
          </el-button>
          <el-button link size="small" @click="toggleTop(row)">
            {{ row.isTop === 1 ? '取消置顶' : '置顶' }}
          </el-button>
          <el-button link size="small" :type="row.status === 1 ? 'danger' : 'primary'" @click="toggleStatus(row)">
            {{ row.status === 1 ? '下架' : '上架' }}
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

    <!-- 审核弹窗 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="440px">
      <p class="dialog-post bn-ellipsis-2">{{ currentPost?.title }}</p>
      <el-form label-position="top">
        <el-form-item :label="dialogMode === 'approve' ? '备注（可选）' : '驳回原因'" :required="dialogMode === 'reject'">
          <el-input
            v-model="auditForm.auditReason"
            type="textarea"
            :rows="3"
            :placeholder="dialogMode === 'approve' ? '可填写审核备注' : '请说明驳回原因，作者可见'"
            maxlength="200"
            show-word-limit
          />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button
          :type="dialogMode === 'approve' ? 'success' : 'warning'"
          :loading="submitting"
          @click="submitAudit"
        >
          确定{{ dialogMode === 'approve' ? '通过' : '驳回' }}
        </el-button>
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

.total-tip {
  margin-left: auto;
  font-size: 13px;
  color: var(--bn-text-muted);
}

.table {
  padding: 0;
  overflow: hidden;
}

.title-cell {
  display: flex;
  align-items: center;
  gap: 7px;
}

.title-text {
  font-weight: 600;
  font-size: 13.5px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.summary {
  font-size: 12px;
  color: var(--bn-text-muted);
  margin-top: 3px;
}

.stat-cell {
  display: flex;
  flex-direction: column;
  font-size: 11.5px;
  color: var(--bn-text-sub);
  line-height: 1.5;
}

.time {
  font-size: 12px;
}

.pager {
  display: flex;
  justify-content: flex-end;
}

.dialog-post {
  font-size: 13.5px;
  font-weight: 600;
  background: #faf8f5;
  padding: 9px 12px;
  border-radius: var(--bn-radius-sm);
  margin-bottom: 14px;
}
</style>
