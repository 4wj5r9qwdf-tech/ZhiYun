import { parseCsv } from './csv'
export type WarehouseImportSource = { warehouse_id: string; warehouse_code: string; status: string }
export type LocationImportSource = { location_id: string; warehouse_id: string; parent_location_id?: string | null; location_level: number; location_code: string; location_status: string; external_location_code?: string | null }
export type LocationPayload = { warehouseId: string; parentLocationId?: string; locationLevel: number; locationCode: string; locationName: string; locationStatus: string; remark: string; agvReachable: boolean; capacityLimit?: string; capacityUnit: string; externalLocationCode: string; entrySource: string }
export type LocationImportRow = { line: number; code: string; name: string; error: string; warehouseCode: string; zoneCode: string; rackCode: string; source: string[]; payload?: LocationPayload; outcome: 'pending' | 'success' | 'failed' }
const names: Record<number, string> = { 1: '库区', 2: '货架', 3: '货位' }
export function importHeaders(level: number): string[] {
  if (!names[level]) throw new Error('请选择库区、货架或货位')
  return ['仓库编码', ...(level > 1 ? ['库区编码'] : []), ...(level > 2 ? ['货架编码'] : []), `${names[level]}编码`, `${names[level]}名称`, '状态', '备注', 'AGV可达', '容量上限', '容量单位', '外部位置映射码']
}
const normalized = (value: string) => value.trim().toLocaleLowerCase()
export function prepareLocationImport(text: string, level: number, warehouses: WarehouseImportSource[], locations: LocationImportSource[], autoCodeEnabled = true): { headers: string[]; rows: LocationImportRow[] } {
  const records = parseCsv(text)
  if (records.length < 2) throw new Error('文件没有可导入的数据，请先下载并填写模板')
  if (records.length > 1001) throw new Error('每次最多导入 1000 条，请分批导入')
  const headers = records[0]!.fields
  const expected = importHeaders(level)
  const required = expected.slice(0, level + 2)
  if (new Set(headers).size !== headers.length) throw new Error('模板包含重复列名')
  const missing = required.filter(name => !headers.includes(name))
  if (missing.length) throw new Error(`缺少必填列：${missing.join('、')}；请使用当前类型的模板`)
  const unknown = headers.filter(name => !expected.includes(name) && name !== '失败原因')
  if (unknown.length) throw new Error(`无法识别列：${unknown.join('、')}；请使用当前类型的模板`)
  const seen = new Set<string>(), seenExternal = new Set<string>()
  const rows = records.slice(1).map(record => {
    const get = (name: string) => record.fields[headers.indexOf(name)]?.trim() || ''
    const errors: string[] = []
    if (record.fields.length !== headers.length) errors.push('列数与表头不一致')
    const code = get(`${names[level]}编码`), name = get(`${names[level]}名称`)
    if ((!code && !autoCodeEnabled) || code.length > 80) errors.push(autoCodeEnabled ? `${names[level]}编码不能超过80字` : `${names[level]}编码必填且不超过80字`)
    if (!name || name.length > 200) errors.push(`${names[level]}名称必填且不超过200字`)
    if (get('备注').length > 1000) errors.push('备注不能超过1000字')
    const warehouse = warehouses.find(item => normalized(item.warehouse_code) === normalized(get('仓库编码')))
    if (!warehouse || warehouse.status !== '0') errors.push('仓库编码不存在或仓库已停用')
    const zone = level > 1 && warehouse ? locations.find(item => item.warehouse_id === warehouse.warehouse_id && item.location_level === 1 && normalized(item.location_code) === normalized(get('库区编码'))) : undefined
    if (level > 1 && (!zone || zone.location_status !== 'active')) errors.push('库区编码不存在、不属于该仓库或已停用')
    const rack = level > 2 && warehouse ? locations.find(item => item.warehouse_id === warehouse.warehouse_id && item.location_level === 2 && normalized(item.location_code) === normalized(get('货架编码'))) : undefined
    if (level > 2 && (!rack || rack.location_status !== 'active' || rack.parent_location_id !== zone?.location_id)) errors.push('货架编码不存在、不属于该库区或已停用')
    if (warehouse && code) {
      const key = `${warehouse.warehouse_id}:${normalized(code)}`
      if (seen.has(key)) errors.push('文件内同一仓库的编码重复')
      if (locations.some(item => item.warehouse_id === warehouse.warehouse_id && normalized(item.location_code) === normalized(code))) errors.push('该仓库中已存在此编码')
      seen.add(key)
    }
    const stateText = get('状态') || '可用'
    const status = ({ 可用: 'active', 停用: 'disabled', 维护中: 'maintenance' } as Record<string, string>)[stateText]
    if (!status) errors.push('状态仅支持可用、停用、维护中')
    const reachable = get('AGV可达') || '是'
    if (!['是', '否', '可达', '不可达'].includes(reachable)) errors.push('AGV可达仅支持是、否、可达、不可达')
    const capacityText = get('容量上限'), capacity = capacityText || undefined
    if (capacity !== undefined && (!/^\d+(\.\d{1,4})?$/.test(capacityText) || capacityText.split('.')[0]!.replace(/^0+/, '').length > 14)) errors.push('容量上限须为非负数，整数最多14位、小数最多4位')
    if (capacity !== undefined && !get('容量单位')) errors.push('填写容量上限时必须填写容量单位')
    if (get('容量单位').length > 30) errors.push('容量单位不能超过30字')
    const external = get('外部位置映射码')
    if (external.length > 120) errors.push('外部位置映射码不能超过120字')
    if (warehouse && external) {
      const key = `${warehouse.warehouse_id}:${normalized(external)}`
      if (seenExternal.has(key) || locations.some(item => item.warehouse_id === warehouse.warehouse_id && normalized(item.external_location_code || '') === normalized(external))) errors.push('外部位置映射码已存在或在文件中重复')
      seenExternal.add(key)
    }
    const payload: LocationPayload = { warehouseId: warehouse?.warehouse_id || '', parentLocationId: level === 3 ? rack?.location_id : zone?.location_id, locationLevel: level, locationCode: code, locationName: name, locationStatus: status || 'active', remark: get('备注'), agvReachable: ['是', '可达'].includes(reachable), capacityLimit: capacity, capacityUnit: get('容量单位'), externalLocationCode: external, entrySource: 'csv_import' }
    return { line: record.line, code, name, error: errors.join('；'), warehouseCode: get('仓库编码'), zoneCode: get('库区编码'), rackCode: get('货架编码'), source: record.fields, payload, outcome: 'pending' as const }
  })
  return { headers, rows }
}
/** 成功记录不再提交；校验失败行跳过，服务端逐条校验，部分失败保留原因。 */
export async function submitLocationImport(rows: LocationImportRow[], create: (payload: LocationPayload) => Promise<{ code: number; msg?: string; data?: string }>, onProgress: (done: number, total: number) => void = () => {}) {
  const candidates = rows.filter(row => !row.error && row.outcome === 'pending' && row.payload)
  let done = 0
  onProgress(0, candidates.length)
  for (const row of candidates) {
    try {
      const result = await create(row.payload!)
      if (result.code !== 200) throw new Error(result.msg || '保存失败')
      if (!row.code && result.data) row.code = result.data
      row.outcome = 'success'
    } catch (error: any) {
      row.outcome = 'failed'
      row.error = error?.response?.data?.msg || (error?.code === 'ECONNABORTED' ? '请求超时，请查询是否已保存后再重试' : error.message) || '保存失败'
    }
    onProgress(++done, candidates.length)
  }
  return { succeeded: rows.filter(row => row.outcome === 'success').length, failures: rows.filter(row => row.outcome !== 'success').map(row => ({ name: `第${row.line}行 ${row.code} ${row.name}`, reason: row.error || '未处理' })) }
}
