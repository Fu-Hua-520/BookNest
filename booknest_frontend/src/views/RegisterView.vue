<script setup>
/**
 * 注册页：手机号（可选）+ 邮箱 + 密码
 * 对齐后端 UserRegisterDTO 校验规则
 */
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const userStore = useUserStore()

const formRef = ref(null)
const loading = ref(false)

const form = reactive({
  phone: '',
  email: '',
  password: '',
  confirmPassword: ''
})

function validateConfirm(rule, value, callback) {
  if (!value) {
    callback(new Error('请再次输入密码'))
  } else if (value !== form.password) {
    callback(new Error('两次输入的密码不一致'))
  } else {
    callback()
  }
}

const rules = {
  phone: [{ pattern: /^$|^1[3-9]\d{9}$/, message: '手机号格式不正确', trigger: 'blur' }],
  email: [
    { required: true, message: '请输入邮箱', trigger: 'blur' },
    { type: 'email', message: '邮箱格式不正确', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 32, message: '密码长度需在 6-32 位之间', trigger: 'blur' }
  ],
  confirmPassword: [{ required: true, validator: validateConfirm, trigger: 'blur' }]
}

async function onSubmit() {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  try {
    await userStore.register({
      phone: form.phone.trim(),
      email: form.email.trim(),
      password: form.password
    })
    ElMessage.success('注册成功，请登录')
    router.replace({ name: 'login' })
  } catch {
    // 拦截器已提示
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
        <h1>加入 BookNest</h1>
        <p class="bn-text-muted">写下你的第一篇书评</p>
      </div>

      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @submit.prevent>
        <el-form-item label="邮箱" prop="email">
          <el-input v-model="form.email" placeholder="用于登录，请填写有效邮箱" clearable>
            <template #prefix><el-icon><Message /></el-icon></template>
          </el-input>
        </el-form-item>

        <el-form-item label="手机号（可选）" prop="phone">
          <el-input v-model="form.phone" placeholder="11 位手机号" clearable maxlength="11">
            <template #prefix><el-icon><Iphone /></el-icon></template>
          </el-input>
        </el-form-item>

        <el-form-item label="密码" prop="password">
          <el-input v-model="form.password" type="password" placeholder="6-32 位密码" show-password>
            <template #prefix><el-icon><Lock /></el-icon></template>
          </el-input>
        </el-form-item>

        <el-form-item label="确认密码" prop="confirmPassword">
          <el-input
            v-model="form.confirmPassword"
            type="password"
            placeholder="请再次输入密码"
            show-password
            @keyup.enter="onSubmit"
          >
            <template #prefix><el-icon><Lock /></el-icon></template>
          </el-input>
        </el-form-item>

        <el-button type="primary" size="large" class="submit-btn" :loading="loading" @click="onSubmit">
          注册
        </el-button>
      </el-form>

      <div class="auth-foot">
        <span class="bn-text-muted">已有账号？</span>
        <router-link to="/login" class="link">直接登录</router-link>
      </div>
    </div>
  </div>
</template>

<style scoped>
.auth-page {
  display: flex;
  justify-content: center;
  padding: 34px 20px 70px;
}

.auth-card {
  width: 100%;
  max-width: 400px;
  padding: 28px 30px 22px;
}

.auth-head {
  text-align: center;
  margin-bottom: 20px;
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
</style>
