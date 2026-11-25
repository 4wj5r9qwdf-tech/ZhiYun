/*
 * 织云文件元信息
 * 文件版本：v1.0
 * 开发/编制人员：JY
 * AI协作：Codex
 * 开发时间：实际开发/编制时间未单独记录；首次纳入版本库：2026-10-24T15:05:55+08:00
 * 开发思路：实现“vite.config”对应前端职责，复用页面规范并保持与 API、权限契约一致。
 * 最近修改时间：2026-11-20T18:02:03+08:00
 * 修改人员：JY；Codex（元信息整理）
 * 修改标记：补录统一文件头元信息，未改动业务逻辑或接口契约。
 * 测试时间：2026-11-20T18:02:03+08:00
 * 测试人员：Codex（文件头元信息核对）
 * 测试状态：头部字段核对通过；本次未重跑业务功能测试，既有结果见对应功能验收记录。
 * 说明：织云 ERP 项目文件：frontend/vite.config.ts
 */

import vue from '@vitejs/plugin-vue'
import { defineConfig } from 'vite'
import Components from 'unplugin-vue-components/vite'
import { AntDesignVueResolver } from 'unplugin-vue-components/resolvers'
import AutoImport from 'unplugin-auto-import/vite'

// https://vite.dev/config/
export default defineConfig({
  build: {
    rolldownOptions: {
      output: {
        codeSplitting: {
          groups: [
            {
              name: 'vendor',
              test: /node_modules/,
              minSize: 100_000,
              maxSize: 450_000,
            },
          ],
        },
      },
    },
  },
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
    // 放行花生壳等公网域名（Host 校验白名单），开发期全放行
    allowedHosts: true,
    // 代理后端（避免跨域），/api/* → http://127.0.0.1:8080/*
    proxy: {
      '/api': {
        target: 'http://127.0.0.1:8080',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/api/, ''),
      },
      // MinIO 静态文件（公司档案图片等）：直连 5273 开发时也可显示，不经 nginx
      '/minio': {
        target: 'http://127.0.0.1:9000',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/minio/, ''),
      },
    },
  },
})
