<script setup>
/**
 * 帖子详情：正文渲染、点赞/收藏、评论与楼中楼回复
 */
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as postApi from '@/api/post'
import * as barApi from '@/api/bar'
import * as chatApi from '@/api/chat'
import * as botApi from '@/api/bot'
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
/** 评论输入框容器：点「回复」后要滚到这里，否则在长楼里找不到输入框 */
const commentEditorWrapRef = ref(null)

/* ---------------- 评论区 AI 机器人 ---------------- */

/**
 * 我自己的、当前可用的机器人（已过审 + 未停用），评论框的「召唤我的 AI」选择器用它。
 * 只列自己的 —— 别人的机器人不在这里露面，想用就在评论里自己打 @名字。
 * 拿不到就退化成空列表，选择器不显示，评论区照常可用。
 */
const myBots = ref([])
/**
 * 全站机器人的名字。
 *
 * **只用来判断「这条评论值不值得等回复」，绝不在界面上渲染任何列表** ——
 * 界面上不展示别人的机器人，但用户 @ 了别人的机器人时，
 * 我们仍然得知道有回复会回来，否则那条回复他要刷新才看得见。
 */
const botNames = ref([])
const botPickerVisible = ref(false)
/** 评论输入框实例：要把 @机器人名 插进光标处，而不是无脑堆到末尾 */
const commentEditorRef = ref(null)
/** 发完评论后是否正在轮询等 AI 回复 */
const aiWaiting = ref(false)
let aiPollTimer = null

/**
 * AI 回复要调外部大模型，慢的话十几秒，前端不能发完就干等。
 * 有限轮询：拿到新的机器人评论就停，超过轮数也停，避免一直打接口。
 */
const AI_POLL_INTERVAL = 3000
const AI_POLL_ROUNDS = 20

/** 跟评默认展示条数（按点赞数取最高的几条），其余折起来 */
const REPLY_PREVIEW_COUNT = 2
/** 已展开跟评的楼层 id；展开后该楼回复按时间顺序全量展示 */
const expandedReplies = ref(new Set())

function isRepliesExpanded(commentId) {
  return expandedReplies.value.has(String(commentId))
}

function toggleReplies(commentId) {
  const next = new Set(expandedReplies.value)
  const key = String(commentId)
  if (next.has(key)) next.delete(key)
  else next.add(key)
  expandedReplies.value = next
}

const postId = computed(() => String(route.params.id || ''))
const isAuthor = computed(
  () => Boolean(userStore.userId) && String(post.value?.authorId) === String(userStore.userId)
)
const contentHtml = computed(() => renderMarkdown(post.value?.content || ''))

/**
 * 待审 / 已下架。
 * 作者本人能在详情页预览自己刚发的帖子，但这篇还没进入公开流通，
 * 点赞收藏必须锁上（后端 togglePostLike/togglePostCollect 也会再拦一次）。
 */
const pendingAudit = computed(() => Number(post.value?.auditStatus) !== 1)

/* ---------------- 吧务：隐藏 / 恢复吧内帖 ---------------- */

/**
 * 当前用户在这个吧里的角色（OWNER / MODERATOR / MEMBER / NONE）。
 * 只有吧主与管理员能隐藏帖子，所以取不到就当没有权限。
 */
const barRole = ref('')
const moderating = ref(false)

const canModerate = computed(() => ['OWNER', 'MODERATOR'].includes(barRole.value))
/** 已下架（被吧务隐藏）但审核已通过 */
const isHidden = computed(
  () => Number(post.value?.auditStatus) === 1 && Number(post.value?.status) !== 1
)
/** 点赞 / 收藏是否锁上：待审或被隐藏都不该攒互动（后端也会再拦一次） */
const interactionLocked = computed(() => pendingAudit.value || isHidden.value)

async function loadBarRole() {
  barRole.value = ''
  const barId = post.value?.categoryId
  if (!barId || !userStore.isLoggedIn) return
  try {
    barRole.value = (await barApi.getMembership(barId))?.role || ''
  } catch {
    barRole.value = ''
  }
}

async function toggleHidden() {
  if (!canModerate.value) return
  const nextVisible = isHidden.value
  try {
    await ElMessageBox.confirm(
      nextVisible ? '恢复后这篇帖子会重新出现在吧里，确定吗？' : '隐藏后其他书友就看不到了，确定吗？',
      nextVisible ? '恢复帖子' : '隐藏帖子',
      { type: 'warning', confirmButtonText: nextVisible ? '恢复' : '隐藏', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  moderating.value = true
  try {
    await barApi.setPostVisible(post.value.categoryId, postId.value, nextVisible)
    post.value.status = nextVisible ? 1 : 3
    ElMessage.success(nextVisible ? '已恢复' : '已隐藏')
  } catch {
    // 拦截器已提示
  } finally {
    moderating.value = false
  }
}

/**
 * 评论楼层（B站式两层结构）。
 *
 * 后端存的 replyId 是「被回复的那一条评论」的 id，可以是楼内任意一条，
 * 所以数据本身是一棵树。展示上按 B 站做法拍平成两层：
 *   - 楼 = 顶级评论（没有 replyId 的那条），楼内所有后代回复平铺排列；
 *   - 回复「楼内的其他回复」时用 replyToUserName 渲染成「回复 @某人」。
 *
 * 必须沿 replyId 一路向上找楼层根：否则回复别人的回复时，它的 replyId 既不是
 * 顶级 id、也不等于任何一楼的 id，会既进不了 tops 也匹配不到 replies，
 * 直接从页面上消失（表现为「只能对一级评论回复」）。
 */
const commentTree = computed(() => {
  const list = comments.value || []
  const byId = new Map(list.map((item) => [String(item.id), item]))

  // 沿 replyId 向上走到没有 replyId 的顶级评论；父级已被删除时把当前这条当楼根，避免评论凭空消失
  function findRootId(item) {
    let cur = item
    const seen = new Set([String(item.id)])
    while (cur && cur.replyId) {
      const parent = byId.get(String(cur.replyId))
      if (!parent) return String(cur.id)
      if (seen.has(String(parent.id))) break // 脏数据成环时兜底
      seen.add(String(parent.id))
      cur = parent
    }
    return String(cur.id)
  }

  const floors = new Map() // 楼层根 id → { top, replies[] }
  list.forEach((item) => {
    const rootId = findRootId(item)
    if (!floors.has(rootId)) floors.set(rootId, { top: null, replies: [] })
    const floor = floors.get(rootId)
    if (String(item.id) === rootId) floor.top = item
    else floor.replies.push(item)
  })

  // 后端已按 create_time 正序返回，Map 保留插入顺序 → 楼层间与楼内回复天然都是时间正序
  return [...floors.values()]
    .filter((floor) => floor.top)
    .map(({ top, replies }) => {
      const mapped = replies.map((reply) => ({
        ...reply,
        // 直接回复楼主的不用再 @ 一遍（楼头就是那个人），回复楼内其他回复才显示 @
        showReplyTo:
          String(reply.replyId) !== String(top.id) && Boolean(reply.replyToUserName)
      }))
      // 跟评默认只露点赞最高的两条：楼太长时把整楼压成一条「回复列表」，
      // 真正的讨论反而被埋在下面。按赞数取前两条，其余折进「展开」。
      // 赞数相同时按原顺序（时间正序）稳定排列，避免每次渲染位置乱跳。
      const shown = [...mapped]
        .sort((a, b) => (Number(b.likeCount) || 0) - (Number(a.likeCount) || 0))
        .slice(0, REPLY_PREVIEW_COUNT)
      const shownIds = new Set(shown.map((item) => String(item.id)))
      return {
        ...top,
        // 展开后要按原始时间顺序展示，先把高赞两条标出来
        replies: mapped.map((reply) => ({
          ...reply,
          isHotReply: shownIds.has(String(reply.id))
        })),
        replyCount: mapped.length
      }
    })
})

async function loadPost() {
  loading.value = true
  error.value = ''
  try {
    post.value = await postApi.getPostDetail(postId.value)
    loadInteractionStatus()
    loadBarRole()
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

/** 求助贴标记：与 PostCard 一致的判定口径（后端 post_type 白名单只会有 NORMAL/HELP） */
const isHelp = computed(() => String(post.value?.postType || '').toUpperCase() === 'HELP')

/* ---------------- 互动：点赞 / 收藏 ---------------- */

function requireLogin() {
  if (userStore.isLoggedIn) return true
  ElMessage.info('请先登录')
  router.push({ name: 'login', query: { redirect: route.fullPath } })
  return false
}

async function toggleLike() {
  if (interactionLocked.value) {
    ElMessage.info('帖子还在审核中，暂不支持点赞')
    return
  }
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
  if (interactionLocked.value) {
    ElMessage.info('帖子还在审核中，暂不支持收藏')
    return
  }
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

/**
 * 拉「我的机器人」里当下真正可用的那些（已过审 + 未停用）。
 *
 * 不拉全站列表：别人的机器人不该出现在我的选择器里 —— 那等于替它们做了推广。
 * 想召唤别人的机器人，在评论里自己打 @名字 即可。
 *
 * ⚠️ `/bot/**` 全在登录拦截范围内（GET 也不放行），游客调必然 401；而 axios 拦截器
 * 对 401 会**清令牌并跳登录页** —— 帖子详情是游客可见页，不挡的话一进详情就被踢走。
 * 游客本来也发不了评论，直接跳过即可。
 */
async function loadMyBots() {
  if (!userStore.isLoggedIn) {
    myBots.value = []
    return
  }
  try {
    const list = (await botApi.listMyBots()) || []
    // 待审 / 已驳回 / 已停用的机器人根本不会回复，列出来只会让用户白等
    myBots.value = list.filter((bot) => Number(bot.auditStatus) === 1 && Number(bot.enabled) === 1)
  } catch {
    myBots.value = []
  }
}

/**
 * 拿全站机器人的名字 —— 只为 {@link mentionsBot} 服务，不渲染。
 * 与 loadMyBots 的登录判断同理，游客直接跳过。
 */
async function loadBotNames() {
  if (!userStore.isLoggedIn) {
    botNames.value = []
    return
  }
  try {
    const list = (await botApi.listAvailableBots()) || []
    botNames.value = list.map((bot) => bot.name)
  } catch {
    botNames.value = []
  }
}

/** 这条评论是 AI 机器人发的（user_id 为空、bot_id 有值） */
function isBotComment(comment) {
  return Boolean(comment?.botId)
}

/**
 * 正文里是否 @ 了任意一个机器人 —— 只有 @ 了才值得等回复。
 *
 * 认的是**全站**名字而不是「我的机器人」：用户 @ 别人的机器人时，
 * 那条回复同样会来，不轮询的话他就得自己刷新才看得到。
 */
function mentionsBot(content) {
  const text = content || ''
  return botNames.value.some((name) => text.includes(`@${name}`))
}

/** 把 @机器人名 插到光标处；拿不到光标信息（如未聚焦）就追加到末尾 */
function insertBotMention(bot) {
  const mention = `@${bot.name} `
  const el = commentEditorRef.value?.textarea
  if (el && typeof el.selectionStart === 'number') {
    const start = el.selectionStart
    const end = el.selectionEnd
    const text = commentContent.value || ''
    commentContent.value = text.slice(0, start) + mention + text.slice(end)
    nextTick(() => {
      el.focus()
      const pos = start + mention.length
      el.setSelectionRange(pos, pos)
    })
  } else {
    commentContent.value = `${commentContent.value || ''}${mention}`
  }
  botPickerVisible.value = false
}

function stopAiPolling() {
  if (aiPollTimer) {
    clearInterval(aiPollTimer)
    aiPollTimer = null
  }
  aiWaiting.value = false
}

/**
 * 轮询等待 AI 回复。
 * @param {Set<string>} knownIds 发布评论前已有的评论 id 集合，用来判断「新到达的机器人评论」
 */
function startAiPolling(knownIds) {
  stopAiPolling()
  aiWaiting.value = true
  let rounds = 0
  aiPollTimer = setInterval(async () => {
    rounds += 1
    if (rounds > AI_POLL_ROUNDS) {
      stopAiPolling()
      return
    }
    try {
      const list = (await postApi.listComments(postId.value)) || []
      comments.value = list
      const arrived = list.some((item) => item.botId && !knownIds.has(String(item.id)))
      if (arrived) {
        stopAiPolling()
        ElMessage.success('AI 已回复')
      }
    } catch {
      stopAiPolling()
    }
  }, AI_POLL_INTERVAL)
}

function onReply(comment) {
  if (!requireLogin()) return
  replyTarget.id = comment.id
  replyTarget.userName = comment.userName || '书友'
  // 输入框在评论区顶部，楼一长就滚出屏幕了 —— 点回复后主动滚回去并聚焦，
  // 否则用户点完没反应，还得自己往上找输入框。
  nextTick(() => {
    commentEditorWrapRef.value?.scrollIntoView({ behavior: 'smooth', block: 'center' })
    commentEditorRef.value?.focus()
  })
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
    // 先记下当前已有的评论 id，一会儿用「多出来的机器人评论」判断 AI 是否回完
    const knownIds = new Set((comments.value || []).map((item) => String(item.id)))
    commentContent.value = ''
    cancelReply()
    await Promise.all([loadComments(), loadPost()])
    // 只有 @ 了机器人才值得轮询等待；没 @ 就别白打接口
    if (mentionsBot(content)) startAiPolling(knownIds)
  } finally {
    submitting.value = false
  }
}

/**
 * 点赞状态回写。
 * commentTree 是 computed 生成的展示副本（{...top} / {...reply}），模板里拿到的
 * comment 并不是响应式源对象，直接改 comment.likeCount 不会触发重渲染，
 * 所以必须按 id 找到 comments 源数组里的那条去改。
 */
function writeCommentLikeState(commentId, liked, likeCount) {
  const source = (comments.value || []).find((item) => String(item.id) === String(commentId))
  if (!source) return
  source.liked = Boolean(liked)
  source.likeCount = likeCount
}

async function toggleCommentLike(comment) {
  if (!requireLogin()) return
  const previous = { liked: Boolean(comment.liked), likeCount: Number(comment.likeCount) || 0 }
  const optimisticLiked = !previous.liked
  // 先本地翻转，点赞数立刻可见；接口回来后用后端返回的准数覆盖，失败则回滚
  writeCommentLikeState(
    comment.id,
    optimisticLiked,
    Math.max(0, previous.likeCount + (optimisticLiked ? 1 : -1))
  )
  try {
    const result = await postApi.toggleCommentLike(comment.id)
    writeCommentLikeState(comment.id, result.liked, result.likeCount)
  } catch {
    writeCommentLikeState(comment.id, previous.liked, previous.likeCount)
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

/**
 * 谁能删这条评论：
 *   - 普通评论 → 本人；
 *   - 机器人评论 → 该机器人的创建者（评论行的 user_id 是空的，只能靠 botOwnerId 判）。
 */
function canDeleteComment(comment) {
  if (!userStore.userId) return false
  if (isBotComment(comment)) {
    return String(comment.botOwnerId) === String(userStore.userId)
  }
  return String(comment.userId) === String(userStore.userId)
}

/* ---------------- 从「回复通知」跳进来的定位 ---------------- */

/** 当前高亮的评论 ID（定位后短暂高亮，2.6s 后自动消失） */
const focusedCommentId = ref('')
let focusTimer = null

/**
 * 回复通知的 URL 上带 ?comment=<评论ID>。
 * 评论区是异步加载的，必须等渲染完再按 id 找元素，否则查不到、只会停在帖子顶部。
 */
async function focusCommentFromQuery() {
  const targetId = String(route.query.comment || '')
  if (!targetId) return
  await nextTick()
  const el = document.getElementById(`comment-${targetId}`)
  if (!el) return
  el.scrollIntoView({ behavior: 'smooth', block: 'center' })
  focusedCommentId.value = targetId
  if (focusTimer) clearTimeout(focusTimer)
  focusTimer = setTimeout(() => {
    focusedCommentId.value = ''
  }, 2600)
}

onMounted(async () => {
  loadMyBots()
  loadBotNames()
  await loadPost()
  await loadComments()
  loadRelated()
  focusCommentFromQuery()
})

watch(postId, async () => {
  stopAiPolling()
  await loadPost()
  await loadComments()
  loadRelated()
  focusCommentFromQuery()
})

/** 同一篇帖子内再点另一条通知（只变 query）也要重新定位 */
watch(
  () => route.query.comment,
  () => focusCommentFromQuery()
)

onBeforeUnmount(() => {
  if (focusTimer) clearTimeout(focusTimer)
  stopAiPolling()
})
</script>

<template>
  <div class="bn-container">
    <el-breadcrumb separator="/" class="crumb">
      <el-breadcrumb-item :to="{ path: '/' }">首页</el-breadcrumb-item>
      <el-breadcrumb-item v-if="post?.categoryId" :to="{ path: `/bars/${post.categoryId}` }">
        {{ post.categoryName || '书吧' }}
      </el-breadcrumb-item>
      <el-breadcrumb-item>正文</el-breadcrumb-item>
    </el-breadcrumb>

    <el-skeleton v-if="loading" class="bn-mt-16" :rows="8" animated />

    <el-alert v-else-if="error" :title="error" type="error" :closable="false" show-icon />

    <template v-else-if="post">
      <div class="detail-layout">
        <article class="bn-card main-card">
          <!-- 待审 / 已下架提示：作者本人能看到预览，但互动已锁 -->
          <el-alert
            v-if="pendingAudit"
            class="audit-tip"
            type="warning"
            :closable="false"
            show-icon
            :title="isAuthor ? '这篇帖子还在审核中，通过前只有你自己能看到' : '这篇帖子未通过审核或已被下架'"
            :description="post.auditReason || undefined"
          />
          <el-alert
            v-else-if="isHidden"
            class="audit-tip"
            type="info"
            :closable="false"
            show-icon
            :title="
              canModerate
                ? '这篇帖子已被吧务隐藏，其他书友看不到，只有吧务和你自己能打开'
                : '这篇帖子已被隐藏'
            "
          />
          <!-- 标签区：分类已改造成书吧，这里同时可点进对应吧 -->
          <div class="flags">
            <el-tag v-if="post.isTop === 1" type="danger" size="small" effect="plain">置顶</el-tag>
            <el-tag v-if="isHelp" type="warning" size="small" effect="dark">求助</el-tag>
            <router-link
              v-if="post.categoryId"
              :to="`/bars/${post.categoryId}`"
              class="bn-tag"
            >
              {{ post.categoryName || '未分类' }}
            </router-link>
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
              :disabled="interactionLocked"
              round
              @click="toggleLike"
            >
              <el-icon style="margin-right: 4px">
                <component :is="liked ? 'StarFilled' : 'Star'" />
              </el-icon>
              {{ liked ? '已赞' : '点赞' }} {{ post.likeCount || 0 }}
            </el-button>

            <el-button
              round
              :loading="collectingPost"
              :disabled="interactionLocked"
              @click="toggleCollect"
            >
              <el-icon style="margin-right: 4px">
                <component :is="collected ? 'CollectionTag' : 'Collection'" />
              </el-icon>
              {{ collected ? '已收藏' : '收藏' }} {{ post.collectCount || 0 }}
            </el-button>

            <el-button round disabled>
              <el-icon style="margin-right: 4px"><ChatDotRound /></el-icon>
              评论 {{ post.commentCount || 0 }}
            </el-button>

            <div class="owner-actions">
              <el-button
                v-if="canModerate"
                size="small"
                text
                :type="isHidden ? 'success' : 'warning'"
                :loading="moderating"
                @click="toggleHidden"
              >
                <el-icon><Hide /></el-icon>{{ isHidden ? '恢复' : '隐藏' }}
              </el-button>
              <template v-if="isAuthor">
                <el-button size="small" text @click="router.push(`/post/edit/${post.id}`)">
                  <el-icon><EditPen /></el-icon>编辑
                </el-button>
                <el-button size="small" text type="danger" @click="removePost">
                  <el-icon><Delete /></el-icon>删除
                </el-button>
              </template>
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
        <div ref="commentEditorWrapRef" class="comment-editor">
          <BnAvatar
            v-if="userStore.isLoggedIn"
            :src="userStore.avatar"
            :name="userStore.username"
            :size="36"
            :linkable="false"
          />
          <div class="editor-body">
            <el-input
              ref="commentEditorRef"
              v-model="commentContent"
              type="textarea"
              :rows="3"
              :placeholder="
                replyTarget.id
                  ? `回复 @${replyTarget.userName}：`
                  : '写下你的看法，友善交流…（打 @机器人名 可以召唤 AI）'
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
              <!-- @ 机器人：点一下把「@机器人名 」插到光标处，省得用户记名字 -->
              <el-popover
                v-if="myBots.length"
                v-model:visible="botPickerVisible"
                placement="bottom-start"
                :width="280"
                trigger="click"
              >
                <template #reference>
                  <el-button size="small" :disabled="!userStore.isLoggedIn">
                    <el-icon style="margin-right: 3px"><MagicStick /></el-icon>召唤我的 AI
                  </el-button>
                </template>
                <div class="bot-picker">
                  <p class="bot-picker-tip">这是我的机器人，点一下把名字插进评论里</p>
                  <button
                    v-for="bot in myBots"
                    :key="bot.id"
                    type="button"
                    class="bot-picker-item"
                    @click="insertBotMention(bot)"
                  >
                    <BnAvatar :src="bot.avatar" :name="bot.name" :size="26" :linkable="false" />
                    <span class="bot-picker-text">
                      <span class="bot-picker-name">{{ bot.name }}</span>
                      <span class="bot-picker-desc bn-ellipsis-1">
                        {{ bot.description || bot.providerLabel || 'AI 机器人' }}
                      </span>
                    </span>
                  </button>
                </div>
              </el-popover>
              <el-button
                type="primary"
                size="small"
                :loading="submitting"
                @click="submitComment"
              >
                {{ replyTarget.id ? '发表回复' : '发表评论' }}
              </el-button>
            </div>
            <!-- AI 回复是异步的，给个明确的等待反馈，别让用户以为没反应 -->
            <p v-if="aiWaiting" class="ai-waiting">
              <el-icon class="is-loading"><Loading /></el-icon>
              已召唤 AI，它正在思考，回复会自动出现在下面…
            </p>
          </div>
        </div>

        <el-skeleton v-if="commentsLoading" class="bn-mt-16" :rows="3" animated />

        <template v-else>
          <div v-if="!commentTree.length" class="bn-empty">还没有评论，来说点什么吧</div>

          <div
            v-for="comment in commentTree"
            :key="comment.id"
            :id="`comment-${comment.id}`"
            class="comment"
            :class="{ 'is-focused': focusedCommentId === comment.id }"
          >
            <BnAvatar
              :src="comment.userAvatar"
              :name="comment.userName"
              :size="36"
              :user-id="comment.userId"
            />
            <div class="comment-body">
              <div class="comment-head">
                <!-- AI 回复没有 user_id，不能往 /user/null 跳，改成纯文本 + AI 角标 -->
                <template v-if="isBotComment(comment)">
                  <span class="comment-name is-bot">{{ comment.userName || 'AI 机器人' }}</span>
                  <span class="ai-badge">AI</span>
                </template>
                <router-link v-else :to="`/user/${comment.userId}`" class="comment-name">
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

              <!-- 楼中楼：默认只露高赞两条，其余折起来 -->
              <div v-if="comment.replies?.length" class="replies">
                <div
                  v-for="reply in comment.replies"
                  v-show="isRepliesExpanded(comment.id) || reply.isHotReply"
                  :key="reply.id"
                  :id="`comment-${reply.id}`"
                  class="reply"
                  :class="{ 'is-focused': focusedCommentId === reply.id }"
                >
                  <BnAvatar
                    :src="reply.userAvatar"
                    :name="reply.userName"
                    :size="28"
                    :user-id="reply.userId"
                  />
                  <div class="reply-body">
                    <div class="comment-head">
                      <template v-if="isBotComment(reply)">
                        <span class="comment-name is-bot">{{ reply.userName || 'AI 机器人' }}</span>
                        <span class="ai-badge">AI</span>
                      </template>
                      <router-link v-else :to="`/user/${reply.userId}`" class="comment-name">
                        {{ reply.userName || '匿名书友' }}
                      </router-link>
                      <span v-if="reply.showReplyTo" class="reply-to">
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

                <!-- 跟评多于两条时给个出口；展开后可以再收起 -->
                <el-button
                  v-if="comment.replies.length > REPLY_PREVIEW_COUNT"
                  link
                  size="small"
                  class="replies-toggle"
                  @click="toggleReplies(comment.id)"
                >
                  <el-icon><component :is="isRepliesExpanded(comment.id) ? 'ArrowUp' : 'ArrowDown'" /></el-icon>
                  {{
                    isRepliesExpanded(comment.id)
                      ? '收起回复'
                      : `展开另外 ${comment.replies.length - REPLY_PREVIEW_COUNT} 条回复`
                  }}
                </el-button>
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

.audit-tip {
  margin-bottom: 14px;
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
  align-items: center;
  gap: 8px;
  margin-top: 8px;
}

/* AI 回复的异步等待提示 */
.ai-waiting {
  display: flex;
  align-items: center;
  gap: 6px;
  margin: 8px 0 0;
  font-size: 12.5px;
  color: var(--bn-primary);
}

/* ---------- 评论区的 @AI 选择器 ---------- */
.bot-picker {
  display: flex;
  flex-direction: column;
  gap: 2px;
  max-height: 280px;
  overflow-y: auto;
}

.bot-picker-tip {
  margin: 0 0 6px;
  font-size: 11.5px;
  color: var(--bn-text-muted);
}

.bot-picker-item {
  display: flex;
  align-items: center;
  gap: 9px;
  padding: 7px 8px;
  border: none;
  border-radius: 8px;
  background: transparent;
  font-family: inherit;
  text-align: left;
  cursor: pointer;
  min-width: 0;
}

.bot-picker-item:hover {
  background: var(--bn-primary-soft);
}

.bot-picker-text {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.bot-picker-name {
  font-size: 13px;
  font-weight: 600;
}

.bot-picker-desc {
  font-size: 11.5px;
  color: var(--bn-text-muted);
}

/* AI 评论的身份标识 */
.comment-name.is-bot {
  color: var(--bn-primary);
  font-weight: 600;
}

.ai-badge {
  display: inline-flex;
  align-items: center;
  padding: 0 5px;
  height: 16px;
  border-radius: 4px;
  background: var(--bn-primary-soft);
  color: var(--bn-primary);
  font-size: 10.5px;
  font-weight: 700;
  letter-spacing: 0.3px;
}

.comment {
  display: flex;
  gap: 11px;
  padding: 16px 0;
  border-bottom: 1px solid #f4f0ec;
  /* 从通知跳进来时用 scrollIntoView 定位，给吸顶导航留出高度，别被盖住 */
  scroll-margin-top: 96px;
}

/* 定位到的评论：短暂高亮一下，让用户一眼找到。
   padding + 负 margin 是为了让底色两侧有呼吸感、又不改变原有布局位置。 */
.comment.is-focused {
  padding-left: 12px;
  padding-right: 12px;
  margin-left: -12px;
  margin-right: -12px;
}

/* 只用横向 padding + 负 margin：纵向不加，否则 flex 里负 margin 会把上下间距吃掉、高亮时跳动 */
.reply.is-focused {
  padding: 0 8px;
  margin: 0 -8px;
}

.comment.is-focused,
.reply.is-focused {
  border-radius: var(--bn-radius-sm);
  animation: comment-flash 2.6s ease-out;
}

@keyframes comment-flash {
  0%,
  40% {
    background: #ffe6bf;
  }
  100% {
    background: transparent;
  }
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
  scroll-margin-top: 96px;
}

/* 展开 / 收起跟评的入口，跟楼内回复左对齐 */
.replies-toggle {
  margin-top: 2px;
  margin-left: 37px;
  font-size: 12.5px;
  color: var(--bn-text-sub);
}

.replies-toggle:hover {
  color: var(--bn-primary);
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
