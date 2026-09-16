<script setup>
/**
 * 首页侧栏：热门书吧
 *
 * 「我关注的吧」不在这里 —— 关注关系属于长期导航，已经移到左侧可收回主侧栏
 * （AppSidebar），换成书单 / 个人主页等页面时也还在，不会跟着首页内容一起消失。
 * 这里只留「热门书吧」这类跟着首页走的推荐内容。
 *
 * 热门书吧按吧内成员数取前 20，后端缓存约一天、隔天自动重算，人人可见（游客也看得到）。
 *
 * 侧栏不放「热门标签」—— 首页主栏已经有一整块热门标签区，
 * 两处都放正是之前被抱怨过的「同一个入口出现两次」。
 *
 * 这里也不放 AI 机器人入口：机器人只在评论区被 @ 时才登场，
 * 首页不用给它一个常驻卡片，书架位子留给内容本身。
 *
 * 每个吧前面是图标（icon），没上传图标时用吧名首字兜底。
 */
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import * as taxonomyApi from '@/api/taxonomy'

const route = useRoute()

/* ---------------- 热门书吧（Top20，后端隔天刷新） ---------------- */
const hotBars = ref([])
const hotBarsLoading = ref(true)

async function loadHotBars() {
  hotBarsLoading.value = true
  try {
    hotBars.value = (await taxonomyApi.listHotBars()) || []
  } catch {
    hotBars.value = []
  } finally {
    hotBarsLoading.value = false
  }
}

/** 路由名是 bar（/bars/:id），不是 category */
const activeBarId = computed(() => (route.name === 'bar' ? String(route.params.id) : ''))

/** 无图标时用吧名首字兜底 */
function initial(name) {
  return (name || '吧').slice(0, 1)
}

onMounted(loadHotBars)
</script>

<template>
  <aside class="sidebar">
    <!-- 热门书吧：按成员数前 20（后端隔天刷新一次） -->
    <div class="bn-card side-block">
      <div class="side-head">
        <h3>热门书吧</h3>
        <router-link to="/bars" class="side-more">全部书吧</router-link>
      </div>

      <div v-if="hotBars.length" class="bar-list">
        <router-link
          v-for="(bar, index) in hotBars"
          :key="bar.id"
          :to="`/bars/${bar.id}`"
          :class="['bar-item', { active: activeBarId === String(bar.id) }]"
        >
          <span class="hot-rank" :class="{ top: index < 3 }">{{ index + 1 }}</span>
          <img v-if="bar.icon" :src="bar.icon" :alt="bar.name" class="bar-icon" />
          <span v-else class="bar-icon bar-icon-text">{{ initial(bar.name) }}</span>
          <span class="bar-name bn-ellipsis-1">{{ bar.name }}</span>
          <span class="bar-count">{{ bar.memberCount || 0 }} 人</span>
        </router-link>
      </div>
      <el-skeleton v-else-if="hotBarsLoading" :rows="4" animated />
      <p v-else class="side-empty">
        还没有书吧，<router-link to="/bars" class="side-link">去申请创建</router-link>
      </p>
    </div>

  </aside>
</template>

<style scoped>
.sidebar {
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

/* Top20 一屏放不下，超出后自身滚动，别把侧栏拉得过长 */
.bar-list {
  display: flex;
  flex-direction: column;
  gap: 2px;
  max-height: 340px;
  overflow-y: auto;
  margin: -3px -4px;
  padding: 3px 4px;
}

.bar-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 5px 6px;
  border-radius: 8px;
  font-size: 13.5px;
  color: var(--bn-text-sub);
  transition: all 0.15s ease;
}

.bar-item:hover,
.bar-item.active {
  background: var(--bn-primary-soft);
  color: var(--bn-primary);
}

/* 热榜序号：前三名高亮，给「热门」一个直观的视觉梯度 */
.hot-rank {
  flex-shrink: 0;
  width: 18px;
  text-align: center;
  font-size: 12px;
  font-weight: 700;
  color: var(--bn-text-muted);
  font-style: italic;
}

.hot-rank.top {
  color: #d4733f;
}

.bar-icon {
  width: 22px;
  height: 22px;
  flex-shrink: 0;
  border-radius: 6px;
  object-fit: cover;
  background: #efe9e2;
}

.bar-icon-text {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 11.5px;
  font-weight: 700;
  color: var(--bn-primary);
  background: var(--bn-primary-soft);
}

.bar-name {
  min-width: 0;
  flex: 1;
}

.bar-count {
  flex-shrink: 0;
  font-size: 11.5px;
  color: var(--bn-text-muted);
}

.side-empty {
  font-size: 12.5px;
  color: var(--bn-text-muted);
  padding: 8px 0;
}

.side-link {
  color: var(--bn-primary);
}
</style>
