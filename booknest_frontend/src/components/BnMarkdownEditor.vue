<script setup>
/**
 * 正文编辑器（工具栏 + 输入 + 分栏实时预览）
 *
 * 设计取舍：
 * - 底层仍是 Markdown（后端把正文当 .md 存 OSS），工具栏只是「语法糖按钮」，
 *   所以插入图片后拿到的就是标准 `![](url)`，预览与详情页渲染天然一致。
 * - 不做语法提示/语法说明，界面只暴露动作按钮，降低非技术用户的上手成本。
 * - 图片走 /common/upload（服务端中转 OSS，见 CommonController），
 *   支持 png/jpg/gif/webp/bmp —— GIF 会原样存下，插进正文就是动图。
 */
import { computed, nextTick, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { renderMarkdown } from '@/utils/markdown'
import { uploadFile } from '@/api/user'

const model = defineModel({ type: String, default: '' })

const props = defineProps({
  placeholder: {
    type: String,
    default: '写下你的想法…'
  },
  minRows: { type: Number, default: 16 },
  /** 是否展示分栏实时预览 */
  preview: { type: Boolean, default: true },
  /** 是否允许插入图片（未登录等场景可关掉） */
  allowImage: { type: Boolean, default: true }
})

const rendered = computed(() => renderMarkdown(model.value))
const charCount = computed(() => (model.value || '').length)

const inputRef = ref(null)
const fileInputRef = ref(null)
const uploading = ref(0)
const dragOver = ref(false)

/** 取原生 textarea，用于光标位置插入与选区操作 */
function textareaEl() {
  return inputRef.value?.textarea || null
}

/**
 * 在光标处插入文本；若存在选中内容则包裹选中内容
 * @param {string} text 要插入的文本
 * @param {{wrap?: boolean, placeholder?: string, cursorOffset?: number}} options
 */
function insertAtCursor(text, options = {}) {
  const el = textareaEl()
  const current = model.value || ''

  if (!el) {
    model.value = current + text
    return
  }

  const start = el.selectionStart ?? current.length
  const end = el.selectionEnd ?? start
  const selected = current.slice(start, end)

  let inserted = text
  let cursor = start + text.length

  if (options.wrap) {
    const body = selected || options.placeholder || ''
    inserted = text + body + text
    // 光标落在包裹体内部
    cursor = selected ? start + inserted.length : start + text.length + body.length
  } else if (options.cursorOffset != null) {
    cursor = start + options.cursorOffset
  }

  model.value = current.slice(0, start) + inserted + current.slice(end)

  nextTick(() => {
    el.focus()
    el.setSelectionRange(cursor, cursor)
  })
}

function insertLink() {
  insertAtCursor('[](https://)', { cursorOffset: 1 })
}

function insertQuote() {
  insertAtCursor('> ', {})
}

function insertCodeBlock() {
  insertAtCursor('\n```\n\n```\n', { cursorOffset: 5 })
}

function insertDivider() {
  insertAtCursor('\n\n---\n\n', {})
}

function triggerImagePick() {
  if (!props.allowImage) return
  fileInputRef.value?.click()
}

function onFilePicked(event) {
  const files = Array.from(event.target.files || [])
  // 同一张图连续选择两次也要能触发 change
  event.target.value = ''
  files.forEach(uploadAndInsert)
}

/** 校验并上传单张图片，上传完成后在光标处替换占位符 */
async function uploadAndInsert(file) {
  if (!file) return
  if (!file.type || !file.type.startsWith('image/')) {
    ElMessage.warning(`「${file.name || '该文件'}」不是图片，已跳过`)
    return
  }

  // 先落一个注释占位符，上传成功后原地替换成真实图片语法，
  // 这样用户能立刻看到「这里有东西正在传」，且不会因为上传耗时丢失插入位置。
  const token = `<!--uploading-${Date.now()}-${Math.random().toString(36).slice(2, 8)}-->`
  insertAtCursor(`\n${token}\n`, {})

  uploading.value += 1
  try {
    const url = await uploadFile(file)
    const alt = (file.name || '图片').replace(/\.[^.]+$/, '')
    model.value = (model.value || '').replace(token, `![${alt}](${url})`)
    ElMessage.success('图片已插入正文')
  } catch (err) {
    model.value = (model.value || '').replace(token, '')
    ElMessage.error(err?.message || '图片上传失败，请重试')
  } finally {
    uploading.value = Math.max(0, uploading.value - 1)
  }
}

/** 粘贴板里带图片时直接上传，不再插入 base64（体积会撑爆正文） */
function onPaste(event) {
  if (!props.allowImage) return
  const items = Array.from(event.clipboardData?.items || [])
  const images = items
    .filter((item) => item.kind === 'file' && item.type.startsWith('image/'))
    .map((item) => item.getAsFile())
    .filter(Boolean)
  if (!images.length) return
  event.preventDefault()
  images.forEach(uploadAndInsert)
}

function onDrop(event) {
  if (!props.allowImage) return
  dragOver.value = false
  const files = Array.from(event.dataTransfer?.files || [])
  const images = files.filter((file) => file.type && file.type.startsWith('image/'))
  if (!images.length) return
  event.preventDefault()
  images.forEach(uploadAndInsert)
}

function onDragOver() {
  if (props.allowImage) dragOver.value = true
}

function onDragLeave(event) {
  // 只有真正离开编辑区才取消高亮，避免掠过子元素时闪烁
  if (!event.currentTarget.contains(event.relatedTarget)) dragOver.value = false
}
</script>

<template>
  <div class="bn-editor">
    <div class="editor-pane" :class="{ 'drag-over': dragOver }">
      <!-- 工具栏：只做语法糖，底层始终是标准 Markdown -->
      <div class="pane-head toolbar">
        <div class="tool-group">
          <button type="button" class="tool-btn" title="加粗" @click="insertAtCursor('**', { wrap: true, placeholder: '加粗文字' })">
            <b>B</b>
          </button>
          <button type="button" class="tool-btn" title="行内代码" @click="insertAtCursor('`', { wrap: true, placeholder: 'code' })">
            &lt;/&gt;
          </button>
          <button type="button" class="tool-btn" title="引用" @click="insertQuote">
            <el-icon><ChatLineSquare /></el-icon>
          </button>
          <button type="button" class="tool-btn" title="代码块" @click="insertCodeBlock">
            <el-icon><Document /></el-icon>
          </button>
          <button type="button" class="tool-btn" title="链接" @click="insertLink">
            <el-icon><Link /></el-icon>
          </button>
          <button type="button" class="tool-btn" title="分割线" @click="insertDivider">
            <el-icon><Minus /></el-icon>
          </button>
        </div>

        <div class="tool-group">
          <button
            type="button"
            class="tool-btn image-btn"
            :disabled="!allowImage || uploading > 0"
            title="插入图片 / GIF 动图"
            @click="triggerImagePick"
          >
            <el-icon><Picture /></el-icon>
            <span>{{ uploading > 0 ? '上传中…' : '插入图片/动图' }}</span>
          </button>
        </div>
      </div>

      <el-input
        ref="inputRef"
        v-model="model"
        type="textarea"
        :rows="minRows"
        :placeholder="placeholder"
        resize="vertical"
        class="editor-input"
        @paste="onPaste"
        @drop="onDrop"
        @dragover.prevent="onDragOver"
        @dragleave="onDragLeave"
      />

      <div v-if="allowImage" class="editor-hint">
        支持粘贴截图、拖拽图片到此处，或点「插入图片/动图」上传本地 GIF
      </div>
    </div>

    <div v-if="preview" class="preview-pane">
      <div class="pane-head">
        <span>实时预览</span>
        <span class="char-count">{{ charCount }} 字</span>
      </div>
      <div v-if="model" class="bn-markdown preview-body" v-html="rendered" />
      <div v-else class="preview-empty">预览区域 · 左侧输入后即时渲染</div>
    </div>

    <!-- 隐藏的文件选择器：交给工具栏按钮唤起 -->
    <input
      ref="fileInputRef"
      type="file"
      accept="image/png,image/jpeg,image/gif,image/webp,image/bmp"
      multiple
      class="hidden-file"
      @change="onFilePicked"
    />
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

.editor-pane.drag-over {
  border-color: var(--bn-primary);
  box-shadow: 0 0 0 2px var(--bn-primary-soft) inset;
}

.pane-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 8px 14px;
  background: #faf8f5;
  border-bottom: 1px solid var(--bn-border);
  font-size: 12.5px;
  font-weight: 600;
  color: var(--bn-text-sub);
}

.tool-group {
  display: flex;
  align-items: center;
  gap: 4px;
}

.tool-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  height: 26px;
  padding: 0 8px;
  border: 1px solid transparent;
  border-radius: 6px;
  background: transparent;
  color: var(--bn-text-sub);
  font-family: inherit;
  font-size: 12.5px;
  cursor: pointer;
  transition: all 0.15s ease;
}

.tool-btn:hover:not(:disabled) {
  background: #fff;
  border-color: var(--bn-border);
  color: var(--bn-primary);
}

.tool-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.image-btn {
  border-color: var(--bn-border);
  background: #fff;
  color: var(--bn-text);
  font-weight: 500;
}

.char-count {
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

.editor-hint {
  padding: 6px 14px;
  border-top: 1px dashed var(--bn-border);
  background: #fdfcfa;
  font-size: 11.5px;
  color: var(--bn-text-muted);
}

.preview-body {
  padding: 14px 16px;
  min-height: 200px;
  flex: 1;
  overflow-y: auto;
}

.preview-body :deep(img) {
  max-width: 100%;
}

.preview-empty {
  padding: 40px 16px;
  text-align: center;
  color: var(--bn-text-muted);
  font-size: 13px;
  flex: 1;
}

.hidden-file {
  display: none;
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
