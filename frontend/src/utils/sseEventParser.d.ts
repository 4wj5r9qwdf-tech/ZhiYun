/*
 * 文件用途：声明 SSE 事件分帧工具的 TypeScript 调用契约。
 * 文件版本：1.0.0
 * 功能目的：为 Vue/TypeScript 调用者提供完整事件块、未完成缓冲区和行拆分函数的类型。
 * 设计思路：声明与同目录 JavaScript 实现同名配对，由 TypeScript 解析 .js 导入时读取。
 * 实现方法：导出解析结果结构及两个纯函数的参数、返回类型。
 * 关键约束：必须与 frontend/src/utils/sseEventParser.js 的导出保持一致。
 * 依赖与引用：frontend/src/utils/sseEventParser.js；frontend/src/components/ErpAiAssistant.vue
 * 初始开发人员：JY
 * 初始开发时间：2026-11-21
 * 当前责任人：JY
 * 最近修改人员：JY
 * AI辅助工具：Codex（智能体）
 * 最近修改时间：2026-11-21
 * 修改标记：ZY-ERP-20261121-MCP认证与SSE解析缺陷修正；补充SSE解析器类型声明
 * 最近测试时间：2026-11-21
 * 测试人员：JY
 * 测试状态：类型声明经 vue-tsc 前端构建通过
 * 测试范围与记录位置：frontend/tests/sseEventParser.test.js；Docs/功能任务/ZY-ERP-20261121-MCP认证与SSE解析缺陷修正.md
 */
export interface SseEventBlockBatch {
  blocks: string[]
  remainder: string
}

export function extractSseEventBlocks(buffer: string, final?: boolean): SseEventBlockBatch
export function splitSseEventLines(block: string): string[]
