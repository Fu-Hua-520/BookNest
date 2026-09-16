<script setup>
/**
 * 书吧页（/bars/:id）：贴吧式的「吧」内部帖子流
 *
 * 历史沿革：这个文件原本是「分类页」。分类改造成书吧后，一个吧在数据上
 * 就是一个一级分类（复用 category 表，见 CategoryServiceImpl），所以页面
 * 逻辑不用重写，只是换了文案与跳转地址。路由里 /category/:id 会重定向到这里，
 * 老链接不会断。
 *
 * 未通过审核 / 已禁用的吧不会出现在 taxonomyStore 的树里（后端只下发可见的吧），
 * 所以这里取不到名字时统一显示「书吧」而不是「分类」。
 */
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as postApi from '@/api/post'
import * as barApi from '@/api/bar'
import * as userApi from '@/api/user'
import { useTaxonomyStore } from '@/stores/taxonomy'
import { useUserStore } from '@/stores/user'
import PostCard from '@/components/PostCard.vue'
import BnState from '@/components/BnState.vue'
import BnAvatar from '@/components/BnAvatar.vue'

const route = useRoute()
const router = useRouter()
const taxonomyStore = useTaxonomyStore()
const userStore = useUserStore()

const posts = ref([])
const loading = ref(false)
const error = ref('')

const page = ref(1)
const PAGE_SIZE = 10
const finished = ref(false)

const barId = computed(() => String(route.params.id || ''))

/**
 * 书吧名：库里存的就是「文学吧」这种完整名字。
 * 这里做一次兜底 —— 万一遇到没带「吧」的老数据，才补上，避免出现「文学吧吧」。
 */
const barName = computed(() => {
  const raw = taxonomyStore.findCategoryName(barId.value) || ''
  if (!raw) return '书吧'
  return raw.endsWith('吧') ? raw : `${raw}吧`
})

const barIcon = computed(() => taxonomyStore.findCategory(barId.value)?.icon || '')

/* ---------------- 我在这个吧里的身份 / 等级 ---------------- */

/**
 * membership 为 null 有两种可能：未登录，或接口失败。
 * 两种情况下 UI 都退化成「未关注 + 无等级」，不区分对待 ——
 * 游客本来也不该看到等级，接口失败时显示个关注按钮也无害。
 */
const membership = ref(null)
const followPending = ref(false)

const followed = computed(() => Boolean(membership.value?.followed))
const isOwner = computed(() => membership.value?.role === 'OWNER')
const canManage = computed(() => ['OWNER', 'MODERATOR'].includes(membership.value?.role))
/** 没有称号时回退成 Lv.N —— 等级字段一定有，称号可以没有 */
const levelText = computed(() => {
  const m = membership.value
  if (!m || !m.followed) return ''
  return m.title ? `Lv.${m.level} ${m.title}` : `Lv.${m.level}`
})

async function loadMembership() {
  // /bar/** 不在公开只读放行前缀里，游客调会 401，所以这里先挡一层
  if (!userStore.isLoggedIn) {
    membership.value = null
    return
  }
  try {
    membership.value = await barApi.getMembership(barId.value)
  } catch {
    membership.value = null
  }
}

async function toggleFollow() {
  if (!userStore.isLoggedIn) {
    router.push({ name: 'login', query: { redirect: route.fullPath } })
    return
  }
  followPending.value = true
  try {
    membership.value = followed.value
      ? await barApi.unfollowBar(barId.value)
      : await barApi.followBar(barId.value)
    ElMessage.success(followed.value ? '已关注这个吧' : '已取消关注')
  } catch {
    // 拦截器已提示
  } finally {
    followPending.value = false
  }
}

/* ---------------- 吧务：管理员 + 等级称号 ---------------- */

const manageVisible = ref(false)
const manageTab = ref('moderators')
const moderators = ref([])
const moderatorsLoading = ref(false)
const userKeyword = ref('')
const userOptions = ref([])
const userSearching = ref(false)

const MAX_LEVEL = 10
const titleRows = ref([])
const titlesSaving = ref(false)

async function openManage() {
  manageVisible.value = true
  loadModerators()
  if (isOwner.value) loadTitles()
}

async function loadModerators() {
  moderatorsLoading.value = true
  try {
    moderators.value = (await barApi.listModerators(barId.value)) || []
  } catch {
    moderators.value = []
  } finally {
    moderatorsLoading.value = false
  }
}

/** 搜索用户来任命管理员：后端只返回 id / 昵称 / 头像，不泄露手机号邮箱 */
async function searchUsers() {
  const kw = userKeyword.value.trim()
  if (!kw) {
    userOptions.value = []
    return
  }
  userSearching.value = true
  try {
    userOptions.value = (await userApi.searchUsers(kw, 10)) || []
  } catch {
    userOptions.value = []
  } finally {
    userSearching.value = false
  }
}

async function appoint(userId) {
  try {
    moderators.value = (await barApi.addModerator(barId.value, userId)) || []
    userKeyword.value = ''
    userOptions.value = []
    ElMessage.success('已任命')
  } catch {
    // 拦截器已提示（已是管理员 / 不是吧主等）
  }
}

async function revoke(row) {
  try {
    await ElMessageBox.confirm(`确定撤销「${row.username || '该用户'}」的管理员身份吗？`, '撤销管理员', {
      type: 'warning',
      confirmButtonText: '撤销',
      cancelButtonText: '取消'
    })
  } catch {
    return
  }
  moderators.value = (await barApi.removeModerator(barId.value, row.userId)) || []
  ElMessage.success('已撤销')
}

async function loadTitles() {
  const list = (await barApi.listTitles(barId.value)) || []
  const map = new Map(list.map((item) => [item.level, item.title]))
  // 固定 10 行：没设过称号的等级留空，「默认无称号」就是这个意思
  titleRows.value = Array.from({ length: MAX_LEVEL }, (_, i) => ({
    level: i + 1,
    title: map.get(i + 1) || ''
  }))
}

async function saveTitles() {
  titlesSaving.value = true
  try {
    const payload = titleRows.value
      .filter((row) => row.title && row.title.trim())
      .map((row) => ({ level: row.level, title: row.title.trim() }))
    await barApi.setTitles(barId.value, payload)
    ElMessage.success('称号已保存')
  } catch {
    // 拦截器已提示
  } finally {
    titlesSaving.value = false
  }
}

/** 在本吧发帖：带上 barId，发帖页会预选这个吧 */
function goPublish() {
  const query = { barId: barId.value }
  if (!userStore.isLoggedIn) {
    router.push({ name: 'login', query: { redirect: `/post/edit?barId=${barId.value}` } })
    return
  }
  router.push({ name: 'post-edit', query })
}

async function load(reset = false) {
  if (reset) {
    page.value = 1
    finished.value = false
    error.value = ''
  }
  if (finished.value) return

  loading.value = true
  try {
    const data = await postApi.listPosts({
      categoryId: barId.value,
      auditStatus: 1,
      page: page.value,
      pageSize: PAGE_SIZE
    })
    const list = data || []
    posts.value = reset ? list : posts.value.concat(list)
    if (list.length < PAGE_SIZE) finished.value = true
    else page.value += 1
  } catch (err) {
    error.value = err.message || '加载失败'
    finished.value = true
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  await taxonomyStore.load()
  load(true)
  loadMembership()
})

watch(barId, async () => {
  await taxonomyStore.load()
  posts.value = []
  membership.value = null
  load(true)
  loadMembership()
})

// 登录态变化（登录 / 退出）要重取身份，否则退出后还显示着「已关注」
watch(
  () => userStore.isLoggedIn,
  () => loadMembership()
)
</script>

<template>
  <div class="bn-container">
    <div class="head">
      <div class="head-main">
        <img v-if="barIcon" :src="barIcon" :alt="barName" class="bar-mark" />
        <div>
          <h1 class="bn-page-title">{{ barName }}</h1>
          <p class="bn-page-sub">这个吧里的帖子与讨论</p>
        </div>
      </div>
      <div class="head-actions">
        <el-button v-if="canManage" @click="openManage">
          <el-icon style="margin-right: 4px"><Setting /></el-icon>吧务
        </el-button>
        <el-button :loading="followPending" @click="toggleFollow">
          <el-icon style="margin-right: 4px">
            <component :is="followed ? 'Select' : 'Plus'" />
          </el-icon>
          {{ followed ? '已关注' : '关注本吧' }}
        </el-button>
        <el-button type="primary" @click="goPublish">
          <el-icon style="margin-right: 4px"><EditPen /></el-icon>在本吧发帖
        </el-button>
      </div>
    </div>

    <!-- 我的吧内等级：只在关注了这个吧后才有意义 -->
    <div v-if="levelText" class="my-level">
      <BnAvatar :src="membership.avatar" :name="membership.username" :size="30" :linkable="false" />
      <span class="level-badge">{{ levelText }}</span>
      <span class="bn-text-muted level-exp">
        经验 {{ membership.exp || 0 }}
        <template v-if="membership.nextLevelExp != null">
          · 再 {{ membership.nextLevelExp }} 点升级
        </template>
        <template v-else>· 已满级</template>
      </span>
      <el-tag v-if="isOwner" size="small" type="warning" effect="plain">吧主</el-tag>
      <el-tag v-else-if="membership.role === 'MODERATOR'" size="small" effect="plain">管理员</el-tag>
    </div>

    <BnState
      :loading="loading && !posts.length"
      :error="error"
      :empty="!posts.length"
      :empty-text="`「${barName}」吧里还没有帖子，来发第一帖`"
    >
      <PostCard v-for="post in posts" :key="post.id" :post="post" class="bn-mt-12" />
    </BnState>

    <div v-if="posts.length && !finished" class="load-more">
      <el-button text :loading="loading" @click="load(false)">加载更多</el-button>
    </div>

    <!-- ---------- 吧务面板：管理员（仅吧主可改）+ 等级称号（仅吧主） ---------- -->
    <el-dialog v-model="manageVisible" title="吧务管理" width="560px">
      <el-tabs v-model="manageTab">
        <el-tab-pane label="管理员" name="moderators">
          <template v-if="isOwner">
            <div class="pick-row">
              <el-input
                v-model="userKeyword"
                placeholder="搜索昵称或账号，找到后点「任命」"
                clearable
                @keyup.enter="searchUsers"
                @clear="userOptions = []"
              />
              <el-button :loading="userSearching" @click="searchUsers">搜索</el-button>
            </div>

            <div v-if="userOptions.length" class="pick-list">
              <div v-for="user in userOptions" :key="user.id" class="pick-item">
                <BnAvatar :src="user.avatar" :name="user.username" :size="28" :linkable="false" />
                <span class="pick-name bn-ellipsis-1">{{ user.username || '书友' }}</span>
                <el-button link size="small" type="primary" @click="appoint(user.id)">任命</el-button>
              </div>
            </div>
            <p v-else-if="userKeyword.trim()" class="bn-text-muted pick-empty">
              没有匹配的用户
            </p>
          </template>
          <p v-else class="bn-text-muted pick-empty">只有吧主可以任命管理员，你可以管理帖子</p>

          <el-divider />

          <div v-loading="moderatorsLoading" class="mod-list">
            <p v-if="!moderators.length" class="bn-text-muted pick-empty">还没有管理员</p>
            <div v-for="row in moderators" :key="row.userId" class="pick-item">
              <BnAvatar :src="row.avatar" :name="row.username" :size="28" :linkable="false" />
              <span class="pick-name bn-ellipsis-1">{{ row.username || '书友' }}</span>
              <el-button
                v-if="isOwner"
                link
                size="small"
                type="danger"
                @click="revoke(row)"
              >
                撤销
              </el-button>
            </div>
          </div>
        </el-tab-pane>

        <el-tab-pane label="等级称号" name="titles" :disabled="!isOwner">
          <p class="bn-text-muted pick-empty">
            给 1 ~ 10 级起个称号，留空的等级只显示 Lv.N
          </p>
          <div class="title-grid">
            <div v-for="row in titleRows" :key="row.level" class="title-row">
              <span class="title-level">Lv.{{ row.level }}</span>
              <el-input v-model="row.title" maxlength="20" placeholder="未设置" size="small" />
            </div>
          </div>
          <div class="title-actions">
            <el-button type="primary" :loading="titlesSaving" size="small" @click="saveTitles">
              保存称号
            </el-button>
          </div>
        </el-tab-pane>
      </el-tabs>
    </el-dialog>
  </div>
</template>

<style scoped>
/* 整行垂直居中：图标与「吧名 + 副标题」这一整块居中对齐，
   不再用 flex-end + margin-bottom 去凑基线（那样图标会掉到文字底下） */
.head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
}

.head-main {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
}

.bar-mark {
  width: 46px;
  height: 46px;
  border-radius: 12px;
  object-fit: cover;
  background: var(--bn-primary-soft);
  flex: none;
  display: block;
}

.head-main .bn-page-title,
.head-main .bn-page-sub {
  margin-bottom: 0;
}

.head-actions {
  display: flex;
  align-items: center;
  gap: 9px;
  flex-wrap: wrap;
}

/* ---------- 我的吧内等级 ---------- */
.my-level {
  display: flex;
  align-items: center;
  gap: 9px;
  flex-wrap: wrap;
  margin-top: 12px;
  padding: 9px 14px;
  border-radius: var(--bn-radius-sm);
  background: var(--bn-primary-soft);
}

.level-badge {
  font-size: 13px;
  font-weight: 600;
  color: var(--bn-primary);
}

.level-exp {
  font-size: 12px;
}

/* ---------- 吧务弹窗 ---------- */
.pick-row {
  display: flex;
  gap: 9px;
  margin-bottom: 10px;
}

.pick-list,
.mod-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
  max-height: 190px;
  overflow-y: auto;
}

.pick-item {
  display: flex;
  align-items: center;
  gap: 9px;
  padding: 5px 8px;
  border-radius: var(--bn-radius-sm);
  background: #faf8f6;
}

.pick-name {
  flex: 1;
  min-width: 0;
  font-size: 13px;
}

.pick-empty {
  font-size: 12.5px;
  margin-bottom: 10px;
}

.title-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 9px;
}

.title-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.title-level {
  flex: none;
  width: 40px;
  font-size: 12.5px;
  font-weight: 600;
  color: var(--bn-primary);
}

.title-actions {
  display: flex;
  justify-content: flex-end;
  margin-top: 14px;
}

.load-more {
  text-align: center;
  padding: 22px 0;
}
</style>
