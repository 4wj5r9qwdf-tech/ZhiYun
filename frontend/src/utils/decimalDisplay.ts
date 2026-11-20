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
