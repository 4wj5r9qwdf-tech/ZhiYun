// 文件用途：集中实现前端tag请求或共享处理能力。
// 文件版本：1.0.0
// 功能目的：集中实现前端tag请求或共享处理能力。
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
import api from './index'

/** 查询已经通过业务标签动态登记的功能归属；系统不返回预置空功能。 */
export function getTagModules() {
  return api.get('/system/tag/modules')
}

/** 查询指定功能当前可用于业务表单选择的启用标签。 */
export function getTagOptions(moduleKey: string) {
  return api.get('/system/tag/options', { params: { moduleKey } })
}

/** 查询功能内的标签分组，默认分组排在最前。 */
export function getTagGroups(moduleKey: string) {
  return api.get('/system/tag/groups', { params: { moduleKey } })
}

/** 新增功能内的自定义标签分组。 */
export function addTagGroup(body: { moduleKey: string; groupName: string }) {
  return api.post('/system/tag/groups', body)
}

/** 重命名自定义标签分组；默认分组由服务端禁止重命名。 */
export function editTagGroup(body: { groupId: string; moduleKey: string; groupName: string }) {
  return api.put('/system/tag/groups', body)
}

/** 删除空的自定义分组；默认分组及包含标签的分组不可删除。 */
export function deleteTagGroup(groupId: string) {
  return api.delete('/system/tag/groups/' + groupId)
}

/** 保存业务表单中新录入的标签目录项；服务端按模块与名称幂等去重。 */
export function ensureTag(moduleKey: string, moduleName: string, tagName: string) {
  return api.post('/system/tag/ensure', { moduleKey, moduleName, tagName })
}

/** 查询管理页中一个模块下的标签目录，可按名称和状态筛选。 */
export function getTagList(moduleKey: string, keyword = '', status = '', groupId = '') {
  return api.get('/system/tag/list', { params: { moduleKey, keyword, status, groupId } })
}

/** 管理员手动新增标签，所属功能由表单明确传入。 */
export function addTag(body: { moduleKey: string; moduleName: string; groupId?: string; tagName: string; isCommon: string }) {
  return api.post('/system/tag', body)
}

/** 更新标签目录信息；moduleKey 用来限定目标功能，不支持跨功能迁移。 */
export function editTag(body: { tagId: string; moduleKey: string; groupId: string; tagName: string; isCommon: string; status: string }) {
  return api.put('/system/tag', body)
}

/** 软停用标签，历史业务关联继续保留。 */
export function disableTag(tagId: string) {
  return api.put('/system/tag/' + tagId + '/disable')
}

/** 删除未被业务记录引用的标签目录项。 */
export function deleteTag(tagId: string) {
  return api.delete('/system/tag/' + tagId)
}
