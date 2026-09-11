<script setup>
import { onMounted, onUnmounted, watch } from 'vue'
import { useRoute } from 'vue-router'
import { useBadgeStore } from '@/stores/badge'
import { useTaxonomyStore } from '@/stores/taxonomy'
import { useUserStore } from '@/stores/user'
import AppHeader from '@/components/AppHeader.vue'
import AppFooter from '@/components/AppFooter.vue'

const route = useRoute()
const badgeStore = useBadgeStore()
const taxonomyStore = useTaxonomyStore()
const userStore = useUserStore()

// 登录后启动未读轮询，退出后停止
watch(
  () => userStore.isLoggedIn,
  (loggedIn) => {
    if (loggedIn) {
      badgeStore.startPolling()
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
})

onUnmounted(() => {
  badgeStore.stopPolling()
})
</script>

<template>
  <AppHeader v-if="!route.path.startsWith('/admin')" />
  <main :class="route.path.startsWith('/admin') ? '' : 'bn-main'">
    <router-view v-slot="{ Component }">
      <component :is="Component" />
    </router-view>
  </main>
  <AppFooter v-if="!route.path.startsWith('/admin')" />
</template>
