<script setup>
/**
 * 书籍封面：无封面时用书名首字 + 书卷底色占位
 */
import { computed, ref, watch } from 'vue'

const props = defineProps({
  src: { type: String, default: '' },
  title: { type: String, default: '' },
  width: { type: String, default: '100%' },
  height: { type: String, default: '200px' },
  radius: { type: String, default: '8px' }
})

const failed = ref(false)
watch(
  () => props.src,
  () => {
    failed.value = false
  }
)

const showImage = computed(() => Boolean(props.src) && !failed.value)
const initial = computed(() => (props.title || '书').trim().charAt(0))
const wraperStyle = computed(() => ({
  width: props.width,
  height: props.height,
  borderRadius: props.radius
}))
</script>

<template>
  <div class="bn-cover" :style="wraperStyle">
    <img v-if="showImage" :src="src" :alt="title" loading="lazy" @error="failed = true" />
    <div v-else class="bn-cover-placeholder">
      <span class="bn-cover-initial">{{ initial }}</span>
      <span class="bn-cover-title">{{ title }}</span>
    </div>
  </div>
</template>

<style scoped>
.bn-cover {
  position: relative;
  overflow: hidden;
  background: linear-gradient(150deg, #f3e9dd 0%, #e6d7c6 100%);
  box-shadow: 0 2px 10px rgba(47, 42, 37, 0.1);
  flex-shrink: 0;
}

.bn-cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.bn-cover-placeholder {
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 10px;
  text-align: center;
}

.bn-cover-initial {
  font-size: 30px;
  font-weight: 700;
  color: #a9754f;
  line-height: 1;
}

.bn-cover-title {
  font-size: 11px;
  color: #8a7c6d;
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}
</style>
