// 文件用途：集中实现前端foundation请求或共享处理能力。
// 文件版本：1.0.0
// 功能目的：集中实现前端foundation请求或共享处理能力。
// 设计思路：抽离跨页面复用逻辑，以明确输入输出减少重复；不替代服务端校验。
// 实现方法：提供 API 封装、格式化工具或组合状态供业务页面调用。
// 关键约束：不得伪造业务成功或改变服务端数据口径。
// 依赖与引用：Docs/规范/前端规范.md；Docs/API/API文档.md
// 初始开发人员：JY（织云项目自研内容）
// 初始开发时间：待追溯（不以补录时间代替原始开发时间）
// 当前责任人：JY（项目维护责任；框架文件仅限项目改造范围）
// 最近修改人员：JY
// AI辅助工具：Codex（智能体）
// 最近修改时间：2026-11-20
// 修改标记：ZY-ERP-20261120-注释整理；补齐文件说明和业务逻辑注释，不改变运行行为
// 最近测试时间：本次整理未执行功能测试
// 测试人员：本次整理未执行
// 测试状态：待功能回归；历史测试记录待追溯
// 测试范围与记录位置：本次为注释/元信息整理；见 Docs/功能任务/ZY-ERP-20261120-既有功能注释整理.md
/**
 * 基础档案统一请求和错误处理，复用已配置的员工会话客户端。
 * Shared foundation requests preserve the existing employee session and response contract.
 */
import api from '../api'
/** 检查业务返回码，不将HTTP成功误判为保存成功。 / Unwrap only successful domain responses. */
export async function foundationRequest<T = any>(method: 'get' | 'post' | 'put' | 'delete', url: string, data?: any): Promise<T> {
  const response: any = method === 'get' ? await api.get(url, { params: data }) : await api.request({ method, url, data })
  if (response.code !== 200) throw new Error(response.msg || '操作失败')
  return response.data as T
}
/** 显示明确的后端业务失败原因。 / Prefer the domain error over transport details. */
export function foundationError(error: any): string { return error?.response?.data?.msg || error?.message || '操作失败，请重试' }
/** 下载文本模板，及时释放临时URL。 / Download an UTF-8 artifact without leaking object URLs. */
export function downloadFoundationFile(filename: string, content: string) {
  const url = URL.createObjectURL(new Blob([content], { type: 'text/csv;charset=utf-8' }))
  const link = document.createElement('a'); link.href = url; link.download = filename; link.click(); setTimeout(() => URL.revokeObjectURL(url), 1000)
}
