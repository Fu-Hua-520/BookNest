<script setup>
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { useBadgeStore } from '@/stores/badge'
import { useLayoutStore } from '@/stores/layout'
import { useTaxonomyStore } from '@/stores/taxonomy'
import { useUserStore } from '@/stores/user'
import AppHeader from '@/components/AppHeader.vue'
import AppFooter from '@/components/AppFooter.vue'
import AppSidebar from '@/components/AppSidebar.vue'

const route = useRoute()
const badgeStore = useBadgeStore()
const layoutStore = useLayoutStore()
const taxonomyStore = useTaxonomyStore()
const userStore = useUserStore()

/** 窄屏下侧栏改为纯图标轨道，并且不再挤占正文（否则首屏只剩一条缝） */
const narrow = ref(typeof window !== 'undefined' && window.innerWidth < 900)
function onResize() {
  narrow.value = window.innerWidth < 900
}

/** 当前页面骨架是否带左侧栏：管理后台是另一套骨架，整页聊天页本身就是聊天界面 */
const withSidebar = computed(
  () => userStore.isLoggedIn && !route.path.startsWith('/admin') && route.path !== '/chat'
)

/**
 * 侧栏是「挤占」而不是「遮盖」正文：
 * 这里给整个外壳加左内边距，宽度跟着侧栏折叠状态走，宽屏下正文永远不会被压住。
 * 窄屏时不加内边距，侧栏以图标轨道形式悬浮在左缘。
 */
const shellStyle = computed(() => {
  if (!withSidebar.value || narrow.value) return {}
  return { paddingLeft: `${layoutStore.sidebarWidth}px` }
})

// 登录后启动未读轮询，退出后停止
watch(
  () => userStore.isLoggedIn,
  (loggedIn) => {
    if (loggedIn) {
      badgeStore.startPolling()
      // 未登录时 taxonomy 的首次拉取会因 401 拿到空数据，登录后强制重新拉一次
      taxonomyStore.load(true)
    } else {
      badgeStore.stopPolling()
      badgeStore.clearNotification()
      badgeStore.clearChat()
    }
  },
  { immediate: true }
)

onMounted(() => {
  taxonomyStore.load()
  window.addEventListener('resize', onResize)
})

onUnmounted(() => {
  badgeStore.stopPolling()
  window.removeEventListener('resize', onResize)
})
</script>

<template>
  <div class="bn-shell" :style="shellStyle">
    <!-- 左侧主侧边栏：消息 / 群聊 / 通知 / 收藏 / 书吧 / 书单 / 我的 -->
    <AppSidebar />
    <AppHeader v-if="!route.path.startsWith('/admin')" />
    <main :class="route.path.startsWith('/admin') ? '' : 'bn-main'">
      <router-view v-slot="{ Component }">
        <component :is="Component" />
      </router-view>
    </main>
    <AppFooter v-if="!route.path.startsWith('/admin')" />
  </div>
</template>

<style scoped>
/* 侧栏展开/收起时正文跟着平移，避免内容「跳」一下 */
.bn-shell {
  transition: padding-left 0.18s ease;
  min-height: 100vh;
  display: flex;
  flex-direction: column;
}

.bn-shell :deep(.bn-main) {
  flex: 1;
}
</style>
