<script setup>
/**
 * 管理后台布局：左侧菜单 + 顶栏 + 内容区
 */
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAdminStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()
const adminStore = useAdminStore()

const MENUS = [
  { name: 'admin-post', path: '/admin/post', label: '帖子审核', icon: 'DocumentChecked' },
  { name: 'admin-book', path: '/admin/book', label: '书籍管理', icon: 'Reading' },
  { name: 'admin-category', path: '/admin/category', label: '书吧管理', icon: 'Grid' },
  { name: 'admin-tag', path: '/admin/tag', label: '标签管理', icon: 'PriceTag' },
  { name: 'admin-user', path: '/admin/user', label: '用户管理', icon: 'UserFilled' },
  { name: 'admin-bot', path: '/admin/bot', label: 'AI 机器人', icon: 'MagicStick' }
]

const activeMenu = computed(() => route.path)
const pageTitle = computed(() => route.meta.title || '管理后台')

function onLogout() {
  adminStore.logout()
  ElMessage.success('已退出管理后台')
  router.push({ name: 'admin-login' })
}
</script>

<template>
  <el-container class="admin-layout">
    <el-aside width="210px" class="aside">
      <div class="brand">
        <span class="brand-mark">B</span>
        <div class="brand-text">
          <p class="brand-name">BookNest</p>
          <p class="brand-sub">管理后台</p>
        </div>
      </div>

      <el-menu :default-active="activeMenu" router class="menu">
        <el-menu-item v-for="item in MENUS" :key="item.name" :index="item.path">
          <el-icon><component :is="item.icon" /></el-icon>
          <span>{{ item.label }}</span>
        </el-menu-item>
      </el-menu>

      <router-link to="/" class="back-site">
        <el-icon><Back /></el-icon>返回前台
      </router-link>
    </el-aside>

    <el-container>
      <el-header class="header">
        <h1 class="header-title">{{ pageTitle }}</h1>
        <div class="header-right">
          <span class="admin-name">
            <el-icon><UserFilled /></el-icon>{{ adminStore.username }}
          </span>
          <el-button size="small" @click="onLogout">
            <el-icon style="margin-right: 3px"><SwitchButton /></el-icon>退出
          </el-button>
        </div>
      </el-header>

      <el-main class="main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<style scoped>
.admin-layout {
  min-height: 100vh;
}

.aside {
  background: #2f2a25;
  display: flex;
  flex-direction: column;
  position: sticky;
  top: 0;
  height: 100vh;
}

.brand {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 18px 18px 16px;
}

.brand-mark {
  width: 34px;
  height: 34px;
  border-radius: 10px;
  background: var(--bn-primary);
  color: #fff;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-weight: 700;
  font-size: 17px;
  flex-shrink: 0;
}

.brand-name {
  font-size: 14.5px;
  font-weight: 700;
  color: #f3ece4;
}

.brand-sub {
  font-size: 11.5px;
  color: #9c938a;
}

.menu {
  flex: 1;
  border-right: none;
  background: transparent;
  padding: 6px 10px;
}

.menu :deep(.el-menu-item) {
  color: #b9b0a7;
  border-radius: 8px;
  margin-bottom: 3px;
  height: 44px;
}

.menu :deep(.el-menu-item:hover) {
  background: rgba(255, 255, 255, 0.07);
  color: #f3ece4;
}

.menu :deep(.el-menu-item.is-active) {
  background: var(--bn-primary);
  color: #fff;
}

.back-site {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 14px 20px;
  font-size: 12.5px;
  color: #9c938a;
  border-top: 1px solid rgba(255, 255, 255, 0.08);
}

.back-site:hover {
  color: #f3ece4;
}

.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  background: #fff;
  border-bottom: 1px solid var(--bn-border);
  height: 58px;
}

.header-title {
  font-size: 16px;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 14px;
}

.admin-name {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-size: 13px;
  color: var(--bn-text-sub);
}

.main {
  background: var(--bn-bg);
  padding: 18px 20px 30px;
}

@media (max-width: 760px) {
  .aside {
    width: 64px !important;
  }
  .brand-text,
  .menu :deep(.el-menu-item span),
  .back-site {
    display: none;
  }
}
</style>
