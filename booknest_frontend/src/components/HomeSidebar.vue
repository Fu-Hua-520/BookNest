<script setup>
/**
 * 首页侧栏：分类树、热门标签、AI 助手入口
 */
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { useTaxonomyStore } from '@/stores/taxonomy'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const taxonomyStore = useTaxonomyStore()
const userStore = useUserStore()

const categories = computed(() => taxonomyStore.categoryTree || [])
const tags = computed(() => taxonomyStore.hotTags || [])

const activeCategoryId = computed(() => (route.name === 'category' ? String(route.params.id) : ''))
const activeTagId = computed(() => (route.name === 'tag' ? String(route.params.id) : ''))
</script>

<template>
  <aside class="sidebar">
    <!-- 分类 -->
    <div class="bn-card side-block">
      <div class="side-head">
        <h3>书籍分类</h3>
        <router-link to="/search" class="side-more">全部</router-link>
      </div>
      <div v-if="categories.length" class="category-list">
        <div v-for="parent in categories" :key="parent.id" class="category-group">
          <router-link
            :to="`/category/${parent.id}`"
            :class="['category-parent', { active: activeCategoryId === String(parent.id) }]"
          >
            {{ parent.name }}
          </router-link>
          <div v-if="parent.children?.length" class="category-children">
            <router-link
              v-for="child in parent.children"
              :key="child.id"
              :to="`/category/${child.id}`"
              :class="['category-child', { active: activeCategoryId === String(child.id) }]"
            >
              {{ child.name }}
            </router-link>
          </div>
        </div>
      </div>
      <el-skeleton v-else-if="taxonomyStore.loading" :rows="4" animated />
      <p v-else class="side-empty">暂无分类</p>
    </div>

    <!-- 热门标签 -->
    <div class="bn-card side-block">
      <div class="side-head">
        <h3>热门标签</h3>
      </div>
      <div v-if="tags.length" class="tag-cloud">
        <router-link
          v-for="tag in tags"
          :key="tag.id"
          :to="`/tag/${tag.id}`"
          :class="['tag-chip', { active: activeTagId === String(tag.id) }]"
        >
          #{{ tag.name }}
          <span v-if="tag.useCount" class="tag-count">{{ tag.useCount }}</span>
        </router-link>
      </div>
      <p v-else class="side-empty">暂无标签</p>
    </div>

    <!-- AI 助手 -->
    <div class="bn-card ai-block">
      <div class="ai-icon">
        <el-icon :size="20"><MagicStick /></el-icon>
      </div>
      <p class="ai-title">BookNest AI 助手</p>
      <p class="ai-desc">找书、查帖、总结书评，基于论坛内容检索回答</p>
      <router-link to="/assistant" class="ai-btn">
        {{ userStore.isLoggedIn ? '开始对话' : '登录后使用' }}
      </router-link>
    </div>
  </aside>
</template>

<style scoped>
.sidebar {
  position: sticky;
  top: 78px;
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
  margin-bottom: 11px;
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

.category-group + .category-group {
  margin-top: 9px;
  padding-top: 9px;
  border-top: 1px dashed var(--bn-border);
}

.category-parent {
  display: inline-block;
  font-size: 13.5px;
  font-weight: 600;
  color: var(--bn-text);
  padding: 2px 0;
}

.category-parent:hover,
.category-parent.active {
  color: var(--bn-primary);
}

.category-children {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 6px;
}

.category-child {
  font-size: 12.5px;
  color: var(--bn-text-sub);
  padding: 2px 9px;
  border-radius: 999px;
  background: #f7f4f0;
  transition: all 0.15s ease;
}

.category-child:hover,
.category-child.active {
  background: var(--bn-primary-soft);
  color: var(--bn-primary);
}

.tag-cloud {
  display: flex;
  flex-wrap: wrap;
  gap: 7px;
}

.tag-chip {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  font-size: 12.5px;
  padding: 3px 10px;
  border-radius: 999px;
  background: #f7f4f0;
  color: var(--bn-text-sub);
  transition: all 0.15s ease;
}

.tag-chip:hover,
.tag-chip.active {
  background: var(--bn-primary-soft);
  color: var(--bn-primary);
}

.tag-count {
  font-size: 10.5px;
  opacity: 0.7;
}

.side-empty {
  font-size: 12.5px;
  color: var(--bn-text-muted);
  padding: 8px 0;
}

.ai-block {
  background: linear-gradient(150deg, #fdf8f2 0%, #f6ece0 100%);
  border-color: #ecdfd0;
  text-align: center;
}

.ai-icon {
  width: 40px;
  height: 40px;
  border-radius: 12px;
  background: #fff;
  color: var(--bn-primary);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 9px;
  box-shadow: 0 2px 8px rgba(139, 94, 60, 0.12);
}

.ai-title {
  font-size: 14.5px;
  font-weight: 600;
}

.ai-desc {
  font-size: 12.5px;
  color: var(--bn-text-sub);
  line-height: 1.6;
  margin: 5px 0 11px;
}

.ai-btn {
  display: block;
  padding: 7px 0;
  border-radius: 8px;
  background: var(--bn-primary);
  color: #fff;
  font-size: 13.5px;
  transition: background 0.15s ease;
}

.ai-btn:hover {
  background: var(--bn-primary-light);
}
</style>
