<script setup>
/**
 * 书单创建 / 编辑
 * 新建时可直接挑选初始书籍；编辑时书籍增删在详情页完成
 */
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import * as booklistApi from '@/api/booklist'
import * as bookApi from '@/api/book'
import BnCover from '@/components/BnCover.vue'

const route = useRoute()
const router = useRouter()

const formRef = ref(null)
const submitting = ref(false)
const pageLoading = ref(false)

const editId = computed(() => route.params.id || '')
const isEdit = computed(() => Boolean(editId.value))

const form = reactive({
  title: '',
  summary: '',
  coverImage: '',
  visibility: 1
})

/** 新建时的初始书籍列表 */
const pickedBooks = ref([])
const bookOptions = ref([])
const bookSearching = ref(false)

const rules = {
  title: [
    { required: true, message: '请输入书单标题', trigger: 'blur' },
    { max: 60, message: '标题不超过 60 字', trigger: 'blur' }
  ]
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

function addPickedBook(bookId) {
  if (!bookId) return
  const book = bookOptions.value.find((item) => String(item.id) === String(bookId))
  if (!book) return
  if (pickedBooks.value.some((item) => String(item.bookId) === String(book.id))) {
    ElMessage.info('这本书已在列表中')
    return
  }
  pickedBooks.value.push({ bookId: String(book.id), book, note: '' })
}

function removePickedBook(index) {
  pickedBooks.value.splice(index, 1)
}

async function loadForEdit() {
  pageLoading.value = true
  try {
    const detail = await booklistApi.getBooklistDetail(editId.value)
    form.title = detail.title || ''
    form.summary = detail.summary || ''
    form.coverImage = detail.coverImage || ''
    form.visibility = detail.visibility ?? 1

    // 编辑模式下把已有条目带入展示，仅作参考，实际增删在详情页
    pickedBooks.value = (detail.items || []).map((item) => ({
      bookId: item.bookId,
      book: item.book,
      note: item.note || '',
      existingId: item.id
    }))
  } catch (err) {
    ElMessage.error(err.message || '书单加载失败')
  } finally {
    pageLoading.value = false
  }
}

async function onSubmit() {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  try {
    if (isEdit.value) {
      await booklistApi.updateBooklist(editId.value, {
        title: form.title.trim(),
        summary: form.summary.trim() || undefined,
        coverImage: form.coverImage || undefined,
        visibility: form.visibility
      })
      ElMessage.success('书单已更新')
      router.push(`/booklist/${editId.value}`)
      return
    }

    const payload = {
      title: form.title.trim(),
      summary: form.summary.trim() || undefined,
      coverImage: form.coverImage || undefined,
      visibility: form.visibility,
      items: pickedBooks.value.length
        ? pickedBooks.value.map((item) => ({
            bookId: item.bookId,
            note: item.note.trim() || undefined
          }))
        : undefined
    }
    const created = await booklistApi.createBooklist(payload)
    ElMessage.success('书单创建成功')
    router.push(created?.id ? `/booklist/${created.id}` : '/booklist')
  } catch {
    // 拦截器已提示
  } finally {
    submitting.value = false
  }
}

onMounted(() => {
  if (isEdit.value) loadForEdit()
})
</script>

<template>
  <div class="bn-container edit-page">
    <div class="page-head">
      <h1 class="bn-page-title">{{ isEdit ? '编辑书单' : '创建书单' }}</h1>
      <p class="bn-page-sub">
        {{ isEdit ? '修改书单的基础信息，书籍增删请在书单详情页操作' : '把想推荐的书整理成一份书单' }}
      </p>
    </div>

    <el-skeleton v-if="pageLoading" :rows="7" animated />

    <el-form v-else ref="formRef" :model="form" :rules="rules" label-position="top" class="bn-card">
      <el-form-item label="书单标题" prop="title">
        <el-input
          v-model="form.title"
          placeholder="例如：值得反复重读的五本小说"
          maxlength="60"
          show-word-limit
          size="large"
        />
      </el-form-item>

      <el-form-item label="书单简介（可选）">
        <el-input
          v-model="form.summary"
          type="textarea"
          :rows="3"
          placeholder="介绍一下这份书单的主题和选书标准"
          maxlength="200"
          show-word-limit
        />
      </el-form-item>

      <el-form-item label="封面图（可选）">
        <div class="cover-row">
          <BnCover
            v-if="form.coverImage"
            :src="form.coverImage"
            title="书单封面"
            width="90px"
            height="120px"
          />
          <el-input
            v-model="form.coverImage"
            placeholder="粘贴图片 URL，留空则使用首本书封面"
            clearable
          />
        </div>
      </el-form-item>

      <el-form-item label="可见性">
        <el-radio-group v-model="form.visibility">
          <el-radio :value="1">
            <span class="radio-label">公开</span>
            <span class="radio-desc">所有人都能看到</span>
          </el-radio>
          <el-radio :value="0">
            <span class="radio-label">私密</span>
            <span class="radio-desc">仅自己可见</span>
          </el-radio>
        </el-radio-group>
      </el-form-item>

      <!-- 初始书籍（仅新建时） -->
      <el-form-item v-if="!isEdit" label="书单内容（可选）">
        <div class="pick-wrap">
          <el-select
            filterable
            remote
            reserve-keyword
            clearable
            placeholder="输入书名搜索并添加"
            :remote-method="searchBooks"
            :loading="bookSearching"
            style="width: 100%"
            @change="addPickedBook"
          >
            <el-option
              v-for="book in bookOptions"
              :key="book.id"
              :label="`${book.title}${book.author ? ' · ' + book.author : ''}`"
              :value="String(book.id)"
            />
          </el-select>

          <div v-if="pickedBooks.length" class="picked-list">
            <div v-for="(item, index) in pickedBooks" :key="item.bookId" class="picked-item">
              <BnCover
                :src="item.book?.coverUrl"
                :title="item.book?.title || '书籍'"
                width="40px"
                height="56px"
                radius="4px"
              />
              <div class="picked-body">
                <p class="picked-title bn-ellipsis-1">{{ item.book?.title || '未知书籍' }}</p>
                <el-input
                  v-model="item.note"
                  size="small"
                  placeholder="推荐语（可选）"
                  maxlength="120"
                />
              </div>
              <el-button link type="danger" @click="removePickedBook(index)">移除</el-button>
            </div>
          </div>
          <p v-else class="bn-text-muted pick-tip">
            可以先留空，创建后再往书单里添加书籍
          </p>
        </div>
      </el-form-item>

      <div v-else class="loaded-items">
        <p class="loaded-label">当前书单包含 {{ pickedBooks.length }} 本书</p>
        <div class="loaded-tags">
          <span v-for="item in pickedBooks" :key="item.bookId" class="bn-tag">
            {{ item.book?.title || '书籍' }}
          </span>
        </div>
      </div>

      <div class="submit-row">
        <el-button @click="router.back()">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="onSubmit">
          {{ isEdit ? '保存修改' : '创建书单' }}
        </el-button>
      </div>
    </el-form>
  </div>
</template>

<style scoped>
.edit-page {
  max-width: 840px;
}

.page-head {
  margin-bottom: 16px;
}

.cover-row {
  display: flex;
  gap: 14px;
  align-items: flex-start;
  width: 100%;
}

.radio-label {
  font-weight: 600;
  margin-right: 6px;
}

.radio-desc {
  font-size: 12px;
  color: var(--bn-text-muted);
}

.pick-wrap {
  width: 100%;
}

.pick-tip {
  font-size: 12.5px;
  margin-top: 8px;
}

.picked-list {
  margin-top: 12px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.picked-item {
  display: flex;
  gap: 11px;
  align-items: center;
  padding: 9px 11px;
  border: 1px solid var(--bn-border);
  border-radius: var(--bn-radius-sm);
  background: #fbf9f7;
}

.picked-body {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 5px;
}

.picked-title {
  font-size: 13.5px;
  font-weight: 600;
}

.loaded-items {
  padding: 12px 14px;
  background: #faf8f5;
  border: 1px solid var(--bn-border);
  border-radius: var(--bn-radius-sm);
  margin-bottom: 18px;
}

.loaded-label {
  font-size: 13px;
  color: var(--bn-text-sub);
  margin-bottom: 8px;
}

.loaded-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 7px;
}

.submit-row {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  border-top: 1px solid var(--bn-border);
  padding-top: 16px;
}
</style>
