<script setup>
/**
 * 个人主页：资料卡 + 书评 / 书单 / 收藏 / 浏览历史 / 关注 / 粉丝
 *
 * 书评走 GET /post/list?userId=xxx，由后端按作者过滤，无需拉全量再本地筛选。
 * 「浏览历史」「我的收藏」只在看自己的主页时出现 —— 这两块是私有数据，
 * 后端接口也都取自登录态，不提供查看他人历史的入口。
 */
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as postApi from '@/api/post'
import * as booklistApi from '@/api/booklist'
import * as followApi from '@/api/follow'
import * as chatApi from '@/api/chat'
import * as userApi from '@/api/user'
import { fromNow } from '@/utils/format'
import { useUserStore } from '@/stores/user'
import BnAvatar from '@/components/BnAvatar.vue'
import BnCover from '@/components/BnCover.vue'
import PostCard from '@/components/PostCard.vue'
import FollowButton from '@/components/FollowButton.vue'
import BnState from '@/components/BnState.vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const userId = computed(() => String(route.params.id || ''))
const isSelf = computed(() => Boolean(userStore.userId) && userStore.userId === userId.value)

const tab = ref('posts')

/** 头像上传中 / 昵称编辑中（仅自己的主页可见这些操作） */
const avatarUploading = ref(false)
const editingName = ref(false)
const nameDraft = ref('')

const userPosts = ref([])
const postsPage = ref(1)
const postsHasMore = ref(false)
const myBooklists = ref([])
const following = ref([])
const followers = ref([])
const collected = ref([])
const collectedPage = ref(1)
const collectedHasMore = ref(false)
const history = ref([])
const historyPage = ref(1)
const historyHasMore = ref(false)

const loadingPosts = ref(false)
const loadingBooklists = ref(false)
const loadingFollow = ref(false)
const loadingCollected = ref(false)
const loadingHistory = ref(false)
const booklistsLoaded = ref(false)
const followLoaded = ref(false)
const collectedLoaded = ref(false)

/** 已加载条数；若还有下一页，说明这只是下限 */
const postCountText = computed(() =>
  postsHasMore.value ? `${userPosts.value.length}+` : String(userPosts.value.length)
)

/**
 * 作者资料：自己的主页直接读 store（换头像 / 改昵称后能立刻反映），
 * 别人的主页没有公开的用户详情接口，就从帖子、关注/粉丝列表里反推。
 */
const profile = computed(() => {
  if (isSelf.value) {
    return {
      name: userStore.username,
      avatar: userStore.avatar,
      id: userStore.userId
    }
  }
  const hit = userPosts.value[0]
  if (hit) {
    return {
      name: hit.authorName || '书友',
      avatar: hit.authorAvatar || '',
      id: userId.value
    }
  }
  const follow = [...following.value, ...followers.value].find(
    (item) => String(item.userId) === userId.value
  )
  if (follow) return { name: follow.username || '书友', avatar: follow.avatar || '', id: userId.value }
  return { name: '书友', avatar: '', id: userId.value }
})

const followCounts = computed(() => ({
  following: following.value.length,
  followers: followers.value.length
}))

const POST_PAGE_SIZE = 20
const LIST_PAGE_SIZE = 12

/**
 * 加载该用户的帖子（后端按 userId 过滤）
 * @param {boolean} reset 是否回到第一页：切换用户 / 首次进入时为 true
 */
async function loadPosts(reset = false) {
  if (reset) {
    postsPage.value = 1
    postsHasMore.value = true
  }
  if (!reset && !postsHasMore.value) return
  loadingPosts.value = true
  try {
    const list =
      (await postApi.listPosts({
        userId: userId.value,
        page: postsPage.value,
        pageSize: POST_PAGE_SIZE
      })) || []
    userPosts.value = reset ? list : [...userPosts.value, ...list]
    postsHasMore.value = list.length >= POST_PAGE_SIZE
    if (postsHasMore.value) postsPage.value += 1
  } catch {
    if (reset) userPosts.value = []
    postsHasMore.value = false
  } finally {
    loadingPosts.value = false
  }
}

async function loadBooklists() {
  if (booklistsLoaded.value) return
  loadingBooklists.value = true
  try {
    if (isSelf.value) {
      myBooklists.value = (await booklistApi.listMyBooklists()) || []
    } else {
      myBooklists.value = (await booklistApi.listBooklists({ userId: userId.value, page: 1, pageSize: 30 })) || []
    }
    booklistsLoaded.value = true
  } catch {
    myBooklists.value = []
  } finally {
    loadingBooklists.value = false
  }
}

async function loadFollow() {
  if (followLoaded.value) return
  loadingFollow.value = true
  try {
    const [followingResult, followersResult] = await Promise.allSettled([
      followApi.listFollowing(userId.value),
      followApi.listFollowers(userId.value)
    ])
    following.value = followingResult.status === 'fulfilled' ? followingResult.value || [] : []
    followers.value = followersResult.status === 'fulfilled' ? followersResult.value || [] : []
    followLoaded.value = true
  } finally {
    loadingFollow.value = false
  }
}

/** 我的收藏（仅自己） */
async function loadCollected(reset = false) {
  if (!isSelf.value) return
  if (reset) {
    collectedPage.value = 1
    collectedHasMore.value = true
    collectedLoaded.value = false
  }
  if (collectedLoaded.value) return
  loadingCollected.value = true
  try {
    const list = (await postApi.listMyCollectedPosts(collectedPage.value, LIST_PAGE_SIZE)) || []
    collected.value = reset ? list : [...collected.value, ...list]
    collectedHasMore.value = list.length >= LIST_PAGE_SIZE
    if (collectedHasMore.value) collectedPage.value += 1
    else collectedLoaded.value = true
  } catch {
    if (reset) collected.value = []
    collectedHasMore.value = false
  } finally {
    loadingCollected.value = false
  }
}

/** 浏览历史（仅自己） */
async function loadHistory(reset = false) {
  if (!isSelf.value) return
  if (reset) historyPage.value = 1
  loadingHistory.value = true
  try {
    const list = (await userApi.listMyHistory(historyPage.value, LIST_PAGE_SIZE)) || []
    history.value = reset ? list : [...history.value, ...list]
    historyHasMore.value = list.length >= LIST_PAGE_SIZE
    if (historyHasMore.value) historyPage.value += 1
  } catch {
    if (reset) history.value = []
    historyHasMore.value = false
  } finally {
    loadingHistory.value = false
  }
}

/** 删除一条浏览记录：本地先摘掉，避免整表重拉 */
async function removeHistoryItem(item) {
  await userApi.removeHistory(item.postId)
  history.value = history.value.filter((entry) => entry.postId !== item.postId)
}

async function clearHistory() {
  try {
    await ElMessageBox.confirm('清空后无法恢复，确定清空浏览历史吗？', '清空浏览历史', {
      type: 'warning',
      confirmButtonText: '清空',
      cancelButtonText: '取消'
    })
  } catch {
    return
  }
  await userApi.clearHistory()
  history.value = []
  ElMessage.success('已清空浏览历史')
}

async function startChat() {
  if (!userStore.isLoggedIn) {
    ElMessage.info('请先登录')
    router.push({ name: 'login', query: { redirect: route.fullPath } })
    return
  }
  if (isSelf.value) return
  const convId = await chatApi.getOrCreateConversation(userId.value)
  router.push({ name: 'chat', query: { conv: convId } })
}

/** 建群入口：建群弹窗复用聊天面板里的那一份，避免两处维护 */
function createGroup() {
  router.push({ name: 'chat', query: { createGroup: 1 } })
}

/* ---------- 资料维护：换头像 / 改昵称 ---------- */

/**
 * 上传头像：先传 OSS 拿 URL，再写回 user.avatar。
 * 用 el-upload 的 http-request 接管，避免再走一遍默认 XHR。
 */
async function onAvatarUpload(options) {
  if (!isSelf.value) return
  avatarUploading.value = true
  try {
    const url = await userApi.uploadFile(options.file)
    if (!url) throw new Error('上传未返回地址')
    await saveProfile({ avatar: url })
    ElMessage.success('头像已更新')
  } catch {
    // 拦截器已提示
  } finally {
    avatarUploading.value = false
  }
}

function beforeAvatarUpload(file) {
  const isImage = /^image\//.test(file.type)
  if (!isImage) {
    ElMessage.warning('头像只能是图片文件')
    return false
  }
  if (file.size / 1024 / 1024 > 5) {
    ElMessage.warning('头像不要超过 5MB')
    return false
  }
  return true
}

function startEditName() {
  nameDraft.value = userStore.username || ''
  editingName.value = true
}

async function submitName() {
  const name = nameDraft.value.trim()
  if (!name) {
    ElMessage.warning('昵称不能为空')
    return
  }
  if (name === (userStore.username || '')) {
    editingName.value = false
    return
  }
  if (name.length > 20) {
    ElMessage.warning('昵称最多 20 个字符')
    return
  }
  const ok = await saveProfile({ username: name })
  if (ok) editingName.value = false
}

/** 统一走 PUT /user/profile，后端只更新传了值的字段 */
async function saveProfile(patch) {
  try {
    const vo = await userApi.updateProfile(patch)
    // 后端回的是最新资料，能拿到就用它，拿不到则用本次提交的值兜底
    userStore.patchProfile({
      username: vo?.username || patch.username || userStore.username,
      avatar: vo?.avatar ?? patch.avatar ?? userStore.avatar
    })
    // 帖子/关注列表里的旧头像不重拉就会一直显示老的
    followLoaded.value = false
    return true
  } catch {
    return false
  }
}

watch(tab, (value) => {
  if (value === 'booklists') loadBooklists()
  if (value === 'following' || value === 'followers') loadFollow()
  if (value === 'collected') loadCollected()
  if (value === 'history') loadHistory(true)
})

watch(userId, () => {
  booklistsLoaded.value = false
  followLoaded.value = false
  collectedLoaded.value = false
  myBooklists.value = []
  following.value = []
  followers.value = []
  collected.value = []
  history.value = []
  tab.value = 'posts'
  loadPosts(true)
})

onMounted(() => {
  loadPosts(true)
  // 支持从侧边栏「我的收藏」直接落到收藏 Tab。
  // 收藏 / 浏览历史是私有数据，只有看自己主页时才有对应面板，别人主页上忽略该参数。
  const wanted = String(route.query.tab || '')
  const selfOnlyTabs = ['collected', 'history']
  if (!wanted) return
  if (selfOnlyTabs.includes(wanted) && !isSelf.value) return
  tab.value = wanted
})
</script>

<template>
  <div class="bn-container">
    <!-- 资料卡 -->
    <section class="bn-card profile-card">
      <!-- 头像：自己的主页可以点着换，别人的只读 -->
      <el-upload
        v-if="isSelf"
        class="avatar-uploader"
        :show-file-list="false"
        :http-request="onAvatarUpload"
        :before-upload="beforeAvatarUpload"
        accept="image/*"
      >
        <div class="avatar-wrap" :class="{ uploading: avatarUploading }">
          <BnAvatar :src="profile.avatar" :name="profile.name" :size="76" :linkable="false" />
          <span class="avatar-mask">
            <el-icon v-if="avatarUploading" class="is-loading"><Loading /></el-icon>
            <el-icon v-else><Camera /></el-icon>
          </span>
        </div>
      </el-upload>
      <BnAvatar v-else :src="profile.avatar" :name="profile.name" :size="76" :linkable="false" />

      <div class="profile-body">
        <div class="name-row">
          <template v-if="isSelf && editingName">
            <el-input
              v-model="nameDraft"
              size="small"
              maxlength="20"
              class="name-input"
              @keyup.enter="submitName"
            />
            <el-button size="small" type="primary" @click="submitName">保存</el-button>
            <el-button size="small" @click="editingName = false">取消</el-button>
          </template>
          <template v-else>
            <h1 class="profile-name">{{ profile.name }}</h1>
            <el-tag v-if="isSelf" size="small" effect="plain">我自己</el-tag>
            <el-button v-if="isSelf" link size="small" class="name-edit" @click="startEditName">
              <el-icon style="margin-right: 2px"><EditPen /></el-icon>改昵称
            </el-button>
          </template>
        </div>

        <div class="profile-stats">
          <button class="stat-btn" @click="tab = 'posts'">
            <b>{{ postCountText }}</b> 书评
          </button>
          <button class="stat-btn" @click="tab = 'booklists'; loadBooklists()">
            <b>{{ booklistsLoaded ? myBooklists.length : '—' }}</b> 书单
          </button>
          <button class="stat-btn" @click="tab = 'following'; loadFollow()">
            <b>{{ followLoaded ? followCounts.following : '—' }}</b> 关注
          </button>
          <button class="stat-btn" @click="tab = 'followers'; loadFollow()">
            <b>{{ followLoaded ? followCounts.followers : '—' }}</b> 粉丝
          </button>
        </div>

        <p class="profile-id bn-text-muted">ID：{{ userId }}</p>
      </div>

      <div class="profile-actions">
        <FollowButton v-if="!isSelf" :user-id="userId" @change="followLoaded = false" />
        <el-button v-if="!isSelf" @click="startChat">
          <el-icon style="margin-right: 4px"><ChatLineRound /></el-icon>私信
        </el-button>
        <el-button v-if="isSelf" @click="createGroup">
          <el-icon style="margin-right: 4px"><UserFilled /></el-icon>创建聊天组
        </el-button>
        <el-button v-if="isSelf" type="primary" @click="router.push('/post/edit')">
          <el-icon style="margin-right: 4px"><EditPen /></el-icon>写书评
        </el-button>
      </div>
    </section>

    <!-- 内容 Tab -->
    <el-tabs v-model="tab" class="tabs">
      <el-tab-pane :label="`书评 (${postCountText})`" name="posts" />
      <el-tab-pane label="书单" name="booklists" />
      <el-tab-pane v-if="isSelf" :label="`收藏 (${collected.length}${collectedHasMore ? '+' : ''})`" name="collected" />
      <el-tab-pane v-if="isSelf" label="浏览历史" name="history" />
      <el-tab-pane label="关注" name="following" />
      <el-tab-pane label="粉丝" name="followers" />
    </el-tabs>

    <!-- 书评 -->
    <template v-if="tab === 'posts'">
      <BnState
        :loading="loadingPosts && !userPosts.length"
        :empty="!loadingPosts && !userPosts.length"
        :empty-text="isSelf ? '你还没有写过书评' : '这位书友还没有发布书评'"
      >
        <PostCard v-for="post in userPosts" :key="post.id" :post="post" class="bn-mt-12" />
        <el-button v-if="isSelf && !userPosts.length" type="primary" class="bn-mt-12" @click="router.push('/post/edit')">
          写第一篇书评
        </el-button>
        <div v-if="postsHasMore" class="load-more">
          <el-button :loading="loadingPosts" @click="loadPosts()">加载更多</el-button>
        </div>
      </BnState>
    </template>

    <!-- 书单 -->
    <template v-else-if="tab === 'booklists'">
      <BnState
        :loading="loadingBooklists"
        :empty="!loadingBooklists && !myBooklists.length"
        empty-text="还没有书单"
      >
        <div class="list-grid">
          <router-link
            v-for="item in myBooklists"
            :key="item.id"
            :to="`/booklist/${item.id}`"
            class="bn-card bn-card-hover list-card"
          >
            <BnCover :src="item.coverImage" :title="item.title" height="112px" />
            <p class="list-title bn-ellipsis-1">{{ item.title }}</p>
            <p class="list-meta">
              {{ item.bookCount || 0 }} 本 · {{ fromNow(item.createTime) }}
              <el-tag v-if="item.visibility === 0" size="small" type="info" effect="plain">私密</el-tag>
            </p>
          </router-link>
        </div>
      </BnState>
    </template>

    <!-- 我的收藏 -->
    <template v-else-if="tab === 'collected'">
      <BnState
        :loading="loadingCollected && !collected.length"
        :empty="!loadingCollected && !collected.length"
        empty-text="还没有收藏任何书评"
      >
        <PostCard v-for="post in collected" :key="post.id" :post="post" class="bn-mt-12" />
        <div v-if="collectedHasMore" class="load-more">
          <el-button :loading="loadingCollected" @click="loadCollected()">加载更多</el-button>
        </div>
      </BnState>
    </template>

    <!-- 浏览历史 -->
    <template v-else-if="tab === 'history'">
      <div class="history-head">
        <p class="bn-text-muted history-tip">同一篇书评只保留最近一次，累计浏览次数会一并显示</p>
        <el-button v-if="history.length" link size="small" type="danger" @click="clearHistory">
          <el-icon style="margin-right: 3px"><Delete /></el-icon>清空历史
        </el-button>
      </div>

      <BnState
        :loading="loadingHistory && !history.length"
        :empty="!loadingHistory && !history.length"
        empty-text="还没有浏览记录"
      >
        <div class="history-list">
          <div v-for="item in history" :key="item.id" class="bn-card history-item">
            <router-link :to="`/post/${item.postId}`" class="history-main">
              <p class="history-title bn-ellipsis-1">{{ item.title }}</p>
              <p class="history-sub">
                {{ item.categoryName || '未分类' }} · {{ item.authorName || '匿名书友' }}
                <span v-if="item.myViewCount > 1" class="history-count">我看过 {{ item.myViewCount }} 次</span>
              </p>
            </router-link>
            <div class="history-side">
              <span class="history-time">{{ fromNow(item.viewTime) }}</span>
              <el-button link size="small" title="从历史中移除" @click="removeHistoryItem(item)">
                <el-icon><Close /></el-icon>
              </el-button>
            </div>
          </div>
        </div>
        <div v-if="historyHasMore" class="load-more">
          <el-button :loading="loadingHistory" @click="loadHistory()">加载更多</el-button>
        </div>
      </BnState>
    </template>

    <!-- 关注 / 粉丝 -->
    <template v-else>
      <BnState
        :loading="loadingFollow"
        :empty="!loadingFollow && !(tab === 'following' ? following : followers).length"
        :empty-text="tab === 'following' ? '还没有关注任何书友' : '还没有粉丝'"
      >
        <div class="follow-grid">
          <router-link
            v-for="item in tab === 'following' ? following : followers"
            :key="item.userId"
            :to="`/user/${item.userId}`"
            class="bn-card bn-card-hover follow-card"
          >
            <BnAvatar :src="item.avatar" :name="item.username" :size="46" :linkable="false" />
            <p class="follow-name bn-ellipsis-1">{{ item.username || '书友' }}</p>
          </router-link>
        </div>
      </BnState>
    </template>
  </div>
</template>

<style scoped>
.profile-card {
  display: flex;
  align-items: center;
  gap: 20px;
  padding: 24px 26px;
  flex-wrap: wrap;
}

.profile-body {
  flex: 1;
  min-width: 200px;
}

.name-row {
  display: flex;
  align-items: center;
  gap: 9px;
  flex-wrap: wrap;
}

/* ---------- 头像上传 ---------- */
.avatar-uploader :deep(.el-upload) {
  display: block;
  line-height: 0;
}

.avatar-wrap {
  position: relative;
  display: inline-block;
  border-radius: 50%;
  cursor: pointer;
  overflow: hidden;
}

.avatar-mask {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 20px;
  background: rgba(0, 0, 0, 0.42);
  opacity: 0;
  transition: opacity 0.2s ease;
}

.avatar-wrap:hover .avatar-mask,
.avatar-wrap.uploading .avatar-mask {
  opacity: 1;
}

.name-input {
  width: 168px;
}

.name-edit {
  font-size: 12.5px;
  color: var(--bn-text-muted);
}

.profile-name {
  font-size: 21px;
}

.profile-stats {
  display: flex;
  gap: 22px;
  margin-top: 11px;
  font-size: 13px;
  color: var(--bn-text-sub);
}

.profile-stats b {
  color: var(--bn-text);
  font-size: 15px;
  margin-right: 2px;
}

.stat-btn {
  border: none;
  background: none;
  padding: 0;
  font-size: 13px;
  color: var(--bn-text-sub);
  cursor: pointer;
  font-family: inherit;
}

.stat-btn:hover {
  color: var(--bn-primary);
}

.profile-id {
  font-size: 12px;
  margin-top: 8px;
  word-break: break-all;
}

.profile-actions {
  display: flex;
  gap: 9px;
  align-items: center;
  flex-wrap: wrap;
}

.tabs {
  margin-top: 18px;
}

.list-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(190px, 1fr));
  gap: 14px;
}

.list-card {
  padding: 11px;
}

.list-title {
  font-size: 14px;
  font-weight: 600;
  margin-top: 9px;
}

.list-meta {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: var(--bn-text-muted);
  margin-top: 4px;
}

.follow-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(140px, 1fr));
  gap: 12px;
}

.follow-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 9px;
  padding: 18px 12px;
  text-align: center;
}

.follow-name {
  font-size: 13.5px;
  font-weight: 500;
  max-width: 100%;
}

/* ---------- 浏览历史 ---------- */
.history-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 10px;
}

.history-tip {
  font-size: 12.5px;
}

.history-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.history-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 13px 16px;
}

.history-main {
  flex: 1;
  min-width: 0;
}

.history-title {
  font-size: 14px;
  font-weight: 500;
}

.history-item:hover .history-title {
  color: var(--bn-primary);
}

.history-sub {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 11.5px;
  color: var(--bn-text-muted);
  margin-top: 3px;
  flex-wrap: wrap;
}

.history-count {
  color: var(--bn-primary);
}

.history-side {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-shrink: 0;
}

.history-time {
  font-size: 11.5px;
  color: var(--bn-text-muted);
  white-space: nowrap;
}

.load-more {
  display: flex;
  justify-content: center;
  margin-top: 16px;
}

@media (max-width: 620px) {
  .profile-card {
    flex-direction: column;
    align-items: center;
    text-align: center;
  }
  .name-row,
  .profile-stats {
    justify-content: center;
  }
  .history-side {
    flex-direction: column;
  }
}
</style>
