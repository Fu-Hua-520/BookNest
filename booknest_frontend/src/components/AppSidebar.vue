<script setup>
/**
 * 左侧主侧边栏（占满整列的固定栏）
 *
 * 设计取向参考 DeepSeek / Slack 这类工作台：
 *   - 侧栏是**整列**的（top:0 → bottom:0），而不是页面左缘的几个悬浮小按钮
 *   - 展开 240px、折叠 58px，折叠后只留图标，状态持久化在 layout store
 *   - 由 App.vue 给正文外壳加 padding-left，侧栏是「挤占」而不是「盖住」内容
 *   - 消息是主功能，所以做成整块高亮卡片（带未读角标），而不是列表里的一行
 *
 * 聊天面板不再做成「点外部自动收起」的抽屉：
 * 之前那种实现里，Element Plus 的 el-dialog 默认 teleport 到 body，
 * 点弹窗内的输入框会被判定成「点在抽屉外」→ 抽屉卸载 → 弹窗跟着消失。
 * 现在聊天面板是一个显式开关的固定面板，只能由关闭按钮收起，从根上不存在这类误判。
 *
 * 「我关注的吧」放在这里而不是首页右侧：关注关系是长期存在的导航目标，
 * 换成页面（比如书单、个人主页）时它跟内容无关、不该消失；右侧那栏是
 * 跟着首页内容走的「热门书吧」，两者分工不同，别再合并回一处。
 */
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useBadgeStore } from '@/stores/badge'
import { useChatStore } from '@/stores/chat'
import { useUserStore } from '@/stores/user'
import * as barApi from '@/api/bar'
import {
  SIDEBAR_WIDTH_COLLAPSED,
  SIDEBAR_WIDTH_EXPANDED,
  useLayoutStore
} from '@/stores/layout'
import ChatPanel from '@/components/chat/ChatPanel.vue'
import BnAvatar from '@/components/BnAvatar.vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const badgeStore = useBadgeStore()
const chatStore = useChatStore()
const layoutStore = useLayoutStore()

/** 窄屏下强制按折叠态渲染：240px 的整列会把正文挤成一条缝 */
const narrow = ref(typeof window !== 'undefined' && window.innerWidth < 900)
function onResize() {
  narrow.value = window.innerWidth < 900
}

/* ---------------- 我关注的吧 ---------------- */
const myBars = ref([])
const myBarsLoaded = ref(false)
/** 「关注的吧」这一行是否展开：默认收起，和通知 / 收藏一样先是一行，点开才列吧 */
const barsOpen = ref(false)

async function loadMyBars() {
  if (!userStore.isLoggedIn) {
    myBars.value = []
    myBarsLoaded.value = true
    return
  }
  try {
    myBars.value = (await barApi.listMyBars()) || []
  } catch {
    myBars.value = []
  } finally {
    myBarsLoaded.value = true
  }
}

/** 无图标时用吧名首字兜底 */
function initial(name) {
  return (name || '吧').slice(0, 1)
}

/** 当前正在浏览的吧（路由名是 bar，不是 category） */
const activeBarId = computed(() => (route.name === 'bar' ? String(route.params.id) : ''))

/** 聊天面板是否打开 */
const flyoutOpen = ref(false)
/** 打开时要求 ChatPanel 落在哪个 Tab：private / group */
const flyoutTab = ref('private')
/** 打开时直接进入的渠道（可空，空则停在会话列表） */
const flyoutChannel = ref(null)

const collapsed = computed(() => layoutStore.collapsed || narrow.value)
/** 宽度自己算而不是直接读 store：窄屏要覆盖用户的折叠偏好 */
const width = computed(() =>
  collapsed.value ? SIDEBAR_WIDTH_COLLAPSED : SIDEBAR_WIDTH_EXPANDED
)

/** 管理后台是另一套骨架；整页聊天页本身就是完整的聊天界面，都不需要侧栏 */
const visible = computed(
  () => userStore.isLoggedIn && !route.path.startsWith('/admin') && route.path !== '/chat'
)

onMounted(() => {
  if (userStore.isLoggedIn) {
    chatStore.connect()
    loadMyBars()
  }
  window.addEventListener('resize', onResize)
})

watch(
  () => userStore.isLoggedIn,
  (loggedIn) => {
    if (loggedIn) {
      chatStore.connect()
      loadMyBars()
    } else {
      chatStore.disconnect()
      myBars.value = []
      closeFlyout()
    }
  }
)

/** 在书吧页关注 / 取关后，左侧列表要跟着变 —— 用路由变化做触发，最省心 */
watch(
  () => route.fullPath,
  (path, prev) => {
    if (prev && path !== prev && userStore.isLoggedIn) loadMyBars()
  }
)

/** 切到管理后台 / 整页聊天时收起面板，避免残留 */
watch(
  () => route.path,
  (path) => {
    if (path.startsWith('/admin') || path === '/chat') closeFlyout()
  }
)

function toggleCollapse() {
  layoutStore.toggleSidebar()
  if (layoutStore.collapsed) closeFlyout()
}

/**
 * 「关注的吧」这一行的点击行为。
 * 折叠态只剩 58px，塞不下一列子项，此时退化成「去书吧广场」的跳转 ——
 * 比点一下没反应、或者硬挤出一列看不清的图标都好。
 */
function onBarsEntryClick() {
  if (collapsed.value) {
    go('bar-list')
    return
  }
  barsOpen.value = !barsOpen.value
}

function openFlyout(tab = 'private', channel = null) {
  flyoutTab.value = tab
  flyoutChannel.value = channel
  flyoutOpen.value = true
  chatStore.connect()
}

function closeFlyout() {
  flyoutOpen.value = false
  flyoutChannel.value = null
}

function go(name, query) {
  router.push(query ? { name, query } : { name })
}

function goProfile() {
  if (!userStore.userId) {
    ElMessage.info('请先登录')
    return
  }
  // /user/:id 的 id 是必填参数，少传会被 vue-router 直接中止导航（表现为「点了没反应」）
  router.push({ name: 'user-profile', params: { id: String(userStore.userId) } })
}

function goCollection() {
  if (!userStore.userId) {
    ElMessage.info('请先登录')
    return
  }
  router.push({ name: 'user-profile', params: { id: String(userStore.userId) }, query: { tab: 'collected' } })
}

function logout() {
  userStore.logout()
  ElMessage.success('已退出登录')
  router.push({ name: 'home' })
}

onUnmounted(() => {
  closeFlyout()
  window.removeEventListener('resize', onResize)
})
</script>

<template>
  <template v-if="visible">
    <aside id="bn-app-sidebar" :class="['app-sidebar', { collapsed }]" :style="{ width: width + 'px' }">
      <!-- 头部：品牌 + 折叠 -->
      <div class="sb-head">
        <router-link to="/" class="sb-brand" :title="collapsed ? 'BookNest 首页' : ''">
          <span class="sb-mark">书</span>
          <span v-if="!collapsed" class="sb-brand-text">BookNest</span>
        </router-link>
        <button
          type="button"
          class="sb-collapse"
          :title="collapsed ? '展开侧栏' : '收起侧栏'"
          @click="toggleCollapse"
        >
          <el-icon><component :is="collapsed ? 'ArrowRight' : 'ArrowLeft'" /></el-icon>
        </button>
      </div>

      <!-- 主操作 -->
      <button type="button" class="sb-primary" :title="collapsed ? '写书评' : ''" @click="go('post-edit')">
        <el-icon><EditPen /></el-icon>
        <span v-if="!collapsed">写书评</span>
      </button>

      <!-- 消息：主功能，做成整块高亮卡片 -->
      <button
        type="button"
        :class="['sb-chat-card', { on: flyoutOpen }]"
        :title="collapsed ? '消息中心' : ''"
        @click="flyoutOpen ? closeFlyout() : openFlyout('private')"
      >
        <span class="sb-chat-icon">
          <el-icon :size="18"><ChatDotRound /></el-icon>
          <span v-if="badgeStore.chatUnread" class="sb-chat-dot">
            {{ badgeStore.chatUnread > 99 ? '99+' : badgeStore.chatUnread }}
          </span>
          <!-- 入群申请红点：与未读数互相独立，处理完由聊天面板回调清零 -->
          <span v-else-if="badgeStore.groupRequestCount" class="sb-chat-dot sb-request-dot" />
        </span>
        <span v-if="!collapsed" class="sb-chat-text">
          <b>消息中心</b>
          <small v-if="badgeStore.groupRequestCount" class="sb-request-text">
            {{ badgeStore.groupRequestCount }} 条入群申请待审批
          </small>
          <small v-else>
            {{ badgeStore.chatUnread ? `${badgeStore.chatUnread} 条未读` : '私信 · 群聊' }}
          </small>
        </span>
      </button>

      <!-- 导航：不再单独放「群聊」—— 上面那张消息卡片点开后,
           面板内自带「私信 / 群聊」两个 Tab，再列一项就是同一个入口出现两次 -->
      <nav class="sb-nav">
        <button type="button" class="sb-item" :title="collapsed ? '通知' : ''" @click="go('notification')">
          <el-badge :value="badgeStore.notificationUnread" :hidden="!badgeStore.notificationUnread" :max="99">
            <el-icon><Bell /></el-icon>
          </el-badge>
          <span v-if="!collapsed" class="sb-item-label">通知</span>
        </button>
        <button type="button" class="sb-item" :title="collapsed ? '我的收藏' : ''" @click="goCollection">
          <el-icon><Star /></el-icon>
          <span v-if="!collapsed" class="sb-item-label">我的收藏</span>
        </button>

        <!-- 关注的吧：与上面几项同款的导航行，点一下才展开列表。
             折叠态没有展开的余地，点击直接去书吧广场。 -->
        <button
          type="button"
          :class="['sb-item', { 'sb-item-on': barsOpen }]"
          :title="collapsed ? '书吧广场' : ''"
          @click="onBarsEntryClick"
        >
          <el-icon><Grid /></el-icon>
          <span v-if="!collapsed" class="sb-item-label">关注的吧</span>
          <el-icon v-if="!collapsed" class="sb-item-arrow">
            <component :is="barsOpen ? 'ArrowDown' : 'ArrowRight'" />
          </el-icon>
        </button>

        <div v-if="barsOpen && !collapsed" class="sb-sublist">
          <router-link
            v-for="bar in myBars"
            :key="bar.id"
            :to="`/bars/${bar.id}`"
            :class="['sb-subitem', { active: activeBarId === String(bar.id) }]"
          >
            <img v-if="bar.icon" :src="bar.icon" :alt="bar.name" class="sb-sub-icon" />
            <span v-else class="sb-sub-icon sb-sub-icon-text">{{ initial(bar.name) }}</span>
            <span class="sb-sub-name bn-ellipsis-1">{{ bar.name }}</span>
          </router-link>

          <p v-if="!myBars.length" class="sb-sub-empty">
            {{ myBarsLoaded ? '还没关注任何书吧' : '加载中…' }}
          </p>
          <button type="button" class="sb-sub-more" @click="go('bar-list')">去书吧广场</button>
        </div>

        <button type="button" class="sb-item" :title="collapsed ? '书单' : ''" @click="go('booklist-list')">
          <el-icon><Files /></el-icon>
          <span v-if="!collapsed" class="sb-item-label">书单</span>
        </button>
        <button type="button" class="sb-item" :title="collapsed ? 'AI 机器人' : ''" @click="go('bot-center')">
          <el-icon><MagicStick /></el-icon>
          <span v-if="!collapsed" class="sb-item-label">AI 机器人</span>
        </button>
        <button type="button" class="sb-item" :title="collapsed ? '个人主页' : ''" @click="goProfile">
          <el-icon><User /></el-icon>
          <span v-if="!collapsed" class="sb-item-label">个人主页</span>
        </button>
      </nav>

      <!-- 底部用户区 -->
      <div class="sb-foot">
        <router-link v-if="!collapsed" :to="`/user/${userStore.userId}`" class="sb-user">
          <BnAvatar :src="userStore.avatar" :name="userStore.username" :size="28" :linkable="false" />
          <span class="sb-user-name bn-ellipsis-1">{{ userStore.username }}</span>
        </router-link>
        <BnAvatar v-else :src="userStore.avatar" :name="userStore.username" :size="28" :linkable="false" />
        <button v-if="!collapsed" type="button" class="sb-logout" title="退出登录" @click="logout">
          <el-icon><SwitchButton /></el-icon>
        </button>
      </div>
    </aside>

    <!-- 聊天面板：紧贴侧栏右侧的整列固定面板 -->
    <transition name="flyout">
      <section v-if="flyoutOpen" class="chat-flyout" :style="{ left: width + 'px' }">
        <header class="flyout-head">
          <div>
            <p class="flyout-title">消息中心</p>
            <p class="flyout-sub">
              <span :class="['conn-dot', { on: chatStore.socketReady }]" />
              {{ chatStore.socketReady ? '实时连接正常' : '连接中…' }}
            </p>
          </div>
          <div class="flyout-actions">
            <el-button link size="small" title="在整页中打开" @click="router.push({ name: 'chat' }); closeFlyout()">
              <el-icon><FullScreen /></el-icon>
            </el-button>
            <el-button link size="small" title="收起" @click="closeFlyout">
              <el-icon><Close /></el-icon>
            </el-button>
          </div>
        </header>
        <div class="flyout-body">
          <ChatPanel compact :initial="flyoutChannel" :initial-tab="flyoutTab" />
        </div>
      </section>
    </transition>
  </template>
</template>

<style scoped>
/* ---------- 侧栏本体 ---------- */
.app-sidebar {
  position: fixed;
  left: 0;
  top: 0;
  bottom: 0;
  z-index: 110;
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 12px 10px;
  background: #fbf9f7;
  border-right: 1px solid var(--bn-border);
  overflow: hidden;
  transition: width 0.18s ease;
}

.sb-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 6px;
  min-height: 34px;
}

.sb-brand {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.sb-mark {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  flex-shrink: 0;
  border-radius: 8px;
  background: var(--bn-primary);
  color: #fff;
  font-weight: 700;
  font-size: 15px;
}

.sb-brand-text {
  font-size: 15.5px;
  font-weight: 700;
  white-space: nowrap;
}

.sb-collapse {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 26px;
  height: 26px;
  flex-shrink: 0;
  border: none;
  border-radius: 7px;
  background: transparent;
  color: var(--bn-text-muted);
  cursor: pointer;
  transition: all 0.15s ease;
}

.sb-collapse:hover {
  background: var(--bn-primary-soft);
  color: var(--bn-primary);
}

/* ---------- 主按钮 ---------- */
.sb-primary {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 7px;
  width: 100%;
  padding: 9px 10px;
  border: none;
  border-radius: 10px;
  background: var(--bn-primary);
  color: #fff;
  font-family: inherit;
  font-size: 13.5px;
  font-weight: 600;
  cursor: pointer;
  transition: background 0.15s ease;
}

.sb-primary:hover {
  background: var(--bn-primary-light);
}

/* ---------- 消息卡片（主功能，做醒目） ---------- */
.sb-chat-card {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  padding: 10px;
  border: 1px solid #eadfd2;
  border-radius: 11px;
  background: linear-gradient(135deg, #fff6ee 0%, #fdeee0 100%);
  font-family: inherit;
  text-align: left;
  cursor: pointer;
  transition: all 0.16s ease;
}

.sb-chat-card:hover {
  border-color: var(--bn-primary);
  box-shadow: 0 4px 14px rgba(47, 42, 37, 0.1);
}

.sb-chat-card.on {
  border-color: var(--bn-primary);
  background: var(--bn-primary-soft);
}

.sb-chat-icon {
  position: relative;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  flex-shrink: 0;
  border-radius: 9px;
  background: var(--bn-primary);
  color: #fff;
}

.sb-chat-dot {
  position: absolute;
  top: -6px;
  right: -8px;
  min-width: 17px;
  height: 17px;
  padding: 0 4px;
  border-radius: 999px;
  background: #e4573d;
  color: #fff;
  font-size: 10px;
  line-height: 17px;
  text-align: center;
  box-shadow: 0 0 0 2px #fff6ee;
}

/* 入群申请纯红点：不带数字，比未读数角标小一号，避免两种提醒混淆 */
.sb-request-dot {
  min-width: 10px;
  width: 10px;
  height: 10px;
  top: -4px;
  right: -5px;
  padding: 0;
}

.sb-request-text {
  color: #e4573d;
}

.sb-chat-text {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.sb-chat-text b {
  font-size: 13.5px;
  color: var(--bn-text);
}

.sb-chat-text small {
  font-size: 11.5px;
  color: var(--bn-text-muted);
  margin-top: 1px;
}

/* ---------- 「关注的吧」展开后的子列表 ---------- */
/* 缩进对齐到图标右侧，视觉上是挂在那一行下面的 */
.sb-sublist {
  display: flex;
  flex-direction: column;
  gap: 1px;
  padding: 2px 6px 4px 34px;
  max-height: 210px;
  overflow-y: auto;
}

.sb-subitem {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 5px 8px;
  border-radius: 8px;
  font-size: 13px;
  color: var(--bn-text-sub);
  transition: all 0.15s ease;
}

.sb-subitem:hover,
.sb-subitem.active {
  background: var(--bn-primary-soft);
  color: var(--bn-primary);
}

.sb-sub-icon {
  width: 20px;
  height: 20px;
  flex-shrink: 0;
  border-radius: 6px;
  object-fit: cover;
  background: #efe9e2;
}

.sb-sub-icon-text {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 11px;
  font-weight: 700;
  color: var(--bn-primary);
  background: var(--bn-primary-soft);
}

.sb-sub-name {
  min-width: 0;
  flex: 1;
}

.sb-sub-empty {
  padding: 4px 8px 2px;
  font-size: 12px;
  color: var(--bn-text-muted);
}

.sb-sub-more {
  align-self: flex-start;
  margin: 3px 0 0 8px;
  padding: 0;
  border: none;
  background: transparent;
  font-family: inherit;
  font-size: 12px;
  color: var(--bn-text-muted);
  cursor: pointer;
}

.sb-sub-more:hover {
  color: var(--bn-primary);
}

/* ---------- 导航 ---------- */
.sb-nav {
  display: flex;
  flex-direction: column;
  gap: 2px;
  flex: 1;
  min-height: 0;
  overflow-y: auto;
}

.sb-item {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  padding: 8px 10px;
  border: none;
  border-radius: 9px;
  background: transparent;
  color: var(--bn-text-sub);
  font-family: inherit;
  font-size: 13.5px;
  text-align: left;
  cursor: pointer;
  transition: all 0.15s ease;
}

.sb-item:hover {
  background: var(--bn-primary-soft);
  color: var(--bn-primary);
}

.sb-item-label {
  flex: 1;
  min-width: 0;
  white-space: nowrap;
}

/* 展开着的父项保持高亮，和子列表形成一组 */
.sb-item-on {
  background: var(--bn-primary-soft);
  color: var(--bn-primary);
}

.sb-item-arrow {
  flex-shrink: 0;
  font-size: 12px;
  color: var(--bn-text-muted);
}

/* ---------- 底部用户区 ---------- */
.sb-foot {
  display: flex;
  align-items: center;
  gap: 8px;
  padding-top: 8px;
  border-top: 1px solid var(--bn-border);
}

.sb-user {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: 1;
  min-width: 0;
}

.sb-user-name {
  font-size: 13px;
  color: var(--bn-text);
}

.sb-logout {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 26px;
  height: 26px;
  flex-shrink: 0;
  border: none;
  border-radius: 7px;
  background: transparent;
  color: var(--bn-text-muted);
  cursor: pointer;
}

.sb-logout:hover {
  background: var(--bn-primary-soft);
  color: var(--bn-primary);
}

/* 折叠态：内容居中，隐藏文字 */
.app-sidebar.collapsed {
  padding: 12px 9px;
  align-items: center;
}

.app-sidebar.collapsed .sb-head {
  flex-direction: column;
  gap: 8px;
}

.app-sidebar.collapsed .sb-primary,
.app-sidebar.collapsed .sb-item {
  justify-content: center;
  padding: 9px 0;
}

.app-sidebar.collapsed .sb-chat-card {
  justify-content: center;
  padding: 8px 0;
}

.app-sidebar.collapsed .sb-foot {
  flex-direction: column;
}

/* ---------- 聊天面板 ---------- */
.chat-flyout {
  position: fixed;
  top: 0;
  bottom: 0;
  width: 400px;
  z-index: 105;
  display: flex;
  flex-direction: column;
  background: #fff;
  border-right: 1px solid var(--bn-border);
  box-shadow: 12px 0 30px rgba(47, 42, 37, 0.08);
  transition: left 0.18s ease;
}

.flyout-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 11px 14px;
  border-bottom: 1px solid var(--bn-border);
  background: #fdfcfa;
}

.flyout-title {
  font-size: 14px;
  font-weight: 600;
}

.flyout-sub {
  display: flex;
  align-items: center;
  gap: 5px;
  font-size: 11.5px;
  color: var(--bn-text-muted);
  margin-top: 2px;
}

.conn-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #c3c3c3;
}

.conn-dot.on {
  background: #4a9c6d;
}

.flyout-body {
  flex: 1;
  min-height: 0;
}

.flyout-enter-active,
.flyout-leave-active {
  transition: opacity 0.16s ease, transform 0.16s ease;
}

.flyout-enter-from,
.flyout-leave-to {
  opacity: 0;
  transform: translateX(-12px);
}

@media (max-width: 900px) {
  .chat-flyout {
    width: min(400px, calc(100vw - 58px));
  }
}
</style>
