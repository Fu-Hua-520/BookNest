<script setup>
/**
 * 数据加载态包装：加载中骨架 / 出错 / 空数据 / 正常内容
 */
defineProps({
  loading: { type: Boolean, default: false },
  error: { type: String, default: '' },
  empty: { type: Boolean, default: false },
  emptyText: { type: String, default: '这里还没有内容' },
  skeletonRows: { type: Number, default: 3 }
})
</script>

<template>
  <div v-if="loading" class="bn-state">
    <el-skeleton :rows="skeletonRows" animated />
  </div>
  <el-alert
    v-else-if="error"
    :title="error"
    type="error"
    :closable="false"
    show-icon
    class="bn-state"
  />
  <div v-else-if="empty" class="bn-empty">
    <el-icon :size="34" color="#c8bdb1"><DocumentDelete /></el-icon>
    <p class="bn-mt-12">{{ emptyText }}</p>
    <slot name="empty-action" />
  </div>
  <slot v-else />
</template>

<style scoped>
.bn-state {
  background: var(--bn-surface);
  border: 1px solid var(--bn-border);
  border-radius: var(--bn-radius);
  padding: 20px;
}
</style>
