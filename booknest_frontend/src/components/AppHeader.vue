<script setup>
/**
 * 顶部导航：Logo、搜索、导航入口、未读角标、用户菜单
 */
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useBadgeStore } from '@/stores/badge'
import { useUserStore } from '@/stores/user'
import BnAvatar from './BnAvatar.vue'

const router = useRouter()
const userStore = useUserStore()
const badgeStore = useBadgeStore()

const keyword = ref('')

const profileLink = computed(() => (userStore.userId ? `/user/${userStore.userId}` : '/login'))

function onSearch() {
  const value = keyword.value.trim()
  if (!value) {
    ElMessage.info('请输入要搜索的关键词')
    return
  }
  router.push({ name: 'search', query: { q: value } })
}

function onCommand(command) {
  if (command === 'logout') {
    userStore.logout()
    ElMessage.success('已退出登录')
    router.push({ name: 'home' })
    return
  }
  if (command === 'admin') {
    router.push({ name: 'admin-post' })
    return
  }
  router.push({ name: command })
}
</script>

<template>
  <header class="bn-header">
    <div class="bn-container header-inner">
      <!-- Logo -->
      <router-link to="/" class="logo">
        <span class="logo-mark">书</span>
        <span class="logo-text">BookNest</span>
      </router-link>

      <!-- 主导航 -->
      <nav class="nav">
        <router-link to="/" class="nav-link">首页</router-link>
        <router-link to="/booklist" class="nav-link">书单</router-link>
        <router-link to="/assistant" class="nav-link">
          <el-icon><MagicStick /></el-icon>AI 助手
        </router-link>
      </nav>

      <!-- 搜索 -->
      <div class="search">
        <el-input
          v-model="keyword"
          placeholder="搜索帖子、书籍、书单"
          clearable
          @keyup.enter="onSearch"
        >
          <template #prefix>
            <el-icon><Search /></el-icon>
          </template>
        </el-input>
      </div>

      <!-- 右侧操作区 -->
      <div class="actions">
        <template v-if="userStore.isLoggedIn">
          <router-link to="/post/edit" class="action-btn primary">写书评</router-link>

          <router-link to="/notification" class="icon-btn" title="通知">
            <el-badge :value="badgeStore.notificationUnread" :hidden="!badgeStore.notificationUnread" :max="99">
              <el-icon :size="19"><Bell /></el-icon>
            </el-badge>
          </router-link>

          <router-link to="/chat" class="icon-btn" title="私信">
            <el-badge :value="badgeStore.chatUnread" :hidden="!badgeStore.chatUnread" :max="99">
              <el-icon :size="19"><ChatDotRound /></el-icon>
            </el-badge>
          </router-link>

          <el-dropdown trigger="click" @command="onCommand">
            <div class="user-entry">
              <BnAvatar :src="userStore.avatar" :name="userStore.username" :size="30" :linkable="false" />
              <span class="user-name bn-ellipsis-1">{{ userStore.username }}</span>
              <el-icon :size="12"><ArrowDown /></el-icon>
            </div>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="user-profile" :disabled="!userStore.userId">
                  <el-icon><User /></el-icon>个人主页
                </el-dropdown-item>
                <el-dropdown-item command="assistant">
                  <el-icon><MagicStick /></el-icon>AI 助手
                </el-dropdown-item>
                <el-dropdown-item v-if="userStore.isAdmin" command="admin" divided>
                  <el-icon><Setting /></el-icon>管理后台
                </el-dropdown-item>
                <el-dropdown-item command="logout" divided>
                  <el-icon><SwitchButton /></el-icon>退出登录
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>

          <!-- 个人主页入口（dropdown 的 command 依赖动态路由，这里用隐式按钮兜底） -->
          <router-link :to="profileLink" class="hidden-link" aria-hidden="true" />
        </template>

        <template v-else>
          <router-link to="/login" class="action-btn">登录</router-link>
          <router-link to="/register" class="action-btn primary">注册</router-link>
        </template>
      </div>
    </div>
  </header>
</template>

<style scoped>
.bn-header {
  position: sticky;
  top: 0;
  z-index: 100;
  background: rgba(255, 255, 255, 0.92);
  backdrop-filter: blur(10px);
  border-bottom: 1px solid var(--bn-border);
}

.header-inner {
  display: flex;
  align-items: center;
  gap: 18px;
  height: 62px;
}

.logo {
  display: flex;
  align-items: center;
  gap: 9px;
  flex-shrink: 0;
}

.logo-mark {
  width: 32px;
  height: 32px;
  border-radius: 9px;
  background: var(--bn-primary);
  color: #fff;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-weight: 700;
  font-size: 17px;
}

.logo-text {
  font-size: 18px;
  font-weight: 700;
  letter-spacing: 0.2px;
}

.nav {
  display: flex;
  align-items: center;
  gap: 4px;
  flex-shrink: 0;
}

.nav-link {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 6px 12px;
  border-radius: 8px;
  font-size: 14px;
  color: var(--bn-text-sub);
  transition: all 0.15s ease;
}

.nav-link:hover {
  background: var(--bn-primary-soft);
  color: var(--bn-primary);
}

.nav-link.router-link-exact-active {
  color: var(--bn-primary);
  font-weight: 600;
  background: var(--bn-primary-soft);
}

.search {
  flex: 1;
  max-width: 340px;
  min-width: 120px;
}

.search :deep(.el-input__wrapper) {
  background: #f7f5f2;
  box-shadow: none;
  border: 1px solid transparent;
}

.search :deep(.el-input__wrapper.is-focus) {
  background: #fff;
  border-color: var(--bn-primary);
}

.actions {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-left: auto;
  flex-shrink: 0;
}

.action-btn {
  padding: 6px 14px;
  border-radius: 8px;
  font-size: 13.5px;
  border: 1px solid var(--bn-border);
  color: var(--bn-text-sub);
  transition: all 0.15s ease;
  white-space: nowrap;
}

.action-btn:hover {
  border-color: var(--bn-primary);
  color: var(--bn-primary);
}

.action-btn.primary {
  background: var(--bn-primary);
  border-color: var(--bn-primary);
  color: #fff;
}

.action-btn.primary:hover {
  background: var(--bn-primary-light);
  border-color: var(--bn-primary-light);
  color: #fff;
}

.icon-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  border-radius: 8px;
  color: var(--bn-text-sub);
  transition: all 0.15s ease;
}

.icon-btn:hover {
  background: var(--bn-primary-soft);
  color: var(--bn-primary);
}

.user-entry {
  display: flex;
  align-items: center;
  gap: 7px;
  padding: 3px 8px 3px 3px;
  border-radius: 999px;
  cursor: pointer;
  transition: background 0.15s ease;
  max-width: 150px;
}

.user-entry:hover {
  background: var(--bn-primary-soft);
}

.user-name {
  font-size: 13.5px;
  max-width: 84px;
}

.hidden-link {
  display: none;
}

@media (max-width: 860px) {
  .nav,
  .user-name {
    display: none;
  }
  .search {
    max-width: none;
  }
}
</style>
