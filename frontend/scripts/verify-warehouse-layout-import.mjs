/** 本次功能专项检查：CSV复杂输入、归属与重复校验、部分失败、连续新增缓存；可选真实接口链路。 */
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import ts from 'typescript'
import { test } from 'node:test'
const root = new URL('../src/utils/', import.meta.url)
async function moduleUrl(name, replacements = {}) {
  let code = ts.transpileModule(await readFile(new URL(name + '.ts', root), 'utf8'), { compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.ES2022 } }).outputText
  for (const [from, to] of Object.entries(replacements)) code = code.replaceAll(`'${from}'`, JSON.stringify(to)).replaceAll(`"${from}"`, JSON.stringify(to))
  return 'data:text/javascript;base64,' + Buffer.from(code).toString('base64')
}
const csvUrl = await moduleUrl('csv')
const { parseCsv, encodeCsv } = await import(csvUrl)
const { importHeaders, prepareLocationImport, submitLocationImport } = await import(await moduleUrl('warehouseLayoutImport', { './csv': csvUrl }))
const { createSavedFormDefaults } = await import(await moduleUrl('savedFormDefaults'))
const warehouses = [{ warehouse_id: 'w1', warehouse_code: 'WH001', status: '0' }, { warehouse_id: 'w2', warehouse_code: 'WH002', status: '0' }, { warehouse_id: 'w3', warehouse_code: 'WH003', status: '1' }]
const locations = [
  { location_id: 'z1', warehouse_id: 'w1', location_level: 1, location_code: 'Z001', location_status: 'active' },
  { location_id: 'z2', warehouse_id: 'w1', location_level: 1, location_code: 'Z002', location_status: 'active' },
  { location_id: 'r1', warehouse_id: 'w1', parent_location_id: 'z1', location_level: 2, location_code: 'R001', location_status: 'active' },
]
const names = { 1: '库区', 2: '货架', 3: '货位' }
function source(level, fields = {}) {
  return { 仓库编码: 'WH001', 库区编码: 'Z001', 货架编码: 'R001', 货位编码: '00001', [`${names[level]}编码`]: '00001', [`${names[level]}名称`]: '测试档案', 状态: '可用', ...fields }
}
function prepare(level, data, ws = warehouses, ls = locations) {
  const headers = importHeaders(level)
  return prepareLocationImport(encodeCsv([headers, ...data.map(row => headers.map(name => row[name] || ''))]), level, ws, ls).rows
}
await test('CSV保留BOM后的字符串编码、逗号、引号、换行和真实行号', () => {
  const records = parseCsv('\uFEFF编码,备注\r\n00001,"面料,\"\"棉\"\"\r\n第二行"\r\n\r\n00002,正常\r\n')
  assert.equal(records[1].fields[0], '00001'); assert.equal(records[1].fields[1], '面料,"棉"\r\n第二行'); assert.equal(records[2].line, 5)
})
await test('CSV结构错误必须在确认导入前拒绝', () => {
  for (const text of ['a\n"未闭合', 'a\n未"转义', 'a\n"值"多余']) assert.throws(() => parseCsv(text), /引号/)
})
await test('库区、货架、货位模板对应正确父级，并保留前导零', () => {
  for (const level of [1, 2, 3]) {
    const row = prepare(level, [source(level)])[0]
    assert.equal(row.error, ''); assert.equal(row.code, '00001'); assert.equal(row.payload.locationLevel, level)
    assert.equal(row.payload.parentLocationId, level === 1 ? undefined : level === 2 ? 'z1' : 'r1')
    assert.equal(row.payload.entrySource, 'csv_import')
  }
})
await test('错误类型、缺失或重复表头不允许导入', () => {
  assert.throws(() => prepareLocationImport('编码,名称\nx,y', 1, warehouses, locations), /缺少必填列/)
  assert.throws(() => prepareLocationImport('仓库编码,库区编码,库区编码\nx,y,z', 1, warehouses, locations), /重复列名/)
  assert.throws(() => prepareLocationImport('仓库编码,库区编码,库区名称,未知列\nx,y,z,k', 1, warehouses, locations), /无法识别列/)
})
await test('空文件和超出批量限制在解析阶段拒绝', () => {
  assert.throws(() => prepareLocationImport('仓库编码,库区编码,库区名称', 1, warehouses, locations), /没有可导入/)
  assert.throws(() => prepareLocationImport('仓库编码,库区编码,库区名称\n' + 'WH001,A,名称\n'.repeat(1001), 1, warehouses, locations), /1000/)
})
await test('校验仓库、库区和货架的归属与可用状态', () => {
  assert.match(prepare(1, [source(1, { 仓库编码: 'WH003' })])[0].error, /仓库已停用/)
  assert.match(prepare(2, [source(2, { 仓库编码: 'WH002' })])[0].error, /库区编码/)
  assert.match(prepare(3, [source(3, { 库区编码: 'Z002' })])[0].error, /货架编码/)
  assert.match(prepare(3, [source(3)], warehouses, locations.map(row => row.location_id === 'r1' ? { ...row, location_status: 'disabled' } : row))[0].error, /货架编码/)
})
await test('同仓跨类型已有编码和文件内大小写重复都会被拒绝', () => {
  assert.match(prepare(1, [source(1, { 库区编码: 'r001' })])[0].error, /已存在此编码/)
  const rows = prepare(1, [source(1, { 库区编码: 'A' }), source(1, { 库区编码: 'a' })])
  assert.equal(rows[0].error, ''); assert.match(rows[1].error, /文件内/)
  assert.equal(prepare(1, [source(1, { 库区编码: 'A' }), source(1, { 仓库编码: 'WH002', 库区编码: 'a' })])[1].error, '')
})
await test('空状态默认可用，非法状态和容量缺单位明确失败', () => {
  assert.equal(prepare(1, [source(1, { 状态: '' })])[0].payload.locationStatus, 'active')
  assert.match(prepare(1, [source(1, { 状态: '启用' })])[0].error, /状态仅支持/)
  assert.match(prepare(1, [source(1, { 容量上限: '2.5' })])[0].error, /容量单位/)
  assert.match(prepare(1, [source(1, { 容量上限: '-1', 容量单位: '箱' })])[0].error, /非负数/)
  assert.match(prepare(1, [source(1, { 容量上限: '0.12345', 容量单位: '箱' })])[0].error, /小数最多4位/)
})
await test('容量保持原始十进制字符串，避免导入高精度数值时JS丢失精度', () => {
  const row = prepare(3, [source(3, { 容量上限: '99999999999999.9999', 容量单位: '米' })])[0]
  assert.equal(row.error, ''); assert.equal(row.payload.capacityLimit, '99999999999999.9999')
})
await test('外部映射重复和字段长度有清楚的行级错误', () => {
  const rows = prepare(1, [source(1, { 库区编码: 'A', 外部位置映射码: 'MAP' }), source(1, { 库区编码: 'B', 外部位置映射码: 'map' })])
  assert.match(rows[1].error, /外部位置映射码/)
  assert.match(prepare(1, [source(1, { 库区名称: '名'.repeat(201) })])[0].error, /200字/)
})
await test('下载失败记录可以保留失败原因列，修正后重新校验', () => {
  const headers = [...importHeaders(1), '失败原因']
  const row = source(1)
  assert.equal(prepareLocationImport(encodeCsv([headers, headers.map(name => name === '失败原因' ? '之前失败' : row[name] || '')]), 1, warehouses, locations).rows[0].error, '')
})
await test('逐条提交保留部分成功和服务端错误，跳过校验失败且不重发成功记录', async () => {
  const rows = prepare(1, [source(1, { 库区编码: 'A' }), source(1, { 库区编码: 'B' }), source(1, { 库区编码: 'C', 仓库编码: '不存在' })])
  const calls = [], progress = []
  const submit = async payload => { calls.push(payload.locationCode); return payload.locationCode === 'A' ? { code: 200 } : { code: 500, msg: '其他用户已使用该编码' } }
  const result = await submitLocationImport(rows, submit, (done, total) => progress.push([done, total]))
  assert.equal(result.succeeded, 1); assert.equal(result.failures.length, 2); assert.match(rows[1].error, /其他用户/)
  assert.deepEqual(calls, ['A', 'B']); assert.deepEqual(progress, [[0, 2], [1, 2], [2, 2]])
  await submitLocationImport(rows, submit); assert.deepEqual(calls, ['A', 'B'])
})
await test('网络异常也反馈到对应行，超时提示先查询是否已保存', async () => {
  const rows = prepare(1, [source(1)])
  const result = await submitLocationImport(rows, async () => { throw Object.assign(new Error('timeout'), { code: 'ECONNABORTED' }) })
  assert.equal(result.succeeded, 0); assert.match(result.failures[0].reason, /查询是否已保存/)
})
await test('连续新增保留业务字段且清除主键；用户和档案类型隔离', () => {
  const data = new Map(), storage = { getItem: key => data.get(key) || null, setItem: (key, value) => data.set(key, value) }
  const empty = () => ({ id: '', code: '', name: '', warehouseId: '', parentId: '', remark: '' })
  const first = createSavedFormDefaults(storage, 'user1:zone', empty, ['id'])
  first.save({ id: 'existing-id', code: 'A01', name: '面料区', warehouseId: 'w1', parentId: '', remark: '保留备注' })
  assert.deepEqual(first.load(), { id: '', code: 'A01', name: '面料区', warehouseId: 'w1', parentId: '', remark: '保留备注' })
  assert.deepEqual(createSavedFormDefaults(storage, 'user1:rack', empty, ['id']).load(), empty())
  assert.deepEqual(createSavedFormDefaults(storage, 'user2:zone', empty, ['id']).load(), empty())
  assert.deepEqual(empty(), { id: '', code: '', name: '', warehouseId: '', parentId: '', remark: '' })
})
await test('无效缓存和不可用存储不影响正常业务，未知字段不会带入表单', () => {
  const empty = () => ({ id: null, code: '' })
  const storage = { getItem: () => '{坏数据', setItem: () => { throw new Error('quota') } }
  const defaults = createSavedFormDefaults(storage, 'key', empty, ['id'])
  assert.deepEqual(defaults.load(), empty()); assert.doesNotThrow(() => defaults.save({ id: 'x', code: 'A' }))
  storage.getItem = () => '{"id":"x","code":"A","unknown":"不应复制"}'
  assert.deepEqual(defaults.load(), { id: null, code: 'A' })
})

if (process.argv.includes('--integration')) {
  await test('真实认证接口：三个CSV模板依次建档、部分失败及导入审计来源', async () => {
    const base = 'http://127.0.0.1:8080'
    const prefix = '__layout_import_' + crypto.randomUUID().replaceAll('-', '').slice(0, 12)
    const client = 'e5cd7e4891bf95d1d19206ce24a7b32e'
    let token = '', wid
    const ids = []
    async function request(method, path, body) {
      const response = await fetch(base + path, { method, headers: { 'Content-Type': 'application/json', clientid: client, ...(token ? { Authorization: 'Bearer ' + token } : {}) }, body: body === undefined ? undefined : JSON.stringify(body), signal: AbortSignal.timeout(15000) })
      return response.json()
    }
    const ok = result => { assert.equal(result.code, 200, result.msg); return result.data }
    try {
      assert.ok(process.env.SUPER_ADMIN_PASSWORD, '未设置接口验收所需密码环境变量')
      token = ok(await request('POST', '/auth/login', { clientId: client, grantType: 'password', username: process.env.SUPER_ADMIN_USERNAME || 'admin', password: process.env.SUPER_ADMIN_PASSWORD })).access_token
      ok(await request('POST', '/system/warehouse', { warehouseCode: prefix, warehouseName: prefix, inboundLocationLevel: 0, status: '0', tagIds: [] }))
      const warehouse = ok(await request('GET', '/system/warehouse/list?keyword=' + prefix)).find(row => row.warehouse_code === prefix)
      wid = String(warehouse.warehouse_id)
      const ws = [{ ...warehouse, warehouse_id: wid }], ls = []
      for (const level of [1, 2, 3]) {
        const fields = source(level, { 仓库编码: prefix, 库区编码: prefix + 'zone', 货架编码: prefix + 'rack', 货位编码: prefix + 'bin', [`${names[level]}名称`]: names[level] + '导入验收', 备注: '带逗号,双引号"和\n换行', 容量上限: level === 3 ? '12.5000' : '', 容量单位: level === 3 ? '箱' : '' })
        const rows = prepare(level, [fields], ws, ls)
        assert.equal(rows[0].error, '')
        const result = await submitLocationImport(rows, payload => request('POST', '/system/warehouse-location', payload))
        assert.equal(result.succeeded, 1)
        const stored = ok(await request('GET', `/system/warehouse-location/list?warehouseId=${wid}&level=${level}`))[0]
        ids.push(String(stored.location_id)); ls.push(stored)
        assert.equal(stored.remark, fields['备注'])
        if (level === 3) assert.equal(Number(stored.capacity_limit), 12.5)
      }
      const valid = prepare(3, [source(3, { 仓库编码: prefix, 库区编码: prefix + 'zone', 货架编码: prefix + 'rack', 货位编码: prefix + 'extra' })], ws, ls)
      // 在预览后模拟另一个请求已占用编码，确认服务端拒绝被逐行反馈。
      ok(await request('POST', '/system/warehouse-location', valid[0].payload))
      const extra = ok(await request('GET', `/system/warehouse-location/list?warehouseId=${wid}&level=3`)).find(row => row.location_code.endsWith('extra'))
      ids.push(String(extra.location_id))
      const duplicateResult = await submitLocationImport(valid, payload => request('POST', '/system/warehouse-location', payload))
      assert.equal(duplicateResult.succeeded, 0); assert.match(duplicateResult.failures[0].reason, /编码已存在/)
      const { execFileSync } = await import('node:child_process')
      const auditCount = execFileSync('sudo', ['-n', '-u', 'postgres', 'psql', '-d', 'zhizhun_erp', '-At', '-c', `SELECT count(DISTINCT oper_param::jsonb->>'action') FROM sys_oper_log WHERE oper_param LIKE '%${prefix}%' AND status=0 AND oper_param::jsonb->>'action' IN ('导入库区','导入货架','导入货位') AND oper_name IS NOT NULL AND oper_time IS NOT NULL;`], { encoding: 'utf8' }).trim()
      assert.equal(auditCount, '3', '三种导入动作应有操作人、时间和详细审计')
    } finally {
      for (const id of [...ids].reverse()) ok(await request('DELETE', '/system/warehouse-location/' + id))
      if (wid) {
        ok(await request('DELETE', '/system/warehouse/' + wid))
        const { execFileSync } = await import('node:child_process')
        execFileSync('sudo', ['-n', '-u', 'postgres', 'psql', '-v', 'ON_ERROR_STOP=1', '-d', 'zhizhun_erp'], { input: `BEGIN; DELETE FROM inv_warehouse_location WHERE warehouse_id=${BigInt(wid)} AND warehouse_id IN (SELECT warehouse_id FROM inv_warehouse WHERE warehouse_code='${prefix}'); DELETE FROM inv_warehouse WHERE warehouse_id=${BigInt(wid)} AND warehouse_code='${prefix}'; COMMIT;`, stdio: ['pipe', 'ignore', 'inherit'] })
      }
    }
  })
}
