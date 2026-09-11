import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import * as adminApi from '@/api/admin'
import * as userApi from '@/api/user'
import { ADMIN_TOKEN_KEY, USER_TOKEN_KEY } from '@/utils/request'

const PROFILE_KEY = 'bn_user_profile'
const ADMIN_PROFILE_KEY = 'bn_admin_profile'

function readProfile() {
  try {
    return JSON.parse(localStorage.getItem(PROFILE_KEY) || 'null')
  } catch {
    return null
  }
}

function readAdminProfile() {
  try {
    return JSON.parse(localStorage.getItem(ADMIN_PROFILE_KEY) || 'null')
  } catch {
    return null
  }
}

/**
 * 用户态：令牌、个人资料、登录/注册/登出
 * 后端 UserLoginVO 自带 role，据此判断是否可进入管理后台
 */
export const useUserStore = defineStore('user', () => {
  const token = ref(localStorage.getItem(USER_TOKEN_KEY) || '')
  const profile = ref(readProfile())

  const isLoggedIn = computed(() => Boolean(token.value))
  const userId = computed(() => profile.value?.id || '')
  const username = computed(() => profile.value?.username || profile.value?.account || '书友')
  const avatar = computed(() => profile.value?.avatar || '')
  const role = computed(() => profile.value?.role || 'USER')
  const isAdmin = computed(() => role.value === 'ADMIN')
  const isMember = computed(() => Number(profile.value?.userLevel || 0) !== 0)

  function persistProfile(value) {
    profile.value = value
    if (value) {
      localStorage.setItem(PROFILE_KEY, JSON.stringify(value))
    } else {
      localStorage.removeItem(PROFILE_KEY)
    }
  }

  async function login(payload) {
    const vo = await userApi.login(payload)
    token.value = vo.token || ''
    localStorage.setItem(USER_TOKEN_KEY, token.value)
    // token 属于凭证，不写入 localStorage 的资料副本
    const { token: _omit, ...rest } = vo
    persistProfile(rest)
    return vo
  }

  async function register(payload) {
    return userApi.register(payload)
  }

  function logout() {
    token.value = ''
    localStorage.removeItem(USER_TOKEN_KEY)
    persistProfile(null)
  }

  /** 局部更新资料（如上传头像后） */
  function patchProfile(patch) {
    persistProfile({ ...(profile.value || {}), ...patch })
  }

  return {
    token,
    profile,
    isLoggedIn,
    userId,
    username,
    avatar,
    role,
    isAdmin,
    isMember,
    login,
    register,
    logout,
    patchProfile
  }
})

/**
 * 管理端登录态
 * 走后端独立的 /admin/login：令牌由管理端密钥签发，与用户端令牌互不通用。
 * 资料与令牌各存各的 key，因此前后台会话可以共存、互不覆盖。
 */
export const useAdminStore = defineStore('admin', () => {
  const token = ref(localStorage.getItem(ADMIN_TOKEN_KEY) || '')
  const adminInfo = ref(readAdminProfile())

  const isLoggedIn = computed(() => Boolean(token.value))
  const username = computed(() => adminInfo.value?.username || adminInfo.value?.account || '管理员')

  async function login(payload) {
    const vo = await adminApi.adminLogin(payload)
    token.value = vo.token || ''
    localStorage.setItem(ADMIN_TOKEN_KEY, token.value)
    // token 属于凭证，不写入资料副本
    const { token: _omit, ...rest } = vo
    adminInfo.value = rest
    localStorage.setItem(ADMIN_PROFILE_KEY, JSON.stringify(rest))
    return vo
  }

  function logout() {
    token.value = ''
    localStorage.removeItem(ADMIN_TOKEN_KEY)
    adminInfo.value = null
    localStorage.removeItem(ADMIN_PROFILE_KEY)
  }

  return { token, adminInfo, isLoggedIn, username, login, logout }
})
