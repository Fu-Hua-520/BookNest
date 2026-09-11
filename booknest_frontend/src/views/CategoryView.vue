<script setup>
/**
 * 分类页：按分类浏览帖子
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

const categoryId = computed(() => String(route.params.id || ''))
const categoryName = computed(() => taxonomyStore.findCategoryName(categoryId.value) || '分类')

/** 当前分类的父级信息，用于二级分类横向切换 */
const currentCategory = computed(() =>
  taxonomyStore
    .flattenCategories()
    .find((item) => String(item.id) === categoryId.value)
)

const siblingCategories = computed(() => {
  const current = currentCategory.value
  if (!current) return []
  const parentId = current.isParent ? current.id : current.parentId
  const parent = taxonomyStore.categoryTree.find((item) => String(item.id) === String(parentId))
  if (!parent) return []
  return [{ id: parent.id, name: parent.name }, ...(parent.children || [])]
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
      categoryId: categoryId.value,
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

watch(categoryId, async () => {
  await taxonomyStore.load()
  posts.value = []
  load(true)
})
</script>

<template>
  <div class="bn-container">
    <div class="head">
      <h1 class="bn-page-title">{{ categoryName }}</h1>
      <p class="bn-page-sub">该分类下的书评与讨论</p>
    </div>

    <div v-if="siblingCategories.length > 1" class="siblings">
      <router-link
        v-for="item in siblingCategories"
        :key="item.id"
        :to="`/category/${item.id}`"
        :class="['sibling', { active: String(item.id) === categoryId }]"
      >
        {{ item.name }}
      </router-link>
    </div>

    <BnState
      :loading="loading && !posts.length"
      :error="error"
      :empty="!posts.length"
      :empty-text="`「${categoryName}」下还没有帖子`"
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

.siblings {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 16px;
}

.sibling {
  padding: 5px 14px;
  border-radius: 999px;
  font-size: 13px;
  background: #fff;
  border: 1px solid var(--bn-border);
  color: var(--bn-text-sub);
  transition: all 0.15s ease;
}

.sibling:hover {
  border-color: var(--bn-primary);
  color: var(--bn-primary);
}

.sibling.active {
  background: var(--bn-primary);
  border-color: var(--bn-primary);
  color: #fff;
}

.load-more {
  text-align: center;
  padding: 22px 0;
}
</style>
