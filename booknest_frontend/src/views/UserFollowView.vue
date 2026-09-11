<script setup>
/**
 * 关注 / 粉丝独立列表页（可通过 URL 直达）
 */
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import * as followApi from '@/api/follow'
import BnAvatar from '@/components/BnAvatar.vue'
import FollowButton from '@/components/FollowButton.vue'
import BnState from '@/components/BnState.vue'

const route = useRoute()

const users = ref([])
const loading = ref(true)
const error = ref('')

const userId = computed(() => String(route.params.id || ''))
const mode = computed(() => (route.name === 'user-followers' ? 'followers' : 'following'))
const title = computed(() => (mode.value === 'followers' ? '粉丝' : '关注'))

async function load() {
  loading.value = true
  error.value = ''
  try {
    users.value =
      mode.value === 'followers'
        ? (await followApi.listFollowers(userId.value)) || []
        : (await followApi.listFollowing(userId.value)) || []
  } catch (err) {
    error.value = err.message || '列表加载失败'
  } finally {
    loading.value = false
  }
}

onMounted(load)
watch([userId, mode], load)
</script>

<template>
  <div class="bn-container follow-page">
    <div class="head">
      <h1 class="bn-page-title">{{ title }}</h1>
      <router-link :to="`/user/${userId}`" class="back-link">
        <el-icon><ArrowLeft /></el-icon>返回个人主页
      </router-link>
    </div>

    <BnState
      :loading="loading"
      :error="error"
      :empty="!loading && !users.length"
      :empty-text="mode === 'followers' ? '还没有粉丝' : '还没有关注任何书友'"
      :skeleton-rows="5"
    >
      <div class="grid">
        <div v-for="item in users" :key="item.userId" class="bn-card item">
          <BnAvatar :src="item.avatar" :name="item.username" :size="52" :user-id="item.userId" />
          <router-link :to="`/user/${item.userId}`" class="name bn-ellipsis-1">
            {{ item.username || '书友' }}
          </router-link>
          <FollowButton :user-id="item.userId" size="small" />
        </div>
      </div>
    </BnState>
  </div>
</template>

<style scoped>
.follow-page {
  max-width: 900px;
}

.head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 18px;
}

.back-link {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 13px;
  color: var(--bn-text-sub);
}

.back-link:hover {
  color: var(--bn-primary);
}

.grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(158px, 1fr));
  gap: 14px;
}

.item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 9px;
  padding: 20px 14px;
  text-align: center;
}

.name {
  font-size: 13.5px;
  font-weight: 600;
  max-width: 100%;
}

.name:hover {
  color: var(--bn-primary);
}
</style>
