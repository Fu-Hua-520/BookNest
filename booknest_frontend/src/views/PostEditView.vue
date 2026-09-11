<script setup>
/**
 * 帖子编辑：新建与编辑复用同一页面
 * 对齐后端 PostPublishDTO / PostUpdateDTO，正文以 Markdown 提交（后端转存 OSS）
 */
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import * as postApi from '@/api/post'
import * as bookApi from '@/api/book'
import * as taxonomyApi from '@/api/taxonomy'
import { useTaxonomyStore } from '@/stores/taxonomy'
import { useUserStore } from '@/stores/user'
import BnCover from '@/components/BnCover.vue'
import BnMarkdownEditor from '@/components/BnMarkdownEditor.vue'

const route = useRoute()
const router = useRouter()
const taxonomyStore = useTaxonomyStore()
const userStore = useUserStore()

const formRef = ref(null)
const submitting = ref(false)
const pageLoading = ref(false)

const editId = computed(() => route.params.id || '')
const isEdit = computed(() => Boolean(editId.value))

const form = reactive({
  title: '',
  summary: '',
  content: '',
  bookId: '',
  categoryId: '',
  coverImage: '',
  tagIds: []
})

/** 关联书籍：搜索选择 */
const bookOptions = ref([])
const bookSearching = ref(false)
const selectedBook = ref(null)

/** 标签：全部标签 + 已选 */
const allTags = ref([])

const rules = {
  title: [
    { required: true, message: '请输入标题', trigger: 'blur' },
    { max: 100, message: '标题不超过 100 字', trigger: 'blur' }
  ],
  categoryId: [{ required: true, message: '请选择分类', trigger: 'change' }],
  content: [{ required: true, message: '请输入正文内容', trigger: 'blur' }]
}

/** 远程搜索书籍，用于关联 */
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

function onBookSelected(bookId) {
  const book = bookOptions.value.find((item) => String(item.id) === String(bookId))
  selectedBook.value = book || null
  // 关联书籍后若未填封面，默认采用书籍封面
  if (book?.coverUrl && !form.coverImage) {
    form.coverImage = book.coverUrl
  }
}

function clearBook() {
  form.bookId = ''
  selectedBook.value = null
}

async function loadForEdit() {
  pageLoading.value = true
  try {
    const detail = await postApi.getPostDetail(editId.value)
    if (String(detail.authorId) !== String(userStore.userId)) {
      ElMessage.error('只能编辑自己的书评')
      router.replace(`/post/${editId.value}`)
      return
    }
    form.title = detail.title || ''
    form.summary = detail.summary || ''
    form.content = detail.content || ''
    form.bookId = detail.bookId || ''
    form.categoryId = detail.categoryId || ''
    form.coverImage = detail.coverImage || ''
    form.tagIds = (detail.tags || []).map((tag) => String(tag.id))

    if (detail.bookId) {
      try {
        selectedBook.value = await bookApi.getBookDetail(detail.bookId)
        bookOptions.value = [selectedBook.value]
      } catch {
        selectedBook.value = null
      }
    }
  } catch (err) {
    ElMessage.error(err.message || '加载失败')
  } finally {
    pageLoading.value = false
  }
}

async function loadTags() {
  try {
    allTags.value = (await taxonomyApi.listTags()) || []
  } catch {
    allTags.value = []
  }
}

async function onSubmit() {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  const payload = {
    title: form.title.trim(),
    summary: form.summary.trim() || undefined,
    content: form.content,
    bookId: form.bookId || undefined,
    categoryId: form.categoryId,
    coverImage: form.coverImage || undefined,
    tagIds: form.tagIds.length ? form.tagIds : undefined
  }

  try {
    if (isEdit.value) {
      await postApi.updatePost(editId.value, payload)
      ElMessage.success('更新成功')
      router.push(`/post/${editId.value}`)
    } else {
      const created = await postApi.publishPost(payload)
      ElMessage.success('发布成功，等待审核通过后展示')
      router.push(created?.id ? `/post/${created.id}` : '/')
    }
  } catch {
    // 拦截器已提示
  } finally {
    submitting.value = false
  }
}

async function onCoverUpload(options) {
  const { file, onSuccess, onError } = options
  try {
    const { uploadFile } = await import('@/api/user')
    const url = await uploadFile(file)
    form.coverImage = url
    onSuccess?.(url)
    ElMessage.success('封面上传成功')
  } catch (err) {
    onError?.(err)
  }
}

onMounted(async () => {
  await taxonomyStore.load()
  loadTags()
  if (isEdit.value) loadForEdit()
})
</script>

<template>
  <div class="bn-container edit-page">
    <div class="page-head">
      <h1 class="bn-page-title">{{ isEdit ? '编辑书评' : '写书评' }}</h1>
      <p class="bn-page-sub">支持 Markdown 语法，发布后需通过审核才会公开展示</p>
    </div>

    <el-skeleton v-if="pageLoading" :rows="10" animated />

    <el-form
      v-else
      ref="formRef"
      :model="form"
      :rules="rules"
      label-position="top"
      class="bn-card"
    >
      <!-- 标题 -->
      <el-form-item label="标题" prop="title">
        <el-input
          v-model="form.title"
          placeholder="给这篇书评起个标题，例如：读完《活着》后的一些想法"
          maxlength="100"
          show-word-limit
          size="large"
        />
      </el-form-item>

      <el-row :gutter="16">
        <!-- 分类 -->
        <el-col :xs="24" :sm="12">
          <el-form-item label="分类" prop="categoryId">
            <el-select v-model="form.categoryId" placeholder="请选择分类" style="width: 100%">
              <el-option-group
                v-for="parent in taxonomyStore.categoryTree"
                :key="parent.id"
                :label="parent.name"
              >
                <el-option :label="`${parent.name}（一级）`" :value="String(parent.id)" />
                <el-option
                  v-for="child in parent.children || []"
                  :key="child.id"
                  :label="child.name"
                  :value="String(child.id)"
                />
              </el-option-group>
            </el-select>
          </el-form-item>
        </el-col>

        <!-- 关联书籍 -->
        <el-col :xs="24" :sm="12">
          <el-form-item label="关联书籍（可选）">
            <el-select
              v-model="form.bookId"
              filterable
              remote
              clearable
              reserve-keyword
              placeholder="输入书名搜索"
              :remote-method="searchBooks"
              :loading="bookSearching"
              style="width: 100%"
              @change="onBookSelected"
              @clear="clearBook"
            >
              <el-option
                v-for="book in bookOptions"
                :key="book.id"
                :label="`${book.title}${book.author ? ' · ' + book.author : ''}`"
                :value="String(book.id)"
              />
            </el-select>
          </el-form-item>
        </el-col>
      </el-row>

      <!-- 已选书籍展示 -->
      <div v-if="selectedBook" class="selected-book">
        <BnCover
          :src="selectedBook.coverUrl"
          :title="selectedBook.title"
          width="38px"
          height="52px"
          radius="4px"
        />
        <div class="selected-book-info">
          <p class="selected-book-title">{{ selectedBook.title }}</p>
          <p class="bn-text-muted selected-book-author">{{ selectedBook.author || '佚名' }}</p>
        </div>
        <el-button link size="small" @click="clearBook">移除</el-button>
      </div>

      <!-- 摘要 -->
      <el-form-item label="摘要（可选）">
        <el-input
          v-model="form.summary"
          type="textarea"
          :rows="2"
          placeholder="一句话概括这篇书评，留空则自动截取正文开头"
          maxlength="200"
          show-word-limit
        />
      </el-form-item>

      <!-- 封面 -->
      <el-form-item label="封面图（可选）">
        <div class="cover-row">
          <BnCover
            v-if="form.coverImage"
            :src="form.coverImage"
            title="封面"
            width="90px"
            height="120px"
          />
          <div class="cover-actions">
            <el-upload
              :show-file-list="false"
              :http-request="onCoverUpload"
              accept="image/*"
              :disabled="!userStore.isLoggedIn"
            >
              <el-button>
                <el-icon style="margin-right: 4px"><Upload /></el-icon>
                {{ form.coverImage ? '更换封面' : '上传封面' }}
              </el-button>
            </el-upload>
            <el-input
              v-model="form.coverImage"
              placeholder="或直接粘贴图片 URL"
              size="small"
              class="cover-url"
              clearable
            />
            <p class="bn-text-muted cover-tip">
              封面会上传至对象存储；未上传时将展示书籍封面
            </p>
          </div>
        </div>
      </el-form-item>

      <!-- 标签 -->
      <el-form-item label="标签（可选，最多 5 个）">
        <el-select
          v-model="form.tagIds"
          multiple
          filterable
          clearable
          collapse-tags
          collapse-tags-tooltip
          :multiple-limit="5"
          placeholder="选择标签，便于书友发现"
          style="width: 100%"
        >
          <el-option
            v-for="tag in allTags"
            :key="tag.id"
            :label="`#${tag.name}`"
            :value="String(tag.id)"
          />
        </el-select>
      </el-form-item>

      <!-- 正文 -->
      <el-form-item label="正文" prop="content">
        <BnMarkdownEditor v-model="form.content" :min-rows="16" />
      </el-form-item>

      <div class="submit-row">
        <el-button @click="router.back()">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="onSubmit">
          {{ isEdit ? '保存修改' : '发布书评' }}
        </el-button>
      </div>
    </el-form>
  </div>
</template>

<style scoped>
.edit-page {
  max-width: 1000px;
}

.page-head {
  margin-bottom: 16px;
}

.bn-form {
  padding: 22px 24px 18px;
}

.selected-book {
  display: flex;
  align-items: center;
  gap: 11px;
  padding: 9px 12px;
  background: #faf7f3;
  border: 1px solid var(--bn-border);
  border-radius: var(--bn-radius-sm);
  margin-bottom: 18px;
}

.selected-book-info {
  flex: 1;
  min-width: 0;
}

.selected-book-title {
  font-size: 13.5px;
  font-weight: 600;
}

.selected-book-author {
  font-size: 12px;
}

.cover-row {
  display: flex;
  gap: 15px;
  align-items: flex-start;
  width: 100%;
}

.cover-actions {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.cover-url {
  max-width: 420px;
}

.cover-tip {
  font-size: 12px;
}

.submit-row {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  padding-top: 6px;
  border-top: 1px solid var(--bn-border);
  margin-top: 6px;
  padding-top: 16px;
}
</style>
