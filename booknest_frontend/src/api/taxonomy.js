import http from '@/utils/request'

/** 分类树 → CategoryVO[]（两级，含 children） */
export function getCategoryTree() {
  return http.get('/category/tree')
}

/** 全部标签 → Tag[] */
export function listTags() {
  return http.get('/tag/list')
}

/** 热门标签 → Tag[] */
export function getHotTags(limit = 20) {
  return http.get('/tag/hot', { params: { limit } })
}

/** 搜索标签 → Tag[] */
export function searchTags(keyword) {
  return http.get('/tag/search', { params: { keyword } })
}

/**
 * 创建一个标签（重名会直接复用已有标签，不报错）
 * 发帖页允许用户随手输入新标签名，拿到返回值后再挂到帖子上。
 * → Tag
 */
export function createTag(name) {
  return http.post('/tag/create', { name })
}

/* ------------------------- 书吧（贴吧式「吧」）------------------------- */

/** 书吧广场：全部已通过审核的书吧（按帖数降序）→ BarVO[] */
export function listBars() {
  return http.get('/category/bars')
}

/** 热门书吧：按成员数降序前 20（服务端缓存约一天，隔天重算）→ BarVO[] */
export function listHotBars() {
  return http.get('/category/hot-bars')
}

/** 按吧名搜索可见书吧 → BarVO[] */
export function searchBars(keyword) {
  return http.get('/category/search', { params: { keyword } })
}

/** 我申请创建的书吧（含待审核 / 已驳回）→ BarVO[] */
export function listMyBarApplications() {
  return http.get('/category/my-applications')
}

/** 申请创建书吧：{ name, description? } → 新书吧ID */
export function applyBar(data) {
  return http.post('/category/apply', data)
}
