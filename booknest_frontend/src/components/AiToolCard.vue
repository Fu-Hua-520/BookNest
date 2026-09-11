<script setup>
/**
 * AI 工具调用卡片：展示工具名、参数、执行状态与结果条数
 */
import { computed } from 'vue'

const props = defineProps({
  toolCall: { type: Object, required: true }
})

const STATUS_MAP = {
  calling: { text: '调用中', type: 'warning', icon: 'Loading' },
  success: { text: '已完成', type: 'success', icon: 'CircleCheck' },
  failed: { text: '调用失败', type: 'danger', icon: 'CircleClose' },
  no_result: { text: '无结果', type: 'info', icon: 'QuestionFilled' }
}

const status = computed(() => STATUS_MAP[props.toolCall.status] || STATUS_MAP.calling)
const displayName = computed(
  () => props.toolCall.displayName || props.toolCall.toolName || '工具调用'
)
const resultText = computed(() => {
  if (props.toolCall.toolResult) return props.toolCall.toolResult
  if (typeof props.toolCall.resultCount === 'number') {
    return `返回 ${props.toolCall.resultCount} 条结果`
  }
  return ''
})

/** 参数可能是 JSON 字符串，尝试美化展示 */
const prettyParams = computed(() => {
  const raw = props.toolCall.parameters
  if (!raw) return ''
  try {
    return JSON.stringify(JSON.parse(raw), null, 2)
  } catch {
    return String(raw)
  }
})
</script>

<template>
  <div class="tool-card">
    <div class="tool-head">
      <el-icon class="tool-icon"><Tools /></el-icon>
      <span class="tool-name">{{ displayName }}</span>
      <el-tag :type="status.type" size="small" effect="light">
        <el-icon v-if="toolCall.status === 'calling'" class="is-loading"><Loading /></el-icon>
        {{ status.text }}
      </el-tag>
    </div>
    <div v-if="prettyParams" class="tool-params">
      <span class="params-label">参数</span>
      <code>{{ prettyParams }}</code>
    </div>
    <p v-if="resultText" class="tool-result">{{ resultText }}</p>
  </div>
</template>

<style scoped>
.tool-card {
  border: 1px dashed #dbcbb9;
  background: #fdfaf6;
  border-radius: var(--bn-radius-sm);
  padding: 10px 13px;
  margin: 8px 0;
  font-size: 13px;
}

.tool-head {
  display: flex;
  align-items: center;
  gap: 8px;
}

.tool-icon {
  color: var(--bn-accent);
}

.tool-name {
  font-weight: 600;
  color: #7a5330;
  flex: 1;
}

.tool-params {
  margin-top: 7px;
  display: flex;
  gap: 8px;
  align-items: flex-start;
}

.params-label {
  color: var(--bn-text-muted);
  flex-shrink: 0;
}

.tool-params code {
  font-family: 'JetBrains Mono', Menlo, Consolas, monospace;
  font-size: 12px;
  color: #6b5544;
  background: #f4ece3;
  padding: 2px 7px;
  border-radius: 4px;
  white-space: pre-wrap;
  word-break: break-all;
  max-height: 110px;
  overflow-y: auto;
  display: block;
}

.tool-result {
  margin-top: 7px;
  color: var(--bn-text-sub);
  font-size: 12.5px;
  line-height: 1.6;
}

.is-loading {
  animation: rotating 1.4s linear infinite;
}

@keyframes rotating {
  to {
    transform: rotate(360deg);
  }
}
</style>
