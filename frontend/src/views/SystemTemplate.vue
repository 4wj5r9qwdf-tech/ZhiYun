<!--
  功能模板（系统模块）—— 织云 ERP 列表页标准件全集展览
  后期开发按需从本页/OrgManage/Person 搬对应区块；每块顶部注释标注对应规范条目。

  标准件清单：
  B.5.1.1 列表页四件套：序号列 / 列宽拖拽(customHeaderCell+右缘14px) / 自适应列宽(列名最小宽+操作列实测) /
          操作列按钮式(primary编辑/danger删除, size=small, 操作列左/右缘16px可拖) / 后端分页禁前端过滤
  B.5.1.2 表单弹窗：a-modal mask-closable=false + keyboard=false / 校验跳Tab / 多选勾选弹窗
  定版布局：页头行=查询条件左置(无标题无返回)；按钮行=+新增(最左)→查询→重置……动作/列设置(最右)
  左树面板：树+类型tag+人数角标+点击筛选该节点及下级+窄竖条折叠手柄(CSS三角, 展开贴面板内右缘/收起贴页左缘)
  行选择框：rowSelection(勾选跨页保持/批量操作按需显隐)
  列排序：sortable → 后端 orderBy 白名单(或前端sorter, 见注)
  演示数据：纯前端内存生成, 不入库不内置——刷新即重置
-->
<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { PlusOutlined, SearchOutlined } from '@ant-design/icons-vue'
import message from 'ant-design-vue/es/message'
import Modal from 'ant-design-vue/es/modal'

// ===== 演示数据（内存生成，不入库） =====
type DemoNode = { orgId: string; orgName: string; orgCategory: string; children?: DemoNode[] }
type DemoRow = { id: string; code: string; name: string; orgId: string; type: string; status: string; amount: number; owner: string; remark: string }

const demoTree: DemoNode[] = [
  { orgId: 'd1', orgName: '总部', orgCategory: '部门', children: [
    { orgId: 'd11', orgName: '生产中心', orgCategory: '部门', children: [
      { orgId: 'd111', orgName: '一车间', orgCategory: '科室' },
      { orgId: 'd112', orgName: '二车间', orgCategory: '科室' },
    ] },
    { orgId: 'd12', orgName: '销售中心', orgCategory: '部门', children: [
      { orgId: 'd121', orgName: '华东销售科', orgCategory: '科室' },
    ] },
  ] },
]
const flatOrgs = (() => {
  const out: { orgId: string; orgName: string }[] = []
  const walk = (ns: DemoNode[]) => ns.forEach(n => { out.push({ orgId: n.orgId, orgName: n.orgName }); if (n.children) walk(n.children) })
  walk(demoTree)
  return out
})()
const demoRows = ref<DemoRow[]>(Array.from({ length: 23 }, (_, i) => ({
  id: 'r' + (i + 1),
  code: 'T' + String(1001 + i),
  name: '模板演示记录' + (i + 1),
  orgId: flatOrgs[i % flatOrgs.length].orgId,
  type: ['部门', '科室', '小组'][i % 3],
  status: i % 5 === 4 ? '1' : '0',
  amount: 1000 + i * 37,
  owner: ['张三', '李四', '王五'][i % 3],
  remark: i % 3 === 0 ? '' : '备注' + i,
})))

// ===== 左树面板：点节点筛该节点及下级；再点取消 =====
const treeCollapsed = ref(false)
const selectedOrgId = ref<string | null>(null)
function onTreeSelect(keys: any[]) { selectedOrgId.value = keys.length ? String(keys[0]) : null }
function walkIds(ns: DemoNode[]): string[] { return ns.flatMap(n => [n.orgId, ...(n.children ? walkIds(n.children) : [])]) }
const orgCountMap = computed(() => {
  const m: Record<string, number> = {}
  const walk = (ns: DemoNode[]) => ns.forEach(n => {
    m[n.orgId] = demoRows.value.filter(r => walkIds([n]).includes(r.orgId)).length
    if (n.children) walk(n.children)
  })
  walk(demoTree)
  return m
})
function typeColorOf(t: string) { return t === '小组' ? 'green' : t === '科室' ? 'blue' : 'default' }

// ===== 查询条件（页头行，变更即查 + 回车查询） =====
const keyword = ref('')
const fType = ref<string | undefined>()
const fStatus = ref<string | undefined>()
const pageNum = ref(1)
const pageSize = ref(20)
const loading = ref(false)
function loadList() { loading.value = true; setTimeout(() => { loading.value = false }, 150) }
watch([keyword, fType, fStatus, selectedOrgId], () => { pageNum.value = 1 })
function resetFilters() {
  keyword.value = ''; fType.value = undefined; fStatus.value = undefined
  selectedOrgId.value = null; treeCollapsed.value = false; pageNum.value = 1
}

// ===== 列表数据：树选节点及下级 + 关键词/类型/状态 过滤（后端分页项目换成接口；此处演示为前端切片） =====
const filteredRows = computed(() => {
  const ids = selectedOrgId.value ? walkIds(demoTree.filter(n => walkIds([n]).includes(selectedOrgId.value!))) : null
  return demoRows.value.filter(r =>
    (!ids || ids.includes(r.orgId))
    && (!keyword.value.trim() || (r.name + r.code + r.owner).includes(keyword.value.trim()))
    && (!fType.value || r.type === fType.value)
    && (!fStatus.value || r.status === fStatus.value))
})
const pagedRows = computed(() => filteredRows.value.slice((pageNum.value - 1) * pageSize.value, pageNum.value * pageSize.value))
const total = computed(() => filteredRows.value.length)

// ===== 行选择框（批量操作按需显隐；跨页保持勾选） =====
const selectedRowKeys = ref<string[]>([])
const hasSelection = computed(() => selectedRowKeys.value.length > 0)
function removeBatch() {
  Modal.confirm({
    title: `删除选中 ${selectedRowKeys.value.length} 条？`,
    content: '演示操作：仅内存生效，刷新即恢复',
    onOk: () => {
      demoRows.value = demoRows.value.filter(r => !selectedRowKeys.value.includes(r.id))
      selectedRowKeys.value = []
      message.success('已删除（内存演示）')
    },
  })
}

// ===== 列表列（序号列/操作列在 columns computed 内拼接；sortable→orderBy 白名单） =====
const ALL_COLUMNS = [
  { title: '编码', key: 'code', width: 110, sorter: true },
  { title: '名称', key: 'name', width: 180, sorter: true },
  { title: '所属组织', key: 'orgId', width: 130 },
  { title: '类型', key: 'type', width: 90 },
  { title: '状态', key: 'status', width: 100 },
  { title: '金额', key: 'amount', width: 110, sorter: true },
  { title: '负责人', key: 'owner', width: 110 },
  { title: '备注', key: 'remark', width: 160 },
]
type ColState = { key: string; visible: boolean; width?: number }
const colState = ref<ColState[]>([])
const colSetOpen = ref(false)
const colStoreKey = computed(() => `tpl_list:${localStorage.getItem('userId') || 'anon'}`)
const opsWidth = ref(Number(localStorage.getItem(`${colStoreKey.value}:ops_w`)) || 200)

function loadColState() {
  try {
    const raw = localStorage.getItem(colStoreKey.value)
    if (raw) {
      const saved: ColState[] = JSON.parse(raw)
      const curKeys = new Set(ALL_COLUMNS.map(c => c.key))
      const ordered: ColState[] = []
      for (const s of saved) if (curKeys.has(s.key)) { ordered.push(s); curKeys.delete(s.key) }
      for (const c of ALL_COLUMNS) if (curKeys.has(c.key)) ordered.push({ key: c.key, visible: true })
      colState.value = ordered
      return
    }
  } catch { /* ignore */ }
  colState.value = ALL_COLUMNS.map(c => ({ key: c.key, visible: true }))
}
function saveColState() { try { localStorage.setItem(colStoreKey.value, JSON.stringify(colState.value)) } catch { /* ignore */ } }
function toggleCol(key: string) { const s = colState.value.find(c => c.key === key); if (s) { s.visible = !s.visible; saveColState() } }
function showAllCols() { colState.value = ALL_COLUMNS.map(c => ({ key: c.key, visible: true })); saveColState() }
function colTitle(key: string) { return ALL_COLUMNS.find(c => c.key === key)?.title || key }
function moveCol(index: number, dir: -1 | 1) {
  const arr = colState.value
  const target = index + dir
  if (target < 0 || target >= arr.length) return
  const [item] = arr.splice(index, 1)
  arr.splice(target, 0, item)
  saveColState()
}
async function autoFitCols() {
  let opsW = 200
  const wraps = document.querySelectorAll('.ops-btns')
  if (wraps.length) {
    const maxContent = Math.max(...[...wraps].map(w => {
      const bs = [...w.querySelectorAll('button')]
      if (!bs.length) return 0
      return Math.max(...bs.map(b => b.getBoundingClientRect().right)) - Math.min(...bs.map(b => b.getBoundingClientRect().left))
    }))
    const td = wraps[0].closest('td')
    const cs = td ? getComputedStyle(td) : null
    const pad = cs ? Math.round(parseFloat(cs.paddingLeft) + parseFloat(cs.paddingRight)) : 16
    opsW = Math.max(120, Math.round(maxContent + pad + 2))
  }
  const mins = new Map<string, number>()
  let visibleSum = 64 + 48  // 序号列 + 选择框列
  for (const c of colState.value) {
    const t = ALL_COLUMNS.find(a => a.key === c.key)?.title || c.key
    const w = Math.max(90, String(t).length * 16 + 48)
    mins.set(c.key, w)
    if (c.visible) visibleSum += w
  }
  visibleSum += opsW
  const container = document.querySelector('.ant-table-container')
  const cw = container ? Math.round(container.clientWidth) : 0
  const visibleCount = colState.value.filter(c => c.visible).length
  if (cw > visibleSum + 10 && visibleCount > 0) {
    const extra = Math.floor((cw - visibleSum) / visibleCount)
    for (const c of colState.value) {
      if (c.visible) mins.set(c.key, (mins.get(c.key) || 90) + extra)
    }
  }
  for (const c of colState.value) { (c as any).width = mins.get(c.key) }
  saveColState()
  opsWidth.value = opsW
  try { localStorage.setItem(`${colStoreKey.value}:ops_w`, String(opsWidth.value)) } catch { /* ignore */ }
}

// 列排序：antd sorter（本地演示）；后端分页项目请用 sortable→orderBy 白名单（见 Person.vue）
function onTableChange(_pagination: any, _filters: any, sorter: any) {
  const key = sorter?.columnKey
  const order = sorter?.order
  if (!key || !order) { loadList(); return }
  demoRows.value = [...demoRows.value].sort((a: any, b: any) => {
    const va = a[key], vb = b[key]
    const cmp = typeof va === 'number' ? va - vb : String(va).localeCompare(String(vb))
    return order === 'ascend' ? cmp : -cmp
  })
}

// ===== 列宽拖拽（B.5.1.1：th 右缘 14px 起拖；操作列左/右缘 16px 均可） =====
function onColResize(key: string, w: number) {
  if (key === 'ops') { opsWidth.value = Math.max(120, Math.round(w)); try { localStorage.setItem(`${colStoreKey.value}:ops_w`, String(opsWidth.value)) } catch { /* ignore */ } return }
  const s = colState.value.find(c => c.key === key)
  if (s) { s.width = Math.max(60, Math.round(w)); saveColState() }
}
let suppressHeaderClickUntil = 0
function onHeaderMouseDown(key: string, e: MouseEvent) {
  if (key === 'index' || key === 'selection') return
  const th = e.currentTarget as HTMLTableCellElement
  const rect = th.getBoundingClientRect()
  const nextIsOps = !!th.nextElementSibling?.classList.contains('ops-th')
  const atOpsLeft = key === 'ops' && e.clientX <= rect.left + 16
  const atOpsRight = key === 'ops' && e.clientX >= rect.right - 16
  const atPrevRightOfOps = nextIsOps && e.clientX >= rect.right - 16
  if (atOpsLeft || atOpsRight || atPrevRightOfOps) {
    const fromLeft = atOpsLeft || atPrevRightOfOps
    const startX = e.clientX
    const startW = opsWidth.value
    const onMove = (ev: MouseEvent) => onColResize('ops', fromLeft ? startW - (ev.clientX - startX) : startW + (ev.clientX - startX))
    const onUp = () => {
      document.removeEventListener('mousemove', onMove); document.removeEventListener('mouseup', onUp)
      document.body.style.cursor = ''; suppressHeaderClickUntil = Date.now() + 300
    }
    document.addEventListener('mousemove', onMove); document.addEventListener('mouseup', onUp)
    document.body.style.cursor = 'col-resize'
    e.preventDefault(); e.stopPropagation()
    return
  }
  if (key === 'ops') return
  if (e.clientX < rect.right - 14) return
  const startX = e.clientX
  const startW = rect.width
  const onMove = (ev: MouseEvent) => onColResize(key, startW + (ev.clientX - startX))
  const onUp = () => {
    document.removeEventListener('mousemove', onMove); document.removeEventListener('mouseup', onUp)
    document.body.style.cursor = ''; suppressHeaderClickUntil = Date.now() + 300
  }
  document.addEventListener('mousemove', onMove); document.addEventListener('mouseup', onUp)
  document.body.style.cursor = 'col-resize'
  e.preventDefault(); e.stopPropagation()
}
function onHeaderClick(e: MouseEvent) {
  if (Date.now() < suppressHeaderClickUntil) { e.preventDefault(); e.stopPropagation() }
}

const columns = computed(() => {
  const idx = {
    title: '序号', key: 'index', width: 64, align: 'center' as const, fixed: 'left' as const,
    customHeaderCell: () => ({ class: 'zy-th-noresize' }),
  }
  const ops = {
    title: '操作', key: 'ops', width: opsWidth.value, fixed: 'right' as const,
    customHeaderCell: () => ({ class: 'ops-th', onMousedown: (e: MouseEvent) => onHeaderMouseDown('ops', e), onClick: onHeaderClick }),
  }
  const list = colState.value
    .filter(c => c.visible)
    .map(c => ({
      ...ALL_COLUMNS.find(a => a.key === c.key)!,
      width: c.width,
      ...(ALL_COLUMNS.find(a => a.key === c.key)!.sorter ? { sorter: true } : {}),
      customHeaderCell: () => ({ onMousedown: (e: MouseEvent) => onHeaderMouseDown(c.key, e), onClick: onHeaderClick }),
    }))
  const sel = { key: 'selection', width: 48, fixed: 'left' as const, customHeaderCell: () => ({ class: 'zy-th-noresize' }) }
  return [idx, sel, ...list, ops]
})

// ===== 演示弹窗（B.5.1.2：mask-closable=false / keyboard=false / 保存校验） =====
const showDlg = ref(false)
const isEdit = ref(false)
const saving = ref(false)
const formRef = ref()
const emptyForm = () => ({ id: null as string | null, code: '', name: '', orgId: undefined as string | undefined, type: '部门', status: '0', amount: undefined as number | undefined, owner: '', remark: '' })
const form = ref(emptyForm())
const rules: any = {
  code: [{ required: true, message: '请输入编码', trigger: 'blur' }],
  name: [{ required: true, message: '请输入名称', trigger: 'blur' }],
  orgId: [{ required: true, message: '请选择所属组织', trigger: 'change' }],
}
function openAdd() { isEdit.value = false; form.value = emptyForm(); showDlg.value = true }
function openEdit(row: any) { isEdit.value = true; form.value = { ...row } as any; showDlg.value = true }
async function saveRow() {
  try {
    await formRef.value?.validate()
  } catch { return }
  saving.value = true
  setTimeout(() => {
    if (isEdit.value && form.value.id) {
      const i = demoRows.value.findIndex(r => r.id === form.value.id)
      if (i >= 0) demoRows.value[i] = { ...demoRows.value[i], ...form.value } as DemoRow
    } else {
      demoRows.value.unshift({ ...form.value, id: 'r' + Date.now(), amount: form.value.amount ?? 0 } as DemoRow)
    }
    saving.value = false
    showDlg.value = false
    message.success('已保存（内存演示，刷新即重置）')
  }, 200)
}
function removeOne(row: any) {
  Modal.confirm({
    title: `删除「${row.name}」？`,
    content: '演示操作：仅内存生效，刷新即恢复',
    onOk: () => { demoRows.value = demoRows.value.filter(r => r.id !== row.id); message.success('已删除（内存演示）') },
  })
}
function toggleStatus(row: any, checked: any) {
  row.status = checked ? '0' : '1'
  message.success(checked ? '已启用（内存演示）' : '已停用（内存演示）')
}

// ===== 导出 CSV（前端 blob，导当前筛选行；后端分页项目见 Person.vue 的 /system/person/export） =====
function exportCsv() {
  const head = '编码,名称,所属组织,类型,状态,金额,负责人,备注'
  const orgName = (id: string) => flatOrgs.find(o => o.orgId === id)?.orgName || ''
  const lines = filteredRows.value.map(r => [r.code, r.name, orgName(r.orgId), r.type, r.status === '0' ? '启用' : '停用', r.amount, r.owner, r.remark].join(','))
  const blob = new Blob(['\ufeff' + [head, ...lines].join('\r\n')], { type: 'text/csv;charset=utf-8' })
  const a = document.createElement('a')
  a.href = URL.createObjectURL(blob)
  a.download = '功能模板导出.csv'
  a.click()
  URL.revokeObjectURL(a.href)
  message.success(`已导出 ${filteredRows.value.length} 条`)
}

onMounted(() => { loadColState(); loadList() })
</script>

<template>
  <div class="tpl-page">
    <!-- 页头行：查询条件左置（定版布局：无标题无返回） -->
    <div class="page-header">
      <div class="head-filters">
        <a-input v-model:value="keyword" placeholder="搜索编码/名称/负责人" allow-clear style="width:200px" @press-enter="loadList">
          <template #prefix><SearchOutlined style="color:#86909C" /></template>
        </a-input>
        <a-select v-model:value="fType" allow-clear placeholder="类型" style="width:110px" @change="loadList">
          <a-select-option value="部门">部门</a-select-option>
          <a-select-option value="科室">科室</a-select-option>
          <a-select-option value="小组">小组</a-select-option>
        </a-select>
        <a-select v-model:value="fStatus" allow-clear placeholder="状态" style="width:100px" @change="loadList">
          <a-select-option value="0">启用</a-select-option>
          <a-select-option value="1">停用</a-select-option>
        </a-select>
      </div>
    </div>

    <!-- 按钮行：+新增（最左）→ 查询 → 重置 …… 批量/导出/列设置（最右） -->
    <div class="filter-bar">
      <a-button type="primary" @click="openAdd"><PlusOutlined />新增</a-button>
      <a-button type="primary" @click="loadList">查询</a-button>
      <a-button @click="resetFilters">重置</a-button>
      <span class="filter-spacer"></span>
      <a-button :disabled="!hasSelection" danger @click="removeBatch">
        删除选中{{ hasSelection ? `（${selectedRowKeys.length}）` : '' }}
      </a-button>
      <a-button @click="exportCsv">↓ 导出</a-button>
      <a-dropdown v-model:open="colSetOpen" :trigger="['click']">
        <a-button>⚙ 列设置</a-button>
        <template #overlay>
          <a-card class="col-set-panel" :body-style="{ padding: '8px 4px' }">
            <div v-for="(c, i) in colState" :key="c.key" class="col-set-item">
              <a-checkbox :checked="c.visible" @change="toggleCol(c.key)">{{ colTitle(c.key) }}</a-checkbox>
              <span class="col-move">
                <a-button type="text" size="small" :disabled="i === 0" @click="moveCol(i, -1)">↑</a-button>
                <a-button type="text" size="small" :disabled="i === colState.length - 1" @click="moveCol(i, 1)">↓</a-button>
              </span>
            </div>
            <a-divider style="margin: 6px 0" />
            <div class="col-set-foot">
              <a size="small" @click="showAllCols">恢复默认</a>
              <a size="small" @click="autoFitCols">自适应列宽</a>
              <a-button type="primary" size="small" @click="colSetOpen = false">完成</a-button>
            </div>
          </a-card>
        </template>
      </a-dropdown>
    </div>

    <!-- 左树 + 右表（树可收起；树面板头部"重置"与表头重置同行为） -->
    <div class="tpl-body" :class="{ 't-collapsed': treeCollapsed }">
      <div class="tree-panel" :class="{ collapsed: treeCollapsed }">
        <div class="tree-head">
          <span class="tree-title">组织架构</span>
          <a v-if="selectedOrgId" class="tree-clear" @click="resetFilters">重置</a>
        </div>
        <div class="tree-body">
          <a-tree
            :tree-data="(demoTree as any)"
            :field-names="{ title: 'orgName', key: 'orgId', children: 'children' }"
            block-node
            default-expand-all
            :selected-keys="selectedOrgId ? [selectedOrgId] : []"
            @select="onTreeSelect"
          >
            <template #title="{ orgName, orgId, orgCategory }">
              <span class="tree-node">
                {{ orgName }}
                <a-tag v-if="orgCategory" :color="typeColorOf(orgCategory)" class="type-tag">{{ orgCategory }}</a-tag>
                <span v-if="orgCountMap[orgId]" class="tree-count">{{ orgCountMap[orgId] }}</span>
              </span>
            </template>
          </a-tree>
        </div>
      </div>
      <div class="tree-toggle" :class="{ 'at-left': treeCollapsed }" :title="treeCollapsed ? '展开组织树' : '收起组织树'" @click="treeCollapsed = !treeCollapsed" />

      <div class="tpl-main">
        <a-table
          :columns="columns"
          :data-source="pagedRows"
          :loading="loading"
          :row-selection="{ selectedRowKeys, onChange: (keys: any[]) => { selectedRowKeys = keys as any } }"
          :pagination="{ total, current: pageNum, pageSize, showSizeChanger: true, showQuickJumper: true, showTotal: (t: number) => `共 ${t} 条`, onChange: (p: number, ps: number) => { pageNum = p; pageSize = ps } }"
          :scroll="{ x: 'max-content', y: 'calc(100vh - 360px)' }"
          table-layout="fixed"
          row-key="id"
          size="middle"
          class="tpl-table"
          @change="onTableChange"
        >
          <template #bodyCell="{ column, record, index }">
            <template v-if="column.key === 'index'">
              {{ (pageNum - 1) * pageSize + index + 1 }}
            </template>
            <template v-else-if="column.key === 'orgId'">
              {{ flatOrgs.find(o => o.orgId === record.orgId)?.orgName || '—' }}
            </template>
            <template v-else-if="column.key === 'type'">
              <a-tag :color="typeColorOf(record.type)">{{ record.type }}</a-tag>
            </template>
            <template v-else-if="column.key === 'status'">
              <a-switch :checked="record.status === '0'" checked-children="启用" un-checked-children="停用" @change="(v: any) => toggleStatus(record, v)" />
            </template>
            <template v-else-if="column.key === 'amount'">
              {{ record.amount?.toLocaleString() }}
            </template>
            <template v-else-if="column.key === 'remark'">
              {{ record.remark || '—' }}
            </template>
            <template v-else-if="column.key === 'ops'">
              <div class="ops-btns">
                <a-button type="primary" size="small" @click="openEdit(record)">编辑</a-button>
                <a-button danger size="small" @click="removeOne(record)">删除</a-button>
              </div>
            </template>
          </template>
        </a-table>
      </div>
    </div>

    <!-- 新增/编辑弹窗（B.5.1.2） -->
    <a-modal
      v-model:open="showDlg"
      :mask-closable="false"
      :keyboard="false"
      :title="isEdit ? '编辑' : '新增'"
      width="640px"
      @ok="saveRow"
      @cancel="showDlg = false"
    >
      <a-form ref="formRef" :model="form" :rules="rules" layout="vertical">
        <a-row :gutter="12">
          <a-col :span="12">
            <a-form-item label="编码" name="code">
              <a-input v-model:value="form.code" :maxlength="20" placeholder="请输入" />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="名称" name="name">
              <a-input v-model:value="form.name" :maxlength="30" placeholder="请输入" />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="所属组织" name="orgId">
              <a-tree-select
                v-model:value="form.orgId"
                :tree-data="demoTree"
                :field-names="{ label: 'orgName', value: 'orgId', children: 'children' }"
                tree-default-expand-all
                allow-clear
                placeholder="请选择"
              />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="类型">
              <a-select v-model:value="form.type">
                <a-select-option value="部门">部门</a-select-option>
                <a-select-option value="科室">科室</a-select-option>
                <a-select-option value="小组">小组</a-select-option>
              </a-select>
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="金额">
              <a-input-number v-model:value="form.amount" style="width:100%" :min="0" placeholder="请输入" />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="负责人">
              <a-input v-model:value="form.owner" :maxlength="20" placeholder="请输入" />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="状态">
              <a-switch v-model:checked="form.status" checked-value="0" un-checked-value="1" checked-children="启用" un-checked-children="停用" />
            </a-form-item>
          </a-col>
          <a-col :span="24">
            <a-form-item label="备注">
              <a-textarea v-model:value="form.remark" :maxlength="100" :rows="2" placeholder="可不填" />
            </a-form-item>
          </a-col>
        </a-row>
      </a-form>
    </a-modal>
  </div>
</template>

<style scoped>
.tpl-page { padding: 12px 16px; }
.page-header { display: flex; align-items: center; margin-bottom: 16px; gap: 10px; flex-wrap: wrap; }
.head-filters { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.filter-bar { display: flex; gap: 8px; align-items: center; flex-wrap: wrap; background: #fff; padding: 12px; border-radius: 10px; box-shadow: 0 2px 8px rgba(29,33,41,.06); margin-bottom: 12px; }
.filter-spacer { flex: 1 1 auto; min-width: 8px; }

/* 左树 + 右表 分栏（树可收起） */
.tpl-body { display: flex; align-items: stretch; gap: 0; min-height: calc(100vh - 300px); position: relative; }
.tpl-body.t-collapsed .tpl-main { margin-left: 14px; }
.tree-panel { width: 260px; flex: none; background: #fff; border-radius: 10px; box-shadow: 0 2px 8px rgba(29,33,41,.06); display: flex; flex-direction: column; overflow: hidden; transition: width .2s, opacity .2s; margin-right: 8px; }
.tree-panel.collapsed { width: 0; height: 0; min-height: 0; opacity: 0; margin-right: 0; padding: 0; border: none; pointer-events: none; }
.tree-head { display: flex; align-items: center; justify-content: space-between; padding: 10px 12px; border-bottom: 1px solid #F2F3F5; }
.tree-title { font-weight: 600; color: #1D2129; }
.tree-clear { color: #1E5EFF; cursor: pointer; font-size: 13px; }
.tree-body { flex: 1; overflow: auto; padding: 8px; }
.tree-node { display: inline-flex; align-items: center; gap: 6px; }
.type-tag { font-size: 11px; line-height: 1; padding: 1px 5px; border-radius: 3px; margin: 0; }
.tree-count { font-size: 11px; min-width: 16px; height: 16px; padding: 0 4px; border-radius: 8px; background: #E8F3FF; color: #1E5EFF; display: inline-flex; align-items: center; justify-content: center; }

/* 折叠手柄：8px 窄竖条 + CSS 三角（展开贴面板内右缘，收起贴页面左缘） */
.tree-toggle { position: absolute; top: 50%; transform: translateY(-50%); left: 248px; z-index: 6; width: 8px; height: 36px; display: flex; align-items: center; justify-content: center; cursor: pointer; background: #fff; border: 1px solid #E5E8EF; border-radius: 4px; transition: left .2s, border-color .15s; }
.tree-toggle::before { content: ''; width: 0; height: 0; border: 3.5px solid transparent; border-right-color: #86909C; margin-left: -2px; }
.tree-toggle.at-left { left: 4px; }
.tree-toggle.at-left::before { border-right-color: transparent; border-left-color: #86909C; margin-left: 2px; }
.tree-toggle:hover { border-color: #1E5EFF; }
.tree-toggle:hover::before { border-right-color: #1E5EFF; }
.tree-toggle.at-left:hover::before { border-left-color: #1E5EFF; border-right-color: transparent; }

.tpl-main { flex: 1; min-width: 0; }
.tpl-table :deep(.ant-table-thead > tr > th) { background: #F7F8FA; font-weight: 600; }
.tpl-table :deep(.ant-table-tbody > tr:hover > td) { background: #F5F8FF; }

/* 操作列按钮组（紧凑，自适应列宽按其内容实测） */
.ops-btns { display: flex; align-items: center; gap: 6px; white-space: nowrap; }
.ops-btns .ant-btn-sm { padding-inline: 5px; }

/* 列设置面板 */
.col-set-panel { width: 220px; border-radius: 8px; box-shadow: 0 6px 20px rgba(29,33,41,.12); }
.col-set-item { display: flex; align-items: center; justify-content: space-between; padding: 2px 8px; }
.col-move { display: inline-flex; }
.col-move .ant-btn { padding: 0 4px; height: 20px; line-height: 1; color: #86909C; }
.col-move .ant-btn:hover:not(:disabled) { color: #1E5EFF; }
.col-set-foot { display: flex; align-items: center; justify-content: space-between; padding: 4px 8px 0; }
</style>
