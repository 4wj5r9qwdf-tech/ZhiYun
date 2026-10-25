<script setup lang="ts">
/**
 * 公司档案（系统模块）—— ant-design-vue 重构版：
 * - a-card 卡片列表（logo/宣传语/背景模式/状态/默认标记）
 * - a-modal + a-form + 校验；a-upload.Dragger 不用，用自定义上传（走 /resource/oss/upload）
 * - message / Modal.confirm 全局反馈
 * - 与 Person.vue 一致的 antd 风格
 */
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { message, Modal } from 'ant-design-vue'
import api from '../api'

const router = useRouter()
function goBack() { router.push({ name: 'SystemIndex' }) }

// 公司列表
const companies = ref<any[]>([])
const loading = ref(false)

// 编辑弹窗
const showDlg = ref(false)
const isEdit = ref(false)
const saving = ref(false)
const formRef = ref()
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

const rules: any = {
  companyName: [{ required: true, message: '请输入公司名称', trigger: 'blur' }],
}

onMounted(() => { loadList() })

async function loadList() {
  loading.value = true
  try {
    const res: any = await api.get('/system/company/list', { params: { pageNum: 1, pageSize: 50 } })
    if (res.code === 200) companies.value = res.data?.rows || []
    else { companies.value = []; message.error(res.msg || '加载失败') }
  } catch (e: any) {
    companies.value = []
    message.error(e?.response?.data?.msg || '加载失败')
  } finally { loading.value = false }
}

function openAdd() {
  isEdit.value = false
  form.value = { companyId: null, companyName: '', logoOssId: null, loginBgOssId: null, logoUrl: '', loginBgUrl: '', slogan: '', loginBgMode: 'A', isDefault: '0', theme: '', status: '0' }
  showDlg.value = true
}

function openEdit(row: any) {
  isEdit.value = true
  form.value = { companyId: row.company_id, companyName: row.company_name, logoOssId: row.logo_oss_id, loginBgOssId: row.login_bg_oss_id, logoUrl: row.logo_url, loginBgUrl: row.login_bg_url, slogan: row.slogan || '', loginBgMode: row.login_bg_mode || 'A', isDefault: row.is_default, theme: row.theme || '', status: row.status }
  showDlg.value = true
}

// 上传图片到 MinIO（走 /resource/oss/upload）
async function uploadFile(e: Event, target: 'logo' | 'bg') {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  if (target === 'bg') {
    Modal.confirm({
      title: '上传登录背景图',
      content: '建议竖版 1080×1920 或方图 1200×1200。继续上传？',
      okText: '继续', cancelText: '取消',
      onOk: () => doUpload(file, target, input),
      onCancel: () => { input.value = '' },
    })
    return
  }
  await doUpload(file, target, input)
}

async function doUpload(file: File, target: 'logo' | 'bg', input: HTMLInputElement) {
  const fd = new FormData()
  fd.append('file', file)
  if (target === 'logo') uploadingLogo.value = true
  else uploadingBg.value = true
  try {
    const res: any = await api.post('/resource/oss/upload', fd, { headers: { 'Content-Type': 'multipart/form-data' } })
    if (res.code === 200 && res.data) {
      if (target === 'logo') { form.value.logoOssId = res.data.ossId; form.value.logoUrl = res.data.url }
      else { form.value.loginBgOssId = res.data.ossId; form.value.loginBgUrl = res.data.url }
      message.success('上传成功')
    } else message.error(res.msg || '上传失败')
  } catch (e: any) { message.error(e?.response?.data?.msg || '上传失败') }
  finally {
    if (target === 'logo') uploadingLogo.value = false
    else uploadingBg.value = false
    input.value = ''
  }
}

async function save() {
  try { await formRef.value?.validate() } catch { return }
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
    if (isEdit.value) { payload.companyId = form.value.companyId; res = await api.put('/system/company', payload) }
    else res = await api.post('/system/company', payload)
    if (res.code === 200) { message.success(isEdit.value ? '修改成功' : '新增成功'); showDlg.value = false; loadList() }
    else message.error(res.msg || '保存失败')
  } catch (e: any) { message.error(e?.response?.data?.msg || '保存失败') }
  finally { saving.value = false }
}

function setDefault(row: any) {
  Modal.confirm({
    title: '设为默认',
    content: `将「${row.company_name}」设为默认展示公司（登录页/首页显示）？`,
    okText: '确定', cancelText: '取消',
    onOk: async () => {
      const res: any = await api.put(`/system/company/default/${row.company_id}`)
      if (res.code === 200) { message.success('已设为默认展示公司'); loadList() }
      else message.error(res.msg || '操作失败')
    },
  })
}

function remove(row: any) {
  Modal.confirm({
    title: '删除公司',
    content: `确定删除公司「${row.company_name}」？此操作不可恢复。`,
    okText: '删除', okType: 'danger', cancelText: '取消',
    onOk: async () => {
      const res: any = await api.delete(`/system/company/${row.company_id}`)
      if (res.code === 200) { message.success('删除成功'); loadList() }
      else message.error(res.msg || '删除失败')
    },
  })
}
</script>

<template>
  <div>
    <div class="page-header">
      <div class="head-left">
        <a class="back" @click.prevent="goBack" href="javascript:;">←</a>
        <span class="head-title">公司档案</span>
      </div>
      <a-button type="primary" @click="openAdd">+ 新增公司</a-button>
    </div>

    <!-- 公司卡片列表 -->
    <a-spin :spinning="loading">
      <a-empty v-if="!loading && companies.length === 0" description="暂无公司数据" style="margin-top:60px" />
      <div v-else class="company-grid">
        <a-card v-for="c in companies" :key="c.company_id" class="company-card" :bordered="true">
          <div class="card-header">
            <div class="card-logo">
              <img v-if="c.logo_url" :src="c.logo_url" :alt="c.company_name" />
              <div v-else class="logo-ph">织</div>
            </div>
            <div class="card-title">
              <span class="name">{{ c.company_name }}</span>
              <a-tag v-if="c.is_default === '1'" color="blue">默认展示</a-tag>
            </div>
          </div>
          <div class="card-body">
            <div class="info-row"><span class="label">宣传语</span><span class="val">{{ c.slogan || '（默认）' }}</span></div>
            <div class="info-row"><span class="label">背景模式</span><span class="val">{{ c.login_bg_mode === 'B' ? 'B 整页' : 'A 左侧' }}</span></div>
            <div class="info-row"><span class="label">登录背景</span><span class="val">{{ c.login_bg_url ? '已上传' : '未上传' }}</span></div>
            <div class="info-row"><span class="label">状态</span><span class="val"><a-tag :color="c.status === '0' ? 'green' : 'default'">{{ c.status === '0' ? '正常' : '停用' }}</a-tag></span></div>
          </div>
          <template #actions>
            <span @click="openEdit(c)">编辑</span>
            <span v-if="c.is_default !== '1'" @click="setDefault(c)">设默认</span>
            <span class="danger" @click="remove(c)">删除</span>
          </template>
        </a-card>
      </div>
    </a-spin>

    <!-- 新增/编辑（a-modal + a-form） -->
    <a-modal
      v-model:open="showDlg"
      :title="isEdit ? '编辑公司' : '新增公司'"
      width="560px"
      :confirm-loading="saving"
      ok-text="保存"
      cancel-text="取消"
      @ok="save"
    >
      <a-form ref="formRef" :model="form" :rules="rules" layout="vertical">
        <a-form-item label="公司名称" name="companyName">
          <a-input v-model:value="form.companyName" placeholder="如：河北佰信达实业有限公司" />
        </a-form-item>
        <a-form-item label="宣传语">
          <a-input v-model:value="form.slogan" placeholder="留空则用默认：服装代工厂生产管理系统" />
        </a-form-item>
        <a-form-item label="登录背景显示模式">
          <a-radio-group v-model:value="form.loginBgMode">
            <a-radio value="A">A：铺满左侧品牌区</a-radio>
            <a-radio value="B">B：铺满整页</a-radio>
          </a-radio-group>
        </a-form-item>
        <a-form-item label="公司 Logo（建议正方形，如 200×200）">
          <div class="upload-row">
            <div class="upload-preview">
              <img v-if="form.logoUrl" :src="form.logoUrl" alt="logo" />
              <div v-else class="upload-ph">未上传</div>
            </div>
            <a-button :loading="uploadingLogo" @click="($refs.logoInput as HTMLInputElement)?.click()">
              {{ uploadingLogo ? '上传中...' : '选择图片' }}
            </a-button>
            <input ref="logoInput" type="file" accept="image/*" style="display:none" @change="uploadFile($event, 'logo')" />
          </div>
        </a-form-item>
        <a-form-item label="登录背景图（建议竖版 1080×1920 或方图 1200×1200）">
          <div class="upload-row">
            <div class="upload-preview bg">
              <img v-if="form.loginBgUrl" :src="form.loginBgUrl" alt="bg" />
              <div v-else class="upload-ph">未上传</div>
            </div>
            <a-button :loading="uploadingBg" @click="($refs.bgInput as HTMLInputElement)?.click()">
              {{ uploadingBg ? '上传中...' : '选择图片' }}
            </a-button>
            <input ref="bgInput" type="file" accept="image/*" style="display:none" @change="uploadFile($event, 'bg')" />
          </div>
        </a-form-item>
        <a-form-item>
          <a-checkbox
            :checked="form.isDefault === '1'"
            @change="(e: any) => form.isDefault = e.target.checked ? '1' : '0'"
          >
            设为默认展示公司（登录页/首页显示）
          </a-checkbox>
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<style scoped>
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.head-left { display: flex; align-items: center; gap: 6px; }
.back { color: #4E5969; cursor: pointer; font-size: 18px; text-decoration: none; }
.back:hover { color: #1E5EFF; }
.head-title { font-size: 16px; font-weight: 600; color: #1D2129; }

.company-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(340px, 1fr)); gap: 16px; }
.company-card { border-radius: 10px; }
.card-header { display: flex; align-items: center; gap: 12px; margin-bottom: 8px; }
.card-logo { width: 44px; height: 44px; border-radius: 8px; background: #1E5EFF; display: flex; align-items: center; justify-content: center; overflow: hidden; flex-shrink: 0; }
.card-logo img { width: 100%; height: 100%; object-fit: contain; }
.logo-ph { color: #fff; font-weight: 700; font-size: 18px; }
.card-title { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.card-title .name { font-size: 15px; font-weight: 600; color: #1d2129; }
.card-body { padding: 4px 0; }
.info-row { display: flex; font-size: 13px; margin-bottom: 8px; align-items: center; }
.info-row .label { color: #86909c; width: 72px; flex-shrink: 0; }
.info-row .val { color: #1d2129; }
.danger { color: #F53F3F; }

.upload-row { display: flex; align-items: center; gap: 12px; }
.upload-preview { width: 80px; height: 80px; border: 1px dashed #E5E8EF; border-radius: 6px; display: flex; align-items: center; justify-content: center; overflow: hidden; background: #fafafa; }
.upload-preview.bg { width: 120px; height: 68px; }
.upload-preview img { width: 100%; height: 100%; object-fit: cover; }
.upload-ph { color: #C9CDD4; font-size: 12px; }
</style>
