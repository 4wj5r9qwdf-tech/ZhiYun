/*
 * 织云文件元信息
 * 文件版本：v1.0
 * 文件用途：前端 Vite 开发服务器与构建工具配置。
 * 功能目的：统一 Vue 插件、组件/导入辅助和本地开发服务行为。
 * 设计思路：将开发期工具插件与打包配置集中在 Vite 配置入口。
 * 实现方法：通过 defineConfig 注册 Vue、自动导入、组件解析及开发代理等配置。
 * 关键约束：代理目标应来自开发环境约定；构建插件调整须核对前端构建结果和类型检查。
 * 依赖与引用：frontend/package.json；Docs/规范/前端规范.md；Vite 与相关插件。
 * 初始开发人员：JY
 * 初始开发时间：实际开发/编制时间未单独记录；首次纳入版本库：2026-10-24T15:05:55+08:00
 * 当前责任人：JY（项目维护责任）
 * 最近修改人员：JY（Codex辅助）
 * AI辅助工具：Codex（智能体）
 * 最近修改时间：2026-11-25
 * 修改标记：ZY-ERP-20261125-文件头补齐；按当前开发规范补全元信息字段，仅调整注释。
 * 测试时间：2026-11-20T18:02:03+08:00
 * 测试人员：Codex（文件头元信息核对）
 * 测试状态：头部字段核对通过；本次未重跑业务功能测试，既有结果见对应功能验收记录。
 * 最近测试时间：2026-11-25
 * 最近测试人员：JY（测试责任人）；AI辅助工具：Codex（静态核对）
 * 最近测试状态：通过；检查文件头注释闭合、Git 差异检查通过且正文逐字节不变；未执行构建或业务运行测试。
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
