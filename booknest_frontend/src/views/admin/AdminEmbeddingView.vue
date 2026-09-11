<script setup>
/**
 * 向量库管理：手动触发一次「清空 + 重写热门帖」的全量重建
 */
import { ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as adminApi from '@/api/admin'

const running = ref(false)
const lastResult = ref(null)
const lastRunAt = ref('')

async function run() {
  try {
    await ElMessageBox.confirm(
      '将清空现有向量集合，然后重新拉取点赞量最高的热门帖写入，整个过程耗时取决于 embedding 接口速率，确定继续吗？',
      '重建 RAG 索引',
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
    const vo = await adminApi.rebuildRagIndex()
    lastResult.value = vo
    lastRunAt.value = new Date().toLocaleString('zh-CN')
    ElMessage.success(`重建完成：${vo?.postCount ?? 0} 篇 / ${vo?.chunkCount ?? 0} 个分块`)
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
        RAG 检索只用「点赞量最高的热门帖」做语料。系统会按配置的周期自动重建一次索引：
        先把集合清空，再把当前热门帖重新分块写入。改了分块策略、换了 embedding 模型，
        或想立刻让新热门帖进索引时，可以在这里手动触发。
      </p>

      <el-alert
        type="info"
        :closable="false"
        show-icon
        class="tip"
        title="前置条件"
        description="需设置 QDRANT_VECTORSTORE_TYPE=qdrant 并正确填写 Qdrant 地址与 DASHSCOPE_API_KEY，否则执行将失败。"
      />

      <div class="pipeline">
        <div class="step">
          <span class="step-index">1</span>
          <div>
            <p class="step-title">清空集合</p>
            <p class="step-desc">删掉旧向量，不留重复数据</p>
          </div>
        </div>
        <div class="step-arrow"><el-icon><Right /></el-icon></div>

        <div class="step">
          <span class="step-index">2</span>
          <div>
            <p class="step-title">取热门帖</p>
            <p class="step-desc">按点赞量取前 N 篇已发布且已过审的帖子</p>
          </div>
        </div>
        <div class="step-arrow"><el-icon><Right /></el-icon></div>

        <div class="step">
          <span class="step-index">3</span>
          <div>
            <p class="step-title">分块向量化</p>
            <p class="step-desc">正文分块后由 text-embedding-v3 生成向量</p>
          </div>
        </div>
        <div class="step-arrow"><el-icon><Right /></el-icon></div>

        <div class="step">
          <span class="step-index">4</span>
          <div>
            <p class="step-title">写入 Qdrant</p>
            <p class="step-desc">连同 postId / 点赞量等元数据一起入库</p>
          </div>
        </div>
      </div>

      <el-divider />

      <div class="action-row">
        <el-button type="primary" :loading="running" :disabled="running" @click="run">
          <el-icon v-if="!running" style="margin-right: 4px"><Refresh /></el-icon>
          {{ running ? '正在执行，请勿离开页面…' : '立即重建索引' }}
        </el-button>

        <div v-if="lastResult" class="last-result">
          <el-tag type="success" effect="plain">
            上次：{{ lastResult.postCount ?? 0 }} 篇 / {{ lastResult.chunkCount ?? 0 }} 分块
          </el-tag>
          <el-tag v-if="lastResult.costMs" type="info" effect="plain">
            耗时 {{ (lastResult.costMs / 1000).toFixed(1) }}s
          </el-tag>
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
        description="重建为同步长任务，请保持页面开启；数据量较大时可能触发请求超时，可稍后在日志中确认执行结果。"
      />
    </div>

    <div class="bn-card notes-card">
      <h3 class="notes-title">运行机制说明</h3>
      <ul class="notes-list">
        <li>默认每 30 天重建一次，周期由 RAG_REFRESH_INTERVAL 控制（ISO-8601 时长或毫秒）</li>
        <li>首次重建在启动后由 RAG_REFRESH_INITIAL_DELAY 指定的时间触发，默认 2 分钟</li>
        <li>不做增量维护：新帖子最长延迟一个周期才会被检索到</li>
        <li>重建期间如有另一个重建在执行，本次触发会被跳过</li>
        <li>语料范围由 RAG_HOT_TOP_N 控制，检索时也只在当前热门帖范围内召回</li>
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
