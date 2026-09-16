<script setup>
/**
 * 首页：热门标签 + 多频道帖子流 + 右侧栏
 *
 * 为什么没有「发现页」了（历史决策，勿改回）：
 *   之前首页既有顶部导航的「热榜/话题/分类/书单」，又有一排模块入口卡片指向
 *   /discover，两处内容与首页帖子流高度重叠，点进去顶部还会同时高亮三个按钮。
 *   现在删掉了 DiscoverView 与 /discover 路由，热榜/话题/分类不再单独做页，
 *   分类改为贴吧式的「书吧」页（/bars），这些入口只在顶部导航里出现一次。
 *
 * 频道与后端排序的对应关系：
 *   推荐 → hot（综合热度：阅读 + 点赞x5 + 评论x3）
 *   最新 → latest（发布时间倒序）
 *   热议 → comments（评论数倒序）
 *   精华 → essence（点赞数倒序）
 *   求助 → latest + postType=HELP（独立帖子类型，与四个排序频道并列）
 * 排序标识由后端 PostSortConstant 白名单归一化，前端传错只会回落成「最新」。
 *
 * 「求助」原先是首页顶部的一块独立版块，后来改成与排序并列的频道：
 * 顶部版块会一直占着首屏，而它本质上只是「帖子类型 = HELP」的一个筛选维度，
 * 跟「推荐/最新」是同一层东西，放在一起切换才不会让人以为是两套内容。
 */
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import * as postApi from '@/api/post'
import * as taxonomyApi from '@/api/taxonomy'
import { useUserStore } from '@/stores/user'
import PostCard from '@/components/PostCard.vue'
import HomeSidebar from '@/components/HomeSidebar.vue'
import HotRankCard from '@/components/HotRankCard.vue'

const router = useRouter()
const userStore = useUserStore()

const CHANNELS = [
  { key: 'hot', label: '推荐', sort: 'hot', postType: '' },
  { key: 'latest', label: '最新', sort: 'latest', postType: '' },
  { key: 'comments', label: '热议', sort: 'comments', postType: '' },
  { key: 'essence', label: '精华', sort: 'essence', postType: '' },
  { key: 'help', label: '求助', sort: 'latest', postType: 'HELP' }
]

const channel = ref('hot')
const posts = ref([])
const loading = ref(false)
const loadingMore = ref(false)
const error = ref('')
const finished = ref(false)

const page = ref(1)
const PAGE_SIZE = 10

/* ---------------- 热门标签 ---------------- */
// 这里只负责「展示」，不再提供创建入口。
// 标签的创建收口在发帖页：用户在标签框里敲一个新名字，发帖时才会
// POST /tag/create 落库（见 PostEditView.resolveTagIds）。
// 之所以搬到发帖时，是因为「首页能建、发帖时又挑不到」会形成两套心智；
// 而且脱离发帖语境建出来的标签几乎不会被用上，只会污染热门标签排序。
const hotTags = ref([])
const tagLoading = ref(true)

/** 当前频道：排序方式 + 帖子类型（求助频道同时带 postType=HELP） */
const currentChannel = computed(
  () => CHANNELS.find((item) => item.key === channel.value) || CHANNELS[0]
)

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
      auditStatus: 1,
      sort: currentChannel.value.sort,
      // 非求助频道不传 postType，让后端按默认（全部类型）走
      postType: currentChannel.value.postType || undefined
    })
    const list = data || []
    posts.value = isFirst ? list : posts.value.concat(list)
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

/** 热门标签：后端按 post_tag 关联表的实时引用数降序取前 N（不用 use_count 遗留列） */
async function loadHotTags() {
  tagLoading.value = true
  try {
    hotTags.value = (await taxonomyApi.getHotTags(14)) || []
  } catch {
    hotTags.value = []
  } finally {
    tagLoading.value = false
  }
}

function goPublish(query) {
  if (!userStore.isLoggedIn) {
    router.push({ name: 'login', query: { redirect: query ? `/post/edit?type=${query.type}` : '/post/edit' } })
    return
  }
  router.push({ name: 'post-edit', query })
}

// 切频道即重置列表重新拉取
watch(channel, () => loadPage(true))

onMounted(() => {
  loadPage(true)
  loadHotTags()
})
</script>

<template>
  <div class="bn-container home-layout">
    <section class="feed">
      <div class="feed-head">
        <div>
          <h1 class="bn-page-title">书友圈</h1>
          <p class="bn-page-sub">找书、评书、聊书 —— 来自书友的真实阅读记录</p>
        </div>
        <el-button type="primary" @click="goPublish()">
          <el-icon style="margin-right: 4px"><EditPen /></el-icon>写书评
        </el-button>
      </div>

      <!-- ---------- 热门标签：按帖子数实时排序（只读展示） ----------
           创建入口已移到发帖页：在标签框里输入新名字，发帖时才落库。 -->
      <section class="tag-block">
        <div class="block-head">
          <h2 class="block-title">
            <el-icon><Collection /></el-icon>热门标签
          </h2>
          <span class="bn-text-muted block-hint">发帖时可直接输入新标签</span>
        </div>

        <el-skeleton v-if="tagLoading" :rows="1" animated />
        <div v-else-if="hotTags.length" class="tag-cloud">
          <router-link
            v-for="tag in hotTags"
            :key="tag.id"
            :to="`/tag/${tag.id}`"
            class="tag-chip"
          >
            #{{ tag.name }}
            <em v-if="tag.useCount">{{ tag.useCount }}</em>
          </router-link>
        </div>
        <p v-else class="bn-text-muted tag-empty">还没有标签，创建第一个吧</p>
      </section>

      <!-- 频道切换 -->
      <div class="channel-bar">
        <button
          v-for="item in CHANNELS"
          :key="item.key"
          type="button"
          :class="['channel', { active: channel === item.key }]"
          @click="channel = item.key"
        >
          {{ item.label }}
        </button>
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
          <p class="bn-mt-12">这个频道还没有内容，来写第一篇吧</p>
          <el-button type="primary" class="bn-mt-12" @click="goPublish()">写书评</el-button>
        </div>
      </template>

      <div v-if="sortedPosts.length" class="load-more">
        <el-button v-if="!finished" :loading="loadingMore" text @click="loadPage(false)">
          加载更多
        </el-button>
        <span v-else class="bn-text-muted">已经到底啦</span>
      </div>
    </section>

    <div class="side">
      <HomeSidebar />
      <HotRankCard />
    </div>
  </div>
</template>

<style scoped>
.home-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 288px;
  gap: 22px;
  align-items: start;
}

.side {
  position: sticky;
  top: 78px;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

/* ---------- 通用版块头 ---------- */
.block-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 10px;
  flex-wrap: wrap;
}

.block-title {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 15px;
  color: var(--bn-text);
}

.block-actions {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.block-hint {
  font-size: 12px;
}

/* ---------- 热门标签 ---------- */
.tag-block {
  padding: 14px 16px;
  margin-top: 14px;
  border-radius: var(--bn-radius);
  border: 1px solid var(--bn-border);
  background: var(--bn-surface);
}

.tag-cloud {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.tag-chip {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 3px 11px;
  border-radius: 999px;
  background: var(--bn-primary-soft);
  color: var(--bn-primary);
  font-size: 12.5px;
  transition: all 0.15s ease;
}

.tag-chip:hover {
  background: var(--bn-primary);
  color: #fff;
}

.tag-chip em {
  font-style: normal;
  font-size: 11px;
  opacity: 0.7;
}

.tag-empty {
  font-size: 12.5px;
}

/* ---------- 频道 ---------- */
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

.channel-bar {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 4px;
  margin: 18px 0 2px;
  background: #f3efea;
  border-radius: 10px;
  width: fit-content;
}

.channel {
  border: none;
  background: transparent;
  padding: 5px 16px;
  border-radius: 8px;
  font-family: inherit;
  font-size: 13.5px;
  color: var(--bn-text-sub);
  cursor: pointer;
  transition: all 0.15s ease;
}

.channel:hover {
  color: var(--bn-primary);
}

.channel.active {
  background: #fff;
  color: var(--bn-primary);
  font-weight: 600;
  box-shadow: 0 1px 4px rgba(47, 42, 37, 0.08);
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
    position: static;
    order: 2;
  }
}
</style>
