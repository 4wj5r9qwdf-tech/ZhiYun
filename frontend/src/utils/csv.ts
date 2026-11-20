// 文件用途：集中实现前端csv请求或共享处理能力。
// 文件版本：1.0.0
// 功能目的：集中实现前端csv请求或共享处理能力。
// 设计思路：抽离跨页面复用逻辑，以明确输入输出减少重复；不替代服务端校验。
// 实现方法：提供 API 封装、格式化工具或组合状态供业务页面调用。
// 关键约束：不得伪造业务成功或改变服务端数据口径。
// 依赖与引用：Docs/规范/前端规范.md；Docs/API/API文档.md
// 初始开发人员：JY（织云项目自研内容）
// 初始开发时间：待追溯（不以补录时间代替原始开发时间）
// 当前责任人：JY（项目维护责任；框架文件仅限项目改造范围）
// 最近修改人员：JY
// AI辅助工具：Codex（智能体）
// 最近修改时间：2026-11-20
// 修改标记：ZY-ERP-20261120-注释整理；补齐文件说明和业务逻辑注释，不改变运行行为
// 最近测试时间：本次整理未执行功能测试
// 测试人员：本次整理未执行
// 测试状态：待功能回归；历史测试记录待追溯
// 测试范围与记录位置：本次为注释/元信息整理；见 Docs/功能任务/ZY-ERP-20261120-既有功能注释整理.md
/** CSV 文本解析：支持 UTF-8 BOM、CRLF、引号内逗号/换行和双引号转义，编码始终保留为字符串。 */
export function parseCsv(text: string): { line: number; fields: string[] }[] {
  const input = text.replace(/^\uFEFF/, '')
  const records: { line: number; fields: string[] }[] = []
  let fields: string[] = [], field = '', quoted = false, closedQuote = false, line = 1, startLine = 1
  const pushField = () => { fields.push(field.trim()); field = ''; closedQuote = false }
  const pushRecord = () => { pushField(); if (fields.some(value => value !== '')) records.push({ line: startLine, fields }); fields = [] }
  for (let i = 0; i < input.length; i++) {
    const ch = input[i]!
    if (quoted) {
      if (ch === '"') {
        if (input[i + 1] === '"') { field += '"'; i++ } else { quoted = false; closedQuote = true }
      } else { field += ch; if (ch === '\n' || (ch === '\r' && input[i + 1] !== '\n')) line++ }
    } else if (ch === ',') pushField()
    else if (ch === '\r' || ch === '\n') {
      pushRecord()
      if (ch === '\r' && input[i + 1] === '\n') i++
      line++; startLine = line
    } else if (ch === '"') {
      if (field.trim() || closedQuote) throw new Error(`第 ${line} 行引号位置不正确`)
      field = ''; quoted = true
    } else {
      if (closedQuote && ch.trim()) throw new Error(`第 ${line} 行引号后只能接逗号或换行`)
      if (!closedQuote) field += ch
    }
  }
  if (quoted) throw new Error(`第 ${startLine} 行引号未闭合`)
  if (field || fields.length || closedQuote) pushRecord()
  return records
}
export function encodeCsv(rows: string[][]): string {
  return '\uFEFF' + rows.map(row => row.map(value => `"${String(value).replaceAll('"', '""')}"`).join(',')).join('\r\n')
}
