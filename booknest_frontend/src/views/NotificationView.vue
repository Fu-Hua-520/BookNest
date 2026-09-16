<script setup>
/**
 * 通知中心：列表 / 已读 / 全部已读 / 删除
 * 通知类型：LIKE 点赞、COMMENT 评论、REPLY 回复评论、FOLLOW 关注、
 *          AI_REPLY 评论区机器人回复、SYSTEM 系统
 */
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as notificationApi from '@/api/notification'
import { fromNow } from '@/utils/format'
import { useBadgeStore } from '@/stores/badge'
import BnState from '@/components/BnState.vue'

const router = useRouter()
const badgeStore = useBadgeStore()

const notifications = ref([])
const loading = ref(true)
const error = ref('')
const filter = ref('all')
const marking = ref(false)

const TYPE_META = {
  LIKE: { label: '点赞', icon: 'Star', color: '#c8783c' },
  COMMENT: { label: '评论', icon: 'ChatDotRound', color: '#4a7ba7' },
  REPLY: { label: '回复', icon: 'ChatLineSquare', color: '#6a5aa8' },
  FOLLOW: { label: '关注', icon: 'User', color: '#4a9c6d' },
  // 评论区 @ 了 AI 机器人，机器人回复了你
  AI_REPLY: { label: 'AI 回复', icon: 'MagicStick', color: '#8b5e3c' },
  SYSTEM: { label: '系统', icon: 'Bell', color: '#8a7c6d' }
}

const unreadCount = computed(() => notifications.value.filter((item) => item.isRead === 0).length)

const visibleList = computed(() => {
  if (filter.value === 'unread') return notifications.value.filter((item) => item.isRead === 0)
  return notifications.value
})

function metaOf(type) {
  return TYPE_META[type] || TYPE_META.SYSTEM
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    notifications.value = (await notificationApi.listNotifications()) || []
    badgeStore.refresh()
  } catch (err) {
    error.value = err.message || '通知加载失败'
  } finally {
    loading.value = false
  }
}

async function onRead(item) {
  if (item.isRead === 1) return
  await notificationApi.markRead(item.id)
  item.isRead = 1
  badgeStore.refresh()
}

/** 点击通知：标记已读并尝试跳转到来源内容（带 anchorId 时定位到那条评论） */
async function onOpen(item) {
  await onRead(item)
  if (!item.sourceId) return
  if (item.type === 'FOLLOW') {
    router.push(`/user/${item.sourceId}`)
    return
  }
  if (item.type === 'LIKE' || item.type === 'COMMENT' || item.type === 'REPLY' || item.type === 'AI_REPLY') {
    router.push({
      path: `/post/${item.sourceId}`,
      query: item.anchorId ? { comment: item.anchorId } : {}
    })
  }
}

async function onReadAll() {
  if (!unreadCount.value) {
    ElMessage.info('没有未读通知')
    return
  }
  marking.value = true
  try {
    await notificationApi.markAllRead()
    notifications.value.forEach((item) => {
      item.isRead = 1
    })
    ElMessage.success('已全部标记为已读')
    badgeStore.clearNotification()
  } finally {
    marking.value = false
  }
}

async function onDelete(item) {
  try {
    await ElMessageBox.confirm('确定删除这条通知吗？', '删除确认', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消'
    })
  } catch {
    return
  }
  await notificationApi.deleteNotification(item.id)
  notifications.value = notifications.value.filter((n) => n.id !== item.id)
  ElMessage.success('已删除')
  badgeStore.refresh()
}

onMounted(load)
</script>

<template>
  <div class="bn-container notification-page">
    <div class="head">
      <div>
        <h1 class="bn-page-title">通知</h1>
        <p class="bn-page-sub">
          {{ unreadCount ? `你有 ${unreadCount} 条未读通知` : '没有未读通知' }}
        </p>
      </div>
      <el-button :loading="marking" :disabled="!unreadCount" @click="onReadAll">
        <el-icon style="margin-right: 4px"><Select /></el-icon>全部已读
      </el-button>
    </div>

    <el-radio-group v-model="filter" class="tabs">
      <el-radio-button value="all">全部 ({{ notifications.length }})</el-radio-button>
      <el-radio-button value="unread">未读 ({{ unreadCount }})</el-radio-button>
    </el-radio-group>

    <BnState
      :loading="loading"
      :error="error"
      :empty="!visibleList.length"
      :empty-text="filter === 'unread' ? '没有未读通知' : '暂时没有通知'"
      :skeleton-rows="5"
    >
      <div class="list">
        <div
          v-for="item in visibleList"
          :key="item.id"
          :class="['bn-card', 'item', { unread: item.isRead === 0 }]"
        >
          <span
            class="type-icon"
            :style="{ background: `${metaOf(item.type).color}18`, color: metaOf(item.type).color }"
          >
            <el-icon><component :is="metaOf(item.type).icon" /></el-icon>
          </span>

          <div class="item-body" @click="onOpen(item)">
            <div class="item-top">
              <span class="type-label">{{ metaOf(item.type).label }}</span>
              <span v-if="item.isRead === 0" class="dot" />
              <span class="item-time">{{ fromNow(item.createTime) }}</span>
            </div>
            <p class="item-content">{{ item.content || '你有一条新通知' }}</p>
          </div>

          <div class="item-ops">
            <el-button v-if="item.isRead === 0" link size="small" @click="onRead(item)">
              标为已读
            </el-button>
            <el-button link size="small" type="danger" @click="onDelete(item)">删除</el-button>
          </div>
        </div>
      </div>
    </BnState>
  </div>
</template>

<style scoped>
.notification-page {
  max-width: 820px;
}

.head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
}

.head .bn-page-sub {
  margin-bottom: 0;
}

.tabs {
  margin: 16px 0 16px;
}

.list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.item {
  display: flex;
  align-items: center;
  gap: 13px;
  padding: 14px 16px;
  transition: border-color 0.15s ease;
}

.item.unread {
  border-color: #e3d3c2;
  background: #fffdfa;
}

.type-icon {
  width: 36px;
  height: 36px;
  border-radius: 10px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  font-size: 16px;
}

.item-body {
  flex: 1;
  min-width: 0;
  cursor: pointer;
}

.item-top {
  display: flex;
  align-items: center;
  gap: 7px;
}

.type-label {
  font-size: 12.5px;
  font-weight: 600;
  color: var(--bn-text-sub);
}

.dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #e05252;
}

.item-time {
  font-size: 11.5px;
  color: var(--bn-text-muted);
  margin-left: auto;
}

.item-content {
  font-size: 13.5px;
  line-height: 1.65;
  margin-top: 3px;
  color: var(--bn-text);
  word-break: break-word;
}

.item-ops {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 2px;
  flex-shrink: 0;
}
</style>
