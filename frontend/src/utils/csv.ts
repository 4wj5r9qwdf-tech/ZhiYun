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
