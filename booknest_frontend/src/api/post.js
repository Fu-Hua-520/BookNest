import http from '@/utils/request'

/**
 * 分页查询帖子列表
 * @param {{categoryId?:string, tagId?:string, auditStatus?:number, page?:number, pageSize?:number}} params
 * @returns {Promise<Array>} PostVO[]
 */
export function listPosts(params) {
  return http.get('/post/list', { params })
}

/** 帖子详情 → PostDetailVO（含 content / auditStatus） */
export function getPostDetail(id) {
  return http.get(`/post/${id}`)
}

/** 发布帖子 → PostVO */
export function publishPost(data) {
  return http.post('/post', data)
}

/** 更新帖子 */
export function updatePost(id, data) {
  return http.put(`/post/${id}`, data)
}

/** 删除帖子 */
export function deletePost(id) {
  return http.delete(`/post/${id}`)
}

/* ------------------------- 互动 ------------------------- */

/** 发布评论（replyId 为被回复的评论 ID，用于楼中楼） */
export function publishComment(postId, data) {
  return http.post(`/post/${postId}/comment`, data)
}

/** 评论列表 → CommentVO[] */
export function listComments(postId) {
  return http.get(`/post/${postId}/comment`)
}

/** 删除评论 */
export function deleteComment(commentId) {
  return http.delete(`/post/comment/${commentId}`)
}

/** 评论点赞/取消 → { liked, likeCount } */
export function toggleCommentLike(commentId) {
  return http.post(`/post/comment/${commentId}/like`)
}

/** 帖子点赞/取消 → { liked, likeCount } */
export function togglePostLike(postId) {
  return http.post(`/post/${postId}/like`)
}

/** 帖子点赞状态 → boolean */
export function getPostLikeStatus(postId) {
  return http.get(`/post/${postId}/like/status`)
}

/** 帖子收藏/取消 → { collected, collectCount } */
export function togglePostCollect(postId) {
  return http.post(`/post/${postId}/collect`)
}

/** 帖子收藏状态 → boolean */
export function getPostCollectStatus(postId) {
  return http.get(`/post/${postId}/collect/status`)
}
