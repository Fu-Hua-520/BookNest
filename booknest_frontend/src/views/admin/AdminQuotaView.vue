<script setup>
/**
 * AI 额度管理：查询 / 设置 / 重置指定用户的每日提问额度
 */
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import * as adminApi from '@/api/admin'

/** 免费额度常量需与后端 RedisConstant.AI_QUOTA_FREE 保持一致 */
const FREE_QUOTA = 10

const userId = ref('')
const quota = ref(null)
const loading = ref(false)
const setting = ref(false)

/** 最近查询过的用户，便于重复操作 */
const history = ref([])

const setForm = reactive({ quota: FREE_QUOTA })

function quotaText(value) {
  if (value === null || value === undefined) return '—'
  if (value < 0) return '会员不限次'
  return `${value} 次`
}

async function query(userIdParam) {
  const id = (userIdParam || userId.value).trim()
  if (!id) {
    ElMessage.warning('请输入用户 ID')
    return
  }
  userId.value = id
  loading.value = true
  try {
    const result = await adminApi.getUserQuota(id)
    quota.value = result
    setForm.quota = result === -1 ? FREE_QUOTA : result
    if (!history.value.includes(id)) history.value.unshift(id)
    if (history.value.length > 8) history.value.pop()
  } catch {
    quota.value = null
  } finally {
    loading.value = false
  }
}

async function apply() {
  const id = userId.value.trim()
  if (!id) {
    ElMessage.warning('请先输入用户 ID')
    return
  }
  const value = Number(setForm.quota)
  if (!Number.isFinite(value)) {
    ElMessage.warning('请输入有效的额度数值')
    return
  }

  setting.value = true
  try {
    await adminApi.setUserQuota(id, value)
    ElMessage.success(`已将额度设置为 ${value} 次`)
    await query(id)
  } finally {
    setting.value = false
  }
}

async function reset() {
  const id = userId.value.trim()
  if (!id) {
    ElMessage.warning('请先输入用户 ID')
    return
  }
  setting.value = true
  try {
    await adminApi.resetUserQuota(id)
    ElMessage.success('额度已重置为默认值')
    await query(id)
  } finally {
    setting.value = false
  }
}

onMounted(() => {
  // 无默认用户，等待管理员输入
})
</script>

<template>
  <div class="quota-page">
    <div class="bn-card query-card">
      <h2 class="card-title">查询用户额度</h2>
      <p class="bn-text-muted card-desc">
        额度存储在 Redis 的 ai:quota:{userId} 中，每日零点自动重置为 {{ FREE_QUOTA }} 次；
        会员账号返回 -1 表示不限次。
      </p>

      <div class="query-row">
        <el-input
          v-model="userId"
          placeholder="输入用户 UUID"
          clearable
          @keyup.enter="query()"
        >
          <template #prefix><el-icon><User /></el-icon></template>
        </el-input>
        <el-button type="primary" :loading="loading" @click="query()">
          <el-icon style="margin-right: 4px"><Search /></el-icon>查询
        </el-button>
      </div>

      <div v-if="history.length" class="history">
        <span class="bn-text-muted history-label">最近查询：</span>
        <button
          v-for="id in history"
          :key="id"
          class="history-chip"
          :title="id"
          @click="query(id)"
        >
          {{ id.slice(0, 8) }}…
        </button>
      </div>
    </div>

    <!-- 额度结果 -->
    <div v-if="quota !== null && !loading" class="bn-card result-card">
      <div class="result-head">
        <div>
          <p class="result-label">当前剩余额度</p>
          <p class="result-value" :class="{ unlimited: quota === -1, exhausted: quota === 0 }">
            {{ quotaText(quota) }}
          </p>
        </div>
        <el-tag :type="quota === -1 ? 'warning' : quota === 0 ? 'danger' : 'success'" effect="plain">
          {{ quota === -1 ? '会员账号' : quota === 0 ? '已用完' : '正常' }}
        </el-tag>
      </div>

      <p class="result-user bn-text-muted">用户 ID：{{ userId }}</p>

      <el-divider />

      <h3 class="sub-title">调整额度</h3>
      <div class="adjust-row">
        <el-input-number v-model="setForm.quota" :min="0" :max="9999" :step="5" />
        <el-button type="primary" :loading="setting" @click="apply">设置额度</el-button>
        <el-button :loading="setting" @click="reset">
          <el-icon style="margin-right: 3px"><RefreshLeft /></el-icon>重置为默认
        </el-button>
      </div>
      <p class="bn-text-muted adjust-tip">
        设置为 0 表示当日不可提问；重置会清除 Redis 中的额度键，用户下次提问时重新初始化为
        {{ FREE_QUOTA }} 次。
      </p>
    </div>

    <div v-else-if="!loading" class="bn-card empty-card">
      <el-icon :size="32" color="#c8bdb1"><MagicStick /></el-icon>
      <p class="bn-mt-12 bn-text-muted">输入用户 ID 查询其 AI 提问额度</p>
    </div>
  </div>
</template>

<style scoped>
.quota-page {
  display: flex;
  flex-direction: column;
  gap: 14px;
  max-width: 720px;
}

.card-title {
  font-size: 15.5px;
  margin-bottom: 5px;
}

.card-desc {
  font-size: 12.5px;
  line-height: 1.7;
  margin-bottom: 16px;
}

.query-row {
  display: flex;
  gap: 10px;
}

.query-row :deep(.el-input) {
  flex: 1;
}

.history {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 7px;
  margin-top: 12px;
}

.history-label {
  font-size: 12.5px;
}

.history-chip {
  border: 1px solid var(--bn-border);
  background: #faf8f5;
  border-radius: 999px;
  padding: 2px 10px;
  font-size: 12px;
  color: var(--bn-text-sub);
  cursor: pointer;
  font-family: 'JetBrains Mono', Menlo, Consolas, monospace;
}

.history-chip:hover {
  border-color: var(--bn-primary);
  color: var(--bn-primary);
}

.result-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.result-label {
  font-size: 12.5px;
  color: var(--bn-text-muted);
}

.result-value {
  font-size: 27px;
  font-weight: 700;
  color: var(--bn-primary);
  line-height: 1.3;
}

.result-value.unlimited {
  color: #c8783c;
}

.result-value.exhausted {
  color: #d0524a;
}

.result-user {
  font-size: 12px;
  margin-top: 5px;
  word-break: break-all;
}

.sub-title {
  font-size: 14px;
  margin-bottom: 11px;
}

.adjust-row {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.adjust-tip {
  font-size: 12px;
  line-height: 1.7;
  margin-top: 10px;
}

.empty-card {
  text-align: center;
  padding: 44px 20px;
}
</style>
