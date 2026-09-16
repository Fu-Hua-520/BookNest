<script setup>
/**
 * 帖子编辑：新建与编辑复用同一页面
 * 对齐后端 PostPublishDTO / PostUpdateDTO，正文以 Markdown 提交（后端转存 OSS）
 *
 * 两个容易踩的点：
 *  1. 分类已改造成贴吧式的「书吧」，但字段名仍是 categoryId（复用 category 表），
 *     界面上叫「书吧」，不要被字段名带偏。
 *  2. 标签下拉开了 allow-create：v-model 里会同时出现「已存在标签的 UUID」和
 *     「用户新敲的标签名」，提交前必须先落库换 ID 再去重，见 resolveTagIds。
 */
import { computed, onMounted, onUnmounted, reactive, ref } from 'vue'
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

/** 帖子类型：白名单与后端 PostTypeConstant 保持一致 */
const POST_TYPES = [
  { value: 'NORMAL', label: '书评', desc: '常规的书评、读书笔记' },
  {
    value: 'HELP',
    label: '求助贴',
    desc: '找书、求推荐、求解惑 —— 会展示在首页「书友求助」版块'
  }
]

const form = reactive({
  title: '',
  summary: '',
  content: '',
  bookId: '',
  categoryId: '',
  coverImage: '',
  tagIds: [],
  postType: 'NORMAL'
})

const currentTypeDesc = computed(
  () => POST_TYPES.find((item) => item.value === form.postType)?.desc || ''
)

/**
 * 书吧选项：**已平铺**。
 *
 * 书吧取消了分级，后端 /category/tree 返回的就是一层平级列表（children 恒空），
 * 所以这里用普通 el-select，不再需要 el-cascader 的层级展开。
 * 数据源只含「已过审 + 未禁用」的吧，待审/被驳回的申请不会混进来。
 */
const categoryOptions = computed(() =>
  (taxonomyStore.categoryTree || []).map((bar) => ({
    value: String(bar.id),
    label: bar.name,
    icon: bar.icon || ''
  }))
)

/** 关联书籍：搜索选择 */
const bookOptions = ref([])
const bookSearching = ref(false)
const selectedBook = ref(null)

/** 标签：全部标签 + 已选 */
const allTags = ref([])

/**
 * 封面：本地 blob 预览 + 上传中状态。
 *
 * 选图后先用 URL.createObjectURL 在本地生成地址立刻显示（瞬时，不等网络），
 * 上传在后台并行进行，成功后换成 OSS 真实 URL。这样弱网下也是「选完秒出图」。
 * blob 地址必须手动 revoke，否则会一直占着内存直到页面关闭。
 */
const localPreview = ref('')
const uploadingCover = ref(false)

/** 封面显示优先级：上传中的本地预览 > 已保存的 OSS URL */
const coverPreview = computed(() => localPreview.value || form.coverImage)

const coverBtnText = computed(() => {
  if (uploadingCover.value) return '上传中'
  return form.coverImage ? '更换封面' : '上传封面'
})

function releasePreview() {
  if (localPreview.value) {
    URL.revokeObjectURL(localPreview.value)
    localPreview.value = ''
  }
}

/** 移除封面：本地预览与已保存的地址一起清掉 */
function clearCover() {
  releasePreview()
  form.coverImage = ''
}

const rules = {
  title: [
    { required: true, message: '请输入标题', trigger: 'blur' },
    { max: 100, message: '标题不超过 100 字', trigger: 'blur' }
  ],
  categoryId: [{ required: true, message: '请选择书吧', trigger: 'change' }],
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

/**
 * 标签归一到「ID 数组」。
 *
 * 下拉开了 allow-create，所以 form.tagIds 里混着两类值：
 *   - 已在 allTags 里的：就是标签 UUID，直接用；
 *   - 不在 allTags 里的：用户在输入框里敲的新标签名，逐个调 /tag/create
 *     落库（后端重名会静默复用），再把返回的 ID 收集起来。
 * 最后整体去重 —— 万一用户既选了「#科幻」又手敲了「科幻」，两路会指向同一个标签，
 * 重复的 tag_id 会撞 post_tag 的 uk_post_tag 唯一键，整篇帖子直接发不出去。
 */
async function resolveTagIds() {
  const knownIds = new Set(allTags.value.map((tag) => String(tag.id)))
  const ids = []
  const newNames = []

  for (const item of form.tagIds) {
    const value = String(item || '').trim()
    if (!value) continue
    if (knownIds.has(value)) ids.push(value)
    else newNames.push(value)
  }

  for (const name of newNames) {
    try {
      const tag = await taxonomyApi.createTag(name)
      if (tag?.id) ids.push(String(tag.id))
    } catch {
      // 单个标签建失败不阻断发帖，用户换一个或去掉即可
    }
  }

  return [...new Set(ids)]
}

async function loadForEdit() {
  pageLoading.value = true
  try {
    const detail = await postApi.getPostDetail(editId.value)
    if (String(detail.authorId) !== String(userStore.userId)) {
      ElMessage.error('只能编辑自己的帖子')
      router.replace(`/post/${editId.value}`)
      return
    }
    form.title = detail.title || ''
    form.summary = detail.summary || ''
    form.content = detail.content || ''
    form.bookId = detail.bookId || ''
    // 级联选项的 value 是字符串，这里统一转成字符串，
    // 否则回填时类型对不上，级联框会显示成空
    form.categoryId = detail.categoryId != null ? String(detail.categoryId) : ''
    form.coverImage = detail.coverImage || ''
    form.tagIds = (detail.tags || []).map((tag) => String(tag.id))
    form.postType = String(detail.postType || 'NORMAL').toUpperCase()

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
  try {
    const tagIds = await resolveTagIds()
    const payload = {
      title: form.title.trim(),
      summary: form.summary.trim() || undefined,
      content: form.content,
      bookId: form.bookId || undefined,
      categoryId: form.categoryId,
      coverImage: form.coverImage || undefined,
      tagIds: tagIds.length ? tagIds : undefined,
      postType: form.postType
    }

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

  // 先用本地 blob 地址把图显示出来，不等网络往返
  releasePreview()
  localPreview.value = URL.createObjectURL(file)
  uploadingCover.value = true

  try {
    const { uploadFile } = await import('@/api/user')
    const url = await uploadFile(file)

    // 先把远端图预加载完再撤掉 blob，否则 revoke 的瞬间会闪一下空白
    await new Promise((resolve) => {
      const img = new Image()
      img.onload = resolve
      img.onerror = resolve
      img.src = url
    })

    form.coverImage = url
    releasePreview()
    onSuccess?.(url)
    ElMessage.success('封面上传成功')
  } catch (err) {
    // 上传失败必须撤掉本地预览，否则用户会误以为封面已经生效
    releasePreview()
    onError?.(err)
  } finally {
    uploadingCover.value = false
  }
}

onMounted(async () => {
  // 首页「我要提问」按钮带 ?type=HELP 进来，直接预选求助贴
  if (String(route.query.type || '').toUpperCase() === 'HELP') {
    form.postType = 'HELP'
  }
  // 书吧页「在本吧发帖」带 ?barId=xxx 进来，直接预选那个吧（字段名仍叫 categoryId）
  if (route.query.barId) {
    form.categoryId = String(route.query.barId)
  }
  await taxonomyStore.load()
  loadTags()
  if (isEdit.value) loadForEdit()
})

// 离开页面时释放 blob 地址，否则这块内存要等到页面关闭才回收
onUnmounted(releasePreview)
</script>

<template>
  <div class="bn-container edit-page">
    <div class="page-head">
      <h1 class="bn-page-title">{{ isEdit ? '编辑帖子' : '写帖子' }}</h1>
      <p class="bn-page-sub">发布后需通过审核才会公开展示</p>
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
      <!-- 帖子类型 -->
      <el-form-item label="帖子类型">
        <div class="type-row">
          <el-radio-group v-model="form.postType">
            <el-radio-button v-for="item in POST_TYPES" :key="item.value" :value="item.value">
              {{ item.label }}
            </el-radio-button>
          </el-radio-group>
          <span class="type-desc bn-text-muted">{{ currentTypeDesc }}</span>
        </div>
      </el-form-item>

      <!-- 标题 -->
      <el-form-item label="标题" prop="title">
        <el-input
          v-model="form.title"
          :placeholder="
            form.postType === 'HELP'
              ? '说清楚你要找什么，例如：想找一本讲宋代生活的入门读物'
              : '给这篇书评起个标题，例如：读完《活着》后的一些想法'
          "
          maxlength="100"
          show-word-limit
          size="large"
        />
      </el-form-item>

      <el-row :gutter="16">
        <!-- 书吧（字段名仍是 categoryId） -->
        <el-col :xs="24" :sm="12">
          <el-form-item label="发到哪个吧" prop="categoryId">
            <el-select
              v-model="form.categoryId"
              filterable
              placeholder="请选择书吧"
              clearable
              style="width: 100%"
            >
              <el-option
                v-for="bar in categoryOptions"
                :key="bar.value"
                :label="bar.label"
                :value="bar.value"
              >
                <span class="bar-option">
                  <img v-if="bar.icon" :src="bar.icon" class="bar-option-icon" :alt="bar.label" />
                  <span v-else class="bar-option-icon bar-option-text">{{ bar.label.slice(0, 1) }}</span>
                  <span>{{ bar.label }}</span>
                </span>
              </el-option>
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
          placeholder="一句话概括这篇帖子，留空则自动截取正文开头"
          maxlength="200"
          show-word-limit
        />
      </el-form-item>

      <!-- 封面 -->
      <el-form-item label="封面图（可选）">
        <div class="cover-row">
          <BnCover
            v-if="coverPreview"
            :src="coverPreview"
            title="封面"
            width="90px"
            height="120px"
            :class="{ 'cover-pending': uploadingCover }"
          />
          <div class="cover-actions">
            <div class="cover-btns">
              <el-upload
                :show-file-list="false"
                :http-request="onCoverUpload"
                accept="image/*"
                :disabled="!userStore.isLoggedIn || uploadingCover"
              >
                <el-button :loading="uploadingCover">
                  <el-icon v-if="!uploadingCover" style="margin-right: 4px"><Upload /></el-icon>
                  {{ coverBtnText }}
                </el-button>
              </el-upload>
              <el-button v-if="coverPreview" link @click="clearCover">移除封面</el-button>
            </div>
          </div>
        </div>
      </el-form-item>

      <!-- 标签：既可从已有标签里选，也可以直接敲一个新名字 -->
      <el-form-item label="标签（可选，最多 5 个）">
        <el-select
          v-model="form.tagIds"
          multiple
          filterable
          clearable
          allow-create
          default-first-option
          collapse-tags
          collapse-tags-tooltip
          :multiple-limit="5"
          placeholder="选择标签，或直接输入新标签名后回车创建"
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
          {{ isEdit ? '保存修改' : '发布' }}
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

.type-row {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.type-desc {
  font-size: 12.5px;
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

/* 上传中：本地预览已显示，压暗表示还没真正落到云端 */
.cover-pending {
  opacity: 0.55;
}

.cover-btns {
  display: flex;
  align-items: center;
  gap: 10px;
}

/* ---------- 书吧下拉里的图标 ---------- */
.bar-option {
  display: inline-flex;
  align-items: center;
  gap: 7px;
}

.bar-option-icon {
  width: 18px;
  height: 18px;
  border-radius: 5px;
  object-fit: cover;
  background: #efe9e2;
}

.bar-option-text {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 10.5px;
  font-weight: 700;
  color: var(--bn-primary);
  background: var(--bn-primary-soft);
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
