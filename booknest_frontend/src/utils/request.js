import axios from 'axios'
import { ElMessage } from 'element-plus'
import router from '@/router'

/**
 * 后端统一返回体：{ code, msg, data }，code === 1 表示成功
 * 用户端令牌头：authentication
 * 管理端令牌头：admin-authentication
 */
export const USER_TOKEN_KEY = 'bn_user_token'
export const ADMIN_TOKEN_KEY = 'bn_admin_token'

const TOKEN_HEADER = {
  user: 'authentication',
  admin: 'admin-authentication'
}

/** 同时挂载两套令牌头，使同一实例可服务用户端与管理端 */
function attachTokens(config) {
  const userToken = localStorage.getItem(USER_TOKEN_KEY)
  const adminToken = localStorage.getItem(ADMIN_TOKEN_KEY)
  if (userToken) config.headers[TOKEN_HEADER.user] = userToken
  if (adminToken) config.headers[TOKEN_HEADER.admin] = adminToken
  return config
}

const http = axios.create({
  baseURL: import.meta.env.VITE_API_BASE || '/api',
  timeout: 30000,
  headers: { 'Content-Type': 'application/json;charset=utf-8' }
})

http.interceptors.request.use(attachTokens, (error) => Promise.reject(error))

http.interceptors.response.use(
  (response) => {
    const body = response.data
    // 非标准返回（如 SSE、文件流）直接透传
    if (body === null || typeof body !== 'object' || !('code' in body)) {
      return body
    }
    if (body.code === 1) {
      return body.data
    }
    const msg = body.msg || '请求失败'
    ElMessage.error(msg)
    return Promise.reject(new Error(msg))
  },
  (error) => {
    const status = error.response?.status
    const isAdminPath = error.config?.url?.startsWith('/admin')

    if (status === 401) {
      if (isAdminPath) {
        localStorage.removeItem(ADMIN_TOKEN_KEY)
        ElMessage.error('管理端登录已过期，请重新登录')
        router.push({ name: 'admin-login' })
      } else {
        localStorage.removeItem(USER_TOKEN_KEY)
        ElMessage.error('登录已过期，请重新登录')
        const redirect = router.currentRoute.value.fullPath
        if (!redirect.startsWith('/login')) {
          router.push({ name: 'login', query: { redirect } })
        }
      }
    } else if (status === 403) {
      ElMessage.error('没有权限执行该操作')
    } else if (status === 500) {
      ElMessage.error(error.response?.data?.msg || '服务器开小差了，请稍后再试')
    } else if (error.code === 'ECONNABORTED') {
      ElMessage.error('请求超时，请检查网络')
    } else {
      ElMessage.error(error.response?.data?.msg || '网络异常，请稍后重试')
    }
    return Promise.reject(error)
  }
)

export default http
