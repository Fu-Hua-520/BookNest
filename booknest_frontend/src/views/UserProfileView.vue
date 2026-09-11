<script setup>
/**
 * 个人主页：资料卡 + 他的书评 / 书单 / 关注 / 粉丝
 * 后端无「按作者查帖子」接口，此处拉取帖子流后按 authorId 本地筛选
 */
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import * as postApi from '@/api/post'
import * as booklistApi from '@/api/booklist'
import * as followApi from '@/api/follow'
import * as chatApi from '@/api/chat'
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

const allPosts = ref([])
const myBooklists = ref([])
const following = ref([])
const followers = ref([])

const loadingPosts = ref(false)
const loadingBooklists = ref(false)
const loadingFollow = ref(false)
const booklistsLoaded = ref(false)
const followLoaded = ref(false)

/** 从帖子流中筛出该用户的帖子 */
const userPosts = computed(() =>
  (allPosts.value || []).filter((item) => String(item.authorId) === userId.value)
)

/** 作者资料从帖子数据中推导（后端无公开的用户详情接口） */
const profile = computed(() => {
  const hit = userPosts.value[0]
  if (hit) {
    return {
      name: hit.authorName || '书友',
      avatar: hit.authorAvatar || '',
      id: userId.value
    }
  }
  if (isSelf.value) {
    return {
      name: userStore.username,
      avatar: userStore.avatar,
      id: userStore.userId
    }
  }
  const follow = [...following.value, ...followers.value].find(
    (item) => String(item.userId) === userId.value
  )
  if (follow) return { name: follow.username || '书友', avatar: follow.avatar || '', id: userId.value }
  return { name: '书友', avatar: '', id: userId.value }
})

/** 是否已关注，用于按钮初始态 */
const followCounts = computed(() => ({
  following: following.value.length,
  followers: followers.value.length
}))

async function loadPosts() {
  loadingPosts.value = true
  try {
    const pages = await Promise.all([
      postApi.listPosts({ page: 1, pageSize: 50, auditStatus: 1 }),
      postApi.listPosts({ page: 2, pageSize: 50, auditStatus: 1 })
    ])
    allPosts.value = [...(pages[0] || []), ...(pages[1] || [])]
  } catch {
    allPosts.value = []
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

watch(tab, (value) => {
  if (value === 'booklists') loadBooklists()
  if (value === 'following' || value === 'followers') loadFollow()
})

watch(userId, () => {
  booklistsLoaded.value = false
  followLoaded.value = false
  myBooklists.value = []
  following.value = []
  followers.value = []
  tab.value = 'posts'
  loadPosts()
})

onMounted(loadPosts)
</script>

<template>
  <div class="bn-container">
    <!-- 资料卡 -->
    <section class="bn-card profile-card">
      <BnAvatar :src="profile.avatar" :name="profile.name" :size="76" :linkable="false" />

      <div class="profile-body">
        <div class="name-row">
          <h1 class="profile-name">{{ profile.name }}</h1>
          <el-tag v-if="isSelf" size="small" effect="plain">我自己</el-tag>
        </div>

        <div class="profile-stats">
          <span><b>{{ userPosts.length }}</b> 书评</span>
          <span><b>{{ booklistsLoaded ? myBooklists.length : '—' }}</b> 书单</span>
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
        <el-button v-if="isSelf" type="primary" @click="router.push('/post/edit')">
          <el-icon style="margin-right: 4px"><EditPen /></el-icon>写书评
        </el-button>
      </div>
    </section>

    <!-- 内容 Tab -->
    <el-tabs v-model="tab" class="tabs">
      <el-tab-pane :label="`书评 (${userPosts.length})`" name="posts" />
      <el-tab-pane label="书单" name="booklists" />
      <el-tab-pane label="关注" name="following" />
      <el-tab-pane label="粉丝" name="followers" />
    </el-tabs>

    <!-- 书评 -->
    <template v-if="tab === 'posts'">
      <BnState
        :loading="loadingPosts"
        :empty="!loadingPosts && !userPosts.length"
        :empty-text="isSelf ? '你还没有写过书评' : '这位书友还没有发布书评'"
      >
        <PostCard v-for="post in userPosts" :key="post.id" :post="post" class="bn-mt-12" />
        <el-button v-if="isSelf && !userPosts.length" type="primary" class="bn-mt-12" @click="router.push('/post/edit')">
          写第一篇书评
        </el-button>
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
}
</style>
