import { USER_TOKEN_KEY } from './request'

/** 解析 AI 事件类型枚举（与后端 ChatStreamEvent.EventType 对齐） */
export const AiEventType = {
  THINKING: 'THINKING',
  TOOL_CALLING: 'TOOL_CALLING',
  TOOL_RESULT: 'TOOL_RESULT',
  MESSAGE: 'MESSAGE',
  DONE: 'DONE',
  ERROR: 'ERROR'
}

const API_BASE = import.meta.env.VITE_API_BASE || '/api'

/**
 * SSE 流式对话客户端
 *
 * 后端为 POST + text/event-stream，无法使用 EventSource，因此用原生 fetch +
 * ReadableStream 手动解析 SSE 帧（按 \n\n 切分，逐行解析 data:）。
 *
 * @param {'chat/stream'|'chat/rag'} path  对话端点
 * @param {{message:string, conversationId?:string, model?:string}} payload 请求体
 * @param {(event:object)=>void} onEvent   每个事件的回调
 * @param {AbortSignal} [signal]           取消信号
 */
export async function streamChat(path, payload, onEvent, signal) {
  const token = localStorage.getItem(USER_TOKEN_KEY)
  const response = await fetch(`${API_BASE}/ai/${path}`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json;charset=utf-8',
      Accept: 'text/event-stream',
      ...(token ? { authentication: token } : {})
    },
    body: JSON.stringify(payload),
    signal
  })

  if (response.status === 401) {
    throw new Error('登录已过期，请重新登录')
  }
  if (!response.ok || !response.body) {
    throw new Error(`对话服务异常（HTTP ${response.status}）`)
  }

  const reader = response.body.getReader()
  const decoder = new TextDecoder('utf-8')
  let buffer = ''

  try {
    while (true) {
      const { done, value } = await reader.read()
      if (done) break
      buffer += decoder.decode(value, { stream: true })

      // SSE 帧以空行分隔
      let boundary = buffer.indexOf('\n\n')
      while (boundary !== -1) {
        const rawFrame = buffer.slice(0, boundary)
        buffer = buffer.slice(boundary + 2)
        const data = extractData(rawFrame)
        if (data) {
          try {
            onEvent(JSON.parse(data))
          } catch {
            // 忽略无法解析的帧，避免中断整个流
          }
        }
        boundary = buffer.indexOf('\n\n')
      }
    }
    // 处理末尾残留帧
    const tail = extractData(buffer)
    if (tail) {
      try {
        onEvent(JSON.parse(tail))
      } catch {
        /* ignore */
      }
    }
  } finally {
    reader.releaseLock?.()
  }
}

/** 从单个 SSE 帧中取出 data 字段内容（兼容 \r\n 换行） */
function extractData(frame) {
  const lines = frame.split(/\r?\n/)
  const dataLines = []
  for (const line of lines) {
    if (line.startsWith('data:')) {
      dataLines.push(line.slice(5).replace(/^ /, ''))
    }
  }
  return dataLines.length ? dataLines.join('\n') : null
}
