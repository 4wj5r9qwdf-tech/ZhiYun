/*
 * 文件用途：承载织云 ERP 对上游框架“FlowConstant”能力的项目扩展或适配。
 * 文件版本：1.0.0
 * 功能目的：承载织云 ERP 对上游框架“FlowConstant”能力的项目扩展或适配。
 * 设计思路：保留上游框架结构和原版权，仅在必要扩展点接入织云组织、权限、审批、审计或 AI 契约。
 * 实现方法：沿用既有框架接口和服务，在本项目改动处完成适配；具体影响以关联任务和代码为准。
 * 关键约束：必须保留原作者和许可证声明；JY 仅登记实际项目改造责任。
 * 依赖与引用：Docs/规范/开发规范.md；Docs/规范/多人协作与任务交接.md；Docs/项目记录/功能实现状态.md
 * 初始开发人员：上游原作者/许可证：保留源文件既有声明；本项目改造责任人：JY
 * 初始开发时间：待追溯（不以补录时间代替原始开发时间）
 * 当前责任人：JY（项目维护责任；框架文件仅限项目改造范围）
 * 最近修改人员：JY
 * AI辅助工具：Codex（智能体）
 * 最近修改时间：2026-11-20
 * 修改标记：ZY-ERP-20261120-注释整理；补齐文件说明和业务逻辑注释，不改变运行行为
 * 最近测试时间：本次整理未执行功能测试
 * 测试人员：本次整理未执行
 * 测试状态：待功能回归；历史测试记录待追溯
 * 测试范围与记录位置：本次为注释/元信息整理；见 Docs/功能任务/ZY-ERP-20261120-既有功能注释整理.md
 */
package org.dromara.workflow.common.constant;


/**
 * 工作流常量
 *
 * @author may
 */
public interface FlowConstant {

    /**
     * 流程发起人
     */
    String INITIATOR = "initiator";

    /**
     * 业务id
     */
    String BUSINESS_ID = "businessId";

    /**
     * 部门id
     */
    String INITIATOR_ORG_ID = "initiatorOrgId";

    /**
     * 流程分类 ID 转名称的翻译标识。
     */
    String CATEGORY_ID_TO_NAME = "category_id_to_name";

    /**
     * 流程分类名称缓存键。
     */
    String FLOW_CATEGORY_NAME = "flow_category_name#30d";

    /**
     * 默认租户 OA 申请分类 ID。
     */
    Long FLOW_CATEGORY_ID = 100L;

    /**
     * 申请人提交动作标识。
     */
    String SUBMIT = "submit";

    /**
     * 抄送列表流程变量名。
     */
    String FLOW_COPY_LIST = "flowCopyList";

    /**
     * 消息类型流程变量名。
     */
    String MESSAGE_TYPE = "messageType";

    /**
     * 消息通知内容流程变量名。
     */
    String MESSAGE_NOTICE = "messageNotice";

    /**
     * 我的发起页面路径。
     */
    String PATH_MY_DOCUMENT = "/task/myDocument";

    /**
     * 我的待办页面路径。
     */
    String PATH_TASK_WAITING = "/task/taskWaiting";

    /**
     * 我的已办页面路径。
     */
    String PATH_TASK_FINISH = "/task/taskFinish";

    /**
     * 我的抄送页面路径。
     */
    String PATH_TASK_COPY = "/task/taskCopyList";

    /**
     * 任务状态字典类型编码。
     */
    String WF_TASK_STATUS = "wf_task_status";

    /**
     * 业务编码流程变量名。
     */
    String BUSINESS_CODE = "businessCode";

    /**
     * 忽略-办理权限校验（true：忽略，false：不忽略）
     */
    String VAR_IGNORE = "ignore";

    /**
     * 忽略-委派处理（true：忽略，false：不忽略）
     */
    String VAR_IGNORE_DEPUTE = "ignoreDepute";

    /**
     * 忽略-会签票签处理（true：忽略，false：不忽略）
     */
    String VAR_IGNORE_COOPERATE = "ignoreCooperate";

}
