// 文件用途：集中实现前端savedFormDefaults请求或共享处理能力。
// 文件版本：1.0.0
// 功能目的：集中实现前端savedFormDefaults请求或共享处理能力。
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
/** 连续新增共用规则：只记成功保存的数据，读写时剔除主键，按调用方的用户/业务键隔离。 */
type FormStorage = Pick<Storage, 'getItem' | 'setItem'>
export function createSavedFormDefaults<T extends object>(storage: FormStorage, key: string, empty: () => T, identityKeys: (keyof T)[]) {
  const sanitize = (source: unknown): T => {
    const result = empty()
    if (source && typeof source === 'object' && !Array.isArray(source)) {
      for (const field of Object.keys(result) as (keyof T)[]) {
        if (!identityKeys.includes(field) && Object.hasOwn(source, field)) result[field] = (source as T)[field]
      }
    }
    return result
  }
  return {
    load(): T {
      try { return sanitize(JSON.parse(storage.getItem(key) || 'null')) } catch { return empty() }
    },
    save(value: T) {
      try { storage.setItem(key, JSON.stringify(sanitize(value))) } catch { /* 存储不可用时不影响已成功的业务保存 */ }
    },
  }
}
