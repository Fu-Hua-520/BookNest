import http from '@/utils/request'

/**
 * 评论区 AI 机器人（/bot）
 *
 * 每个机器人由用户自己创建、填入自己的 API Key（DeepSeek 或阿里云百炼），
 * 经管理员审核通过后，评论里 @机器人名 就能触发它异步回复。
 */

/**
 * 全站可用机器人（已过审 + 未停用）→ AiBotBriefVO[]
 * 评论区 @ 选择器用它，只含可公开展示的字段。
 */
export function listAvailableBots() {
  return http.get('/bot/available')
}

/** 我创建的机器人（含待审 / 已驳回）→ AiBotVO[] */
export function listMyBots() {
  return http.get('/bot/mine')
}

/** 我的机器人详情 → AiBotVO */
export function getMyBot(id) {
  return http.get(`/bot/mine/${id}`)
}

/**
 * 创建机器人（提交审核）
 * @param {{name:string, description?:string, avatar?:string, provider:'deepseek'|'dashscope',
 *          model?:string, apiKey:string, systemPrompt?:string,
 *          temperature?:number, maxTokens?:number}} data
 */
export function createBot(data) {
  return http.post('/bot', data)
}

/**
 * 编辑机器人
 * 任何配置改动都会把机器人退回「待审核」；apiKey 留空表示沿用原来的 Key。
 */
export function updateBot(id, data) {
  return http.put(`/bot/${id}`, data)
}

/** 删除机器人（连带删除它的历史回复） */
export function deleteBot(id) {
  return http.delete(`/bot/${id}`)
}

/**
 * 启用 / 停用
 * 标量参数必须走 query：后端是 @RequestParam，放 body 会 400 且错误体无 msg。
 */
export function setBotEnabled(id, enabled) {
  return http.post(`/bot/${id}/enabled`, null, { params: { enabled } })
}
