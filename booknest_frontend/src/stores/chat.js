import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { ChatSocket } from '@/utils/websocket'
import { useUserStore } from './user'

/**
 * 聊天连接与事件总线
 *
 * 为什么放在 store 而不是组件里：
 * 「全局左侧栏里的聊天面板 AppSidebar/ChatPanel」和「独立聊天页 ChatView」可能同时存在，
 * 各自持有 WebSocket 会开出两条连接、同一条消息被判重逻辑处理两遍。
 * 这里集中持有一条连接，把入站帧通过 onEvent 分发给订阅者（组件），
 * 组件只负责「这条消息跟我当前打开的渠道是否一致」这类展示层判断。
 */
export const useChatStore = defineStore('chat', () => {
  /** 与后端的私信 WebSocket 是否已连上 */
  const socketReady = ref(false)

  /** 入站帧订阅者 */
  const listeners = new Set()
  let socket = null

  const connected = computed(() => socketReady.value)

  function emit(payload) {
    for (const listener of [...listeners]) {
      try {
        listener(payload)
      } catch (err) {
        // 单个订阅者报错不能拖垮其他订阅者（否则一条消息就把整条链路打断）
        console.error('[chat] 事件处理失败', err)
      }
    }
  }

  /**
   * 订阅入站帧
   * @param {(payload: object) => void} listener
   * @returns {() => void} 取消订阅
   */
  function onEvent(listener) {
    listeners.add(listener)
    return () => listeners.delete(listener)
  }

  /** 建立连接（幂等：已连上或正在重连时直接返回） */
  function connect() {
    if (socket) return
    const userStore = useUserStore()
    if (!userStore.isLoggedIn) return

    socket = new ChatSocket({
      onOpen: () => {
        socketReady.value = true
      },
      onClose: () => {
        socketReady.value = false
      },
      onMessage: (payload) => emit(payload)
    })
    socket.connect()
  }

  /** 断开连接（退出登录时调用，避免拿旧令牌一直重连） */
  function disconnect() {
    if (socket) {
      socket.close()
      socket = null
    }
    socketReady.value = false
  }

  /**
   * 发送上行帧
   * @param {object} payload 帧体（type/receiverId|groupId/content/msgType）
   * @returns {boolean} 是否已发出（连接未就绪时为 false）
   */
  function send(payload) {
    if (!socket) return false
    return socket.send(payload)
  }

  return { socketReady, connected, connect, disconnect, send, onEvent }
})
