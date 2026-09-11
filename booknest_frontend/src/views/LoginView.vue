<script setup>
/**
 * 登录页：邮箱 + 密码
 * 登录成功后可跳转管理后台（仅 ADMIN 角色）
 */
import { computed, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useBadgeStore } from '@/stores/badge'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const badgeStore = useBadgeStore()

const formRef = ref(null)
const loading = ref(false)

const form = reactive({
  email: '',
  password: ''
})

const rules = {
  email: [
    { required: true, message: '请输入邮箱', trigger: 'blur' },
    { type: 'email', message: '邮箱格式不正确', trigger: 'blur' }
  ],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

const redirectPath = computed(() => route.query.redirect || '/')

async function onSubmit() {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  try {
    const vo = await userStore.login({ email: form.email.trim(), password: form.password })
    ElMessage.success(`欢迎回来，${vo.username || vo.account || '书友'}`)
    badgeStore.startPolling()
    router.replace(redirectPath.value)
  } catch {
    // 错误提示已由请求拦截器统一处理
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="auth-page">
    <div class="auth-card bn-card">
      <div class="auth-head">
        <span class="logo-mark">书</span>
        <h1>登录 BookNest</h1>
        <p class="bn-text-muted">以书为纽带，遇见同好</p>
      </div>

      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @submit.prevent>
        <el-form-item label="邮箱" prop="email">
          <el-input v-model="form.email" placeholder="请输入注册邮箱" size="large" clearable>
            <template #prefix><el-icon><Message /></el-icon></template>
          </el-input>
        </el-form-item>

        <el-form-item label="密码" prop="password">
          <el-input
            v-model="form.password"
            type="password"
            placeholder="请输入密码"
            size="large"
            show-password
            @keyup.enter="onSubmit"
          >
            <template #prefix><el-icon><Lock /></el-icon></template>
          </el-input>
        </el-form-item>

        <el-button type="primary" size="large" class="submit-btn" :loading="loading" @click="onSubmit">
          登录
        </el-button>
      </el-form>

      <div class="auth-foot">
        <span class="bn-text-muted">还没有账号？</span>
        <router-link to="/register" class="link">立即注册</router-link>
      </div>

      <el-divider>
        <span class="divider-text">或</span>
      </el-divider>

      <router-link to="/admin/login" class="admin-entry">
        <el-icon><Setting /></el-icon>管理员登录
      </router-link>
    </div>
  </div>
</template>

<style scoped>
.auth-page {
  display: flex;
  justify-content: center;
  padding: 40px 20px 70px;
}

.auth-card {
  width: 100%;
  max-width: 400px;
  padding: 30px 30px 24px;
}

.auth-head {
  text-align: center;
  margin-bottom: 24px;
}

.logo-mark {
  width: 44px;
  height: 44px;
  border-radius: 12px;
  background: var(--bn-primary);
  color: #fff;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-weight: 700;
  font-size: 22px;
  margin-bottom: 12px;
}

.auth-head h1 {
  font-size: 20px;
  margin-bottom: 4px;
}

.auth-head p {
  font-size: 13px;
}

.submit-btn {
  width: 100%;
  margin-top: 4px;
}

.auth-foot {
  text-align: center;
  margin-top: 16px;
  font-size: 13px;
}

.link {
  color: var(--bn-primary);
  margin-left: 4px;
  font-weight: 500;
}

.link:hover {
  text-decoration: underline;
}

.divider-text {
  font-size: 12px;
  color: var(--bn-text-muted);
}

.admin-entry {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 5px;
  font-size: 13px;
  color: var(--bn-text-sub);
  padding: 7px;
  border-radius: 8px;
  border: 1px dashed var(--bn-border);
  transition: all 0.15s ease;
}

.admin-entry:hover {
  color: var(--bn-primary);
  border-color: var(--bn-primary);
}
</style>
