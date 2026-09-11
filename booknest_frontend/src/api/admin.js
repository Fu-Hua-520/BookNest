import http from '@/utils/request'

/**
 * 管理后台接口集合
 * 管理端令牌头由 axios 拦截器统一注入（admin-authentication）
 */

/* ------------------------- 认证 ------------------------- */

/**
 * 管理员登录 → UserLoginVO
 * 独立于用户端 /user/login：令牌由管理端密钥签发，仅管理端拦截器认
 * @param {{email:string, password:string}} data
 */
export function adminLogin(data) {
  return http.post('/admin/login', data)
}

/* ------------------------- 帖子管理 ------------------------- */

/** 分页查询帖子（管理视角，含未审核） → PageInfo<Post> */
export function listPosts(params) {
  return http.get('/admin/post/list', { params })
}

/**
 * 帖子审核
 * @param {string} id 帖子ID
 * @param {{auditStatus:number, auditReason?:string}} data
 */
export function auditPost(id, data) {
  return http.post(`/admin/post/${id}/audit`, data)
}

/** 设置/取消置顶 */
export function setPostTop(id, isTop) {
  return http.post(`/admin/post/${id}/top`, null, { params: { isTop } })
}

/** 上架/下架 */
export function setPostStatus(id, status) {
  return http.post(`/admin/post/${id}/status`, null, { params: { status } })
}

/* ------------------------- 书籍管理 ------------------------- */

/** 分页查询书籍 → PageInfo<Book> */
export function listBooks(params) {
  return http.get('/admin/book/list', { params })
}

/** 新增书籍 */
export function createBook(data) {
  return http.post('/admin/book', data)
}

/** 编辑书籍 */
export function updateBook(id, data) {
  return http.put(`/admin/book/${id}`, data)
}

/** 删除书籍 */
export function deleteBook(id) {
  return http.delete(`/admin/book/${id}`)
}

/* ------------------------- 分类管理 ------------------------- */

/** 完整分类树（含禁用分类） → AdminCategoryVO[] */
export function listCategoryTree() {
  return http.get('/admin/category/tree')
}

/** 新增分类 → 新分类 id */
export function createCategory(data) {
  return http.post('/admin/category', data)
}

/** 更新分类 */
export function updateCategory(id, data) {
  return http.put(`/admin/category/${id}`, data)
}

/** 删除分类（有子分类或被帖子引用时后端拒绝） */
export function deleteCategory(id) {
  return http.delete(`/admin/category/${id}`)
}

/* ------------------------- 标签管理 ------------------------- */

/** 分页查询标签（含禁用标签） → PageInfo<Tag> */
export function listTagPage(params) {
  return http.get('/admin/tag/list', { params })
}

/** 新增标签 → 新标签 id */
export function createTag(data) {
  return http.post('/admin/tag', data)
}

/** 更新标签（重命名 / 启用禁用） */
export function updateTag(id, data) {
  return http.put(`/admin/tag/${id}`, data)
}

/** 删除标签（已被帖子引用时后端拒绝） */
export function deleteTag(id) {
  return http.delete(`/admin/tag/${id}`)
}

/* ------------------------- 用户管理 ------------------------- */

/** 分页查询用户 → PageInfo<UserAdminVO> */
export function listUsers(params) {
  return http.get('/admin/user/list', { params })
}

/** 启用/禁用用户（status：1 正常，0 禁用） */
export function updateUserStatus(id, status) {
  return http.post(`/admin/user/${id}/status`, null, { params: { status } })
}

/** 调整角色（USER / ADMIN） */
export function updateUserRole(id, role) {
  return http.post(`/admin/user/${id}/role`, null, { params: { role } })
}

/* ------------------------- AI 额度 ------------------------- */

/** 查询指定用户剩余额度 → number */
export function getUserQuota(userId) {
  return http.get(`/admin/ai-quota/${userId}`)
}

/** 设置指定用户额度 */
export function setUserQuota(userId, quota) {
  return http.post(`/admin/ai-quota/${userId}`, null, { params: { quota } })
}

/** 重置指定用户额度 */
export function resetUserQuota(userId) {
  return http.delete(`/admin/ai-quota/${userId}`)
}

/* ------------------------- 向量库 ------------------------- */

/** 手动重建 RAG 索引：清空集合后重新写入点赞量前 N 的热门帖 → 重建统计 */
export function rebuildRagIndex() {
  return http.post('/admin/embedding/rebuild', null, { timeout: 600000 })
}
