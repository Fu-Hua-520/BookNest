<script setup>
/**
 * AI 推荐结果卡片：帖子 / 书籍 / 书单 / 外部链接
 */
import { computed } from 'vue'
import BnCover from './BnCover.vue'

const props = defineProps({
  items: { type: Array, default: () => [] }
})

const TYPE_LABEL = {
  post: '帖子',
  book: '书籍',
  booklist: '书单',
  web: '网页',
  external: '网页'
}

/** 依据 type 推导站内跳转地址 */
function linkOf(item) {
  if (item.link) return item.link
  if (!item.id) return ''
  if (item.type === 'book') return `/book/${item.id}`
  if (item.type === 'booklist') return `/booklist/${item.id}`
  return `/post/${item.id}`
}

const visibleItems = computed(() => (props.items || []).slice(0, 8))

function isExternal(item) {
  return item.type === 'web' || item.type === 'external' || Boolean(item.sourceUrl)
}
</script>

<template>
  <div v-if="visibleItems.length" class="recommend-wrap">
    <div class="recommend-head">
      <el-icon><Compass /></el-icon>
      <span>为你找到 {{ visibleItems.length }} 条相关内容</span>
    </div>
    <div class="recommend-grid">
      <component
        :is="isExternal(item) ? 'a' : 'router-link'"
        v-for="(item, index) in visibleItems"
        :key="item.id || index"
        :to="isExternal(item) ? undefined : linkOf(item)"
        :href="isExternal(item) ? item.sourceUrl || item.link : undefined"
        :target="isExternal(item) ? '_blank' : undefined"
        :rel="isExternal(item) ? 'noopener noreferrer' : undefined"
        class="recommend-card"
      >
        <BnCover
          v-if="item.coverImage"
          :src="item.coverImage"
          :title="item.title"
          width="46px"
          height="62px"
          radius="5px"
        />
        <div v-else class="recommend-icon">
          <el-icon :size="18"><Document /></el-icon>
        </div>

        <div class="recommend-body">
          <div class="recommend-top">
            <span class="recommend-type">{{ TYPE_LABEL[item.type] || '内容' }}</span>
            <span v-if="item.siteName" class="recommend-site">{{ item.siteName }}</span>
          </div>
          <p class="recommend-title bn-ellipsis-2">{{ item.title }}</p>
          <p v-if="item.description || item.author" class="recommend-desc bn-ellipsis-2">
            {{ item.description || item.author }}
          </p>
          <div class="recommend-meta">
            <span v-if="item.rating">评分 {{ Number(item.rating).toFixed(1) }}</span>
            <span v-if="item.viewCount">{{ item.viewCount }} 阅读</span>
          </div>
        </div>
      </component>
    </div>
  </div>
</template>

<style scoped>
.recommend-wrap {
  margin-top: 12px;
  padding-top: 10px;
  border-top: 1px dashed var(--bn-border);
}

.recommend-head {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12.5px;
  color: var(--bn-text-muted);
  margin-bottom: 9px;
}

.recommend-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(230px, 1fr));
  gap: 9px;
}

.recommend-card {
  display: flex;
  gap: 10px;
  padding: 9px;
  border: 1px solid var(--bn-border);
  border-radius: var(--bn-radius-sm);
  background: #fff;
  transition: all 0.16s ease;
  align-items: flex-start;
}

.recommend-card:hover {
  border-color: var(--bn-primary);
  background: #fffdfa;
}

.recommend-icon {
  width: 46px;
  height: 62px;
  border-radius: 5px;
  background: #f3ece4;
  color: #a9754f;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.recommend-body {
  min-width: 0;
  flex: 1;
}

.recommend-top {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 2px;
}

.recommend-type {
  font-size: 11px;
  padding: 0 6px;
  border-radius: 999px;
  background: var(--bn-primary-soft);
  color: var(--bn-primary);
}

.recommend-site {
  font-size: 11px;
  color: var(--bn-text-muted);
}

.recommend-title {
  font-size: 13px;
  font-weight: 600;
  line-height: 1.45;
  margin-bottom: 2px;
}

.recommend-desc {
  font-size: 12px;
  color: var(--bn-text-muted);
  line-height: 1.5;
}

.recommend-meta {
  display: flex;
  gap: 10px;
  font-size: 11.5px;
  color: var(--bn-text-muted);
  margin-top: 3px;
}
</style>
