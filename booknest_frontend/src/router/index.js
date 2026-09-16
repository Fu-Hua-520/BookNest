import { createRouter, createWebHistory } from 'vue-router'
import { ADMIN_TOKEN_KEY, USER_TOKEN_KEY } from '@/utils/request'

/** 路由表：与 plan.md 第 7 节约定对齐 */
const routes = [
  {
    path: '/',
    name: 'home',
    component: () => import('@/views/HomeView.vue'),
    meta: { title: '首页' }
  },
  {
    path: '/post/:id',
    name: 'post-detail',
    component: () => import('@/views/PostDetailView.vue'),
    meta: { title: '帖子详情' }
  },
  {
    path: '/post/edit/:id?',
    name: 'post-edit',
    component: () => import('@/views/PostEditView.vue'),
    meta: { title: '写书评', requiresAuth: true }
  },
  {
    path: '/book/:id',
    name: 'book-detail',
    component: () => import('@/views/BookDetailView.vue'),
    meta: { title: '书籍详情' }
  },
  {
    path: '/booklist',
    name: 'booklist-list',
    component: () => import('@/views/BooklistListView.vue'),
    meta: { title: '书单广场' }
  },
  {
    path: '/booklist/create',
    name: 'booklist-create',
    component: () => import('@/views/BooklistEditView.vue'),
    meta: { title: '创建书单', requiresAuth: true }
  },
  {
    path: '/booklist/:id/edit',
    name: 'booklist-edit',
    component: () => import('@/views/BooklistEditView.vue'),
    meta: { title: '编辑书单', requiresAuth: true }
  },
  {
    path: '/booklist/:id',
    name: 'booklist-detail',
    component: () => import('@/views/BooklistDetailView.vue'),
    meta: { title: '书单详情' }
  },
  {
    path: '/bars',
    name: 'bar-list',
    component: () => import('@/views/BarsView.vue'),
    meta: { title: '书吧广场' }
  },
  {
    path: '/bars/:id',
    name: 'bar',
    component: () => import('@/views/CategoryView.vue'),
    meta: { title: '书吧' }
  },
  // 兼容改造前的老链接：/category/:id 就是现在的书吧
  {
    path: '/category/:id',
    redirect: (to) => ({ name: 'bar', params: { id: to.params.id } })
  },
  {
    path: '/tag/:id',
    name: 'tag',
    component: () => import('@/views/TagView.vue'),
    meta: { title: '标签' }
  },
  {
    path: '/search',
    name: 'search',
    component: () => import('@/views/SearchView.vue'),
    meta: { title: '搜索' }
  },
  {
    path: '/bots',
    name: 'bot-center',
    component: () => import('@/views/BotCenterView.vue'),
    meta: { title: 'AI 机器人', requiresAuth: true }
  },
  {
    path: '/chat',
    name: 'chat',
    component: () => import('@/views/ChatView.vue'),
    meta: { title: '私信', requiresAuth: true }
  },
  {
    path: '/user/:id',
    name: 'user-profile',
    component: () => import('@/views/UserProfileView.vue'),
    meta: { title: '个人主页' }
  },
  {
    path: '/user/:id/following',
    name: 'user-following',
    component: () => import('@/views/UserFollowView.vue'),
    meta: { title: '关注列表' }
  },
  {
    path: '/user/:id/followers',
    name: 'user-followers',
    component: () => import('@/views/UserFollowView.vue'),
    meta: { title: '粉丝列表' }
  },
  {
    path: '/notification',
    name: 'notification',
    component: () => import('@/views/NotificationView.vue'),
    meta: { title: '通知', requiresAuth: true }
  },
  {
    path: '/login',
    name: 'login',
    component: () => import('@/views/LoginView.vue'),
    meta: { title: '登录', guestOnly: true }
  },
  {
    path: '/register',
    name: 'register',
    component: () => import('@/views/RegisterView.vue'),
    meta: { title: '注册', guestOnly: true }
  },
  {
    path: '/admin/login',
    name: 'admin-login',
    component: () => import('@/views/admin/AdminLoginView.vue'),
    meta: { title: '管理后台登录' }
  },
  {
    path: '/admin',
    component: () => import('@/views/admin/AdminLayout.vue'),
    redirect: { name: 'admin-post' },
    meta: { requiresAdmin: true },
    children: [
      {
        path: 'post',
        name: 'admin-post',
        component: () => import('@/views/admin/AdminPostView.vue'),
        meta: { title: '帖子审核' }
      },
      {
        path: 'book',
        name: 'admin-book',
        component: () => import('@/views/admin/AdminBookView.vue'),
        meta: { title: '书籍管理' }
      },
      {
        path: 'category',
        name: 'admin-category',
        component: () => import('@/views/admin/AdminCategoryView.vue'),
        meta: { title: '书吧管理' }
      },
      {
        path: 'tag',
        name: 'admin-tag',
        component: () => import('@/views/admin/AdminTagView.vue'),
        meta: { title: '标签管理' }
      },
      {
        path: 'bot',
        name: 'admin-bot',
        component: () => import('@/views/admin/AdminBotView.vue'),
        meta: { title: 'AI 机器人' }
      },
      {
        path: 'user',
        name: 'admin-user',
        component: () => import('@/views/admin/AdminUserView.vue'),
        meta: { title: '用户管理' }
      }
    ]
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'not-found',
    component: () => import('@/views/NotFoundView.vue'),
    meta: { title: '页面不存在' }
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior(to, from, savedPosition) {
    return savedPosition || { top: 0 }
  }
})

/** 全局守卫：登录校验 + 管理端校验 + 标题设置 */
router.beforeEach((to) => {
  document.title = to.meta.title ? `${to.meta.title} · BookNest` : 'BookNest · 书籍交流社区'

  if (to.meta.requiresAuth && !localStorage.getItem(USER_TOKEN_KEY)) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
  if (to.meta.requiresAdmin && !localStorage.getItem(ADMIN_TOKEN_KEY)) {
    return { name: 'admin-login', query: { redirect: to.fullPath } }
  }
  if (to.meta.guestOnly && localStorage.getItem(USER_TOKEN_KEY)) {
    return { name: 'home' }
  }
  return true
})

export default router
