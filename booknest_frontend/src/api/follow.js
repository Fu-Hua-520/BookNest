import http from '@/utils/request'

/** 关注用户 */
export function follow(userId) {
  return http.post(`/follow/${userId}`)
}

/** 取消关注 */
export function unfollow(userId) {
  return http.delete(`/follow/${userId}`)
}

/** 是否已关注 → boolean */
export function isFollowing(userId) {
  return http.get(`/follow/${userId}/status`)
}

/** 关注列表 → FollowVO[] */
export function listFollowing(userId) {
  return http.get(`/follow/${userId}/following`)
}

/** 粉丝列表 → FollowVO[] */
export function listFollowers(userId) {
  return http.get(`/follow/${userId}/followers`)
}
