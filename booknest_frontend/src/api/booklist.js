import http from '@/utils/request'

/**
 * 分页查询公开书单列表
 * @param {{userId?:string, page?:number, pageSize?:number}} params
 * @returns {Promise<Array>} BooklistVO[]
 */
export function listBooklists(params) {
  return http.get('/booklist/list', { params })
}

/** 查询我的书单 → BooklistVO[] */
export function listMyBooklists() {
  return http.get('/booklist/my')
}

/** 书单详情 → BooklistDetailVO（含 items） */
export function getBooklistDetail(id) {
  return http.get(`/booklist/${id}`)
}

/** 创建书单（items 为可选初始条目） → BooklistVO */
export function createBooklist(data) {
  return http.post('/booklist', data)
}

/** 更新书单（仅标题/简介/封面/可见性） */
export function updateBooklist(id, data) {
  return http.put(`/booklist/${id}`, data)
}

/** 删除书单 */
export function deleteBooklist(id) {
  return http.delete(`/booklist/${id}`)
}

/** 向书单添加条目 { bookId, note? } → BooklistItemVO */
export function addBooklistItem(id, data) {
  return http.post(`/booklist/${id}/item`, data)
}

/** 移除书单条目 */
export function removeBooklistItem(id, itemId) {
  return http.delete(`/booklist/${id}/item/${itemId}`)
}
