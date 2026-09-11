<script setup>
/**
 * 书籍卡片：网格流中的单本书
 */
import { computed } from 'vue'
import BnCover from './BnCover.vue'
import { formatRating } from '@/utils/format'

const props = defineProps({
  book: { type: Object, required: true },
  /** 卡片下方附加说明（如书单条目备注） */
  note: { type: String, default: '' },
  /** 是否展示简介 */
  showDescription: { type: Boolean, default: true }
})

const book = computed(() => props.book || {})
const hasRating = computed(() => Number(book.value.rating) > 0)
</script>

<template>
  <router-link :to="`/book/${book.id}`" class="book-card bn-card bn-card-hover">
    <BnCover :src="book.coverUrl" :title="book.title" height="168px" />
    <div class="book-info">
      <p class="book-title bn-ellipsis-1" :title="book.title">{{ book.title }}</p>
      <p class="book-author bn-ellipsis-1">{{ book.author || '佚名' }}</p>

      <div class="book-rate">
        <el-rate
          v-if="hasRating"
          :model-value="Number(book.rating) / 2"
          disabled
          allow-half
          size="small"
          :show-score="false"
        />
        <span :class="['rate-text', { muted: !hasRating }]">{{ formatRating(book.rating) }}</span>
        <span v-if="book.ratingCount" class="rate-count">({{ book.ratingCount }})</span>
      </div>

      <p v-if="showDescription && book.description" class="book-desc bn-ellipsis-2">
        {{ book.description }}
      </p>
      <p v-if="note" class="book-note bn-ellipsis-2">备注：{{ note }}</p>
    </div>
  </router-link>
</template>

<style scoped>
.book-card {
  display: block;
  padding: 12px;
}

.book-info {
  margin-top: 10px;
}

.book-title {
  font-size: 14.5px;
  font-weight: 600;
  line-height: 1.4;
}

.book-author {
  font-size: 12.5px;
  color: var(--bn-text-sub);
  margin-top: 2px;
}

.book-rate {
  display: flex;
  align-items: center;
  gap: 5px;
  margin-top: 5px;
  min-height: 22px;
}

.book-rate :deep(.el-rate) {
  height: 16px;
  --el-rate-icon-size: 13px;
}

.rate-text {
  font-size: 12.5px;
  color: #c8783c;
  font-weight: 600;
}

.rate-text.muted {
  color: var(--bn-text-muted);
  font-weight: 400;
}

.rate-count {
  font-size: 11.5px;
  color: var(--bn-text-muted);
}

.book-desc {
  font-size: 12px;
  color: var(--bn-text-muted);
  line-height: 1.6;
  margin-top: 6px;
}

.book-note {
  font-size: 12px;
  color: #8a7357;
  background: #faf5ee;
  border-radius: 4px;
  padding: 5px 7px;
  margin-top: 7px;
  line-height: 1.6;
}
</style>
