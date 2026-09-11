<script setup>
/**
 * Markdown 渲染与简易编辑器（分栏预览）
 */
import { computed } from 'vue'
import { renderMarkdown } from '@/utils/markdown'

const model = defineModel({ type: String, default: '' })

defineProps({
  placeholder: {
    type: String,
    default: '用 Markdown 写下你的想法…\n\n# 一级标题\n**加粗** · *斜体* · `代码`\n> 引用\n\n- 列表项'
  },
  minRows: { type: Number, default: 16 },
  /** 是否展示分栏实时预览 */
  preview: { type: Boolean, default: true }
})

const rendered = computed(() => renderMarkdown(model.value))
</script>

<template>
  <div class="bn-editor">
    <div class="editor-pane">
      <div class="pane-head">
        <span>Markdown 编辑</span>
        <span class="pane-tip">支持 Markdown 语法</span>
      </div>
      <el-input
        v-model="model"
        type="textarea"
        :rows="minRows"
        :placeholder="placeholder"
        resize="vertical"
        class="editor-input"
      />
    </div>

    <div v-if="preview" class="preview-pane">
      <div class="pane-head">
        <span>实时预览</span>
      </div>
      <div v-if="model" class="bn-markdown preview-body" v-html="rendered" />
      <div v-else class="preview-empty">预览区域 · 左侧输入后即时渲染</div>
    </div>
  </div>
</template>

<style scoped>
.bn-editor {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px;
}

.editor-pane,
.preview-pane {
  border: 1px solid var(--bn-border);
  border-radius: var(--bn-radius);
  overflow: hidden;
  background: #fff;
  display: flex;
  flex-direction: column;
}

.pane-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 14px;
  background: #faf8f5;
  border-bottom: 1px solid var(--bn-border);
  font-size: 12.5px;
  font-weight: 600;
  color: var(--bn-text-sub);
}

.pane-tip {
  font-weight: 400;
  color: var(--bn-text-muted);
}

.editor-input :deep(.el-textarea__inner) {
  border: none;
  box-shadow: none;
  border-radius: 0;
  font-family: 'JetBrains Mono', Menlo, Consolas, monospace;
  font-size: 13.5px;
  line-height: 1.75;
  padding: 14px;
}

.preview-body {
  padding: 14px 16px;
  min-height: 200px;
  flex: 1;
  overflow-y: auto;
}

.preview-empty {
  padding: 40px 16px;
  text-align: center;
  color: var(--bn-text-muted);
  font-size: 13px;
  flex: 1;
}

@media (max-width: 820px) {
  .bn-editor {
    grid-template-columns: 1fr;
  }
  .preview-pane {
    max-height: 320px;
  }
}
</style>
