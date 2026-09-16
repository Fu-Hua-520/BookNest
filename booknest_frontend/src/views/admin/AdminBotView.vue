<script setup>
/**
 * 管理后台 - AI 机器人审核
 *
 * 机器人要拿用户自己填的第三方 API Key 去发评论，一旦拿来发违规内容，
 * 平台是要担责的。所以「创建」与「每次改配置」都必须过审，这个页面就是那道闸门。
 *
 * 审批前要能看清「审的是什么」：厂商、模型、系统提示词、温度、Key 是否已配，
 * 因此表格支持展开行查看完整配置 —— 只看名字就点通过，等于没审。
 */
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as adminApi from '@/api/admin'
import BnAvatar from '@/components/BnAvatar.vue'
import { formatDateTime } from '@/utils/format'

const list = ref([])
const loading = ref(false)
const auditStatus = ref(0) // 0-待审核 1-已通过 2-已驳回
const auditing = ref('')

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
    list.value = (await adminApi.listBots(auditStatus.value)) || []
  } catch {
    list.value = []
  } finally {
    loading.value = false
  }
}

async function approve(row) {
  try {
    await ElMessageBox.confirm(
      `通过后「${row.name}」立刻可以在全站评论区被 @ 触发，使用创建者自己的 API Key。确定通过吗？`,
      '通过审核',
      { confirmButtonText: '通过', cancelButtonText: '取消', type: 'info' }
    )
  } catch {
    return
  }
  auditing.value = String(row.id)
  try {
    await adminApi.auditBot(row.id, true)
    ElMessage.success('已通过')
    load()
  } catch {
    /* 拦截器已提示 */
  } finally {
    auditing.value = ''
  }
}

async function reject(row) {
  let reason = ''
  try {
    const result = await ElMessageBox.prompt('请填写驳回原因，创建者会看到这条说明', '驳回机器人', {
      confirmButtonText: '驳回',
      cancelButtonText: '取消',
      inputPlaceholder: '例如：系统提示词包含违规内容',
      inputValidator: (value) => (value && value.trim() ? true : '驳回原因不能为空')
    })
    reason = (result.value || '').trim()
  } catch {
    return
  }
  auditing.value = String(row.id)
  try {
    await adminApi.auditBot(row.id, false, reason)
    ElMessage.success('已驳回')
    load()
  } catch {
    /* 拦截器已提示 */
  } finally {
    auditing.value = ''
  }
}

/**
 * 删除机器人。任何状态都能删 —— 机器人可能因 Key 失效、提示词违规
 * 或创建者长期不上线而必须下架，不该被「审核状态」卡住。
 */
async function remove(row) {
  try {
    await ElMessageBox.confirm(
      `删除后「${row.name}」在评论区留下的历史回复也会一并消失，且不可恢复。确定删除吗？`,
      '删除机器人',
      { confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning' }
    )
  } catch {
    return
  }
  try {
    await adminApi.deleteBot(row.id)
    ElMessage.success('已删除')
    load()
  } catch {
    /* 拦截器已提示 */
  }
}

onMounted(load)
</script>

<template>
  <div class="bot-admin">
    <div class="bn-card filter-bar">
      <span class="tip">审批状态</span>
      <el-radio-group v-model="auditStatus" @change="load">
        <el-radio-button :value="0">待审核</el-radio-button>
        <el-radio-button :value="1">已通过</el-radio-button>
        <el-radio-button :value="2">已驳回</el-radio-button>
      </el-radio-group>
      <el-button class="refresh-btn" @click="load">
        <el-icon><Refresh /></el-icon>
      </el-button>
    </div>

    <el-table v-loading="loading" :data="list" class="bn-card table" border row-key="id">
      <!-- 展开行：审核要看的是配置本身，不是名字 -->
      <el-table-column type="expand">
        <template #default="{ row }">
          <div class="detail">
            <div class="detail-item">
              <span class="detail-label">系统提示词</span>
              <pre class="detail-value pre">{{ row.systemPrompt || '（未填写，使用默认身份）' }}</pre>
            </div>
            <div class="detail-grid">
              <div class="detail-item">
                <span class="detail-label">API Key</span>
                <span class="detail-value code">{{ row.apiKeySet ? row.apiKeyMasked : '未配置' }}</span>
              </div>
              <div class="detail-item">
                <span class="detail-label">接口地址</span>
                <span class="detail-value code">{{ row.baseUrl }}</span>
              </div>
              <div class="detail-item">
                <span class="detail-label">温度</span>
                <span class="detail-value">{{ row.temperature ?? '—' }}</span>
              </div>
              <div class="detail-item">
                <span class="detail-label">最大 token</span>
                <span class="detail-value">{{ row.maxTokens ?? '—' }}</span>
              </div>
            </div>
          </div>
        </template>
      </el-table-column>

      <el-table-column label="机器人" min-width="200">
        <template #default="{ row }">
          <div class="bot-cell">
            <BnAvatar :src="row.avatar" :name="row.name" :size="36" :linkable="false" />
            <div class="bot-cell-text">
              <span class="bot-cell-name">{{ row.name }}</span>
              <div class="bot-cell-tags">
                <el-tag size="small" effect="plain" type="info">
                  {{ row.providerLabel || row.provider }}
                </el-tag>
                <el-tag size="small" effect="plain" type="info">{{ row.model }}</el-tag>
              </div>
            </div>
          </div>
        </template>
      </el-table-column>

      <el-table-column label="创建者" width="140">
        <template #default="{ row }">
          <span class="bn-text-sub">{{ row.ownerName || '—' }}</span>
        </template>
      </el-table-column>

      <el-table-column label="简介" min-width="200">
        <template #default="{ row }">
          <span class="bn-text-sub">{{ row.description || '—' }}</span>
        </template>
      </el-table-column>

      <el-table-column label="回复数" width="90" align="center">
        <template #default="{ row }">
          <span class="bn-text-sub">{{ row.replyCount || 0 }}</span>
        </template>
      </el-table-column>

      <el-table-column label="提交时间" width="150">
        <template #default="{ row }">
          <span class="bn-text-muted time">{{ formatDateTime(row.updateTime || row.createTime) }}</span>
        </template>
      </el-table-column>

      <el-table-column label="状态" width="110" align="center">
        <template #default="{ row }">
          <el-tag :type="auditType(row.auditStatus)" size="small" effect="light">
            {{ auditText(row.auditStatus) }}
          </el-tag>
        </template>
      </el-table-column>

      <el-table-column label="驳回原因" min-width="150">
        <template #default="{ row }">
          <span class="bn-text-muted">{{ row.rejectReason || '—' }}</span>
        </template>
      </el-table-column>

      <el-table-column label="操作" width="180" fixed="right">
        <template #default="{ row }">
          <div class="ops">
            <template v-if="row.auditStatus === 0">
              <el-button
                link
                size="small"
                type="primary"
                :loading="auditing === String(row.id)"
                @click="approve(row)"
              >
                通过
              </el-button>
              <el-button
                link
                size="small"
                type="danger"
                :loading="auditing === String(row.id)"
                @click="reject(row)"
              >
                驳回
              </el-button>
              <el-button link size="small" type="danger" @click="remove(row)">删除</el-button>
            </template>
            <template v-else>
              <span class="bn-text-muted done-tip">已处理</span>
              <!-- 「已处理」是说明文字，删除是危险操作，两者必须拉开距离，避免误点 -->
              <el-button link size="small" type="danger" class="del-btn" @click="remove(row)">
                删除
              </el-button>
            </template>
          </div>
        </template>
      </el-table-column>

      <template #empty>
        <div class="bn-empty">没有符合条件的机器人</div>
      </template>
    </el-table>
  </div>
</template>

<style scoped>
.bot-admin {
  display: flex;
  flex-direction: column;
}

.filter-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 14px;
}

.filter-bar .tip {
  font-size: 13px;
  color: var(--bn-text-sub);
}

.refresh-btn {
  margin-left: auto;
}

.table {
  border-radius: var(--bn-radius);
  overflow: hidden;
}

.bot-cell {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.bot-cell-text {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}

.bot-cell-name {
  font-size: 13.5px;
  font-weight: 600;
}

.bot-cell-tags {
  display: flex;
  align-items: center;
  gap: 5px;
  flex-wrap: wrap;
}

.time {
  font-size: 12px;
}

/* ---------- 操作列：删除是危险操作，必须和其他内容拉开距离 ---------- */
.ops {
  display: flex;
  align-items: center;
  gap: 4px;
}

.ops .done-tip {
  margin-right: 14px;
  font-size: 12.5px;
}

.ops .del-btn {
  /* 未处理状态下与「驳回」之间也要有明确间隔，防止点错 */
  margin-left: 8px;
}

/* ---------- 展开行的配置详情 ---------- */
.detail {
  padding: 6px 12px 12px 46px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.detail-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px 24px;
}

.detail-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}

.detail-label {
  font-size: 11.5px;
  color: var(--bn-text-muted);
}

.detail-value {
  font-size: 12.5px;
  color: var(--bn-text);
}

.detail-value.code {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 12px;
  word-break: break-all;
}

.pre {
  margin: 0;
  padding: 8px 10px;
  border-radius: 8px;
  background: #faf8f6;
  border: 1px solid var(--bn-border);
  font-family: inherit;
  font-size: 12.5px;
  line-height: 1.65;
  white-space: pre-wrap;
  word-break: break-word;
}
</style>
