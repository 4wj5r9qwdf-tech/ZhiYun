// 文件用途：按系统显示精度格式化单耗、单价、成本和百分比，并提供无浮点误差的十进制字符串求和。
// 文件版本：1.0.0
// 功能目的：按系统显示精度格式化单耗、单价、成本和百分比，并提供无浮点误差的十进制字符串求和。
// 设计思路：显示精度只影响只读展示；业务原值仍用于编辑、保存和服务端计算，转换使用字符串与 BigInt。
// 实现方法：读取并校验精度配置；字符串计算舍入与微量提示；求和时统一小数位后精确相加。
// 关键约束：显示后的字符串不得回写或替代算料原值；非法输入必须保留或显式拒绝。
// 依赖与引用：Docs/规范/开发规范.md；sql/数字显示精度_20261120.sql；frontend/src/views/StyleBomManage.vue
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
// 系统显示规则只作用于只读值。编辑、保存和汇总使用原始十进制值。
// Display rules apply to read-only values; calculations and writes use source decimals.
export const precisionKey = 'system.display.decimals'
export const defaultPrecision = { bomUsage: 4, purchasePrice: 4, costPrice: 6, percentage: 2 }
export type PrecisionField = keyof typeof defaultPrecision
export const precisionLabels: Record<PrecisionField,string> = {
  bomUsage: 'BOM 单耗', purchasePrice: '采购单价', costPrice: '成本单价', percentage: '百分比'
}
/** 解析系统显示精度配置；键缺失或越界时回退默认值，不影响页面加载。 / Read display settings and fall back safely when a key is missing or invalid. */
export function readPrecision(value?: string) {
  const result = { ...defaultPrecision }
  try {
    const parsed = JSON.parse(value || '{}')
    for (const key of Object.keys(result) as PrecisionField[]) {
      if (Number.isInteger(parsed[key]) && parsed[key] >= 0 && parsed[key] <= 8) result[key] = parsed[key]
    }
  } catch { /* 旧环境尚未配置，使用业务默认值。 */ }
  return result
}
// 按十进制字符串四舍五入，避免大数经过二进制浮点后丢失尾数。
// Round the decimal string directly, preserving significant digits for large values.
export function displayDecimal(value: unknown, places: number, raw = false): string {
  const text = String(value ?? '').trim()
  if (!text) return '—'
  const match = /^(-?)(\d+)(?:\.(\d+))?$/.exec(text)
  if (raw || !match) return text
  // 单位档案或历史数据异常时也限制前端显示精度，避免超长格式化和负位数异常。
  // Clamp persisted precision defensively so malformed unit data cannot trigger invalid formatting.
  const safePlaces = Number.isInteger(places) ? Math.min(8, Math.max(0, places)) : 0
  const [, sign, whole, fraction = ''] = match
  const digits = fraction.padEnd(safePlaces + 1, '0')
  const scaled = BigInt(whole + digits.slice(0, safePlaces)) + (Number(digits[safePlaces] || 0) >= 5 ? 1n : 0n)
  const padded = scaled.toString().padStart(safePlaces + 1, '0')
  if (!scaled && /[1-9]/.test(text)) {
    const step = safePlaces ? '0.' + '0'.repeat(safePlaces-1) + '1' : '1'
    return sign ? '> -' + step : '< ' + step
  }
  return (scaled ? sign : '') + (safePlaces ? padded.slice(0,-safePlaces) + '.' + padded.slice(-safePlaces) : padded)
}
// 试算数量由服务端以十进制字符串返回；按字符串精确相加，避免浮点汇总误差。
// Demand totals arrive as decimal strings; sum them exactly without JavaScript floating-point error.
export function sumDecimal(values: unknown[]): string | null {
  const parts=values.map(value=>String(value ?? '').trim())
  if (parts.some(value=>!/^[-+]?\d+(\.\d+)?$/.test(value))) return null
  const scale=Math.max(0,...parts.map(value=>(value.split('.')[1] || '').length))
  const total=parts.reduce((sum,value)=>{ const negative=value.startsWith('-'); const [whole,fraction='']=value.replace(/^[+-]/,'').split('.'); const magnitude=BigInt(whole+fraction.padEnd(scale,'0')); return sum+(negative?-magnitude:magnitude) },0n)
  if (!scale) return total.toString()
  const negative=total<0n, magnitude=(negative?-total:total).toString().padStart(scale+1,'0')
  const formatted=(magnitude.slice(0,-scale)+'.'+magnitude.slice(-scale)).replace(/\.?0+$/,'')
  return (negative?'-':'')+formatted
}
