import http from '@/utils/request'

/** 通知列表 → NotificationVO[] */
export function listNotifications() {
  return http.get('/notification/list')
}

/** 未读通知数 → number */
export function getUnreadCount() {
  return http.get('/notification/unread-count')
}

/** 标记单条已读 */
export function markRead(id) {
  return http.put(`/notification/${id}/read`)
}

/** 全部标记已读 */
export function markAllRead() {
  return http.put('/notification/read-all')
}

/** 删除通知 */
export function deleteNotification(id) {
  return http.delete(`/notification/${id}`)
}
