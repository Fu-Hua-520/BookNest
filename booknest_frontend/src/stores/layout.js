import { defineStore } from 'pinia'
import { computed, ref, watch } from 'vue'

/**
 * 全局左侧栏布局状态
 *
 * 为什么单独开一个 store：
 * 侧边栏要「占满左侧整列」，就必须挤占正文的空间 —— App.vue 的外壳需要知道
 * 当前侧栏宽度来施加 padding-left，而折叠状态由 AppSidebar 里的按钮切换。
 * 两个组件分处不同层级，用 store 传递比 prop/emit 透传清晰得多。
 *
 * 折叠状态持久化到 localStorage：否则每次刷新侧栏都弹回展开态，很打断使用。
 */
const COLLAPSED_KEY = 'bn_sidebar_collapsed'

/** 展开宽度 / 折叠宽度（px），与 AppSidebar 的 CSS 变量保持一致 */
export const SIDEBAR_WIDTH_EXPANDED = 240
export const SIDEBAR_WIDTH_COLLAPSED = 58

export const useLayoutStore = defineStore('layout', () => {
  const collapsed = ref(localStorage.getItem(COLLAPSED_KEY) === '1')

  /** 侧栏当前占用的宽度；未登录 / 管理后台 / 聊天整页时由调用方判断为 0 */
  const sidebarWidth = computed(() =>
    collapsed.value ? SIDEBAR_WIDTH_COLLAPSED : SIDEBAR_WIDTH_EXPANDED
  )

  function toggleSidebar() {
    collapsed.value = !collapsed.value
  }

  watch(collapsed, (value) => {
    localStorage.setItem(COLLAPSED_KEY, value ? '1' : '0')
  })

  return { collapsed, sidebarWidth, toggleSidebar }
})
