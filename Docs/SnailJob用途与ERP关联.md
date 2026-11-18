# SnailJob 用途、用法与 ERP 关联说明

> 更新日期：2026-11-17
> 文档性质：组件说明与候选关联分析，不代表已将 ERP 调度平台选型改为 SnailJob。

## 一、先说结论

SnailJob 是一个分布式任务调度与失败重试平台。ERP 代码负责实现具体业务动作，SnailJob 负责按计划触发、分发执行、记录运行情况，并在符合配置的失败场景中安排重试。

SnailJob 不是 AI 智能体，也不会因为后台界面已经部署就自动执行 ERP 业务。要接入一个业务任务，仍需在 ERP 后端实现受控的任务执行器，并配置客户端与 SnailJob 服务端之间的连接。

当前项目规范仍将 XXL-Job 定为 ERP 定时任务方案：用于业务预警、审批催办、吊挂拉取和预聚合等工作。本文只说明 SnailJob 的能力及其可能关联，不改变该选型。若未来要采用 SnailJob，应先统一调度平台和运行边界，避免同一业务任务同时由两套平台触发。

## 二、主要组成与概念

| 名称 | 作用 | 在 ERP 接入中的对应物 |
|---|---|---|
| 服务端 / 管理端 | 保存调度、重试和工作流配置，分派任务并提供运行监控 | SnailJob 管理页面及服务端进程 |
| 客户端 / 执行器 | 连接服务端，向平台注册可调用的任务处理器，并在 ERP 进程中执行任务 | ERP Spring Boot 应用中的 SnailJob Client |
| 命名空间 | 隔离不同环境或业务空间 | 开发、测试、生产环境分别配置，避免互相触发 |
| 执行器组 | 将任务路由到一组客户端 | ERP 后端任务客户端所属的组 |
| 定时任务 | 指定执行器、触发时间、参数、路由及失败策略 | 例如每天拉取吊挂数据的计划 |
| 重试场景 | 为一类可重放的业务方法配置重试方式 | 例如可按业务记录号幂等补偿的外部同步 |
| 工作流 | 把多个后台任务按依赖、顺序或条件编排 | 例如“拉取 → 校验 → 汇总 → 生成预警” |

控制台负责“什么时候运行、交给谁运行、运行结果如何”；业务执行器负责“具体做什么”。任务参数只能作为输入，不能作为可信的员工身份或权限凭证。

## 三、能做什么

### 3.1 定时调度

支持 Cron、固定频率等调度方式，可按集群、广播、分片或 Map/MapReduce 等方式执行任务。管理端可以查看批次和日志，并进行手动触发、暂停、恢复或停止等操作。ERP 一般优先采用 Java 执行器，让业务逻辑留在受版本控制的后端代码中。

### 3.2 失败重试与异常补偿

SnailJob 提供本地、远程、先本地后远程等重试策略。远程重试可把失败现场交由服务端管理，并在任务超过重试上限后转入死信队列供排查和后续处理。

重试不是“保证只执行一次”。同一任务可能被再次调用，ERP 必须自己保证幂等，例如使用稳定的业务记录 ID、同步游标或幂等键，并在每次重放前检查业务状态。只有可安全重放的短暂故障才应重试；数据校验错误、业务规则拒绝等确定性错误应及时终止或转人工处理。

### 3.3 后台任务工作流

可以把多个任务节点编排成后台处理链，传递有限的流程上下文，并设置失败后的跳过或阻塞策略。这里的“工作流”是系统任务编排，不是 Warm-Flow 的员工审批流程。

### 3.4 执行监控与告警

管理端可以查看任务批次、执行状态和日志，按配置发送失败或重试相关告警。生产环境仍应遵守 ERP 自己的业务审计要求；SnailJob 的任务日志不能替代 ERP 对具体业务对象变更的审计记录。

官方文档还列出 HTTP、Shell、CMD、PowerShell 等执行类型。ERP 生产业务建议优先使用受控的 Java 执行器；不要把任意 Shell、SQL 或 URL 作为生产业务任务开放给普通配置人员。

## 四、基本使用流程

### 4.1 接入前准备

1. 先决定当前环境是否采用 SnailJob。项目文档目前指定 XXL-Job，因此不能只为试用而把 SnailJob 客户端开关打开并登记正式 ERP 任务。
2. 准备服务端地址、命名空间、执行器组、接入凭据及客户端可被服务端访问的地址和端口。不同环境使用各自配置，凭据放部署环境变量或受控配置中，不写入文档、代码仓库或日志。
3. 确认 SnailJob 服务端运行所需的数据存储、版本兼容、网络端口、备份和访问控制。ERP 主数据库为 PostgreSQL；不能从 SnailJob 控制台可访问就推断它已使用 ERP 主库或已满足生产部署要求。

### 4.2 编写任务执行器

ERP 后端的执行器可通过 `@JobExecutor` 注册。下面是与仓库示例相同的基本形式；示例只返回成功结果，不执行真实业务：

```java
@Component
@JobExecutor(name = "exampleJobExecutor")
public class ExampleJobExecutor {

    public ExecuteResult jobExecute(JobArgs jobArgs) {
        // 实际接入时，在这里调用 ERP 的业务 Service，并校验参数、状态和幂等键。
        return ExecuteResult.success("任务完成");
    }
}
```

执行器中应调用 ERP 已有 Service，不直接复制业务规则或绕开校验。批量任务应分页或分片处理、控制并发和单次事务范围，并记录任务批次与具体业务对象的关联，便于追查。

### 4.3 配置客户端并登记调度

1. 在 ERP 后端配置中提供服务端连接信息、命名空间、执行器组和凭据；启用客户端后启动 ERP 服务。
2. 在 SnailJob 管理端确认客户端在线、执行器名称已注册，且命名空间和组一致。
3. 新建定时任务，选择执行器、Cron/频率、参数和执行策略；根据任务性质设定超时、阻塞、失败重试及告警方式。
4. 先手动运行小范围任务，查看服务端批次日志和 ERP 业务日志；核对幂等、重复触发、部分失败和人工重试结果，再考虑启用正式周期。
5. 将任务配置变更纳入运维记录。变更 Cron、参数、路由、重试次数或暂停任务，都可能改变业务执行结果。

重试类方法可使用 `@Retryable` 声明场景和重试方式，具体属性及配置以项目锁定的 SnailJob 版本为准。应使用有业务含义的场景名，明确哪些异常可重试，并设计重试完成或达到上限后的处理动作。

## 五、与织云 ERP 的可能关联

| ERP 场景 | SnailJob 可以承担的部分 | 必须保留的 ERP 业务约束 |
|---|---|---|
| 业务预警扫描 | 定时触发检查证件到期、交期风险、库存阈值、宿舍待退房等规则 | 查询仍走 ERP 预警规则和白名单；命中后走统一 `sendMessage`；用 `alert_hit` 等机制去重，不能由 SnailJob 另建一套通知规则 |
| 审批催办 | 定时扫描超时待办并调用通知通道 | Warm-Flow 仍负责审批流转和审批结果；SnailJob 只能负责催办触发，不能替审批人通过或驳回 |
| 吊挂数据拉取 | 周期性拉取外部 SQL Server 数据，并对可恢复的网络或服务故障做重试 | 通过现有适配器写入 ERP 本地表；用稳定记录键、游标或 Redis 幂等键防止重拉重复入账；记录同步水位和业务结果 |
| 看板与报表预聚合 | 按小时、按天或按月生成产量、考勤、在制等汇总数据 | 写入专用汇总结果，避免把重计算放到报工等关键写请求中；工资等重要批算要有可核对、可追溯的批次 |
| 多步骤批处理 | 串联同步、校验、汇总、异常清单或通知任务 | 节点应可重入，失败策略与补偿方式要明确；涉及库存、财务、工资等重要数据的步骤不能仅依赖工作流配置保证正确性 |
| AI 智能体的定时触发（可选） | 在明确设计后，定时调用 ERP 内的智能体服务，例如生成每日异常摘要 | SnailJob 与 SnailAI/ERP 智能体互不自动绑定；工具调用仍按当前员工或受控系统身份执行，保留权限校验和审计，禁止借定时任务绕过授权 |

以上是可行关联，不表示这些任务已经接入 SnailJob。它们目前仍服从项目既定的任务调度与业务规则。

## 六、与现有技术边界的关系

### 6.1 SnailJob 与当前计划中的 XXL-Job

《开发规范》及《地基/审批与预警.md》把 XXL-Job 定为业务预警、审批催办、吊挂拉取和预聚合等任务的调度方案。SnailJob 的调度能力可以覆盖其中一部分；它额外提供面向持久化异常补偿的重试模块和可视化后台任务编排。

当前代码仓库同时包含 SnailJob 2.0.2 服务端模块和客户端依赖，但开发、生产配置中的 `snail-job.enabled` 默认为 `false`。`ruoyi-job` 下的 `testJobExecutor`、账单与分片示例是框架演示代码，不是织云 ERP 业务任务。本次核对也未在 `RuoYi-Vue-Plus` 的 Maven/Java 配置中发现 XXL-Job 客户端或执行器实现，因此“文档选型已写定”和“调度功能代码已落地”是两个状态，不能混为一谈。

用户提供的 SnailJob 首页截图中在线机器显示为 0；这只能说明截图时未见在线执行器，不能用来证明后台配置的任务属于 ERP 或已成功执行。

若未来决定切换到 SnailJob，应先评估数据库兼容、独立存储、客户端网络、安全配置、任务迁移、告警、运维和回滚方式，再更新《开发规范》《地基/审批与预警.md》及部署文档。不要让同一预警或同步任务同时由 XXL-Job 和 SnailJob 调度。

### 6.2 SnailJob 与审批、智能体

- **Warm-Flow**：管理员工之间的审批流程、待办和审批结果回调。SnailJob 工作流只编排后台程序任务，不能替代审批。
- **AI 智能体 / SnailAI**：对话与工具编排组件。SnailJob 不负责模型、会话或智能体权限，也不因同属一个代码仓库而自动绑定。只有新增明确的任务执行器/API 调用后，SnailJob 才能触发某个 AI 服务。
- **ERP 权限与审计**：调度任务运行在后台服务身份下。涉及员工权限的数据访问、对外通知和业务状态变化，必须由 ERP 服务端执行权限边界、幂等和业务审计；不能信任控制台参数冒充员工身份。

## 七、当前代码入口

- SnailJob 版本定义：`RuoYi-Vue-Plus/pom.xml` 中的 `snailjob.version`。
- 客户端启用条件和启动配置：`RuoYi-Vue-Plus/ruoyi-common/ruoyi-common-job/src/main/java/org/dromara/common/job/config/SnailJobConfig.java`。
- 开发配置：`RuoYi-Vue-Plus/ruoyi-admin/src/main/resources/application-dev.yml` 的 `snail-job` 配置段。生产配置也有相应配置段；凭据必须由部署配置提供，不应复制到本文。
- 示例执行器：`RuoYi-Vue-Plus/ruoyi-modules/ruoyi-job/src/main/java/org/dromara/job/snailjob/TestAnnoJobExecutor.java`、`AlipayBillTask.java`、`SummaryBillTask.java`。
- 服务端模块：`RuoYi-Vue-Plus/ruoyi-extend/ruoyi-snailjob-server/`。

## 八、参考资料

- [SnailJob 官方快速开始](https://snailjob.opensnail.com/docs/quickstart/quick_start.html)
- [SnailJob 官方核心能力](https://snailjob.opensnail.com/docs/introduce/preface.html)
- [JobExecutor 注解](https://snailjob.opensnail.com/docs/guide/job/job_executor.html)
- [重试策略说明](https://snailjob.opensnail.com/docs/guide/retry/server_report_retryStrategy.html)
- [工作流任务节点](https://snailjob.opensnail.com/docs/guide/workflow/wf_task_node.html)
- [SnailJob 2.0.2 发布记录](https://github.com/aizuda/snail-job/releases/tag/vsj2.0.2)
- 项目现行选型：[开发规范.md](开发规范.md)、[审批与预警地基](地基/审批与预警.md)
