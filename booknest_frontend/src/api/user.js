import http from '@/utils/request'

/** 用户注册：{ phone?, email, password } */
export function register(data) {
  return http.post('/user/register', data)
}

/** 用户登录：{ email, password } → UserLoginVO（含 token） */
export function login(data) {
  return http.post('/user/login', data)
}

/** —— 以下接口后端尚未提供，保留签名以便后续对齐 —— */

/** 上传文件到 OSS → 返回可访问 URL（走 /common，无需登录） */
export function uploadFile(file) {
  const formData = new FormData()
  formData.append('file', file)
  return http.post('/common/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 60000
  })
}
