import http from '@/utils/request'

/** 用户注册：{ phone?, email, password } */
export function register(data) {
  return http.post('/user/register', data)
}

/** 用户登录：{ email, password } → UserLoginVO（含 token） */
export function login(data) {
  return http.post('/user/login', data)
}

/** 上传文件到 OSS → 返回可访问 URL（服务端中转，需登录） */
export function uploadFile(file) {
  const formData = new FormData()
  formData.append('file', file)
  return http.post('/common/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 60000
  })
}

/**
 * 更新我的资料（昵称 / 头像）
 * 目标用户由后端从登录态取，不接受传 userId；字段「传了才改」。
 * @param {{username?:string, avatar?:string}} data
 * @returns {Promise<object>} UserProfileVO
 */
export function updateProfile(data) {
  return http.put('/user/profile', data)
}

/**
 * 搜索用户（吧主任命管理员时挑人用）
 * @param {string} keyword 昵称或账号关键字
 * @param {number} limit 最多条数
 * @returns {Promise<Array<{id:string, username:string, avatar:string}>>}
 */
export function searchUsers(keyword, limit = 10) {
  return http.get('/user/search', { params: { keyword, limit } })
}

/* ------------------------- 浏览历史 ------------------------- */

/** 我的浏览历史 → BrowseHistoryVO[]（按最后浏览时间倒序，同帖只占一行） */
export function listMyHistory(page = 1, pageSize = 20) {
  return http.get('/user/history', { params: { page, pageSize } })
}

/** 删除单条浏览记录（按帖子 ID） */
export function removeHistory(postId) {
  return http.delete(`/user/history/${postId}`)
}

/** 清空浏览历史 */
export function clearHistory() {
  return http.delete('/user/history')
}
