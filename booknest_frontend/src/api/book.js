import http from '@/utils/request'

/**
 * 搜索书籍（按书名关键字）
 * @param {string} keyword
 * @returns {Promise<Array>} Book[]
 */
export function searchBooks(keyword) {
  return http.get('/book/search', { params: { keyword } })
}

/** 根据 ID 查询书籍 → Book */
export function getBookDetail(id) {
  return http.get(`/book/${id}`)
}

/** 根据 ISBN 外部补全书籍信息 → Book（含 source 标记） */
export function fetchBookByIsbn(isbn) {
  return http.get(`/book/isbn/${isbn}`)
}
