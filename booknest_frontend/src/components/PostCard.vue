<script setup>
/**
 * 帖子卡片：首页/分类/标签/搜索/个人主页共用
 */
import { computed } from 'vue'
import BnAvatar from './BnAvatar.vue'
import { formatCount, fromNow } from '@/utils/format'

const props = defineProps({
  post: { type: Object, required: true },
  /** 是否显示封面缩略图 */
  showCover: { type: Boolean, default: true }
})

const post = computed(() => props.post || {})
const detailLink = computed(() => `/post/${post.value.id}`)
const authorLink = computed(() => (post.value.authorId ? `/user/${post.value.authorId}` : ''))
const categoryLink = computed(() => (post.value.categoryId ? `/category/${post.value.categoryId}` : ''))
const cover = computed(() => post.value.coverImage || '')
const tags = computed(() => (post.value.tags || []).slice(0, 4))
</script>

<template>
  <article class="bn-card bn-card-hover post-card">
    <div class="post-body">
      <div class="post-main">
        <!-- 置顶与分类 -->
        <div class="post-flags">
          <el-tag v-if="post.isTop === 1" type="danger" size="small" effect="plain">置顶</el-tag>
          <router-link v-if="categoryLink" :to="categoryLink" class="bn-tag">
            {{ post.categoryName || '未分类' }}
          </router-link>
          <router-link v-if="post.bookId" :to="`/book/${post.bookId}`" class="bn-tag book-tag">
            《{{ post.bookTitle || '关联书籍' }}》
          </router-link>
        </div>

        <router-link :to="detailLink" class="post-title bn-ellipsis-1">{{ post.title }}</router-link>

        <p v-if="post.summary" class="post-summary bn-ellipsis-2">{{ post.summary }}</p>

        <div v-if="tags.length" class="post-tags">
          <router-link v-for="tag in tags" :key="tag.id" :to="`/tag/${tag.id}`" class="post-tag">
            #{{ tag.name }}
          </router-link>
        </div>

        <div class="post-footer">
          <router-link v-if="authorLink" :to="authorLink" class="bn-row bn-gap-8 author">
            <BnAvatar :src="post.authorAvatar" :name="post.authorName" :size="24" :linkable="false" />
            <span class="author-name">{{ post.authorName || '匿名书友' }}</span>
          </router-link>

          <div class="bn-meta">
            <span class="bn-meta-item">
              <el-icon><View /></el-icon>{{ formatCount(post.viewCount) }}
            </span>
            <span class="bn-meta-item">
              <el-icon><Star /></el-icon>{{ formatCount(post.likeCount) }}
            </span>
            <span class="bn-meta-item">
              <el-icon><ChatDotRound /></el-icon>{{ formatCount(post.commentCount) }}
            </span>
            <span class="bn-meta-item">{{ fromNow(post.publishTime) }}</span>
          </div>
        </div>
      </div>

      <router-link v-if="showCover && cover" :to="detailLink" class="post-cover">
        <img :src="cover" :alt="post.title" loading="lazy" />
      </router-link>
    </div>
  </article>
</template>

<style scoped>
.post-body {
  display: flex;
  gap: 16px;
  align-items: stretch;
}

.post-main {
  flex: 1;
  min-width: 0;
}

.post-flags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 8px;
}

.book-tag {
  background: #eef4f8;
  color: #3f6d8e;
  border-color: #d8e6f0;
}

.post-title {
  display: block;
  font-size: 17px;
  font-weight: 600;
  color: var(--bn-text);
  margin-bottom: 6px;
  transition: color 0.15s ease;
}

.post-title:hover {
  color: var(--bn-primary);
}

.post-summary {
  color: var(--bn-text-sub);
  font-size: 13.5px;
  line-height: 1.7;
  margin-bottom: 10px;
}

.post-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-bottom: 12px;
}

.post-tag {
  font-size: 12px;
  color: var(--bn-primary);
  opacity: 0.85;
}

.post-tag:hover {
  opacity: 1;
  text-decoration: underline;
}

.post-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}

.author {
  color: var(--bn-text-sub);
  font-size: 13px;
}

.author-name:hover {
  color: var(--bn-primary);
}

.post-cover {
  width: 132px;
  height: 96px;
  border-radius: var(--bn-radius-sm);
  overflow: hidden;
  flex-shrink: 0;
  align-self: center;
}

.post-cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
  transition: transform 0.25s ease;
}

.post-card:hover .post-cover img {
  transform: scale(1.04);
}

@media (max-width: 640px) {
  .post-cover {
    display: none;
  }
}
</style>
