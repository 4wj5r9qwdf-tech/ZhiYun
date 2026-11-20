<!--
文件用途：提供织云 ERP NoticeManage的查询、编辑、状态展示和用户操作界面。
文件版本：1.0.0
功能目的：提供织云 ERP NoticeManage的查询、编辑、状态展示和用户操作界面。
设计思路：使用 Vue 3 响应式状态与项目公共布局；页面编排交互，服务端负责业务规则和最终授权。
实现方法：加载选项/列表，按当前状态驱动表单和弹窗，调用业务 API 后刷新并反馈结果。
关键约束：按钮可见性不是授权；业务校验仍由服务端执行。
依赖与引用：Docs/规范/前端规范.md；Docs/API/API文档.md；Docs/项目记录/功能实现状态.md
初始开发人员：JY（织云项目自研内容）
初始开发时间：待追溯（不以补录时间代替原始开发时间）
当前责任人：JY（项目维护责任；框架文件仅限项目改造范围）
最近修改人员：JY
AI辅助工具：Codex（智能体）
最近修改时间：2026-11-20
修改标记：ZY-ERP-20261120-注释整理；补齐文件说明和业务逻辑注释，不改变运行行为
最近测试时间：本次整理未执行功能测试
测试人员：本次整理未执行
测试状态：待功能回归；历史测试记录待追溯
测试范围与记录位置：本次为注释/元信息整理；见 Docs/功能任务/ZY-ERP-20261120-既有功能注释整理.md
-->
<script setup lang="ts">
/** 维护框架通知公告。 / Maintain framework notices and announcements. */
import { onMounted, reactive, ref } from 'vue'
import message from 'ant-design-vue/es/message'
import Modal from 'ant-design-vue/es/modal'
import api from '../api'
import { can } from '../utils/perms'
import StandardListLayout from '../components/StandardListLayout.vue'
import FoundationTable from '../components/FoundationTable.vue'
const rows=ref<any[]>([]), loading=ref(false), total=ref(0), page=ref(1), size=ref(20), keyword=ref(''), opened=ref(false), saving=ref(false)
const form=reactive({noticeId:'',noticeTitle:'',noticeType:'1',noticeContent:'',status:'0',remark:''})
const columns=[{key:'noticeTitle',title:'标题',width:260},{key:'noticeTypeLabel',title:'类型',width:100},{key:'statusLabel',title:'状态',width:100},{key:'createByName',title:'创建人',width:120},{key:'createTime',title:'创建时间',width:180}]
/** 分页读取公告。 / Load notices with server-side pagination. */
async function load(){loading.value=true;try{const r:any=await api.get('/system/notice/list',{params:{pageNum:page.value,pageSize:size.value,noticeTitle:keyword.value.trim()||undefined}});if(r.code!==200)throw Error(r.msg);rows.value=(r.data?.rows||[]).map((x:any)=>({...x,id:String(x.noticeId),noticeTypeLabel:String(x.noticeType)==='1'?'通知':'公告',statusLabel:String(x.status)==='0'?'正常':'关闭'}));total.value=Number(r.data?.total||0)}catch(e:any){message.error(e?.response?.data?.msg||e.message||'加载失败')}finally{loading.value=false}}
/** 读取详情后编辑公告。 / Load a notice before editing. */
async function edit(row?:any){Object.assign(form,{noticeId:'',noticeTitle:'',noticeType:'1',noticeContent:'',status:'0',remark:''});if(row){if(!can('system:notice:query'))return message.warning('当前账号没有查看公告详情的权限');try{const r:any=await api.get('/system/notice/'+row.id);if(r.code!==200)throw Error(r.msg);Object.assign(form,r.data,{noticeId:String(r.data.noticeId)})}catch(e:any){message.error(e.message||'读取失败');return}}opened.value=true}
/** 保存公告，新增时由框架发送公告通知。 / Save through the framework, which broadcasts newly added notices. */
async function save(){if(!form.noticeTitle.trim()){message.warning('请输入标题');return}saving.value=true;try{const body={...form,noticeTitle:form.noticeTitle.trim(),noticeId:form.noticeId||undefined};const r:any=form.noticeId?await api.put('/system/notice',body):await api.post('/system/notice',body);if(r.code!==200)throw Error(r.msg);opened.value=false;message.success('保存成功');await load()}catch(e:any){message.error(e?.response?.data?.msg||e.message||'保存失败')}finally{saving.value=false}}
/** 删除选择的公告。 / Delete the selected announcement. */
function remove(row:any){Modal.confirm({title:'删除公告',content:`确认删除“${row.noticeTitle}”吗？`,okText:'删除',cancelText:'取消',async onOk(){const r:any=await api.delete('/system/notice/'+row.id);if(r.code!==200){message.error(r.msg||'删除失败');return}await load()}})}
onMounted(load)
</script>
<template>
  <StandardListLayout>
    <template #filters>
      <label class="zy-filter-field">
        <span>公告标题</span>
        <a-input v-model:value="keyword" allow-clear placeholder="请输入标题" @press-enter="page=1;load()" />
      </label>
    </template>
    <FoundationTable
      page-toolbar
      storage-key="notice_list"
      :columns="columns"
      :rows="rows"
      :loading="loading"
      :server-total="total"
      :server-page="page"
      :server-page-size="size"
      :action-width="130"
      @page-change="(p,s)=>{page=p;size=s;load()}"
    >
      <template #actions>
        <a-button v-if="can('system:notice:add')" type="primary" @click="edit()">新增</a-button>
        <a-button @click="page=1;load()">查询</a-button>
        <a-button @click="keyword='';page=1;load()">重置</a-button>
      </template>
      <template #cell="{column,row}">{{ row[column.key] || '—' }}</template>
      <template #action="{row}">
        <a-button v-if="can('system:notice:edit')&&can('system:notice:query')" type="link" size="small" @click="edit(row)">编辑</a-button>
        <a-button v-if="can('system:notice:remove')" type="link" danger size="small" @click="remove(row)">删除</a-button>
      </template>
    </FoundationTable>
  </StandardListLayout>

  <a-modal
    :open="opened"
    :title="form.noticeId?'编辑公告':'新增公告'"
    :confirm-loading="saving"
    :mask-closable="false"
    :keyboard="false"
    @ok="save"
    @cancel="opened=false"
  >
    <a-form layout="vertical">
      <a-alert
        v-if="!form.noticeId"
        type="info"
        show-icon
        message="新增后会推送到所有人员的右上角铃铛；编辑已有公告不会再次推送。"
        style="margin-bottom:16px"
      />
      <a-form-item label="标题" required>
        <a-input v-model:value="form.noticeTitle" :maxlength="50" />
      </a-form-item>
      <a-form-item label="类型">
        <a-select v-model:value="form.noticeType" :options="[{value:'1',label:'通知'},{value:'2',label:'公告'}]" />
      </a-form-item>
      <a-alert type="info" show-icon style="margin-bottom:16px">
        <template #message>通知和公告的区别</template>
        <template #description>
          <div><strong>通知：</strong>适合及时传达、需要员工尽快知晓或处理的工作信息和提醒。</div>
          <div><strong>公告：</strong>适合正式发布制度、政策或面向员工传达的重要信息。</div>
          <div style="margin-top:6px">两种类型都会进入右上角铃铛，已读和保留规则相同；类型只区分内容性质。</div>
        </template>
      </a-alert>
      <a-form-item label="内容">
        <a-textarea v-model:value="form.noticeContent" :rows="6" />
      </a-form-item>
      <a-form-item label="状态">
        <a-select v-model:value="form.status" :options="[{value:'0',label:'正常'},{value:'1',label:'关闭'}]" />
      </a-form-item>
    </a-form>
  </a-modal>
</template>
