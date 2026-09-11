import http from '@/utils/request'

/** 会话列表 → ChatConversationVO[] */
export function listConversations() {
  return http.get('/chat/conversations')
}

/** 获取或创建与目标用户的会话 → 会话 ID */
export function getOrCreateConversation(targetUserId) {
  return http.post(`/chat/conversations/with/${targetUserId}`)
}

/** 会话消息 → ChatMsgVO[] */
export function listMessages(convId, page = 1, pageSize = 50) {
  return http.get(`/chat/conversations/${convId}/messages`, { params: { page, pageSize } })
}

/** 标记会话已读 */
export function markConversationRead(convId) {
  return http.post(`/chat/conversations/${convId}/read`)
}

/** 未读私信总数 → number */
export function getUnreadCount() {
  return http.get('/chat/unread-count')
}

/** 目标用户是否在线 → boolean */
export function isUserOnline(userId) {
  return http.get(`/chat/user/${userId}/online`)
}
