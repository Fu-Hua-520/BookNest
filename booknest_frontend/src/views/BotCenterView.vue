<script setup>
/**
 * AI 机器人中心：创建 / 管理「评论区 AI 机器人」
 *
 * 每个机器人由用户自己创建，填入自己的 API Key（DeepSeek 或阿里云百炼），
 * 经管理员审核通过后，任何帖子的评论区里 @机器人名 就能触发它回复。
 *
 * 三条约定，界面上都要说清楚，否则用户会踩坑：
 *   1. 名称 = @ 触发词，全局唯一，只能汉字/字母/数字/下划线/连字符；
 *   2. 创建后是「待审核」，审核通过前 @ 它不会有任何反应；
 *   3. 任何配置改动都会退回待审核（审核针对的是「这套 Key + 这个提示词」）。
 */
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as botApi from '@/api/bot'
import BnAvatar from '@/components/BnAvatar.vue'
import { formatDateTime } from '@/utils/format'

const router = useRouter()

/* ------------------------- 厂商 / 模型的本地镜像 ------------------------- */

/**
 * 与后端 AiBotConstant 保持一致。
 * 后端仍是唯一权威（提交时会再校验一次），这里只用来渲染下拉，
 * 免得用户对着一个空下拉框猜有什么模型可选。
 */
const PROVIDERS = [
  {
    value: 'deepseek',
    label: 'DeepSeek',
    hint: 'api.deepseek.com',
    models: ['deepseek-flash', 'deepseek-chat', 'deepseek-reasoner'],
    defaultModel: 'deepseek-flash'
  },
  {
    value: 'dashscope',
    label: '阿里云百炼',
    hint: 'dashscope.aliyuncs.com/compatible-mode',
    models: ['qwen-max', 'qwen-plus', 'qwen-turbo', 'qwen-long'],
    defaultModel: 'qwen-max'
  }
]

const MAX_BOTS = 5

/* ------------------------- 列表 ------------------------- */

const myBots = ref([])
const loading = ref(false)
const error = ref('')

/* ------------------------- 表单 ------------------------- */

const dialog = reactive({
  visible: false,
  mode: 'create', // create | edit
  id: '',
  /** 编辑时后端返回的脱敏 Key，用来提示「已配置」 */
  apiKeyMasked: '',
  apiKeySet: false,
  submitting: false,
  uploadingAvatar: false,
  form: {
    name: '',
    description: '',
    avatar: '',
    provider: 'deepseek',
    model: 'deepseek-flash',
    apiKey: '',
    systemPrompt: '',
    temperature: 0.7,
    // 界面上不再暴露这个字段：「token」是实现细节，而且回复长度已经被
    // 系统提示词限死在 200 字，再让用户调它没有意义。仍按默认值提交，
    // 后端字段保留，将来要放开也只改这里。
    maxTokens: 800
  }
})

const dialogTitle = computed(() => (dialog.mode === 'create' ? '创建 AI 机器人' : '编辑 AI 机器人'))

const currentProvider = computed(
  () => PROVIDERS.find((item) => item.value === dialog.form.provider) || PROVIDERS[0]
)

const modelOptions = computed(() => currentProvider.value.models)

/** 已通过的机器人数量（用于「还能建几个」提示） */
const approvedCount = computed(() => myBots.value.filter((bot) => bot.auditStatus === 1).length)

function auditText(status) {
  if (status === 0) return '待审核'
  if (status === 2) return '已驳回'
  return '已通过'
}

function auditType(status) {
  if (status === 0) return 'warning'
  if (status === 2) return 'danger'
  return 'success'
}

/* ------------------------- 加载 ------------------------- */

async function load() {
  loading.value = true
  error.value = ''
  try {
    myBots.value = (await botApi.listMyBots()) || []
  } catch (err) {
    error.value = err.message || '机器人列表加载失败'
    myBots.value = []
  } finally {
    loading.value = false
  }
}

/* ------------------------- 表单操作 ------------------------- */

function resetForm() {
  dialog.form.name = ''
  dialog.form.description = ''
  dialog.form.avatar = ''
  dialog.form.provider = 'deepseek'
  dialog.form.model = 'deepseek-flash'
  dialog.form.apiKey = ''
  dialog.form.systemPrompt = ''
  dialog.form.temperature = 0.7
  dialog.form.maxTokens = 800
}

function openCreate() {
  if (myBots.value.length >= MAX_BOTS) {
    ElMessage.warning(`每人最多创建 ${MAX_BOTS} 个机器人，先删掉不用的再建吧`)
    return
  }
  dialog.mode = 'create'
  dialog.id = ''
  dialog.apiKeyMasked = ''
  dialog.apiKeySet = false
  resetForm()
  dialog.visible = true
}

function openEdit(bot) {
  dialog.mode = 'edit'
  dialog.id = bot.id
  dialog.apiKeyMasked = bot.apiKeyMasked || ''
  dialog.apiKeySet = !!bot.apiKeySet
  dialog.form.name = bot.name || ''
  dialog.form.description = bot.description || ''
  dialog.form.avatar = bot.avatar || ''
  dialog.form.provider = bot.provider || 'deepseek'
  dialog.form.model = bot.model || currentProvider.value.defaultModel
  // Key 不回传明文：留空即「不修改」
  dialog.form.apiKey = ''
  dialog.form.systemPrompt = bot.systemPrompt || ''
  dialog.form.temperature = bot.temperature ?? 0.7
  dialog.form.maxTokens = bot.maxTokens ?? 800
  dialog.visible = true
}

/** 切厂商时把模型同步到该厂商的默认模型，避免残留上一个厂商的模型名 */
function onProviderChange(value) {
  const provider = PROVIDERS.find((item) => item.value === value)
  dialog.form.model = provider ? provider.defaultModel : ''
}

/**
 * 头像：选图即上传（复用 /common/upload 的 OSS 中转）。
 * 上传失败必须清空 avatar，否则用户以为传上了、提交却是空的。
 */
async function onAvatarUpload(options) {
  const { file, onSuccess, onError } = options
  dialog.uploadingAvatar = true
  try {
    const { uploadFile } = await import('@/api/user')
    const url = await uploadFile(file)
    dialog.form.avatar = url
    onSuccess?.(url)
    ElMessage.success('头像上传成功')
  } catch (err) {
    dialog.form.avatar = ''
    onError?.(err)
  } finally {
    dialog.uploadingAvatar = false
  }
}

function clearAvatar() {
  dialog.form.avatar = ''
}

/** 名称即 @ 触发词，必须与后端 AiBotConstant.NAME_PATTERN 同口径 */
const NAME_RE = /^[\p{Script=Han}A-Za-z0-9_-]+$/u

function validate() {
  const f = dialog.form
  const name = f.name.trim()
  if (name.length < 2 || name.length > 20) {
    ElMessage.warning('机器人名称需为 2~20 个字')
    return null
  }
  if (!NAME_RE.test(name)) {
    ElMessage.warning('名称只能用汉字、字母、数字、下划线或连字符')
    return null
  }
  if (!f.apiKey.trim() && dialog.mode === 'create') {
    ElMessage.warning('请填写 API Key')
    return null
  }
  if (!f.model) {
    ElMessage.warning('请选择模型')
    return null
  }
  return {
    name,
    description: f.description.trim() || undefined,
    avatar: f.avatar || undefined,
    provider: f.provider,
    model: f.model,
    // 编辑时留空 = 沿用原 Key，不要在这里塞空串覆盖
    apiKey: f.apiKey.trim() || undefined,
    systemPrompt: f.systemPrompt.trim() || undefined,
    temperature: f.temperature,
    maxTokens: f.maxTokens
  }
}

async function submit() {
  const payload = validate()
  if (!payload) return

  // 编辑会把机器人退回待审核，先跟用户确认一次
  if (dialog.mode === 'edit') {
    try {
      await ElMessageBox.confirm(
        '修改配置后机器人会回到「待审核」，管理员重新通过前它不会再回复评论。确定提交吗？',
        '提交修改',
        { confirmButtonText: '提交', cancelButtonText: '再想想', type: 'warning' }
      )
    } catch {
      return
    }
  }

  dialog.submitting = true
  try {
    if (dialog.mode === 'create') {
      await botApi.createBot(payload)
      ElMessage.success('已提交，等待管理员审核')
    } else {
      await botApi.updateBot(dialog.id, payload)
      ElMessage.success('已保存，等待管理员重新审核')
    }
    dialog.visible = false
    await load()
  } catch {
    // 拦截器已提示
  } finally {
    dialog.submitting = false
  }
}

async function onToggleEnabled(bot, next) {
  try {
    await botApi.setBotEnabled(bot.id, next)
    bot.enabled = next ? 1 : 0
    ElMessage.success(next ? '已启用' : '已停用')
  } catch {
    // 失败就把开关拨回去，别让界面和库里的状态不一致
    bot.enabled = next ? 0 : 1
  }
}

async function onDelete(bot) {
  try {
    await ElMessageBox.confirm(
      `删除后「${bot.name}」在评论区留下的历史回复也会一起消失，且不可恢复。确定删除吗？`,
      '删除机器人',
      { confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning' }
    )
  } catch {
    return
  }
  try {
    await botApi.deleteBot(bot.id)
    ElMessage.success('已删除')
    await load()
  } catch {
    /* 拦截器已提示 */
  }
}

onMounted(load)
</script>

<template>
  <div class="bn-container bot-page">
    <header class="bot-head">
      <div>
        <h1 class="bn-page-title">AI 机器人</h1>
        <p class="bn-page-sub">
          造一个属于你的书友 AI —— 在任意帖子的评论里 <code>@机器人名</code> 就能召唤它
        </p>
      </div>
      <el-button type="primary" @click="openCreate">
        <el-icon style="margin-right: 4px"><Plus /></el-icon>创建机器人
      </el-button>
    </header>

    <el-alert type="info" :closable="false" show-icon class="how-card">
      <template #title>怎么用？</template>
      <div class="how-body">
        <p>1. 填写厂商（DeepSeek / 阿里云百炼）、模型和自己的 API Key，可选填一段系统提示词来设定它的身份。</p>
        <p>2. 提交后由管理员审核，通过前它不会回复任何评论。</p>
        <p>3. 审核通过后，在任意帖子的评论里输入「@机器人名 + 问题」就能触发它回复，每条回复不超过 200 字。</p>
      </div>
    </el-alert>

    <el-alert v-if="error" :title="error" type="error" show-icon :closable="false" class="bn-mt-12">
      <template #default>
        <el-button size="small" @click="load">重新加载</el-button>
      </template>
    </el-alert>

    <section class="section">
      <div class="section-head">
        <h2 class="section-title">我的机器人</h2>
        <span class="bn-text-muted section-count">
          {{ myBots.length }} / {{ MAX_BOTS }} 个<span v-if="approvedCount">（{{ approvedCount }} 个已上线）</span>
        </span>
      </div>

      <el-skeleton v-if="loading" :rows="4" animated />

      <div v-else-if="myBots.length" class="bot-grid">
        <article v-for="bot in myBots" :key="bot.id" class="bot-card">
          <div class="bot-card-head">
            <BnAvatar :src="bot.avatar" :name="bot.name" :size="44" :linkable="false" />
            <div class="bot-title">
              <div class="bot-name-row">
                <span class="bot-name bn-ellipsis-1">{{ bot.name }}</span>
                <el-tag :type="auditType(bot.auditStatus)" size="small" effect="light">
                  {{ auditText(bot.auditStatus) }}
                </el-tag>
              </div>
              <div class="bot-tags">
                <el-tag size="small" effect="plain" type="info">{{ bot.providerLabel || bot.provider }}</el-tag>
                <el-tag size="small" effect="plain" type="info">{{ bot.model }}</el-tag>
                <span v-if="bot.replyCount" class="bn-text-muted reply-count">
                  已回复 {{ bot.replyCount }} 次
                </span>
              </div>
            </div>
          </div>

          <p class="bot-desc bn-ellipsis-2">
            {{ bot.description || '这个机器人还没有简介' }}
          </p>

          <!-- 驳回原因要显眼：用户得知道改哪儿 -->
          <el-alert
            v-if="bot.auditStatus === 2 && bot.rejectReason"
            type="error"
            :closable="false"
            class="reject-note"
          >
            <template #title>驳回原因：{{ bot.rejectReason }}</template>
          </el-alert>

          <div class="bot-card-foot">
            <div class="foot-left">
              <el-switch
                :model-value="bot.enabled === 1"
                :disabled="bot.auditStatus !== 1"
                size="small"
                @update:model-value="(val) => onToggleEnabled(bot, val)"
              />
              <span class="bn-text-muted switch-label">
                {{ bot.auditStatus !== 1 ? '过审后可启停' : bot.enabled === 1 ? '已启用' : '已停用' }}
              </span>
            </div>
            <div class="foot-right">
              <span class="bn-text-muted time">{{ formatDateTime(bot.updateTime || bot.createTime) }}</span>
              <el-button link size="small" @click="openEdit(bot)">编辑</el-button>
              <el-button link size="small" type="danger" @click="onDelete(bot)">删除</el-button>
            </div>
          </div>
        </article>
      </div>

      <div v-else-if="!loading" class="bn-empty">
        <el-icon :size="34" color="#c8bdb1"><MagicStick /></el-icon>
        <p class="bn-mt-12">你还没有创建过 AI 机器人</p>
        <el-button type="primary" class="bn-mt-12" @click="openCreate">
          <el-icon style="margin-right: 4px"><Plus /></el-icon>创建第一个机器人
        </el-button>
      </div>
    </section>

    <!-- 创建 / 编辑 -->
    <el-dialog v-model="dialog.visible" :title="dialogTitle" width="560px" top="6vh">
      <el-form label-position="top" @submit.prevent>
        <el-form-item label="机器人头像（可选）">
          <div class="avatar-row">
            <BnAvatar :src="dialog.form.avatar" :name="dialog.form.name || '?'" :size="54" :linkable="false" />
            <div class="avatar-actions">
              <div class="avatar-btns">
                <el-upload
                  :show-file-list="false"
                  :http-request="onAvatarUpload"
                  accept="image/*"
                  :disabled="dialog.uploadingAvatar"
                >
                  <el-button size="small" :loading="dialog.uploadingAvatar">
                    {{ dialog.form.avatar ? '更换头像' : '上传头像' }}
                  </el-button>
                </el-upload>
                <el-button v-if="dialog.form.avatar" link size="small" @click="clearAvatar">移除</el-button>
              </div>
              <p class="bn-text-muted avatar-tip">不上传时用机器人名首字当头像</p>
            </div>
          </div>
        </el-form-item>

        <el-form-item label="机器人名称（即 @ 触发词）">
          <el-input
            v-model="dialog.form.name"
            maxlength="20"
            show-word-limit
            placeholder="2~20 个字，例如：小书虫"
          />
          <p class="bn-text-muted field-tip">
            只能用汉字、字母、数字、下划线、连字符；全站唯一，改完要重新过审。
          </p>
        </el-form-item>

        <el-form-item label="简介（可选）">
          <el-input
            v-model="dialog.form.description"
            type="textarea"
            :rows="2"
            maxlength="200"
            show-word-limit
            placeholder="一句话介绍它能做什么，会显示在机器人列表里"
          />
        </el-form-item>

        <div class="form-row">
          <el-form-item label="模型厂商" class="form-col">
            <el-select v-model="dialog.form.provider" style="width: 100%" @change="onProviderChange">
              <el-option v-for="p in PROVIDERS" :key="p.value" :label="p.label" :value="p.value">
                <span>{{ p.label }}</span>
                <span class="opt-hint">{{ p.hint }}</span>
              </el-option>
            </el-select>
          </el-form-item>
          <el-form-item label="模型" class="form-col">
            <el-select v-model="dialog.form.model" filterable allow-create style="width: 100%">
              <el-option v-for="m in modelOptions" :key="m" :label="m" :value="m" />
            </el-select>
          </el-form-item>
        </div>

        <el-form-item label="API Key">
          <el-input
            v-model="dialog.form.apiKey"
            type="password"
            show-password
            :placeholder="
              dialog.mode === 'edit' && dialog.apiKeySet
                ? `已配置（${dialog.apiKeyMasked}），留空表示不修改`
                : '粘贴你的 API Key'
            "
          />
          <p class="bn-text-muted field-tip">调用费用由你自己的账号承担。</p>
        </el-form-item>

        <el-form-item label="系统提示词（可选，用来设定它的身份）">
          <el-input
            v-model="dialog.form.systemPrompt"
            type="textarea"
            :rows="4"
            maxlength="2000"
            show-word-limit
            placeholder="例如：你是一位毒舌但靠谱的科幻小说评论家，回答要简短犀利。"
          />
          <p class="bn-text-muted field-tip">每条回复都会被限制在 200 字以内，这里不用再写字数要求。</p>
        </el-form-item>

        <el-form-item label="采样温度">
          <el-slider v-model="dialog.form.temperature" :min="0" :max="2" :step="0.1" show-input />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="dialog.submitting" @click="submit">
          {{ dialog.mode === 'create' ? '提交审核' : '保存并重新送审' }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.bot-page {
  padding-top: 22px;
  padding-bottom: 44px;
}

.bot-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 14px;
}

.bot-head .bn-page-sub {
  margin-bottom: 0;
}

.bot-head code {
  padding: 1px 5px;
  border-radius: 4px;
  background: var(--bn-primary-soft);
  color: var(--bn-primary);
  font-size: 12.5px;
}

.how-card {
  border-radius: var(--bn-radius);
  margin-bottom: 20px;
}

.how-body {
  font-size: 12.5px;
  line-height: 1.75;
}

.how-body p {
  margin: 0;
}

.section {
  margin-bottom: 26px;
}

.section-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.section-title {
  font-size: 15px;
  font-weight: 700;
}

.section-count {
  font-size: 12.5px;
}

.bot-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.bot-card {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 14px 15px;
  border: 1px solid var(--bn-border);
  border-radius: var(--bn-radius);
  background: var(--bn-surface);
}

.bot-card-head {
  display: flex;
  align-items: center;
  gap: 11px;
  min-width: 0;
}

.bot-title {
  display: flex;
  flex-direction: column;
  gap: 5px;
  min-width: 0;
  flex: 1;
}

.bot-name-row {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.bot-name {
  font-size: 14.5px;
  font-weight: 600;
}

.bot-tags {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}

.reply-count {
  font-size: 11.5px;
}

.bot-desc {
  font-size: 12.5px;
  color: var(--bn-text-muted);
  line-height: 1.6;
  min-height: 34px;
}

.reject-note {
  border-radius: 8px;
}

.reject-note :deep(.el-alert__title) {
  font-size: 12px;
  line-height: 1.55;
}

.bot-card-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding-top: 10px;
  border-top: 1px dashed var(--bn-border);
}

.foot-left {
  display: flex;
  align-items: center;
  gap: 7px;
}

.switch-label {
  font-size: 11.5px;
}

.foot-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

.foot-right .time {
  font-size: 11.5px;
}

/* ---------- 弹窗 ---------- */
.avatar-row {
  display: flex;
  align-items: center;
  gap: 14px;
  width: 100%;
}

.avatar-actions {
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-width: 0;
}

.avatar-btns {
  display: flex;
  align-items: center;
  gap: 10px;
}

.avatar-tip,
.field-tip {
  font-size: 11.5px;
  line-height: 1.5;
}

.field-tip {
  margin-top: 4px;
}

.opt-hint {
  float: right;
  color: var(--bn-text-muted);
  font-size: 11.5px;
}

.form-row {
  display: flex;
  gap: 14px;
}

.form-col {
  flex: 1;
  min-width: 0;
}

@media (max-width: 860px) {
  .bot-grid {
    grid-template-columns: 1fr;
  }
  .form-row {
    flex-direction: column;
    gap: 0;
  }
  .bot-head {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>
