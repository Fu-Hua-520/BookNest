import { USER_TOKEN_KEY } from './request'

/**
 * 私信 WebSocket 客户端
 *
 * 握手地址：/ws/chat?token={JWT}，token 走查询参数（见后端
 * ChatWebSocketHandshakeInterceptor）。含心跳保活与指数退避重连。
 */
export class ChatSocket {
  constructor({ onMessage, onOpen, onClose } = {}) {
    this.onMessage = onMessage
    this.onOpen = onOpen
    this.onClose = onClose
    this.ws = null
    this.heartbeatTimer = null
    this.reconnectTimer = null
    this.reconnectAttempts = 0
    this.manualClosed = false
  }

  get connected() {
    return this.ws?.readyState === WebSocket.OPEN
  }

  connect() {
    const token = localStorage.getItem(USER_TOKEN_KEY)
    if (!token) return

    this.manualClosed = false
    const protocol = window.location.protocol === 'https:' ? 'wss' : 'ws'
    const url = `${protocol}://${window.location.host}/ws/chat?token=${encodeURIComponent(token)}`

    try {
      this.ws = new WebSocket(url)
    } catch {
      this.scheduleReconnect()
      return
    }

    this.ws.onopen = () => {
      this.reconnectAttempts = 0
      this.startHeartbeat()
      this.onOpen?.()
    }

    this.ws.onmessage = (event) => {
      if (event.data === 'pong') return
      try {
        this.onMessage?.(JSON.parse(event.data))
      } catch {
        this.onMessage?.({ type: 'TEXT', content: event.data })
      }
    }

    this.ws.onclose = () => {
      this.stopHeartbeat()
      this.onClose?.()
      if (!this.manualClosed) this.scheduleReconnect()
    }

    this.ws.onerror = () => {
      // onerror 后必然触发 onclose，重连逻辑统一放在 onclose
      this.ws?.close()
    }
  }

  send(payload) {
    if (!this.connected) return false
    this.ws.send(typeof payload === 'string' ? payload : JSON.stringify(payload))
    return true
  }

  startHeartbeat() {
    this.stopHeartbeat()
    this.heartbeatTimer = setInterval(() => {
      if (this.connected) this.ws.send('ping')
    }, 25000)
  }

  stopHeartbeat() {
    if (this.heartbeatTimer) {
      clearInterval(this.heartbeatTimer)
      this.heartbeatTimer = null
    }
  }

  scheduleReconnect() {
    if (this.reconnectTimer || this.manualClosed) return
    // 指数退避，上限 30 秒
    const delay = Math.min(1000 * 2 ** this.reconnectAttempts, 30000)
    this.reconnectAttempts += 1
    this.reconnectTimer = setTimeout(() => {
      this.reconnectTimer = null
      this.connect()
    }, delay)
  }

  close() {
    this.manualClosed = true
    this.stopHeartbeat()
    if (this.reconnectTimer) {
      clearTimeout(this.reconnectTimer)
      this.reconnectTimer = null
    }
    this.ws?.close()
    this.ws = null
  }
}
