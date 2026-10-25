import vue from '@vitejs/plugin-vue'
import { defineConfig } from 'vite'
import Components from 'unplugin-vue-components/vite'
import { AntDesignVueResolver } from 'unplugin-vue-components/resolvers'
import AutoImport from 'unplugin-auto-import/vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [
    vue(),
    // 按需引入 ant-design-vue（4.x CSS-in-JS，无需 importStyle）
    Components({
      resolvers: [
        AntDesignVueResolver({
          importStyle: false, // 4.x 用 CSS-in-JS 自动处理样式
          cjs: true,          // 规避 Vite 8 严格 ESM 目录导入问题
        }),
      ],
      dts: 'src/types/components.d.ts',
    }),
    // 自动导入 API（message/Modal 等，减少手动 import）
    AutoImport({
      imports: ['vue', 'vue-router'],
      dts: 'src/types/auto-imports.d.ts',
    }),
  ],
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
