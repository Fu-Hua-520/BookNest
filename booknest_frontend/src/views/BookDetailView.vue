<script setup>
/**
 * 书籍详情：元数据 + 该书相关书评（按 bookId 过滤帖子流）
 */
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import * as bookApi from '@/api/book'
import * as postApi from '@/api/post'
import { formatRating } from '@/utils/format'
import { renderMarkdown } from '@/utils/markdown'
import { useUserStore } from '@/stores/user'
import BnCover from '@/components/BnCover.vue'
import PostCard from '@/components/PostCard.vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const book = ref(null)
const posts = ref([])
const loading = ref(true)
const postsLoading = ref(true)
const error = ref('')

const bookId = computed(() => String(route.params.id || ''))
const hasRating = computed(() => Number(book.value?.rating) > 0)
const descriptionHtml = computed(() => renderMarkdown(book.value?.description || ''))

/** 该书评论文本中出现的帖子，按分类过滤无法覆盖，改为拉全量再本地筛 bookId */
const bookPosts = computed(() =>
  (posts.value || []).filter((item) => String(item.bookId) === bookId.value)
)

async function loadBook() {
  loading.value = true
  error.value = ''
  try {
    book.value = await bookApi.getBookDetail(bookId.value)
  } catch (err) {
    error.value = err.message || '书籍信息加载失败'
  } finally {
    loading.value = false
  }
}

/** 后端帖子列表不支持按 bookId 查询，此处拉取多页后在本地筛选 */
async function loadPosts() {
  postsLoading.value = true
  try {
    const pages = await Promise.all([
      postApi.listPosts({ page: 1, pageSize: 50, auditStatus: 1 }),
      postApi.listPosts({ page: 2, pageSize: 50, auditStatus: 1 })
    ])
    posts.value = [...(pages[0] || []), ...(pages[1] || [])]
  } catch {
    posts.value = []
  } finally {
    postsLoading.value = false
  }
}

function writeReview() {
  const target = `/post/edit?bookId=${bookId.value}`
  if (!userStore.isLoggedIn) {
    router.push({ name: 'login', query: { redirect: target } })
    return
  }
  router.push(target)
}

onMounted(() => {
  loadBook()
  loadPosts()
})

watch(bookId, () => {
  loadBook()
  loadPosts()
})
</script>

<template>
  <div class="bn-container">
    <el-breadcrumb separator="/" class="crumb">
      <el-breadcrumb-item :to="{ path: '/' }">首页</el-breadcrumb-item>
      <el-breadcrumb-item>书籍详情</el-breadcrumb-item>
    </el-breadcrumb>

    <el-skeleton v-if="loading" class="bn-mt-16" :rows="6" animated />

    <el-alert v-else-if="error" :title="error" type="error" :closable="false" show-icon />

    <template v-else-if="book">
      <section class="bn-card book-hero">
        <BnCover :src="book.coverUrl" :title="book.title" width="148px" height="206px" />

        <div class="hero-info">
          <h1 class="book-title">{{ book.title }}</h1>
          <p class="book-author">{{ book.author || '佚名' }}</p>

          <div class="rate-row">
            <el-rate
              v-if="hasRating"
              :model-value="Number(book.rating) / 2"
              disabled
              allow-half
              :show-score="false"
            />
            <span :class="['rate-value', { muted: !hasRating }]">{{ formatRating(book.rating) }}</span>
            <span v-if="book.ratingCount" class="bn-text-muted">共 {{ book.ratingCount }} 人评分</span>
          </div>

          <el-descriptions :column="2" size="small" class="book-meta">
            <el-descriptions-item label="ISBN">
              {{ book.isbn || '—' }}
            </el-descriptions-item>
            <el-descriptions-item label="出版社">
              {{ book.publisher || '—' }}
            </el-descriptions-item>
            <el-descriptions-item label="出版日期">
              {{ book.publishDate || '—' }}
            </el-descriptions-item>
            <el-descriptions-item label="数据来源">
              <el-tag v-if="book.source" size="small" effect="plain">{{ book.source }}</el-tag>
              <span v-else>—</span>
            </el-descriptions-item>
          </el-descriptions>

          <div class="hero-actions">
            <el-button type="primary" @click="writeReview">
              <el-icon style="margin-right: 4px"><EditPen /></el-icon>写这本书的书评
            </el-button>
          </div>
        </div>
      </section>

      <section v-if="book.description" class="bn-card">
        <div class="bn-section-title" style="margin-top: 0">
          <h2>内容简介</h2>
        </div>
        <div class="bn-markdown" v-html="descriptionHtml" />
      </section>

      <section class="posts-section">
        <div class="bn-section-title">
          <h2>相关书评{{ bookPosts.length ? ` (${bookPosts.length})` : '' }}</h2>
        </div>

        <el-skeleton v-if="postsLoading" :rows="3" animated />

        <template v-else>
          <PostCard v-for="post in bookPosts" :key="post.id" :post="post" class="bn-mt-12" />
          <div v-if="!bookPosts.length" class="bn-empty">
            <el-icon :size="32" color="#c8bdb1"><DocumentDelete /></el-icon>
            <p class="bn-mt-12">这本书还没有书评，来写第一篇</p>
            <el-button type="primary" class="bn-mt-12" @click="writeReview">写书评</el-button>
          </div>
        </template>
      </section>
    </template>
  </div>
</template>

<style scoped>
.crumb {
  margin-bottom: 14px;
}

.book-hero {
  display: flex;
  gap: 24px;
  padding: 24px 26px;
  align-items: flex-start;
}

.hero-info {
  flex: 1;
  min-width: 0;
}

.book-title {
  font-size: 24px;
  line-height: 1.35;
  margin-bottom: 6px;
}

.book-author {
  font-size: 14.5px;
  color: var(--bn-text-sub);
}

.rate-row {
  display: flex;
  align-items: center;
  gap: 9px;
  margin: 11px 0 16px;
  font-size: 13px;
}

.rate-value {
  font-size: 18px;
  font-weight: 700;
  color: #c8783c;
}

.rate-value.muted {
  font-size: 13px;
  color: var(--bn-text-muted);
  font-weight: 400;
}

.book-meta {
  max-width: 520px;
  margin-bottom: 18px;
}

.book-meta :deep(.el-descriptions__label) {
  color: var(--bn-text-muted);
}

.posts-section {
  margin-top: 6px;
}

@media (max-width: 700px) {
  .book-hero {
    flex-direction: column;
    align-items: center;
    text-align: center;
  }
  .book-hero :deep(.bn-cover) {
    margin: 0 auto;
  }
  .rate-row,
  .hero-actions {
    justify-content: center;
  }
  .book-meta {
    text-align: left;
  }
}
</style>
