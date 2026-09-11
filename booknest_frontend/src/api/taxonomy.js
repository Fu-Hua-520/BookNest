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
