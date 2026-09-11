<script setup>
/**
 * 关注按钮：自动查询关注状态，支持关注/取关切换
 */
import { onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import * as followApi from '@/api/follow'
import { useUserStore } from '@/stores/user'

const props = defineProps({
  userId: { type: String, required: true },
  size: { type: String, default: 'default' }
})

const emit = defineEmits(['change'])

const router = useRouter()
const userStore = useUserStore()

const following = ref(false)
const loading = ref(false)
const checked = ref(false)

const isSelf = () => Boolean(userStore.userId) && String(userStore.userId) === String(props.userId)

async function loadStatus() {
  if (!userStore.isLoggedIn || isSelf() || !props.userId) return
  try {
    following.value = await followApi.isFollowing(props.userId)
  } catch {
    following.value = false
  } finally {
    checked.value = true
  }
}

async function toggle() {
  if (!userStore.isLoggedIn) {
    ElMessage.info('登录后才能关注书友')
    router.push({ name: 'login', query: { redirect: router.currentRoute.value.fullPath } })
    return
  }
  if (isSelf()) return

  loading.value = true
  try {
    if (following.value) {
      await followApi.unfollow(props.userId)
      following.value = false
      ElMessage.success('已取消关注')
    } else {
      await followApi.follow(props.userId)
      following.value = true
      ElMessage.success('关注成功')
    }
    emit('change', following.value)
  } finally {
    loading.value = false
  }
}

onMounted(loadStatus)
watch(() => props.userId, loadStatus)
</script>

<template>
  <el-button
    v-if="!isSelf()"
    :type="following ? 'default' : 'primary'"
    :size="size"
    :loading="loading"
    @click="toggle"
  >
    <el-icon v-if="!following" style="margin-right: 3px"><Plus /></el-icon>
    <el-icon v-else style="margin-right: 3px"><Check /></el-icon>
    {{ following ? '已关注' : '关注' }}
  </el-button>
</template>
