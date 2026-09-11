<script setup>
/**
 * 私信页：会话列表 + 消息收发（WebSocket 实时 + HTTP 历史回补）
 */
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import * as chatApi from '@/api/chat'
import { ChatSocket } from '@/utils/websocket'
import { AutoScroll } from '@/utils/autoscroll'
import { fromNow } from '@/utils/format'
import { useBadgeStore } from '@/stores/badge'
import { useUserStore } from '@/stores/user'
import BnAvatar from '@/components/BnAvatar.vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const badgeStore = useBadgeStore()

const conversations = ref([])
const activeId = ref(String(route.query.conv || ''))
const messages = ref([])
const input = ref('')

const loadingConv = ref(false)
const loadingMsg = ref(false)
const sending = ref(false)
const online = ref(false)

const scrollerRef = ref(null)
const autoScroll = new AutoScroll()
let socket = null

const activeConv = computed(() =>
  conversations.value.find((item) => String(item.id) === String(activeId.value))
)
const peerName = computed(() => activeConv.value?.peerName || '私信')
const peerId = computed(() => activeConv.value?.peerId || '')

async function loadConversations() {
  loadingConv.value = true
  try {
    conversations.value = (await chatApi.listConversations()) || []
    // 若 URL 指定了会话，确保其存在
    if (activeId.value && !conversations.value.some((c) => String(c.id) === activeId.value)) {
      conversations.value.unshift({
        id: activeId.value,
        peerId: '',
        peerName: '新对话',
        lastMessage: '',
        lastMsgAt: '',
        unreadCount: 0
      })
    }
  } catch {
    conversations.value = []
  } finally {
    loadingConv.value = false
  }
}

async function loadMessages(id) {
  if (!id) {
    messages.value = []
    return
  }
  loadingMsg.value = true
  try {
    messages.value = (await chatApi.listMessages(id, 1, 100)) || []
    scrollToBottom(true)
    await chatApi.markConversationRead(id).catch(() => {})
    badgeStore.refresh()
  } catch {
    messages.value = []
  } finally {
    loadingMsg.value = false
  }
}

async function checkOnline() {
  if (!peerId.value) {
    online.value = false
    return
  }
  try {
    online.value = await chatApi.isUserOnline(peerId.value)
  } catch {
    online.value = false
  }
}

function scrollToBottom(force = false) {
  nextTick(() => {
    const el = scrollerRef.value
    if (!el) return
    if (force || autoScroll.shouldStick) el.scrollTop = el.scrollHeight
  })
}

function onScroll() {
  const el = scrollerRef.value
  if (el) autoScroll.update(el)
}

function selectConversation(id) {
  if (String(id) === String(activeId.value)) return
  activeId.value = String(id)
  router.replace({ name: 'chat', query: { conv: activeId.value } })
}

/** 建立 WebSocket 连接并处理入站消息 */
function setupSocket() {
  socket = new ChatSocket({
    onOpen: () => {
      online.value = true
    },
    onClose: () => {
      online.value = false
    },
    onMessage: (payload) => {
      // 后端推送的消息体字段与 ChatMsgVO 对齐
      const msg = payload?.data || payload
      if (!msg || !msg.content) return

      const convId = String(msg.conversationId || '')
      const isCurrent = convId && convId === String(activeId.value)

      if (isCurrent) {
        // 避免与本地已追加的消息重复
        const duplicated = messages.value.some(
          (item) => item.id && msg.id && String(item.id) === String(msg.id)
        )
        if (!duplicated) {
          messages.value.push(msg)
          scrollToBottom()
        }
        chatApi.markConversationRead(convId).catch(() => {})
      }
      loadConversations()
      badgeStore.refresh()
    }
  })
  socket.connect()
}

/** 发送消息：优先走 WebSocket，未连接时回退 HTTP 会话创建 + 提示 */
async function send() {
  const content = input.value.trim()
  if (!content) return
  if (!activeId.value) {
    ElMessage.info('请先从会话列表中选择一位书友')
    return
  }

  sending.value = true
  try {
    const payload = {
      type: 'CHAT',
      conversationId: activeId.value,
      receiverId: peerId.value,
      content
    }

    if (!socket?.connected) {
      ElMessage.warning('实时连接尚未建立，请稍后重试')
      return
    }

    socket.send(payload)
    // 本地乐观追加，标记为自己发送
    messages.value.push({
      id: `local-${Date.now()}`,
      conversationId: activeId.value,
      senderId: userStore.userId,
      content,
      isRead: 0,
      createTime: new Date().toISOString(),
      isMine: true
    })
    input.value = ''
    scrollToBottom(true)
    loadConversations()
  } finally {
    sending.value = false
  }
}

/** 从某个用户发起对话 */
async function startWithUser(userId) {
  if (!userId) return
  const convId = await chatApi.getOrCreateConversation(userId)
  await loadConversations()
  activeId.value = String(convId)
  router.replace({ name: 'chat', query: { conv: activeId.value } })
}

watch(activeId, async (id) => {
  await loadMessages(id)
  checkOnline()
})

onMounted(async () => {
  setupSocket()
  await loadConversations()

  // 支持 ?user=xxx 直接发起对话
  const target = String(route.query.user || '')
  if (target) {
    await startWithUser(target)
  } else if (!activeId.value && conversations.value.length) {
    activeId.value = String(conversations.value[0].id)
  } else if (activeId.value) {
    await loadMessages(activeId.value)
  }
})

onUnmounted(() => {
  socket?.close()
})
</script>

<template>
  <div class="bn-container chat-page">
    <!-- 会话列表 -->
    <aside class="conv-panel bn-card">
      <div class="panel-head">
        <h2>私信</h2>
        <el-tag :type="online ? 'success' : 'info'" size="small" effect="plain">
          {{ online ? '连接正常' : '未连接' }}
        </el-tag>
      </div>

      <el-skeleton v-if="loadingConv" :rows="5" animated />

      <div v-else-if="!conversations.length" class="bn-empty panel-empty">
        <el-icon :size="30" color="#c8bdb1"><ChatDotRound /></el-icon>
        <p class="bn-mt-12">还没有会话</p>
        <p class="bn-text-muted empty-tip">在书友主页点击「私信」开始聊天</p>
      </div>

      <div v-else class="conv-list">
        <div
          v-for="conv in conversations"
          :key="conv.id"
          :class="['conv-item', { active: String(conv.id) === String(activeId) }]"
          @click="selectConversation(conv.id)"
        >
          <BnAvatar :src="conv.peerAvatar" :name="conv.peerName" :size="38" :linkable="false" />
          <div class="conv-body">
            <div class="conv-top">
              <span class="conv-name bn-ellipsis-1">{{ conv.peerName || '书友' }}</span>
              <span class="conv-time">{{ fromNow(conv.lastMsgAt) }}</span>
            </div>
            <p class="conv-last bn-ellipsis-1">{{ conv.lastMessage || '暂无消息' }}</p>
          </div>
          <el-badge
            v-if="conv.unreadCount"
            :value="conv.unreadCount"
            :max="99"
            class="conv-badge"
          />
        </div>
      </div>
    </aside>

    <!-- 消息面板 -->
    <section class="msg-panel bn-card">
      <template v-if="activeId">
        <header class="msg-head">
          <BnAvatar :src="activeConv?.peerAvatar" :name="peerName" :size="34" :user-id="peerId" />
          <div class="msg-head-body">
            <router-link v-if="peerId" :to="`/user/${peerId}`" class="msg-peer">
              {{ peerName }}
            </router-link>
            <span v-else class="msg-peer">{{ peerName }}</span>
            <span :class="['online-dot', { on: online }]">
              {{ online ? '在线' : '离线' }}
            </span>
          </div>
        </header>

        <div ref="scrollerRef" class="msg-scroller" @scroll="onScroll">
          <el-skeleton v-if="loadingMsg" :rows="4" animated />

          <template v-else>
            <div v-if="!messages.length" class="msg-empty">
              <p class="bn-text-muted">还没有消息，打个招呼吧</p>
            </div>

            <div
              v-for="(msg, index) in messages"
              :key="msg.id || index"
              :class="['msg-row', { mine: msg.isMine ?? String(msg.senderId) === String(userStore.userId) }]"
            >
              <BnAvatar
                :src="msg.senderAvatar || (String(msg.senderId) === String(userStore.userId) ? userStore.avatar : activeConv?.peerAvatar)"
                :name="msg.senderName || (String(msg.senderId) === String(userStore.userId) ? userStore.username : peerName)"
                :size="32"
                :linkable="false"
              />
              <div class="msg-bubble-wrap">
                <div class="msg-bubble">{{ msg.content }}</div>
                <span class="msg-time">{{ fromNow(msg.createTime) }}</span>
              </div>
            </div>
          </template>
        </div>

        <footer class="msg-composer">
          <el-input
            v-model="input"
            type="textarea"
            :rows="2"
            resize="none"
            placeholder="输入消息，Enter 发送"
            @keydown.enter.exact.prevent="send"
          />
          <el-button type="primary" :loading="sending" @click="send">发送</el-button>
        </footer>
      </template>

      <div v-else class="bn-empty no-conv">
        <el-icon :size="36" color="#c8bdb1"><ChatLineRound /></el-icon>
        <p class="bn-mt-12">选择左侧会话开始聊天</p>
      </div>
    </section>
  </div>
</template>

<style scoped>
.chat-page {
  display: grid;
  grid-template-columns: 290px minmax(0, 1fr);
  gap: 18px;
  height: calc(100vh - 160px);
  min-height: 520px;
}

.conv-panel {
  padding: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 16px;
  border-bottom: 1px solid var(--bn-border);
}

.panel-head h2 {
  font-size: 15px;
}

.panel-empty {
  padding: 40px 16px;
}

.empty-tip {
  font-size: 12px;
  margin-top: 4px;
}

.conv-list {
  flex: 1;
  overflow-y: auto;
  padding: 8px;
}

.conv-item {
  position: relative;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px;
  border-radius: 8px;
  cursor: pointer;
  transition: background 0.15s ease;
}

.conv-item:hover {
  background: #f9f6f3;
}

.conv-item.active {
  background: var(--bn-primary-soft);
}

.conv-body {
  flex: 1;
  min-width: 0;
}

.conv-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.conv-name {
  font-size: 13.5px;
  font-weight: 600;
  flex: 1;
  min-width: 0;
}

.conv-time {
  font-size: 11px;
  color: var(--bn-text-muted);
  flex-shrink: 0;
}

.conv-last {
  font-size: 12.5px;
  color: var(--bn-text-muted);
  margin-top: 3px;
}

.conv-badge {
  position: absolute;
  right: 8px;
  bottom: 8px;
}

/* 消息面板 */
.msg-panel {
  padding: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.msg-head {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 11px 18px;
  border-bottom: 1px solid var(--bn-border);
  background: #fdfcfa;
}

.msg-head-body {
  display: flex;
  align-items: center;
  gap: 10px;
}

.msg-peer {
  font-size: 14.5px;
  font-weight: 600;
}

a.msg-peer:hover {
  color: var(--bn-primary);
}

.online-dot {
  font-size: 12px;
  color: var(--bn-text-muted);
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.online-dot::before {
  content: '';
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #c3c3c3;
}

.online-dot.on {
  color: #4a9c6d;
}

.online-dot.on::before {
  background: #4a9c6d;
}

.msg-scroller {
  flex: 1;
  overflow-y: auto;
  padding: 18px;
}

.msg-empty {
  text-align: center;
  padding: 50px 0;
  font-size: 13px;
}

.msg-row {
  display: flex;
  gap: 10px;
  margin-bottom: 16px;
  align-items: flex-start;
}

.msg-row.mine {
  flex-direction: row-reverse;
}

.msg-bubble-wrap {
  max-width: 68%;
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.msg-row.mine .msg-bubble-wrap {
  align-items: flex-end;
}

.msg-bubble {
  padding: 9px 13px;
  border-radius: 10px;
  background: #faf8f5;
  border: 1px solid var(--bn-border);
  font-size: 14px;
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-word;
}

.msg-row.mine .msg-bubble {
  background: var(--bn-primary);
  color: #fff;
  border-color: var(--bn-primary);
}

.msg-time {
  font-size: 11px;
  color: var(--bn-text-muted);
}

.msg-composer {
  display: flex;
  gap: 10px;
  align-items: flex-end;
  padding: 13px 18px;
  border-top: 1px solid var(--bn-border);
  background: #fdfcfa;
}

.msg-composer :deep(.el-textarea) {
  flex: 1;
}

.no-conv {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
}

@media (max-width: 880px) {
  .chat-page {
    grid-template-columns: 1fr;
    height: auto;
  }
  .conv-panel {
    max-height: 240px;
  }
  .msg-panel {
    height: 560px;
  }
}
</style>
