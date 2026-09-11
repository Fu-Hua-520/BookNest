/**
 * 通用格式化工具
 */

/** 日期时间：支持 'yyyy-MM-dd HH:mm:ss' 字符串与时间戳 */
export function formatDateTime(value, withTime = true) {
  if (!value) return ''
  const date = toDate(value)
  if (!date) return String(value)
  const pad = (n) => String(n).padStart(2, '0')
  const datePart = `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
  if (!withTime) return datePart
  return `${datePart} ${pad(date.getHours())}:${pad(date.getMinutes())}`
}

/** 相对时间：刚刚 / N 分钟前 / N 小时前 / N 天前 / 具体日期 */
export function fromNow(value) {
  if (!value) return ''
  const date = toDate(value)
  if (!date) return String(value)
  const diff = Date.now() - date.getTime()
  if (diff < 0) return formatDateTime(value)
  const minute = 60 * 1000
  const hour = 60 * minute
  const day = 24 * hour

  if (diff < minute) return '刚刚'
  if (diff < hour) return `${Math.floor(diff / minute)} 分钟前`
  if (diff < day) return `${Math.floor(diff / hour)} 小时前`
  if (diff < 7 * day) return `${Math.floor(diff / day)} 天前`
  return formatDateTime(value, false)
}

function toDate(value) {
  if (value instanceof Date) return value
  if (typeof value === 'number') return new Date(value)
  // 后端返回 'yyyy-MM-dd HH:mm:ss'，Safari 对空格分隔不兼容，统一替换为 T
  const normalized = String(value).replace(' ', 'T')
  const date = new Date(normalized)
  return Number.isNaN(date.getTime()) ? null : date
}

/** 数字缩写：1234 → 1.2k，12345678 → 1234.6w */
export function formatCount(value) {
  const num = Number(value || 0)
  if (num < 1000) return String(num)
  if (num < 10000) return `${(num / 1000).toFixed(1).replace(/\.0$/, '')}k`
  return `${(num / 10000).toFixed(1).replace(/\.0$/, '')}w`
}

/** 书籍评分展示：4.5 → 4.5 */
export function formatRating(value) {
  const num = Number(value)
  if (!Number.isFinite(num) || num <= 0) return '暂无评分'
  return num.toFixed(1)
}

/** 文件大小 */
export function formatFileSize(bytes) {
  if (!bytes) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB']
  let index = 0
  let size = bytes
  while (size >= 1024 && index < units.length - 1) {
    size /= 1024
    index += 1
  }
  return `${size.toFixed(index === 0 ? 0 : 1)} ${units[index]}`
}

/** 头像兜底：无头像时用用户名首字符生成字母头像 */
export function avatarFallback(name) {
  const char = (name || '书').trim().charAt(0)
  return char.toUpperCase()
}

/** 稳定色板：按字符串哈希取色，用于字母头像背景 */
const AVATAR_COLORS = ['#8b5e3c', '#a9754f', '#c8783c', '#7a6a55', '#9c6b4f', '#6f7d5a', '#8a6b8f', '#5f7a86']

export function avatarColor(name) {
  const text = String(name || 'booknest')
  let hash = 0
  for (let i = 0; i < text.length; i += 1) {
    hash = (hash * 31 + text.charCodeAt(i)) % 100000
  }
  return AVATAR_COLORS[hash % AVATAR_COLORS.length]
}

/** 防抖 */
export function debounce(fn, wait = 300) {
  let timer = null
  return function debounced(...args) {
    if (timer) clearTimeout(timer)
    timer = setTimeout(() => fn.apply(this, args), wait)
  }
}
