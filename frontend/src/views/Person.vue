<script setup lang="ts">
/**
 * 人员信息（人员模块）：
 * - 列表：工号/姓名/性别/手机(脱敏)/车间/小组/岗位/角色/状态/员工卡
 * - 新增/编辑：表单按四块分组（基本信息/任职/联系方式/合同薪酬/社保保险）
 * - 建人员=建登录账号（工号6位，默认密码123456，可重置）
 * - 岗位/角色多选（RuoYi sys_post/sys_role）；车间=部门 sys_dept
 * - 复用 TopBar + FunctionCardGrid 不涉及，本页是列表页
 */
import { ref, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import api from '../api'
import DataTable from '../components/DataTable.vue'

const router = useRouter()

// 列表
const rows = ref<any[]>([])
const total = ref(0)
const loading = ref(false)
// 筛选（做全：keyword/状态/车间/性别/岗位/角色）
const keyword = ref('')
const empStatus = ref('')
const deptId = ref<number | null>(null)
const gender = ref('')
const postsFilter = ref('')
const rolesFilter = ref('')
const orderBy = ref('')   // 字段:asc|desc

// 表格列定义（key 对应后端返回字段；sum=数值合计列）
const columns = [
  { key: 'job_no', title: '工号', sortable: true },
  { key: 'name', title: '姓名', sortable: true },
  { key: 'gender', title: '性别' },
  { key: 'phone_masked', title: '手机号' },
  { key: 'dept_name', title: '车间', sortable: true },
  { key: 'group_name', title: '小组', sortable: true },
  { key: 'posts', title: '岗位' },
  { key: 'roles', title: '角色' },
  { key: 'emp_status', title: '状态', sortable: true },
  { key: 'card_no', title: '员工卡', sortable: true },
]

// 下拉数据
const depts = ref<any[]>([])       // 车间（部门树，展平）
const posts = ref<any[]>([])       // 岗位
const roles = ref<any[]>([])       // 角色

// 弹窗
const showDialog = ref(false)
const isEdit = ref(false)
const saving = ref(false)
const msg = ref('')
const msgType = ref<'success' | 'error'>('success')

const emptyForm = () => ({
  userId: null as number | null,
  jobNo: '', name: '', gender: '1', email: '', phoneNumber: '', deptId: null as number | null,
  status: '0', remark: '',
  postIds: [] as number[],
  roleIds: [] as number[],
  ext: {
    maritalStatus: '', certType: '身份证', certNo: '', nation: '', education: '', birthDate: '',
    graduateSchool: '', nativePlace: '', address: '', certExpireDate: '',
    hireDate: '', groupName: '', empType: '正式', leaveDate: '', cardNo: '', empStatus: '试用',
    bankCardNo: '', signEntity: '', contractYears: null as number | null, contractStart: '', contractEnd: '',
    hasSocial: '0', socialAmount: null as number | null, socialSubsidy: null as number | null,
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
        pageNum: 1, pageSize: 100,
        keyword: keyword.value, empStatus: empStatus.value,
        deptId: deptId.value ?? undefined, gender: gender.value,
        posts: postsFilter.value, roles: rolesFilter.value, orderBy: orderBy.value,
      },
    })
    if (res.code === 200) { rows.value = res.data.rows; total.value = res.data.total }
  } finally { loading.value = false }
}

// 表格排序回调 → 传后端 orderBy
function onSort(key: string, dir: 'asc' | 'desc') {
  orderBy.value = `${key}:${dir}`
  loadList()
}

// 导出（CSV，Excel 可开）
async function exportCsv() {
  try {
    const res: any = await api.get('/system/person/export', {
      params: {
        keyword: keyword.value, empStatus: empStatus.value,
        deptId: deptId.value ?? undefined, gender: gender.value,
        posts: postsFilter.value, roles: rolesFilter.value,
      },
    })
    if (res.code === 200) {
      const byteChars = atob(res.data.content)
      const bytes = new Uint8Array(byteChars.length)
      for (let i = 0; i < byteChars.length; i++) bytes[i] = byteChars.charCodeAt(i)
      const blob = new Blob([bytes], { type: 'text/csv;charset=utf-8' })
      const a = document.createElement('a')
      a.href = URL.createObjectURL(blob)
      a.download = res.data.fileName
      a.click()
      URL.revokeObjectURL(a.href)
      showMsg(`已导出 ${res.data.count} 条`)
    } else showMsg(res.msg || '导出失败', 'error')
  } catch (e: any) { showMsg(e?.response?.data?.msg || '导出失败', 'error') }
}

// 导入（前端解析 CSV，逐条调新增；简单批量）
function importFile(e: Event) {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  const reader = new FileReader()
  reader.onload = async () => {
    const text = String(reader.result || '').replace(/^﻿/, '')
    const lines = text.split(/\r?\n/).filter(l => l.trim())
    if (lines.length < 2) { showMsg('文件无数据', 'error'); input.value = ''; return }
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
    showMsg(`导入完成：成功 ${ok} 条，失败 ${fail} 条`, fail ? 'error' : 'success')
    loadList()
  }
  reader.readAsText(file, 'utf-8')
  input.value = ''
}

async function loadOptions() {
  try {
    const [d, p, r] = await Promise.all([
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

function openAdd() { isEdit.value = false; form.value = emptyForm(); showDialog.value = true }

async function openEdit(row: any) {
  isEdit.value = true
  const res: any = await api.get(`/system/person/${row.user_id}`)
  if (res.code !== 200) { showMsg('加载详情失败', 'error'); return }
  const d = res.data
  form.value = {
    userId: d.user_id, jobNo: d.user_name, name: d.nick_name, gender: d.gender || '1',
    email: d.email || '', phoneNumber: d.phone_number || '', deptId: d.dept_id ? Number(d.dept_id) : null,
    status: d.status || '0', remark: d.remark || '',
    postIds: (d.postIds || []).map(Number), roleIds: (d.roleIds || []).map(Number),
    ext: {
      maritalStatus: d.marital_status || '', certType: d.cert_type || '身份证', certNo: d.cert_no || '',
      nation: d.nation || '', education: d.education || '', birthDate: d.birth_date || '',
      graduateSchool: d.graduate_school || '', nativePlace: d.native_place || '', address: d.address || '',
      certExpireDate: d.cert_expire_date || '',
      hireDate: d.hire_date || '', groupName: d.group_name || '', empType: d.emp_type || '正式',
      leaveDate: d.leave_date || '', cardNo: d.card_no || '', empStatus: d.emp_status || '试用',
      bankCardNo: d.bank_card_no || '', signEntity: d.sign_entity || '',
      contractYears: d.contract_years ?? null, contractStart: d.contract_start || '', contractEnd: d.contract_end || '',
      hasSocial: d.has_social || '0', socialAmount: d.social_amount ?? null, socialSubsidy: d.social_subsidy ?? null,
      hasCommercial: d.has_commercial || '0', commercialType: d.commercial_type || '', socialCheckDate: d.social_check_date || '',
      isDorm: d.is_dorm || '0',
    },
  }
  showDialog.value = true
}

async function save() {
  if (!form.value.jobNo || form.value.jobNo.length !== 6) { showMsg('工号必须6位数字', 'error'); return }
  if (!form.value.name) { showMsg('请输入姓名', 'error'); return }
  if (!isEdit.value && !form.value.ext.hireDate) { showMsg('请选择入职日期', 'error'); return }
  saving.value = true
  try {
    const payload: any = { ...form.value }
    if (isEdit.value) delete payload.jobNo // 编辑不改工号
    const res: any = isEdit.value
      ? await api.put('/system/person', payload)
      : await api.post('/system/person', payload)
    if (res.code === 200) { showMsg('保存成功'); showDialog.value = false; loadList() }
    else showMsg(res.msg || '保存失败', 'error')
  } catch (e: any) { showMsg(e?.response?.data?.msg || '保存失败', 'error') }
  finally { saving.value = false }
}

async function resetPwd(row: any) {
  if (!confirm(`确认将「${row.name}」密码重置为 123456？`)) return
  const res: any = await api.put(`/system/person/${row.user_id}/reset-password`)
  if (res.code === 200) showMsg('密码已重置为 123456')
  else showMsg(res.msg || '重置失败', 'error')
}

function showMsg(t: string, type: 'success' | 'error' = 'success') { msg.value = t; msgType.value = type; setTimeout(() => { msg.value = '' }, 3000) }
function close() { showDialog.value = false }

function resetFilter() {
  keyword.value = ''; empStatus.value = ''; deptId.value = null
  gender.value = ''; postsFilter.value = ''; rolesFilter.value = ''; orderBy.value = ''
  loadList()
}

// 选中岗位/角色（多选切换）
function toggleId(arr: number[], id: number) {
  const i = arr.indexOf(id)
  if (i >= 0) arr.splice(i, 1); else arr.push(id)
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
        <button class="btn" @click="exportCsv">↓ 导出</button>
        <label class="btn imp">↑ 导入<input type="file" accept=".csv,text/csv" @change="importFile" hidden /></label>
        <button class="btn-primary" @click="openAdd">+ 新增人员</button>
      </div>
    </div>

    <!-- 筛选条件（做全：常用+全量） -->
    <div class="filter-bar">
      <input v-model="keyword" placeholder="搜索工号/姓名/手机号" @keyup.enter="loadList" />
      <select v-model="empStatus" @change="loadList">
        <option value="">状态</option>
        <option>试用</option><option>在职</option><option>离职</option><option>长假</option>
      </select>
      <select v-model="deptId" @change="loadList">
        <option :value="null">车间</option>
        <option v-for="d in depts" :key="d.deptId" :value="Number(d.deptId)">{{ d.deptName }}</option>
      </select>
      <select v-model="gender" @change="loadList">
        <option value="">性别</option>
        <option value="1">男</option><option value="2">女</option>
      </select>
      <input v-model="postsFilter" placeholder="岗位" @keyup.enter="loadList" />
      <input v-model="rolesFilter" placeholder="角色" @keyup.enter="loadList" />
      <button class="btn" @click="loadList">查询</button>
      <button class="btn ghost" @click="resetFilter">重置</button>
    </div>

    <div v-if="msg" class="msg" :class="'msg-' + msgType">{{ msg }}</div>

    <!-- 列表（系统级 DataTable：排序/列显隐/列顺序/合计行，配置按页+用户持久化） -->
    <DataTable
      storage-key="person_list"
      :columns="columns"
      :rows="rows"
      :loading="loading"
      @sort="onSort"
    >
      <template #cell-gender="{ value }">{{ value === '1' ? '男' : value === '2' ? '女' : '-' }}</template>
      <template #cell-emp_status="{ value }"><span class="tag" :class="'st-' + (value || '')">{{ value || '-' }}</span></template>
      <template #cell-ops="{ row }">
        <a @click="openEdit(row)">编辑</a>
        <a @click="resetPwd(row)">重置密码</a>
      </template>
    </DataTable>

    <!-- 新增/编辑弹窗 -->
    <div v-if="showDialog" class="mask" @click.self="close">
      <div class="dialog">
        <div class="dialog-title">{{ isEdit ? '编辑人员' : '新增人员' }}</div>
        <div class="dialog-body">
          <!-- 基本信息 -->
          <section class="fsec">
            <div class="fsec-title">基本信息</div>
            <div class="fgrid">
              <label><span class="req">*</span>工号(6位)<input v-model="form.jobNo" :disabled="isEdit" maxlength="6" placeholder="登录用户名" /></label>
              <label><span class="req">*</span>姓名<input v-model="form.name" placeholder="请输入" /></label>
              <label>性别
                <select v-model="form.gender"><option value="1">男</option><option value="2">女</option></select>
              </label>
              <label>婚姻状况
                <select v-model="form.ext.maritalStatus"><option value="">请选择</option><option>未婚</option><option>已婚</option><option>离异</option></select>
              </label>
              <label>证件类型
                <select v-model="form.ext.certType"><option>身份证</option><option>护照</option><option>其他</option></select>
              </label>
              <label>证件号码<input v-model="form.ext.certNo" placeholder="请输入" /></label>
              <label>民族<input v-model="form.ext.nation" placeholder="请输入" /></label>
              <label>学历
                <select v-model="form.ext.education"><option value="">请选择</option><option>初中</option><option>高中</option><option>中专</option><option>大专</option><option>本科</option></select>
              </label>
              <label>出生日期<input v-model="form.ext.birthDate" type="date" /></label>
              <label>毕业院校<input v-model="form.ext.graduateSchool" placeholder="请输入" /></label>
              <label>籍贯<input v-model="form.ext.nativePlace" placeholder="请输入" /></label>
              <label>证件到期日<input v-model="form.ext.certExpireDate" type="date" /></label>
              <label class="col2">详细地址<input v-model="form.ext.address" placeholder="请输入" /></label>
              <label class="col2">备注<input v-model="form.remark" placeholder="请输入" /></label>
            </div>
          </section>

          <!-- 任职信息 -->
          <section class="fsec">
            <div class="fsec-title">任职信息</div>
            <div class="fgrid">
              <label><span class="req">*</span>入职日期<input v-model="form.ext.hireDate" type="date" /></label>
              <label><span class="req">*</span>所属车间(部门)
                <select v-model="form.deptId"><option :value="null">请选择</option><option v-for="d in depts" :key="d.deptId" :value="Number(d.deptId)">{{ d.deptName }}</option></select>
              </label>
              <label>所属小组<input v-model="form.ext.groupName" placeholder="如 裁剪一组" /></label>
              <label>员工性质
                <select v-model="form.ext.empType"><option>正式</option><option>临时</option><option>劳务</option></select>
              </label>
              <label><span class="req">*</span>在职状态
                <select v-model="form.ext.empStatus"><option>试用</option><option>在职</option><option>离职</option><option>长假</option></select>
              </label>
              <label>员工卡<input v-model="form.ext.cardNo" placeholder="工牌号" /></label>
              <label>离职日期<input v-model="form.ext.leaveDate" type="date" /></label>
            </div>
            <!-- 岗位/角色多选 -->
            <div class="fgrid mt">
              <div class="col2">
                <div class="sub-label"><span class="req">*</span>岗位（可多选，带默认角色）</div>
                <div class="chips">
                  <span v-for="p in posts" :key="p.postId" class="chip" :class="{ on: form.postIds.includes(Number(p.postId)) }"
                    @click="toggleId(form.postIds, Number(p.postId))">{{ p.postName }}</span>
                  <span v-if="posts.length === 0" class="tip">暂无岗位</span>
                </div>
              </div>
              <div class="col2">
                <div class="sub-label"><span class="req">*</span>角色（可多选）</div>
                <div class="chips">
                  <span v-for="r in roles" :key="r.roleId" class="chip" :class="{ on: form.roleIds.includes(Number(r.roleId)) }"
                    @click="toggleId(form.roleIds, Number(r.roleId))">{{ r.roleName }}</span>
                  <span v-if="roles.length === 0" class="tip">暂无角色</span>
                </div>
              </div>
            </div>
          </section>

          <!-- 联系方式 -->
          <section class="fsec">
            <div class="fsec-title">联系方式</div>
            <div class="fgrid">
              <label>手机号（登录）<input v-model="form.phoneNumber" maxlength="11" placeholder="11位，第二登录标识" /></label>
              <label>邮箱<input v-model="form.email" placeholder="请输入" /></label>
            </div>
          </section>

          <!-- 合同薪酬 -->
          <section class="fsec">
            <div class="fsec-title">合同薪酬</div>
            <div class="fgrid">
              <label>银行卡号<input v-model="form.ext.bankCardNo" placeholder="敏感，脱敏" /></label>
              <label>签约主体<input v-model="form.ext.signEntity" placeholder="合同签约公司" /></label>
              <label>合同年限<input v-model.number="form.ext.contractYears" type="number" placeholder="年" /></label>
              <label>合同签订日期<input v-model="form.ext.contractStart" type="date" /></label>
              <label>合同截止日期<input v-model="form.ext.contractEnd" type="date" /></label>
            </div>
          </section>

          <!-- 社保保险登记 -->
          <section class="fsec">
            <div class="fsec-title">社保保险登记</div>
            <div class="fgrid">
              <label>是否缴纳社保
                <select v-model="form.ext.hasSocial"><option value="0">否</option><option value="1">是</option></select>
              </label>
              <label>社保缴纳金额(元/月)<input v-model.number="form.ext.socialAmount" type="number" /></label>
              <label>社保补助(元/月)<input v-model.number="form.ext.socialSubsidy" type="number" /></label>
              <label>是否缴纳商业险
                <select v-model="form.ext.hasCommercial"><option value="0">否</option><option value="1">是</option></select>
              </label>
              <label>商业险种<input v-model="form.ext.commercialType" placeholder="如 意外险/雇主责任险" /></label>
              <label>社保核对日期<input v-model="form.ext.socialCheckDate" type="date" /></label>
              <label>是否住宿（只登记）
                <select v-model="form.ext.isDorm"><option value="0">否</option><option value="1">是</option></select>
              </label>
            </div>
          </section>
        </div>
        <div class="dialog-foot">
          <button class="btn" @click="close">取消</button>
          <button class="btn-primary" :disabled="saving" @click="save">{{ saving ? '保存中...' : '保存' }}</button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; gap: 12px; flex-wrap: wrap; }
.head-left { display: flex; align-items: center; gap: 6px; }
.back { color: #4E5969; cursor: pointer; font-size: 18px; text-decoration: none; }
.back:hover { color: #1E5EFF; }
.head-title { font-size: 16px; font-weight: 600; color: #1D2129; }
.head-actions { display: flex; gap: 8px; align-items: center; }
.imp { display: inline-flex; align-items: center; cursor: pointer; }
.filter-bar { display: flex; gap: 8px; align-items: center; flex-wrap: wrap; background: #fff; padding: 10px 12px; border-radius: 10px; box-shadow: 0 2px 8px rgba(29,33,41,.06); margin-bottom: 12px; }
.filter-bar input, .filter-bar select { height: 32px; border: 1px solid #E5E8EF; border-radius: 6px; padding: 0 10px; font-size: 13px; outline: none; }
.filter-bar input { width: 160px; }
.filter-bar input:focus, .filter-bar select:focus { border-color: #1E5EFF; }
.btn { height: 32px; padding: 0 14px; background: #fff; border: 1px solid #E5E8EF; border-radius: 6px; font-size: 13px; color: #4E5969; cursor: pointer; }
.btn:hover { border-color: #1E5EFF; color: #1E5EFF; }
.btn.ghost { background: transparent; }
.btn-primary { height: 32px; padding: 0 16px; background: #1E5EFF; border: none; border-radius: 6px; font-size: 13px; color: #fff; cursor: pointer; }
.btn-primary:hover { background: #1747c7; }
.btn-primary:disabled { opacity: .6; cursor: not-allowed; }

.msg { padding: 8px 12px; border-radius: 6px; font-size: 13px; margin-bottom: 12px; }
.msg-success { background: #E8F8F0; color: #00B388; border: 1px solid #B7E8D0; }
.msg-error { background: #FEECEC; color: #F53F3F; border: 1px solid #FAC7C7; }

.tag { padding: 2px 8px; border-radius: 4px; font-size: 12px; }
.st-试用 { background: #FFF7E8; color: #FF9500; }
.st-在职 { background: #E8F8F0; color: #00B388; }
.st-离职 { background: #FEECEC; color: #F53F3F; }
.st-长假 { background: #E8F0FF; color: #1E5EFF; }
.ops a { color: #1E5EFF; cursor: pointer; margin-right: 10px; }
.ops a:hover { text-decoration: underline; }

/* 弹窗 */
.mask { position: fixed; inset: 0; background: rgba(29,33,41,.45); display: flex; align-items: center; justify-content: center; z-index: 100; }
.dialog { width: 920px; max-width: 95vw; max-height: 90vh; background: #fff; border-radius: 12px; display: flex; flex-direction: column; }
.dialog-title { font-size: 16px; font-weight: 600; padding: 16px 20px; border-bottom: 1px solid #F0F2F5; }
.dialog-body { flex: 1; overflow: auto; padding: 16px 20px; }
.dialog-foot { padding: 12px 20px; border-top: 1px solid #F0F2F5; display: flex; justify-content: flex-end; gap: 10px; }

.fsec { margin-bottom: 20px; }
.fsec-title { font-size: 14px; font-weight: 600; color: #1D2129; margin-bottom: 10px; padding-left: 8px; border-left: 3px solid #1E5EFF; }
.fgrid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 12px 16px; }
.fgrid.mt { margin-top: 12px; }
label { display: flex; flex-direction: column; gap: 4px; font-size: 12px; color: #4E5969; }
label input, label select { height: 32px; border: 1px solid #E5E8EF; border-radius: 6px; padding: 0 10px; font-size: 13px; outline: none; color: #1D2129; }
label input:focus, label select:focus { border-color: #1E5EFF; }
label input:disabled { background: #F5F7FA; color: #86909C; }
.col2 { grid-column: span 2; }
.req { color: #F53F3F; margin-right: 2px; }
.sub-label { font-size: 12px; color: #4E5969; margin-bottom: 6px; }
.chips { display: flex; flex-wrap: wrap; gap: 6px; }
.chip { padding: 4px 10px; border: 1px solid #E5E8EF; border-radius: 6px; font-size: 12px; cursor: pointer; color: #4E5969; background: #fff; }
.chip.on { background: #E8F0FF; border-color: #1E5EFF; color: #1E5EFF; }
.tip { color: #C9CDD4; font-size: 12px; }
</style>
