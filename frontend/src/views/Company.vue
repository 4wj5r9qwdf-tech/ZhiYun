<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import api from '../api'

const router = useRouter()
function goBack() { router.push({ name: 'SystemIndex' }) }

// 公司列表
const companies = ref<any[]>([])
const loading = ref(false)

// 编辑弹窗
const showDialog = ref(false)
const isEdit = ref(false)
const form = ref({
  companyId: null as number | null,
  companyName: '',
  logoOssId: null as string | null,
  loginBgOssId: null as string | null,
  logoUrl: '',
  loginBgUrl: '',
  slogan: '',
  loginBgMode: 'A',
  isDefault: '0',
  theme: '',
  status: '0',
})
const uploadingLogo = ref(false)
const uploadingBg = ref(false)
const saving = ref(false)
const msg = ref('')
const msgType = ref<'success' | 'error'>('success')

onMounted(() => {
  loadList()
})

async function loadList() {
  loading.value = true
  try {
    const res: any = await api.get('/system/company/list', { params: { pageNum: 1, pageSize: 50 } })
    if (res.code === 200) {
      companies.value = res.data?.rows || []
    }
  } catch (e: any) {
    showMsg(e?.response?.data?.msg || '加载失败', 'error')
  } finally {
    loading.value = false
  }
}

function showMsg(text: string, type: 'success' | 'error' = 'success') {
  msg.value = text
  msgType.value = type
  setTimeout(() => (msg.value = ''), 3000)
}

function openAdd() {
  isEdit.value = false
  form.value = { companyId: null, companyName: '', logoOssId: null, loginBgOssId: null, logoUrl: '', loginBgUrl: '', slogan: '', loginBgMode: 'A', isDefault: '0', theme: '', status: '0' }
  showDialog.value = true
}

function openEdit(row: any) {
  isEdit.value = true
  form.value = { ...row, logoOssId: row.logo_oss_id, loginBgOssId: row.login_bg_oss_id, logoUrl: row.logo_url, loginBgUrl: row.login_bg_url, companyName: row.company_name, loginBgMode: row.login_bg_mode || 'A', isDefault: row.is_default, theme: row.theme || '', status: row.status }
  showDialog.value = true
}

// 上传文件到 MinIO（走 RuoYi 自带 /system/oss/upload）
async function uploadFile(e: Event, target: 'logo' | 'bg') {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  // 尺寸建议提示
  if (target === 'bg') {
    const ok = confirm(`登录背景图建议尺寸：\n竖版 1080×1920 或 方图 1200×1200\n\n当前图片：${file.name}，继续上传？`)
    if (!ok) { input.value = ''; return }
  }
  const fd = new FormData()
  fd.append('file', file)
  if (target === 'logo') uploadingLogo.value = true
  else uploadingBg.value = true
  try {
    const res: any = await api.post('/resource/oss/upload', fd, { headers: { 'Content-Type': 'multipart/form-data' } })
    if (res.code === 200 && res.data) {
      if (target === 'logo') {
        form.value.logoOssId = res.data.ossId
        form.value.logoUrl = res.data.url
      } else {
        form.value.loginBgOssId = res.data.ossId
        form.value.loginBgUrl = res.data.url
      }
      showMsg('上传成功')
    } else {
      showMsg(res.msg || '上传失败', 'error')
    }
  } catch (e: any) {
    showMsg(e?.response?.data?.msg || '上传失败', 'error')
  } finally {
    if (target === 'logo') uploadingLogo.value = false
    else uploadingBg.value = false
    input.value = ''
  }
}

async function save() {
  if (!form.value.companyName) { showMsg('请输入公司名称', 'error'); return }
  saving.value = true
  try {
    const payload: any = {
      companyName: form.value.companyName,
      logoOssId: form.value.logoOssId,
      loginBgOssId: form.value.loginBgOssId,
      slogan: form.value.slogan,
      loginBgMode: form.value.loginBgMode,
      isDefault: form.value.isDefault,
      theme: form.value.theme,
      status: form.value.status,
    }
    let res: any
    if (isEdit.value) {
      payload.companyId = form.value.companyId
      res = await api.put('/system/company', payload)
    } else {
      res = await api.post('/system/company', payload)
    }
    if (res.code === 200) {
      showMsg(isEdit.value ? '修改成功' : '新增成功')
      showDialog.value = false
      loadList()
    } else {
      showMsg(res.msg || '保存失败', 'error')
    }
  } catch (e: any) {
    showMsg(e?.response?.data?.msg || '保存失败', 'error')
  } finally {
    saving.value = false
  }
}

async function setDefault(row: any) {
  try {
    const res: any = await api.put(`/system/company/default/${row.company_id}`)
    if (res.code === 200) { showMsg('已设为默认展示公司'); loadList() }
    else showMsg(res.msg || '操作失败', 'error')
  } catch (e: any) {
    showMsg(e?.response?.data?.msg || '操作失败', 'error')
  }
}

async function remove(row: any) {
  if (!confirm(`确定删除公司「${row.company_name}」？`)) return
  try {
    const res: any = await api.delete(`/system/company/${row.company_id}`)
    if (res.code === 200) { showMsg('删除成功'); loadList() }
    else showMsg(res.msg || '删除失败', 'error')
  } catch (e: any) {
    showMsg(e?.response?.data?.msg || '删除失败', 'error')
  }
}
</script>

<template>
  <div class="company-page">
    <div class="page-header">
      <h2><a class="back" @click.prevent="goBack" href="javascript:;">←</a> 公司档案</h2>
      <button class="btn-primary" @click="openAdd">+ 新增公司</button>
    </div>

    <!-- 提示条 -->
    <div v-if="msg" class="msg" :class="'msg-' + msgType">{{ msg }}</div>

    <!-- 公司列表 -->
    <div class="company-grid">
      <div v-for="c in companies" :key="c.company_id" class="company-card">
        <div class="card-header">
          <div class="card-logo">
            <img v-if="c.logo_url" :src="c.logo_url" :alt="c.company_name" />
            <div v-else class="logo-ph">织</div>
          </div>
          <div class="card-title">
            <span class="name">{{ c.company_name }}</span>
            <span v-if="c.is_default === '1'" class="badge-default">默认展示</span>
          </div>
        </div>
        <div class="card-body">
          <div class="info-row"><span class="label">宣传语</span><span class="val">{{ c.slogan || '（默认）' }}</span></div>
          <div class="info-row"><span class="label">背景模式</span><span class="val">{{ c.login_bg_mode === 'B' ? 'B 整页' : 'A 左侧' }}</span></div>
          <div class="info-row"><span class="label">登录背景</span><span class="val">{{ c.login_bg_url ? '已上传' : '未上传' }}</span></div>
          <div class="info-row"><span class="label">状态</span><span class="val" :class="c.status === '0' ? 'st-ok' : 'st-off'">{{ c.status === '0' ? '正常' : '停用' }}</span></div>
        </div>
        <div class="card-actions">
          <button class="btn-text" @click="openEdit(c)">编辑</button>
          <button v-if="c.is_default !== '1'" class="btn-text" @click="setDefault(c)">设默认</button>
          <button class="btn-text btn-danger" @click="remove(c)">删除</button>
        </div>
      </div>
      <div v-if="!loading && companies.length === 0" class="empty">暂无公司数据</div>
    </div>

    <!-- 编辑弹窗 -->
    <div v-if="showDialog" class="dialog-mask" @click.self="showDialog = false">
      <div class="dialog">
        <h3>{{ isEdit ? '编辑公司' : '新增公司' }}</h3>
        <div class="form-item">
          <label>公司名称 *</label>
          <input v-model="form.companyName" placeholder="如：河北佰信达实业有限公司" />
        </div>
        <div class="form-item">
          <label>宣传语</label>
          <input v-model="form.slogan" placeholder="留空则用默认：服装代工厂生产管理系统" />
        </div>
        <div class="form-item">
          <label>登录背景显示模式</label>
          <div class="radio-group">
            <label><input type="radio" v-model="form.loginBgMode" value="A" /> A：铺满左侧品牌区</label>
            <label><input type="radio" v-model="form.loginBgMode" value="B" /> B：铺满整页</label>
          </div>
        </div>
        <div class="form-item">
          <label>公司 Logo（建议正方形，如 200×200）</label>
          <div class="upload-row">
            <div class="upload-preview">
              <img v-if="form.logoUrl" :src="form.logoUrl" alt="logo" />
              <div v-else class="upload-ph">未上传</div>
            </div>
            <label class="btn-upload">
              {{ uploadingLogo ? '上传中...' : '选择图片' }}
              <input type="file" accept="image/*" @change="uploadFile($event, 'logo')" :disabled="uploadingLogo" hidden />
            </label>
          </div>
        </div>
        <div class="form-item">
          <label>登录背景图（建议竖版 1080×1920 或方图 1200×1200）</label>
          <div class="upload-row">
            <div class="upload-preview bg">
              <img v-if="form.loginBgUrl" :src="form.loginBgUrl" alt="bg" />
              <div v-else class="upload-ph">未上传</div>
            </div>
            <label class="btn-upload">
              {{ uploadingBg ? '上传中...' : '选择图片' }}
              <input type="file" accept="image/*" @change="uploadFile($event, 'bg')" :disabled="uploadingBg" hidden />
            </label>
          </div>
        </div>
        <div class="form-item">
          <label><input type="checkbox" v-model="form.isDefault" true-value="1" false-value="0" /> 设为默认展示公司（登录页/首页显示）</label>
        </div>
        <div class="dialog-actions">
          <button class="btn-cancel" @click="showDialog = false">取消</button>
          <button class="btn-primary" :disabled="saving" @click="save">{{ saving ? '保存中...' : '保存' }}</button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
/* 工业蓝主题（前端规范 B 节） */
.company-page { padding: 24px; }
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; }
.page-header h2 { font-size: 20px; color: #1d2129; margin: 0; }
.page-header .back { color: #4E5969; cursor: pointer; font-size: 18px; margin-right: 4px; text-decoration: none; }
.page-header .back:hover { color: #1E5EFF; }
.btn-primary { height: 36px; padding: 0 18px; background: #1E5EFF; color: #fff; border: none; border-radius: 6px; font-size: 14px; cursor: pointer; }
.btn-primary:hover:not(:disabled) { background: #0f3db8; }
.btn-primary:disabled { opacity: 0.6; cursor: not-allowed; }
.msg { padding: 10px 14px; border-radius: 6px; font-size: 14px; margin-bottom: 16px; }
.msg-success { background: #e8faf3; color: #00b388; border: 1px solid #b2e8d4; }
.msg-error { background: #ffece8; color: #f53f3f; border: 1px solid #fcc4bd; }
.company-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(340px, 1fr)); gap: 16px; }
.company-card { background: #fff; border-radius: 8px; box-shadow: 0 2px 8px rgba(29,33,41,.06); overflow: hidden; }
.card-header { display: flex; align-items: center; gap: 12px; padding: 16px; border-bottom: 1px solid #f2f3f5; }
.card-logo { width: 44px; height: 44px; border-radius: 8px; background: #1E5EFF; display: flex; align-items: center; justify-content: center; overflow: hidden; flex-shrink: 0; }
.card-logo img { width: 100%; height: 100%; object-fit: contain; }
.logo-ph { color: #fff; font-weight: 700; font-size: 18px; }
.card-title { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.card-title .name { font-size: 15px; font-weight: 600; color: #1d2129; }
.badge-default { background: #e8f0ff; color: #1E5EFF; font-size: 11px; padding: 2px 8px; border-radius: 10px; }
.card-body { padding: 12px 16px; }
.info-row { display: flex; font-size: 13px; margin-bottom: 8px; }
.info-row .label { color: #86909c; width: 72px; flex-shrink: 0; }
.info-row .val { color: #1d2129; }
.st-ok { color: #00b388; }
.st-off { color: #86909c; }
.card-actions { display: flex; gap: 4px; padding: 12px 16px; border-top: 1px solid #f2f3f5; }
.btn-text { background: none; border: none; color: #1E5EFF; font-size: 13px; cursor: pointer; padding: 4px 8px; border-radius: 4px; }
.btn-text:hover { background: #e8f0ff; }
.btn-danger { color: #f53f3f; }
.btn-danger:hover { background: #ffece8; }
.empty { grid-column: 1/-1; text-align: center; color: #86909c; padding: 60px 0; }
.dialog-mask { position: fixed; inset: 0; background: rgba(0,0,0,.4); display: flex; align-items: center; justify-content: center; z-index: 100; }
.dialog { background: #fff; border-radius: 12px; padding: 24px; width: 520px; max-height: 90vh; overflow-y: auto; }
.dialog h3 { font-size: 18px; color: #1d2129; margin: 0 0 20px; }
.form-item { margin-bottom: 18px; }
.form-item label { display: block; font-size: 13px; color: #4e5969; margin-bottom: 6px; font-weight: 500; }
.form-item input[type=text], .form-item input:not([type]) { width: 100%; height: 38px; border: 1px solid #e5e8ef; border-radius: 6px; padding: 0 12px; font-size: 14px; box-sizing: border-box; }
.form-item input:focus { border-color: #1E5EFF; outline: none; }
.radio-group { display: flex; gap: 16px; }
.radio-group label { display: flex; align-items: center; gap: 6px; font-weight: 400; color: #1d2129; }
.upload-row { display: flex; align-items: center; gap: 12px; }
.upload-preview { width: 80px; height: 80px; border: 1px dashed #e5e8ef; border-radius: 6px; display: flex; align-items: center; justify-content: center; overflow: hidden; }
.upload-preview.bg { width: 120px; height: 68px; }
.upload-preview img { width: 100%; height: 100%; object-fit: cover; }
.upload-ph { color: #c9cdd4; font-size: 12px; }
.btn-upload { height: 32px; padding: 0 14px; background: #fff; border: 1px solid #e5e8ef; border-radius: 6px; color: #1d2129; font-size: 13px; cursor: pointer; display: inline-flex; align-items: center; }
.btn-upload:hover { border-color: #1E5EFF; color: #1E5EFF; }
.dialog-actions { display: flex; justify-content: flex-end; gap: 10px; margin-top: 24px; }
.btn-cancel { height: 36px; padding: 0 18px; background: #fff; border: 1px solid #e5e8ef; border-radius: 6px; color: #4e5969; font-size: 14px; cursor: pointer; }
.btn-cancel:hover { border-color: #1E5EFF; color: #1E5EFF; }
</style>
