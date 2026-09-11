import http from '@/utils/request'

/** AI 会话列表 → AIConversation[] */
export function listConversations() {
  return http.get('/ai/conversations')
}

/** 会话消息 → AIMessage[] */
export function listConversationMessages(id) {
  return http.get(`/ai/conversations/${id}/messages`)
}

/** 更新会话标题 */
export function updateConversationTitle(id, title) {
  return http.post(`/ai/conversations/${id}/title`, null, { params: { title } })
}

/** 删除会话 */
export function deleteConversation(id) {
  return http.delete(`/ai/conversations/${id}`)
}

/**
 * 当前用户剩余提问额度
 * @returns {Promise<number>} 剩余次数，-1 表示会员不限
 */
export function getQuota() {
  return http.get('/ai/quota')
}
