<script setup>
/**
 * 私信页（整页版聊天）
 *
 * 聊天本体（会话列表 / 私信 / 群聊 / 图片消息 / 已读）都在 components/chat/ChatPanel.vue，
 * 本页只负责：把 URL 上的意图翻译成 ChatPanel 的初始渠道。
 *   /chat?conv=<会话ID>        直接打开某个私聊
 *   /chat?group=<群ID>         直接打开某个群聊
 *   /chat?user=<用户ID>        与某人发起/继续私聊
 *   /chat?createGroup=1        进入即弹出「创建群聊」
 */
import { onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import * as chatApi from '@/api/chat'
import { useUserStore } from '@/stores/user'
import ChatPanel from '@/components/chat/ChatPanel.vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

/** 传给 ChatPanel 的初始渠道 */
const initial = ref(null)
const autoCreateGroup = ref(false)

async function resolveTarget() {
  const conv = String(route.query.conv || '')
  const group = String(route.query.group || '')
  const user = String(route.query.user || '')
  autoCreateGroup.value = String(route.query.createGroup || '') === '1'

  // 建群是「一次性动作」，消费掉就把 query 里的标记抹掉：
  // 否则用户关掉弹窗后，任何一次 query 变化都会把它重新弹出来。
  if (autoCreateGroup.value) {
    const rest = { ...route.query }
    delete rest.createGroup
    router.replace({ name: 'chat', query: rest })
  }

  if (group) {
    initial.value = { type: 'GROUP', id: group }
    return
  }
  if (conv) {
    initial.value = { type: 'PRIVATE', id: conv }
    return
  }
  if (user && user !== String(userStore.userId)) {
    // 从书友主页「私信」跳过来：先拿到（或创建）会话 ID
    try {
      const convId = await chatApi.getOrCreateConversation(user)
      initial.value = { type: 'PRIVATE', id: String(convId) }
      router.replace({ name: 'chat', query: { conv: String(convId) } })
    } catch {
      initial.value = null
    }
    return
  }
  initial.value = null
}

onMounted(resolveTarget)
watch(() => route.query, resolveTarget)
</script>

<template>
  <div class="bn-container chat-page">
    <header class="page-head">
      <div>
        <h1 class="bn-page-title">消息</h1>
        <p class="bn-page-sub">私信与群聊，支持发送图片</p>
      </div>
    </header>

    <div class="chat-shell">
      <ChatPanel :initial="initial" :auto-create-group="autoCreateGroup" />
    </div>
  </div>
</template>

<style scoped>
.chat-page {
  display: flex;
  flex-direction: column;
}

.page-head {
  margin-bottom: 12px;
}

.page-head .bn-page-sub {
  margin-bottom: 0;
}

.chat-shell {
  height: calc(100vh - 220px);
  min-height: 520px;
}

@media (max-width: 880px) {
  .chat-shell {
    height: auto;
    min-height: 520px;
  }
}
</style>
