<script setup>
const year = new Date().getFullYear()
</script>

<template>
  <footer class="bn-footer">
    <div class="bn-container footer-inner">
      <div class="footer-brand">
        <span class="logo-mark">书</span>
        <div>
          <p class="footer-title">BookNest · 书籍交流社区</p>
          <p class="footer-desc">以书为纽带，写下你的书评，建一份自己的书单。</p>
        </div>
      </div>
      <p class="footer-copy">© {{ year }} BookNest · 仅供学习交流使用</p>
    </div>
  </footer>
</template>

<style scoped>
.bn-footer {
  border-top: 1px solid var(--bn-border);
  background: #fff;
  padding: 26px 0;
  margin-top: auto;
}

.footer-inner {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 14px;
  text-align: center;
}

.footer-brand {
  display: flex;
  align-items: center;
  gap: 12px;
  text-align: left;
}

.logo-mark {
  width: 34px;
  height: 34px;
  border-radius: 9px;
  background: var(--bn-primary);
  color: #fff;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-weight: 700;
  font-size: 17px;
  flex-shrink: 0;
}

.footer-title {
  font-weight: 600;
  font-size: 14px;
}

.footer-desc {
  font-size: 12.5px;
  color: var(--bn-text-muted);
}

.footer-copy {
  font-size: 12px;
  color: var(--bn-text-muted);
}
</style>
