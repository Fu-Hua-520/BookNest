<script setup>
/**
 * 书单详情：条目列表 + 条目增删 + 作者可见的编辑入口
 */
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as booklistApi from '@/api/booklist'
import * as bookApi from '@/api/book'
import { fromNow } from '@/utils/format'
import { useUserStore } from '@/stores/user'
import BnAvatar from '@/components/BnAvatar.vue'
import BnCover from '@/components/BnCover.vue'
import BnState from '@/components/BnState.vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const booklist = ref(null)
const loading = ref(true)
const error = ref('')

const addVisible = ref(false)
const addSubmitting = ref(false)
const bookOptions = ref([])
const bookSearching = ref(false)
const addForm = ref({ bookId: '', note: '' })

const listId = computed(() => String(route.params.id || ''))
const isOwner = computed(
  () => Boolean(userStore.userId) && String(booklist.value?.userId) === String(userStore.userId)
)
const items = computed(() => booklist.value?.items || [])

async function load() {
  loading.value = true
  error.value = ''
  try {
    booklist.value = await booklistApi.getBooklistDetail(listId.value)
  } catch (err) {
    error.value = err.message || '书单加载失败'
  } finally {
    loading.value = false
  }
}

async function searchBooks(keyword) {
  if (!keyword) {
    bookOptions.value = []
    return
  }
  bookSearching.value = true
  try {
    bookOptions.value = await bookApi.searchBooks(keyword)
  } catch {
    bookOptions.value = []
  } finally {
    bookSearching.value = false
  }
}

function openAdd() {
  addForm.value = { bookId: '', note: '' }
  bookOptions.value = []
  addVisible.value = true
}

async function submitAdd() {
  if (!addForm.value.bookId) {
    ElMessage.warning('请先选择一本书')
    return
  }
  addSubmitting.value = true
  try {
    await booklistApi.addBooklistItem(listId.value, {
      bookId: addForm.value.bookId,
      note: addForm.value.note.trim() || undefined
    })
    ElMessage.success('已添加到书单')
    addVisible.value = false
    await load()
  } finally {
    addSubmitting.value = false
  }
}

async function removeItem(item) {
  const name = item.book?.title || '这本书'
  try {
    await ElMessageBox.confirm(`确定从书单中移除《${name}》吗？`, '移除确认', {
      type: 'warning',
      confirmButtonText: '移除',
      cancelButtonText: '取消'
    })
  } catch {
    return
  }
  await booklistApi.removeBooklistItem(listId.value, item.id)
  ElMessage.success('已移除')
  await load()
}

async function removeBooklist() {
  try {
    await ElMessageBox.confirm('删除书单后无法恢复，确定删除吗？', '删除确认', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消'
    })
  } catch {
    return
  }
  await booklistApi.deleteBooklist(listId.value)
  ElMessage.success('书单已删除')
  router.replace({ name: 'booklist-list' })
}

onMounted(load)
watch(listId, load)
</script>

<template>
  <div class="bn-container">
    <el-breadcrumb separator="/" class="crumb">
      <el-breadcrumb-item :to="{ path: '/' }">首页</el-breadcrumb-item>
      <el-breadcrumb-item :to="{ path: '/booklist' }">书单广场</el-breadcrumb-item>
      <el-breadcrumb-item>书单详情</el-breadcrumb-item>
    </el-breadcrumb>

    <BnState :loading="loading" :error="error" :skeleton-rows="6">
      <template v-if="booklist">
        <section class="bn-card hero">
          <BnCover
            :src="booklist.coverImage"
            :title="booklist.title"
            width="132px"
            height="180px"
          />

          <div class="hero-body">
            <div class="hero-top">
              <h1 class="list-title">{{ booklist.title }}</h1>
              <el-tag v-if="booklist.visibility === 0" size="small" type="info" effect="plain">
                私密书单
              </el-tag>
            </div>

            <p class="list-summary">{{ booklist.summary || '这份书单还没有简介' }}</p>

            <div class="owner-row">
              <BnAvatar
                :src="booklist.userAvatar"
                :name="booklist.userName"
                :size="30"
                :user-id="booklist.userId"
              />
              <router-link :to="`/user/${booklist.userId}`" class="owner-name">
                {{ booklist.userName || '书友' }}
              </router-link>
              <span class="bn-text-muted">创建于 {{ fromNow(booklist.createTime) }}</span>
            </div>

            <div class="stats">
              <div class="stat">
                <b>{{ booklist.bookCount || 0 }}</b>
                <span>本书</span>
              </div>
              <div class="stat">
                <b>{{ booklist.likeCount || 0 }}</b>
                <span>点赞</span>
              </div>
              <div class="stat">
                <b>{{ booklist.collectCount || 0 }}</b>
                <span>收藏</span>
              </div>
            </div>

            <div v-if="isOwner" class="hero-actions">
              <el-button type="primary" @click="openAdd">
                <el-icon style="margin-right: 4px"><Plus /></el-icon>添加书籍
              </el-button>
              <el-button @click="router.push(`/booklist/${booklist.id}/edit`)">
                <el-icon style="margin-right: 4px"><EditPen /></el-icon>编辑书单
              </el-button>
              <el-button type="danger" plain @click="removeBooklist">
                <el-icon style="margin-right: 4px"><Delete /></el-icon>删除
              </el-button>
            </div>
          </div>
        </section>

        <div class="bn-section-title">
          <h2>书单内容{{ items.length ? ` (${items.length})` : '' }}</h2>
        </div>

        <div v-if="items.length" class="items">
          <div v-for="(item, index) in items" :key="item.id" class="bn-card item">
            <span class="item-index">{{ index + 1 }}</span>

            <router-link v-if="item.bookId" :to="`/book/${item.bookId}`" class="item-cover">
              <BnCover
                :src="item.book?.coverUrl"
                :title="item.book?.title || '书籍'"
                width="64px"
                height="92px"
                radius="5px"
              />
            </router-link>
            <BnCover
              v-else
              :src="item.book?.coverUrl"
              title="书籍"
              width="64px"
              height="92px"
              radius="5px"
            />

            <div class="item-body">
              <router-link
                v-if="item.bookId"
                :to="`/book/${item.bookId}`"
                class="item-title"
              >
                {{ item.book?.title || '未知书籍' }}
              </router-link>
              <span v-else class="item-title">{{ item.book?.title || '未知书籍' }}</span>

              <p class="item-author">{{ item.book?.author || '佚名' }}</p>

              <p v-if="item.book?.description" class="item-desc bn-ellipsis-2">
                {{ item.book.description }}
              </p>

              <p v-if="item.note" class="item-note">
                <el-icon><ChatLineSquare /></el-icon>
                {{ item.note }}
              </p>
            </div>

            <div class="item-ops">
              <el-rate
                v-if="Number(item.book?.rating) > 0"
                :model-value="Number(item.book.rating) / 2"
                disabled
                allow-half
                size="small"
                :show-score="false"
              />
              <el-button v-if="isOwner" link size="small" type="danger" @click="removeItem(item)">
                移除
              </el-button>
            </div>
          </div>
        </div>

        <div v-else class="bn-empty">
          <el-icon :size="34" color="#c8bdb1"><Collection /></el-icon>
          <p class="bn-mt-12">这份书单还是空的</p>
          <el-button v-if="isOwner" type="primary" class="bn-mt-12" @click="openAdd">
            添加第一本书
          </el-button>
        </div>
      </template>
    </BnState>

    <!-- 添加书籍弹窗 -->
    <el-dialog v-model="addVisible" title="添加书籍到书单" width="460px">
      <el-form label-position="top">
        <el-form-item label="选择书籍" required>
          <el-select
            v-model="addForm.bookId"
            filterable
            remote
            reserve-keyword
            placeholder="输入书名搜索"
            :remote-method="searchBooks"
            :loading="bookSearching"
            style="width: 100%"
          >
            <el-option
              v-for="book in bookOptions"
              :key="book.id"
              :label="`${book.title}${book.author ? ' · ' + book.author : ''}`"
              :value="String(book.id)"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="推荐语（可选）">
          <el-input
            v-model="addForm.note"
            type="textarea"
            :rows="2"
            placeholder="为什么推荐这本书？"
            maxlength="120"
            show-word-limit
          />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="addVisible = false">取消</el-button>
        <el-button type="primary" :loading="addSubmitting" @click="submitAdd">添加</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.crumb {
  margin-bottom: 14px;
}

.hero {
  display: flex;
  gap: 22px;
  padding: 22px 24px;
  align-items: flex-start;
}

.hero-body {
  flex: 1;
  min-width: 0;
}

.hero-top {
  display: flex;
  align-items: center;
  gap: 10px;
}

.list-title {
  font-size: 22px;
}

.list-summary {
  font-size: 13.5px;
  color: var(--bn-text-sub);
  line-height: 1.7;
  margin: 8px 0 12px;
}

.owner-row {
  display: flex;
  align-items: center;
  gap: 9px;
  font-size: 13px;
}

.owner-name {
  font-weight: 600;
}

.owner-name:hover {
  color: var(--bn-primary);
}

.stats {
  display: flex;
  gap: 30px;
  margin: 16px 0;
}

.stat {
  display: flex;
  flex-direction: column;
}

.stat b {
  font-size: 19px;
  color: var(--bn-primary);
}

.stat span {
  font-size: 12px;
  color: var(--bn-text-muted);
}

.hero-actions {
  display: flex;
  gap: 9px;
  flex-wrap: wrap;
}

.items {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.item {
  display: flex;
  gap: 14px;
  padding: 14px 16px;
  align-items: flex-start;
}

.item-index {
  width: 26px;
  height: 26px;
  border-radius: 8px;
  background: var(--bn-primary-soft);
  color: var(--bn-primary);
  font-size: 13px;
  font-weight: 700;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  margin-top: 4px;
}

.item-cover {
  flex-shrink: 0;
}

.item-body {
  flex: 1;
  min-width: 0;
}

.item-title {
  font-size: 15px;
  font-weight: 600;
}

a.item-title:hover {
  color: var(--bn-primary);
}

.item-author {
  font-size: 12.5px;
  color: var(--bn-text-sub);
  margin-top: 2px;
}

.item-desc {
  font-size: 12.5px;
  color: var(--bn-text-muted);
  line-height: 1.6;
  margin-top: 7px;
}

.item-note {
  display: flex;
  align-items: flex-start;
  gap: 5px;
  font-size: 12.5px;
  color: #8a7357;
  background: #faf5ee;
  border-radius: 5px;
  padding: 6px 9px;
  margin-top: 8px;
  line-height: 1.6;
}

.item-ops {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 6px;
  flex-shrink: 0;
}

@media (max-width: 700px) {
  .hero {
    flex-direction: column;
    align-items: center;
    text-align: center;
  }
  .owner-row,
  .stats,
  .hero-actions {
    justify-content: center;
  }
}
</style>
