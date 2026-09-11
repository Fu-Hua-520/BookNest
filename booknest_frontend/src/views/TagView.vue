<script setup>
/**
 * 标签页：按标签浏览帖子
 */
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import * as postApi from '@/api/post'
import { useTaxonomyStore } from '@/stores/taxonomy'
import PostCard from '@/components/PostCard.vue'
import BnState from '@/components/BnState.vue'

const route = useRoute()
const taxonomyStore = useTaxonomyStore()

const posts = ref([])
const loading = ref(false)
const error = ref('')

const page = ref(1)
const PAGE_SIZE = 10
const finished = ref(false)

const tagId = computed(() => String(route.params.id || ''))

/** 标签名优先从已缓存的热门标签中取 */
const tagName = computed(() => {
  const hit = (taxonomyStore.hotTags || []).find((item) => String(item.id) === tagId.value)
  return hit?.name || '标签'
})

async function load(reset = false) {
  if (reset) {
    page.value = 1
    finished.value = false
    error.value = ''
  }
  if (finished.value) return

  loading.value = true
  try {
    const data = await postApi.listPosts({
      tagId: tagId.value,
      auditStatus: 1,
      page: page.value,
      pageSize: PAGE_SIZE
    })
    const list = data || []
    posts.value = reset ? list : posts.value.concat(list)
    if (list.length < PAGE_SIZE) finished.value = true
    else page.value += 1
  } catch (err) {
    error.value = err.message || '加载失败'
    finished.value = true
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  await taxonomyStore.load()
  load(true)
})

watch(tagId, async () => {
  posts.value = []
  load(true)
})
</script>

<template>
  <div class="bn-container">
    <div class="head">
      <h1 class="bn-page-title">#{{ tagName }}</h1>
      <p class="bn-page-sub">使用该标签的书评</p>
    </div>

    <!-- 热门标签快捷切换 -->
    <div v-if="taxonomyStore.hotTags.length" class="tag-bar">
      <router-link
        v-for="tag in taxonomyStore.hotTags"
        :key="tag.id"
        :to="`/tag/${tag.id}`"
        :class="['tag-link', { active: String(tag.id) === tagId }]"
      >
        #{{ tag.name }}
      </router-link>
    </div>

    <BnState
      :loading="loading && !posts.length"
      :error="error"
      :empty="!posts.length"
      :empty-text="`还没有帖子使用 #${tagName} 标签`"
    >
      <PostCard v-for="post in posts" :key="post.id" :post="post" class="bn-mt-12" />
    </BnState>

    <div v-if="posts.length && !finished" class="load-more">
      <el-button text :loading="loading" @click="load(false)">加载更多</el-button>
    </div>
  </div>
</template>

<style scoped>
.head .bn-page-sub {
  margin-bottom: 14px;
}

.tag-bar {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 16px;
}

.tag-link {
  padding: 4px 12px;
  border-radius: 999px;
  font-size: 12.5px;
  background: #fff;
  border: 1px solid var(--bn-border);
  color: var(--bn-text-sub);
  transition: all 0.15s ease;
}

.tag-link:hover {
  border-color: var(--bn-primary);
  color: var(--bn-primary);
}

.tag-link.active {
  background: var(--bn-primary);
  border-color: var(--bn-primary);
  color: #fff;
}

.load-more {
  text-align: center;
  padding: 22px 0;
}
</style>
