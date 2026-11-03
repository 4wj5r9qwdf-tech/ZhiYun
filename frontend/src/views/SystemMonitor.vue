<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import message from 'ant-design-vue/es/message'
import { SearchOutlined } from '@ant-design/icons-vue'
import api from '../api'
import { can, canEnter } from '../utils/perms'
import StandardListLayout from '../components/StandardListLayout.vue'
import '../assets/standard-list-fill.css'

const props = defineProps<{ kind: string }>()
const categories = [
  { key: 'operlog', label: '操作日志', permission: 'monitor:operlog:list' },
  { key: 'loginInfo', label: '登录日志', permission: 'monitor:logininfo:list' },
].filter(item => canEnter(item.permission))
const category = ref(categories[0]?.key ?? '')
const kind = computed(() => props.kind === 'logs' ? category.value : props.kind)
const fields: Record<string, Record<string, string>> = {
  "menu-directory": {"menuName": "菜单名称", "menuType": "类型", "path": "路径", "status": "状态"},
  "cache-overview": {"metric": "指标", "value": "值"},
  "dictionary": {"dictName": "字典名称", "dictType": "字典类型", "remark": "备注"},
  "parameters": {"configName": "参数名称", "configKey": "参数键", "configValue": "参数值", "configType": "参数来源", "remark": "说明"},
  "files": {"originalName": "原文件名", "usage": "应用位置", "fileSuffix": "后缀", "createTime": "上传时间"},
  "storage-config": {"configKey": "配置名称", "bucketName": "存储桶", "region": "区域", "status": "状态"},
  "clients": {"clientKey": "客户端", "deviceType": "设备类型", "grantType": "授权方式", "status": "状态"},

  online: { userName: '账号', ipaddr: '登录 IP', browser: '浏览器', os: '操作系统', loginTime: '登录时间' },
  operlog: { operName: '操作人', title: '功能', businessType: '业务动作', auditObject: '操作对象', auditDetail: '详情', operUrl: '请求路径', operIp: '来源 IP', costTime: '耗时（毫秒）', requestMethod: '请求方式', operTime: '操作时间', status: '结果' },
  loginInfo: { userName: '账号', ipaddr: '登录 IP', browser: '浏览器', loginTime: '登录时间', status: '结果' },
}
const fileName = ref('')
const fileFilter = ref('')
const fileBusy = ref(false)
const filePicker = ref<HTMLInputElement>()
const previewUrl = ref('')
function closePreview() {
  URL.revokeObjectURL(previewUrl.value)
  previewUrl.value = ''
}
async function downloadFile(row: Record<string, unknown>, preview = false) {
  try {
    const file = await api.get('/resource/oss/download/' + row.ossId, { responseType: 'blob' }) as unknown as Blob
    if (!(file instanceof Blob) || file.type.includes('application/json')) throw new Error()
    const url = URL.createObjectURL(file)
    if (preview) {
      closePreview()
      previewUrl.value = url
      return
    }
    const link = document.createElement('a')
    link.href = url
    link.download = String(row.originalName)
    link.click()
    window.setTimeout(() => URL.revokeObjectURL(url), 1000)
  } catch {
    message.error(preview ? '预览失败' : '下载失败')
  }
}
async function uploadFiles(event: Event) {
  const input = event.target as HTMLInputElement
  const files = Array.from(input.files ?? [])
  fileBusy.value = true
  try {
    for (const file of files) {
      const data = new FormData()
      data.append('file', file)
      try {
        const res: any = await api.post('/resource/oss/upload', data)
        if (res.code !== 200) throw new Error()
        message.success(file.name + '：上传成功')
      } catch {
        message.error(file.name + '：上传失败')
      }
    }
    await load()
  } finally {
    input.value = ''
    fileBusy.value = false
  }
}
const isLogs = computed(() => props.kind === 'logs')
const account = ref('')
const columnSettingsOpen = ref(false)
const outcome = ref<string>()
const applied = ref<{ account?: string; status?: string }>({})
const pageSize = ref(20)
const hiddenColumns = ref<Record<string, string[]>>({})
const technicalFields = ['operUrl', 'operIp', 'costTime', 'requestMethod']
const allColumns = computed(() => Object.entries(fields[kind.value] || {}).map(([key, title]) => ({
  key, title, dataIndex: key,
  ...(isLogs.value ? { width: ['operTime', 'loginTime', 'auditObject', 'operUrl'].includes(key) ? 190 : 110, ellipsis: true } : {}),
})))
const columns = computed(() => {
  if (kind.value === 'files') return [...allColumns.value, { key: 'fileAction', title: '操作', width: 160 }]
  if (!isLogs.value) return kind.value === 'parameters' ? [...allColumns.value, { key: 'action', title: '操作', width: 90 }] : allColumns.value
  const hidden = hiddenColumns.value[kind.value] ?? (kind.value === 'operlog' ? technicalFields : [])
  return [
    { key: 'sequence', title: '序号', width: 64 },
    ...allColumns.value.filter(column => column.key !== 'auditDetail' && !hidden.includes(column.key)),
    ...allColumns.value.filter(column => column.key === 'auditDetail'),
  ]
})
function toggleColumn(key: string) {
  const hidden = hiddenColumns.value[kind.value] ?? (kind.value === 'operlog' ? technicalFields : [])
  hiddenColumns.value[kind.value] = hidden.includes(key) ? hidden.filter(item => item !== key) : [...hidden, key]
}
function search() {
  applied.value = { account: account.value.trim() || undefined, status: outcome.value }
  page.value = 1
  void load()
}
function resetFilters() {
  account.value = ''
  outcome.value = undefined
  search()
}
let requestId = 0
const rows = ref<Record<string, unknown>[]>([])
const loading = ref(false)
const page = ref(1)
const total = ref(0)
const selected = ref<Record<string, unknown>>()
const configDialog = ref(false)
const configSaving = ref(false)
const editingConfig = ref<Record<string, any>>({})
function editConfig(row: Record<string, unknown>) {
  editingConfig.value = { ...row }
  configDialog.value = true
}
async function saveConfig() {
  configSaving.value = true
  try {
    const res: any = await api.put('/system/config', editingConfig.value)
    if (res.code !== 200) throw new Error(res.msg || '保存失败')
    message.success('参数已保存')
    configDialog.value = false
    await load()
  } catch (error) {
    message.error(error instanceof Error ? error.message : '保存失败')
  } finally {
    configSaving.value = false
  }
}
function sizeAudit(row: Record<string, unknown>) {
  const url = String(row.operUrl ?? '')
  const person = /^\/system\/person(?:\/\d+(?:\/(?:reset-password|restore|permanent))?)?$/.test(url)
  const customer = url.match(/^\/system\/customer(?:\/(\d+))?(\/status)?$/)
  const path = (person ? [url, 'person'] : null) ?? (customer ? [customer[0], 'customer', customer[1], customer[2]] : null) ?? url.match(/^\/system\/size-data\/(size-group|size)(?:\/(\d+))?(\/status)?$/)
  const action = ({ '1': '新增', '2': '修改', '3': '删除' } as Record<string, string>)[String(row.businessType)] ?? '其他'

  function parse(value: unknown): Record<string, unknown> {
    try { return JSON.parse(String(value)) ?? {} } catch { return {} }
  }
  // LogAspect joins separately serialized arguments with spaces.
  const rawParams = String(row.operParam ?? '')
  let params = parse(rawParams)
  if (!Object.keys(params).length || Array.isArray(params) || typeof params !== 'object') {
    params = {}
    for (const match of rawParams.matchAll(/"(?:\\.|[^"\\])*"|\{(?:[^{}"\\]|"(?:\\.|[^"\\])*")*\}/g)) {
      if (!match[0].startsWith('{')) continue
      const candidate = parse(match[0])
      if ('name' in candidate || 'status' in candidate || 'sizeIds' in candidate) {
        params = candidate
        break
      }
    }
  }
  if (!path && typeof params.action !== 'string') return { businessType: action, auditObject: '未记录' }
  const result = parse(row.jsonResult)
  const target = person ? '人员' : customer ? '客户' : path?.[1] === 'size-group' ? '尺码组' : '尺码'
  const id = path?.[2] ?? (typeof params.id === 'string' ? params.id : undefined) ?? (String(row.status) === '0' ? String(result.data ?? '').match(/^\d+$/)?.[0] : undefined)
  const status = String(params.status ?? '')
  const verb = path?.[3] ? status === '0' ? '启用' : status === '1' ? '停用' : '变更状态' : action
  const name = typeof params.name === 'string' ? params.name : ''
  const auditObject = [name, id && ('ID ' + id)].filter(Boolean).join(' / ')
  return {
    businessType: typeof params.action === 'string' ? params.action : verb + target,
    auditObject: auditObject || '未记录',
    auditFields: customer && !params.changes && !path?.[3]
      ? { '变更明细': '历史日志未保存变更明细' }
      : params.changes && typeof params.changes === 'object'
      ? Object.fromEntries(Object.entries(params.changes).map(([key, pair]) => {
          const labels: Record<string, string> = { sales_representative_id: '关联业务员', name: '名称', name_en: '英文名称', sort_order: '排序', remark: '备注', status: '状态', sizeIds: '尺码成员 ID', tagIds: '标签 ID' }
          const format = (value: unknown) => ['status', '状态', '账号状态'].includes(key) && ['0', '1'].includes(String(value))
            ? String(value) === '0' ? '启用' : '停用'
            : Array.isArray(value) ? value.map(item => typeof item === 'object' ? JSON.stringify(item) : String(item)).join('、') || '空' : typeof value === 'object' && value !== null ? JSON.stringify(value) : String(value ?? '空')
          return [labels[key] ?? key, Array.isArray(pair) ? pair.map(format).join(' → ') : '未记录']
        }))
      : {
      '提交名称': name || undefined,
      '提交英文名称': params.name_en,
      '提交排序': params.sort_order,
      '提交备注': params.remark,
      '提交尺码 ID': Array.isArray(params.sizeIds) ? params.sizeIds.join('、') : undefined,
      '目标状态': status === '0' ? '启用' : status === '1' ? '停用' : undefined,
      '变更前的值': undefined,
    },
  }
}
async function load() {
  if (!kind.value) return
  const currentRequest = ++requestId
  const currentKind = kind.value
  loading.value = true
  try {
    const endpoints: Record<string,string> = {"menu-directory": "/system/menu/list", "cache-overview": "/monitor/cache", "dictionary": "/system/dict/type/list", "parameters": "/system/config/list", "files": "/resource/oss/list", "storage-config": "/resource/oss/config/list", "clients": "/system/client/list"}
    const res: any = await api.get(endpoints[kind.value] || '/monitor/' + kind.value + '/list', { params: { pageNum: page.value, pageSize: isLogs.value ? pageSize.value : 20,
      ...(currentKind === 'files' ? { originalName: fileFilter.value || undefined } : {}),
      ...(isLogs.value ? { [currentKind === 'operlog' ? 'operName' : 'userName']: applied.value.account, status: applied.value.status } : {}) } })
    if (currentRequest !== requestId) return
    if (res.code !== 200) throw new Error(res.msg || '查询失败')
    const data = res.data
    const records = kind.value === 'cache-overview'
      ? Object.entries(data.info || {}).map(([metric, value]) => ({ metric, value }))
      : Array.isArray(data) ? data : data.rows || []
    const usage = new Map<string, string[]>()
    if (currentKind === 'files' && can('system:company:list')) {
      try {
        const ids = records.map((row: Record<string, unknown>) => String(row.ossId)).filter((id: string) => /^\d+$/.test(id))
        const companies: any = ids.length ? await api.get('/system/company/file-usage/' + ids.join(',')) : { code: 200, data: [] }
        if (companies.code === 200) {
          for (const company of companies.data?.rows ?? companies.data ?? []) {
            for (const [id, field] of [[company.logo_oss_id, '公司标志'], [company.login_bg_oss_id, '登录背景']]) {
              if (id == null || id === '') continue
              const key = String(id)
              const locations = usage.get(key) ?? []
              locations.push('公司档案 / ' + company.company_name + ' / ' + field)
              usage.set(key, locations)
            }
          }
        }
      } catch {
        // Keep unknown usage visible when the company list is unavailable.
      }
    }
    if (currentRequest !== requestId) return
    rows.value = records.map((row: Record<string, unknown>, index: number) => ({
      ...Object.fromEntries(allColumns.value.map(({ key }) => [key, row[key]])), errorMsg: row.errorMsg, ossId: row.ossId, usage: currentKind === "files" ? usage.get(String(row.ossId))?.join("；") ?? "待核对" : undefined, ...(currentKind === "parameters" ? { configId: row.configId } : {}), ...(kind.value === 'operlog' ? sizeAudit(row) : {}), index,
    }))
    total.value = data.total ?? rows.value.length
  } catch (error) {
    if (currentRequest === requestId) message.error(error instanceof Error ? error.message : '查询失败')
  } finally {
    if (currentRequest === requestId) loading.value = false
  }
}
watch(kind, () => { rows.value = []; total.value = 0; selected.value = undefined; resetFilters() }, { immediate: true })
</script>
<template>
  <StandardListLayout>
    <template v-if="isLogs || kind === 'files'" #filters>
      <a-input v-if="kind === 'files'" v-model:value="fileName" allow-clear placeholder="搜索原文件名" style="width: 240px" @press-enter="fileFilter = fileName.trim(); search()" />
      <template v-else>
      <div class="head-filters">
      <a-radio-group v-model:value="category">
        <a-radio-button v-for="item in categories" :key="item.key" :value="item.key">{{ item.label }}</a-radio-button>
      </a-radio-group>
      <a-input v-model:value="account" allow-clear :placeholder="kind === 'operlog' ? '搜索操作人' : '搜索登录账号'"
        style="width: 200px" @press-enter="search">
        <template #prefix><SearchOutlined class="search-icon" /></template>
      </a-input>
      <a-select v-model:value="outcome" allow-clear placeholder="结果" style="width: 110px"
        :options="[{ label: '成功', value: '0' }, { label: '失败', value: '1' }]" />
      </div>
      </template>
    </template>
    <template #actions>
      <div v-if="isLogs" class="filter-bar">
        <a-button type="primary" :loading="loading" @click="search">查询</a-button>
        <a-button @click="resetFilters">重置</a-button>
        <span class="filter-spacer" />
        <a-dropdown v-model:open="columnSettingsOpen" :trigger="['click']" placement="bottomRight">
          <a-button>⚙ 列设置</a-button>
          <template #overlay>
            <a-card class="col-set-panel" :body-style="{ padding: '8px' }">
              <div v-for="column in allColumns.filter(item => item.key !== 'auditDetail')" :key="column.key" class="col-set-item">
                <a-checkbox :checked="columns.some(item => item.key === column.key)" @change="toggleColumn(column.key)">{{ column.title }}</a-checkbox>
              </div>
              <a-divider style="margin: 6px 0" />
              <div class="col-set-foot">
                <a-button type="link" size="small" @click="delete hiddenColumns[kind]">恢复默认</a-button>
                <a-button type="primary" size="small" @click="columnSettingsOpen = false">完成</a-button>
              </div>
            </a-card>
          </template>
        </a-dropdown>
      </div>
      <div v-else-if="kind === 'files'" class="filter-bar">
        <template v-if="can('system:oss:upload')">
          <a-button type="primary" :loading="fileBusy" @click="filePicker?.click()">＋ 上传文件</a-button>
          <input ref="filePicker" type="file" multiple hidden @change="uploadFiles" />
        </template>
        <a-button type="primary" :loading="loading" @click="fileFilter = fileName.trim(); search()">查询</a-button>
        <a-button @click="fileName = ''; fileFilter = ''; search()">重置</a-button>
      </div>
      <a-button v-else type="primary" :loading="loading" @click="load">刷新</a-button>
    </template>
    <a-table class="zy-fill-table" :columns="columns" :data-source="rows" :loading="loading" row-key="index"
      :size="isLogs ? 'middle' : undefined" :table-layout="isLogs ? 'fixed' : undefined"
      :scroll="isLogs ? { x: columns.reduce((sum, column) => sum + (column.width ?? 110), 0), y: 'calc(100vh - 390px)' } : { y: 'calc(100vh - 330px)' }"
      :pagination="{ current: page, pageSize: isLogs ? pageSize : 20, total, showSizeChanger: isLogs, showQuickJumper: isLogs, showTotal: isLogs ? (count: number) => `共 ${count} 条` : undefined, onChange: (value: number, size: number) => { page = value; pageSize = size; load() } }">
      <template #bodyCell="{ column, text, record, index }">
        <template v-if="column.key === 'fileAction' && can('system:oss:download') && !String(record.originalName).startsWith('.')">
          <a-button v-if="/^\.?(png|jpe?g|gif|webp)$/i.test(String(record.fileSuffix))" type="link" @click="downloadFile(record, true)">预览</a-button>
          <a-button type="link" @click="downloadFile(record)">下载</a-button>
        </template>
        <template v-else-if="column.key === 'sequence'">{{ (page - 1) * pageSize + index + 1 }}</template>
        <a-button v-else-if="column.key === 'action' && can('system:config:edit') && record.configKey === 'sys.oss.previewListResource'" type="link" @click="editConfig(record)">编辑</a-button>
        <template v-else-if="kind === 'parameters' && column.key === 'configType'">{{ text === 'Y' ? '系统内置' : text === 'N' ? '自定义' : '未识别' }}</template>
        <template v-else-if="kind === 'parameters' && column.key === 'configValue'">{{ record.configKey === 'sys.oss.previewListResource' ? (text === 'true' ? '开启' : '关闭') : '—' }}</template>
        <a-button v-else-if="column.key === 'auditDetail'" type="link" @click="selected = record">查看</a-button>
        <template v-else-if="column.key === 'status' && ['operlog', 'loginInfo'].includes(kind)"><a-tag :color="String(text) === '0' ? 'green' : 'red'">{{ String(text) === '0' ? '成功' : '失败' }}</a-tag></template>
        <template v-else-if="column.key === 'loginTime' && typeof text === 'number'">{{ new Date(text).toLocaleString('zh-CN') }}</template>
      </template>
    </a-table>
    <a-modal v-if="kind === 'files'" :open="!!previewUrl" title="图片预览" :footer="null" @cancel="closePreview">
      <img v-if="previewUrl" :src="previewUrl" alt="文件预览" style="display: block; max-width: 100%; max-height: 70vh; margin: auto" />
    </a-modal>
    <a-modal v-if="kind === 'parameters'" v-model:open="configDialog" title="编辑系统参数" :confirm-loading="configSaving" @ok="saveConfig">
      <a-form layout="vertical">
        <a-form-item label="参数名称"><a-input v-model:value="editingConfig.configName" /></a-form-item>
        <a-form-item label="参数键"><a-input v-model:value="editingConfig.configKey" disabled /></a-form-item>
        <a-form-item label="参数值"><a-switch :checked="editingConfig.configValue === 'true'" checked-children="开启" un-checked-children="关闭" @change="editingConfig.configValue = $event ? 'true' : 'false'" /></a-form-item>
        <a-form-item label="说明"><a-input v-model:value="editingConfig.remark" /></a-form-item>
      </a-form>
    </a-modal>
    <a-modal :open="!!selected" title="操作日志详情" :width="760" :footer="null" @cancel="selected = undefined">
      <a-descriptions v-if="selected" bordered :column="1" size="small">
        <a-descriptions-item label="操作人">{{ selected.operName }}</a-descriptions-item>
        <a-descriptions-item label="操作时间">{{ selected.operTime }}</a-descriptions-item>
        <a-descriptions-item label="结果">{{ String(selected.status) === '0' ? '成功' : '失败' }}</a-descriptions-item>
        <a-descriptions-item v-if="selected.errorMsg" label="失败原因">{{ selected.errorMsg }}</a-descriptions-item>
        <a-descriptions-item label="业务动作">{{ selected.businessType }}</a-descriptions-item>
        <a-descriptions-item label="操作对象">{{ selected.auditObject }}</a-descriptions-item>
        <a-descriptions-item label="请求路径">{{ selected.requestMethod }} {{ selected.operUrl }}</a-descriptions-item>
        <a-descriptions-item label="来源 IP">{{ selected.operIp }}</a-descriptions-item>
        <a-descriptions-item label="耗时">{{ selected.costTime ?? '—' }} 毫秒</a-descriptions-item>
        <a-descriptions-item v-for="(value, label) in (selected.auditFields as Record<string, unknown>)" :key="label" :label="label">
          {{ value ?? '未记录' }}
        </a-descriptions-item>
      </a-descriptions>
    </a-modal>
  </StandardListLayout>
</template>


<style scoped>
.head-filters {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.search-icon { color: #86909c; }
.filter-bar {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  padding: 12px;
  background: #fff;
  border-radius: 10px;
  box-shadow: 0 2px 8px rgba(29, 33, 41, .06);
}
.filter-spacer { flex: 1 1 auto; min-width: 8px; }
.col-set-panel { width: 220px; border-radius: 8px; box-shadow: 0 6px 20px rgba(29, 33, 41, .12); }
.col-set-item { padding: 2px 8px; }
.col-set-foot { display: flex; align-items: center; justify-content: space-between; padding: 4px 8px 0; }
</style>
