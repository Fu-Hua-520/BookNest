<script setup>
/**
 * 帖子详情：正文渲染、点赞/收藏、评论与楼中楼回复
 */
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as postApi from '@/api/post'
import * as chatApi from '@/api/chat'
import { fromNow } from '@/utils/format'
import { renderMarkdown } from '@/utils/markdown'
import { useUserStore } from '@/stores/user'
import BnAvatar from '@/components/BnAvatar.vue'
import FollowButton from '@/components/FollowButton.vue'
import PostCard from '@/components/PostCard.vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const post = ref(null)
const comments = ref([])
const relatedPosts = ref([])

const loading = ref(true)
const error = ref('')
const commentsLoading = ref(false)
const submitting = ref(false)
const likingPost = ref(false)
const collectingPost = ref(false)

const liked = ref(false)
const collected = ref(false)

/** 回复目标：为空表示发顶层评论 */
const replyTarget = reactive({ id: '', userName: '' })
const commentContent = ref('')

const postId = computed(() => String(route.params.id || ''))
const isAuthor = computed(
  () => Boolean(userStore.userId) && String(post.value?.authorId) === String(userStore.userId)
)
const contentHtml = computed(() => renderMarkdown(post.value?.content || ''))

/** 评论按父子层级分组：顶级评论 + replies */
const commentTree = computed(() => {
  const list = comments.value || []
  const tops = list.filter((item) => !item.replyId)
  const repliesOf = (parentId) =>
    list.filter((item) => item.replyId && String(item.replyId) === String(parentId))
  return tops.map((top) => ({ ...top, replies: repliesOf(top.id) }))
})

async function loadPost() {
  loading.value = true
  error.value = ''
  try {
    post.value = await postApi.getPostDetail(postId.value)
    loadInteractionStatus()
  } catch (err) {
    error.value = err.message || '帖子加载失败'
  } finally {
    loading.value = false
  }
}

async function loadInteractionStatus() {
  if (!userStore.isLoggedIn) return
  const [likeResult, collectResult] = await Promise.allSettled([
    postApi.getPostLikeStatus(postId.value),
    postApi.getPostCollectStatus(postId.value)
  ])
  if (likeResult.status === 'fulfilled') liked.value = Boolean(likeResult.value)
  if (collectResult.status === 'fulfilled') collected.value = Boolean(collectResult.value)
}

async function loadComments() {
  commentsLoading.value = true
  try {
    comments.value = await postApi.listComments(postId.value)
  } catch {
    comments.value = []
  } finally {
    commentsLoading.value = false
  }
}

/** 同分类下取其他帖子作为「相关阅读」 */
async function loadRelated() {
  if (!post.value?.categoryId) return
  try {
    const list = await postApi.listPosts({
      categoryId: post.value.categoryId,
      auditStatus: 1,
      page: 1,
      pageSize: 5
    })
    relatedPosts.value = (list || []).filter((item) => String(item.id) !== postId.value).slice(0, 4)
  } catch {
    relatedPosts.value = []
  }
}

function requireLogin() {
  if (userStore.isLoggedIn) return true
  ElMessage.info('请先登录')
  router.push({ name: 'login', query: { redirect: route.fullPath } })
  return false
}

async function toggleLike() {
  if (!requireLogin()) return
  likingPost.value = true
  try {
    const result = await postApi.togglePostLike(postId.value)
    liked.value = Boolean(result.liked)
    if (post.value) post.value.likeCount = result.likeCount
  } finally {
    likingPost.value = false
  }
}

async function toggleCollect() {
  if (!requireLogin()) return
  collectingPost.value = true
  try {
    const result = await postApi.togglePostCollect(postId.value)
    collected.value = Boolean(result.collected)
    if (post.value) post.value.collectCount = result.collectCount
    ElMessage.success(collected.value ? '已加入收藏' : '已取消收藏')
  } finally {
    collectingPost.value = false
  }
}

function onReply(comment) {
  if (!requireLogin()) return
  replyTarget.id = comment.id
  replyTarget.userName = comment.userName || '书友'
}

function cancelReply() {
  replyTarget.id = ''
  replyTarget.userName = ''
}

async function submitComment() {
  if (!requireLogin()) return
  const content = commentContent.value.trim()
  if (!content) {
    ElMessage.warning('评论内容不能为空')
    return
  }

  submitting.value = true
  try {
    await postApi.publishComment(postId.value, {
      content,
      replyId: replyTarget.id || undefined
    })
    ElMessage.success('评论成功')
    commentContent.value = ''
    cancelReply()
    await Promise.all([loadComments(), loadPost()])
  } finally {
    submitting.value = false
  }
}

async function toggleCommentLike(comment) {
  if (!requireLogin()) return
  try {
    const result = await postApi.toggleCommentLike(comment.id)
    comment.liked = Boolean(result.liked)
    comment.likeCount = result.likeCount
  } catch {
    /* ignore */
  }
}

async function removeComment(comment) {
  try {
    await ElMessageBox.confirm('确定删除这条评论吗？', '删除确认', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消'
    })
  } catch {
    return
  }
  await postApi.deleteComment(comment.id)
  ElMessage.success('已删除')
  await Promise.all([loadComments(), loadPost()])
}

async function removePost() {
  try {
    await ElMessageBox.confirm('删除后无法恢复，确定删除这篇书评吗？', '删除确认', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消'
    })
  } catch {
    return
  }
  await postApi.deletePost(postId.value)
  ElMessage.success('书评已删除')
  router.replace('/')
}

/** 私信作者 */
async function startChat() {
  if (!requireLogin()) return
  if (isAuthor.value) {
    ElMessage.info('这是你自己的帖子')
    return
  }
  const convId = await chatApi.getOrCreateConversation(post.value.authorId)
  router.push({ name: 'chat', query: { conv: convId } })
}

function canDeleteComment(comment) {
  return Boolean(userStore.userId) && String(comment.userId) === String(userStore.userId)
}

onMounted(async () => {
  await loadPost()
  loadComments()
  loadRelated()
})

watch(postId, async () => {
  await loadPost()
  loadComments()
  loadRelated()
})
</script>

<template>
  <div class="bn-container">
    <el-breadcrumb separator="/" class="crumb">
      <el-breadcrumb-item :to="{ path: '/' }">首页</el-breadcrumb-item>
      <el-breadcrumb-item v-if="post?.categoryId" :to="{ path: `/category/${post.categoryId}` }">
        {{ post.categoryName || '分类' }}
      </el-breadcrumb-item>
      <el-breadcrumb-item>正文</el-breadcrumb-item>
    </el-breadcrumb>

    <el-skeleton v-if="loading" class="bn-mt-16" :rows="8" animated />

    <el-alert v-else-if="error" :title="error" type="error" :closable="false" show-icon />

    <template v-else-if="post">
      <div class="detail-layout">
        <article class="bn-card main-card">
          <!-- 标签区 -->
          <div class="flags">
            <el-tag v-if="post.isTop === 1" type="danger" size="small" effect="plain">置顶</el-tag>
            <span v-if="post.categoryName" class="bn-tag">{{ post.categoryName }}</span>
            <router-link v-if="post.bookId" :to="`/book/${post.bookId}`" class="bn-tag book-tag">
              《{{ post.bookTitle || '关联书籍' }}》
            </router-link>
          </div>

          <!-- 标题与作者 -->
          <h1 class="title">{{ post.title }}</h1>

          <div class="author-row">
            <BnAvatar
              :src="post.authorAvatar"
              :name="post.authorName"
              :size="40"
              :user-id="post.authorId"
            />
            <div class="author-meta">
              <router-link :to="`/user/${post.authorId}`" class="author-name">
                {{ post.authorName || '匿名书友' }}
              </router-link>
              <div class="bn-meta">
                <span class="bn-meta-item">
                  <el-icon><Clock /></el-icon>{{ fromNow(post.publishTime) }}
                </span>
                <span class="bn-meta-item">
                  <el-icon><View /></el-icon>{{ post.viewCount || 0 }} 阅读
                </span>
              </div>
            </div>
            <div class="author-actions">
              <FollowButton v-if="post.authorId" :user-id="post.authorId" size="small" />
              <el-button
                v-if="!isAuthor"
                size="small"
                plain
                @click="startChat"
              >
                <el-icon style="margin-right: 3px"><ChatLineRound /></el-icon>私信
              </el-button>
            </div>
          </div>

          <el-divider />

          <!-- 正文 -->
          <div class="bn-markdown" v-html="contentHtml" />

          <!-- 标签 -->
          <div v-if="post.tags?.length" class="post-tags">
            <router-link v-for="tag in post.tags" :key="tag.id" :to="`/tag/${tag.id}`" class="bn-tag">
              #{{ tag.name }}
            </router-link>
          </div>

          <el-divider />

          <!-- 底部操作 -->
          <div class="actions-bar">
            <el-button
              :type="liked ? 'primary' : 'default'"
              :loading="likingPost"
              round
              @click="toggleLike"
            >
              <el-icon style="margin-right: 4px">
                <component :is="liked ? 'StarFilled' : 'Star'" />
              </el-icon>
              {{ liked ? '已赞' : '点赞' }} {{ post.likeCount || 0 }}
            </el-button>

            <el-button round @click="toggleCollect" :loading="collectingPost">
              <el-icon style="margin-right: 4px">
                <component :is="collected ? 'CollectionTag' : 'Collection'" />
              </el-icon>
              {{ collected ? '已收藏' : '收藏' }} {{ post.collectCount || 0 }}
            </el-button>

            <el-button round disabled>
              <el-icon style="margin-right: 4px"><ChatDotRound /></el-icon>
              评论 {{ post.commentCount || 0 }}
            </el-button>

            <div class="owner-actions" v-if="isAuthor">
              <el-button size="small" text @click="router.push(`/post/edit/${post.id}`)">
                <el-icon><EditPen /></el-icon>编辑
              </el-button>
              <el-button size="small" text type="danger" @click="removePost">
                <el-icon><Delete /></el-icon>删除
              </el-button>
            </div>
          </div>
        </article>

        <!-- 相关阅读 -->
        <aside v-if="relatedPosts.length" class="related">
          <div class="bn-card">
            <h3 class="related-title">相关阅读</h3>
            <router-link
              v-for="item in relatedPosts"
              :key="item.id"
              :to="`/post/${item.id}`"
              class="related-item"
            >
              <p class="bn-ellipsis-2 related-name">{{ item.title }}</p>
              <span class="bn-text-muted related-time">{{ fromNow(item.publishTime) }}</span>
            </router-link>
          </div>
        </aside>
      </div>

      <!-- 评论区 -->
      <section class="bn-card comment-card">
        <div class="bn-section-title" style="margin-top: 0">
          <h2>评论 {{ comments.length ? `(${comments.length})` : '' }}</h2>
        </div>

        <!-- 评论输入 -->
        <div class="comment-editor">
          <BnAvatar
            v-if="userStore.isLoggedIn"
            :src="userStore.avatar"
            :name="userStore.username"
            :size="36"
            :linkable="false"
          />
          <div class="editor-body">
            <el-input
              v-model="commentContent"
              type="textarea"
              :rows="3"
              :placeholder="
                replyTarget.id ? `回复 @${replyTarget.userName}：` : '写下你的看法，友善交流…'
              "
              maxlength="500"
              show-word-limit
            />
            <div v-if="replyTarget.id" class="reply-hint">
              <span>
                正在回复
                <b>@{{ replyTarget.userName }}</b>
              </span>
              <el-button link size="small" @click="cancelReply">取消</el-button>
            </div>
            <div class="editor-actions">
              <el-button
                type="primary"
                size="small"
                :loading="submitting"
                @click="submitComment"
              >
                {{ replyTarget.id ? '发表回复' : '发表评论' }}
              </el-button>
            </div>
          </div>
        </div>

        <el-skeleton v-if="commentsLoading" class="bn-mt-16" :rows="3" animated />

        <template v-else>
          <div v-if="!commentTree.length" class="bn-empty">还没有评论，来说点什么吧</div>

          <div v-for="comment in commentTree" :key="comment.id" class="comment">
            <BnAvatar
              :src="comment.userAvatar"
              :name="comment.userName"
              :size="36"
              :user-id="comment.userId"
            />
            <div class="comment-body">
              <div class="comment-head">
                <router-link :to="`/user/${comment.userId}`" class="comment-name">
                  {{ comment.userName || '匿名书友' }}
                </router-link>
                <span class="bn-text-muted comment-time">{{ fromNow(comment.createTime) }}</span>
              </div>
              <p class="comment-content">{{ comment.content }}</p>

              <div class="comment-ops">
                <el-button
                  link
                  size="small"
                  :class="{ liked: comment.liked }"
                  @click="toggleCommentLike(comment)"
                >
                  <el-icon><Star /></el-icon>
                  {{ comment.likeCount ? comment.likeCount : '赞' }}
                </el-button>
                <el-button link size="small" @click="onReply(comment)">回复</el-button>
                <el-button
                  v-if="canDeleteComment(comment)"
                  link
                  size="small"
                  type="danger"
                  @click="removeComment(comment)"
                >
                  删除
                </el-button>
              </div>

              <!-- 楼中楼 -->
              <div v-if="comment.replies?.length" class="replies">
                <div v-for="reply in comment.replies" :key="reply.id" class="reply">
                  <BnAvatar
                    :src="reply.userAvatar"
                    :name="reply.userName"
                    :size="28"
                    :user-id="reply.userId"
                  />
                  <div class="reply-body">
                    <div class="comment-head">
                      <router-link :to="`/user/${reply.userId}`" class="comment-name">
                        {{ reply.userName || '匿名书友' }}
                      </router-link>
                      <span v-if="reply.replyToUserName" class="reply-to">
                        回复 @{{ reply.replyToUserName }}
                      </span>
                      <span class="bn-text-muted comment-time">{{ fromNow(reply.createTime) }}</span>
                    </div>
                    <p class="comment-content">{{ reply.content }}</p>
                    <div class="comment-ops">
                      <el-button
                        link
                        size="small"
                        :class="{ liked: reply.liked }"
                        @click="toggleCommentLike(reply)"
                      >
                        <el-icon><Star /></el-icon>
                        {{ reply.likeCount ? reply.likeCount : '赞' }}
                      </el-button>
                      <el-button link size="small" @click="onReply(reply)">回复</el-button>
                      <el-button
                        v-if="canDeleteComment(reply)"
                        link
                        size="small"
                        type="danger"
                        @click="removeComment(reply)"
                      >
                        删除
                      </el-button>
                    </div>
                  </div>
                </div>
              </div>
            </div>
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

.detail-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 244px;
  gap: 20px;
  align-items: start;
}

.main-card {
  padding: 24px 26px 20px;
}

.flags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 10px;
}

.book-tag {
  background: #eef4f8;
  color: #3f6d8e;
  border-color: #d8e6f0;
}

.title {
  font-size: 25px;
  line-height: 1.4;
  margin-bottom: 16px;
}

.author-row {
  display: flex;
  align-items: center;
  gap: 11px;
  flex-wrap: wrap;
}

.author-meta {
  flex: 1;
  min-width: 0;
}

.author-name {
  font-size: 14.5px;
  font-weight: 600;
}

.author-name:hover {
  color: var(--bn-primary);
}

.author-actions {
  display: flex;
  gap: 8px;
  align-items: center;
}

.post-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 20px;
}

.actions-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.owner-actions {
  margin-left: auto;
  display: flex;
  gap: 4px;
}

.related {
  position: sticky;
  top: 78px;
}

.related-title {
  font-size: 14.5px;
  margin-bottom: 10px;
}

.related-item {
  display: block;
  padding: 9px 0;
  border-bottom: 1px dashed var(--bn-border);
}

.related-item:last-child {
  border-bottom: none;
  padding-bottom: 0;
}

.related-name {
  font-size: 13px;
  line-height: 1.5;
  margin-bottom: 3px;
}

.related-item:hover .related-name {
  color: var(--bn-primary);
}

.related-time {
  font-size: 11.5px;
}

.comment-card {
  margin-top: 16px;
  padding: 20px 26px 22px;
}

.comment-editor {
  display: flex;
  gap: 11px;
  align-items: flex-start;
}

.editor-body {
  flex: 1;
  min-width: 0;
}

.reply-hint {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 12.5px;
  color: var(--bn-text-muted);
  margin-top: 6px;
  padding: 4px 8px;
  background: var(--bn-primary-soft);
  border-radius: 5px;
}

.editor-actions {
  display: flex;
  justify-content: flex-end;
  margin-top: 8px;
}

.comment {
  display: flex;
  gap: 11px;
  padding: 16px 0;
  border-bottom: 1px solid #f4f0ec;
}

.comment:last-child {
  border-bottom: none;
  padding-bottom: 0;
}

.comment-body {
  flex: 1;
  min-width: 0;
}

.comment-head {
  display: flex;
  align-items: center;
  gap: 9px;
  flex-wrap: wrap;
}

.comment-name {
  font-size: 13.5px;
  font-weight: 600;
}

.comment-name:hover {
  color: var(--bn-primary);
}

.comment-time {
  font-size: 12px;
}

.reply-to {
  font-size: 12px;
  color: var(--bn-primary);
}

.comment-content {
  font-size: 14px;
  line-height: 1.72;
  margin: 6px 0 7px;
  white-space: pre-wrap;
  word-break: break-word;
}

.comment-ops {
  display: flex;
  align-items: center;
  gap: 12px;
}

.comment-ops :deep(.el-button) {
  padding: 0;
  font-size: 12.5px;
  color: var(--bn-text-muted);
  height: auto;
}

.comment-ops :deep(.el-button.liked) {
  color: var(--bn-accent);
}

.replies {
  margin-top: 10px;
  padding: 10px 12px;
  background: #faf8f6;
  border-radius: var(--bn-radius-sm);
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.reply {
  display: flex;
  gap: 9px;
}

.reply-body {
  flex: 1;
  min-width: 0;
}

@media (max-width: 1000px) {
  .detail-layout {
    grid-template-columns: 1fr;
  }
  .related {
    display: none;
  }
}
</style>
