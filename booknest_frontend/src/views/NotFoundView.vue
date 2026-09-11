<script setup>
import { useRouter } from 'vue-router'

const router = useRouter()
</script>

<template>
  <div class="bn-container not-found">
    <p class="code">404</p>
    <h1>这一页翻不到了</h1>
    <p class="bn-text-muted">页面可能已被删除，或者链接输错了</p>
    <div class="actions">
      <el-button type="primary" @click="router.push('/')">回到首页</el-button>
      <el-button @click="router.back()">返回上一页</el-button>
    </div>
  </div>
</template>

<style scoped>
.not-found {
  text-align: center;
  padding: 70px 20px;
}

.code {
  font-size: 74px;
  font-weight: 700;
  color: #e2d7cb;
  line-height: 1;
  margin-bottom: 10px;
}

h1 {
  font-size: 20px;
  margin-bottom: 6px;
}

.actions {
  margin-top: 22px;
  display: flex;
  gap: 10px;
  justify-content: center;
}
</style>
