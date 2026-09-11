<script setup>
/**
 * 用户头像：有 avatar 用图片，无则渲染首字母色块
 */
import { computed } from 'vue'
import { avatarColor, avatarFallback } from '@/utils/format'

const props = defineProps({
  src: { type: String, default: '' },
  name: { type: String, default: '' },
  size: { type: Number, default: 40 },
  /** 是否可点击跳转个人主页 */
  userId: { type: String, default: '' },
  linkable: { type: Boolean, default: true }
})

const initial = computed(() => avatarFallback(props.name))
const background = computed(() => avatarColor(props.name))
const style = computed(() => ({
  width: `${props.size}px`,
  height: `${props.size}px`,
  fontSize: `${Math.max(12, Math.round(props.size * 0.42))}px`,
  background: props.src ? 'transparent' : background.value
}))
const target = computed(() => (props.userId && props.linkable ? `/user/${props.userId}` : ''))
</script>

<template>
  <router-link v-if="target" :to="target" class="bn-avatar" :style="style">
    <img v-if="src" :src="src" :alt="name" />
    <span v-else>{{ initial }}</span>
  </router-link>
  <span v-else class="bn-avatar" :style="style">
    <img v-if="src" :src="src" :alt="name" />
    <span v-else>{{ initial }}</span>
  </span>
</template>

<style scoped>
.bn-avatar {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  overflow: hidden;
  flex-shrink: 0;
  color: #fff;
  font-weight: 600;
  user-select: none;
  border: 1px solid rgba(47, 42, 37, 0.06);
  transition: opacity 0.15s ease;
}

a.bn-avatar:hover {
  opacity: 0.86;
}

.bn-avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}
</style>
