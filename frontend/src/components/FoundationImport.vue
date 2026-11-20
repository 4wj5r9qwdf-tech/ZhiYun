<!--
文件用途：为织云 ERP 页面提供可复用的基础资料导入组件展示或交互能力。
文件版本：1.0.0
功能目的：为织云 ERP 页面提供可复用的基础资料导入组件展示或交互能力。
设计思路：以属性接收调用页面提供的数据和配置，以事件返回用户操作；组件不决定业务授权。
实现方法：封装稳定视觉结构与通用交互，由业务页面传入数据并处理事件。
关键约束：不能绕过调用页或服务端的数据权限。
依赖与引用：Docs/规范/前端规范.md；frontend/src/views/SystemTemplate.vue
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
/**
 * 基础档案CSV导入编排：预览后逐条真实保存，失败可下载，仅保留成功结果。
 * Preview and import bounded CSV batches using each domain's real create contract.
 */
import { ref } from 'vue'
import message from 'ant-design-vue/es/message'
import ImportResultTable from './ImportResultTable.vue'
import BatchResultModal from './BatchResultModal.vue'
import { parseCsv, encodeCsv } from '../utils/csv'
import { foundationError, downloadFoundationFile } from '../utils/foundation'
const props = defineProps<{ open: boolean; title: string; headers: string[]; legacyHeaders?: string[] | string[][]; help: string; parse: (fields: string[]) => Record<string, any>; submit: (payload: Record<string, any>) => Promise<unknown>; duplicateKey?: (payload: Record<string, any>) => string }>()
const emit = defineEmits<{ (e: 'update:open', value: boolean): void; (e: 'done'): void }>()
type ImportRow = { line: number; code: string; name: string; error: string; fields: string[]; payload: Record<string, any>; imported?: boolean }
const rows = ref<ImportRow[]>([]), saving = ref(false), resultOpen = ref(false), succeeded = ref(0), failures = ref<{ name: string; reason: string }[]>([])
/** 读取UTF-8模板，拒绝超大文件和过多记录，避免长时间锁住界面。 / Parse a bounded CSV with explicit schema checks. */
async function selectFile(event: Event) {
  const input = event.target as HTMLInputElement, file = input.files?.[0]; input.value = ''; if (!file) return
  rows.value = []
  try {
    if (file.size > 2 * 1024 * 1024) throw new Error('文件不能超过2MB')
    const records = parseCsv(await file.text()), header = records.shift()
    const hasFailureColumn = header?.fields.at(-1) === '失败原因'
    const inputHeaders = hasFailureColumn ? header?.fields.slice(0, -1) : header?.fields
    const sameHeaders = (expected: string[]) => inputHeaders?.length === expected.length &&
      expected.every((name, index) => name === inputHeaders[index])
    const previous = props.legacyHeaders ?? []
    const legacyCandidates: string[][] = typeof previous[0] === 'string' ? [previous as string[]] : previous as string[][]
    const legacy = legacyCandidates.find(sameHeaders)
    if (!sameHeaders(props.headers) && !legacy) throw new Error('表头与模板不一致，请下载当前模板')
    if (records.length > 1000) throw new Error('单次最多导入1000条')
    const seen = new Set<string>()
    if (hasFailureColumn) records.forEach(record => { record.fields.pop() })
    rows.value = records.map(record => {
      const validLength = record.fields.length === (legacy ?? props.headers).length
      // 旧模板只缺末尾新增列，补空值后仍走相同的业务校验。
      // Older templates omit trailing fields; validate those rows through the same parser.
      if (legacy && validLength) {
        record.fields.push(...Array<string>(props.headers.length - legacy.length).fill(''))
      }
      let payload: Record<string, any> = {}, error = ''
      try { if (!validLength) throw new Error('列数与模板不一致'); payload = props.parse(record.fields); if (!payload.code || !payload.name) throw new Error('编码和名称不能为空'); const key=props.duplicateKey?.(payload)||payload.code; if (seen.has(key)) throw new Error('文件内业务键重复'); seen.add(key) } catch (e) { error = foundationError(e) }
      return { line: record.line, code: payload.code || record.fields[0] || '', name: payload.name || record.fields[1] || '', fields: record.fields, payload, error }
    })
  } catch (e) { message.error(foundationError(e)) }
}
/** 仅提交预览通过且尚未成功的行，服务器再次做完整校验。 / Import each pending valid row once and retain individual failures. */
async function runImport() {
  if (saving.value) return
  saving.value = true; succeeded.value = 0; failures.value = []
  try {
    for (const row of rows.value) {
      if (row.imported) continue
      if (row.error) { failures.value.push({ name: `第${row.line}行 ${row.name}`, reason: row.error }); continue }
      try { await props.submit(row.payload); row.imported = true; succeeded.value++ } catch (e) { row.error = foundationError(e); failures.value.push({ name: `第${row.line}行 ${row.name}`, reason: row.error }) }
    }
    emit('done'); resultOpen.value = true
  } finally { saving.value = false }
}
/** 下载未导入行及错误，成功行不重复提交。 / Export unprocessed rows with actionable failure reasons. */
function downloadFailures() { downloadFoundationFile('未导入记录.csv', encodeCsv([[...props.headers, '失败原因'], ...rows.value.filter(r => !r.imported).map(r => [...r.fields, r.error])])) }
/** 关闭后释放预览状态，执行中不允许丢失结果。 / Clear previews only when the operation is idle. */
function close() { if (saving.value) return; rows.value = []; emit('update:open', false) }
</script>
<template>
  <a-modal :open="open" :title="title" :width="900" :mask-closable="false" :keyboard="false" :closable="!saving" @cancel="close">
    <a-alert type="info" show-icon :message="help" style="margin-bottom:16px" />
    <a-space><a-button @click="downloadFoundationFile(`${title}模板.csv`,encodeCsv([headers]))">下载模板</a-button><label><a-button :disabled="saving" @click="($refs.file as HTMLInputElement).click()">选择CSV文件</a-button><input ref="file" type="file" accept=".csv,text/csv" hidden @change="selectFile" /></label></a-space>
    <ImportResultTable :rows="rows" />
    <template #footer><a-button :disabled="saving" @click="close">关闭</a-button><a-button type="primary" :loading="saving" :disabled="!rows.some(r=>!r.error&&!r.imported)" @click="runImport">确认导入</a-button></template>
  </a-modal>
  <BatchResultModal v-model:open="resultOpen" title="导入结果" :succeeded="succeeded" :failures="failures"><template #actions><a-button v-if="failures.length" @click="downloadFailures">下载未导入记录</a-button></template></BatchResultModal>
</template>
