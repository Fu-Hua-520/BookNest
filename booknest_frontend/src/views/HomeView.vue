<script setup>
/**
 * 首页：帖子信息流 + 侧栏（分类/标签/AI 入口）
 * 后端 /post/list 无排序参数，默认即按发布时间倒序，天然构成「最新」流
 */
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import * as postApi from '@/api/post'
import { useUserStore } from '@/stores/user'
import PostCard from '@/components/PostCard.vue'
import HomeSidebar from '@/components/HomeSidebar.vue'

const router = useRouter()
const userStore = useUserStore()

const posts = ref([])
const loading = ref(false)
const loadingMore = ref(false)
const error = ref('')
const finished = ref(false)

const page = ref(1)
const PAGE_SIZE = 10

/** 置顶帖始终排在最前 */
const sortedPosts = computed(() => {
  const list = [...posts.value]
  return list.sort((a, b) => Number(b.isTop || 0) - Number(a.isTop || 0))
})

async function loadPage(reset = false) {
  if (reset) {
    page.value = 1
    finished.value = false
    error.value = ''
  }
  if (finished.value) return

  const isFirst = page.value === 1
  if (isFirst) loading.value = true
  else loadingMore.value = true

  try {
    const data = await postApi.listPosts({
      page: page.value,
      pageSize: PAGE_SIZE,
      auditStatus: 1
    })
    const list = data || []
    if (isFirst) {
      posts.value = list
    } else {
      posts.value = posts.value.concat(list)
    }
    if (list.length < PAGE_SIZE) finished.value = true
    else page.value += 1
  } catch (err) {
    error.value = err.message || '帖子列表加载失败'
    finished.value = true
  } finally {
    loading.value = false
    loadingMore.value = false
  }
}

function goPublish() {
  if (!userStore.isLoggedIn) {
    router.push({ name: 'login', query: { redirect: '/post/edit' } })
    return
  }
  router.push({ name: 'post-edit' })
}

onMounted(() => loadPage(true))
</script>

<template>
  <div class="bn-container home-layout">
    <section class="feed">
      <div class="feed-head">
        <div>
          <h1 class="bn-page-title">书友圈</h1>
          <p class="bn-page-sub">最新书评与读书笔记</p>
        </div>
        <el-button type="primary" @click="goPublish">
          <el-icon style="margin-right: 4px"><EditPen /></el-icon>写书评
        </el-button>
      </div>

      <el-alert
        v-if="error"
        :title="error"
        type="error"
        show-icon
        :closable="false"
        class="bn-mt-12"
      >
        <template #default>
          <el-button size="small" @click="loadPage(true)">重新加载</el-button>
        </template>
      </el-alert>

      <el-skeleton v-if="loading" class="bn-mt-16" :rows="4" animated />

      <template v-else>
        <PostCard v-for="post in sortedPosts" :key="post.id" :post="post" class="bn-mt-12" />

        <div v-if="!sortedPosts.length && !error" class="bn-empty">
          <el-icon :size="34" color="#c8bdb1"><DocumentDelete /></el-icon>
          <p class="bn-mt-12">还没有书评，来写第一篇吧</p>
          <el-button type="primary" class="bn-mt-12" @click="goPublish">写书评</el-button>
        </div>
      </template>

      <div v-if="sortedPosts.length" class="load-more">
        <el-button v-if="!finished" :loading="loadingMore" text @click="loadPage(false)">
          加载更多
        </el-button>
        <span v-else class="bn-text-muted">已经到底啦</span>
      </div>
    </section>

    <HomeSidebar class="side" />
  </div>
</template>

<style scoped>
.home-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 288px;
  gap: 22px;
  align-items: start;
}

.feed-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
  padding-bottom: 4px;
}

.feed-head .bn-page-sub {
  margin-bottom: 0;
}

.load-more {
  text-align: center;
  padding: 22px 0;
  font-size: 13px;
}

@media (max-width: 980px) {
  .home-layout {
    grid-template-columns: 1fr;
  }
  .side {
    order: 2;
  }
}
</style>
