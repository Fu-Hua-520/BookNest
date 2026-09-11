import { fileURLToPath, URL } from 'node:url'
import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'

// BookNest 前端构建配置
// 后端默认 http://localhost:8080，开发期通过 Vite 代理转发，避免跨域与 CORS 预检
//
// 重要：vite.config.js 在 Node 侧执行，比 .env 文件的加载时机更早，
// 因此 process.env.VITE_xxx 在这里恒为 undefined（Vite 不会把 .env 注入 process.env）。
// 必须用 loadEnv(mode, envDir, prefixes) 显式读取。
// 第三个参数传 '' 表示不做前缀过滤 —— 这里读的 VITE_API_TARGET 只用于本地代理，
// 不需要暴露给浏览器（暴露给浏览器的变量仍须以 VITE_ 开头）。
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  const apiTarget = env.VITE_API_TARGET || 'http://localhost:8080'

  return {
    plugins: [vue()],
    resolve: {
      alias: {
        '@': fileURLToPath(new URL('./src', import.meta.url))
      }
    },
    server: {
      port: 5173,
      host: '127.0.0.1',
      proxy: {
        // 后端业务接口
        '/api': {
          target: apiTarget,
          changeOrigin: true,
          // 剥掉 /api 前缀：后端接口无 context-path，形如 /user/login。
          // 与生产环境 nginx 的 `proxy_pass http://app:8080/;`（尾斜杠同样剥前缀）保持一致，
          // 否则开发期会请求到 /api/user/login 而 404。
          rewrite: (path) => path.replace(/^\/api/, ''),
          // SSE 流式响应必须关闭代理缓冲，否则事件会被攒批
          configure(proxy) {
            proxy.on('proxyReq', (proxyReq) => {
              proxyReq.setHeader('Connection', 'keep-alive')
            })
          }
        },
        // 私信 WebSocket
        '/ws': {
          target: apiTarget,
          changeOrigin: true,
          ws: true
        }
      }
    },
    build: {
      outDir: 'dist',
      sourcemap: false,
      chunkSizeWarningLimit: 1500
    }
  }
})
