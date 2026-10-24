import vue from '@vitejs/plugin-vue'
import { defineConfig } from 'vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5273,
    strictPort: true, // 端口被占直接报错，不自动跳端口（固定 5273）
    // 代理后端（避免跨域），/api/* → http://127.0.0.1:8080/*
    proxy: {
      '/api': {
        target: 'http://127.0.0.1:8080',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/api/, ''),
      },
    },
  },
})
