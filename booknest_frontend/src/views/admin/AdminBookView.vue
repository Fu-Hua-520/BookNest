<script setup>
/**
 * 书籍管理：分页查询 + 新增 / 编辑 / 删除 + ISBN 自动补全
 */
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as adminApi from '@/api/admin'
import * as bookApi from '@/api/book'
import { formatDateTime } from '@/utils/format'

const loading = ref(false)
const tableData = ref([])
const total = ref(0)
const pageInfo = reactive({ page: 1, pageSize: 10 })
const keyword = ref('')

const dialogVisible = ref(false)
const dialogMode = ref('create')
const submitting = ref(false)
const isbnLoading = ref(false)
const formRef = ref(null)

const emptyForm = () => ({
  id: '',
  title: '',
  author: '',
  isbn: '',
  coverUrl: '',
  description: '',
  publisher: '',
  publishDate: '',
  rating: null,
  ratingCount: null,
  source: ''
})

const form = reactive(emptyForm())

const rules = {
  title: [{ required: true, message: '请输入书名', trigger: 'blur' }]
}

/** 按 ISBN 从外部源补全书籍信息 */
async function fetchByIsbn() {
  const isbn = form.isbn?.trim()
  if (!isbn) {
    ElMessage.warning('请先填写 ISBN')
    return
  }
  isbnLoading.value = true
  try {
    const book = await bookApi.fetchBookByIsbn(isbn)
    if (!book) {
      ElMessage.info('未查到该 ISBN 的书籍信息')
      return
    }
    // 仅回填空字段，避免覆盖已填内容
    const fields = ['title', 'author', 'coverUrl', 'description', 'publisher', 'publishDate', 'source']
    for (const field of fields) {
      if (book[field] && !form[field]) form[field] = book[field]
    }
    if (book.rating && !form.rating) form.rating = book.rating
    ElMessage.success('已根据 ISBN 补全信息')
  } catch {
    /* 拦截器已提示 */
  } finally {
    isbnLoading.value = false
  }
}

async function load() {
  loading.value = true
  try {
    const data = await adminApi.listBooks({
      keyword: keyword.value.trim() || undefined,
      page: pageInfo.page,
      pageSize: pageInfo.pageSize
    })
    tableData.value = data?.list || []
    total.value = Number(data?.total || 0)
  } catch {
    tableData.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function onSearch() {
  pageInfo.page = 1
  load()
}

function openCreate() {
  dialogMode.value = 'create'
  Object.assign(form, emptyForm())
  dialogVisible.value = true
}

function openEdit(row) {
  dialogMode.value = 'edit'
  Object.assign(form, emptyForm(), {
    id: row.id,
    title: row.title || '',
    author: row.author || '',
    isbn: row.isbn || '',
    coverUrl: row.coverUrl || '',
    description: row.description || '',
    publisher: row.publisher || '',
    publishDate: row.publishDate || '',
    rating: row.rating ?? null,
    ratingCount: row.ratingCount ?? null,
    source: row.source || ''
  })
  dialogVisible.value = true
}

async function onSubmit() {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  const payload = {
    title: form.title.trim(),
    author: form.author.trim() || undefined,
    isbn: form.isbn.trim() || undefined,
    coverUrl: form.coverUrl.trim() || undefined,
    description: form.description.trim() || undefined,
    publisher: form.publisher.trim() || undefined,
    publishDate: form.publishDate.trim() || undefined,
    rating: form.rating === null || form.rating === '' ? undefined : Number(form.rating),
    ratingCount: form.ratingCount === null || form.ratingCount === '' ? undefined : Number(form.ratingCount),
    source: form.source.trim() || undefined
  }

  try {
    if (dialogMode.value === 'create') {
      await adminApi.createBook(payload)
      ElMessage.success('书籍已创建')
    } else {
      await adminApi.updateBook(form.id, payload)
      ElMessage.success('书籍已更新')
    }
    dialogVisible.value = false
    load()
  } finally {
    submitting.value = false
  }
}

async function onDelete(row) {
  try {
    await ElMessageBox.confirm(`确定删除《${row.title}》吗？该操作不可恢复。`, '删除确认', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消'
    })
  } catch {
    return
  }
  await adminApi.deleteBook(row.id)
  ElMessage.success('已删除')
  load()
}

onMounted(load)
</script>

<template>
  <div class="admin-page">
    <div class="bn-card filter-bar">
      <el-input
        v-model="keyword"
        placeholder="按书名 / 作者搜索"
        clearable
        style="width: 240px"
        @keyup.enter="onSearch"
      >
        <template #prefix><el-icon><Search /></el-icon></template>
      </el-input>

      <el-button type="primary" @click="onSearch">查询</el-button>
      <el-button @click="load">
        <el-icon><Refresh /></el-icon>
      </el-button>

      <el-button type="primary" plain class="add-btn" @click="openCreate">
        <el-icon style="margin-right: 4px"><Plus /></el-icon>新增书籍
      </el-button>

      <span class="total-tip">共 {{ total }} 条</span>
    </div>

    <el-table v-loading="loading" :data="tableData" class="bn-card table" border stripe>
      <el-table-column label="书名" min-width="200">
        <template #default="{ row }">
          <div class="book-cell">
            <img v-if="row.coverUrl" :src="row.coverUrl" class="book-thumb" alt="" />
            <div v-else class="book-thumb placeholder">
              {{ (row.title || '书').charAt(0) }}
            </div>
            <div class="book-cell-body">
              <p class="book-title">{{ row.title }}</p>
              <p class="book-sub">{{ row.author || '佚名' }}</p>
            </div>
          </div>
        </template>
      </el-table-column>

      <el-table-column label="ISBN" width="140">
        <template #default="{ row }">
          <span class="bn-text-sub isbn">{{ row.isbn || '—' }}</span>
        </template>
      </el-table-column>

      <el-table-column label="出版社" width="150">
        <template #default="{ row }">
          <span class="bn-text-sub">{{ row.publisher || '—' }}</span>
        </template>
      </el-table-column>

      <el-table-column label="评分" width="100">
        <template #default="{ row }">
          <span v-if="Number(row.rating) > 0" class="rating">
            {{ Number(row.rating).toFixed(1) }}
          </span>
          <span v-else class="bn-text-muted">—</span>
        </template>
      </el-table-column>

      <el-table-column label="来源" width="100">
        <template #default="{ row }">
          <el-tag v-if="row.source" size="small" effect="plain">{{ row.source }}</el-tag>
          <span v-else class="bn-text-muted">—</span>
        </template>
      </el-table-column>

      <el-table-column label="创建时间" width="150">
        <template #default="{ row }">
          <span class="bn-text-muted time">{{ formatDateTime(row.createTime) }}</span>
        </template>
      </el-table-column>

      <el-table-column label="操作" width="130" fixed="right">
        <template #default="{ row }">
          <el-button link size="small" @click="openEdit(row)">编辑</el-button>
          <el-button link size="small" type="danger" @click="onDelete(row)">删除</el-button>
        </template>
      </el-table-column>

      <template #empty>
        <div class="bn-empty">暂无数据</div>
      </template>
    </el-table>

    <div class="pager">
      <el-pagination
        v-model:current-page="pageInfo.page"
        v-model:page-size="pageInfo.pageSize"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next"
        background
        @current-change="load"
        @size-change="onSearch"
      />
    </div>

    <!-- 新增 / 编辑 -->
    <el-dialog
      v-model="dialogVisible"
      :title="dialogMode === 'create' ? '新增书籍' : '编辑书籍'"
      width="620px"
      top="6vh"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
        <el-row :gutter="14">
          <el-col :span="16">
            <el-form-item label="书名" prop="title">
              <el-input v-model="form.title" placeholder="请输入书名" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="作者">
              <el-input v-model="form.author" placeholder="作者" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item label="ISBN（可一键补全）">
          <div class="isbn-row">
            <el-input v-model="form.isbn" placeholder="输入 ISBN，如 9787020002207" />
            <el-button :loading="isbnLoading" @click="fetchByIsbn">
              <el-icon style="margin-right: 3px"><Download /></el-icon>补全
            </el-button>
          </div>
        </el-form-item>

        <el-row :gutter="14">
          <el-col :span="12">
            <el-form-item label="出版社">
              <el-input v-model="form.publisher" placeholder="出版社" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="出版日期">
              <el-input v-model="form.publishDate" placeholder="如 2019-01-01" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="14">
          <el-col :span="12">
            <el-form-item label="评分">
              <el-input-number
                v-model="form.rating"
                :min="0"
                :max="10"
                :precision="1"
                :step="0.1"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="评分人数">
              <el-input-number v-model="form.ratingCount" :min="0" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item label="封面图 URL">
          <el-input v-model="form.coverUrl" placeholder="https://..." clearable />
        </el-form-item>

        <el-form-item label="内容简介">
          <el-input
            v-model="form.description"
            type="textarea"
            :rows="4"
            placeholder="支持 Markdown"
            maxlength="1000"
            show-word-limit
          />
        </el-form-item>

        <el-form-item label="数据来源">
          <el-input v-model="form.source" placeholder="如 manual / GoogleBooks / OpenLibrary" />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="onSubmit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.admin-page {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.filter-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
  padding: 14px 16px;
}

.add-btn {
  margin-left: auto;
}

.total-tip {
  font-size: 13px;
  color: var(--bn-text-muted);
}

.table {
  padding: 0;
  overflow: hidden;
}

.book-cell {
  display: flex;
  align-items: center;
  gap: 10px;
}

.book-thumb {
  width: 34px;
  height: 48px;
  border-radius: 4px;
  object-fit: cover;
  flex-shrink: 0;
  background: #f0e9e1;
}

.book-thumb.placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
  color: #a9754f;
  font-weight: 700;
  font-size: 15px;
}

.book-title {
  font-size: 13.5px;
  font-weight: 600;
}

.book-sub {
  font-size: 12px;
  color: var(--bn-text-muted);
  margin-top: 2px;
}

.isbn,
.time {
  font-size: 12px;
}

.rating {
  color: #c8783c;
  font-weight: 600;
  font-size: 13px;
}

.pager {
  display: flex;
  justify-content: flex-end;
}

.isbn-row {
  display: flex;
  gap: 9px;
  width: 100%;
}
</style>
