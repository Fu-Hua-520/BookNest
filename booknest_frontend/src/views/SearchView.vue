<script setup>
/**
 * 搜索页：帖 / 书籍 / 书单 三 Tab
 * 后端 /post/list 不支持关键词，帖子 Tab 采用「拉取最新 N 条 + 前端匹配」的降级策略并明确提示
 */
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import * as postApi from '@/api/post'
import * as bookApi from '@/api/book'
import * as booklistApi from '@/api/booklist'
import { useTaxonomyStore } from '@/stores/taxonomy'
import BnCover from '@/components/BnCover.vue'
import BnAvatar from '@/components/BnAvatar.vue'
import PostCard from '@/components/PostCard.vue'
import BnState from '@/components/BnState.vue'
import { formatRating, fromNow } from '@/utils/format'

const route = useRoute()
const router = useRouter()
const taxonomyStore = useTaxonomyStore()

const keyword = ref(String(route.query.q || ''))
const input = ref(keyword.value)
const tab = ref('post')

const loading = ref(false)
const posts = ref([])
const books = ref([])
const booklists = ref([])
/** 帖子搜索为本地匹配，标记数据来源的局限 */
const postSearchLimited = ref(false)

const hasResult = computed(() => {
  if (tab.value === 'post') return posts.value.length > 0
  if (tab.value === 'book') return books.value.length > 0
  return booklists.value.length > 0
})

/** 帖子本地模糊匹配：标题 / 摘要 / 作者 / 分类 / 标签 */
function matchPost(post, kw) {
  const lower = kw.toLowerCase()
  const fields = [
    post.title,
    post.summary,
    post.authorName,
    post.categoryName,
    post.bookTitle,
    ...(post.tags || []).map((tag) => tag.name)
  ]
  return fields.some((field) => String(field || '').toLowerCase().includes(lower))
}

async function runSearch() {
  const kw = keyword.value.trim()
  if (!kw) {
    posts.value = []
    books.value = []
    booklists.value = []
    return
  }

  loading.value = true
  postSearchLimited.value = false
  try {
    const [bookResult, booklistResult, postPages] = await Promise.allSettled([
      bookApi.searchBooks(kw),
      booklistApi.listBooklists({ page: 1, pageSize: 30 }),
      Promise.all([
        postApi.listPosts({ page: 1, pageSize: 50, auditStatus: 1 }),
        postApi.listPosts({ page: 2, pageSize: 50, auditStatus: 1 })
      ])
    ])

    books.value = bookResult.status === 'fulfilled' ? bookResult.value || [] : []

    const allBooklists = booklistResult.status === 'fulfilled' ? booklistResult.value || [] : []
    booklists.value = allBooklists.filter((item) =>
      `${item.title} ${item.summary || ''} ${item.userName || ''}`
        .toLowerCase()
        .includes(kw.toLowerCase())
    )

    if (postPages.status === 'fulfilled') {
      const merged = [...(postPages.value[0] || []), ...(postPages.value[1] || [])]
      posts.value = merged.filter((post) => matchPost(post, kw))
      postSearchLimited.value = true
    } else {
      posts.value = []
    }
  } finally {
    loading.value = false
  }
}

function onSearch() {
  const kw = input.value.trim()
  if (!kw) return
  keyword.value = kw
  router.replace({ name: 'search', query: { q: kw } })
}

function searchTag(tag) {
  input.value = tag.name
  onSearch()
}

watch(
  () => route.query.q,
  (value) => {
    const kw = String(value || '')
    if (kw !== keyword.value) {
      keyword.value = kw
      input.value = kw
      runSearch()
    }
  }
)

watch(keyword, runSearch)

onMounted(() => {
  taxonomyStore.load()
  if (keyword.value) runSearch()
})
</script>

<template>
  <div class="bn-container">
    <div class="search-head">
      <h1 class="bn-page-title">搜索</h1>
      <p class="bn-page-sub">在 BookNest 中查找书评、书籍与书单</p>

      <el-input
        v-model="input"
        size="large"
        placeholder="输入关键词，回车搜索"
        clearable
        @keyup.enter="onSearch"
      >
        <template #prefix><el-icon><Search /></el-icon></template>
        <template #append>
          <el-button @click="onSearch">搜索</el-button>
        </template>
      </el-input>

      <div v-if="taxonomyStore.hotTags.length" class="hot-tags">
        <span class="bn-text-muted">热门：</span>
        <button
          v-for="tag in taxonomyStore.hotTags.slice(0, 10)"
          :key="tag.id"
          class="hot-tag"
          @click="searchTag(tag)"
        >
          {{ tag.name }}
        </button>
      </div>
    </div>

    <template v-if="keyword">
      <el-tabs v-model="tab" class="tabs">
        <el-tab-pane :label="`书评 (${posts.length})`" name="post" />
        <el-tab-pane :label="`书籍 (${books.length})`" name="book" />
        <el-tab-pane :label="`书单 (${booklists.length})`" name="booklist" />
      </el-tabs>

      <el-alert
        v-if="postSearchLimited && tab === 'post'"
        type="info"
        :closable="false"
        show-icon
        class="tip"
        title="帖子搜索目前在最新内容范围内匹配，结果可能不完整"
      />

      <BnState
        :loading="loading"
        :empty="!loading && !hasResult"
        :empty-text="`没有找到与「${keyword}」相关的内容`"
        :skeleton-rows="4"
      >
        <!-- 帖子 -->
        <template v-if="tab === 'post'">
          <PostCard v-for="post in posts" :key="post.id" :post="post" class="bn-mt-12" />
        </template>

        <!-- 书籍 -->
        <template v-else-if="tab === 'book'">
          <div class="book-grid">
            <router-link
              v-for="book in books"
              :key="book.id"
              :to="`/book/${book.id}`"
              class="bn-card bn-card-hover book-item"
            >
              <BnCover :src="book.coverUrl" :title="book.title" width="70px" height="100px" radius="5px" />
              <div class="book-item-body">
                <p class="book-item-title bn-ellipsis-1">{{ book.title }}</p>
                <p class="book-item-author">{{ book.author || '佚名' }}</p>
                <p class="book-item-rate">
                  {{ formatRating(book.rating) }}
                  <span v-if="book.publisher">· {{ book.publisher }}</span>
                </p>
                <p v-if="book.description" class="book-item-desc bn-ellipsis-2">
                  {{ book.description }}
                </p>
              </div>
            </router-link>
          </div>
        </template>

        <!-- 书单 -->
        <template v-else>
          <router-link
            v-for="item in booklists"
            :key="item.id"
            :to="`/booklist/${item.id}`"
            class="bn-card bn-card-hover list-item"
          >
            <BnCover :src="item.coverImage" :title="item.title" width="72px" height="98px" radius="5px" />
            <div class="list-item-body">
              <p class="list-item-title">{{ item.title }}</p>
              <p class="list-item-summary bn-ellipsis-2">
                {{ item.summary || '这份书单还没有简介' }}
              </p>
              <div class="list-item-foot">
                <div class="bn-row bn-gap-8">
                  <BnAvatar :src="item.userAvatar" :name="item.userName" :size="20" :linkable="false" />
                  <span class="bn-text-sub">{{ item.userName || '书友' }}</span>
                </div>
                <span class="bn-text-muted">
                  {{ item.bookCount || 0 }} 本 · {{ fromNow(item.createTime) }}
                </span>
              </div>
            </div>
          </router-link>
        </template>
      </BnState>
    </template>

    <div v-else class="bn-empty">
      <el-icon :size="34" color="#c8bdb1"><Search /></el-icon>
      <p class="bn-mt-12">输入关键词开始搜索</p>
    </div>
  </div>
</template>

<style scoped>
.search-head {
  max-width: 760px;
  margin-bottom: 8px;
}

.hot-tags {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  margin-top: 12px;
  font-size: 13px;
}

.hot-tag {
  border: none;
  background: #fff;
  border: 1px solid var(--bn-border);
  border-radius: 999px;
  padding: 3px 11px;
  font-size: 12.5px;
  color: var(--bn-text-sub);
  cursor: pointer;
  transition: all 0.15s ease;
}

.hot-tag:hover {
  border-color: var(--bn-primary);
  color: var(--bn-primary);
}

.tabs {
  margin-top: 8px;
}

.tip {
  margin-bottom: 14px;
}

.book-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 14px;
}

.book-item,
.list-item {
  display: flex;
  gap: 14px;
  padding: 14px 16px;
  align-items: flex-start;
}

.book-item-body,
.list-item-body {
  flex: 1;
  min-width: 0;
}

.book-item-title,
.list-item-title {
  font-size: 15px;
  font-weight: 600;
}

.book-item-author {
  font-size: 12.5px;
  color: var(--bn-text-sub);
  margin-top: 2px;
}

.book-item-rate {
  font-size: 12.5px;
  color: #c8783c;
  margin-top: 5px;
}

.book-item-desc,
.list-item-summary {
  font-size: 12.5px;
  color: var(--bn-text-muted);
  line-height: 1.65;
  margin-top: 7px;
}

.list-item-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  font-size: 12px;
  margin-top: 9px;
}
</style>
