/*
文件用途：验证 SSE 分帧解析对标准行结束符和跨网络分块边界的兼容性。
文件版本：1.0.0
功能目的：确保 LF、CRLF、CR、混合行结束符及被分块的 CRLF 都能得到正确事件边界。
设计思路：使用 Node 内置测试器调用生产解析器，验证事件内容和剩余缓冲区，不依赖浏览器或新增依赖。
实现方法：覆盖多事件连续输入、逐行拆分以及分隔符恰好跨块的场景。
关键约束：每个完整事件只产生一次；未完整事件必须保留在 remainder。
依赖与引用：frontend/src/utils/sseEventParser.js；Docs/规范/开发规范.md
初始开发人员：JY
初始开发时间：2026-11-21
当前责任人：JY
最近修改人员：JY
AI辅助工具：Codex（智能体）
最近修改时间：2026-11-21
修改标记：ZY-ERP-20261121-MCP认证与SSE解析缺陷修正；添加SSE分隔符边界测试
最近测试时间：2026-11-21
测试人员：JY
测试状态：Node 内置测试通过（3/3）
测试范围与记录位置：node --test tests/sseEventParser.test.js；Docs/功能任务/ZY-ERP-20261121-MCP认证与SSE解析缺陷修正.md
*/
import assert from "node:assert/strict"
import test from "node:test"

import { extractSseEventBlocks, splitSseEventLines } from "../src/utils/sseEventParser.js"

test("解析 LF、CRLF、CR 和混合行结束符分隔的多个事件", () => {
  const input = "event: text\r\ndata: first\r\n\r\nevent: text\ndata: second\n\nevent: done\rdata: third\r\r"
  const parsed = extractSseEventBlocks(input, true)

  assert.deepEqual(parsed.blocks, [
    "event: text\r\ndata: first",
    "event: text\ndata: second",
    "event: done\rdata: third"
  ])
  assert.equal(parsed.remainder, "")
  assert.deepEqual(splitSseEventLines(parsed.blocks[2]), ["event: done", "data: third"])
})

test("将跨网络分块的 CRLF 保留到下一块后再识别", () => {
  const first = extractSseEventBlocks("event: text\r\ndata: partial\r\n\r")

  assert.deepEqual(first.blocks, [])
  assert.equal(first.remainder, "event: text\r\ndata: partial\r\n\r")

  const second = extractSseEventBlocks(first.remainder + "\nevent: done\n\n")

  assert.deepEqual(second.blocks, ["event: text\r\ndata: partial", "event: done"])
  assert.equal(second.remainder, "")
})

test("流结束时刷新末尾的单独 CR 分隔符", () => {
  const parsed = extractSseEventBlocks("event: text\rdata: final\r\r", true)

  assert.deepEqual(parsed.blocks, ["event: text\rdata: final"])
  assert.equal(parsed.remainder, "")
})
