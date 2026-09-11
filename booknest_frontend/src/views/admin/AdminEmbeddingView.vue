<script setup>
/**
 * 向量库管理：全量重嵌入论坛帖子向量（RAG 检索的数据基础）
 */
import { ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as adminApi from '@/api/admin'

const running = ref(false)
const lastCount = ref(null)
const lastRunAt = ref('')

async function run() {
  try {
    await ElMessageBox.confirm(
      '将对全部帖子重新分块并生成向量写入 zvector，耗时较长（取决于帖子数量与 embedding 接口速率），确定继续吗？',
      '全量重嵌入',
      {
        type: 'warning',
        confirmButtonText: '开始执行',
        cancelButtonText: '取消',
        confirmButtonClass: 'el-button--danger'
      }
    )
  } catch {
    return
  }

  running.value = true
  try {
    const count = await adminApi.batchEmbed()
    lastCount.value = count
    lastRunAt.value = new Date().toLocaleString('zh-CN')
    ElMessage.success(`重嵌入完成，共处理 ${count ?? 0} 条`)
  } finally {
    running.value = false
  }
}
</script>

<template>
  <div class="embedding-page">
    <div class="bn-card">
      <h2 class="card-title">RAG 向量库</h2>
      <p class="bn-card-desc bn-text-muted">
        帖子的 RAG 检索依赖向量库中的数据。修改了分块策略、更换 embedding 模型，
        或向量库数据缺失时，需要执行一次全量重嵌入。
      </p>

      <el-alert
        type="info"
        :closable="false"
        show-icon
        class="tip"
        title="前置条件"
        description="需在配置中开启 booknest.zvector.enabled=true 并正确填写 AnalyticDB 连接信息与 DASHSCOPE_API_KEY，否则执行将失败。"
      />

      <div class="pipeline">
        <div class="step">
          <span class="step-index">1</span>
          <div>
            <p class="step-title">分块</p>
            <p class="step-desc">帖子正文按 512 字切分，重叠 50 字</p>
          </div>
        </div>
        <div class="step-arrow"><el-icon><Right /></el-icon></div>

        <div class="step">
          <span class="step-index">2</span>
          <div>
            <p class="step-title">向量化</p>
            <p class="step-desc">DashScope text-embedding-v3 生成向量</p>
          </div>
        </div>
        <div class="step-arrow"><el-icon><Right /></el-icon></div>

        <div class="step">
          <span class="step-index">3</span>
          <div>
            <p class="step-title">入库</p>
            <p class="step-desc">写入 zvector（AnalyticDB PostgreSQL）</p>
          </div>
        </div>
      </div>

      <el-divider />

      <div class="action-row">
        <el-button type="primary" :loading="running" :disabled="running" @click="run">
          <el-icon v-if="!running" style="margin-right: 4px"><Refresh /></el-icon>
          {{ running ? '正在执行，请勿离开页面…' : '开始全量重嵌入' }}
        </el-button>

        <div v-if="lastCount !== null" class="last-result">
          <el-tag type="success" effect="plain">上次处理 {{ lastCount }} 条</el-tag>
          <span class="bn-text-muted">{{ lastRunAt }}</span>
        </div>
      </div>

      <el-alert
        v-if="running"
        type="warning"
        :closable="false"
        show-icon
        class="running-tip"
        title="任务执行中"
        description="全量嵌入为同步长任务，请保持页面开启；数据量较大时可能触发请求超时，可稍后在日志中确认执行结果。"
      />
    </div>

    <div class="bn-card notes-card">
      <h3 class="notes-title">检索链路说明</h3>
      <ul class="notes-list">
        <li>检索时先做 HyDE 改写，再向量召回 Top-8</li>
        <li>同时走 MySQL 关键词召回，RRF 融合后取 Top-5</li>
        <li>融合结果注入 System Prompt，由 DeepSeek 生成回答</li>
        <li>相似度阈值与 topK 可在 application.yml 的 booknest.zvector 下调整</li>
      </ul>
    </div>
  </div>
</template>

<style scoped>
.embedding-page {
  display: flex;
  flex-direction: column;
  gap: 14px;
  max-width: 760px;
}

.card-title {
  font-size: 15.5px;
  margin-bottom: 6px;
}

.bn-card-desc {
  font-size: 13px;
  line-height: 1.75;
  margin-bottom: 16px;
}

.tip {
  margin-bottom: 18px;
}

.pipeline {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.step {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 11px 14px;
  border: 1px solid var(--bn-border);
  border-radius: var(--bn-radius-sm);
  background: #faf8f5;
  flex: 1;
  min-width: 170px;
}

.step-index {
  width: 24px;
  height: 24px;
  border-radius: 50%;
  background: var(--bn-primary);
  color: #fff;
  font-size: 12.5px;
  font-weight: 700;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.step-title {
  font-size: 13.5px;
  font-weight: 600;
}

.step-desc {
  font-size: 11.5px;
  color: var(--bn-text-muted);
  margin-top: 1px;
}

.step-arrow {
  color: #c8bdb1;
}

.action-row {
  display: flex;
  align-items: center;
  gap: 16px;
  flex-wrap: wrap;
}

.last-result {
  display: flex;
  align-items: center;
  gap: 9px;
  font-size: 12.5px;
}

.running-tip {
  margin-top: 16px;
}

.notes-card {
  padding: 18px 20px;
}

.notes-title {
  font-size: 14.5px;
  margin-bottom: 10px;
}

.notes-list {
  list-style: disc;
  padding-left: 20px;
  font-size: 13px;
  color: var(--bn-text-sub);
  line-height: 1.9;
}
</style>
