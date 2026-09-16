<script setup>
/**
 * 侧栏榜单卡：热门排行 + 热门作者
 *
 * 两个榜单都基于同一份「综合热度 Top 20」数据：
 *   - 排行直接取前 5 条
 *   - 作者榜由这 20 条的作者聚合而来（上榜帖数降序）
 * 这样只需一次请求。它不等于「全站发帖最多的作者」，
 * 因此标题写「热门作者」而不是「活跃作者」，避免语义失真。
 */
import { computed, onMounted, ref } from 'vue'
import * as postApi from '@/api/post'
import { formatCount } from '@/utils/format'
import BnAvatar from '@/components/BnAvatar.vue'

const posts = ref([])
const loading = ref(true)

const topPosts = computed(() => posts.value.slice(0, 5))

const topAuthors = computed(() => {
  const counter = new Map()
  for (const post of posts.value) {
    if (!post.authorId) continue
    const entry = counter.get(post.authorId) || {
      userId: post.authorId,
      name: post.authorName || '书友',
      avatar: post.authorAvatar || '',
      count: 0,
      views: 0
    }
    entry.count += 1
    entry.views += Number(post.viewCount || 0)
    counter.set(post.authorId, entry)
  }
  return [...counter.values()].sort((a, b) => b.count - a.count || b.views - a.views).slice(0, 5)
})

async function load() {
  loading.value = true
  try {
    posts.value = (await postApi.listPosts({ sort: 'hot', page: 1, pageSize: 20 })) || []
  } catch {
    posts.value = []
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="rank-wrap">
    <!-- 热门排行 -->
    <div class="bn-card side-block">
      <div class="side-head">
        <h3>热门排行</h3>
        <!-- 发现页已下线（首页与发现页内容重叠），这里不再提供「完整榜单」入口，
             想看完整热度排序请直接用首页的「推荐」频道 -->
      </div>

      <el-skeleton v-if="loading" :rows="4" animated />

      <p v-else-if="!topPosts.length" class="side-empty">暂无数据</p>

      <ol v-else class="rank-list">
        <li v-for="(post, index) in topPosts" :key="post.id" class="rank-item">
          <span :class="['rank-no', { top: index < 3 }]">{{ index + 1 }}</span>
          <router-link :to="`/post/${post.id}`" class="rank-title bn-ellipsis-2">{{ post.title }}</router-link>
          <span class="rank-meta">
            <el-icon><View /></el-icon>{{ formatCount(post.viewCount) }}
          </span>
        </li>
      </ol>
    </div>

    <!-- 热门作者 -->
    <div class="bn-card side-block">
      <div class="side-head">
        <h3>热门作者</h3>
      </div>

      <el-skeleton v-if="loading" :rows="3" animated />

      <p v-else-if="!topAuthors.length" class="side-empty">暂无数据</p>

      <div v-else class="author-list">
        <router-link
          v-for="author in topAuthors"
          :key="author.userId"
          :to="`/user/${author.userId}`"
          class="author-item"
        >
          <BnAvatar :src="author.avatar" :name="author.name" :size="30" :linkable="false" />
          <span class="author-name bn-ellipsis-1">{{ author.name }}</span>
          <span class="author-count">{{ author.count }} 篇</span>
        </router-link>
      </div>
    </div>
  </div>
</template>

<style scoped>
.rank-wrap {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.side-block {
  padding: 15px 16px;
}

.side-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}

.side-head h3 {
  font-size: 14.5px;
}

.side-more {
  font-size: 12.5px;
  color: var(--bn-text-muted);
}

.side-more:hover {
  color: var(--bn-primary);
}

.side-empty {
  font-size: 12.5px;
  color: var(--bn-text-muted);
  padding: 6px 0;
}

.rank-list {
  display: flex;
  flex-direction: column;
  gap: 9px;
}

.rank-item {
  display: flex;
  align-items: flex-start;
  gap: 9px;
}

.rank-no {
  flex-shrink: 0;
  width: 17px;
  height: 17px;
  border-radius: 5px;
  background: #f2eee9;
  color: var(--bn-text-muted);
  font-size: 11px;
  line-height: 17px;
  text-align: center;
}

.rank-no.top {
  background: var(--bn-primary);
  color: #fff;
}

.rank-title {
  flex: 1;
  min-width: 0;
  font-size: 13px;
  line-height: 1.5;
}

.rank-title:hover {
  color: var(--bn-primary);
}

.rank-meta {
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  gap: 3px;
  font-size: 11.5px;
  color: var(--bn-text-muted);
  padding-top: 1px;
}

.author-list {
  display: flex;
  flex-direction: column;
  gap: 9px;
}

.author-item {
  display: flex;
  align-items: center;
  gap: 9px;
}

.author-name {
  flex: 1;
  min-width: 0;
  font-size: 13px;
}

.author-item:hover .author-name {
  color: var(--bn-primary);
}

.author-count {
  flex-shrink: 0;
  font-size: 11.5px;
  color: var(--bn-text-muted);
}
</style>
