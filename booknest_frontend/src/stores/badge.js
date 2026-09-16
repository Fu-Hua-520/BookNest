import { defineStore } from 'pinia'
import { ref } from 'vue'
import * as notificationApi from '@/api/notification'
import * as chatApi from '@/api/chat'
import { useUserStore } from './user'

/** 未读角标轮询间隔（毫秒） */
const POLL_INTERVAL = 60000

/**
 * 全局未读态：通知未读数 + 私信未读数 + 入群申请待审批数，驱动导航栏红点
 */
export const useBadgeStore = defineStore('badge', () => {
  const notificationUnread = ref(0)
  const chatUnread = ref(0)
  /** 我（作为群主）名下待审批的入群申请数 —— 有值时消息卡片要点红点 */
  const groupRequestCount = ref(0)
  let timer = null

  async function refresh() {
    const userStore = useUserStore()
    if (!userStore.isLoggedIn) {
      notificationUnread.value = 0
      chatUnread.value = 0
      groupRequestCount.value = 0
      return
    }
    // 三个角标相互独立，单个失败不影响另一个
    const [notificationResult, chatResult, requestResult] = await Promise.allSettled([
      notificationApi.getUnreadCount(),
      chatApi.getUnreadCount(),
      chatApi.getGroupRequestCount()
    ])
    if (notificationResult.status === 'fulfilled') {
      notificationUnread.value = Number(notificationResult.value || 0)
    }
    if (chatResult.status === 'fulfilled') {
      chatUnread.value = Number(chatResult.value || 0)
    }
    if (requestResult.status === 'fulfilled') {
      groupRequestCount.value = Number(requestResult.value || 0)
    }
  }

  function startPolling() {
    stopPolling()
    refresh()
    timer = setInterval(refresh, POLL_INTERVAL)
  }

  function stopPolling() {
    if (timer) {
      clearInterval(timer)
      timer = null
    }
  }

  function clearNotification() {
    notificationUnread.value = 0
  }

  function clearChat() {
    chatUnread.value = 0
  }

  /** 群主处理完入群申请后由聊天面板回调，红点立即消失 */
  function clearGroupRequests() {
    groupRequestCount.value = 0
  }

  return {
    notificationUnread,
    chatUnread,
    groupRequestCount,
    refresh,
    startPolling,
    stopPolling,
    clearNotification,
    clearChat,
    clearGroupRequests
  }
})
