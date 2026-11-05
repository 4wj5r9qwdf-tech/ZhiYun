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
