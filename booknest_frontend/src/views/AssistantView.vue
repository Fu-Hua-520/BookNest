<script setup>
/**
 * AI 助手：SSE 流式对话 + 工具调用卡片 + 推荐结果 + 额度显示 + 会话管理
 * 契约对齐后端 ChatStreamEvent：THINKING / TOOL_CALLING / TOOL_RESULT / MESSAGE / DONE / ERROR
 */
import { computed, nextTick, onMounted, onUnmounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as aiApi from '@/api/ai'
import { streamChat } from '@/utils/sse'
import { renderMarkdown } from '@/utils/markdown'
import { fromNow } from '@/utils/format'
import { AutoScroll } from '@/utils/autoscroll'
import AiToolCard from '@/components/AiToolCard.vue'
import AiRecommend from '@/components/AiRecommend.vue'
import BnAvatar from '@/components/BnAvatar.vue'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()

const conversations = ref([])
const activeId = ref('')
const messages = ref([])
const input = ref('')
const streaming = ref(false)
const loadingHistory = ref(false)
const useRag = ref(true)
const quota = ref(null)

const scrollerRef = ref(null)
const autoScroll = new AutoScroll()
let controller = null

const quotaText = computed(() => {
  if (quota.value === null || quota.value === undefined) return '额度加载中'
  if (quota.value < 0) return '会员不限次'
  return `今日剩余 ${quota.value} 次`
})

const quotaExhausted = computed(() => quota.value !== null && quota.value !== -1 && quota.value <= 0)

const activeTitle = computed(
  () => conversations.value.find((item) => String(item.id) === String(activeId.value))?.title || '新对话'
)

const canSend = computed(() => !streaming.value && input.value.trim() && !quotaExhausted.value)

const SUGGESTIONS = [
  '帮我找找论坛里关于《活着》的书评',
  '最近有哪些值得一读的小说书单？',
  '总结一下论坛里讨论最多的三本书',
  '推荐一本适合入门哲学的书'
]

async function loadConversations() {
  try {
    conversations.value = (await aiApi.listConversations()) || []
  } catch {
    conversations.value = []
  }
}

async function loadHistory(id) {
  if (!id) {
    messages.value = []
    return
  }
  loadingHistory.value = true
  try {
    const list = (await aiApi.listConversationMessages(id)) || []
    messages.value = list.map((item) => ({
      id: item.id,
      role: item.role === 'assistant' ? 'assistant' : 'user',
      content: item.content || '',
      isError: item.isError === 1,
      toolCalls: parseJson(item.toolCalls),
      recommendations: parseJson(item.recommendations),
      createTime: item.createTime
    }))
    scrollToBottom(true)
  } catch {
    messages.value = []
  } finally {
    loadingHistory.value = false
  }
}

function parseJson(raw) {
  if (!raw) return null
  try {
    return typeof raw === 'string' ? JSON.parse(raw) : raw
  } catch {
    return null
  }
}

async function loadQuota() {
  try {
    quota.value = await aiApi.getQuota()
  } catch {
    quota.value = null
  }
}

async function switchConversation(id) {
  if (streaming.value) {
    ElMessage.info('请等待当前回答完成')
    return
  }
  activeId.value = id
  await loadHistory(id)
}

function newConversation() {
  if (streaming.value) return
  activeId.value = ''
  messages.value = []
  input.value = ''
}

function scrollToBottom(force = false) {
  nextTick(() => {
    const el = scrollerRef.value
    if (!el) return
    if (force || autoScroll.shouldStick) {
      el.scrollTop = el.scrollHeight
    }
  })
}

function onScroll() {
  const el = scrollerRef.value
  if (!el) return
  autoScroll.update(el)
}

/** 发送消息并消费 SSE 流 */
async function send(text) {
  const content = (text || input.value).trim()
  if (!content || streaming.value) return

  if (quotaExhausted.value) {
    ElMessage.warning('今日免费额度已用完，明天再来吧')
    return
  }

  messages.value.push({ role: 'user', content })
  input.value = ''
  scrollToBottom(true)

  // 占位助手消息，流式追加内容
  const assistantMsg = {
    role: 'assistant',
    content: '',
    thinking: false,
    toolCalls: [],
    recommendations: [],
    isError: false
  }
  messages.value.push(assistantMsg)

  streaming.value = true
  controller = new AbortController()

  try {
    await streamChat(
      useRag.value ? 'chat/rag' : 'chat/stream',
      {
        message: content,
        conversationId: activeId.value || undefined
      },
      (event) => handleEvent(event, assistantMsg),
      controller.signal
    )
  } catch (err) {
    if (err.name !== 'AbortError') {
      assistantMsg.isError = true
      assistantMsg.content = assistantMsg.content || err.message || '对话失败，请稍后重试'
    }
  } finally {
    streaming.value = false
    assistantMsg.thinking = false
    controller = null
    scrollToBottom()
    loadQuota()
    loadConversations()
  }
}

function handleEvent(event, assistantMsg) {
  switch (event.type) {
    case 'THINKING':
      assistantMsg.thinking = true
      break

    case 'TOOL_CALLING':
      if (event.toolCall) {
        assistantMsg.toolCalls.push({ ...event.toolCall })
        // 首次工具调用回填会话 ID
        if (event.toolCall.sessionId && !activeId.value) activeId.value = event.toolCall.sessionId
      }
      break

    case 'TOOL_RESULT':
      if (event.toolCall) {
        const index = assistantMsg.toolCalls.findIndex(
          (item) => item.toolName === event.toolCall.toolName
        )
        if (index >= 0) assistantMsg.toolCalls[index] = { ...assistantMsg.toolCalls[index], ...event.toolCall }
        else assistantMsg.toolCalls.push({ ...event.toolCall })
      }
      break

    case 'MESSAGE':
      assistantMsg.thinking = false
      assistantMsg.content += event.content || ''
      if (event.sessionId && !activeId.value) activeId.value = event.sessionId
      scrollToBottom()
      break

    case 'DONE':
      assistantMsg.thinking = false
      if (event.recommendations?.length) {
        assistantMsg.recommendations = event.recommendations
      }
      if (event.sessionId) activeId.value = event.sessionId
      break

    case 'ERROR':
      assistantMsg.thinking = false
      assistantMsg.isError = true
      if (!assistantMsg.content) assistantMsg.content = event.error || '对话出现异常'
      break

    default:
      break
  }
}

function stopStreaming() {
  controller?.abort()
  streaming.value = false
}

async function renameConversation(conv) {
  try {
    const { value } = await ElMessageBox.prompt('输入新的会话标题', '重命名会话', {
      inputValue: conv.title || '',
      confirmButtonText: '保存',
      cancelButtonText: '取消',
      inputValidator: (v) => (v && v.trim() ? true : '标题不能为空')
    })
    await aiApi.updateConversationTitle(conv.id, value.trim())
    ElMessage.success('已重命名')
    loadConversations()
  } catch {
    /* 取消 */
  }
}

async function removeConversation(conv) {
  try {
    await ElMessageBox.confirm('删除后无法恢复，确定删除这个会话吗？', '删除会话', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消'
    })
  } catch {
    return
  }
  await aiApi.deleteConversation(conv.id)
  ElMessage.success('已删除')
  if (String(conv.id) === String(activeId.value)) newConversation()
  loadConversations()
}

function onKeydown(event) {
  // Enter 发送，Shift+Enter 换行
  if (event.key === 'Enter' && !event.shiftKey) {
    event.preventDefault()
    if (canSend.value) send()
  }
}

onMounted(() => {
  loadConversations()
  loadQuota()
})

onUnmounted(() => {
  controller?.abort()
})
</script>

<template>
  <div class="bn-container assistant-page">
    <!-- 会话侧栏 -->
    <aside class="conv-sidebar bn-card">
      <el-button class="new-btn" type="primary" @click="newConversation" :disabled="streaming">
        <el-icon style="margin-right: 4px"><Plus /></el-icon>新建对话
      </el-button>

      <div class="conv-list">
        <div
          v-for="conv in conversations"
          :key="conv.id"
          :class="['conv-item', { active: String(conv.id) === String(activeId) }]"
          @click="switchConversation(conv.id)"
        >
          <div class="conv-body">
            <p class="conv-title bn-ellipsis-1">{{ conv.title || '未命名对话' }}</p>
            <p class="conv-time">{{ fromNow(conv.updateTime || conv.createTime) }}</p>
          </div>
          <el-dropdown trigger="click" @command="(cmd) => cmd === 'rename' ? renameConversation(conv) : removeConversation(conv)">
            <el-icon class="conv-more" @click.stop><MoreFilled /></el-icon>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="rename">
                  <el-icon><EditPen /></el-icon>重命名
                </el-dropdown-item>
                <el-dropdown-item command="delete">
                  <el-icon><Delete /></el-icon>删除
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>

        <p v-if="!conversations.length" class="bn-text-muted conv-empty">还没有历史对话</p>
      </div>
    </aside>

    <!-- 对话主区 -->
    <section class="chat-area bn-card">
      <header class="chat-head">
        <div class="head-left">
          <span class="ai-badge"><el-icon><MagicStick /></el-icon></span>
          <div>
            <p class="head-title">{{ activeTitle }}</p>
            <p class="head-sub">基于论坛内容检索的智能助手</p>
          </div>
        </div>
        <div class="head-right">
          <el-tooltip content="开启后将在论坛帖子中检索相关内容" placement="bottom">
            <el-switch
              v-model="useRag"
              active-text="RAG 检索"
              inline-prompt
              :disabled="streaming"
            />
          </el-tooltip>
          <el-tag :type="quotaExhausted ? 'danger' : 'info'" effect="plain" size="small">
            {{ quotaText }}
          </el-tag>
        </div>
      </header>

      <!-- 消息区 -->
      <div ref="scrollerRef" class="scroller" @scroll="onScroll">
        <div v-if="loadingHistory" class="loading-history">
          <el-skeleton :rows="4" animated />
        </div>

        <!-- 欢迎态 -->
        <div v-else-if="!messages.length" class="welcome">
          <span class="welcome-icon"><el-icon :size="26"><MagicStick /></el-icon></span>
          <h2>你好，我是 BookNest 助手</h2>
          <p class="bn-text-muted">
            我可以帮你在论坛里找书评、查书籍信息、整理书单，也能回答读书相关的问题
          </p>
          <div class="suggestions">
            <button
              v-for="(item, index) in SUGGESTIONS"
              :key="index"
              class="suggestion"
              @click="send(item)"
            >
              {{ item }}
            </button>
          </div>
        </div>

        <!-- 消息列表 -->
        <div v-for="(msg, index) in messages" :key="msg.id || index" :class="['msg', msg.role]">
          <BnAvatar
            v-if="msg.role === 'user'"
            :src="userStore.avatar"
            :name="userStore.username"
            :size="32"
            :linkable="false"
          />
          <span v-else class="ai-avatar"><el-icon><MagicStick /></el-icon></span>

          <div class="msg-body">
            <!-- 工具调用卡片 -->
            <AiToolCard v-for="(tool, ti) in msg.toolCalls || []" :key="ti" :tool-call="tool" />

            <!-- 思考中 -->
            <div v-if="msg.thinking && !msg.content" class="thinking">
              <span class="dot" />
              <span class="dot" />
              <span class="dot" />
              <span class="thinking-text">正在思考…</span>
            </div>

            <!-- 正文 -->
            <div
              v-if="msg.role === 'assistant'"
              :class="['bn-markdown', 'bubble', { error: msg.isError }]"
              v-html="renderMarkdown(msg.content)"
            />
            <div v-else class="bubble user-bubble">{{ msg.content }}</div>

            <!-- 推荐结果 -->
            <AiRecommend v-if="msg.recommendations?.length" :items="msg.recommendations" />
          </div>
        </div>
      </div>

      <!-- 输入区 -->
      <footer class="composer">
        <el-input
          v-model="input"
          type="textarea"
          :rows="3"
          resize="none"
          placeholder="问点什么… Enter 发送，Shift + Enter 换行"
          :disabled="streaming"
          @keydown="onKeydown"
        />
        <div class="composer-foot">
          <span class="bn-text-muted composer-tip">
            {{ quotaExhausted ? '今日免费额度已用完' : '回答基于论坛内容与公开知识，请自行判断准确性' }}
          </span>
          <el-button v-if="streaming" type="danger" plain @click="stopStreaming">
            <el-icon style="margin-right: 4px"><VideoPause /></el-icon>停止生成
          </el-button>
          <el-button v-else type="primary" :disabled="!canSend" @click="send()">
            <el-icon style="margin-right: 4px"><Promotion /></el-icon>发送
          </el-button>
        </div>
      </footer>
    </section>
  </div>
</template>

<style scoped>
.assistant-page {
  display: grid;
  grid-template-columns: 244px minmax(0, 1fr);
  gap: 18px;
  height: calc(100vh - 160px);
  min-height: 520px;
}

/* 侧栏 */
.conv-sidebar {
  padding: 14px;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.new-btn {
  width: 100%;
  margin-bottom: 12px;
}

.conv-list {
  flex: 1;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.conv-item {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 10px;
  border-radius: 8px;
  cursor: pointer;
  transition: background 0.15s ease;
}

.conv-item:hover {
  background: #f7f4f0;
}

.conv-item.active {
  background: var(--bn-primary-soft);
}

.conv-body {
  flex: 1;
  min-width: 0;
}

.conv-title {
  font-size: 13px;
  font-weight: 500;
}

.conv-item.active .conv-title {
  color: var(--bn-primary);
}

.conv-time {
  font-size: 11px;
  color: var(--bn-text-muted);
  margin-top: 1px;
}

.conv-more {
  color: var(--bn-text-muted);
  padding: 3px;
  border-radius: 4px;
}

.conv-more:hover {
  background: #ebe4dc;
  color: var(--bn-primary);
}

.conv-empty {
  font-size: 12.5px;
  text-align: center;
  padding: 20px 0;
}

/* 对话主区 */
.chat-area {
  display: flex;
  flex-direction: column;
  padding: 0;
  overflow: hidden;
}

.chat-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  padding: 13px 18px;
  border-bottom: 1px solid var(--bn-border);
  background: #fdfcfa;
  flex-wrap: wrap;
}

.head-left {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.ai-badge {
  width: 34px;
  height: 34px;
  border-radius: 10px;
  background: linear-gradient(140deg, #a9754f, #8b5e3c);
  color: #fff;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.head-title {
  font-size: 14.5px;
  font-weight: 600;
}

.head-sub {
  font-size: 12px;
  color: var(--bn-text-muted);
}

.head-right {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 12.5px;
}

/* 消息区 */
.scroller {
  flex: 1;
  overflow-y: auto;
  padding: 20px 18px;
  scroll-behavior: smooth;
}

.loading-history {
  max-width: 620px;
  margin: 0 auto;
}

.welcome {
  text-align: center;
  padding: 40px 20px;
  max-width: 620px;
  margin: 0 auto;
}

.welcome-icon {
  width: 54px;
  height: 54px;
  border-radius: 16px;
  background: var(--bn-primary-soft);
  color: var(--bn-primary);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 14px;
}

.welcome h2 {
  font-size: 18px;
  margin-bottom: 7px;
}

.welcome p {
  font-size: 13.5px;
  line-height: 1.7;
}

.suggestions {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 9px;
  margin-top: 22px;
}

.suggestion {
  text-align: left;
  padding: 10px 13px;
  border: 1px solid var(--bn-border);
  border-radius: var(--bn-radius-sm);
  background: #fff;
  font-size: 13px;
  color: var(--bn-text-sub);
  cursor: pointer;
  transition: all 0.15s ease;
  font-family: inherit;
}

.suggestion:hover {
  border-color: var(--bn-primary);
  color: var(--bn-primary);
  background: #fffdfa;
}

/* 单条消息 */
.msg {
  display: flex;
  gap: 11px;
  margin-bottom: 20px;
  align-items: flex-start;
}

.msg.user {
  flex-direction: row-reverse;
}

.ai-avatar {
  width: 32px;
  height: 32px;
  border-radius: 10px;
  background: linear-gradient(140deg, #a9754f, #8b5e3c);
  color: #fff;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.msg-body {
  min-width: 0;
  max-width: 78%;
}

.msg.user .msg-body {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
}

.bubble {
  padding: 11px 15px;
  border-radius: 10px;
  background: #faf8f5;
  border: 1px solid var(--bn-border);
}

.bubble.error {
  background: #fef3f2;
  border-color: #fbd9d5;
}

.user-bubble {
  background: var(--bn-primary);
  color: #fff;
  border: none;
  white-space: pre-wrap;
  word-break: break-word;
  font-size: 14px;
  line-height: 1.7;
}

.msg.user .bn-markdown {
  text-align: left;
}

/* 思考动画 */
.thinking {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 10px 14px;
  background: #faf8f5;
  border: 1px solid var(--bn-border);
  border-radius: 10px;
}

.dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--bn-primary-light);
  animation: bounce 1.3s infinite ease-in-out;
}

.dot:nth-child(2) {
  animation-delay: 0.16s;
}

.dot:nth-child(3) {
  animation-delay: 0.32s;
}

.thinking-text {
  font-size: 12.5px;
  color: var(--bn-text-muted);
  margin-left: 4px;
}

@keyframes bounce {
  0%,
  70%,
  100% {
    transform: translateY(0);
    opacity: 0.5;
  }
  35% {
    transform: translateY(-4px);
    opacity: 1;
  }
}

/* 输入区 */
.composer {
  border-top: 1px solid var(--bn-border);
  padding: 13px 18px 15px;
  background: #fdfcfa;
}

.composer :deep(.el-textarea__inner) {
  border-radius: var(--bn-radius-sm);
  font-size: 14px;
  line-height: 1.7;
}

.composer-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-top: 9px;
}

.composer-tip {
  font-size: 12px;
}

@media (max-width: 900px) {
  .assistant-page {
    grid-template-columns: 1fr;
    height: auto;
  }
  .conv-sidebar {
    max-height: 200px;
  }
  .chat-area {
    height: 640px;
  }
  .suggestions {
    grid-template-columns: 1fr;
  }
  .msg-body {
    max-width: 88%;
  }
}
</style>
