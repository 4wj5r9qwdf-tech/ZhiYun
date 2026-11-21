/*
 * 文件用途：将增量到达的 SSE 文本缓冲区解析为完整事件块。
 * 文件版本：1.0.0
 * 功能目的：兼容标准 LF、CRLF、CR 行结束符，并安全处理网络分块切断分隔符的情况。
 * 设计思路：空行是两个连续行结束符；非最终分块末尾的 CR 暂缓判定，避免把被切开的 CRLF 误当成单独 CR。
 * 实现方法：扫描完整分隔符并返回已完成事件及剩余缓冲区；流结束时通过 final 标志处理末尾 CR。
 * 关键约束：不得丢失分隔符两侧字符；事件回调只接收完整分帧，EOF 时由调用方处理未终止的最后一帧。
 * 依赖与引用：frontend/src/components/ErpAiAssistant.vue；Docs/规范/开发规范.md
 * 初始开发人员：JY
 * 初始开发时间：2026-11-21
 * 当前责任人：JY
 * 最近修改人员：JY
 * AI辅助工具：Codex（智能体）
 * 最近修改时间：2026-11-21
 * 修改标记：ZY-ERP-20261121-MCP认证与SSE解析缺陷修正；新增可单测的SSE分帧解析器
 * 最近测试时间：2026-11-21
 * 测试人员：JY
 * 测试状态：SSE 分帧 Node 测试 3/3 通过
 * 测试范围与记录位置：frontend/tests/sseEventParser.test.js；Docs/功能任务/ZY-ERP-20261121-MCP认证与SSE解析缺陷修正.md
 */

/**
 * 从接收缓冲区取出所有已由两个连续 SSE 行结束符封闭的事件。
 * 非最终读取若以 CR 结尾，会暂留该字符，因为下一块的 LF 可能仍属于 CRLF。
 */
export function extractSseEventBlocks(buffer, final = false) {
  const scannable = !final && buffer.endsWith("\r")
    ? buffer.slice(0, -1)
    : buffer
  const blocks = []
  let consumed = 0
  let previousEndingStart = -1
  let index = 0

  while (index < scannable.length) {
    const current = scannable[index]
    let endingLength = 0

    // CRLF 是一个行结束符；只有单独 CR 或 LF 才占一个字符。
    if (current === "\r") endingLength = scannable[index + 1] === "\n" ? 2 : 1
    else if (current === "\n") endingLength = 1

    if (endingLength === 0) {
      previousEndingStart = -1
      index += 1
      continue
    }

    // 两个相邻的完整行结束符表示一个 SSE 空行，分隔前面的事件块。
    if (previousEndingStart >= 0) {
      blocks.push(scannable.slice(consumed, previousEndingStart))
      consumed = index + endingLength
      previousEndingStart = -1
    } else {
      previousEndingStart = index
    }
    index += endingLength
  }

  return {
    blocks,
    remainder: buffer.slice(consumed)
  }
}

/** 按 SSE 支持的三种行结束符拆分单个事件块。 */
export function splitSseEventLines(block) {
  return block.split(/\r\n|\r|\n/)
}
