import http from '@/utils/request'

/**
 * 书吧社区化接口：关注 / 等级 / 称号 / 吧务
 *
 * ⚠️ 全部接口都需要登录（/bar 不在「公开只读放行」前缀里）。
 * 未登录时不要调 getMembership，否则会直接 401 —— 前端在书吧页用
 * `userStore.isLoggedIn` 挡一层，游客看到的就是「登录后可关注」。
 */

/** 我在某吧的身份与等级 → BarMemberVO */
export function getMembership(barId) {
  return http.get(`/bar/${barId}/membership`)
}

/** 我关注的书吧（侧边栏「我关注的吧」）→ BarVO[]，按关注时间倒序 */
export function listMyBars() {
  return http.get('/bar/my-bars')
}

/** 关注书吧（关注即入吧）→ 最新 BarMemberVO */
export function followBar(barId) {
  return http.post(`/bar/${barId}/follow`)
}

/** 取消关注（退吧，等级经验一并作废）→ 最新 BarMemberVO */
export function unfollowBar(barId) {
  return http.delete(`/bar/${barId}/follow`)
}

/** 管理员列表 → BarModeratorVO[] */
export function listModerators(barId) {
  return http.get(`/bar/${barId}/moderators`)
}

/** 任命管理员（仅吧主）→ 最新管理员列表 */
export function addModerator(barId, userId) {
  return http.post(`/bar/${barId}/moderators`, { userId })
}

/** 撤销管理员（仅吧主）→ 最新管理员列表 */
export function removeModerator(barId, userId) {
  return http.delete(`/bar/${barId}/moderators/${userId}`)
}

/** 等级称号列表 → BarLevelTitleVO[]（没设过称号的等级不会出现） */
export function listTitles(barId) {
  return http.get(`/bar/${barId}/titles`)
}

/**
 * 整批重设等级称号（仅吧主）→ 最新称号列表
 * @param {string} barId 书吧ID
 * @param {Array<{level:number, title:string}>} titles 传空数组表示清空全部称号
 */
export function setTitles(barId, titles) {
  return http.put(`/bar/${barId}/titles`, titles || [])
}

/**
 * 隐藏 / 恢复吧内帖子（吧主或管理员）
 * @param {boolean} visible true-恢复 false-隐藏
 */
export function setPostVisible(barId, postId, visible) {
  return http.put(`/bar/${barId}/posts/${postId}/visible`, { visible })
}
