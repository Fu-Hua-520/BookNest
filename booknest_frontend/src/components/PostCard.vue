<script setup>
/**
 * 帖子卡片：首页/分类/标签/搜索/个人主页共用
 *
 * 点击行为说明（曾出现的 bug）：
 * 早期只有「标题 / 封面 / 作者 / 分类 / 标签」是 <a>，卡片主体区域
 * （摘要文字、底部阅读数·点赞数·评论数、卡片右侧留白）没有任何点击处理器，
 * 点上去既不跳转也不发请求，表现就是「点帖子莫名其妙没反应，F12 里一条请求都没有」。
 * 现在整张卡片都是点击热区，内部已有的链接/按钮仍然各自生效。
 */
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import BnAvatar from './BnAvatar.vue'
import { formatCount, fromNow } from '@/utils/format'

const props = defineProps({
  post: { type: Object, required: true },
  /** 是否显示封面缩略图 */
  showCover: { type: Boolean, default: true }
})

const router = useRouter()

const post = computed(() => props.post || {})
const detailLink = computed(() => (post.value.id ? `/post/${post.value.id}` : ''))
const authorLink = computed(() => (post.value.authorId ? `/user/${post.value.authorId}` : ''))

/**
 * 分类已改造成贴吧式的「书吧」，所以这里跳转的是书吧页 /bars/:id。
 * 老路由 /category/:id 仍保留重定向（见 router/index.js），外部旧链接不会 404。
 */
const categoryLink = computed(() => (post.value.categoryId ? `/bars/${post.value.categoryId}` : ''))

/** 求助贴标记：后端 post_type 为 HELP 时在卡片上加醒目角标 */
const isHelp = computed(() => String(post.value.postType || '').toUpperCase() === 'HELP')

const cover = computed(() => post.value.coverImage || '')
const tags = computed(() => (post.value.tags || []).slice(0, 4))

/** 卡片整体可点：内部已有链接/按钮时放行，其余情况走详情页 */
function goDetail(event) {
  if (!detailLink.value) return
  // 目标是卡片内已有的 <a>/<button>，交给它们自己的默认行为
  if (event.target instanceof Element && event.target.closest('a, button')) return
  // 按住修饰键或中键：沿用浏览器「新标签页打开」的直觉
  if (event.metaKey || event.ctrlKey || event.shiftKey || event.button === 1) {
    window.open(detailLink.value, '_blank')
    return
  }
  router.push(detailLink.value)
}

/** 键盘可达：卡片可聚焦，Enter 进入详情 */
function goDetailByKey(event) {
  if (event.key !== 'Enter' || !detailLink.value) return
  event.preventDefault()
  router.push(detailLink.value)
}
</script>

<template>
  <article
    class="bn-card bn-card-hover post-card"
    :class="{ clickable: detailLink }"
    :tabindex="detailLink ? 0 : undefined"
    :aria-label="detailLink ? `查看帖子：${post.title || ''}` : undefined"
    @click="goDetail"
    @keydown="goDetailByKey"
  >
    <div class="post-body">
      <div class="post-main">
        <!-- 置顶 / 求助 / 书吧 / 关联书籍 -->
        <div class="post-flags">
          <el-tag v-if="post.isTop === 1" type="danger" size="small" effect="plain">置顶</el-tag>
          <el-tag v-if="isHelp" type="warning" size="small" effect="dark">求助</el-tag>
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
.post-card.clickable {
  cursor: pointer;
}

/* 键盘聚焦时给出可见反馈，鼠标点击不留描边 */
.post-card.clickable:focus-visible {
  outline: 2px solid var(--bn-primary);
  outline-offset: 2px;
}

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
