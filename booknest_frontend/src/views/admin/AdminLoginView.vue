<script setup>
/**
 * 管理后台登录
 * 调用后端独立的 /admin/login：令牌由管理端密钥签发，与前台令牌互不通用。
 * 非管理员账号会被后端拒绝，为避免枚举邮箱，统一返回"账号或密码错误"。
 */
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAdminStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()
const adminStore = useAdminStore()

const formRef = ref(null)
const loading = ref(false)

const form = reactive({ email: '', password: '' })

const rules = {
  email: [
    { required: true, message: '请输入管理员邮箱', trigger: 'blur' },
    { type: 'email', message: '邮箱格式不正确', trigger: 'blur' }
  ],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

async function onSubmit() {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  try {
    await adminStore.login({ email: form.email.trim(), password: form.password })
    ElMessage.success('登录成功')
    router.replace(route.query.redirect || { name: 'admin-post' })
  } catch {
    // 错误提示已由请求拦截器统一处理
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="admin-login">
    <div class="login-card bn-card">
      <div class="card-head">
        <span class="logo-mark">B</span>
        <h1>BookNest 管理后台</h1>
        <p class="bn-text-muted">仅限管理员账号登录</p>
      </div>

      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @submit.prevent>
        <el-form-item label="管理员邮箱" prop="email">
          <el-input v-model="form.email" placeholder="请输入邮箱" size="large" clearable>
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
          登录后台
        </el-button>
      </el-form>

      <router-link to="/" class="back-home">
        <el-icon><ArrowLeft /></el-icon>返回前台
      </router-link>
    </div>
  </div>
</template>

<style scoped>
.admin-login {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 40px 20px;
  background: linear-gradient(150deg, #f7f5f2 0%, #efe7dd 100%);
}

.login-card {
  width: 100%;
  max-width: 380px;
  padding: 30px 30px 22px;
}

.card-head {
  text-align: center;
  margin-bottom: 22px;
}

.logo-mark {
  width: 46px;
  height: 46px;
  border-radius: 13px;
  background: #2f2a25;
  color: #fff;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-weight: 700;
  font-size: 22px;
  margin-bottom: 12px;
}

.card-head h1 {
  font-size: 19px;
  margin-bottom: 4px;
}

.card-head p {
  font-size: 12.5px;
}

.submit-btn {
  width: 100%;
  margin-top: 4px;
}

.back-home {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  margin-top: 16px;
  font-size: 12.5px;
  color: var(--bn-text-muted);
}

.back-home:hover {
  color: var(--bn-primary);
}
</style>
