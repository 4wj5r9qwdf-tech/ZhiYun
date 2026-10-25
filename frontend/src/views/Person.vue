<script setup lang="ts">
/**
 * 人员信息（人员模块）—— ant-design-vue 重构版：
 * - a-table：排序/筛选/列设置(显隐+顺序)/固定操作列/分页 全自带（替代手写 DataTable）
 * - a-form + a-modal：表单校验/分组布局
 * - a-input/a-select/a-date-picker/a-checkbox-group：控件统一
 * - message/Modal.confirm：全局反馈（替代手写 msg/confirm）
 * - 建人员=建登录账号（工号6位，默认密码123456，可重置/自定义）
 * - 后端 orderBy 白名单：job_no/name/dept/group/emp_status/card_no/hire_date
 */
import { ref, computed, onMounted, defineComponent, h } from 'vue'
import { useRouter } from 'vue-router'
import message from 'ant-design-vue/es/message'
import Modal from 'ant-design-vue/es/modal'
import api from '../api'

const router = useRouter()

// ===== 列表 =====
const rows = ref<any[]>([])
const total = ref(0)
const loading = ref(false)
const pageNum = ref(1)
const pageSize = ref(20)

// 筛选
const keyword = ref('')
const empStatus = ref<string | undefined>()
const deptId = ref<number | undefined>()
const gender = ref<string | undefined>()
const postsFilter = ref('')
const rolesFilter = ref('')
const orderBy = ref('')   // 字段:asc|desc

// 状态颜色
const statusColor: Record<string, string> = { 试用: 'orange', 在职: 'green', 离职: 'red', 长假: 'blue' }

// 表格列（a-table）：sortable → 后端 orderBy；fixed 操作列
const ALL_COLUMNS = [
  { title: '工号', dataIndex: 'job_no', key: 'job_no', width: 100, sorter: true },
  { title: '姓名', dataIndex: 'name', key: 'name', width: 120, sorter: true },
  { title: '性别', dataIndex: 'gender', key: 'gender', width: 70 },
  { title: '手机号', dataIndex: 'phone_masked', key: 'phone_masked', width: 120 },
  { title: '车间', dataIndex: 'dept_name', key: 'dept_name', width: 120, sorter: true },
  { title: '小组', dataIndex: 'group_name', key: 'group_name', width: 100, sorter: true },
  { title: '岗位', dataIndex: 'posts', key: 'posts', width: 120, ellipsis: true },
  { title: '角色', dataIndex: 'roles', key: 'roles', width: 120, ellipsis: true },
  { title: '状态', dataIndex: 'emp_status', key: 'emp_status', width: 90, sorter: true },
  { title: '员工卡', dataIndex: 'card_no', key: 'card_no', width: 100, sorter: true },
]

// 列设置：显隐 + 顺序 + 列宽，按"列表页+用户"持久化（localStorage，key 带 userId）
type ColState = { key: string; visible: boolean; width?: number }
const colState = ref<ColState[]>([])
const colStoreKey = computed(() => `person_list:${localStorage.getItem('userId') || 'anon'}`)

function loadColState() {
  try {
    const raw = localStorage.getItem(colStoreKey.value)
    if (raw) {
      const saved: ColState[] = JSON.parse(raw)
      const curKeys = new Set(ALL_COLUMNS.map(c => c.key))
      const ordered: ColState[] = []
      for (const s of saved) if (curKeys.has(s.key)) { ordered.push(s); curKeys.delete(s.key) }
      for (const c of ALL_COLUMNS) if (curKeys.has(c.key)) ordered.push({ key: c.key, visible: true, width: c.width })
      colState.value = ordered
      return
    }
  } catch { /* ignore */ }
  colState.value = ALL_COLUMNS.map(c => ({ key: c.key, visible: true, width: c.width }))
}
function saveColState() { try { localStorage.setItem(colStoreKey.value, JSON.stringify(colState.value)) } catch { /* ignore */ } }
function toggleCol(key: string) {
  const s = colState.value.find(c => c.key === key)
  if (s) { s.visible = !s.visible; saveColState() }
}
function showAllCols() {
  colState.value = ALL_COLUMNS.map(c => ({ key: c.key, visible: true, width: c.width }))
  saveColState()
}
function colTitle(key: string) { return ALL_COLUMNS.find(c => c.key === key)?.title || key }
// 列设置里上下移动排序（拖拽或按钮）
function moveCol(index: number, dir: -1 | 1) {
  const arr = colState.value
  const j = index + dir
  if (j < 0 || j >= arr.length) return
  ;[arr[index], arr[j]] = [arr[j], arr[index]]
  saveColState()
}
onMounted(loadColState)

// 列宽拖拽调整：改 colState 里对应列的 width 并持久化
function onColResize(key: string, w: number) {
  const s = colState.value.find(c => c.key === key)
  if (s) { s.width = Math.max(60, Math.round(w)); saveColState() }
}

// a-table components：表头单元格加 resize 手柄（右侧拖拽改列宽）
const tableComponents = {
  header: {
    cell: defineComponent({
      props: ['column', 'index'],
      setup(props: any, { slots }: any) {
        const onStart = (e: MouseEvent) => {
          const th = (e.currentTarget as HTMLElement).closest('th') as HTMLElement
          if (!th) return
          const startX = e.clientX
          const startW = th.getBoundingClientRect().width
          const key = props.column?.key
          const onMove = (ev: MouseEvent) => { onColResize(key, startW + (ev.clientX - startX)) }
          const onUp = () => {
            document.removeEventListener('mousemove', onMove)
            document.removeEventListener('mouseup', onUp)
            document.body.style.cursor = ''
          }
          document.addEventListener('mousemove', onMove)
          document.addEventListener('mouseup', onUp)
          document.body.style.cursor = 'col-resize'
          e.preventDefault()
          e.stopPropagation()
        }
        return () =>
          h('th', { class: 'zy-resizable-th' }, [
            h('div', { class: 'zy-th-inner' }, slots.default?.()),
            // 除操作列外，右侧 resize 手柄
            props.column?.key !== 'ops'
              ? h('span', { class: 'zy-resize-handle', onMousedown: onStart })
              : null,
          ])
      },
    }),
  },
}

// 按 colState 顺序+显隐生成实际列（操作列固定最后、不可隐藏；应用自定义列宽）
const columns = computed(() => {
  const ops = { title: '操作', key: 'ops', width: 130, fixed: 'right' as const }
  const list = colState.value
    .filter(c => c.visible)
    .map(c => {
      const base = ALL_COLUMNS.find(a => a.key === c.key)!
      return { ...base, width: c.width ?? base.width }
    })
    .filter(Boolean)
  return [...list, ops]
})

// 下拉数据
const depts = ref<any[]>([])
const posts = ref<any[]>([])
const roles = ref<any[]>([])

// ===== 弹窗 =====
const showDlg = ref(false)
const isEdit = ref(false)
const saving = ref(false)
const formRef = ref()

const emptyForm = () => ({
  userId: null as number | null,
  jobNo: '', name: '', gender: '1', email: '', phoneNumber: '', deptId: undefined as number | undefined,
  status: '0', remark: '',
  password: '',
  postIds: [] as number[],
  roleIds: [] as number[],
  ext: {
    maritalStatus: '', certType: '身份证', certNo: '', nation: '', education: '', birthDate: '',
    graduateSchool: '', nativePlace: '', address: '', certExpireDate: '',
    hireDate: '', groupName: '', empType: '正式', leaveDate: '', cardNo: '', empStatus: '试用',
    bankCardNo: '', signEntity: '', contractYears: undefined as number | undefined, contractStart: '', contractEnd: '',
    hasSocial: '0', socialAmount: undefined as number | undefined, socialSubsidy: undefined as number | undefined,
    hasCommercial: '0', commercialType: '', socialCheckDate: '',
    isDorm: '0',
  },
})
const form = ref(emptyForm())

onMounted(() => { loadList(); loadOptions() })

async function loadList() {
  loading.value = true
  try {
    const res: any = await api.get('/system/person/list', {
      params: {
        pageNum: pageNum.value, pageSize: pageSize.value,
        keyword: keyword.value, empStatus: empStatus.value ?? '',
        deptId: deptId.value ?? undefined, gender: gender.value ?? '',
        posts: postsFilter.value, roles: rolesFilter.value, orderBy: orderBy.value,
      },
    })
    if (res.code === 200) { rows.value = res.data.rows; total.value = res.data.total }
  } finally { loading.value = false }
}

// a-table 排序 → 后端 orderBy
function onTableChange(_pag: any, _filt: any, sorter: any) {
  if (sorter?.order) {
    const dir = sorter.order === 'descend' ? 'desc' : 'asc'
    orderBy.value = `${sorter.field}:${dir}`
  } else orderBy.value = ''
  loadList()
}

function onPageChange(p: number, ps: number) { pageNum.value = p; pageSize.value = ps; loadList() }

function showTotalText(t: number) { return `共 ${t} 条` }

// 导出 CSV
async function exportCsv() {
  try {
    const res: any = await api.get('/system/person/export', {
      params: {
        keyword: keyword.value, empStatus: empStatus.value ?? '',
        deptId: deptId.value ?? undefined, gender: gender.value ?? '',
        posts: postsFilter.value, roles: rolesFilter.value,
      },
    })
    if (res.code === 200) {
      const byteChars = atob(res.data.content)
      const bytes = new Uint8Array(byteChars.length)
      for (let i = 0; i < byteChars.length; i++) bytes[i] = byteChars.charCodeAt(i)
      const blob = new Blob([bytes], { type: 'text/csv;charset=utf-8' })
      const a = document.createElement('a')
      a.href = URL.createObjectURL(blob); a.download = res.data.fileName; a.click()
      URL.revokeObjectURL(a.href)
      message.success(`已导出 ${res.data.count} 条`)
    } else message.error(res.msg || '导出失败')
  } catch (e: any) { message.error(e?.response?.data?.msg || '导出失败') }
}

// 导入 CSV（前端解析，逐条新增）
function importFile(e: Event) {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  const reader = new FileReader()
  reader.onload = async () => {
    const text = String(reader.result || '').replace(/^﻿/, '')
    const lines = text.split(/\r?\n/).filter(l => l.trim())
    if (lines.length < 2) { message.error('文件无数据'); input.value = ''; return }
    const header = lines[0].split(',').map(h => h.replace(/"/g, '').trim())
    const idx = (name: string) => header.indexOf(name)
    let ok = 0, fail = 0
    for (let i = 1; i < lines.length; i++) {
      const cols = lines[i].match(/("([^"]|"")*"|[^,]*)/g)?.filter((_, j) => j % 2 === 0).map(c => c.replace(/^"|"$/g, '').replace(/""/g, '"')) || []
      const jobNo = cols[idx('工号')]?.trim()
      const name = cols[idx('姓名')]?.trim()
      if (!jobNo || !name) { fail++; continue }
      try {
        const res: any = await api.post('/system/person', {
          jobNo, name,
          gender: cols[idx('性别')] === '男' ? '1' : '2',
          phoneNumber: cols[idx('手机号')]?.trim() || '',
          postIds: [], roleIds: [],
          ext: { hireDate: new Date().toISOString().slice(0, 10), empStatus: cols[idx('状态')]?.trim() || '试用',
                 groupName: cols[idx('小组')]?.trim() || '', cardNo: cols[idx('员工卡')]?.trim() || '' },
        })
        if (res.code === 200) ok++; else fail++
      } catch { fail++ }
    }
    if (fail) message.warning(`导入完成：成功 ${ok} 条，失败 ${fail} 条`)
    else message.success(`导入完成：成功 ${ok} 条`)
    loadList()
  }
  reader.readAsText(file, 'utf-8')
  input.value = ''
}

async function loadOptions() {
  try {
    const [d, p, r]: any[] = await Promise.all([
      api.get('/system/dept/list'),
      api.get('/system/post/list', { params: { pageNum: 1, pageSize: 100 } }),
      api.get('/system/role/list', { params: { pageNum: 1, pageSize: 100 } }),
    ])
    if (d.code === 200) depts.value = flattenDept(d.data || [])
    if (p.code === 200) posts.value = p.data.rows || []
    if (r.code === 200) roles.value = r.data.rows || []
  } catch { /* ignore */ }
}

function flattenDept(list: any[]): any[] {
  const out: any[] = []
  const walk = (arr: any[]) => arr.forEach(n => { out.push(n); if (n.children?.length) walk(n.children) })
  walk(list)
  return out
}

function openAdd() { isEdit.value = false; form.value = emptyForm(); showDlg.value = true }

async function openEdit(row: any) {
  isEdit.value = true
  const res: any = await api.get(`/system/person/${row.user_id}`)
  if (res.code !== 200) { message.error('加载详情失败'); return }
  const d = res.data
  form.value = {
    userId: d.user_id, jobNo: d.user_name, name: d.nick_name, gender: d.gender || '1',
    email: d.email || '', phoneNumber: d.phone_number || '', deptId: d.dept_id ? Number(d.dept_id) : undefined,
    status: d.status || '0', remark: d.remark || '', password: '',
    postIds: (d.postIds || []).map(Number), roleIds: (d.roleIds || []).map(Number),
    ext: {
      maritalStatus: d.marital_status || '', certType: d.cert_type || '身份证', certNo: d.cert_no || '',
      nation: d.nation || '', education: d.education || '', birthDate: d.birth_date || '',
      graduateSchool: d.graduate_school || '', nativePlace: d.native_place || '', address: d.address || '',
      certExpireDate: d.cert_expire_date || '',
      hireDate: d.hire_date || '', groupName: d.group_name || '', empType: d.emp_type || '正式',
      leaveDate: d.leave_date || '', cardNo: d.card_no || '', empStatus: d.emp_status || '试用',
      bankCardNo: d.bank_card_no || '', signEntity: d.sign_entity || '',
      contractYears: d.contract_years ?? undefined, contractStart: d.contract_start || '', contractEnd: d.contract_end || '',
      hasSocial: d.has_social || '0', socialAmount: d.social_amount ?? undefined, socialSubsidy: d.social_subsidy ?? undefined,
      hasCommercial: d.has_commercial || '0', commercialType: d.commercial_type || '', socialCheckDate: d.social_check_date || '',
      isDorm: d.is_dorm || '0',
    },
  }
  showDlg.value = true
}

// a-form 校验规则
const rules: any = {
  jobNo: [{ required: true, message: '请输入工号', trigger: 'blur' },
          { len: 6, message: '工号必须6位', trigger: 'blur' }],
  name: [{ required: true, message: '请输入姓名', trigger: 'blur' }],
  'ext.hireDate': [{ required: true, message: '请选择入职日期', trigger: 'change' }],
  'ext.empStatus': [{ required: true, message: '请选择在职状态', trigger: 'change' }],
}

async function save() {
  try {
    await formRef.value?.validate()
  } catch { return }
  saving.value = true
  try {
    const payload: any = { ...form.value }
    if (isEdit.value) delete payload.jobNo
    if (!payload.password) delete payload.password
    // 数字字段 undefined → null（后端 numeric 列空值收 null）
    for (const k of ['contractYears', 'socialAmount', 'socialSubsidy']) {
      if (payload.ext[k] === undefined) payload.ext[k] = null
    }
    const res: any = isEdit.value
      ? await api.put('/system/person', payload)
      : await api.post('/system/person', payload)
    if (res.code === 200) { message.success('保存成功'); showDlg.value = false; loadList() }
    else message.error(res.msg || '保存失败')
  } catch (e: any) { message.error(e?.response?.data?.msg || '保存失败') }
  finally { saving.value = false }
}

function resetPwd(row: any) {
  Modal.confirm({
    title: '重置密码',
    content: `确认将「${row.nick_name || row.name}」密码重置为 123456？`,
    okText: '确定', cancelText: '取消',
    onOk: async () => {
      const res: any = await api.put(`/system/person/${row.user_id}/reset-password`)
      if (res.code === 200) message.success('密码已重置为 123456')
      else message.error(res.msg || '重置失败')
    },
  })
}

function resetFilter() {
  keyword.value = ''; empStatus.value = undefined; deptId.value = undefined
  gender.value = undefined; postsFilter.value = ''; rolesFilter.value = ''; orderBy.value = ''
  pageNum.value = 1
  loadList()
}
</script>

<template>
  <div>
    <div class="page-header">
      <div class="head-left">
        <a class="back" @click.prevent="router.push({ name: 'PersonIndex' })" href="javascript:;">←</a>
        <span class="head-title">人员信息</span>
      </div>
      <div class="head-actions">
        <a-button size="middle" @click="exportCsv">↓ 导出</a-button>
        <a-button size="middle" @click="($refs.impInput as HTMLInputElement)?.click()">↑ 导入</a-button>
        <input ref="impInput" type="file" accept=".csv,text/csv" style="display:none" @change="importFile" />
        <a-button type="primary" size="middle" @click="openAdd">+ 新增人员</a-button>
      </div>
    </div>

    <!-- 筛选（a-input/a-select 统一控件） -->
    <div class="filter-bar">
      <a-input v-model:value="keyword" placeholder="搜索工号/姓名/手机号" allow-clear style="width:180px" @press-enter="loadList" />
      <a-select v-model:value="empStatus" placeholder="状态" allow-clear style="width:110px" @change="loadList">
        <a-select-option value="试用">试用</a-select-option>
        <a-select-option value="在职">在职</a-select-option>
        <a-select-option value="离职">离职</a-select-option>
        <a-select-option value="长假">长假</a-select-option>
      </a-select>
      <a-select v-model:value="deptId" placeholder="车间" allow-clear style="width:140px" @change="loadList">
        <a-select-option v-for="d in depts" :key="Number(d.deptId)" :value="Number(d.deptId)">{{ d.deptName }}</a-select-option>
      </a-select>
      <a-select v-model:value="gender" placeholder="性别" allow-clear style="width:90px" @change="loadList">
        <a-select-option value="1">男</a-select-option>
        <a-select-option value="2">女</a-select-option>
      </a-select>
      <a-input v-model:value="postsFilter" placeholder="岗位" allow-clear style="width:110px" @press-enter="loadList" />
      <a-input v-model:value="rolesFilter" placeholder="角色" allow-clear style="width:110px" @press-enter="loadList" />
      <a-button type="primary" @click="loadList">查询</a-button>
      <a-button @click="resetFilter">重置</a-button>
    </div>

    <!-- 列表工具行：条数 + 列设置 -->
    <div class="table-bar">
      <span class="total">共 {{ total }} 条</span>
      <a-dropdown :trigger="['click']">
        <a-button size="middle">⚙ 列设置</a-button>
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
            <a size="small" @click="showAllCols">恢复默认（全部显示+顺序）</a>
          </a-card>
        </template>
      </a-dropdown>
    </div>

    <!-- 列表（a-table：撑满 + 列间竖线 + 列设置） -->
    <a-table
      :columns="columns"
      :data-source="rows"
      :loading="loading"
      :pagination="{ current: pageNum, pageSize, total, showSizeChanger: true, showTotal: showTotalText, onChange: onPageChange, showQuickJumper: true }"
      :scroll="{ x: 'max-content', y: 'calc(100vh - 360px)' }"
      :components="tableComponents"
      row-key="user_id"
      size="middle"
      class="person-table"
      @change="onTableChange"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'gender'">
          {{ record.gender === '1' ? '男' : record.gender === '2' ? '女' : '-' }}
        </template>
        <template v-else-if="column.key === 'emp_status'">
          <a-tag :color="statusColor[record.emp_status]">{{ record.emp_status || '-' }}</a-tag>
        </template>
        <template v-else-if="column.key === 'ops'">
          <a @click="openEdit(record)">编辑</a>
          <a-divider type="vertical" />
          <a @click="resetPwd(record)">重置密码</a>
        </template>
      </template>
    </a-table>

    <!-- 新增/编辑（a-modal + a-form 校验/分组） -->
    <a-modal
      v-model:open="showDlg"
      :title="isEdit ? '编辑人员' : '新增人员'"
      width="960px"
      :confirm-loading="saving"
      ok-text="保存"
      cancel-text="取消"
      @ok="save"
    >
      <a-form ref="formRef" :model="form" :rules="rules" layout="vertical">
        <!-- 基本信息 -->
        <div class="fsec">基本信息</div>
        <a-row :gutter="12">
          <a-col :span="8"><a-form-item label="工号(6位)" name="jobNo"><a-input v-model:value="form.jobNo" :disabled="isEdit" :maxlength="6" placeholder="登录用户名" /></a-form-item></a-col>
          <a-col :span="8"><a-form-item label="姓名" name="name"><a-input v-model:value="form.name" placeholder="请输入" /></a-form-item></a-col>
          <a-col :span="8"><a-form-item label="登录密码"><a-input v-model:value="form.password" type="password" :placeholder="isEdit ? '留空则不修改' : '留空默认 123456'" autocomplete="new-password" /></a-form-item></a-col>
          <a-col :span="8"><a-form-item label="性别"><a-select v-model:value="form.gender"><a-select-option value="1">男</a-select-option><a-select-option value="2">女</a-select-option></a-select></a-form-item></a-col>
          <a-col :span="8"><a-form-item label="婚姻状况"><a-select v-model:value="form.ext.maritalStatus" allow-clear><a-select-option value="">请选择</a-select-option><a-select-option value="未婚">未婚</a-select-option><a-select-option value="已婚">已婚</a-select-option><a-select-option value="离异">离异</a-select-option></a-select></a-form-item></a-col>
          <a-col :span="8"><a-form-item label="证件类型"><a-select v-model:value="form.ext.certType"><a-select-option value="身份证">身份证</a-select-option><a-select-option value="护照">护照</a-select-option><a-select-option value="其他">其他</a-select-option></a-select></a-form-item></a-col>
          <a-col :span="8"><a-form-item label="证件号码"><a-input v-model:value="form.ext.certNo" placeholder="请输入" /></a-form-item></a-col>
          <a-col :span="8"><a-form-item label="民族"><a-input v-model:value="form.ext.nation" placeholder="请输入" /></a-form-item></a-col>
          <a-col :span="8"><a-form-item label="学历"><a-select v-model:value="form.ext.education" allow-clear><a-select-option value="">请选择</a-select-option><a-select-option value="初中">初中</a-select-option><a-select-option value="高中">高中</a-select-option><a-select-option value="中专">中专</a-select-option><a-select-option value="大专">大专</a-select-option><a-select-option value="本科">本科</a-select-option></a-select></a-form-item></a-col>
          <a-col :span="8"><a-form-item label="出生日期"><a-input v-model:value="form.ext.birthDate" type="date" /></a-form-item></a-col>
          <a-col :span="8"><a-form-item label="毕业院校"><a-input v-model:value="form.ext.graduateSchool" placeholder="请输入" /></a-form-item></a-col>
          <a-col :span="8"><a-form-item label="籍贯"><a-input v-model:value="form.ext.nativePlace" placeholder="请输入" /></a-form-item></a-col>
          <a-col :span="8"><a-form-item label="证件到期日"><a-input v-model:value="form.ext.certExpireDate" type="date" /></a-form-item></a-col>
          <a-col :span="16"><a-form-item label="详细地址"><a-input v-model:value="form.ext.address" placeholder="请输入" /></a-form-item></a-col>
          <a-col :span="24"><a-form-item label="备注"><a-input v-model:value="form.remark" placeholder="请输入" /></a-form-item></a-col>
        </a-row>

        <!-- 任职信息 -->
        <div class="fsec">任职信息</div>
        <a-row :gutter="12">
          <a-col :span="8"><a-form-item label="入职日期" name="ext.hireDate"><a-input v-model:value="form.ext.hireDate" type="date" /></a-form-item></a-col>
          <a-col :span="8"><a-form-item label="所属车间(部门)"><a-select v-model:value="form.deptId" allow-clear placeholder="请选择"><a-select-option v-for="d in depts" :key="Number(d.deptId)" :value="Number(d.deptId)">{{ d.deptName }}</a-select-option></a-select></a-form-item></a-col>
          <a-col :span="8"><a-form-item label="所属小组"><a-input v-model:value="form.ext.groupName" placeholder="如 裁剪一组" /></a-form-item></a-col>
          <a-col :span="8"><a-form-item label="员工性质"><a-select v-model:value="form.ext.empType"><a-select-option value="正式">正式</a-select-option><a-select-option value="临时">临时</a-select-option><a-select-option value="劳务">劳务</a-select-option></a-select></a-form-item></a-col>
          <a-col :span="8"><a-form-item label="在职状态" name="ext.empStatus"><a-select v-model:value="form.ext.empStatus"><a-select-option value="试用">试用</a-select-option><a-select-option value="在职">在职</a-select-option><a-select-option value="离职">离职</a-select-option><a-select-option value="长假">长假</a-select-option></a-select></a-form-item></a-col>
          <a-col :span="8"><a-form-item label="员工卡"><a-input v-model:value="form.ext.cardNo" placeholder="工牌号" /></a-form-item></a-col>
          <a-col :span="8"><a-form-item label="离职日期"><a-input v-model:value="form.ext.leaveDate" type="date" /></a-form-item></a-col>
          <a-col :span="12">
            <a-form-item label="岗位（可多选）">
              <a-select v-model:value="form.postIds" mode="multiple" placeholder="选择岗位">
                <a-select-option v-for="p in posts" :key="Number(p.postId)" :value="Number(p.postId)">{{ p.postName }}</a-select-option>
              </a-select>
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="角色（可多选）">
              <a-select v-model:value="form.roleIds" mode="multiple" placeholder="选择角色">
                <a-select-option v-for="r in roles" :key="Number(r.roleId)" :value="Number(r.roleId)">{{ r.roleName }}</a-select-option>
              </a-select>
            </a-form-item>
          </a-col>
        </a-row>

        <!-- 联系方式 -->
        <div class="fsec">联系方式</div>
        <a-row :gutter="12">
          <a-col :span="8"><a-form-item label="手机号(登录)"><a-input v-model:value="form.phoneNumber" :maxlength="11" placeholder="11位，第二登录标识" /></a-form-item></a-col>
          <a-col :span="8"><a-form-item label="邮箱"><a-input v-model:value="form.email" placeholder="请输入" /></a-form-item></a-col>
        </a-row>

        <!-- 合同薪酬 -->
        <div class="fsec">合同薪酬</div>
        <a-row :gutter="12">
          <a-col :span="8"><a-form-item label="银行卡号"><a-input v-model:value="form.ext.bankCardNo" placeholder="敏感，脱敏" /></a-form-item></a-col>
          <a-col :span="8"><a-form-item label="签约主体"><a-input v-model:value="form.ext.signEntity" placeholder="合同签约公司" /></a-form-item></a-col>
          <a-col :span="8"><a-form-item label="合同年限"><a-input-number v-model:value="form.ext.contractYears" :min="0" style="width:100%" placeholder="年" /></a-form-item></a-col>
          <a-col :span="8"><a-form-item label="合同签订日期"><a-input v-model:value="form.ext.contractStart" type="date" /></a-form-item></a-col>
          <a-col :span="8"><a-form-item label="合同截止日期"><a-input v-model:value="form.ext.contractEnd" type="date" /></a-form-item></a-col>
        </a-row>

        <!-- 社保保险登记 -->
        <div class="fsec">社保保险登记</div>
        <a-row :gutter="12">
          <a-col :span="8"><a-form-item label="是否缴纳社保"><a-select v-model:value="form.ext.hasSocial"><a-select-option value="0">否</a-select-option><a-select-option value="1">是</a-select-option></a-select></a-form-item></a-col>
          <a-col :span="8"><a-form-item label="社保缴纳金额(元/月)"><a-input-number v-model:value="form.ext.socialAmount" :min="0" style="width:100%" /></a-form-item></a-col>
          <a-col :span="8"><a-form-item label="社保补助(元/月)"><a-input-number v-model:value="form.ext.socialSubsidy" :min="0" style="width:100%" /></a-form-item></a-col>
          <a-col :span="8"><a-form-item label="是否缴纳商业险"><a-select v-model:value="form.ext.hasCommercial"><a-select-option value="0">否</a-select-option><a-select-option value="1">是</a-select-option></a-select></a-form-item></a-col>
          <a-col :span="8"><a-form-item label="商业险种"><a-input v-model:value="form.ext.commercialType" placeholder="如 意外险/雇主责任险" /></a-form-item></a-col>
          <a-col :span="8"><a-form-item label="社保核对日期"><a-input v-model:value="form.ext.socialCheckDate" type="date" /></a-form-item></a-col>
          <a-col :span="8"><a-form-item label="是否住宿(只登记)"><a-select v-model:value="form.ext.isDorm"><a-select-option value="0">否</a-select-option><a-select-option value="1">是</a-select-option></a-select></a-form-item></a-col>
        </a-row>
      </a-form>
    </a-modal>
  </div>
</template>

<style scoped>
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; gap: 12px; flex-wrap: wrap; }
.head-left { display: flex; align-items: center; gap: 6px; }
.back { color: #4E5969; cursor: pointer; font-size: 18px; text-decoration: none; }
.back:hover { color: #1E5EFF; }
.head-title { font-size: 16px; font-weight: 600; color: #1D2129; }
.head-actions { display: flex; gap: 8px; align-items: center; }

.filter-bar { display: flex; gap: 8px; align-items: center; flex-wrap: wrap; background: #fff; padding: 12px; border-radius: 10px; box-shadow: 0 2px 8px rgba(29,33,41,.06); margin-bottom: 12px; }

/* 表格工具行（条数 + 列设置） */
.table-bar { display: flex; align-items: center; justify-content: space-between; margin-bottom: 8px; }
.table-bar .total { font-size: 13px; color: #4E5969; }
.col-set-panel { width: 200px; border-radius: 8px; box-shadow: 0 6px 20px rgba(29,33,41,.12); }
.col-set-item { display: flex; align-items: center; justify-content: space-between; padding: 2px 8px; }
.col-move { display: inline-flex; }
.col-move .ant-btn { padding: 0 4px; height: 20px; line-height: 1; color: #86909C; }
.col-move .ant-btn:hover:not(:disabled) { color: #1E5EFF; }

/* 表格：撑满容器 + 列间浅竖线（不加粗外框，保持美观） */
.person-table { width: 100%; background: #fff; border-radius: 10px; }
.person-table :deep(.ant-table) { font-size: 13px; }
/* 列间竖线：只给单元格加右侧浅分割线，不加整表外框 */
.person-table :deep(.ant-table-thead > tr > th),
.person-table :deep(.ant-table-tbody > tr > td) { border-right: 1px solid #F0F2F5; }
.person-table :deep(.ant-table-thead > tr > th:last-child),
.person-table :deep(.ant-table-tbody > tr > td:last-child) { border-right: none; }
/* 表头底部、行底部分隔线 */
.person-table :deep(.ant-table-thead > tr > th) { background: #FAFBFD; }
.person-table :deep(.ant-table-tbody > tr:hover > td) { background: #F5F8FF; }

/* 列宽拖拽：表头右侧 resize 手柄 */
.zy-resizable-th { position: relative; }
.zy-th-inner { display: inline-block; max-width: calc(100% - 12px); overflow: hidden; text-overflow: ellipsis; }
.zy-resize-handle { position: absolute; right: 0; top: 0; bottom: 0; width: 8px; cursor: col-resize; z-index: 1; }
.zy-resize-handle:hover, .zy-resize-handle:active { background: #1E5EFF; opacity: .35; }

.fsec { font-size: 14px; font-weight: 600; color: #1D2129; margin: 16px 0 12px; padding-left: 8px; border-left: 3px solid #1E5EFF; }
.fsec:first-child { margin-top: 0; }
</style>
