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
  // 后端 audit() 用 @RequestParam 接收，参数必须放 query string。
  // 放 JSON body 时 Spring 解析不到 → MissingServletRequestParameterException → 400。
  return http.post(`/admin/post/${id}/audit`, null, { params: data })
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

/* ------------------------- 书吧管理 ------------------------- */

/** 完整书吧列表（含禁用书吧） → AdminCategoryVO[] */
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

/** 书吧创建申请列表 → BarVO[]（auditStatus: 0-待审核 1-已通过 2-已驳回） */
export function listBarApplications(auditStatus = 0) {
  return http.get('/admin/category/applications', { params: { auditStatus } })
}

/**
 * 审批书吧创建申请（approve=true 通过；驳回时必须带 rejectReason）
 * @param {string} ownerId 可选：通过时指定吧主用户ID（不传维持申请人）
 */
export function auditBar(id, approve, rejectReason, ownerId) {
  return http.put(`/admin/category/${id}/audit`, {
    approve,
    rejectReason,
    ownerId: ownerId || undefined
  })
}

/* ------------------------- 书吧吧务（/admin/bar） ------------------------- */

/**
 * 书吧吧务全貌：吧主 + 管理员 + 等级称号 + 成员数 → BarManageVO
 * @param {string} barId
 */
export function getBarManage(barId) {
  return http.get(`/admin/bar/${barId}`)
}

/**
 * 任命 / 更换吧主
 * @param {string} barId
 * @param {string|null} userId 传 null / '' 表示收回吧主（变成官方吧）
 */
export function setBarOwner(barId, userId) {
  return http.put(`/admin/bar/${barId}/owner`, { userId: userId || null })
}

/** 任命管理员 → BarModeratorVO[] */
export function addBarModerator(barId, userId) {
  return http.post(`/admin/bar/${barId}/moderators`, { userId })
}

/** 撤销管理员 → BarModeratorVO[] */
export function removeBarModerator(barId, userId) {
  return http.delete(`/admin/bar/${barId}/moderators/${userId}`)
}

/**
 * 整批重设等级称号
 * @param {string} barId
 * @param {{level:number,title:string}[]} titles 传空数组表示清空
 */
export function setBarTitles(barId, titles) {
  return http.put(`/admin/bar/${barId}/titles`, titles)
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

/* ------------------------- AI 机器人审核 ------------------------- */

/**
 * AI 机器人列表 → AiBotVO[]
 * @param {number|null} auditStatus 0-待审核 1-已通过 2-已驳回；传 null 查全部
 */
export function listBots(auditStatus = 0) {
  return http.get('/admin/bot/list', { params: { auditStatus } })
}

/**
 * 审批 AI 机器人
 * @param {boolean} approve 驳回时必须带 rejectReason
 */
export function auditBot(id, approve, rejectReason) {
  return http.put(`/admin/bot/${id}/audit`, { approve, rejectReason })
}

/**
 * 删除 AI 机器人（连带删除它的历史回复）
 * 管理端不受归属限制，任何状态的机器人都能删。
 */
export function deleteBot(id) {
  return http.delete(`/admin/bot/${id}`)
}
