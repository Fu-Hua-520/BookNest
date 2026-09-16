import http from '@/utils/request'

/* ------------------------- 私聊 ------------------------- */

/** 会话列表 → ChatConversationVO[] */
export function listConversations() {
  return http.get('/chat/conversations')
}

/** 获取或创建与目标用户的会话 → 会话 ID */
export function getOrCreateConversation(targetUserId) {
  return http.post(`/chat/conversations/with/${targetUserId}`)
}

/**
 * 渠道消息 → ChatMsgVO[]
 * 私聊传会话 ID，群聊传群 ID —— 后端两个渠道共用同一张消息表，接口也共用。
 */
export function listMessages(channelId, page = 1, pageSize = 50) {
  return http.get(`/chat/conversations/${channelId}/messages`, { params: { page, pageSize } })
}

/** 标记私聊会话已读 */
export function markConversationRead(convId) {
  return http.post(`/chat/conversations/${convId}/read`)
}

/** 未读私信总数（含群聊未读）→ number */
export function getUnreadCount() {
  return http.get('/chat/unread-count')
}

/** 我（作为群主）名下所有群待审批的入群申请总数 → number（红点轮询用） */
export function getGroupRequestCount() {
  return http.get('/chat/groups/pending-request-count')
}

/** 目标用户是否在线 → boolean */
export function isUserOnline(userId) {
  return http.get(`/chat/user/${userId}/online`)
}

/* ------------------------- 群聊 ------------------------- */

/** 我加入的群 → ChatGroupVO[] */
export function listMyGroups() {
  return http.get('/chat/groups')
}

/** 创建群聊：{ name, notice?, avatar?, memberIds: string[] } → ChatGroupVO */
export function createGroup(data) {
  return http.post('/chat/groups', data)
}

/** 群详情（含成员）→ ChatGroupVO */
export function getGroupDetail(groupId) {
  return http.get(`/chat/groups/${groupId}`)
}

/** 群成员 → GroupMemberVO[] */
export function listGroupMembers(groupId) {
  return http.get(`/chat/groups/${groupId}/members`)
}

/** 邀请入群 → 更新后的 ChatGroupVO */
export function inviteGroupMembers(groupId, userIds) {
  return http.post(`/chat/groups/${groupId}/members`, { userIds })
}

/** 退群（传自己的 userId）或群主移除成员 */
export function removeGroupMember(groupId, userId) {
  return http.delete(`/chat/groups/${groupId}/members/${userId}`)
}

/** 修改群资料（仅群主）：{ name?, notice?, avatar? } */
export function updateGroup(groupId, data) {
  return http.put(`/chat/groups/${groupId}`, data)
}

/** 解散群（仅群主） */
export function dissolveGroup(groupId) {
  return http.delete(`/chat/groups/${groupId}`)
}

/** 标记群已读（推进已读位点） */
export function markGroupRead(groupId) {
  return http.post(`/chat/groups/${groupId}/read`)
}

/* ------------------------- 群邀请 / 加入申请 -------------------------
   进群一律要经过一次明确的确认动作：
     邀请别人 → 对方收到待确认邀请，同意后才入群
     主动加入 → 自己提交申请，群主通过后才入群
   下面这些接口就是这条链路上的各环节。
   ------------------------------------------------------------------ */

/** 我收到的待确认入群邀请 → GroupInvitationVO[] */
export function listMyInvitations() {
  return http.get('/chat/groups/invitations')
}

/** 同意 / 拒绝入群邀请 → 同意时返回群 ID，拒绝返回 null */
export function handleInvitation(invitationId, accept) {
  return http.post(`/chat/groups/invitations/${invitationId}/handle`, { accept })
}

/** 我发起且仍待审批的加入申请对应的群 ID → string[] */
export function listMyPendingJoins() {
  return http.get('/chat/groups/my-pending-joins')
}

/** 发现群聊：按群名搜索我还没加入的群 → ChatGroupVO[] */
export function searchGroups(keyword) {
  return http.get('/chat/groups/search', { params: { keyword } })
}

/** 申请加入某个群（待群主审批） */
export function applyJoinGroup(groupId, message) {
  return http.post(`/chat/groups/${groupId}/join`, { message })
}

/** 群主查看某个群待审批的加入申请 → GroupInvitationVO[] */
export function listJoinRequests(groupId) {
  return http.get(`/chat/groups/${groupId}/requests`)
}

/** 群主通过入群申请 */
export function approveJoinRequest(invitationId) {
  return http.post(`/chat/groups/requests/${invitationId}/approve`)
}

/** 群主拒绝入群申请 */
export function rejectJoinRequest(invitationId) {
  return http.post(`/chat/groups/requests/${invitationId}/reject`)
}
