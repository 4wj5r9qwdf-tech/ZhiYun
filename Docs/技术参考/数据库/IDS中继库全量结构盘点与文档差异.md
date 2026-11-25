<!--
文件用途：提供 2026-11-24 当前 SQL Server IDS 中继库的完整对象、字段、依赖、索引、同步客户端副本静态核对及两份 Excel 说明差异快照。
文件版本：2.0
功能目的：让项目成员按实际中继库结构逐对象核对，区分物理对象缺失、视图投影缺失、字段别名/类型差异和可选接口能力。
设计思路：以 IDS 系统目录和视图定义为结构事实，以 7R/IDS Excel 为供应商文档事实；不把字段名推断写成已确认业务含义。
实现方法：只读枚举用户表、视图、列元数据、索引、约束和 SQL 依赖；比对两份 Excel 字段清单；对视图关联键做只输出聚合计数的重复组合检查；解析 `T_TableInfo.SelectSql` 的源表与投影形式但不执行；静态检查用户提供的已编译同步客户端程序集以定位同步路径和查询模板，不运行客户端；解码 SQL Server Unicode 扩展属性供人阅读。
关键约束：本快照仅代表本次连接到的 IDS 实例；不查询或记录 T_DbInfo 中的连接口令值，不查询员工生产明细，不修改中继库。
依赖与引用：Docs/技术参考/数据库/7R标准公共数据库对接字典说明V2.0.xlsx；Docs/技术参考/数据库/IDS视图字段说明-20241223.xlsx；Docs/技术参考/数据库/衣拿吊挂7R与IDS中继库字段核对与理解.md；Docs/规划/系统与流程/生产流程规划.md 第 15.14 节。
当前责任人：JY
本次整理人员：Codex（智能体，依据 JY 授权核对）
本次整理时间：2026-11-24
最近修改人员：Codex（智能体）
最近修改时间：2026-11-25
修改标记：ZY-ERP-20261124-IDS中继库全量对象与字段核对；追加数据库新建/修改/复制页面、产线配置口径、同步客户端静态路径及三大回传视图的实时 SQL 字段溯源
最近测试时间：2026-11-25
测试人员：Codex（智能体）
测试状态：数据库只读元数据、T_TableInfo 查询文本形态与关联键聚合盘点；两份 Excel 逐字段释义抽取与实际字段核对；49 条数据库注释解码；客户端副本配置键、关键程序集字符串及反编译方法静态核对；运行客户端主页面、手动同步页面、参数设置页面、数据库表映射页面及新建/修改/复制数据库窗口截图核读；文档链接、格式与映射交叉核对通过；未执行 SelectSql、DDL、客户端程序或同步操作。
追加静态核对时间：2026-11-25
追加静态核对状态：从本地编译副本进一步确认 54 条 CHANGETABLE 查询模板（覆盖 52 个不同源表名）、历史衣架按日期区间上传、产量/ODP 手动同步入口、OPEN 类独立线程及“复表”复制 T_TableInfo 映射配置的代码路径；未运行客户端或触达生产业务数据。`SelectSql` 的同步运行时作用仍需隔离环境验证。
核心视图定义复核时间：2026-11-25
核心视图定义复核状态：重新只读读取当前 IDS 的三个 `OBJECT_DEFINITION`，按 SELECT 表达式逐项映射字段来源与连接键；补充 ODPI 的单价来源、QCRI 的员工/产线/GroupId 来源，以及 VHRD 的衣架连接和款式工序匹配限制。另对有效款式工序的 `GroupId + STOP_STOPM_Key + STOP_OC_Key` 组合做只返回计数的聚合检查，当前重复组为 0；没有读取员工或具体生产记录，未修改数据库。
-->
# IDS 中继库全量结构盘点与文档差异

> 文档版本：2.0；更新日期：2026-11-25。本文含结构全量清单和文档释义对照；未覆盖字段的业务语义不视为已确认。三个回传视图的 SQL 定义于 2026-11-25 再次读取，其字段来源与连接口径见第 4 节。
>
> 本文是 2026-11-24 对当前 IDS 数据库所做的结构快照。用户补充确认：吊挂数据由独立客户端工具上传同步；文档对象在中继库不存在，或对象存在但没有同步记录，表示该对象尚未配置上传。用户进一步确认各产线的表映射规则相同，线体间若有差异主要检查各库配置的查询语句；当前结构快照中常规表映射按 7 个数据库标识重复登记，41 个常规查询均为同名表 `SELECT *` 形式，未发现查询过滤差异。后续无需逐条收集相同映射的界面截图，若软件/工艺部署变化再按 `DbId` 核对例外。升级衣拿、同步软件或更换车间数据库后仍须重新比对。

## 1. 本次全库盘点结果

| 项目 | 实际结果 | 说明 |
|---|---:|---|
| 用户表 | 62 | dbo 用户表 |
| 视图 | 3 | 三个回传视图 |
| 表与视图字段 | 851 | 按 sys.columns 统计 |
| 索引 | 64 | 60 个聚集、4 个非聚集 |
| 外键约束 | 0 | 当前库没有 SQL Server 外键约束 |
| 检查约束 | 1 | 仅 ToIna_Employee 的员工号范围检查 |
| 视图/约束依赖 | 35 | 34 条视图到源对象引用、1 条检查约束依赖 |
| 存储过程、函数、触发器 | 0 | 本次用户对象扫描未发现 |
| 同义词 | 0 | 未发现 |

### 数据库版本和内部字典

- 数据库为 IDS，兼容级别 100，排序规则 `Chinese_PRC_CI_AS`，`READ_COMMITTED_SNAPSHOT` 未启用。兼容级别不能单独用于推断 SQL Server 引擎版本。
- `T_IDS_Version` 当前记录为 IDS 2.1、From Line DB 7.28.12、To Line DB 7.34.21。用户提供的衣拿客户端关于窗口为 7.42.35.10；这些版本标识属于不同组件，不能直接判定兼容或冲突，需厂商说明映射关系。
- `T_TableInfo` 有 289 条同步元数据记录、43 个不同源表名、15 个数据库标识。只读检查了 `SelectSql` 配置文本但没有执行：41 个常规源表各有查询，形式都是 `SELECT * FROM <同名源表>`，没有列投影或过滤条件；两个带 `_LS` 后缀的特殊名称没有 `SelectSql` 和 `StableName`。因此 IDS 内保存的是按整表登记的同步源配置，不是逐列映射字典。
- 2026-11-25 对用户提供的 Windows 本机副本 `E:/佰信达erp与吊挂系统/20250802DataThreadSync` 做了文件清单、配置键状态和 .NET 字符串静态检查，没有启动程序、读取日志内容或读取配置中的连接口令。副本包含 `DataThreadSync.exe`、`Ina.Dts.Serverlib.dll`、`Ina.Dts.SqlConfig.dll` 等。静态代码确认客户端使用 `T_DbInfo`（`DbId/DbName/DbSource/DbUser/DbPassword/LineId/LineName/Enable/GroupId`）和 `T_TableInfo`（`TableId/DbId/TableName/SelectSql/StableName/SyncVersion`）配置源连接及表查询/目标名称；代码有读取和更新 `SyncVersion` 的路径，但其完整游标语义仍未确认。
- 2026-11-25 随后对该副本的已编译程序集做了方法级静态核对：`LineSqlConfig.GetLineSelectSql` 内含 54 条 `CHANGETABLE(CHANGES ...)` 查询模板、涉及 52 个不同源表名；普通同步另有按表名选择 SQL 的分支，其中 `Hanger_Routing_Details` 会连接 `Hanger_Master` 补出 `HM_Key`，部分线体班次/休息时间表会补 `LineId`、`GroupId`、`DelFlag`。手动历史衣架上传明确查询 `IHS_HISTORY` 的 `Hanger_Master_History` 与 `Hanger_Routing_Details_History`，按完成时间区间筛选，并补接共享款式/颜色/尺码/工序键；产量入口调用独立 `SyncODPData`；另有 OPEN 类同步线程。上述是本地副本的静态方法调用证据，不代表运行机器配置已启用或成功上传。
- 同步表编辑窗口保存 `T_TableInfo.SelectSql`，但本次扫描到的程序集里 `get_SelectSql` 只在表编辑窗口加载时用于回填文本，未找到同步业务方法读取该属性；自动上传路径可见按表名选择内置查询模板。因此目前不能把页面“查询语句”当成已证实会被同步线程执行的配置项，也不能断言所有版本或外部组件都忽略它，应在隔离测试环境验证。
- “复表”方法从源 `DbId` 读取 `T_TableInfo` 行，为目标 `DbId` 新建对应映射并复制源表名、服务器表名、查询文本、同步版本等配置字段；本次方法调用中未观察到复制源业务表行的调用。该实现解释了按钮打开的复制窗口，但仍未实测覆盖策略、失败回滚或不同版本行为。
- 同一副本还包含一条独立的条码同步代码路径：从线体源表 `Barcode_Info` 读取变更，按 `BCI_Key` 与 IDS 侧 `P_Barcode_Info` 关联，并生成 `DelFlag` 删除标志；配置文件中的 `Barcode_Info` 开关为 `false`。该发现只说明这份副本包含此实现且其本地配置关闭，不能代表实际运行客户端的当前启用状态。
- 用户提供的运行客户端主页面截图显示：`Default` 两条连接均为“没启用”；1–5 号线的 `IHS` 与 `IHS_SHARED` 连接均显示“已启动”；6 号线两条连接均显示“数据库连接异常”。同屏上传日志可见源表 `Hanger_Master`、`Hanger_Routing_Details`、`ODP_Master`、`ODP_Detail` 的上传记录。这能证明截图时客户端正在处理部分线体表数据，但没有显示 `Barcode_Info` 开关、表到 IDS `StableName` 的映射、视图上传配置或目标连接详情；因此不能据此判断 Tag_Info 候选路径或 VPD/VRD/VSD 是否启用。截图未将网络地址记入本文。
- 用户随后提供的手动同步页面截图显示：可选“单库同步”或“全部同步”，并有“同步产量”“上传衣架历史数据”“单库上传衣架历史数据”入口；手动上传衣架历史数据可选起止日期，截图默认同为 2026-11-25。左侧列表有组 ID、LoadingLine、线名、库名等列，可见 IHS 与 IHS_SHARED 两类库。该页面确认客户端提供按库/全库和按日期的手动同步入口，但没有显示具体 SQL 目标对象、字段映射或历史查询条件；本轮没有点击任何同步按钮。
- 用户提供的“参数设置—运行设置”截图中，“程序开启后10秒内开启线程”“是否开启线外组”“是否开启基础数据下载”均显示已勾选，语言为简体中文。该页没有显示 `Barcode_Info`、`PlanData`、`ReworkTable`、`QCDownLoad` 或逐表目标映射，不能据此推断这些数据通道已启用。“配置文件”按钮可见，但本轮未打开其内容。
- 用户随后提供的“同步参数设置”页面显示每条线可分别配置 `IHS` 与 `IHS_SHARED` 两个数据库；数据库详情区展示数据库名、数据源、用户名、密码掩码、线 ID、启用状态、组 ID 和线名等项目。本文不记录截图中的连接地址、账号或任何密码信息。数据库表列表列出表 ID、源表名、服务器表名和同步版本；“新表/改表/删表”操作可对单条映射维护。表信息窗口的字段为表 ID、数据库 ID、表名、服务器表名、查询语句和同步版本；示例 `Colour_Master` 的查询为 `select * from Colour_Master`。这与 `T_TableInfo` 的 `TableId/DbId/TableName/StableName/SelectSql/SyncVersion` 字段相对应，其中“服务器表名”对应 `StableName` 的判断同时基于静态代码字段和截图控件含义。同步版本的业务游标规则仍未完全确认。
- 上述数据库设置截图中选中的是 1 号线的 `IHS` 与 `IHS_SHARED`。当前可读到的映射行只是截图可见部分，不代表全线、全库或滚动列表的完整清单：

| 数据库 | 源表名 | 页面显示的服务器表名 | 备注 |
|---|---|---|---|
| IHS | `ODP_Master` | `P_ODP_Master` | 截图可读 |
| IHS | `ODP_Detail` | `P_ODP_Detail` | 截图可读 |
| IHS | `Style_Loading` | `B_Style_Loading` | 截图可读 |
| IHS | `Style_Loading_History` | `B_Style_Loading_History` | 截图可读 |
| IHS | `Hanger_Master` | `P_Hanger_Master` | 截图可读 |
| IHS | `Hanger_Routing_Details` | `P_Hanger_Routing_Details` | 截图可读 |
| IHS | `Line_Daily_Break_Times` | `P_Line_Daily_Break_Times` | 截图可读 |
| IHS | `Line_Daily_Shift_Times` | `P_Line_Daily_Shift_Times` | 截图可读 |
| IHS | `Line_Information` | `B_Line_Information` | 截图可读 |
| IHS | `Line_Pusher_Setup` | `B_Line_Pusher_Setup` | 截图可读 |
| IHS | `Line_Wireless_Controller` | `B_Line_Wireless_Contr…` | 目标名被列宽截断，需完整窗口确认 |
| IHS | `Line_System_Configuration` | `B_Line_System_Confi…` | 目标名被列宽截断，需完整窗口确认 |
| IHS | `Line_Network_Setup` | `B_Line_Network_Setup` | 截图可读 |
| IHS_SHARED | `Colour_Master` | `B_Colour_Master` | 编辑窗口另显示查询 `select * from Colour_Master` |
| IHS_SHARED | `Hanger_Registry` | `B_Hanger_Registry` | 截图可读 |
| IHS_SHARED | `Machine_Codes` | `B_Machine_Codes` | 截图可读 |
| IHS_SHARED | `Operation_Codes` | `B_Operation_Codes` | 截图可读 |
| IHS_SHARED | `Pay_Categories` | `B_Pay_Categories` | 截图可读 |
| IHS_SHARED | `Primary_Codes` | `B_Primary_Codes` | 截图可读 |
| IHS_SHARED | `QC_Header_Codes` | `B_QC_Header_Codes` | 截图可读 |
| IHS_SHARED | `QC_Rework` | `P_QC_Rework` | 截图可读 |
| IHS_SHARED | `QC_Sub_Codes` | `B_QC_Sub_Codes` | 截图可读 |
| IHS_SHARED | `Size_Master` | `B_Size_Master` | 截图可读 |
| IHS_SHARED | `Sorting_Information_B…` | `B_Sorting_Information…` | 两端名称均被列宽截断 |
| IHS_SHARED | `Style_Colours_Not_Used` | `B_Style_Colours_Not_Used` | 截图可读 |

数据库级“改库”窗口显示数据库 ID、数据库名、数据源、用户名、密码、线 ID、线名、组 ID、启用状态，以及“测试连接”和“保存”按钮；“新库”窗口显示同类字段，数据库 ID 当前为空、组 ID 显示为 0、启用复选框显示已选。密码输入框在修改窗口中以明文显示。本文不抄录或存储截图中的任何连接地址、账号或密码；如果截图中的密码仍在使用，应由系统责任人评估轮换，后续截图应先遮盖该字段。本轮没有点击测试连接、保存或任何新增/修改/删除按钮。

“复表”按钮打开标题为“复制表数据”的窗口，提供源数据库、目标数据库下拉框、“复制”按钮和进度条。这证明客户端提供了从一个数据库配置指向另一个数据库的复制入口，但截图不能确定具体复制的是 `T_TableInfo` 映射、源表行数据还是其他配置，也不能确定追加/覆盖策略、事务边界和失败回滚方式；本轮未点击“复制”。新建表窗口显示可填表名、服务器表名和查询语句，但没有保存或触发任何表级新增、修改、删除操作。截图没有展示查询语句对来源库的权限边界、是否支持任意 SQL、增量过滤策略或失败回滚行为，这些仍需静态代码或厂商资料确认。

将截图中可完整辨认的源表名/服务器表名与本报告下方按当前 IDS `T_TableInfo` 汇总的全局映射清单对照，均能找到相同映射；被截图列宽截断的 `Line_Wireless_Controller`、`Line_System_Configuration` 和 `Sorting_Information_By_Line` 也能在 IDS 汇总清单中找到对应完整 `StableName`。但 `T_TableInfo` 汇总按数据库标识计数，不能单独证明截图这个具体线体的每一行配置在截图时均已启用或已成功上传；这需要按对应 `DbId` 核验实际记录和客户端运行状态。

用户补充确认各产线的表映射规则一致，差异若存在主要可能在各数据库配置的 `SelectSql`。这与当前 IDS 汇总结果相符：41 个常规源表各按 7 个数据库标识登记，`SelectSql` 都是 `SELECT * FROM <同名表>` 形式，没有看到按线体添加筛选条件的查询。该结论限定于当前 IDS 快照和常规表映射；特殊 `_LS` 行、独立条码同步及未来改配置仍需另行核对。
- 副本配置中另见 `PlanData=0`、`ReworkTable=false`、`QCDownLoad=0`；尚未将这些键逐一对应到 VPD/VRD/VSD 的具体上传行为，不据此判定这些视图在实际机器上的开关状态。
- 对该副本顶层 EXE/DLL 做静态字符串扫描，`Ina.BLL.dll` 中能看到 VHRD 查询模板以及一个 `VPD_StylePlanID` 字段别名；未找到 `V_ProductData`、`V_ReworkData`、`V_ScrapData`、`QCRI_` 或 `TGI_` 的字面字符串。该结果只说明当前副本可见的编译字符串范围，不足以证明软件不支持相关数据对象，也不能代替厂商的功能说明或实际客户端配置。
- `T_ColInfo` 当前为 0 行，不能提供逐字段映射说明。851 个字段中只有 49 个存在 SQL Server `MS_Description` 扩展属性，其余字段没有数据库注释。
- `T_DbInfo` 字段定义含 `DbPassword`，数据库注释将其标记为数据库用户密码。本次只检查字段定义，没有读取该表任何连接配置或口令值；该表应按敏感配置表处理。
- 目录中还存在 `report_user_pwd` 表。仅根据表名判断它可能承载报表账号凭据；本次未读取其任何记录，建议将其纳入敏感表权限核查。
- 系统目录登记的 34 条视图依赖都指向当前 IDS 的 `dbo` 对象，没有发现视图对其他数据库或服务器的静态依赖。外部线体同步关系另由 `T_TableInfo.SelectSql` 元数据记录。
- VHRD 关联风险已通过只返回聚合数的查询确认：底表有 `HRD_HM_Key`，但现有视图按可复用 `HM_ID + GroupId` 连接且未过滤 DelFlag；同组重复衣架号较多。视图回传的单行数、PO 和款色码需要验证后才能用于数量对账或单件追踪。

### 字段语义覆盖状态

- 两份 Excel 为 12 个接口对象提供了字段级中文说明；其中 8 个对象在本 IDS 实例实际存在，涉及 177 个字段。按同名字段和文档中可明确对照的两个别名核对，169 个实际字段与字典列名直接匹配，2 个字段通过别名对应；另有 1 个字段释义疑似重复、1 个字段的字段名与视图源列角色不一致，均须复核。`TISO_Line` 另有数据库注释可作初步说明。`ODPI_ODP_Shift`、`VHRD_Return_For_Repair`、`VHRD_STOP_Order` 尚无字段级释义。
- 四个未发现同名对象（`Tag_Info`、`V_ProductData`、`V_ReworkData`、`V_ScrapData`）共 73 个文档字段已逐字段转录，说明目前能确认供应商文档如何描述这些目标字段，但当前实例不能按同名结构验证写入值和运行规则。`Tag_Info` 另有 `P_Barcode_Info` 候选内部表，尚未证明等价。
- 49 条数据库注释现已解码为可读中文；其中多数注释与 ToIna 接口字段重合，另外只覆盖 `B_Employee_Master` 的 5 列和 `T_DbInfo` 的 6 列。除上述接口对象外，另有 57 个实际表/视图对象不在两份工作簿的完整对象字典中；即使加上 SQL 注释，仍有大量内部字段缺少可核实的中文定义。字段名前缀、SQL 类型、索引或视图关联只能提供线索，不能当作完整业务定义。
- 全部 851 个实际字段现在都在 §7 标注了释义证据状态：169 个字段名与文档直接匹配、2 个按已记录的别名关系对应、1 个文档释义疑似重复、1 个视图源列角色待确认、12 个字段只有数据库注释、666 个字段在现有字典和 SQL 注释中没有直接释义。客户端静态代码为少量字段补充了读写关系线索，但这些线索不等于厂商业务定义；其余字段用途目前不能声称已完整理解。
- 因此本报告已做到对象和字段结构全量盘点，并补入可追溯的文档释义；整个 IDS 中每个字段的业务语义尚未全部确认。后续必须结合同步客户端配置、字段取值/状态变更、吊挂操作和厂商定义逐项补齐。

#### T_TableInfo 中登记的线体表同步名称

表中 `TableName` 是登记的源表名，`StableName` 是 IDS 侧稳定名称映射。普通映射在 7 个数据库标识下各登记一条；两条 `_LS` 特殊名称各一条，且没有 `SelectSql` 或 `StableName`。41 条常规查询均已核实为整表通配查询，查询对象与 `TableName` 同名。该登记表没有 `ToIna_*` 输入表、`Tag_Info` 或回传视图的映射记录；这些不是当前通用线体表全量上传项。条码源表 `Barcode_Info` 则有独立客户端代码路径，不通过这张通用登记表配置。运行主页面截图只显示源表上传活动，没有展示客户端配置界面，不能将日志中的源表名直接当作 `StableName` 或最终目标对象。

| 源 TableName | IDS StableName | 登记行数 |
|---|---|---:|
| Bridging_Hanger_Master | P_Bridging_Hanger_Master | 7 |
| Bridging_Hanger_Routing_Details | P_Bridging_Hanger_Routing_Details | 7 |
| Classified_Scheme | B_Classified_Scheme | 7 |
| Colour_Master | B_Colour_Master | 7 |
| Employee_Master | B_Employee_Master | 7 |
| Hanger_Master | P_Hanger_Master | 7 |
| Hanger_Registry | B_Hanger_Registry | 7 |
| Hanger_Routing_Details | P_Hanger_Routing_Details | 7 |
| Line_Bridging_Information | B_Line_Bridging_Information | 7 |
| Line_Daily_Break_Times | P_Line_Daily_Break_Times | 7 |
| Line_Daily_Shift_Times | P_Line_Daily_Shift_Times | 7 |
| Line_Information | B_Line_Information | 7 |
| Line_Network_Setup | B_Line_Network_Setup | 7 |
| Line_Pusher_Setup | B_Line_Pusher_Setup | 7 |
| Line_System_Configuration | B_Line_System_Configuration | 7 |
| Line_Wireless_Controller | B_Line_Wireless_Controller | 7 |
| Line_Workstation_Setup | B_Line_Workstation_Setup | 7 |
| Lines_On_Network | B_Lines_On_Network | 7 |
| Machine_Codes | B_Machine_Codes | 7 |
| ODP_Detail | P_ODP_Detail | 7 |
| ODP_Master | P_ODP_Master | 7 |
| Operation_Codes | B_Operation_Codes | 7 |
| P_Bridging_Hanger_Routing_Details_LS | — | 1 |
| P_Hanger_Routing_Details_LS | — | 1 |
| Pay_Categories | B_Pay_Categories | 7 |
| Primary_Codes | B_Primary_Codes | 7 |
| QC_Header_Codes | B_QC_Header_Codes | 7 |
| QC_Rework | P_QC_Rework | 7 |
| QC_Sub_Codes | B_QC_Sub_Codes | 7 |
| Size_Master | B_Size_Master | 7 |
| Sorting_Information_By_Line | B_Sorting_Information_By_Line | 7 |
| Style_Colours_Not_Used | B_Style_Colours_Not_Used | 7 |
| Style_Loading | B_Style_Loading | 7 |
| Style_Loading_History | B_Style_Loading_History | 7 |
| Style_Loading_Sorting_Information | B_Style_Loading_Sorting_Information | 7 |
| Style_Master | B_Style_Master | 7 |
| Style_Operations | B_Style_Operations | 7 |
| Style_Operations_Master | B_Style_Operations_Master | 7 |
| Style_Planned_Orders | B_Style_Planned_Orders | 7 |
| Style_Route_Detail | B_Style_Route_Detail | 7 |
| Style_Route_Master | B_Style_Route_Master | 7 |
| Style_Sizes_Not_Used | B_Style_Sizes_Not_Used | 7 |
| Work_Codes | B_Work_Codes | 7 |

## 2. 文档中定义但当前 IDS 实例没有同名对象

| 文档对象 | 来源 | 当前 IDS 目录 | 结论与下一步判断 |
|---|---|---|---|
| `Tag_Info` | 7R Excel | 当前 IDS 无同名对象；另有内部表 `P_Barcode_Info` | 不能把“同名对象缺失”直接等同于“没有条码数据通道”。静态客户端副本显示 `Barcode_Info`→`P_Barcode_Info` 的独立同步实现，但副本开关为 `false`；需在实际运行机器核对，并确认它是否与 7R `Tag_Info` 等价。 |
| `V_ProductData` | 7R Excel | 当前 IDS 数据库对象目录中不存在 | 按现场口径，说明该数据对象上传尚未配置。检查客户端能否配置视图/查询结果上传及映射；同时核实它应是吊挂端视图、客户端生成结果还是 IDS 兼容视图。 |
| `V_ReworkData` | 7R Excel | 当前 IDS 数据库对象目录中不存在 | 按现场口径，说明返工数据上传尚未配置。除核对客户端对象映射外，还需确认返工聚合口径和字段来源。 |
| `V_ScrapData` | 7R Excel | 当前 IDS 数据库对象目录中不存在 | 按现场口径，说明报废数据上传尚未配置。除核对客户端对象映射外，还需确认报废聚合口径和字段来源。 |

这里的“对象目录中不存在”是通过 SQL Server 系统目录确认没有该表/视图；不是“对象存在但当前行数为 0”。用户已明确现场判断口径：对象缺失或没有同步记录，说明对应上传尚未配置。例外需谨慎辨别：`Tag_Info` 虽然没有同名对象，但 IDS 有 `P_Barcode_Info`，且静态客户端副本包含从 `Barcode_Info` 同步到 `P_Barcode_Info` 的实现。当前可确认的是对象/实现候选关系，不能据此确认现役机器已配置，也不能认定内部表与 7R 接口字段完全等价。

#### `Tag_Info` 与 `P_Barcode_Info` 的候选对应关系

本次 IDS 结构清单中，`P_Barcode_Info` 有 16 个字段，统计行数估算为 0；客户端副本代码包含从线体源 `Barcode_Info` 读取变化并写入该表的路径，`BCI_Key` 用于关联。它不是 7R 字典中同名的 `Tag_Info`，字段也不完全一致：

| 7R `Tag_Info` 字段 | IDS `P_Barcode_Info` 候选字段 | 当前证据与限制 |
|---|---|---|
| `TGI_Key` | `BCI_Key` | 均为 UUID 主键类型；静态客户端按 `BCI_Key` 关联源/目标。 |
| `TGI_TagID` | `BCI_LabelID` | 名称和长度相似，可能是标签码；业务等价仍需验证。 |
| `TGI_StyleID` | `BCI_StyleID` | 字段名与类型相近。 |
| `TGI_ColourShotDes`、`TGI_ColourDes` | `BCI_Colour` | 7R 分开描述颜色 ID 与颜色描述；IDS 只有一个颜色字段，不能建立一对一映射。 |
| `TGI_Size` | `BCI_Size` | 字段名与类型相近。 |
| `TGI_Dept` | `BCI_Dept` | 字段名与类型相近。 |
| `TGI_InsertTime` | `BCI_InserTime` | 字段名近似，IDS 字段名少一个字母；时间边界仍需验证。 |
| `TGI_Used`、`TGI_Used_Count` | `BCI_Used`、`BCI_Used_Count` | 名称相近；使用次数的更新时机仍需验证。 |
| `TGI_LotNumber`、`TGI_Trim`、`TGI_Quantity`、`TGI_HM_Key` | `BCI_LotNumber`、`BCI_Trim`、`BCI_Quantity`、`BCI_HM_Key` | 名称相近；包号、裁床次/扩展字段、标签件数及衣架绑定时点未现场确认。 |
| `TGI_PlannedOrders`、`TGI_PerQuantity`、`TGI_MaxMantissa` | 无对应字段 | 7R 提供线计划/订单、单位数和最大尾数；当前 `P_Barcode_Info` 结构未承载这些字段。 |
| — | `LineId`、`DelFlag`、`GroupId` | IDS 内部额外字段；客户端代码会携带 `DelFlag`，其跨表/跨系统含义需按 IDS 约定确认。 |

副本配置中的 `Barcode_Info=false`，所以只能说“该副本配置关闭该路径”。运行客户端主页面截图显示线体同步正在运行，但没有条码项或表映射设置，故无法判断现役客户端是否启用了该条码通道。静态扫描未在该副本顶层程序集中找到 `TGI_` 字段，但这不足以证明不支持 7R 标签接口。此证据足以说明：在添加 `Tag_Info` 前，应先确认 `P_Barcode_Info` 是否就是已部署的内部承接表，以及现役客户端的映射和数据流；不能仅因字典对象名缺失就立即新建同名表。

### 缺失对象对应的文档字段

以下字段是 Excel 明确声明、但当前 IDS 没有同名对象承载的字段。

| 缺失对象 | 文档定义字段（含类型） |
|---|---|
| `Tag_Info` | TGI_Key (uniqueidentifier)；TGI_TagID (nvarchar(20))；TGI_PlannedOrders (nvarchar(50))；TGI_StyleID (nvarchar(25))；TGI_ColourShotDes (nvarchar(10))；TGI_ColourDes (nvarchar(50))；TGI_Size (nvarchar(10))；TGI_Dept (nvarchar(25))；TGI_InsertTime (datetime)；TGI_Used (bit)；TGI_Used_Count (int)；TGI_LotNumber (nvarchar(25))；TGI_Trim (nvarchar(25))；TGI_Quantity (int)；TGI_HM_Key (uniqueidentifier)；TGI_PerQuantity (int)；TGI_MaxMantissa (int) |
| `V_ProductData` | VPD_ProductDate (date)；VPD_EmployeeID (nvarchar(15))；VPD_EmployeeName (nvarchar(50))；VPD_LineID (nvarchar(50))；VPD_StationID (int)；VPD_StyleID (nvarchar(25))；VPD_StyleName (nvarchar(50))；VPD_StylePlanID (nvarchar(50))；VPD_ColourID (nvarchar(10))；VPD_ColourName (nvarchar(50))；VPD_SizeID (nvarchar(10))；VPD_SizeName (nvarchar(50))；VPD_ProcessID (nvarchar(25))；VPD_ProcessName (nvarchar(35))；VPD_Quantity (int)；VPD_Wages (decimal(18.2))；VPD_Hours (decimal(18,2)) |
| `V_ReworkData` | VRD_ReworkDate (date)；VRD_EmployeeID (nvarchar(15))；VRD_EmployeeName (nvarchar(50))；VRD_LineID (nvarchar(50))；VRD_StationID_QC (int)；VRD_StationID_Defect (int)；VRD_StationID_Repair (int)；VRD_StyleID (nvarchar(25))；VRD_StyleName (nvarchar(50))；VRD_StylePlanID (nvarchar(50))；VRD_ColourID (nvarchar(10))；VRD_ColourName (nvarchar(50))；VRD_SizeID (nvarchar(10))；VRD_SizeName (nvarchar(50))；VRD_ProcessID (nvarchar(25))；VRD_ProcessName (nvarchar(35))；VRD_HangerQuantity (int)；VRD_ReworkQuantity (int)；VRD_RepairQuantity (int)；VRD_QCID (int)；VRD_QCName (nvarchar(32)) |
| `V_ScrapData` | VSD_ScrapDate (date)；VSD_EmployeeID (nvarchar(15))；VSD_EmployeeName (nvarchar(50))；VSD_LineID (nvarchar(50))；VSD_StationID_QC (int)；VSD_StationID_Defect (int)；VSD_StyleID (nvarchar(25))；VSD_StyleName (nvarchar(50))；VSD_StylePlanID (nvarchar(50))；VSD_ColourID (nvarchar(10))；VSD_ColourName (nvarchar(50))；VSD_SizeID (nvarchar(10))；VSD_SizeName (nvarchar(50))；VSD_ProcessID (nvarchar(25))；VSD_ProcessName (nvarchar(25))；VSD_HangerQuantity (int)；VSD_VRD_QCID (int)；VSD_VRD_QCName (nvarchar(32)) |

两份 Excel 是对接对象字段说明，不是整个 IDS 数据库的数据字典。当前 IDS 共 65 个表/视图对象，其中 8 个对象与文档对象同名；另外 57 个对象未在两份 Excel 作为完整对象结构列出，主要是衣拿内部基础资料、产线配置、生产事务表、颜色/尺码输入表及 IDS 自身元数据。完整清单见第 6 节。

## 3. 文档字段与实际字段的逐项差异

### 3.1 文档中有、实际视图没有的字段

| 文档对象 | 文档字段 | 当前实际结构 | 判断 |
|---|---|---|---|
| V_Hanger_Routing_Details | VHRD_HM_CM_Key (int) | 视图未投影 | 底层 `P_Hanger_Master.HM_CM_Key` 存在，可评估增加只读别名。 |
| V_Hanger_Routing_Details | VHRD_HM_Production_Time (real) | 视图未投影 | 底层 `P_Hanger_Master.HM_Production_Time` 存在，可评估增加只读别名。 |
| V_Hanger_Routing_Details | VHRD_HM_SM_Key (int) | 视图未投影 | 底层 `P_Hanger_Master.HM_SM_Key` 存在，可评估增加只读别名。 |
| V_Hanger_Routing_Details | VHRD_HM_SRM_Key (int) | 视图未投影 | 底层 `P_Hanger_Master.HM_SRM_Key` 存在，可评估增加只读别名。 |
| V_Hanger_Routing_Details | VHRD_HM_ST_Key (int) | 视图未投影 | 底层 `P_Hanger_Master.HM_ST_Key` 存在，可评估增加只读别名。 |
| V_Hanger_Routing_Details | VHRD_HM_STPO_Key (int) | 视图未投影 | 底层 `P_Hanger_Master.HM_STPO_Key` 存在；当前视图提供计划 ID `VHRD_STPO_ID`。 |
| V_Hanger_Routing_Details | VHRD_ST_Line (smallint) | 视图未投影 | 底层 `B_Style_Master.ST_Line` 存在，可评估增加只读别名。 |
| V_ODP_Info | ODPI_LCD_Name (nvarchar(15)) | 实际字段为 `ODPI_EM_LCD_Name` | 是字段别名不同，不是缺少员工 LCD 姓名数据。 |
| V_QC_Rework_Info | 文档误写 ODPI_STPO_ID (nvarchar(50)) | 实际字段为 `QCRI_STPO_ID` | IDS Excel 将 QCRI 的计划字段误写为 ODPI 前缀。 |
| V_QC_Rework_Info | 文档未列 QCRI_HM_ID、QCRI_HM_Key | 底层 `P_QC_Rework.QCR_HM_ID`、`QCR_HM_Key` 存在，视图未投影 | 可向厂商确认是否安全发布；用于追踪前还要确认它们代表事件发生时的衣架关系。 |

### 3.2 实际视图有、文档集之间新增或不一致的字段

| 实际对象 | 实际新增/不同字段 | 说明 |
|---|---|---|
| ToIna_Style_Operations | TISO_Line nvarchar(50) | 当前下发表比 7R 多一个产线字段。 |
| V_ODP_Info | ODPI_ODP_Shift | 当前 ODPI 额外暴露班次字段。 |
| V_Hanger_Routing_Details | VHRD_Return_For_Repair、VHRD_STOP_Order | 实际视图有，7R 和 IDS 两份 Excel 均未列出。 |
| V_Hanger_Routing_Details | VHRD_HM_Current_Location_Type、VHRD_HM_Current_Location | 7R 列出，2024 IDS Excel 未列出；当前视图存在。 |
| V_Hanger_Routing_Details | VHRD_EM_SSN | IDS Excel 列出，7R 未列出；当前视图存在。 |
| V_Hanger_Routing_Details | VHRD_STPO_ID | IDS Excel 列出；7R 对应字段写为 VHRD_HM_STPO_Key。当前视图提供计划 ID 字符串，不提供该 Key 投影。 |
| V_QC_Rework_Info | QCRI_STPO_ID | IDS Excel 把该字段写成 ODPI_STPO_ID；实际视图名称按 QCRI 前缀。 |

### 3.3 文档和实际字段类型不一致

| 来源文档 | 对象.字段 | 文档类型 | 当前 IDS 类型 |
|---|---|---|---|
| 7R Excel | V_Hanger_Routing_Details.VHRD_HM_Lot_Number | nvarchar(25) | nvarchar(100) |
| 7R Excel | V_Hanger_Routing_Details.VHRD_OC_Description | nvarchar(35) | nvarchar(255) |
| 7R Excel | V_Hanger_Routing_Details.VHRD_STOP_Number | smallint | nvarchar(50) |
| 7R Excel | V_Hanger_Routing_Details.VHRD_HM_ID | nvarchar(12) | nvarchar(50) |
| 7R Excel | V_Hanger_Routing_Details.VHRD_HRD_Comments | nvarchar(50) | nvarchar(255) |
| IDS Excel | V_ODP_Info.ODPI_Lot_Number | nvarchar(25) | nvarchar(100) |
| IDS Excel | V_Hanger_Routing_Details.VHRD_HM_Lot_Number | nvarchar(25) | nvarchar(100) |
| IDS Excel | V_Hanger_Routing_Details.VHRD_HM_ID | nvarchar(12) | nvarchar(50) |
| IDS Excel | V_Hanger_Routing_Details.VHRD_HRD_Comments | nvarchar(50) | nvarchar(255) |
| IDS Excel | V_QC_Rework_Info.QCRI_LineNumber | int | smallint |

此外，`P_Hanger_Routing_Details.HRD_HM_ID` 为 nvarchar(12)，`P_Hanger_Master.HM_ID` 为 nvarchar(50)，VHRD 按两个字段相等并同时匹配 GroupId。`P_QC_Rework.QCR_HM_ID` 也为 nvarchar(12)，`P_Hanger_Master.HM_ID` 为 nvarchar(50)。连接键长度不一致；不得静默截断，需确认合法长度和数据完整性。

### 3.4 两份字段字典给出的逐字段中文释义

本节将两份 Excel 中的字段释义逐行转录，并与当前 IDS 实例字段名核对。它可以回答“供应商文档说这列是什么”，但不自动证明吊挂客户端实际如何填写该字段；字段单位、状态变化和同步触发条件仍需现场验证。工作簿中未给出意义的字段会明确标为“工作簿未提供中文释义”。

#### ToIna_Employee（7R 标准公共数据库对接字典 V2.0）

文档对象标题：ToIna_Employee员工信息表（TIE）
本地结构核对：当前 IDS 实例对象存在，共 10 列。以下含义是工作簿的原文口径，不代表已通过吊挂端行为或客户端配置实测。

| 字段 | 文档中文释义 | 文档类型 | 限制/备注 | 当前 IDS 同名字段核对 |
|---|---|---|---|---|
| `TIE_Key` | Key码；主键 | int | PK | 存在 |
| `TIE_ID` | 员工编号；非空,TIE_ID>=0 and TIE_ID<=999999 | int | Not Null | 存在 |
| `TIE_Name` | 员工姓名 | nvarchar(50) | — | 存在 |
| `TIE_Sex` | 员工性别 | nvarchar(1) | — | 存在 |
| `TIE_RFID` | 员工卡号 | nvarchar(10) | — | 存在 |
| `TIE_Line` | 员工所属生产线号 | nvarchar(50) | — | 存在 |
| `TIE_Workshop` | 员工所属生产车间 | nvarchar(25) | — | 存在 |
| `TIE_Group` | 员工所属组别 | nvarchar(15) | — | 存在 |
| `TIE_Department` | 员工所属部门 | nvarchar(15) | — | 存在 |
| `TIE_SSN` | 员工身份唯一识别信息 | nvarchar(15) | — | 存在 |

#### ToIna_Style（7R 标准公共数据库对接字典 V2.0）

文档对象标题：ToIna_Style款式信息表（TIS）
本地结构核对：当前 IDS 实例对象存在，共 8 列。以下含义是工作簿的原文口径，不代表已通过吊挂端行为或客户端配置实测。

| 字段 | 文档中文释义 | 文档类型 | 限制/备注 | 当前 IDS 同名字段核对 |
|---|---|---|---|---|
| `TIS_Key` | Key码；主键 | int | PK | 存在 |
| `TIS_StyleID` | 款号；“款式”唯一识别字段，不同款式不可重复 | nvarchar(25) | Not Null | 存在 |
| `TIS_StyleDescription` | 款式描述 | nvarchar(50) | — | 存在 |
| `TIS_StyleCollection` | 品名；或者款号 | nvarchar(25) | — | 存在 |
| `TIS_Used` | 是否已导入；0：未导入过；1：已导入过 | bit | — | 存在 |
| `TIS_UsedTime` | 导入时间 | datetime | — | 存在 |
| `TIS_Quantity` | 生产数量 | int | — | 存在 |
| `TIS_Line` | 生产线号 | nvarchar(50) | — | 存在 |

#### ToIna_Style_Operations（7R 标准公共数据库对接字典 V2.0）

文档对象标题：ToIna_Style_Operations款式工序流信息表（TISO）
本地结构核对：当前 IDS 实例对象存在，共 10 列。以下含义是工作簿的原文口径，不代表已通过吊挂端行为或客户端配置实测。

| 字段 | 文档中文释义 | 文档类型 | 限制/备注 | 当前 IDS 同名字段核对 |
|---|---|---|---|---|
| `TISO_Key` | Key码；主键 | int | PK | 存在 |
| `TISO_StyleID` | 款号 | nvarchar(25) | Not Null | 存在 |
| `TISO_Op_Code` | 工序代码 | nvarchar(25) | Not Null | 存在 |
| `TISO_Op_Order` | 工序顺序 | smallint | Not Null | 存在 |
| `TISO_Op_Name` | 工序名称 | nvarchar(35) | — | 存在 |
| `TISO_Op_PieceRate` | 工序单价 | real | — | 存在 |
| `TISO_Op_StandardTime` | 工序标准工时 | real | — | 存在 |
| `TISO_Op_MachineType` | 使用车台类型 | nvarchar(25) | — | 存在 |
| `TISO_Op_IsOnline` | 线内/线外工序识别；1：线上工序；0：线外工序； | bit | — | 存在 |

#### ToIna_Route_Map（7R 标准公共数据库对接字典 V2.0）

文档对象标题：ToIna_Route_Map款式路线图表（TIRM）
本地结构核对：当前 IDS 实例对象存在，共 8 列。以下含义是工作簿的原文口径，不代表已通过吊挂端行为或客户端配置实测。

| 字段 | 文档中文释义 | 文档类型 | 限制/备注 | 当前 IDS 同名字段核对 |
|---|---|---|---|---|
| `TIRM_Key` | Key码；主键 | int | PK | 存在 |
| `TIRM_TIS_StyleID` | 款号；非空 | nvarchar(25) | Not Null | 存在 |
| `TIRM_TISO_Op_Code` | 工序代码；非空 | nvarchar(25) | Not Null | 存在 |
| `TIRM_Line` | 生产线号 | nvarchar(50) | Not Null | 存在 |
| `TIRM_Station_Number` | 站位号；非空 | int | Not Null | 存在 |
| `TIRM_Created_Date` | 创建时间 | datetime | — | 存在 |
| `TIRM_Loading_Line` | 上裁线号 | nvarchar(50) | Not Null | 存在 |
| `TIRM_MapKey` | 路线图唯一码；一套路线图只有一个码 | nvarchar(50) | Not Null | 存在 |

#### ToIna_Style_Planned_Orders（7R 标准公共数据库对接字典 V2.0）

文档对象标题：ToIna_Style_Planned_Orders款式线计划信息表（TSPO）
本地结构核对：当前 IDS 实例对象存在，共 18 列。以下含义是工作簿的原文口径，不代表已通过吊挂端行为或客户端配置实测。

| 字段 | 文档中文释义 | 文档类型 | 限制/备注 | 当前 IDS 同名字段核对 |
|---|---|---|---|---|
| `TSPO_Key` | Key码；主键 | int | PK | 存在 |
| `TSPO_ID` | 线计划号；非空，或者订单号 | nvarchar(50) | Not Null | 存在 |
| `TSPO_StyleID` | 款号；非空 | nvarchar(25) | Not Null | 存在 |
| `TSPO_CI_Name` | 客户名 | nvarchar(50) | — | 存在 |
| `TSPO_PlannedQuantity` | 计划数量 | int | — | 存在 |
| `TSPO_Comments` | 备注信息 | nvarchar(255) | — | 存在 |
| `TSPO_PlanStartDate` | 计划开始日期 | datetime | — | 存在 |
| `TSPO_PlanFinishDate` | 计划完成日期 | datetime | — | 存在 |
| `TSPO_Level` | 计划等级 | int | — | 存在 |
| `TSPO_ColourID` | 颜色ID | nvarchar(10) | — | 存在 |
| `TSPO_Colour_Description` | 颜色描述 | nvarchar(50) | — | 存在 |
| `TSPO_SizeID` | 尺码ID | nvarchar(10) | — | 存在 |
| `TSPO_Size_Description` | 尺码描述 | nvarchar(50) | — | 存在 |
| `TSPO_CreateDate` | 创建时间 | datetime | — | 存在 |
| `TSPO_ModifiedDate` | 最后修改时间 | datetime | — | 存在 |
| `TSPO_Line` | 生产线号 | nvarchar(50) | — | 存在 |
| `TSPO_Used` | 是否已导入；0：未导入过；1：已导入过 | bit | — | 存在 |
| `TSPO_UsedTime` | 使用时间 | datetime | — | 存在 |

#### Tag_Info（7R 标准公共数据库对接字典 V2.0）

文档对象标题：Tag_Info标签信息表（TGI）
本地结构核对：当前 IDS 实例没有同名 `Tag_Info`；另有内部 `P_Barcode_Info` 候选结构及客户端副本中的 `Barcode_Info` 同步代码。以下含义是工作簿原文口径，不代表两套对象字段等价或实际运行机已启用同步。

| 字段 | 文档中文释义 | 文档类型 | 限制/备注 | 当前 IDS 同名字段核对 |
|---|---|---|---|---|
| `TGI_Key` | Key码；主键 | uniqueidentifier | PK | 对象不存在 |
| `TGI_TagID` | 标签号；非空 | nvarchar(20) | Not Null | 对象不存在 |
| `TGI_PlannedOrders` | 线计划号；或者订单号 | nvarchar(50) | — | 对象不存在 |
| `TGI_StyleID` | 款号 | nvarchar(25) | — | 对象不存在 |
| `TGI_ColourShotDes` | 颜色ID | nvarchar(10) | — | 对象不存在 |
| `TGI_ColourDes` | 颜色描述 | nvarchar(50) | — | 对象不存在 |
| `TGI_Size` | 尺码 | nvarchar(10) | — | 对象不存在 |
| `TGI_Dept` | 扩展字段；存款式的扩展属性 | nvarchar(25) | — | 对象不存在 |
| `TGI_InsertTime` | 导入时间 | datetime | — | 对象不存在 |
| `TGI_Used` | 是否使用；0：未使用；1：已使用 | bit | — | 对象不存在 |
| `TGI_Used_Count` | 已使用数量 | int | — | 对象不存在 |
| `TGI_LotNumber` | 包号 | nvarchar(25) | — | 对象不存在 |
| `TGI_Trim` | 扩展字段；存款式的扩展属性 | nvarchar(25) | — | 对象不存在 |
| `TGI_Quantity` | 标签对应衣服的件数 | int | — | 对象不存在 |
| `TGI_HM_Key` | 衣架KEY码 | uniqueidentifier | — | 对象不存在 |
| `TGI_PerQuantity` | 标签对应的单位数；没有数据时取TGI_Quantity | int | — | 对象不存在 |
| `TGI_MaxMantissa` | 最大尾数 | int | — | 对象不存在 |

#### V_ProductData（7R 标准公共数据库对接字典 V2.0）

文档对象标题：V_ProductData产量数据视图（VPD）    注意：需要购买和安装《生产数据服务器同步软件》才可使用此视图
文档部署注记：注意：需要购买和安装《生产数据服务器同步软件》才可使用此视图
本地结构核对：当前 IDS 实例对象目录中不存在。以下含义是工作簿的原文口径，不代表已通过吊挂端行为或客户端配置实测。

| 字段 | 文档中文释义 | 文档类型 | 限制/备注 | 当前 IDS 同名字段核对 |
|---|---|---|---|---|
| `VPD_ProductDate` | 日期 | date | — | 对象不存在 |
| `VPD_EmployeeID` | 员工编号 | nvarchar(15) | — | 对象不存在 |
| `VPD_EmployeeName` | 员工姓名 | nvarchar(50) | — | 对象不存在 |
| `VPD_LineID` | 线号 | nvarchar(50) | — | 对象不存在 |
| `VPD_StationID` | 站位号 | int | — | 对象不存在 |
| `VPD_StyleID` | 款式编号 | nvarchar(25) | — | 对象不存在 |
| `VPD_StyleName` | 款式名称 | nvarchar(50) | — | 对象不存在 |
| `VPD_StylePlanID` | 款式计划号 | nvarchar(50) | — | 对象不存在 |
| `VPD_ColourID` | 颜色编号 | nvarchar(10) | — | 对象不存在 |
| `VPD_ColourName` | 颜色名称 | nvarchar(50) | — | 对象不存在 |
| `VPD_SizeID` | 尺码编号 | nvarchar(10) | — | 对象不存在 |
| `VPD_SizeName` | 尺码名称 | nvarchar(50) | — | 对象不存在 |
| `VPD_ProcessID` | 工序编号 | nvarchar(25) | — | 对象不存在 |
| `VPD_ProcessName` | 工序名称 | nvarchar(35) | — | 对象不存在 |
| `VPD_Quantity` | 数量 | int | — | 对象不存在 |
| `VPD_Wages` | 工资 | decimal(18.2) | — | 对象不存在 |
| `VPD_Hours` | 工时；单位:秒 | decimal(18,2) | — | 对象不存在 |

#### V_ReworkData（7R 标准公共数据库对接字典 V2.0）

文档对象标题：V_ReworkData返工数据视图（VRD）     注意：需要购买和安装《生产数据服务器同步软件》才可使用此视图
文档部署注记：注意：需要购买和安装《生产数据服务器同步软件》才可使用此视图
本地结构核对：当前 IDS 实例对象目录中不存在。以下含义是工作簿的原文口径，不代表已通过吊挂端行为或客户端配置实测。

| 字段 | 文档中文释义 | 文档类型 | 限制/备注 | 当前 IDS 同名字段核对 |
|---|---|---|---|---|
| `VRD_ReworkDate` | 日期 | date | — | 对象不存在 |
| `VRD_EmployeeID` | 员工编号 | nvarchar(15) | — | 对象不存在 |
| `VRD_EmployeeName` | 员工姓名 | nvarchar(50) | — | 对象不存在 |
| `VRD_LineID` | 线号 | nvarchar(50) | — | 对象不存在 |
| `VRD_StationID_QC` | 质检站位号 | int | — | 对象不存在 |
| `VRD_StationID_Defect` | 责任站位号 | int | — | 对象不存在 |
| `VRD_StationID_Repair` | 修复站位号 | int | — | 对象不存在 |
| `VRD_StyleID` | 款式编号 | nvarchar(25) | — | 对象不存在 |
| `VRD_StyleName` | 款式名称 | nvarchar(50) | — | 对象不存在 |
| `VRD_StylePlanID` | 款式计划号 | nvarchar(50) | — | 对象不存在 |
| `VRD_ColourID` | 颜色编号 | nvarchar(10) | — | 对象不存在 |
| `VRD_ColourName` | 颜色名称 | nvarchar(50) | — | 对象不存在 |
| `VRD_SizeID` | 尺码编号 | nvarchar(10) | — | 对象不存在 |
| `VRD_SizeName` | 尺码名称 | nvarchar(50) | — | 对象不存在 |
| `VRD_ProcessID` | 工序编号 | nvarchar(25) | — | 对象不存在 |
| `VRD_ProcessName` | 工序名称 | nvarchar(35) | — | 对象不存在 |
| `VRD_HangerQuantity` | 衣架数量 | int | — | 对象不存在 |
| `VRD_ReworkQuantity` | 返工数量 | int | — | 对象不存在 |
| `VRD_RepairQuantity` | 修复数量 | int | — | 对象不存在 |
| `VRD_QCID` | 质检编号 | int | — | 对象不存在 |
| `VRD_QCName` | 质检名称 | nvarchar(32) | — | 对象不存在 |

#### V_ScrapData（7R 标准公共数据库对接字典 V2.0）

文档对象标题：V_ScrapData报废数据视图（VSD）      注意：需要购买和安装《生产数据服务器同步软件》才可使用此视图
文档部署注记：注意：需要购买和安装《生产数据服务器同步软件》才可使用此视图
本地结构核对：当前 IDS 实例对象目录中不存在。以下含义是工作簿的原文口径，不代表已通过吊挂端行为或客户端配置实测。

| 字段 | 文档中文释义 | 文档类型 | 限制/备注 | 当前 IDS 同名字段核对 |
|---|---|---|---|---|
| `VSD_ScrapDate` | 日期 | date | — | 对象不存在 |
| `VSD_EmployeeID` | 员工编号 | nvarchar(15) | — | 对象不存在 |
| `VSD_EmployeeName` | 员工姓名 | nvarchar(50) | — | 对象不存在 |
| `VSD_LineID` | 线号 | nvarchar(50) | — | 对象不存在 |
| `VSD_StationID_QC` | 质检站位号 | int | — | 对象不存在 |
| `VSD_StationID_Defect` | 责任站位号 | int | — | 对象不存在 |
| `VSD_StyleID` | 款式编号 | nvarchar(25) | — | 对象不存在 |
| `VSD_StyleName` | 款式名称 | nvarchar(50) | — | 对象不存在 |
| `VSD_StylePlanID` | 款式计划号 | nvarchar(50) | — | 对象不存在 |
| `VSD_ColourID` | 颜色编号 | nvarchar(10) | — | 对象不存在 |
| `VSD_ColourName` | 颜色名称 | nvarchar(50) | — | 对象不存在 |
| `VSD_SizeID` | 尺码编号 | nvarchar(10) | — | 对象不存在 |
| `VSD_SizeName` | 尺码名称 | nvarchar(50) | — | 对象不存在 |
| `VSD_ProcessID` | 工序编号 | nvarchar(25) | — | 对象不存在 |
| `VSD_ProcessName` | 工序名称 | nvarchar(25) | — | 对象不存在 |
| `VSD_HangerQuantity` | 衣架数量 | int | — | 对象不存在 |
| `VSD_VRD_QCID` | 质检编号 | int | — | 对象不存在 |
| `VSD_VRD_QCName` | 质检名称 | nvarchar(32) | — | 对象不存在 |

#### V_Hanger_Routing_Details（7R 标准公共数据库对接字典 V2.0）

文档对象标题：V_Hanger_Routing_Details衣架记录视图（VHRD）      注意：需要购买和安装《生产数据服务器同步软件》才可使用此视图
文档部署注记：注意：需要购买和安装《生产数据服务器同步软件》才可使用此视图
本地结构核对：当前 IDS 实例对象存在，共 39 列。以下含义是工作簿的原文口径，不代表已通过吊挂端行为或客户端配置实测。

| 字段 | 文档中文释义 | 文档类型 | 限制/备注 | 当前 IDS 同名字段核对 |
|---|---|---|---|---|
| `VHRD_HRD_Key` | 主键 | uniqueidentifier | — | 存在 |
| `VHRD_HM_ST_Key` | 款式Key码 | int | — | 同名字段未找到 |
| `VHRD_HM_Lot_Number` | 包号 | nvarchar(25) | — | 存在 |
| `VHRD_HM_CM_Key` | 颜色Key码 | int | — | 同名字段未找到 |
| `VHRD_HM_SM_Key` | 尺码Key码 | int | — | 同名字段未找到 |
| `VHRD_HM_Item_Card` | 扎牌号码 | nvarchar(20) | — | 存在 |
| `VHRD_HM_Garments_Per_Hanger` | 衣架上裁件数 | smallint | — | 存在 |
| `VHRD_HRD_OC_Key` | 工序编号 | int | — | 存在 |
| `VHRD_HRD_EM_Key` | 员工工号 | int | — | 存在 |
| `VHRD_HRD_Line` | 生产线号 | smallint | — | 存在 |
| `VHRD_HRD_Workstation` | 工作站 | smallint | — | 存在 |
| `VHRD_HRD_ODP_Credited` | 是否记产量；0：否；1:是 | bit | — | 存在 |
| `VHRD_HRD_Completed_Time` | 完成时间 | datetime | — | 存在 |
| `VHRD_HRD_Started_Time` | 开始时间 | datetime | — | 存在 |
| `VHRD_ST_ID` | 款式编号 | nvarchar(25) | — | 存在 |
| `VHRD_ST_Description` | 款式描述 | nvarchar(50) | — | 存在 |
| `VHRD_ST_Collection` | 款式品名 | nvarchar(25) | — | 存在 |
| `VHRD_ST_Fabric` | 布料 | nvarchar(25) | — | 存在 |
| `VHRD_ST_Trim` | 裁床次 | nvarchar(25) | — | 存在 |
| `VHRD_OC_Description` | 工序描述 | nvarchar(35) | — | 存在 |
| `VHRD_OC_Standard_Time` | 工序标注工时 | real | — | 存在 |
| `VHRD_OC_Piece_Rate` | 工序计件工资 | real | — | 存在 |
| `VHRD_PC_Description` | 工序分类名 | nvarchar(25) | — | 存在 |
| `VHRD_EM_FirstName` | 员工姓 | nvarchar(50) | — | 存在 |
| `VHRD_EM_LastName` | 员工名 | nvarchar(50) | — | 存在 |
| `VHRD_EM_LCD_Name` | LCD显示员工姓名 | nvarchar(15) | — | 存在 |
| `VHRD_STOP_Number` | 工序号 | smallint | — | 存在 |
| `VHRD_HM_ID` | 衣架ID | nvarchar(12) | — | 存在 |
| `VHRD_HM_Loaded_On_Line` | 衣架上裁线号 | smallint | — | 存在 |
| `VHRD_HM_Current_Line` | 衣架当前所在线号 | smallint | — | 存在 |
| `VHRD_HM_SRM_Key` | 路线图Key码 | inte | — | 同名字段未找到 |
| `VHRD_HM_STPO_Key` | 订单Key码 | int | — | 同名字段未找到 |
| `VHRD_HM_Production_Time` | 衣架运行时间 | real | — | 同名字段未找到 |
| `VHRD_HM_Key` | 衣架Key码 | uniqueidentifier | — | 存在 |
| `VHRD_HRD_Comments` | 衣架运行备注信息 | nvarchar(50) | — | 存在 |
| `VHRD_CM_Short_Description` | 颜色简述 | nvarchar(10) | — | 存在 |
| `VHRD_CM_Description` | 颜色描述 | nvarchar(255) | — | 存在 |
| `VHRD_SM_Short_Description` | 尺码简述 | nvarchar(10) | — | 存在 |
| `VHRD_SM_Description` | 尺码描述 | nvarchar(255) | — | 存在 |
| `VHRD_HM_Current_Location_Type` | 衣架当前位置类型；P：在轨道上；S：在出站口；W：在站位里 | nvarchar(1) | — | 存在 |
| `VHRD_HM_Current_Location` | 衣架当前位置类型 | smallint | — | 存在 |
| `VHRD_ST_Line` | 款式所属生产线 | smallint | — | 同名字段未找到 |

#### V_ODP_Info（IDS 视图字段说明 2024-12-23）

文档对象标题：V_ODP_Info产量信息字段说明 注意：需要购买和安装《生产数据服务器同步软件》才可使用此视图
文档部署注记：注意：需要购买和安装《生产数据服务器同步软件》才可使用此视图
本地结构核对：当前 IDS 实例对象存在，共 42 列。以下含义是工作簿的原文口径，不代表已通过吊挂端行为或客户端配置实测。

| 字段 | 文档中文释义 | 文档类型 | 限制/备注 | 当前 IDS 同名字段核对 |
|---|---|---|---|---|
| `ODPI_Key` | 主键 | uniqueidentifier | — | 存在 |
| `ODPI_Date` | 生产日期 | datetime | — | 存在 |
| `ODPI_EM_Key` | 员工工号 | int | — | 存在 |
| `ODPI_EM_FirstName` | 员工姓 | nvarchar(50) | — | 存在 |
| `ODPI_EM_LastName` | 员工名 | nvarchar(50) | — | 存在 |
| `ODPI_LCD_Name` | LCD显示的名字 | nvarchar(15) | — | 同名字段未找到；当前字段为 `ODPI_EM_LCD_Name`，需确认命名兼容。 |
| `ODPI_Workstation` | 工作站号 | smallint | — | 存在 |
| `ODPI_Quantity` | 产量 | smallint | — | 存在 |
| `ODPI_ST_ID` | 款式编号 | nvarchar(25) | — | 存在 |
| `ODPI_ST_Description` | 款式描述 | nvarchar(50) | — | 存在 |
| `ODPI_Lot_Number` | 包号 | nvarchar(25) | — | 存在 |
| `ODPI_OC_Key` | 工序编号 | int | — | 存在 |
| `ODPI_OC_Description` | 工序描述 | nvarchar(255) | — | 存在 |
| `ODPI_CM_Short_Description` | 颜色简述 | nvarchar(10) | — | 存在 |
| `ODPI_SM_Short_Description` | 尺码简述 | nvarchar(10) | — | 存在 |
| `ODPI_Piece_Rate` | 工序计件单价 | real | — | 存在 |
| `ODPI_OC_Standard_Time` | 工序标准工时 | real | — | 存在 |
| `ODPI_Actual_Time` | 实际用时 | int | — | 存在 |
| `ODPI_STOP_Order` | 工序顺序号 | smallint | — | 存在 |
| `ODPI_PC_Description` | 工序分类名 | nvarchar(25) | — | 存在 |
| `ODPI_ST_Collection` | 款式品名 | nvarchar(25) | — | 存在 |
| `ODPI_CM_Description` | 颜色描述 | nvarchar(255) | — | 存在 |
| `ODPI_SM_Description` | 尺码描述 | nvarchar(255) | — | 存在 |
| `ODPI_OC_PA_Key` | 工序薪资类别Key码 | nvarchar(10) | — | 存在 |
| `ODPI_ODPD_PA_Key` | 工序薪资类别Key码 | nvarchar(10) | — | 存在 |
| `ODPI_PA_CurrencyValue` | 薪资 | real | — | 存在 |
| `ODPI_ODPD_Is_Overtime` | 是否加班 | bit | 0：否；1:是 | 存在 |
| `ODPI_MC_Type` | 机种类别 | nvarchar(25) | — | 存在 |
| `ODPI_STOP_Number` | 工序号码 | nvarchar(50) | — | 存在 |
| `ODPI_OC_Piece_Rate_Additional` | 工序补贴工价 | real | — | 存在 |
| `ODPI_ODP_Actual_Clock_In` | 员工实际登入时间 | datetime | — | 存在 |
| `ODPI_ODP_Actual_Clock_Out` | 员工实际登出时间 | datetime | — | 存在 |
| `ODPI_EM_City` | 员工所在城市 | nvarchar(25) | — | 存在 |
| `ODPI_ST_Fabric` | 款式布料 | nvarchar(25) | — | 存在 |
| `ODPI_EM_RFID` | 员工持有RFID卡号 | nvarchar(10) | — | 存在 |
| `ODPI_EM_Department` | 员工所在部门 | nvarchar(15) | — | 存在 |
| `ODPI_EM_SSN` | 员工所在组别 | nvarchar(15) | — | 存在 |
| `ODPI_Start_Time` | 开始时间 | datetime | — | 存在 |
| `ODPI_ODP_LineNumber` | 生产线号 | int | — | 存在 |
| `ODPI_STPO_ID` | 订单号 | nvarchar(50) | — | 存在 |
| `GroupId` | 组别 | int | — | 存在 |

#### V_Hanger_Routing_Details（IDS 视图字段说明 2024-12-23）

文档对象标题：V_Hanger_Routing_Details衣架记录视图（VHRD）      注意：需要购买和安装《生产数据服务器同步软件》才可使用此视图
文档部署注记：注意：需要购买和安装《生产数据服务器同步软件》才可使用此视图
本地结构核对：当前 IDS 实例对象存在，共 39 列。以下含义是工作簿的原文口径，不代表已通过吊挂端行为或客户端配置实测。

| 字段 | 文档中文释义 | 文档类型 | 限制/备注 | 当前 IDS 同名字段核对 |
|---|---|---|---|---|
| `VHRD_HRD_Key` | 主键 | uniqueidentifier | — | 存在 |
| `VHRD_HM_Lot_Number` | 包号 | nvarchar(25) | — | 存在 |
| `VHRD_HM_Item_Card` | 扎牌号码 | nvarchar(20) | — | 存在 |
| `VHRD_HM_Garments_Per_Hanger` | 衣架上裁件数 | smallint | — | 存在 |
| `VHRD_HRD_OC_Key` | 工序编号 | int | — | 存在 |
| `VHRD_HRD_EM_Key` | 员工工号 | int | — | 存在 |
| `VHRD_HRD_Line` | 生产线号 | smallint | — | 存在 |
| `VHRD_HRD_Workstation` | 工作站 | smallint | — | 存在 |
| `VHRD_HRD_ODP_Credited` | 是否记产量 | bit | 0：否；1:是 | 存在 |
| `VHRD_HRD_Completed_Time` | 完成时间 | datetime | — | 存在 |
| `VHRD_HRD_Started_Time` | 开始时间 | datetime | — | 存在 |
| `VHRD_ST_ID` | 款式编号 | nvarchar(25) | — | 存在 |
| `VHRD_ST_Description` | 款式描述 | nvarchar(50) | — | 存在 |
| `VHRD_ST_Collection` | 款式品名 | nvarchar(25) | — | 存在 |
| `VHRD_STPO_ID` | 订单号 | nvarchar(50) | — | 存在 |
| `VHRD_ST_Fabric` | 布料 | nvarchar(25) | — | 存在 |
| `VHRD_ST_Trim` | 裁床次 | nvarchar(25) | — | 存在 |
| `VHRD_OC_Description` | 工序描述 | nvarchar(255) | — | 存在 |
| `VHRD_OC_Standard_Time` | 工序标准工时 | real | — | 存在 |
| `VHRD_OC_Piece_Rate` | 工序计件工资 | real | — | 存在 |
| `VHRD_PC_Description` | 工序分类名 | nvarchar(25) | — | 存在 |
| `VHRD_EM_FirstName` | 员工姓 | nvarchar(50) | — | 存在 |
| `VHRD_EM_LastName` | 员工名 | nvarchar(50) | — | 存在 |
| `VHRD_EM_LCD_Name` | LCD显示员工姓名 | nvarchar(15) | — | 存在 |
| `VHRD_STOP_Number` | 工序号 | nvarchar(50) | — | 存在 |
| `VHRD_HM_ID` | 衣架ID | nvarchar(12) | — | 存在 |
| `VHRD_HM_Loaded_On_Line` | 衣架上裁线号 | smallint | — | 存在 |
| `VHRD_HM_Current_Line` | 衣架当前所在线号 | smallint | — | 存在 |
| `VHRD_HM_Key` | 衣架Key码 | uniqueidentifier | — | 存在 |
| `VHRD_HRD_Comments` | 衣架运行备注信息 | nvarchar(50) | — | 存在 |
| `VHRD_CM_Short_Description` | 颜色简述 | nvarchar(10) | — | 存在 |
| `VHRD_CM_Description` | 颜色描述 | nvarchar(255) | — | 存在 |
| `VHRD_SM_Short_Description` | 尺码简述 | nvarchar(10) | — | 存在 |
| `VHRD_SM_Description` | 尺码描述 | nvarchar(255) | — | 存在 |
| `VHRD_EM_SSN` | 员工SSN | nvarchar(15) | — | 存在 |

#### V_QC_Rework_Info（IDS 视图字段说明 2024-12-23）

文档对象标题：V_QC_Rework_Info 返工记录字段说明 注意：需要购买和安装《生产数据服务器同步软件》才可使用此视图
文档部署注记：注意：需要购买和安装《生产数据服务器同步软件》才可使用此视图
本地结构核对：当前 IDS 实例对象存在，共 42 列。以下含义是工作簿的原文口径，不代表已通过吊挂端行为或客户端配置实测。

| 字段 | 文档中文释义 | 文档类型 | 限制/备注 | 当前 IDS 同名字段核对 |
|---|---|---|---|---|
| `QCRI_Key` | 主键 | uniqueidentifier | — | 存在 |
| `QCRI_Defect_EM_Key` | 缺陷产生员工号 | int | — | 存在 |
| `QCRI_Defect_EM_FirstName` | 缺陷产生员工姓 | nvarchar(50) | — | 存在 |
| `QCRI_Defect_EM_LastName` | 缺陷产生员工名 | nvarchar(50) | — | 存在 |
| `QCRI_Defect_DateTime` | 缺陷产生时间 | datetime | — | 存在 |
| `QCRI_Defect_Quantity` | 缺陷数量 | smallint | — | 存在 |
| `QCRI_ST_ID` | 款式编号 | nvarchar(25) | — | 存在 |
| `QCRI_ST_Description` | 款式描述 | nvarchar(50) | — | 存在 |
| `QCRI_ST_Collection` | 款式品名 | nvarchar(25) | — | 存在 |
| `ODPI_STPO_ID` | 订单号 | nvarchar(50) | — | 文档列名疑似误写；当前字段为 `QCRI_STPO_ID`。 |
| `QCRI_OC_Key` | 工序编号 | int | — | 存在 |
| `QCRI_OC_Description` | 工序描述 | nvarchar(255) | — | 存在 |
| `QCRI_PC_Description` | 工序分类名 | nvarchar(25) | — | 存在 |
| `QCRI_STOP_Number` | 工序号码 | nvarchar(50) | — | 存在 |
| `QCRI_CM_Short_Description` | 颜色简述 | nvarchar(10) | — | 存在 |
| `QCRI_CM_Description` | 颜色描述 | nvarchar(255) | — | 存在 |
| `QCRI_SM_Short_Description` | 尺码简述 | nvarchar(10) | — | 存在 |
| `QCRI_SM_Description` | 尺码描述 | nvarchar(255) | — | 存在 |
| `QCRI_QCSC_Key` | 质检原因key码 | uniqueidentifier | — | 存在 |
| `QCRI_QCSC_Description` | 质检原因描述 | nvarchar(255) | — | 存在 |
| `QCRI_QCSC_Is_Rework` | 是否返修 | bit | 0：否；1：是 | 存在 |
| `QCRI_QC_EM_Key` | 质检员工工号 | int | — | 存在 |
| `QCRI_QC_EM_FirstName` | 质检员工姓 | nvarchar(50) | — | 存在 |
| `QCRI_QC_EM_LastName` | 质检员工名 | nvarchar(50) | — | 存在 |
| `QCRI_QC_Station` | 质检工作站号 | smallint | — | 存在 |
| `QCRI_QC_DateTime` | 质检时间 | datetime | — | 存在 |
| `QCRI_Repair_EM_Key` | 修复员工工号 | int | — | 存在 |
| `QCRI_Repair_EM_FirstName` | 修复员工姓 | nvarchar(50) | — | 存在 |
| `QCRI_Repair_EM_LastName` | 修复员工名 | nvarchar(50) | — | 存在 |
| `QCRI_Repair_DateTime` | 修复时间 | datetime | — | 存在 |
| `QCRI_Repair_Quantity` | 修复数量 | int | — | 存在 |
| `QCRI_QC_EM_Department` | 质检员工所属部门 | nvarchar(15) | — | 存在 |
| `QCRI_QC_EM_SSN` | 质检员工组别 | nvarchar(15) | — | 存在 |
| `QCRI_Defect_EM_Department` | 缺陷产生员工所属部门 | nvarchar(15) | — | 存在 |
| `QCRI_Defect_EM_SSN` | 缺陷产生员工组别 | nvarchar(15) | — | 存在 |
| `QCRI_Repair_EM_Department` | 修复员工所属部门 | nvarchar(15) | — | 存在 |
| `QCRI_Repair_EM_SSN` | 修复员工组别 | nvarchar(15) | — | 存在 |
| `QCRI_Defect_EM_LCD_Name` | 缺陷产生员工LCD名字 | nvarchar(15) | — | 存在 |
| `QCRI_QC_EM_LCD_Name` | 质检员工LCD名字 | nvarchar(15) | — | 存在 |
| `QCRI_Repair_EM_LCD_Name` | 修复员工LCD名字 | nvarchar(15) | — | 存在 |
| `QCRI_LineNumber` | 生产线号 | int | — | 存在 |
| `GroupId` | 组别 | int | — | 存在 |


### 3.5 字段字典中需要回查的释义/类型问题

| 对象/字段 | 资料里的问题 | 核查结论/下一步 |
|---|---|---|
| `VPD_Wages` | 7R 工作簿写成 `decimal(18.2)`，格式不像有效的 SQL Server `decimal(precision, scale)` 类型声明。 | 保留来源原文，不擅自修正为 `decimal(18,2)`；该对象在当前 IDS 中不存在，须向同步软件/厂商核对真实类型。 |
| `VHRD_HM_SRM_Key` | 7R 工作簿数据类型写为 `inte`。 | 疑似拼写错误，但目标类型须以厂商当前视图定义为准；当前视图没有此投影列。 |
| `VHRD_HM_Current_Location` | 7R 工作簿中文释义为“衣架当前位置类型”，与相邻的 `VHRD_HM_Current_Location_Type` 重复。 | 字段类型是 `smallint`，可能表示位置编号，但不能仅凭类型定论；需核对客户端/厂商定义及实际站位值映射。 |
| `TISO_Line` | 当前 IDS 有该字段，7R 工作簿没有；数据库注释为“线内/线外工序识别”，而 `TISO_Op_IsOnline` 已由 7R 定义为线上/线外标志。 | 可能为历史兼容字段或不同含义；不能假设两列等价，应核对客户端读取哪个字段、冲突时如何处理。 |
| `VHRD_Return_For_Repair`、`VHRD_STOP_Order`、`ODPI_ODP_Shift` | 当前视图存在，但两份字段字典未解释。 | 需从当前客户端界面/数据契约或供应商定义确认含义，不能按名称直接作为尾查合格、工序状态或班次口径。 |
| `QCRI_QC_EM_Key` | IDS 视图字段名意为质检员工，但 SQL 表达式来源为 `QCR_Sent_To_Rework_By_EM_Key`。 | 当前映射已记录；必须确认“送返修人”是否就是质检员，避免错认责任角色。 |

7R 工作簿对 `V_ProductData`、`V_ReworkData`、`V_ScrapData` 和 `V_Hanger_Routing_Details` 的对象标题均附注：需购买并安装“生产数据服务器同步软件”后才可使用。用户已确认现场通过独立客户端工具上传同步；因此当前应先核对该客户端是否安装/启用对应数据对象、上传目标和映射配置。文档注记说明依赖条件，但不单独证明当前客户端具体缺少哪一个开关。

## 4. 实际视图、SQL 依赖和记录口径

### V_Hanger_Routing_Details

- 以 `P_Hanger_Routing_Details` 为事件主表，内连接 `P_Hanger_Master`（衣架 ID + GroupId），再左连接计划、款式、款式工序、工序、员工、颜色、尺码和产线。
- 无聚合、无 WHERE。主记录原本应是一条衣架路线事件；对应主衣架数据缺失时，内连接会使路线事件不出现在视图中。
- 但底表有 nullable 的 `HRD_HM_Key`，当前视图没有使用它，而是用 `HRD_HM_ID = HM_ID AND GroupId` 连接；视图也没有过滤 `P_Hanger_Master.DelFlag`。同一 GroupId 下 HM_ID 有历史重复，存在把一条路线事件连到多条衣架主记录、重复放大或带出旧 PO/款色码的明显风险，见下方数据级检查。
- 直接引用：`B_Colour_Master`、`B_Employee_Master`、`B_Line_Information`、`B_Operation_Codes`、`B_Primary_Codes`、`B_Size_Master`、`B_Style_Master`、`B_Style_Operations`、`B_Style_Operations_Master`、`P_Hanger_Master`、`P_Hanger_Routing_Details`、`P_Style_Planned_Orders`。

### V_ODP_Info

- 以 `P_ODP_Detail` 为主表，左连接 ODP 主单、员工、款式、工序、工资类别、颜色、尺码和计划；过滤 `P_ODP_Detail.DelFlag=0`。
- ODP_Key 与 LineId 用于关联主单。视图保留一条报工明细，没有 GROUP BY。
- 直接引用：`B_Colour_Master`、`B_Employee_Master`、`B_Machine_Codes`、`B_Operation_Codes`、`B_Pay_Categories`、`B_Primary_Codes`、`B_Size_Master`、`B_Style_Master`、`B_Style_Operations`、`P_ODP_Detail`、`P_ODP_Master`、`P_Style_Planned_Orders`。
- 视图的 `ODPI_Piece_Rate` 来自工序基础资料当前单价，`ODPI_PA_CurrencyValue` 来自薪资类别；底表虽有明细单价字段，视图没有投影。因此视图中的当前工价不能当作历史报工时的工资快照。

### V_QC_Rework_Info

- 以 `P_QC_Rework` 为缺陷/返修事实表，通过外连接关联款式、计划、工序、颜色尺码、质量原因和多角色员工；无聚合、无 WHERE。
- 直接引用：`B_Colour_Master`、`B_Employee_Master`、`B_Operation_Codes`、`B_Primary_Codes`、`B_QC_Sub_Codes`、`B_Size_Master`、`B_Style_Master`、`B_Style_Operations`、`P_QC_Rework`、`P_Style_Planned_Orders`。
- 视图底层有衣架 ID/Key 字段，但视图未提供；没有通用尾查通过字段。没有 QCRI 记录不表示检验合格。
- `QCRI_QC_EM_Key` 实际来自 `QCR_Sent_To_Rework_By_EM_Key`，字段名字面上的“质检员工”与底层来源“送返修人”不完全一致，须向厂商确认人员角色。

### 三个核心回传视图的字段来源拆解（当前 IDS SQL）

本节来源是 2026-11-25 对当前 IDS 执行只读 `OBJECT_DEFINITION` 得到的三个视图定义；这里只记录 SELECT 和 JOIN 中可直接证实的来源，不根据字段名补写业务规则。字段类型、完整字段清单及工作簿原文释义仍见第 3 节。

#### `V_ODP_Info`：报工明细与主单、字典的拼接

| 字段组 | 代表字段 | 实际取值来源与匹配关系 | 需要保留的边界 |
|---|---|---|---|
| 报工事实 | `ODPI_Key`、`ODPI_Quantity`、`ODPI_Lot_Number`、`ODPI_Actual_Time`、`ODPI_Start_Time`、`ODPI_ODPD_Is_Overtime`、`ODPI_ODPD_PA_Key` | `P_ODP_Detail`；事件键取 `ODPD_Key`，数量、包号、实际时间等来自明细行 | 无聚合；视图保留明细事实。底表复合主键包含 `ODPD_Key`、`ODPD_ODP_Key`、`LineId`，视图没有完整投影这组键；在确认单列唯一性前，不要只凭 `ODPI_Key` 认定跨线全局去重契约。 |
| 主单与人员 | `ODPI_Date`、`ODPI_EM_Key`、员工姓名/LCD/RFID/部门/组别、上下班打卡时间、班次、`ODPI_ODP_LineNumber` | `P_ODP_Master` 通过明细 `ODPD_ODP_Key + LineId` 左连接；员工资料再按主单员工键和 `GroupId` 连接 `B_Employee_Master` | 视图员工是报工主单的员工，不是明细上独立保存的员工；员工描述和组织字段来自当前员工主档。主单缺失时明细仍可保留，但主单/员工字段为空。 |
| 款式与款式工序 | 款号、款名、款式品名、布料、`ODPI_ST_Key`、工序编码/说明、工序序号/编号 | 款式来自明细 `ODPD_ST_Key + GroupId` 连接 `B_Style_Master`；工序来自 `ODPD_OC_Key + GroupId` 连接 `B_Operation_Codes`；工序序号/编号再按款式的 `STOPM_Key`、工序编码、`GroupId` 及 `DelFlag=0` 连接 `B_Style_Operations` | 工序路线连接没有使用 `STOP_Key` 这样的节点唯一键，只按款式路线和工序编码配对；若同款同工序编码有多个有效节点，视图可能把一条明细展开成多行。 |
| 颜色和尺码 | `ODPI_CM_*`、`ODPI_SM_*` | `P_ODP_Detail.ODPD_CM_Key/ODPD_SM_Key + GroupId` 左连接颜色/尺码字典 | 这是字典补充的名称，不是明细中被冻结的名称快照。 |
| 计价与设备 | `ODPI_Piece_Rate`、`ODPI_OC_Standard_Time`、`ODPI_OC_Piece_Rate_Additional`、`ODPI_OC_PA_Key` 来自 `B_Operation_Codes`；`ODPI_ODPD_PA_Key` 来自明细；`ODPI_PA_CurrencyValue` 来自 `B_Pay_Categories`，按明细的薪资类别键匹配；机种来自 `B_Machine_Codes` | 工序与薪资类别分别使用 `GroupId + Key` 左连接；机种通过工序分类码的 `PC_MC_Key` 连接 | 视图中的计件工价/标准时间来自当前工序字典，不是底层 ODP 明细保存的历史单价快照。工序默认薪资类别键与本次明细采用的薪资类别键是两个字段，不能混用。 |
| 计划号 | `ODPI_STPO_ID` | `P_ODP_Detail.ODPD_STPO_KEY + GroupId` 左连接 `P_Style_Planned_Orders` 后取 `STPO_ID` | 这是衣拿计划对象的文本 ID。它是否等于客户 PO，取决于 ERP 下发时怎么赋值，不能从视图自身推出。 |
| 查询范围 | — | 仅 `WHERE P_ODP_Detail.DelFlag = 0`；没有 `GROUP BY` | 这是有效明细筛选，不代表主单、员工字典、路线或其他关联表也按同一种删除状态过滤。 |

#### `V_Hanger_Routing_Details`：衣架路线事件与衣架当前/款色码信息的拼接

| 字段组 | 代表字段 | 实际取值来源与匹配关系 | 需要保留的边界 |
|---|---|---|---|
| 路线事件 | `VHRD_HRD_Key`、工序 Key、员工 Key、事件线号、工作站、记产量标志、完成/开始时间、返修标志、备注 | `P_Hanger_Routing_Details`；每条底表路线明细为设计上的事件粒度 | 无 `GROUP BY`、没有 `WHERE`。事件线号来自 `HRD_Line`；`HRD_ODP_Credited` 是记产量标志，不是质检合格标志。 |
| 衣架主档 | 衣架 ID/Key、包号、扎牌、每架裁件数、当前/上架线、当前位置、款式/颜色/尺码/计划关联 | 通过 `HRD_HM_ID = P_Hanger_Master.HM_ID` 且 `GroupId` 相同作 INNER JOIN；对应字段来自 `P_Hanger_Master` | 连接没有使用底表 `HRD_HM_Key`，而是使用可复用的衣架号码；没有过滤 `P_Hanger_Master.DelFlag`。`HM_Key` 是 JOIN 命中的主档键，并非这条事件自身投影出的事件期键。一个衣架的包号、扎牌及每架件数会随着每条路线事件重复出现，不能按 VHRD 行直接累加每架件数。 |
| PO/计划与款式 | `VHRD_STPO_ID`、款式编号/名称、品名、布料、裁床次 | 衣拿计划号按衣架主档 `HM_STPO_Key + GroupId` 连接 `P_Style_Planned_Orders`；款式按主档 `HM_ST_Key + GroupId` 连接 `B_Style_Master` | PO/计划归属是衣架主档当前携带的计划关系；需确认衣架重用或更新时历史事件关联是否仍代表事件发生时的计划。 |
| 工序路线描述 | 工序描述、计件单价、标准时间、工序分类、款式工序编号/顺序 | `HRD_OC_Key + GroupId` 连接 `B_Operation_Codes`；再由工序分类连 `B_Primary_Codes`；路线主键关系为款式 `ST_STOPM_Key` 对到路线主表 `STOPM_Key`，路线明细以 `STOP_STOPM_Key + HRD_OC_Key + GroupId + DelFlag=0` 左连接 | SQL 未按路线节点 Key/顺序连接；当前 IDS 有效路线的 `(GroupId, STOP_STOPM_Key, STOP_OC_Key)` 聚合重复组数为 0，所以没有观察到这类当前数据扩行；数据库未设复合唯一约束，未来配置仍需防重复。输出单价和标准时间来自当前字典，不是事件时刻工资快照。 |
| 员工、颜色尺码、线体 | 员工姓名/SSN/LCD、颜色/尺码描述、产线描述 | 员工用 `HRD_EM_Key + GroupId`；颜色/尺码用衣架主档 Key + GroupId；线体说明按事件 `HRD_Line = B_Line_Information.LineId` | 员工与描述字段来自现有字典。视图同时含“事件发生线”与“衣架当前线/上架线”，三个字段表示不同关系。 |

#### `V_QC_Rework_Info`：质量/返修事实与原因、员工、计划资料的拼接

| 字段组 | 代表字段 | 实际取值来源与匹配关系 | 需要保留的边界 |
|---|---|---|---|
| 质量事件 | `QCRI_Key`、缺陷员工 Key、缺陷时间/数量、缺陷工序/款式/颜色/尺码 Key、送返修时间/站位、修复员工/时间/数量 | `P_QC_Rework`；`QCRI_Key` 对应 `QCR_Key` | 无 `GROUP BY`、没有 `WHERE`。表主键为 `QCR_Key + LineId`，视图没有投影源表 `LineId`；`QCRI_LineNumber` 不是该源记录 LineId，见下行。 |
| 质量原因 | 原因 Key、描述、是否返修、输出 `GroupId` | `QCR_QCSC_Key + GroupId` 左连接 `B_QC_Sub_Codes`；SELECT 的 `GroupId` 取自 `B_QC_Sub_Codes.GroupId`，而不是 `P_QC_Rework.GroupId` | 如果原因字典未匹配，视图投影的 GroupId 会为空，即使事实表本身有 GroupId；ERP 不宜只用该输出字段做租户/组织范围判断。 |
| 款式、工序与计划 | 款式/工序/颜色/尺码信息、原因涉及工序的款式序号、`QCRI_STPO_ID` | 款式和工序通过 `QCR_Defect_ST_Key/QCR_Defect_OC_Key + GroupId` 关联字典和有效路线；计划通过 `QCR_STPO_Key + GroupId` 连接 `P_Style_Planned_Orders` 后取 `STPO_ID` | 路线节点仍按款式 + 工序编码关联，重复节点有展开风险。`STPO_ID` 是计划 ID 文本，不自动等于客户 PO。 |
| 人员角色 | 缺陷员工字段来自 `QCR_Defect_EM_Key`；视图命名为质检员工的一组字段来自 `QCR_Sent_To_Rework_By_EM_Key`；修复员工字段来自 `QCR_Repair_EM_Key` | 三种人员分别连接员工字典，匹配时用 `GroupId + Employee Key` | `QCRI_QC_*` 名称与底层“送返修人”来源不一致；在确认前，不要据此认定为实际检验操作员。姓名/部门/组别是人员字典当前值。 |
| 产线号 | `QCRI_LineNumber` | SQL 表达式取 `B_Style_Master.ST_Line` | 这是款式主档的生产线字段，不是 `P_QC_Rework.LineId` 的事件线。不能用于准确标注质量事件实际发生在哪条线。 |
| 单件衣架关联 | — | `P_QC_Rework` 底表存在 `QCR_HM_ID/QCR_HM_Key`，当前视图 SELECT 未输出 | 当前 QCRI 不能把一条质量事件直接绑定到一件成衣/一个衣架；没有记录也不表示检验通过。 |

#### 三视图共同的统计与关联限制

- 三个视图均为事件/明细视图而非日汇总视图；VHRD 按路线事件记录，ODPI 按报工明细，QCRI 按质量事件。统计数量应分别使用各自事实字段，并先确认事件是否重复、撤销或重报。
- ODPI 与 QCRI 的款式工序字段来自字典/路线联接，未使用路线节点唯一键。数据库仅有 `STOP_Key` 单列主键，没有约束 `(GroupId, STOP_STOPM_Key, STOP_OC_Key)` 唯一；对当前 `DelFlag=0` 路线行按该组合做只输出计数的核对，重复组数为 0，因此本次未观察到这类当前数据扩行，但约束缺失意味着未来维护仍有重复配置风险。
- 只有 VHRD 带有衣架主档 ID/Key，但其衣架连接使用可复用 `HM_ID + GroupId`，且没有筛主档 `DelFlag`；ODPI 和 QCRI 没有通用事件级衣架关联键。三个视图不能仅凭同一个 PO、款色码和相近时间拼成可靠的单件生产指纹。
- 源表复合主键没有完整投影到所有视图：ODPI 未显式输出明细行的全部源复合键，QCRI 未输出事实表 LineId。ERP 接口若要幂等同步，应先确认事件键在实际数据中的唯一范围及删除/更新语义，不要自行假定单列 Key 全局唯一。
- 这些视图混合事实表字段与当前字典字段。姓名、组织、工序名称、标准工时、计件单价等均可能由当前字典联接得出，不应直接视为事件发生时的历史快照。

### 三个核心视图的全部输出列与 SQL 来源表达式

下表按 2026-11-25 当前 IDS 实例中 `OBJECT_DEFINITION` 的 SELECT 顺序逐项转录。来源表达式是视图 SELECT 的直接表达式；JOIN 键和业务边界见本节前面的分视图说明。未经确认的字段含义仍按字段字典的证据边界处理。

| 视图 | 输出字段 | SELECT 来源表达式 |
|---|---|---|
| `V_ODP_Info` | `ODPI_Key` | `dbo.P_ODP_Detail.ODPD_Key` |
| `V_ODP_Info` | `ODPI_Date` | `dbo.P_ODP_Master.ODP_Date` |
| `V_ODP_Info` | `ODPI_EM_Key` | `dbo.P_ODP_Master.ODP_EM_Key` |
| `V_ODP_Info` | `ODPI_EM_FirstName` | `dbo.B_Employee_Master.EM_FirstName` |
| `V_ODP_Info` | `ODPI_EM_LastName` | `dbo.B_Employee_Master.EM_LastName` |
| `V_ODP_Info` | `ODPI_EM_LCD_Name` | `dbo.B_Employee_Master.EM_LCD_Name` |
| `V_ODP_Info` | `ODPI_Workstation` | `dbo.P_ODP_Detail.ODPD_Workstation` |
| `V_ODP_Info` | `ODPI_Quantity` | `dbo.P_ODP_Detail.ODPD_Quantity` |
| `V_ODP_Info` | `ODPI_ST_ID` | `dbo.B_Style_Master.ST_ID` |
| `V_ODP_Info` | `ODPI_ST_Description` | `dbo.B_Style_Master.ST_Description` |
| `V_ODP_Info` | `ODPI_Lot_Number` | `dbo.P_ODP_Detail.ODPD_Lot_Number` |
| `V_ODP_Info` | `ODPI_OC_Key` | `dbo.P_ODP_Detail.ODPD_OC_Key` |
| `V_ODP_Info` | `ODPI_OC_Description` | `dbo.B_Operation_Codes.OC_Description` |
| `V_ODP_Info` | `ODPI_CM_Short_Description` | `dbo.B_Colour_Master.CM_Short_Description` |
| `V_ODP_Info` | `ODPI_SM_Short_Description` | `dbo.B_Size_Master.SM_Short_Description` |
| `V_ODP_Info` | `ODPI_Piece_Rate` | `dbo.B_Operation_Codes.OC_Piece_Rate` |
| `V_ODP_Info` | `ODPI_OC_Standard_Time` | `dbo.B_Operation_Codes.OC_Standard_Time` |
| `V_ODP_Info` | `ODPI_Actual_Time` | `dbo.P_ODP_Detail.ODPD_Actual_Time` |
| `V_ODP_Info` | `ODPI_STOP_Order` | `dbo.B_Style_Operations.STOP_Order` |
| `V_ODP_Info` | `ODPI_PC_Description` | `dbo.B_Primary_Codes.PC_Description` |
| `V_ODP_Info` | `ODPI_ST_Collection` | `dbo.B_Style_Master.ST_Collection` |
| `V_ODP_Info` | `ODPI_CM_Description` | `dbo.B_Colour_Master.CM_Description` |
| `V_ODP_Info` | `ODPI_SM_Description` | `dbo.B_Size_Master.SM_Description` |
| `V_ODP_Info` | `ODPI_OC_PA_Key` | `dbo.B_Operation_Codes.OC_PA_Key` |
| `V_ODP_Info` | `ODPI_ODPD_PA_Key` | `dbo.P_ODP_Detail.ODPD_PA_Key` |
| `V_ODP_Info` | `ODPI_PA_CurrencyValue` | `dbo.B_Pay_Categories.PA_CurrencyValue` |
| `V_ODP_Info` | `ODPI_ODPD_Is_Overtime` | `dbo.P_ODP_Detail.ODPD_Is_Overtime` |
| `V_ODP_Info` | `ODPI_MC_Type` | `dbo.B_Machine_Codes.MC_Type` |
| `V_ODP_Info` | `ODPI_STOP_Number` | `dbo.B_Style_Operations.STOP_Number` |
| `V_ODP_Info` | `ODPI_OC_Piece_Rate_Additional` | `dbo.B_Operation_Codes.OC_Piece_Rate_Additional` |
| `V_ODP_Info` | `ODPI_ODP_Actual_Clock_In` | `dbo.P_ODP_Master.ODP_Actual_Clock_In` |
| `V_ODP_Info` | `ODPI_ODP_Actual_Clock_Out` | `dbo.P_ODP_Master.ODP_Actual_Clock_Out` |
| `V_ODP_Info` | `ODPI_EM_City` | `dbo.B_Employee_Master.EM_City` |
| `V_ODP_Info` | `ODPI_ST_Fabric` | `dbo.B_Style_Master.ST_Fabric` |
| `V_ODP_Info` | `ODPI_EM_RFID` | `dbo.B_Employee_Master.EM_RFID` |
| `V_ODP_Info` | `ODPI_EM_Department` | `dbo.B_Employee_Master.EM_Department` |
| `V_ODP_Info` | `ODPI_EM_SSN` | `dbo.B_Employee_Master.EM_SSN` |
| `V_ODP_Info` | `ODPI_Start_Time` | `dbo.P_ODP_Detail.ODPD_Start_Time` |
| `V_ODP_Info` | `ODPI_ODP_LineNumber` | `dbo.P_ODP_Master.LineId` |
| `V_ODP_Info` | `ODPI_ODP_Shift` | `dbo.P_ODP_Master.ODP_Shift` |
| `V_ODP_Info` | `ODPI_STPO_ID` | `dbo.P_Style_Planned_Orders.STPO_ID` |
| `V_ODP_Info` | `GroupId` | `dbo.P_ODP_Detail.GroupId` |
| `V_Hanger_Routing_Details` | `VHRD_HRD_Key` | `dbo.P_Hanger_Routing_Details.HRD_Key` |
| `V_Hanger_Routing_Details` | `VHRD_HM_Lot_Number` | `dbo.P_Hanger_Master.HM_Lot_Number` |
| `V_Hanger_Routing_Details` | `VHRD_HM_Item_Card` | `dbo.P_Hanger_Master.HM_Item_Card` |
| `V_Hanger_Routing_Details` | `VHRD_HM_Garments_Per_Hanger` | `dbo.P_Hanger_Master.HM_Garments_Per_Hanger` |
| `V_Hanger_Routing_Details` | `VHRD_HRD_OC_Key` | `dbo.P_Hanger_Routing_Details.HRD_OC_Key` |
| `V_Hanger_Routing_Details` | `VHRD_HRD_EM_Key` | `dbo.P_Hanger_Routing_Details.HRD_EM_Key` |
| `V_Hanger_Routing_Details` | `VHRD_HRD_Line` | `dbo.P_Hanger_Routing_Details.HRD_Line` |
| `V_Hanger_Routing_Details` | `VHRD_HRD_Workstation` | `dbo.P_Hanger_Routing_Details.HRD_Workstation` |
| `V_Hanger_Routing_Details` | `VHRD_HRD_ODP_Credited` | `dbo.P_Hanger_Routing_Details.HRD_ODP_Credited` |
| `V_Hanger_Routing_Details` | `VHRD_HRD_Completed_Time` | `dbo.P_Hanger_Routing_Details.HRD_Completed_Time` |
| `V_Hanger_Routing_Details` | `VHRD_Return_For_Repair` | `dbo.P_Hanger_Routing_Details.HRD_Return_For_Repair` |
| `V_Hanger_Routing_Details` | `VHRD_HRD_Started_Time` | `dbo.P_Hanger_Routing_Details.HRD_Started_Time` |
| `V_Hanger_Routing_Details` | `VHRD_ST_ID` | `dbo.B_Style_Master.ST_ID` |
| `V_Hanger_Routing_Details` | `VHRD_ST_Description` | `dbo.B_Style_Master.ST_Description` |
| `V_Hanger_Routing_Details` | `VHRD_ST_Collection` | `dbo.B_Style_Master.ST_Collection` |
| `V_Hanger_Routing_Details` | `VHRD_STPO_ID` | `dbo.P_Style_Planned_Orders.STPO_ID` |
| `V_Hanger_Routing_Details` | `VHRD_ST_Fabric` | `dbo.B_Style_Master.ST_Fabric` |
| `V_Hanger_Routing_Details` | `VHRD_ST_Trim` | `dbo.B_Style_Master.ST_Trim` |
| `V_Hanger_Routing_Details` | `VHRD_OC_Description` | `dbo.B_Operation_Codes.OC_Description` |
| `V_Hanger_Routing_Details` | `VHRD_OC_Standard_Time` | `dbo.B_Operation_Codes.OC_Standard_Time` |
| `V_Hanger_Routing_Details` | `VHRD_OC_Piece_Rate` | `dbo.B_Operation_Codes.OC_Piece_Rate` |
| `V_Hanger_Routing_Details` | `VHRD_PC_Description` | `dbo.B_Primary_Codes.PC_Description` |
| `V_Hanger_Routing_Details` | `VHRD_EM_FirstName` | `dbo.B_Employee_Master.EM_FirstName` |
| `V_Hanger_Routing_Details` | `VHRD_EM_LastName` | `dbo.B_Employee_Master.EM_LastName` |
| `V_Hanger_Routing_Details` | `VHRD_EM_LCD_Name` | `dbo.B_Employee_Master.EM_LCD_Name` |
| `V_Hanger_Routing_Details` | `VHRD_STOP_Number` | `dbo.B_Style_Operations.STOP_Number` |
| `V_Hanger_Routing_Details` | `VHRD_HM_ID` | `dbo.P_Hanger_Master.HM_ID` |
| `V_Hanger_Routing_Details` | `VHRD_HM_Loaded_On_Line` | `dbo.P_Hanger_Master.HM_Loaded_On_Line` |
| `V_Hanger_Routing_Details` | `VHRD_HM_Current_Line` | `dbo.P_Hanger_Master.HM_Current_Line` |
| `V_Hanger_Routing_Details` | `VHRD_HRD_Comments` | `dbo.P_Hanger_Routing_Details.HRD_Comments` |
| `V_Hanger_Routing_Details` | `VHRD_CM_Short_Description` | `dbo.B_Colour_Master.CM_Short_Description` |
| `V_Hanger_Routing_Details` | `VHRD_CM_Description` | `dbo.B_Colour_Master.CM_Description` |
| `V_Hanger_Routing_Details` | `VHRD_SM_Short_Description` | `dbo.B_Size_Master.SM_Short_Description` |
| `V_Hanger_Routing_Details` | `VHRD_SM_Description` | `dbo.B_Size_Master.SM_Description` |
| `V_Hanger_Routing_Details` | `VHRD_HM_Current_Location_Type` | `dbo.P_Hanger_Master.HM_Current_Location_Type` |
| `V_Hanger_Routing_Details` | `VHRD_HM_Current_Location` | `dbo.P_Hanger_Master.HM_Current_Location` |
| `V_Hanger_Routing_Details` | `VHRD_STOP_Order` | `dbo.B_Style_Operations.STOP_Order` |
| `V_Hanger_Routing_Details` | `VHRD_HM_Key` | `dbo.P_Hanger_Master.HM_Key` |
| `V_Hanger_Routing_Details` | `VHRD_EM_SSN` | `dbo.B_Employee_Master.EM_SSN` |
| `V_QC_Rework_Info` | `QCRI_Key` | `dbo.P_QC_Rework.QCR_Key` |
| `V_QC_Rework_Info` | `QCRI_Defect_EM_Key` | `dbo.P_QC_Rework.QCR_Defect_EM_Key` |
| `V_QC_Rework_Info` | `QCRI_Defect_EM_FirstName` | `EM_Master_Defect.EM_FirstName` |
| `V_QC_Rework_Info` | `QCRI_Defect_EM_LastName` | `EM_Master_Defect.EM_LastName` |
| `V_QC_Rework_Info` | `QCRI_Defect_DateTime` | `dbo.P_QC_Rework.QCR_Defect_DateTime` |
| `V_QC_Rework_Info` | `QCRI_Defect_Quantity` | `dbo.P_QC_Rework.QCR_Defect_Quantity` |
| `V_QC_Rework_Info` | `QCRI_ST_ID` | `dbo.B_Style_Master.ST_ID` |
| `V_QC_Rework_Info` | `QCRI_ST_Description` | `dbo.B_Style_Master.ST_Description` |
| `V_QC_Rework_Info` | `QCRI_ST_Collection` | `dbo.B_Style_Master.ST_Collection` |
| `V_QC_Rework_Info` | `QCRI_STPO_ID` | `dbo.P_Style_Planned_Orders.STPO_ID` |
| `V_QC_Rework_Info` | `QCRI_OC_Key` | `dbo.P_QC_Rework.QCR_Defect_OC_Key` |
| `V_QC_Rework_Info` | `QCRI_OC_Description` | `dbo.B_Operation_Codes.OC_Description` |
| `V_QC_Rework_Info` | `QCRI_PC_Description` | `dbo.B_Primary_Codes.PC_Description` |
| `V_QC_Rework_Info` | `QCRI_STOP_Number` | `dbo.B_Style_Operations.STOP_Number` |
| `V_QC_Rework_Info` | `QCRI_CM_Short_Description` | `dbo.B_Colour_Master.CM_Short_Description` |
| `V_QC_Rework_Info` | `QCRI_CM_Description` | `dbo.B_Colour_Master.CM_Description` |
| `V_QC_Rework_Info` | `QCRI_SM_Short_Description` | `dbo.B_Size_Master.SM_Short_Description` |
| `V_QC_Rework_Info` | `QCRI_SM_Description` | `dbo.B_Size_Master.SM_Description` |
| `V_QC_Rework_Info` | `QCRI_QCSC_Key` | `dbo.P_QC_Rework.QCR_QCSC_Key` |
| `V_QC_Rework_Info` | `QCRI_QCSC_Description` | `dbo.B_QC_Sub_Codes.QCSC_Description` |
| `V_QC_Rework_Info` | `QCRI_QCSC_Is_Rework` | `dbo.B_QC_Sub_Codes.QCSC_Is_Rework` |
| `V_QC_Rework_Info` | `QCRI_QC_EM_Key` | `dbo.P_QC_Rework.QCR_Sent_To_Rework_By_EM_Key` |
| `V_QC_Rework_Info` | `QCRI_QC_EM_FirstName` | `EM_Master_QC.EM_FirstName` |
| `V_QC_Rework_Info` | `QCRI_QC_EM_LastName` | `EM_Master_QC.EM_LastName` |
| `V_QC_Rework_Info` | `QCRI_QC_Station` | `dbo.P_QC_Rework.QCR_From_QC_Station` |
| `V_QC_Rework_Info` | `QCRI_QC_DateTime` | `dbo.P_QC_Rework.QCR_QC_DateTime` |
| `V_QC_Rework_Info` | `QCRI_Repair_EM_Key` | `dbo.P_QC_Rework.QCR_Repair_EM_Key` |
| `V_QC_Rework_Info` | `QCRI_Repair_EM_FirstName` | `EM_Master_Rework.EM_FirstName` |
| `V_QC_Rework_Info` | `QCRI_Repair_EM_LastName` | `EM_Master_Rework.EM_LastName` |
| `V_QC_Rework_Info` | `QCRI_Repair_DateTime` | `dbo.P_QC_Rework.QCR_Repair_DateTime` |
| `V_QC_Rework_Info` | `QCRI_Repair_Quantity` | `dbo.P_QC_Rework.QCR_Repair_Quantity` |
| `V_QC_Rework_Info` | `QCRI_QC_EM_Department` | `EM_Master_QC.EM_Department` |
| `V_QC_Rework_Info` | `QCRI_QC_EM_SSN` | `EM_Master_QC.EM_SSN` |
| `V_QC_Rework_Info` | `QCRI_Defect_EM_Department` | `EM_Master_Defect.EM_Department` |
| `V_QC_Rework_Info` | `QCRI_Defect_EM_SSN` | `EM_Master_Defect.EM_SSN` |
| `V_QC_Rework_Info` | `QCRI_Repair_EM_Department` | `EM_Master_Rework.EM_Department` |
| `V_QC_Rework_Info` | `QCRI_Repair_EM_SSN` | `EM_Master_Rework.EM_SSN` |
| `V_QC_Rework_Info` | `QCRI_Defect_EM_LCD_Name` | `EM_Master_Defect.EM_LCD_Name` |
| `V_QC_Rework_Info` | `QCRI_QC_EM_LCD_Name` | `EM_Master_QC.EM_LCD_Name` |
| `V_QC_Rework_Info` | `QCRI_Repair_EM_LCD_Name` | `EM_Master_Rework.EM_LCD_Name` |
| `V_QC_Rework_Info` | `QCRI_LineNumber` | `dbo.B_Style_Master.ST_Line` |
| `V_QC_Rework_Info` | `GroupId` | `dbo.B_QC_Sub_Codes.GroupId` |

### 外键与逻辑关联

- 整个数据库没有外键约束。视图 JOIN 不是数据库级完整性约束；多处业务关系依赖 GroupId 与本地 Key 一起匹配。
- 对 ODPI/VHRD/QCRI 使用的颜色、尺码、员工、款式、工序、计划等查找键做了聚合重复组合检查；除衣架主表外，本次检查的 GroupId + 业务 Key 组合没有发现重复组。该检查只证明查询时这些查找表的当前数据没有重复，不替代数据库唯一约束。
- 衣架主表的 HM_ID + GroupId 在查询时有 11,562 个重复组合、额外 210,573 行；其中 DelFlag=1 的重复组合有 11,235 组、额外 202,809 行，另有 7,764 组 HM_ID 同时存在多个 DelFlag 状态。当前 P_Hanger_Master 的 DelFlag=0 为 8,033 行、DelFlag=1 为 214,762 行。按字段名推测 DelFlag 是删除标志，但 IDS 没有注释说明其 0/1 口径，需厂商确认。VHRD 当前 JOIN 未按 DelFlag 过滤，且没有使用明细中的 HRD_HM_Key。
- 这表明 HM_ID 是可复用的物理衣架号，不能作为历史路线事件的唯一关联键。建议先向厂商确认 HRD_HM_Key 的有效覆盖率和业务含义，再验证是否应使用 HRD_HM_Key = HM_Key 关联衣架记录；不要直接修改厂商视图。
- 本轮没有全量扫描并比较 VHRD 与底层 HRD 的结果行数，故实际被放大的事件数尚未量化；上面是由当前视图 JOIN、无 DelFlag 过滤和已观察到的重复主表键共同确认的风险。
- 唯一检查约束为 `CK_ToIna_Employee`：`TIE_ID >= 0 AND TIE_ID <= 999999`。
- 用户对象扫描未发现存储过程、函数、触发器或同义词。视图未加密、未绑定 schema，三个视图都没有索引。

## 5. 索引和同步风险

- 64 个索引中，61 个为主键索引；另有 3 个辅助非聚集索引都在 `P_Hanger_Master`，列为 `HM_ID`、`HM_Current_Line`、`DelFlag`。60 个索引为聚集，`ToIna_Style_Planned_Orders` 的主键索引为非聚集。
- 61 个唯一索引均为主键索引；没有独立 UNIQUE 约束、过滤索引或禁用索引。
- 62 张表中，`ToIna_Size` 是唯一未发现任何索引或主键的表；重复尺码数据不受数据库唯一约束保护。
- `P_Hanger_Routing_Details` 估算约 2,976,545 行，只有 `HRD_Key` 聚集主键；没有 `HRD_HM_ID/GroupId`、完成时间或开始时间索引。VHRD 是明细视图，周期性全量扫描有显著性能风险。
- `P_ODP_Detail` 估算约 221,665 行，聚集主键键列为 `ODPD_Key`、`ODPD_ODP_Key`、`LineId`；视图主单关联使用后两列，它们不是该复合索引首列。
- `P_QC_Rework` 估算约 170,329 行，只有 `QCR_Key`、`LineId` 复合主键索引；未见质检时间、衣架键、计划键辅助索引。
- `P_Hanger_Master` 估算约 222,726 行；VHRD 用 `HM_ID + GroupId` 连接，但辅助索引没有这两个字段组成的组合键。该表 HM_ID 历史重复较多，问题不只是查询速度，也涉及视图结果正确性。
- 上述行数来自 2026-11-24 数据库分区统计估算，不是精确实时 COUNT。补索引前须确认衣拿厂商维护边界、写入负载与升级脚本；本轮没有改索引。

## 6. 全库用户对象清单

前缀分类按对象名和视图引用关系做工程归类，不是厂商正式业务分类。完整对象清单、字段数和估算行数如下。释义覆盖按目前两份字段字典、已解码数据库注释计算，格式为“字典释义/仅数据库注释/待补释义”；视图定义中的技术关联不直接计为中文业务释义。

| 对象 | 类型 | 字段数 | 索引数 | 估算行数 | 初步归类 | 字段释义覆盖（字典/注释/待补） |
|---|---|---:|---:|---:|---|
| dbo.B_Classified_Scheme | 表 | 17 | 5 | 0* | 基础资料/产线配置 | 0/0/17 |
| dbo.B_Colour_Master | 表 | 6 | 2 | 118* | 基础资料/产线配置 | 0/0/6 |
| dbo.B_Employee_Master | 表 | 32 | 2 | 1,482* | 基础资料/产线配置 | 0/5/27 |
| dbo.B_Hanger_Registry | 表 | 4 | 2 | 0* | 基础资料/产线配置 | 0/0/4 |
| dbo.B_Line_Bridging_Information | 表 | 5 | 2 | 0* | 基础资料/产线配置 | 0/0/5 |
| dbo.B_Line_Information | 表 | 9 | 1 | 6* | 基础资料/产线配置 | 0/0/9 |
| dbo.B_Line_Network_Setup | 表 | 18 | 1 | 6* | 基础资料/产线配置 | 0/0/18 |
| dbo.B_Line_Pusher_Setup | 表 | 17 | 1 | 6* | 基础资料/产线配置 | 0/0/17 |
| dbo.B_Line_System_Configuration | 表 | 14 | 1 | 6* | 基础资料/产线配置 | 0/0/14 |
| dbo.B_Line_Wireless_Controller | 表 | 7 | 2 | 10* | 基础资料/产线配置 | 0/0/7 |
| dbo.B_Line_Workstation_Setup | 表 | 16 | 2 | 208* | 基础资料/产线配置 | 0/0/16 |
| dbo.B_Lines_On_Network | 表 | 5 | 1 | 6* | 基础资料/产线配置 | 0/0/5 |
| dbo.B_Machine_Codes | 表 | 9 | 2 | 30* | 基础资料/产线配置 | 0/0/9 |
| dbo.B_Operation_Codes | 表 | 14 | 2 | 4,351* | 基础资料/产线配置 | 0/0/14 |
| dbo.B_Pay_Categories | 表 | 7 | 2 | 48* | 基础资料/产线配置 | 0/0/7 |
| dbo.B_Primary_Codes | 表 | 7 | 2 | 258* | 基础资料/产线配置 | 0/0/7 |
| dbo.B_QC_Header_Codes | 表 | 6 | 2 | 12* | 基础资料/产线配置 | 0/0/6 |
| dbo.B_QC_Sub_Codes | 表 | 9 | 2 | 13* | 基础资料/产线配置 | 0/0/9 |
| dbo.B_Size_Master | 表 | 6 | 2 | 156* | 基础资料/产线配置 | 0/0/6 |
| dbo.B_Sorting_Information_By_Line | 表 | 7 | 2 | 0* | 基础资料/产线配置 | 0/0/7 |
| dbo.B_Style_Colours_Not_Used | 表 | 5 | 3 | 46* | 基础资料/产线配置 | 0/0/5 |
| dbo.B_Style_Loading | 表 | 19 | 2 | 7* | 基础资料/产线配置 | 0/0/19 |
| dbo.B_Style_Loading_History | 表 | 17 | 3 | 7,240* | 基础资料/产线配置 | 0/0/17 |
| dbo.B_Style_Loading_Sorting_Information | 表 | 9 | 2 | 0* | 基础资料/产线配置 | 0/0/9 |
| dbo.B_Style_Master | 表 | 18 | 2 | 268* | 基础资料/产线配置 | 0/0/18 |
| dbo.B_Style_Operations | 表 | 11 | 2 | 4,374* | 基础资料/产线配置 | 0/0/11 |
| dbo.B_Style_Operations_Master | 表 | 8 | 2 | 270* | 基础资料/产线配置 | 0/0/8 |
| dbo.B_Style_Purchase_Orders | 表 | 11 | 2 | 0* | 基础资料/产线配置 | 0/0/11 |
| dbo.B_Style_Route_Detail | 表 | 16 | 2 | 11,224* | 基础资料/产线配置 | 0/0/16 |
| dbo.B_Style_Route_Master | 表 | 10 | 2 | 399* | 基础资料/产线配置 | 0/0/10 |
| dbo.B_Style_Sizes_Not_Used | 表 | 5 | 3 | 7* | 基础资料/产线配置 | 0/0/5 |
| dbo.B_WorkShop_Information | 表 | 3 | 1 | 0* | 基础资料/产线配置 | 0/0/3 |
| dbo.B_Work_Codes | 表 | 12 | 2 | 36* | 基础资料/产线配置 | 0/0/12 |
| dbo.B_Workstation_Employee_Information | 表 | 20 | 3 | 0* | 基础资料/产线配置 | 0/0/20 |
| dbo.P_Barcode_Info | 表 | 16 | 2 | 0* | 生产运行与事务记录 | 0/0/16 |
| dbo.P_Bridging_Hanger_Master | 表 | 23 | 1 | 0* | 生产运行与事务记录 | 0/0/23 |
| dbo.P_Bridging_Hanger_Routing_Details | 表 | 18 | 1 | 0* | 生产运行与事务记录 | 0/0/18 |
| dbo.P_Clock_InOut_History | 表 | 8 | 2 | 0* | 生产运行与事务记录 | 0/0/8 |
| dbo.P_Hanger_Master | 表 | 29 | 4 | 222,726* | 生产运行与事务记录 | 0/0/29 |
| dbo.P_Hanger_Routing_Details | 表 | 21 | 1 | 2,976,545* | 生产运行与事务记录 | 0/0/21 |
| dbo.P_Line_Daily_Break_Times | 表 | 10 | 2 | 5,926* | 生产运行与事务记录 | 0/0/10 |
| dbo.P_Line_Daily_Shift_Times | 表 | 13 | 2 | 5,811* | 生产运行与事务记录 | 0/0/13 |
| dbo.P_ODP_Detail | 表 | 27 | 3 | 221,665* | 生产运行与事务记录 | 0/0/27 |
| dbo.P_ODP_Master | 表 | 17 | 2 | 48,509* | 生产运行与事务记录 | 0/0/17 |
| dbo.P_QC_Rework | 表 | 23 | 2 | 170,329* | 生产运行与事务记录 | 0/0/23 |
| dbo.P_QC_Scrap | 表 | 14 | 2 | 0* | 生产运行与事务记录 | 0/0/14 |
| dbo.P_Style_Planned_Orders | 表 | 20 | 2 | 859* | 生产运行与事务记录 | 0/0/20 |
| dbo.P_Style_Planned_Orders_Quantity_Loaded | 表 | 6 | 2 | 0* | 生产运行与事务记录 | 0/0/6 |
| dbo.P_Style_Planned_Orders_Quantity_Registered | 表 | 6 | 2 | 0* | 生产运行与事务记录 | 0/0/6 |
| dbo.T_ColInfo | 表 | 5 | 1 | 0* | IDS/同步元数据 | 0/0/5 |
| dbo.T_DbInfo | 表 | 9 | 1 | 14* | IDS/同步元数据 | 0/6/3 |
| dbo.T_IDS_Version | 表 | 3 | 1 | 1* | IDS/同步元数据 | 0/0/3 |
| dbo.T_TableInfo | 表 | 6 | 1 | 289* | IDS/同步元数据 | 0/0/6 |
| dbo.ToIna_Colour | 表 | 4 | 1 | 2* | ERP 向衣拿输入接口 | 0/0/4 |
| dbo.ToIna_Employee | 表 | 10 | 1 | 385* | ERP 向衣拿输入接口 | 10/0/0 |
| dbo.ToIna_Route_Map | 表 | 8 | 1 | 0* | ERP 向衣拿输入接口 | 8/0/0 |
| dbo.ToIna_Size | 表 | 4 | 0 | 2* | ERP 向衣拿输入接口 | 0/0/4 |
| dbo.ToIna_Style | 表 | 8 | 1 | 1,576* | ERP 向衣拿输入接口 | 8/0/0 |
| dbo.ToIna_Style_Operations | 表 | 10 | 1 | 578* | ERP 向衣拿输入接口 | 9/1/0 |
| dbo.ToIna_Style_Planned_Orders | 表 | 18 | 1 | 432* | ERP 向衣拿输入接口 | 18/0/0 |
| dbo.report_daily_target | 表 | 7 | 3 | 2* | 附属报表对象 | 0/0/7 |
| dbo.report_user_pwd | 表 | 6 | 1 | 6* | 附属报表对象 | 0/0/6 |
| dbo.V_Hanger_Routing_Details | 视图 | 39 | 0 | — | 视图 | 37/0/2 |
| dbo.V_ODP_Info | 视图 | 42 | 0 | — | 视图 | 41/0/1 |
| dbo.V_QC_Rework_Info | 视图 | 42 | 0 | — | 视图 | 42/0/0 |


## 7. 全字段清单

字段释义证据状态说明：`见 §3.4` 表示两份 Excel 有字段定义；`仅有数据库注释` 表示有 SQL Server 扩展属性但业务范围/值域未必完整；`暂无字段级释义` 表示现有两份字典和数据库注释都没有该字段的直接说明。字段出现在视图 JOIN/SELECT 中只证明其技术关联，不自动等于完整业务语义。


类型长度按 SQL Server 显示；NULL 表示允许为空；默认/计算表达式和 MS_Description 仅在数据库实际存在时列出。没有注释表示数据库没有注释，不表示字段没有业务意义。

### dbo.B_Classified_Scheme（表，17 字段）

| 序号 | 字段 | SQL 类型 | 可空 | 标识/计算 | 默认值或计算表达式 | 数据库注释 | 释义证据状态 |
|---:|---|---|---|---|---|---|---|
| 1 | CS_Key | uniqueidentifier | NOT NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 2 | CS_CM_Key | int | NOT NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 3 | CS_SM_Key | int | NOT NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 4 | CS_Lot_Number | nvarchar(100) | NOT NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 5 | CS_ST_Key | int | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 6 | CS_SRM_Key | int | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 7 | CS_OC_Key | int | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 8 | CS_Station_Number | smallint | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 9 | CS_Line_Number | smallint | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 10 | CS_Quantity_Loaded | int | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 11 | CS_Quantity_Ordered | int | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 12 | CS_Completed | bit | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 13 | CS_Create_Time | datetime | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 14 | CS_Classified_Order | smallint | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 15 | LineId | int | NOT NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 16 | DelFlag | int | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 17 | GroupId | int | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |

### dbo.B_Colour_Master（表，6 字段）

| 序号 | 字段 | SQL 类型 | 可空 | 标识/计算 | 默认值或计算表达式 | 数据库注释 | 释义证据状态 |
|---:|---|---|---|---|---|---|---|
| 1 | CM_Key | int | NOT NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 2 | CM_Short_Description | nvarchar(10) | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 3 | CM_Description | nvarchar(255) | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 4 | LineId | int | NOT NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 5 | DelFlag | int | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 6 | GroupId | int | NULL | — | DEFAULT ((0)) | — | 暂无字段级释义；待查同步配置/厂商定义 |

### dbo.B_Employee_Master（表，32 字段）

| 序号 | 字段 | SQL 类型 | 可空 | 标识/计算 | 默认值或计算表达式 | 数据库注释 | 释义证据状态 |
|---:|---|---|---|---|---|---|---|
| 1 | EM_Key | int | NOT NULL | — | — | 工号 | 仅有数据库注释；业务范围/取值仍需确认 |
| 2 | EM_FirstName | nvarchar(50) | NULL | — | — | 姓 | 仅有数据库注释；业务范围/取值仍需确认 |
| 3 | EM_LastName | nvarchar(50) | NULL | — | — | 名 | 仅有数据库注释；业务范围/取值仍需确认 |
| 4 | EM_LCD_Name | nvarchar(15) | NULL | — | — | 姓名 | 仅有数据库注释；业务范围/取值仍需确认 |
| 5 | EM_Sex | nvarchar(1) | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 6 | EM_Department | nvarchar(15) | NULL | — | — | 部门 | 仅有数据库注释；业务范围/取值仍需确认 |
| 7 | EM_SSN | nvarchar(15) | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 8 | EM_GuaranteedPay | real | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 9 | EM_AveragePay | real | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 10 | EM_UserOnePay | real | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 11 | EM_UserTwoPay | real | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 12 | EM_Incentive | real | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 13 | EM_Picture | image | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 14 | EM_Address | nvarchar(50) | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 15 | EM_City | nvarchar(25) | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 16 | EM_Province | nvarchar(25) | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 17 | EM_PostalCode | nvarchar(10) | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 18 | EM_HomePhoneNumber | nvarchar(20) | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 19 | EM_CellPhoneNumber | nvarchar(20) | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 20 | EM_EmergencyContactName | nvarchar(50) | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 21 | EM_EmergencyContactRelation | nvarchar(15) | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 22 | EM_EmergencyContactPhone | nvarchar(20) | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 23 | EM_BloodType | nvarchar(5) | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 24 | EM_BirthDate | datetime | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 25 | EM_DateHired | datetime | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 26 | EM_TerminationDate | datetime | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 27 | EM_RFID | nvarchar(10) | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 28 | EM_Supervisor | bit | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 29 | EM_Default_Target_Quantity | smallint | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 30 | LineId | int | NOT NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 31 | DelFlag | int | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 32 | GroupId | int | NULL | — | DEFAULT ((0)) | — | 暂无字段级释义；待查同步配置/厂商定义 |

### dbo.B_Hanger_Registry（表，4 字段）

| 序号 | 字段 | SQL 类型 | 可空 | 标识/计算 | 默认值或计算表达式 | 数据库注释 | 释义证据状态 |
|---:|---|---|---|---|---|---|---|
| 1 | HR_HM_ID | nvarchar(12) | NOT NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 2 | LineId | int | NOT NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 3 | DelFlag | int | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 4 | GroupId | int | NULL | — | DEFAULT ((0)) | — | 暂无字段级释义；待查同步配置/厂商定义 |

### dbo.B_Line_Bridging_Information（表，5 字段）

| 序号 | 字段 | SQL 类型 | 可空 | 标识/计算 | 默认值或计算表达式 | 数据库注释 | 释义证据状态 |
|---:|---|---|---|---|---|---|---|
| 1 | From_Line | smallint | NOT NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 2 | From_Station | smallint | NOT NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 3 | To_Line | smallint | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 4 | To_Station | smallint | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 5 | GroupId | int | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |

### dbo.B_Line_Information（表，9 字段）

| 序号 | 字段 | SQL 类型 | 可空 | 标识/计算 | 默认值或计算表达式 | 数据库注释 | 释义证据状态 |
|---:|---|---|---|---|---|---|---|
| 1 | Date_Of_Configuration | datetime | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 2 | Line_Number | smallint | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 3 | Line_Description | nvarchar(50) | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 4 | Stations | smallint | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 5 | LineId | int | NOT NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 6 | DelFlag | int | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 7 | GroupId | int | NULL | — | DEFAULT ((0)) | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 8 | LI_WI_Key | uniqueidentifier | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 9 | Line_TargetQuantity | int | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |

### dbo.B_Line_Network_Setup（表，18 字段）

| 序号 | 字段 | SQL 类型 | 可空 | 标识/计算 | 默认值或计算表达式 | 数据库注释 | 释义证据状态 |
|---:|---|---|---|---|---|---|---|
| 1 | Date_Of_Configuration | datetime | NOT NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 2 | Operating_System | nvarchar(20) | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 3 | Interface | nvarchar(20) | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 4 | Device_Name | nvarchar(20) | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 5 | Cimetrics_Baud_Rate | int | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 6 | Cimetrics_Retries | smallint | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 7 | Cimetrics_Slave_Response | smallint | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 8 | Cimetrics_Transmit_Timeout | smallint | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 9 | Reader_Type | nvarchar(50) | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 10 | Sensor_Hanger_Scan_COM_Port | smallint | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 11 | Sensor_Hanger_Scan | bit | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 12 | Console_Hardware | nvarchar(8) | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 13 | Use_Mobile_Console | bit | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 14 | Mobile_Console_Group_Number | smallint | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 15 | Mobile_Console_Device_Number | smallint | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 16 | LineId | int | NOT NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 17 | DelFlag | int | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 18 | GroupId | int | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |

### dbo.B_Line_Pusher_Setup（表，17 字段）

| 序号 | 字段 | SQL 类型 | 可空 | 标识/计算 | 默认值或计算表达式 | 数据库注释 | 释义证据状态 |
|---:|---|---|---|---|---|---|---|
| 1 | Date_Of_Configuration | datetime | NOT NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 2 | Units_Of_Measurement | nvarchar(10) | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 3 | Number_of_Pushers | int | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 4 | Distance_Between_Pushers | real | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 5 | Time_between_pushers | int | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 6 | Sensor_Timeout | int | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 7 | InFeed_Duration | smallint | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 8 | OutFeed_Duration | smallint | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 9 | Pusher_Present_On_High_Bit | bit | NOT NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 10 | Interface_Output_Address | int | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 11 | Interface_Input_Address | int | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 12 | LineId | int | NOT NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 13 | GroupId | int | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 14 | DelFlag | int | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 15 | Ignore_Pushers | int | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 16 | Pusher_Sensor_Type | smallint | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 17 | Pusher_RFID_COM_Port | smallint | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |

### dbo.B_Line_System_Configuration（表，14 字段）

| 序号 | 字段 | SQL 类型 | 可空 | 标识/计算 | 默认值或计算表达式 | 数据库注释 | 释义证据状态 |
|---:|---|---|---|---|---|---|---|
| 1 | Date_Of_Configuration | datetime | NOT NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 2 | Company_Name | nvarchar(255) | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 3 | Company_Address | nvarchar(255) | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 4 | Company_URL | nvarchar(255) | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 5 | Company_Email | nvarchar(255) | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 6 | Company_Logo | image | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 7 | Payment_Type | smallint | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 8 | Standard_Piece_Rate_Amount | real | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 9 | Automatically_Calculate_Makeup | bit | NOT NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 10 | Makeup_To | nvarchar(10) | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 11 | Non_Standard_Piece_Rate_Amount | real | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 12 | LineId | int | NOT NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 13 | DelFlag | int | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 14 | GroupId | int | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |

### dbo.B_Line_Wireless_Controller（表，7 字段）

| 序号 | 字段 | SQL 类型 | 可空 | 标识/计算 | 默认值或计算表达式 | 数据库注释 | 释义证据状态 |
|---:|---|---|---|---|---|---|---|
| 1 | LWC_Group_Id | smallint | NOT NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 2 | LWC_COM_Port | smallint | NOT NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 3 | LWC_Baud_Rate | int | NOT NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 4 | LWC_Default_Timeout | smallint | NOT NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 5 | LineId | int | NOT NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 6 | DelFlag | int | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 7 | GroupId | int | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |

### dbo.B_Line_Workstation_Setup（表，16 字段）

| 序号 | 字段 | SQL 类型 | 可空 | 标识/计算 | 默认值或计算表达式 | 数据库注释 | 释义证据状态 |
|---:|---|---|---|---|---|---|---|
| 1 | Workstation | smallint | NOT NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 2 | Hub | smallint | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 3 | Position | nvarchar(1) | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 4 | In_Feed_Pushers_From_Sensor | smallint | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 5 | In_Feed_Distance_To_Next_Pusher | real | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 6 | Out_Feed_Pushers_From_Sensor | smallint | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 7 | Out_Feed_Distance_To_Next_Pusher | real | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 8 | Use_Wireless_Console | bit | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 9 | Wireless_Console_Group | smallint | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 10 | Cable_Console_Port | smallint | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 11 | GroupId | int | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 12 | LineId | int | NOT NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 13 | DelFlag | int | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 14 | Multimedia_Terminal_IP | nvarchar(25) | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 15 | Additional_Wireless_Console1_Group | smallint | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 16 | Additional_Wireless_Console2_Group | smallint | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |

### dbo.B_Lines_On_Network（表，5 字段）

| 序号 | 字段 | SQL 类型 | 可空 | 标识/计算 | 默认值或计算表达式 | 数据库注释 | 释义证据状态 |
|---:|---|---|---|---|---|---|---|
| 1 | Line_Number | smallint | NOT NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 2 | Line_Description | nvarchar(50) | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 3 | Stations | smallint | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 4 | Line_Type | nvarchar(4) | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 5 | GroupId | int | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |

### dbo.B_Machine_Codes（表，9 字段）

| 序号 | 字段 | SQL 类型 | 可空 | 标识/计算 | 默认值或计算表达式 | 数据库注释 | 释义证据状态 |
|---:|---|---|---|---|---|---|---|
| 1 | MC_Key | int | NOT NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 2 | MC_Name | nvarchar(15) | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 3 | MC_Type | nvarchar(25) | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 4 | MC_Gauge | nvarchar(5) | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 5 | MC_Attachments | nvarchar(25) | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 6 | MC_Quantity | smallint | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 7 | LineId | int | NOT NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 8 | DelFlag | int | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 9 | GroupId | int | NULL | — | DEFAULT ((0)) | — | 暂无字段级释义；待查同步配置/厂商定义 |

### dbo.B_Operation_Codes（表，14 字段）

| 序号 | 字段 | SQL 类型 | 可空 | 标识/计算 | 默认值或计算表达式 | 数据库注释 | 释义证据状态 |
|---:|---|---|---|---|---|---|---|
| 1 | OC_Key | int | NOT NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 2 | OC_Sub_Code | smallint | NOT NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 3 | OC_PC_Key | smallint | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 4 | OC_Description | nvarchar(255) | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 5 | OC_Standard_Time | real | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 6 | OC_Piece_Rate | real | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 7 | OC_PA_Key | nvarchar(10) | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 8 | OC_Online | bit | NOT NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 9 | OC_Non_Standard_Time | real | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 10 | OC_Piece_Rate_Additional | real | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 11 | OC_ID | nvarchar(50) | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 12 | LineId | int | NOT NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 13 | DelFlag | int | NULL | — | DEFAULT ((0)) | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 14 | GroupId | int | NULL | — | DEFAULT ((0)) | — | 暂无字段级释义；待查同步配置/厂商定义 |

### dbo.B_Pay_Categories（表，7 字段）

| 序号 | 字段 | SQL 类型 | 可空 | 标识/计算 | 默认值或计算表达式 | 数据库注释 | 释义证据状态 |
|---:|---|---|---|---|---|---|---|
| 1 | PA_Key | nvarchar(10) | NOT NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 2 | PA_CategoryType | nvarchar(20) | NOT NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 3 | PA_Description | nvarchar(25) | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 4 | PA_CurrencyValue | real | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 5 | LineId | int | NOT NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 6 | DelFlag | int | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 7 | GroupId | int | NULL | — | DEFAULT ((0)) | — | 暂无字段级释义；待查同步配置/厂商定义 |

### dbo.B_Primary_Codes（表，7 字段）

| 序号 | 字段 | SQL 类型 | 可空 | 标识/计算 | 默认值或计算表达式 | 数据库注释 | 释义证据状态 |
|---:|---|---|---|---|---|---|---|
| 1 | PC_Key | smallint | NOT NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 2 | PC_Description | nvarchar(25) | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 3 | PC_MC_Key | int | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 4 | PC_ID | nvarchar(50) | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 5 | LineId | int | NOT NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 6 | DelFlag | int | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 7 | GroupId | int | NULL | — | DEFAULT ((0)) | — | 暂无字段级释义；待查同步配置/厂商定义 |

### dbo.B_QC_Header_Codes（表，6 字段）

| 序号 | 字段 | SQL 类型 | 可空 | 标识/计算 | 默认值或计算表达式 | 数据库注释 | 释义证据状态 |
|---:|---|---|---|---|---|---|---|
| 1 | QCHC_Key | uniqueidentifier | NOT NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 2 | QCHC_Description | nvarchar(255) | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 3 | QCHC_LCD | nvarchar(32) | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 4 | LineId | int | NOT NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 5 | DelFlag | int | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 6 | GroupId | int | NULL | — | DEFAULT ((0)) | — | 暂无字段级释义；待查同步配置/厂商定义 |

### dbo.B_QC_Sub_Codes（表，9 字段）

| 序号 | 字段 | SQL 类型 | 可空 | 标识/计算 | 默认值或计算表达式 | 数据库注释 | 释义证据状态 |
|---:|---|---|---|---|---|---|---|
| 1 | QCSC_Key | uniqueidentifier | NOT NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 2 | QCSC_QCHC_Key | uniqueidentifier | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 3 | QCSC_Description | nvarchar(255) | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 4 | QCSC_LCD | nvarchar(32) | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 5 | QCSC_Number | int | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 6 | QCSC_Is_Rework | bit | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 7 | LineId | int | NOT NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 8 | DelFlag | int | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 9 | GroupId | int | NULL | — | DEFAULT ((0)) | — | 暂无字段级释义；待查同步配置/厂商定义 |

### dbo.B_Size_Master（表，6 字段）

| 序号 | 字段 | SQL 类型 | 可空 | 标识/计算 | 默认值或计算表达式 | 数据库注释 | 释义证据状态 |
|---:|---|---|---|---|---|---|---|
| 1 | SM_Key | int | NOT NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 2 | SM_Short_Description | nvarchar(10) | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 3 | SM_Description | nvarchar(255) | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 4 | LineId | int | NOT NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 5 | DelFlag | int | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 6 | GroupId | int | NULL | — | DEFAULT ((0)) | — | 暂无字段级释义；待查同步配置/厂商定义 |

### dbo.B_Sorting_Information_By_Line（表，7 字段）

| 序号 | 字段 | SQL 类型 | 可空 | 标识/计算 | 默认值或计算表达式 | 数据库注释 | 释义证据状态 |
|---:|---|---|---|---|---|---|---|
| 1 | SIBL_SLSI_Key | int | NOT NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 2 | SIBL_Line | smallint | NOT NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 3 | SIBL_Quantity | int | NOT NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 4 | SIBL_Min_Loaded_Time | datetime | NOT NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 5 | LineId | int | NOT NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 6 | DelFlag | int | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 7 | GroupId | int | NULL | — | DEFAULT ((0)) | — | 暂无字段级释义；待查同步配置/厂商定义 |

### dbo.B_Style_Colours_Not_Used（表，5 字段）

| 序号 | 字段 | SQL 类型 | 可空 | 标识/计算 | 默认值或计算表达式 | 数据库注释 | 释义证据状态 |
|---:|---|---|---|---|---|---|---|
| 1 | SCNU_ST_Key | int | NOT NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 2 | SCNU_CM_Key | int | NOT NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 3 | LineId | int | NOT NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 4 | DelFlag | int | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 5 | GroupId | int | NULL | — | DEFAULT ((0)) | — | 暂无字段级释义；待查同步配置/厂商定义 |

### dbo.B_Style_Loading（表，19 字段）

| 序号 | 字段 | SQL 类型 | 可空 | 标识/计算 | 默认值或计算表达式 | 数据库注释 | 释义证据状态 |
|---:|---|---|---|---|---|---|---|
| 1 | SL_Station | smallint | NOT NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 2 | SL_ST_Key | int | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 3 | SL_STPO_Key | int | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 4 | SL_Lot_Number | nvarchar(100) | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 5 | SL_CM_Key | int | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 6 | SL_SM_Key | int | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 7 | SL_Garments_Per_Hanger | smallint | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 8 | SL_SRM_Key | int | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 9 | SL_Item_Card | nvarchar(20) | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 10 | SL_Allowed_Out | bit | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 11 | SL_SLSI_Key | int | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 12 | SL_Sort_Before | bit | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 13 | SL_Sort_Before_Operation | bit | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 14 | SL_Sort_Before_OC_Key | int | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 15 | SL_Manual_Bundle_Quantity | int | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 16 | SL_Manual_Bundle_Remaining | int | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 17 | LineId | int | NOT NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 18 | DelFlag | int | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 19 | GroupId | int | NULL | — | DEFAULT ((0)) | — | 暂无字段级释义；待查同步配置/厂商定义 |

### dbo.B_Style_Loading_History（表，17 字段）

| 序号 | 字段 | SQL 类型 | 可空 | 标识/计算 | 默认值或计算表达式 | 数据库注释 | 释义证据状态 |
|---:|---|---|---|---|---|---|---|
| 1 | SL_Station | smallint | NOT NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 2 | SL_Started_Loading_Date | datetime | NOT NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 3 | SL_ST_Key | int | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 4 | SL_STPO_Key | int | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 5 | SL_Lot_Number | nvarchar(25) | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 6 | SL_CM_Key | int | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 7 | SL_SM_Key | int | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 8 | SL_Garments_Per_Hanger | smallint | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 9 | SL_SRM_Key | int | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 10 | SL_Item_Card | nvarchar(20) | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 11 | SL_SLSI_Key | int | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 12 | SL_Sort_Before | bit | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 13 | SL_Sort_Before_Operation | bit | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 14 | SL_Sort_Before_OC_Key | int | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 15 | LineId | int | NOT NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 16 | DelFlag | int | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 17 | GroupId | int | NULL | — | DEFAULT ((0)) | — | 暂无字段级释义；待查同步配置/厂商定义 |

### dbo.B_Style_Loading_Sorting_Information（表，9 字段）

| 序号 | 字段 | SQL 类型 | 可空 | 标识/计算 | 默认值或计算表达式 | 数据库注释 | 释义证据状态 |
|---:|---|---|---|---|---|---|---|
| 1 | SLSI_Key | int | NOT NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 2 | SLSI_Type | smallint | NOT NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 3 | SLSI_Active | bit | NOT NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 4 | SLSI_Description | nvarchar(50) | NOT NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 5 | SLSI_Date_Created | datetime | NOT NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 6 | SLSI_Before_OC_Key | int | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 7 | LineId | int | NOT NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 8 | DelFlag | int | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 9 | GroupId | int | NULL | — | DEFAULT ((0)) | — | 暂无字段级释义；待查同步配置/厂商定义 |

### dbo.B_Style_Master（表，18 字段）

| 序号 | 字段 | SQL 类型 | 可空 | 标识/计算 | 默认值或计算表达式 | 数据库注释 | 释义证据状态 |
|---:|---|---|---|---|---|---|---|
| 1 | ST_Key | int | NOT NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 2 | ST_ID | nvarchar(25) | NOT NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 3 | ST_Description | nvarchar(50) | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 4 | ST_Collection | nvarchar(25) | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 5 | ST_Fabric | nvarchar(25) | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 6 | ST_Trim | nvarchar(25) | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 7 | ST_Logo_Name | nvarchar(25) | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 8 | ST_Logo_Placement | nvarchar(25) | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 9 | ST_Comments | ntext | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 10 | ST_Picture | image | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 11 | ST_Date_Created | datetime | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 12 | ST_Last_Updated | datetime | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 13 | ST_Line | smallint | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 14 | ST_Bundle_Active_SRM_Key | int | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 15 | LineId | int | NOT NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 16 | DelFlag | int | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 17 | GroupId | int | NULL | — | DEFAULT ((0)) | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 18 | ST_STOPM_Key | uniqueidentifier | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |

### dbo.B_Style_Operations（表，11 字段）

| 序号 | 字段 | SQL 类型 | 可空 | 标识/计算 | 默认值或计算表达式 | 数据库注释 | 释义证据状态 |
|---:|---|---|---|---|---|---|---|
| 1 | STOP_Key | int | NOT NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 2 | STOP_ST_Key | int | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 3 | STOP_OC_Key | int | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 4 | STOP_Order | smallint | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 5 | STOP_Alternate_Order | smallint | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 6 | STOP_Number | nvarchar(50) | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 7 | STOP_Alternate_Order_Multiple | nvarchar(100) | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 8 | STOP_STOPM_Key | uniqueidentifier | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 9 | LineId | int | NOT NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 10 | DelFlag | int | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 11 | GroupId | int | NULL | — | DEFAULT ((0)) | — | 暂无字段级释义；待查同步配置/厂商定义 |

### dbo.B_Style_Operations_Master（表，8 字段）

| 序号 | 字段 | SQL 类型 | 可空 | 标识/计算 | 默认值或计算表达式 | 数据库注释 | 释义证据状态 |
|---:|---|---|---|---|---|---|---|
| 1 | STOPM_Key | uniqueidentifier | NOT NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 2 | STOPM_ID | nvarchar(50) | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 3 | STOPM_Description | nvarchar(50) | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 4 | STOPM_Last_Updated | datetime | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 5 | LineId | int | NOT NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 6 | DelFlag | int | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 7 | GroupId | int | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 8 | STOPM_SyncTime | datetime | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |

### dbo.B_Style_Purchase_Orders（表，11 字段）

| 序号 | 字段 | SQL 类型 | 可空 | 标识/计算 | 默认值或计算表达式 | 数据库注释 | 释义证据状态 |
|---:|---|---|---|---|---|---|---|
| 1 | STPO_Key | int | NOT NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 2 | STPO_ST_Key | int | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 3 | STPO_Number | nvarchar(15) | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 4 | STPO_Agent_Buyer | nvarchar(50) | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 5 | STPO_Quantity_Ordered | int | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 6 | STPO_Comments | nvarchar(255) | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 7 | STPO_Date_Created | datetime | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 8 | STPO_Date_Modified | datetime | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 9 | LineId | int | NOT NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 10 | DelFlag | int | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 11 | GroupId | int | NULL | — | DEFAULT ((0)) | — | 暂无字段级释义；待查同步配置/厂商定义 |

### dbo.B_Style_Route_Detail（表，16 字段）

| 序号 | 字段 | SQL 类型 | 可空 | 标识/计算 | 默认值或计算表达式 | 数据库注释 | 释义证据状态 |
|---:|---|---|---|---|---|---|---|
| 1 | SRD_Key | int | NOT NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 2 | SRD_SRM_Key | int | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 3 | SRD_OC_Key | int | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 4 | SRD_Line_Number | int | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 5 | SRD_Station_Number | int | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 6 | SRD_Receive | bit | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 7 | SRD_Receive_Colour_Key | int | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 8 | SRD_Receive_Size_Key | int | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 9 | SRD_Distribution_Percent | real | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 10 | LineId | int | NOT NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 11 | DelFlag | int | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 12 | GroupId | int | NULL | — | DEFAULT ((0)) | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 13 | SRD_Use_Manual_Distribution | bit | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 14 | SRD_CS_Used | bit | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 15 | SRD_CS_Key | uniqueidentifier | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 16 | SRD_Quantity_Sent | int | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |

### dbo.B_Style_Route_Master（表，10 字段）

| 序号 | 字段 | SQL 类型 | 可空 | 标识/计算 | 默认值或计算表达式 | 数据库注释 | 释义证据状态 |
|---:|---|---|---|---|---|---|---|
| 1 | SRM_Key | int | NOT NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 2 | SRM_Description | nvarchar(255) | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 3 | SRM_ST_Key | int | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 4 | SRM_Line | smallint | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 5 | Created_Date | datetime | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 6 | Modified_Date | datetime | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 7 | SRM_STOPM_Key | uniqueidentifier | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 8 | LineId | int | NOT NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 9 | DelFlag | int | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 10 | GroupId | int | NULL | — | DEFAULT ((0)) | — | 暂无字段级释义；待查同步配置/厂商定义 |

### dbo.B_Style_Sizes_Not_Used（表，5 字段）

| 序号 | 字段 | SQL 类型 | 可空 | 标识/计算 | 默认值或计算表达式 | 数据库注释 | 释义证据状态 |
|---:|---|---|---|---|---|---|---|
| 1 | SSNU_ST_Key | int | NOT NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 2 | SSNU_SM_Key | int | NOT NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 3 | LineId | int | NOT NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 4 | DelFlag | int | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 5 | GroupId | int | NULL | — | DEFAULT ((0)) | — | 暂无字段级释义；待查同步配置/厂商定义 |

### dbo.B_WorkShop_Information（表，3 字段）

| 序号 | 字段 | SQL 类型 | 可空 | 标识/计算 | 默认值或计算表达式 | 数据库注释 | 释义证据状态 |
|---:|---|---|---|---|---|---|---|
| 1 | WI_Key | uniqueidentifier | NOT NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 2 | WI_Name | nvarchar(50) | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 3 | WI_Description | nvarchar(100) | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |

### dbo.B_Work_Codes（表，12 字段）

| 序号 | 字段 | SQL 类型 | 可空 | 标识/计算 | 默认值或计算表达式 | 数据库注释 | 释义证据状态 |
|---:|---|---|---|---|---|---|---|
| 1 | WC_Key | smallint | NOT NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 2 | WC_Description | nvarchar(25) | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 3 | WC_IsPieceWork | bit | NOT NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 4 | WC_IsNonStandard | bit | NOT NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 5 | WC_IsDelay | bit | NOT NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 6 | WC_SetNoReceive | bit | NOT NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 7 | WC_PA_Key | nvarchar(10) | NOT NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 8 | WC_Supplement | real | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 9 | WC_Prohibit_Outfeed | bit | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 10 | LineId | int | NOT NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 11 | DelFlag | int | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 12 | GroupId | int | NULL | — | DEFAULT ((0)) | — | 暂无字段级释义；待查同步配置/厂商定义 |

### dbo.B_Workstation_Employee_Information（表，20 字段）

| 序号 | 字段 | SQL 类型 | 可空 | 标识/计算 | 默认值或计算表达式 | 数据库注释 | 释义证据状态 |
|---:|---|---|---|---|---|---|---|
| 1 | WEI_Workstation | smallint | NOT NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 2 | WEI_Position | smallint | NOT NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 3 | WEI_Clocked_In | bit | NOT NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 4 | WEI_EM_Key | int | NOT NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 5 | WEI_ST_Key | int | NOT NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 6 | WEI_STPO_Key | int | NOT NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 7 | WEI_Lot_Number | nvarchar(50) | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 8 | WEI_SRM_Key | int | NOT NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 9 | WEI_Garments_Per_Hanger | smallint | NOT NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 10 | WEI_OC_Key | int | NOT NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 11 | WEI_CM_Key | int | NOT NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 12 | WEI_SM_Key | int | NOT NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 13 | WEI_Quantity_Produced | int | NOT NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 14 | WEI_Efficiency | real | NOT NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 15 | WEI_Total_Quantity_Produced | int | NOT NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 16 | WEI_Total_Efficiency | real | NOT NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 17 | WEI_LLC | datetime | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 18 | LineId | int | NOT NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 19 | DelFlag | int | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 20 | GroupId | int | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |

### dbo.P_Barcode_Info（表，16 字段）

| 序号 | 字段 | SQL 类型 | 可空 | 标识/计算 | 默认值或计算表达式 | 数据库注释 | 释义证据状态 |
|---:|---|---|---|---|---|---|---|
| 1 | BCI_Key | uniqueidentifier | NOT NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 2 | BCI_LabelID | nvarchar(20) | NOT NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 3 | BCI_StyleID | nvarchar(25) | NOT NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 4 | BCI_Colour | nvarchar(20) | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 5 | BCI_Size | nvarchar(10) | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 6 | BCI_Dept | nvarchar(25) | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 7 | BCI_InserTime | datetime | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 8 | BCI_Used | bit | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 9 | BCI_Used_Count | int | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 10 | BCI_LotNumber | nvarchar(25) | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 11 | BCI_Trim | nvarchar(25) | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 12 | BCI_Quantity | int | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 13 | BCI_HM_Key | uniqueidentifier | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 14 | LineId | int | NOT NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 15 | DelFlag | int | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 16 | GroupId | int | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |

### dbo.P_Bridging_Hanger_Master（表，23 字段）

| 序号 | 字段 | SQL 类型 | 可空 | 标识/计算 | 默认值或计算表达式 | 数据库注释 | 释义证据状态 |
|---:|---|---|---|---|---|---|---|
| 1 | HM_ID | nvarchar(50) | NOT NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 2 | HM_Loaded_On_Line | smallint | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 3 | HM_Current_Line | smallint | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 4 | HM_Current_Location_Type | nvarchar(1) | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 5 | HM_Current_Location | smallint | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 6 | HM_SRM_Key | int | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 7 | HM_ST_Key | int | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 8 | HM_STPO_Key | int | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 9 | HM_Lot_Number | nvarchar(100) | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 10 | HM_CM_Key | int | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 11 | HM_SM_Key | int | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 12 | HM_Garments_Per_Hanger | smallint | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 13 | HM_Production_Time | real | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 14 | HM_PI_Key | int | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 15 | HM_Item_Card | nvarchar(20) | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 16 | HM_Routing_Info | nvarchar(50) | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 17 | HM_Is_Scrapped | bit | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 18 | HM_ACC_Key | uniqueidentifier | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 19 | HM_Key | uniqueidentifier | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 20 | HM_SLSI_Key | int | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 21 | HM_Sort_Complete | bit | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 22 | GroupId | int | NULL | — | DEFAULT ((0)) | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 23 | HM_Label | nvarchar(50) | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |

### dbo.P_Bridging_Hanger_Routing_Details（表，18 字段）

| 序号 | 字段 | SQL 类型 | 可空 | 标识/计算 | 默认值或计算表达式 | 数据库注释 | 释义证据状态 |
|---:|---|---|---|---|---|---|---|
| 1 | HRD_Key | uniqueidentifier | NOT NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 2 | HRD_HM_ID | nvarchar(12) | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 3 | HRD_OC_Key | int | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 4 | HRD_EM_Key | int | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 5 | HRD_Line | smallint | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 6 | HRD_Workstation | smallint | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 7 | HRD_Completed_Time | datetime | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 8 | HRD_ODP_Credited | bit | NOT NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 9 | HRD_Comments | nvarchar(255) | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 10 | HRD_PID_Key | int | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 11 | HRD_Started_Time | datetime | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 12 | HRD_WC_Key | smallint | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 13 | HRD_Return_For_Repair | bit | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 14 | HRD_Create_Time | datetime | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 15 | GroupId | int | NULL | — | DEFAULT ((0)) | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 16 | HRD_OC_Completed | bit | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 17 | HRD_Enter_Time | datetime | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 18 | HRD_Defect_Order | smallint | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |

### dbo.P_Clock_InOut_History（表，8 字段）

| 序号 | 字段 | SQL 类型 | 可空 | 标识/计算 | 默认值或计算表达式 | 数据库注释 | 释义证据状态 |
|---:|---|---|---|---|---|---|---|
| 1 | CIOH_Key | bigint | NOT NULL | IDENTITY | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 2 | CIOH_ODP_Key | uniqueidentifier | NOT NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 3 | CIOH_Clock_In_Time | datetime | NOT NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 4 | CIOH_Clock_Out_Time | datetime | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 5 | CIOH_Workstation | int | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 6 | LineId | int | NOT NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 7 | DelFlag | int | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 8 | GroupId | int | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |

### dbo.P_Hanger_Master（表，29 字段）

| 序号 | 字段 | SQL 类型 | 可空 | 标识/计算 | 默认值或计算表达式 | 数据库注释 | 释义证据状态 |
|---:|---|---|---|---|---|---|---|
| 1 | HM_ID | nvarchar(50) | NOT NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 2 | HM_Key | uniqueidentifier | NOT NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 3 | HM_Loaded_On_Line | smallint | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 4 | HM_Current_Line | smallint | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 5 | HM_Current_Location_Type | nvarchar(1) | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 6 | HM_Current_Location | smallint | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 7 | HM_SRM_Key | int | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 8 | HM_ST_Key | int | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 9 | HM_STPO_Key | int | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 10 | HM_Lot_Number | nvarchar(100) | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 11 | HM_CM_Key | int | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 12 | HM_SM_Key | int | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 13 | HM_Garments_Per_Hanger | smallint | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 14 | HM_Production_Time | real | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 15 | HM_Item_Card | nvarchar(20) | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 16 | HM_PI_Key | int | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 17 | HM_Routing_Info | nvarchar(50) | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 18 | HM_Is_Scrapped | bit | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 19 | HM_ACC_Key | uniqueidentifier | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 20 | HM_SLSI_Key | int | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 21 | HM_Sort_Complete | bit | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 22 | DelFlag | int | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 23 | GroupId | int | NULL | — | DEFAULT ((0)) | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 24 | HM_SP_Key | int | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 25 | HM_Label | nvarchar(50) | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 26 | HM_Enter_Station | int | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 27 | HM_Enter_Time | datetime | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 28 | HM_OC_List | nvarchar(900) | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 29 | HM_Update_Time | datetime | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |

### dbo.P_Hanger_Routing_Details（表，21 字段）

| 序号 | 字段 | SQL 类型 | 可空 | 标识/计算 | 默认值或计算表达式 | 数据库注释 | 释义证据状态 |
|---:|---|---|---|---|---|---|---|
| 1 | HRD_Key | uniqueidentifier | NOT NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 2 | HRD_HM_ID | nvarchar(12) | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 3 | HRD_HM_Key | uniqueidentifier | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 4 | HRD_OC_Key | int | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 5 | HRD_EM_Key | int | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 6 | HRD_Line | smallint | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 7 | HRD_Workstation | smallint | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 8 | HRD_Completed_Time | datetime | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 9 | HRD_ODP_Credited | bit | NOT NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 10 | HRD_Comments | nvarchar(255) | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 11 | HRD_WC_Key | smallint | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 12 | HRD_Started_Time | datetime | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 13 | HRD_PID_Key | int | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 14 | HRD_Return_For_Repair | bit | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 15 | HRD_Create_Time | datetime | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 16 | DelFlag | int | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 17 | HRD_AutoId | int | NOT NULL | IDENTITY | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 18 | GroupId | int | NULL | — | DEFAULT ((0)) | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 19 | HRD_OC_Completed | bit | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 20 | HRD_Enter_Time | datetime | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 21 | HRD_Defect_Order | smallint | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |

### dbo.P_Line_Daily_Break_Times（表，10 字段）

| 序号 | 字段 | SQL 类型 | 可空 | 标识/计算 | 默认值或计算表达式 | 数据库注释 | 释义证据状态 |
|---:|---|---|---|---|---|---|---|
| 1 | Ldbt_Key | uniqueidentifier | NOT NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 2 | Break_Date | datetime | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 3 | Break_Number | smallint | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 4 | Break_Start_Time | datetime | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 5 | Break_End_Time | datetime | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 6 | Break_Allow_To_Send_Hanger | bit | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 7 | Break_Automatic_Logout | bit | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 8 | LineId | int | NOT NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 9 | DelFlag | int | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 10 | GroupId | int | NULL | — | DEFAULT ((0)) | — | 暂无字段级释义；待查同步配置/厂商定义 |

### dbo.P_Line_Daily_Shift_Times（表，13 字段）

| 序号 | 字段 | SQL 类型 | 可空 | 标识/计算 | 默认值或计算表达式 | 数据库注释 | 释义证据状态 |
|---:|---|---|---|---|---|---|---|
| 1 | Ldst_Key | uniqueidentifier | NOT NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 2 | Shift_Date | datetime | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 3 | Shift_Number | smallint | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 4 | Shift_Start_Time | datetime | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 5 | Shift_End_Time | datetime | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 6 | Shift_Normal_Pay_Factor | real | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 7 | Shift_Hours_Before_Overtime | real | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 8 | Shift_Overtime_Pay_Factor1 | real | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 9 | Shift_Overtime_Pay_Factor2 | real | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 10 | Shift_Overtime_Pay_Factor3 | real | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 11 | LineId | int | NOT NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 12 | DelFlag | int | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 13 | GroupId | int | NULL | — | DEFAULT ((0)) | — | 暂无字段级释义；待查同步配置/厂商定义 |

### dbo.P_ODP_Detail（表，27 字段）

| 序号 | 字段 | SQL 类型 | 可空 | 标识/计算 | 默认值或计算表达式 | 数据库注释 | 释义证据状态 |
|---:|---|---|---|---|---|---|---|
| 1 | ODPD_Key | uniqueidentifier | NOT NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 2 | ODPD_ODP_Key | uniqueidentifier | NOT NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 3 | ODPD_Workstation | smallint | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 4 | ODPD_WC_Key | smallint | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 5 | ODPD_Quantity | smallint | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 6 | ODPD_ST_Key | int | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 7 | ODPD_Lot_Number | nvarchar(100) | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 8 | ODPD_OC_Key | int | NOT NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 9 | ODPD_Standard | real | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 10 | ODPD_Actual_Time | int | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 11 | ODPD_PA_Key | nvarchar(10) | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 12 | ODPD_Pay_Rate | real | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 13 | ODPD_Piece_Rate | real | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 14 | ODPD_Start_Time | datetime | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 15 | ODPD_CM_Key | int | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 16 | ODPD_SM_Key | int | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 17 | ODPD_Normal_Pay_Factor | real | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 18 | ODPD_Is_Overtime | bit | NOT NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 19 | ODPD_Overtime_Factor | real | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 20 | ODPD_Edited_By | nvarchar(10) | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 21 | ODPD_Edited_Date | datetime | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 22 | ODPD_Actual_Time_From_Reader | int | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 23 | ODPD_Sync_DateTime | datetime | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 24 | LineId | int | NOT NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 25 | DelFlag | int | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 26 | GroupId | int | NULL | — | DEFAULT ((0)) | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 27 | ODPD_STPO_Key | int | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |

### dbo.P_ODP_Master（表，17 字段）

| 序号 | 字段 | SQL 类型 | 可空 | 标识/计算 | 默认值或计算表达式 | 数据库注释 | 释义证据状态 |
|---:|---|---|---|---|---|---|---|
| 1 | ODP_Key | uniqueidentifier | NOT NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 2 | ODP_Date | datetime | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 3 | ODP_Shift | smallint | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 4 | ODP_EM_Key | int | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 5 | ODP_Actual_Clock_In | datetime | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 6 | ODP_Actual_Clock_Out | datetime | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 7 | ODP_Shift_Clock_In | datetime | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 8 | ODP_Shift_Clock_Out | datetime | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 9 | ODP_First_Hanger_Time | datetime | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 10 | ODP_Last_Hanger_Time | datetime | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 11 | ODP_Current_Station | smallint | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 12 | ODP_Lump_Sum_Payment | float | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 13 | ODP_Make_Up_Pay_Rate | real | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 14 | ODP_Last_Hanger_Start_Time | datetime | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 15 | LineId | int | NOT NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 16 | DelFlag | int | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 17 | GroupId | int | NULL | — | DEFAULT ((0)) | — | 暂无字段级释义；待查同步配置/厂商定义 |

### dbo.P_QC_Rework（表，23 字段）

| 序号 | 字段 | SQL 类型 | 可空 | 标识/计算 | 默认值或计算表达式 | 数据库注释 | 释义证据状态 |
|---:|---|---|---|---|---|---|---|
| 1 | QCR_Key | uniqueidentifier | NOT NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 2 | QCR_Defect_DateTime | datetime | NOT NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 3 | QCR_Defect_EM_Key | int | NOT NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 4 | QCR_Defect_ST_Key | int | NOT NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 5 | QCR_Defect_OC_Key | int | NOT NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 6 | QCR_Sent_To_Rework_By_EM_Key | int | NOT NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 7 | QCR_Defect_Quantity | smallint | NOT NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 8 | QCR_From_QC_Station | smallint | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 9 | QCR_HM_ID | nvarchar(12) | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 10 | QCR_QC_DateTime | datetime | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 11 | QCR_Repair_EM_Key | int | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 12 | QCR_Repair_DateTime | datetime | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 13 | QCR_Repair_Quantity | int | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 14 | QCR_Defect_CM_Key | int | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 15 | QCR_Defect_SM_Key | int | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 16 | QCR_QCSC_Key | uniqueidentifier | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 17 | QCR_HM_Key | uniqueidentifier | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 18 | QCR_STPO_Key | int | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 19 | LineId | int | NOT NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 20 | DelFlag | int | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 21 | QCR_AutoId | int | NOT NULL | IDENTITY | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 22 | GroupId | int | NULL | — | DEFAULT ((0)) | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 23 | QCR_QC_OC_Key | int | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |

### dbo.P_QC_Scrap（表，14 字段）

| 序号 | 字段 | SQL 类型 | 可空 | 标识/计算 | 默认值或计算表达式 | 数据库注释 | 释义证据状态 |
|---:|---|---|---|---|---|---|---|
| 1 | QCS_Key | uniqueidentifier | NOT NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 2 | QCS_Defect_DateTime | datetime | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 3 | QCS_EM_Key | int | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 4 | QCS_ST_Key | int | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 5 | QCS_OC_Key | int | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 6 | QCS_Sent_To_Scrap_By_EM_Key | int | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 7 | QCS_HM_ID | nvarchar(50) | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 8 | QCS_Scrap_DateTime | datetime | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 9 | QCS_QCSC_Key | uniqueidentifier | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 10 | QCS_STPO_Key | int | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 11 | LineId | int | NOT NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 12 | DelFlag | int | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 13 | GroupId | int | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 14 | QCS_HM_Key | uniqueidentifier | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |

### dbo.P_Style_Planned_Orders（表，20 字段）

| 序号 | 字段 | SQL 类型 | 可空 | 标识/计算 | 默认值或计算表达式 | 数据库注释 | 释义证据状态 |
|---:|---|---|---|---|---|---|---|
| 1 | STPO_Key | int | NOT NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 2 | STPO_ST_Key | int | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 3 | STPO_ID | nvarchar(50) | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 4 | STPO_CI_Name | nvarchar(50) | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 5 | STPO_PlannedQuantity | int | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 6 | STPO_Comment | nvarchar(50) | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 7 | STPO_CreateDate | datetime | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 8 | STPO_ModifiedDate | datetime | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 9 | STPO_OM_ID | nvarchar(50) | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 10 | STPO_PlanStartDate | datetime | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 11 | STPO_PlanFinishDate | datetime | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 12 | STPO_SM_Key | int | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 13 | STPO_CM_Key | int | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 14 | STPO_Level | int | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 15 | STPO_LP_Key | uniqueidentifier | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 16 | STPO_Level_Description | nvarchar(25) | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 17 | STPO_Completed | bit | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 18 | LineId | int | NOT NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 19 | DelFlag | int | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 20 | GroupId | int | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |

### dbo.P_Style_Planned_Orders_Quantity_Loaded（表，6 字段）

| 序号 | 字段 | SQL 类型 | 可空 | 标识/计算 | 默认值或计算表达式 | 数据库注释 | 释义证据状态 |
|---:|---|---|---|---|---|---|---|
| 1 | STPO_Key | int | NOT NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 2 | STPO_Line | smallint | NOT NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 3 | STPO_Quantity_Loaded | int | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 4 | LineId | int | NOT NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 5 | DelFlag | int | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 6 | GroupId | int | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |

### dbo.P_Style_Planned_Orders_Quantity_Registered（表，6 字段）

| 序号 | 字段 | SQL 类型 | 可空 | 标识/计算 | 默认值或计算表达式 | 数据库注释 | 释义证据状态 |
|---:|---|---|---|---|---|---|---|
| 1 | STPR_Key | int | NOT NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 2 | STPR_Line | smallint | NOT NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 3 | STPR_Quantity_Registered | int | NOT NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 4 | LineId | int | NOT NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 5 | DelFlag | int | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 6 | GroupId | int | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |

### dbo.T_ColInfo（表，5 字段）

| 序号 | 字段 | SQL 类型 | 可空 | 标识/计算 | 默认值或计算表达式 | 数据库注释 | 释义证据状态 |
|---:|---|---|---|---|---|---|---|
| 1 | ScolId | nvarchar(50) | NOT NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 2 | DbId | nvarchar(50) | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 3 | StableId | nvarchar(50) | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 4 | ScolName | nvarchar(50) | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 5 | ScolKey | int | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |

### dbo.T_DbInfo（表，9 字段）

| 序号 | 字段 | SQL 类型 | 可空 | 标识/计算 | 默认值或计算表达式 | 数据库注释 | 释义证据状态 |
|---:|---|---|---|---|---|---|---|
| 1 | DbId | nvarchar(50) | NOT NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 2 | DbName | nvarchar(50) | NULL | — | — | 数据库名称 | 仅有数据库注释；业务范围/取值仍需确认 |
| 3 | DbSource | nvarchar(50) | NULL | — | — | 数据库地址 | 仅有数据库注释；业务范围/取值仍需确认 |
| 4 | DbUser | nvarchar(50) | NULL | — | — | 数据库用户 | 仅有数据库注释；业务范围/取值仍需确认 |
| 5 | DbPassword | nvarchar(50) | NULL | — | — | 数据库用户密码 | 仅有数据库注释；业务范围/取值仍需确认 |
| 6 | LineId | int | NULL | — | — | 线号 | 仅有数据库注释；业务范围/取值仍需确认 |
| 7 | LineName | nvarchar(50) | NULL | — | — | 线名称 | 仅有数据库注释；业务范围/取值仍需确认 |
| 8 | Enable | int | NULL | — | DEFAULT ((0)) | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 9 | GroupId | int | NULL | — | DEFAULT ((0)) | — | 暂无字段级释义；待查同步配置/厂商定义 |

### dbo.T_IDS_Version（表，3 字段）

| 序号 | 字段 | SQL 类型 | 可空 | 标识/计算 | 默认值或计算表达式 | 数据库注释 | 释义证据状态 |
|---:|---|---|---|---|---|---|---|
| 1 | DV_IDS_Version | varchar(50) | NOT NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 2 | DV_From_LineDb_Version | varchar(50) | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 3 | DV_To_LineDb_Version | varchar(50) | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |

### dbo.T_TableInfo（表，6 字段）

| 序号 | 字段 | SQL 类型 | 可空 | 标识/计算 | 默认值或计算表达式 | 数据库注释 | 释义证据状态 |
|---:|---|---|---|---|---|---|---|
| 1 | TableId | nvarchar(50) | NOT NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 2 | DbId | nvarchar(50) | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 3 | TableName | nvarchar(50) | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 4 | SelectSql | text | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 5 | StableName | nvarchar(50) | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 6 | SyncVersion | bigint | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |

### dbo.ToIna_Colour（表，4 字段）

| 序号 | 字段 | SQL 类型 | 可空 | 标识/计算 | 默认值或计算表达式 | 数据库注释 | 释义证据状态 |
|---:|---|---|---|---|---|---|---|
| 1 | TCO_Key | int | NOT NULL | IDENTITY | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 2 | TCO_StyleID | nvarchar(25) | NOT NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 3 | TCO_ColourID | nvarchar(10) | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 4 | TCO_Colour_Description | nvarchar(50) | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |

### dbo.ToIna_Employee（表，10 字段）

| 序号 | 字段 | SQL 类型 | 可空 | 标识/计算 | 默认值或计算表达式 | 数据库注释 | 释义证据状态 |
|---:|---|---|---|---|---|---|---|
| 1 | TIE_Key | int | NOT NULL | IDENTITY | — | Key码； | 见 §3.4 文档字段释义 |
| 2 | TIE_ID | int | NULL | — | — | 员工编号； | 见 §3.4 文档字段释义 |
| 3 | TIE_Name | nvarchar(50) | NULL | — | — | 员工姓名； | 见 §3.4 文档字段释义 |
| 4 | TIE_Sex | nvarchar(1) | NULL | — | — | 员工性别； | 见 §3.4 文档字段释义 |
| 5 | TIE_RFID | nvarchar(10) | NULL | — | — | 员工卡号； | 见 §3.4 文档字段释义 |
| 6 | TIE_Line | nvarchar(50) | NULL | — | — | 员工所属生产线号； | 见 §3.4 文档字段释义 |
| 7 | TIE_Workshop | nvarchar(25) | NULL | — | — | 员工所属生产车间； | 见 §3.4 文档字段释义 |
| 8 | TIE_Group | nvarchar(15) | NULL | — | — | 员工所属组别； | 见 §3.4 文档字段释义 |
| 9 | TIE_Department | nvarchar(15) | NULL | — | — | 员工所属部门； | 见 §3.4 文档字段释义 |
| 10 | TIE_SSN | nvarchar(15) | NULL | — | — | 员工身份唯一识别信息； | 见 §3.4 文档字段释义 |

### dbo.ToIna_Route_Map（表，8 字段）

| 序号 | 字段 | SQL 类型 | 可空 | 标识/计算 | 默认值或计算表达式 | 数据库注释 | 释义证据状态 |
|---:|---|---|---|---|---|---|---|
| 1 | TIRM_Key | int | NOT NULL | IDENTITY | — | Key码； | 见 §3.4 文档字段释义 |
| 2 | TIRM_TIS_StyleID | nvarchar(25) | NOT NULL | — | — | 款式ID | 见 §3.4 文档字段释义 |
| 3 | TIRM_TISO_Op_Code | nvarchar(25) | NOT NULL | — | — | 工序ID | 见 §3.4 文档字段释义 |
| 4 | TIRM_Line | nvarchar(50) | NULL | — | — | 生产线号； | 见 §3.4 文档字段释义 |
| 5 | TIRM_Station_Number | int | NOT NULL | — | — | 工作站号（须和对应线号的衣拿吊挂系统工作站号严格对应） | 见 §3.4 文档字段释义 |
| 6 | TIRM_Created_Date | datetime | NULL | — | — | 创建时间； | 见 §3.4 文档字段释义 |
| 7 | TIRM_Loading_Line | nvarchar(50) | NULL | — | — | 上裁线号； | 见 §3.4 文档字段释义 |
| 8 | TIRM_MapKey | nvarchar(50) | NULL | — | — | 路线图唯一码； | 见 §3.4 文档字段释义 |

### dbo.ToIna_Size（表，4 字段）

| 序号 | 字段 | SQL 类型 | 可空 | 标识/计算 | 默认值或计算表达式 | 数据库注释 | 释义证据状态 |
|---:|---|---|---|---|---|---|---|
| 1 | TSI_Key | int | NOT NULL | IDENTITY | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 2 | TSI_StyleID | nvarchar(25) | NOT NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 3 | TSI_SizeID | nvarchar(10) | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 4 | TSI_Size_Description | nvarchar(50) | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |

### dbo.ToIna_Style（表，8 字段）

| 序号 | 字段 | SQL 类型 | 可空 | 标识/计算 | 默认值或计算表达式 | 数据库注释 | 释义证据状态 |
|---:|---|---|---|---|---|---|---|
| 1 | TIS_Key | int | NOT NULL | IDENTITY | — | Key码； | 见 §3.4 文档字段释义 |
| 2 | TIS_StyleID | nvarchar(25) | NOT NULL | — | — | 款号：；“款式”唯一识别字段，不同款式不可重复 | 见 §3.4 文档字段释义 |
| 3 | TIS_StyleDescription | nvarchar(50) | NULL | — | — | 款式描述：；“款式”附加字段，例如中文描述 | 见 §3.4 文档字段释义 |
| 4 | TIS_StyleCollection | nvarchar(25) | NULL | — | — | “款式”附加字段，例如款式隶属的订单/制单号 | 见 §3.4 文档字段释义 |
| 5 | TIS_Used | bit | NULL | — | — | 是否已导入；0：未导入过；1：已导入过； | 见 §3.4 文档字段释义 |
| 6 | TIS_UsedTime | datetime | NULL | — | — | 导入时间； | 见 §3.4 文档字段释义 |
| 7 | TIS_Quantity | int | NULL | — | — | 款式生产数量 | 见 §3.4 文档字段释义 |
| 8 | TIS_Line | nvarchar(50) | NULL | — | — | 生产线号码 | 见 §3.4 文档字段释义 |

### dbo.ToIna_Style_Operations（表，10 字段）

| 序号 | 字段 | SQL 类型 | 可空 | 标识/计算 | 默认值或计算表达式 | 数据库注释 | 释义证据状态 |
|---:|---|---|---|---|---|---|---|
| 1 | TISO_Key | int | NOT NULL | IDENTITY | — | Key码； | 见 §3.4 文档字段释义 |
| 2 | TISO_StyleID | nvarchar(25) | NOT NULL | — | — | “款式”唯一识别字段，不同款式不可重复 | 见 §3.4 文档字段释义 |
| 3 | TISO_Op_Code | nvarchar(25) | NOT NULL | — | — | “工序”唯一识别字段，不同工序不可重复 | 见 §3.4 文档字段释义 |
| 4 | TISO_Op_Order | smallint | NOT NULL | — | — | “款式生产工序表”排序字段，或款式工序表内唯一识别 | 见 §3.4 文档字段释义 |
| 5 | TISO_Op_Name | nvarchar(35) | NULL | — | — | “工序”附加字段，工序描述 | 见 §3.4 文档字段释义 |
| 6 | TISO_Op_PieceRate | real | NULL | — | — | “工序”附加字段,工序单价 | 见 §3.4 文档字段释义 |
| 7 | TISO_Op_StandardTime | real | NULL | — | — | “工序”附加字段,标准工时 | 见 §3.4 文档字段释义 |
| 8 | TISO_Op_MachineType | nvarchar(25) | NULL | — | — | “工序”附加字段，使用车台类型。 | 见 §3.4 文档字段释义 |
| 9 | TISO_Op_IsOnline | bit | NULL | — | DEFAULT ((0)) | 线内/线外工序识别 | 见 §3.4 文档字段释义 |
| 10 | TISO_Line | nvarchar(50) | NULL | — | — | 线内/线外工序识别； | 仅有数据库注释；业务范围/取值仍需确认 |

### dbo.ToIna_Style_Planned_Orders（表，18 字段）

| 序号 | 字段 | SQL 类型 | 可空 | 标识/计算 | 默认值或计算表达式 | 数据库注释 | 释义证据状态 |
|---:|---|---|---|---|---|---|---|
| 1 | TSPO_Key | int | NOT NULL | IDENTITY | — | 自增Key | 见 §3.4 文档字段释义 |
| 2 | TSPO_ID | nvarchar(50) | NOT NULL | — | — | — | 见 §3.4 文档字段释义 |
| 3 | TSPO_StyleID | nvarchar(25) | NOT NULL | — | — | — | 见 §3.4 文档字段释义 |
| 4 | TSPO_CI_Name | nvarchar(50) | NULL | — | — | Customer Name | 见 §3.4 文档字段释义 |
| 5 | TSPO_PlannedQuantity | int | NULL | — | — | — | 见 §3.4 文档字段释义 |
| 6 | TSPO_Comments | nvarchar(255) | NULL | — | — | — | 见 §3.4 文档字段释义 |
| 7 | TSPO_PlanStartDate | datetime | NULL | — | — | — | 见 §3.4 文档字段释义 |
| 8 | TSPO_PlanFinishDate | datetime | NULL | — | — | — | 见 §3.4 文档字段释义 |
| 9 | TSPO_Level | int | NULL | — | — | — | 见 §3.4 文档字段释义 |
| 10 | TSPO_ColourID | nvarchar(10) | NULL | — | — | — | 见 §3.4 文档字段释义 |
| 11 | TSPO_Colour_Description | nvarchar(50) | NULL | — | — | — | 见 §3.4 文档字段释义 |
| 12 | TSPO_SizeID | nvarchar(10) | NULL | — | — | — | 见 §3.4 文档字段释义 |
| 13 | TSPO_Size_Description | nvarchar(50) | NULL | — | — | — | 见 §3.4 文档字段释义 |
| 14 | TSPO_CreateDate | datetime | NULL | — | — | — | 见 §3.4 文档字段释义 |
| 15 | TSPO_ModifiedDate | datetime | NULL | — | — | — | 见 §3.4 文档字段释义 |
| 16 | TSPO_Line | nvarchar(50) | NULL | — | — | — | 见 §3.4 文档字段释义 |
| 17 | TSPO_Used | bit | NULL | — | DEFAULT ((0)) | — | 见 §3.4 文档字段释义 |
| 18 | TSPO_UsedTime | datetime | NULL | — | — | — | 见 §3.4 文档字段释义 |

### dbo.report_daily_target（表，7 字段）

| 序号 | 字段 | SQL 类型 | 可空 | 标识/计算 | 默认值或计算表达式 | 数据库注释 | 释义证据状态 |
|---:|---|---|---|---|---|---|---|
| 1 | report_date | date | NOT NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 2 | line_no | smallint | NOT NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 3 | item_code | nvarchar(100) | NOT NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 4 | target_qty | int | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 5 | emp_cnt | int | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 6 | leader_name | nvarchar(50) | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 7 | update_time | datetime | NOT NULL | — | DEFAULT (getdate()) | — | 暂无字段级释义；待查同步配置/厂商定义 |

### dbo.report_user_pwd（表，6 字段）

| 序号 | 字段 | SQL 类型 | 可空 | 标识/计算 | 默认值或计算表达式 | 数据库注释 | 释义证据状态 |
|---:|---|---|---|---|---|---|---|
| 1 | emp_id | int | NOT NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 2 | pwd | varchar(100) | NOT NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 3 | is_leader | bit | NOT NULL | — | DEFAULT ((0)) | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 4 | group_name | nvarchar(50) | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 5 | display_name | nvarchar(50) | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 6 | update_time | datetime | NOT NULL | — | DEFAULT (getdate()) | — | 暂无字段级释义；待查同步配置/厂商定义 |

### dbo.V_Hanger_Routing_Details（视图，39 字段）

| 序号 | 字段 | SQL 类型 | 可空 | 标识/计算 | 默认值或计算表达式 | 数据库注释 | 释义证据状态 |
|---:|---|---|---|---|---|---|---|
| 1 | VHRD_HRD_Key | uniqueidentifier | NOT NULL | — | — | — | 见 §3.4 文档字段释义 |
| 2 | VHRD_HM_Lot_Number | nvarchar(100) | NULL | — | — | — | 见 §3.4 文档字段释义 |
| 3 | VHRD_HM_Item_Card | nvarchar(20) | NULL | — | — | — | 见 §3.4 文档字段释义 |
| 4 | VHRD_HM_Garments_Per_Hanger | smallint | NULL | — | — | — | 见 §3.4 文档字段释义 |
| 5 | VHRD_HRD_OC_Key | int | NULL | — | — | — | 见 §3.4 文档字段释义 |
| 6 | VHRD_HRD_EM_Key | int | NULL | — | — | — | 见 §3.4 文档字段释义 |
| 7 | VHRD_HRD_Line | smallint | NULL | — | — | — | 见 §3.4 文档字段释义 |
| 8 | VHRD_HRD_Workstation | smallint | NULL | — | — | — | 见 §3.4 文档字段释义 |
| 9 | VHRD_HRD_ODP_Credited | bit | NOT NULL | — | — | — | 见 §3.4 文档字段释义 |
| 10 | VHRD_HRD_Completed_Time | datetime | NULL | — | — | — | 见 §3.4 文档字段释义 |
| 11 | VHRD_Return_For_Repair | bit | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 12 | VHRD_HRD_Started_Time | datetime | NULL | — | — | — | 见 §3.4 文档字段释义 |
| 13 | VHRD_ST_ID | nvarchar(25) | NULL | — | — | — | 见 §3.4 文档字段释义 |
| 14 | VHRD_ST_Description | nvarchar(50) | NULL | — | — | — | 见 §3.4 文档字段释义 |
| 15 | VHRD_ST_Collection | nvarchar(25) | NULL | — | — | — | 见 §3.4 文档字段释义 |
| 16 | VHRD_STPO_ID | nvarchar(50) | NULL | — | — | — | 见 §3.4 文档字段释义 |
| 17 | VHRD_ST_Fabric | nvarchar(25) | NULL | — | — | — | 见 §3.4 文档字段释义 |
| 18 | VHRD_ST_Trim | nvarchar(25) | NULL | — | — | — | 见 §3.4 文档字段释义 |
| 19 | VHRD_OC_Description | nvarchar(255) | NULL | — | — | — | 见 §3.4 文档字段释义 |
| 20 | VHRD_OC_Standard_Time | real | NULL | — | — | — | 见 §3.4 文档字段释义 |
| 21 | VHRD_OC_Piece_Rate | real | NULL | — | — | — | 见 §3.4 文档字段释义 |
| 22 | VHRD_PC_Description | nvarchar(25) | NULL | — | — | — | 见 §3.4 文档字段释义 |
| 23 | VHRD_EM_FirstName | nvarchar(50) | NULL | — | — | — | 见 §3.4 文档字段释义 |
| 24 | VHRD_EM_LastName | nvarchar(50) | NULL | — | — | — | 见 §3.4 文档字段释义 |
| 25 | VHRD_EM_LCD_Name | nvarchar(15) | NULL | — | — | — | 见 §3.4 文档字段释义 |
| 26 | VHRD_STOP_Number | nvarchar(50) | NULL | — | — | — | 见 §3.4 文档字段释义 |
| 27 | VHRD_HM_ID | nvarchar(50) | NOT NULL | — | — | — | 见 §3.4 文档字段释义 |
| 28 | VHRD_HM_Loaded_On_Line | smallint | NULL | — | — | — | 见 §3.4 文档字段释义 |
| 29 | VHRD_HM_Current_Line | smallint | NULL | — | — | — | 见 §3.4 文档字段释义 |
| 30 | VHRD_HRD_Comments | nvarchar(255) | NULL | — | — | — | 见 §3.4 文档字段释义 |
| 31 | VHRD_CM_Short_Description | nvarchar(10) | NULL | — | — | — | 见 §3.4 文档字段释义 |
| 32 | VHRD_CM_Description | nvarchar(255) | NULL | — | — | — | 见 §3.4 文档字段释义 |
| 33 | VHRD_SM_Short_Description | nvarchar(10) | NULL | — | — | — | 见 §3.4 文档字段释义 |
| 34 | VHRD_SM_Description | nvarchar(255) | NULL | — | — | — | 见 §3.4 文档字段释义 |
| 35 | VHRD_HM_Current_Location_Type | nvarchar(1) | NULL | — | — | — | 见 §3.4；P/S/W 释义已记录，仍需核对现场值域 |
| 36 | VHRD_HM_Current_Location | smallint | NULL | — | — | — | 见 §3.4；文档释义疑似重复，待核实 |
| 37 | VHRD_STOP_Order | smallint | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 38 | VHRD_HM_Key | uniqueidentifier | NOT NULL | — | — | — | 见 §3.4 文档字段释义 |
| 39 | VHRD_EM_SSN | nvarchar(15) | NULL | — | — | — | 见 §3.4 文档字段释义 |

### dbo.V_ODP_Info（视图，42 字段）

| 序号 | 字段 | SQL 类型 | 可空 | 标识/计算 | 默认值或计算表达式 | 数据库注释 | 释义证据状态 |
|---:|---|---|---|---|---|---|---|
| 1 | ODPI_Key | uniqueidentifier | NOT NULL | — | — | — | 见 §3.4 文档字段释义 |
| 2 | ODPI_Date | datetime | NULL | — | — | — | 见 §3.4 文档字段释义 |
| 3 | ODPI_EM_Key | int | NULL | — | — | — | 见 §3.4 文档字段释义 |
| 4 | ODPI_EM_FirstName | nvarchar(50) | NULL | — | — | — | 见 §3.4 文档字段释义 |
| 5 | ODPI_EM_LastName | nvarchar(50) | NULL | — | — | — | 见 §3.4 文档字段释义 |
| 6 | ODPI_EM_LCD_Name | nvarchar(15) | NULL | — | — | — | 见 §3.4；文档字段名为 `ODPI_LCD_Name`，已按视图表达式对应 |
| 7 | ODPI_Workstation | smallint | NULL | — | — | — | 见 §3.4 文档字段释义 |
| 8 | ODPI_Quantity | smallint | NULL | — | — | — | 见 §3.4 文档字段释义 |
| 9 | ODPI_ST_ID | nvarchar(25) | NULL | — | — | — | 见 §3.4 文档字段释义 |
| 10 | ODPI_ST_Description | nvarchar(50) | NULL | — | — | — | 见 §3.4 文档字段释义 |
| 11 | ODPI_Lot_Number | nvarchar(100) | NULL | — | — | — | 见 §3.4 文档字段释义 |
| 12 | ODPI_OC_Key | int | NOT NULL | — | — | — | 见 §3.4 文档字段释义 |
| 13 | ODPI_OC_Description | nvarchar(255) | NULL | — | — | — | 见 §3.4 文档字段释义 |
| 14 | ODPI_CM_Short_Description | nvarchar(10) | NULL | — | — | — | 见 §3.4 文档字段释义 |
| 15 | ODPI_SM_Short_Description | nvarchar(10) | NULL | — | — | — | 见 §3.4 文档字段释义 |
| 16 | ODPI_Piece_Rate | real | NULL | — | — | — | 见 §3.4 文档字段释义 |
| 17 | ODPI_OC_Standard_Time | real | NULL | — | — | — | 见 §3.4 文档字段释义 |
| 18 | ODPI_Actual_Time | int | NULL | — | — | — | 见 §3.4 文档字段释义 |
| 19 | ODPI_STOP_Order | smallint | NULL | — | — | — | 见 §3.4 文档字段释义 |
| 20 | ODPI_PC_Description | nvarchar(25) | NULL | — | — | — | 见 §3.4 文档字段释义 |
| 21 | ODPI_ST_Collection | nvarchar(25) | NULL | — | — | — | 见 §3.4 文档字段释义 |
| 22 | ODPI_CM_Description | nvarchar(255) | NULL | — | — | — | 见 §3.4 文档字段释义 |
| 23 | ODPI_SM_Description | nvarchar(255) | NULL | — | — | — | 见 §3.4 文档字段释义 |
| 24 | ODPI_OC_PA_Key | nvarchar(10) | NULL | — | — | — | 见 §3.4 文档字段释义 |
| 25 | ODPI_ODPD_PA_Key | nvarchar(10) | NULL | — | — | — | 见 §3.4 文档字段释义 |
| 26 | ODPI_PA_CurrencyValue | real | NULL | — | — | — | 见 §3.4 文档字段释义 |
| 27 | ODPI_ODPD_Is_Overtime | bit | NOT NULL | — | — | — | 见 §3.4 文档字段释义 |
| 28 | ODPI_MC_Type | nvarchar(25) | NULL | — | — | — | 见 §3.4 文档字段释义 |
| 29 | ODPI_STOP_Number | nvarchar(50) | NULL | — | — | — | 见 §3.4 文档字段释义 |
| 30 | ODPI_OC_Piece_Rate_Additional | real | NULL | — | — | — | 见 §3.4 文档字段释义 |
| 31 | ODPI_ODP_Actual_Clock_In | datetime | NULL | — | — | — | 见 §3.4 文档字段释义 |
| 32 | ODPI_ODP_Actual_Clock_Out | datetime | NULL | — | — | — | 见 §3.4 文档字段释义 |
| 33 | ODPI_EM_City | nvarchar(25) | NULL | — | — | — | 见 §3.4 文档字段释义 |
| 34 | ODPI_ST_Fabric | nvarchar(25) | NULL | — | — | — | 见 §3.4 文档字段释义 |
| 35 | ODPI_EM_RFID | nvarchar(10) | NULL | — | — | — | 见 §3.4 文档字段释义 |
| 36 | ODPI_EM_Department | nvarchar(15) | NULL | — | — | — | 见 §3.4 文档字段释义 |
| 37 | ODPI_EM_SSN | nvarchar(15) | NULL | — | — | — | 见 §3.4 文档字段释义 |
| 38 | ODPI_Start_Time | datetime | NULL | — | — | — | 见 §3.4 文档字段释义 |
| 39 | ODPI_ODP_LineNumber | int | NULL | — | — | — | 见 §3.4 文档字段释义 |
| 40 | ODPI_ODP_Shift | smallint | NULL | — | — | — | 暂无字段级释义；待查同步配置/厂商定义 |
| 41 | ODPI_STPO_ID | nvarchar(50) | NULL | — | — | — | 见 §3.4 文档字段释义 |
| 42 | GroupId | int | NULL | — | — | — | 见 §3.4 文档字段释义 |

### dbo.V_QC_Rework_Info（视图，42 字段）

| 序号 | 字段 | SQL 类型 | 可空 | 标识/计算 | 默认值或计算表达式 | 数据库注释 | 释义证据状态 |
|---:|---|---|---|---|---|---|---|
| 1 | QCRI_Key | uniqueidentifier | NOT NULL | — | — | — | 见 §3.4 文档字段释义 |
| 2 | QCRI_Defect_EM_Key | int | NOT NULL | — | — | — | 见 §3.4 文档字段释义 |
| 3 | QCRI_Defect_EM_FirstName | nvarchar(50) | NULL | — | — | — | 见 §3.4 文档字段释义 |
| 4 | QCRI_Defect_EM_LastName | nvarchar(50) | NULL | — | — | — | 见 §3.4 文档字段释义 |
| 5 | QCRI_Defect_DateTime | datetime | NOT NULL | — | — | — | 见 §3.4 文档字段释义 |
| 6 | QCRI_Defect_Quantity | smallint | NOT NULL | — | — | — | 见 §3.4 文档字段释义 |
| 7 | QCRI_ST_ID | nvarchar(25) | NULL | — | — | — | 见 §3.4 文档字段释义 |
| 8 | QCRI_ST_Description | nvarchar(50) | NULL | — | — | — | 见 §3.4 文档字段释义 |
| 9 | QCRI_ST_Collection | nvarchar(25) | NULL | — | — | — | 见 §3.4 文档字段释义 |
| 10 | QCRI_STPO_ID | nvarchar(50) | NULL | — | — | — | 见 §3.4；文档字段名为 `ODPI_STPO_ID`，已按视图表达式对应 |
| 11 | QCRI_OC_Key | int | NOT NULL | — | — | — | 见 §3.4 文档字段释义 |
| 12 | QCRI_OC_Description | nvarchar(255) | NULL | — | — | — | 见 §3.4 文档字段释义 |
| 13 | QCRI_PC_Description | nvarchar(25) | NULL | — | — | — | 见 §3.4 文档字段释义 |
| 14 | QCRI_STOP_Number | nvarchar(50) | NULL | — | — | — | 见 §3.4 文档字段释义 |
| 15 | QCRI_CM_Short_Description | nvarchar(10) | NULL | — | — | — | 见 §3.4 文档字段释义 |
| 16 | QCRI_CM_Description | nvarchar(255) | NULL | — | — | — | 见 §3.4 文档字段释义 |
| 17 | QCRI_SM_Short_Description | nvarchar(10) | NULL | — | — | — | 见 §3.4 文档字段释义 |
| 18 | QCRI_SM_Description | nvarchar(255) | NULL | — | — | — | 见 §3.4 文档字段释义 |
| 19 | QCRI_QCSC_Key | uniqueidentifier | NULL | — | — | — | 见 §3.4 文档字段释义 |
| 20 | QCRI_QCSC_Description | nvarchar(255) | NULL | — | — | — | 见 §3.4 文档字段释义 |
| 21 | QCRI_QCSC_Is_Rework | bit | NULL | — | — | — | 见 §3.4 文档字段释义 |
| 22 | QCRI_QC_EM_Key | int | NOT NULL | — | — | — | 见 §3.4；视图源列名角色不一致，待厂商确认 |
| 23 | QCRI_QC_EM_FirstName | nvarchar(50) | NULL | — | — | — | 见 §3.4 文档字段释义 |
| 24 | QCRI_QC_EM_LastName | nvarchar(50) | NULL | — | — | — | 见 §3.4 文档字段释义 |
| 25 | QCRI_QC_Station | smallint | NULL | — | — | — | 见 §3.4 文档字段释义 |
| 26 | QCRI_QC_DateTime | datetime | NULL | — | — | — | 见 §3.4 文档字段释义 |
| 27 | QCRI_Repair_EM_Key | int | NULL | — | — | — | 见 §3.4 文档字段释义 |
| 28 | QCRI_Repair_EM_FirstName | nvarchar(50) | NULL | — | — | — | 见 §3.4 文档字段释义 |
| 29 | QCRI_Repair_EM_LastName | nvarchar(50) | NULL | — | — | — | 见 §3.4 文档字段释义 |
| 30 | QCRI_Repair_DateTime | datetime | NULL | — | — | — | 见 §3.4 文档字段释义 |
| 31 | QCRI_Repair_Quantity | int | NULL | — | — | — | 见 §3.4 文档字段释义 |
| 32 | QCRI_QC_EM_Department | nvarchar(15) | NULL | — | — | — | 见 §3.4 文档字段释义 |
| 33 | QCRI_QC_EM_SSN | nvarchar(15) | NULL | — | — | — | 见 §3.4 文档字段释义 |
| 34 | QCRI_Defect_EM_Department | nvarchar(15) | NULL | — | — | — | 见 §3.4 文档字段释义 |
| 35 | QCRI_Defect_EM_SSN | nvarchar(15) | NULL | — | — | — | 见 §3.4 文档字段释义 |
| 36 | QCRI_Repair_EM_Department | nvarchar(15) | NULL | — | — | — | 见 §3.4 文档字段释义 |
| 37 | QCRI_Repair_EM_SSN | nvarchar(15) | NULL | — | — | — | 见 §3.4 文档字段释义 |
| 38 | QCRI_Defect_EM_LCD_Name | nvarchar(15) | NULL | — | — | — | 见 §3.4 文档字段释义 |
| 39 | QCRI_QC_EM_LCD_Name | nvarchar(15) | NULL | — | — | — | 见 §3.4 文档字段释义 |
| 40 | QCRI_Repair_EM_LCD_Name | nvarchar(15) | NULL | — | — | — | 见 §3.4 文档字段释义 |
| 41 | QCRI_LineNumber | smallint | NULL | — | — | — | 见 §3.4 文档字段释义 |
| 42 | GroupId | int | NULL | — | — | — | 见 §3.4 文档字段释义 |



## 8. 全部索引

复合索引按键序列列出；INCLUDE 列不参与排序/唯一性判断。过滤条件、禁用状态来自当前索引定义。

| 表 | 索引名 | 类型 | 主键 | 唯一 | 禁用 | 键列（顺序） | INCLUDE 列 | 过滤条件 |
|---|---|---|---|---|---|---|---|---|
| dbo.B_Classified_Scheme | PK_B_Classified_Scheme | CLUSTERED | 是 | 是 | 否 | CS_Key, CS_CM_Key, CS_SM_Key, CS_Lot_Number, LineId | — | — |
| dbo.B_Colour_Master | PK__B_Colour__42477AE0040707D9 | CLUSTERED | 是 | 是 | 否 | CM_Key, LineId | — | — |
| dbo.B_Employee_Master | PK__B_Employ__11512D4EF272956D | CLUSTERED | 是 | 是 | 否 | EM_Key, LineId | — | — |
| dbo.B_Hanger_Registry | PK__B_Hanger__7EAA56CBE5A564F8 | CLUSTERED | 是 | 是 | 否 | HR_HM_ID, LineId | — | — |
| dbo.B_Line_Bridging_Information | PK_B_Line_Bridging_Information | CLUSTERED | 是 | 是 | 否 | From_Line, From_Station | — | — |
| dbo.B_Line_Information | PK_Line_Information | CLUSTERED | 是 | 是 | 否 | LineId | — | — |
| dbo.B_Line_Network_Setup | PK_B_Line_Network_Setup | CLUSTERED | 是 | 是 | 否 | LineId | — | — |
| dbo.B_Line_Pusher_Setup | PK_B_Line_Pusher_Setup | CLUSTERED | 是 | 是 | 否 | LineId | — | — |
| dbo.B_Line_System_Configuration | PK_B_Line_System_Configuration | CLUSTERED | 是 | 是 | 否 | LineId | — | — |
| dbo.B_Line_Wireless_Controller | PK_B_Line_Wireless_Controller | CLUSTERED | 是 | 是 | 否 | LWC_Group_Id, LineId | — | — |
| dbo.B_Line_Workstation_Setup | PK_B_Line_Workstation_Setup | CLUSTERED | 是 | 是 | 否 | Workstation, LineId | — | — |
| dbo.B_Lines_On_Network | PK_B_Lines_On_Network | CLUSTERED | 是 | 是 | 否 | Line_Number | — | — |
| dbo.B_Machine_Codes | PK__B_Machin__7578B7450E690323 | CLUSTERED | 是 | 是 | 否 | MC_Key, LineId | — | — |
| dbo.B_Operation_Codes | PK__B_Operat__4F24836E10E07F16 | CLUSTERED | 是 | 是 | 否 | OC_Key, LineId | — | — |
| dbo.B_Pay_Categories | PK__B_Pay_Ca__83F0A309D2E8B659 | CLUSTERED | 是 | 是 | 否 | PA_Key, LineId | — | — |
| dbo.B_Primary_Codes | PK__B_Primar__FC044ED0093F5D4E | CLUSTERED | 是 | 是 | 否 | PC_Key, LineId | — | — |
| dbo.B_QC_Header_Codes | PK__B_QC_Hea__0EA0D52F25F41A6C | CLUSTERED | 是 | 是 | 否 | QCHC_Key, LineId | — | — |
| dbo.B_QC_Sub_Codes | PK__B_QC_Sub__CA877ECE3FB891E6 | CLUSTERED | 是 | 是 | 否 | QCSC_Key, LineId | — | — |
| dbo.B_Size_Master | PK__B_Size_M__D9B8AD06897B1659 | CLUSTERED | 是 | 是 | 否 | SM_Key, LineId | — | — |
| dbo.B_Sorting_Information_By_Line | PK__B_Sortin__8EBDA8F11D0C414C | CLUSTERED | 是 | 是 | 否 | SIBL_SLSI_Key, LineId | — | — |
| dbo.B_Style_Colours_Not_Used | PK__B_Style___9D0DAE8FB159FFE3 | CLUSTERED | 是 | 是 | 否 | SCNU_ST_Key, SCNU_CM_Key, LineId | — | — |
| dbo.B_Style_Loading | PK__B_Style___2A29112C5629CD9C | CLUSTERED | 是 | 是 | 否 | SL_Station, LineId | — | — |
| dbo.B_Style_Loading_History | PK__B_Style___D4A324007B3B8791 | CLUSTERED | 是 | 是 | 否 | SL_Station, SL_Started_Loading_Date, LineId | — | — |
| dbo.B_Style_Loading_Sorting_Information | PK__B_Style___196AD3F9A23A3208 | CLUSTERED | 是 | 是 | 否 | SLSI_Key, LineId | — | — |
| dbo.B_Style_Master | PK__B_Style___F84E3722B24B2BC6 | CLUSTERED | 是 | 是 | 否 | ST_Key, LineId | — | — |
| dbo.B_Style_Operations | PK__B_Style___B5B1379F6501FCD8 | CLUSTERED | 是 | 是 | 否 | STOP_Key, LineId | — | — |
| dbo.B_Style_Operations_Master | PK__B_Style___81256CDD16644E42 | CLUSTERED | 是 | 是 | 否 | STOPM_Key, LineId | — | — |
| dbo.B_Style_Purchase_Orders | PK__B_Style___20BAF22153DA1810 | CLUSTERED | 是 | 是 | 否 | STPO_Key, LineId | — | — |
| dbo.B_Style_Route_Detail | PK__B_Style___3B780F2C0A8DA65C | CLUSTERED | 是 | 是 | 否 | SRD_Key, LineId | — | — |
| dbo.B_Style_Route_Master | PK__B_Style___CF6071D559904A2C | CLUSTERED | 是 | 是 | 否 | SRM_Key, LineId | — | — |
| dbo.B_Style_Sizes_Not_Used | PK__B_Style___A76D4281AEBCB04D | CLUSTERED | 是 | 是 | 否 | SSNU_ST_Key, SSNU_SM_Key, LineId | — | — |
| dbo.B_WorkShop_Information | PK_B_WorkShop_Information | CLUSTERED | 是 | 是 | 否 | WI_Key | — | — |
| dbo.B_Work_Codes | PK__B_Work_C__EB5FD0A656C7F2B9 | CLUSTERED | 是 | 是 | 否 | WC_Key, LineId | — | — |
| dbo.B_Workstation_Employee_Information | PK_Workstation_Employee_Information | CLUSTERED | 是 | 是 | 否 | WEI_Workstation, WEI_Position, LineId | — | — |
| dbo.P_Barcode_Info | PK_P_Barcode_Info | CLUSTERED | 是 | 是 | 否 | BCI_Key, LineId | — | — |
| dbo.P_Bridging_Hanger_Master | PK_P_Bridging_Hanger_Master | CLUSTERED | 是 | 是 | 否 | HM_ID | — | — |
| dbo.P_Bridging_Hanger_Routing_Details | PK_P_Bridging_Hanger_Routing_Details | CLUSTERED | 是 | 是 | 否 | HRD_Key | — | — |
| dbo.P_Clock_InOut_History | PK_P_Clock_InOut_History | CLUSTERED | 是 | 是 | 否 | CIOH_Key, LineId | — | — |
| dbo.P_Hanger_Master | PK_P_Hanger_Master | CLUSTERED | 是 | 是 | 否 | HM_Key | — | — |
| dbo.P_Hanger_Master | HM_ID | NONCLUSTERED | 否 | 否 | 否 | HM_ID | — | — |
| dbo.P_Hanger_Master | HM_Current_Line | NONCLUSTERED | 否 | 否 | 否 | HM_Current_Line | — | — |
| dbo.P_Hanger_Master | DelFlag | NONCLUSTERED | 否 | 否 | 否 | DelFlag | — | — |
| dbo.P_Hanger_Routing_Details | PK_P_Hanger_Routing_Details | CLUSTERED | 是 | 是 | 否 | HRD_Key | — | — |
| dbo.P_Line_Daily_Break_Times | PK_P_Line_Daily_Break_Times_1 | CLUSTERED | 是 | 是 | 否 | Ldbt_Key, LineId | — | — |
| dbo.P_Line_Daily_Shift_Times | PK_P_Line_Daily_Shift_Times | CLUSTERED | 是 | 是 | 否 | Ldst_Key, LineId | — | — |
| dbo.P_ODP_Detail | PK__P_ODP_De__EC71103AA1FA53FF | CLUSTERED | 是 | 是 | 否 | ODPD_Key, ODPD_ODP_Key, LineId | — | — |
| dbo.P_ODP_Master | PK__P_ODP_Ma__2E1CB1D6E87F2CEE | CLUSTERED | 是 | 是 | 否 | ODP_Key, LineId | — | — |
| dbo.P_QC_Rework | PK__P_QC_Rew__BD1562DA373B3228 | CLUSTERED | 是 | 是 | 否 | QCR_Key, LineId | — | — |
| dbo.P_QC_Scrap | PK_P_QC_Scrap | CLUSTERED | 是 | 是 | 否 | QCS_Key, LineId | — | — |
| dbo.P_Style_Planned_Orders | PK_P_Style_Planned_Orders | CLUSTERED | 是 | 是 | 否 | STPO_Key, LineId | — | — |
| dbo.P_Style_Planned_Orders_Quantity_Loaded | PK_P_Style_Planned_Orders_Quantity_Loaded | CLUSTERED | 是 | 是 | 否 | STPO_Key, LineId | — | — |
| dbo.P_Style_Planned_Orders_Quantity_Registered | PK_P_Style_Planned_Orders_Quantity_Registered | CLUSTERED | 是 | 是 | 否 | STPR_Key, LineId | — | — |
| dbo.T_ColInfo | PK__T_ColInf__7FF189E0DDFF315B | CLUSTERED | 是 | 是 | 否 | ScolId | — | — |
| dbo.T_DbInfo | PK__T_DbInfo__7D51ADE22BD406B6 | CLUSTERED | 是 | 是 | 否 | DbId | — | — |
| dbo.T_IDS_Version | PK_T_IDS_Version | CLUSTERED | 是 | 是 | 否 | DV_IDS_Version | — | — |
| dbo.T_TableInfo | PK__T_TableI__7D5F01EE0CC82A90 | CLUSTERED | 是 | 是 | 否 | TableId | — | — |
| dbo.ToIna_Colour | PK_ToIna_Colour | CLUSTERED | 是 | 是 | 否 | TCO_Key | — | — |
| dbo.ToIna_Employee | PK_ToIna_Employee | CLUSTERED | 是 | 是 | 否 | TIE_Key | — | — |
| dbo.ToIna_Route_Map | PK_ToIna_RouteMap | CLUSTERED | 是 | 是 | 否 | TIRM_Key | — | — |
| dbo.ToIna_Style | PK_ToIna_Style | CLUSTERED | 是 | 是 | 否 | TIS_Key | — | — |
| dbo.ToIna_Style_Operations | PK_Ina_Style_Operations | CLUSTERED | 是 | 是 | 否 | TISO_Key | — | — |
| dbo.ToIna_Style_Planned_Orders | aaaaaStyle_Purchase_Orders_PK | NONCLUSTERED | 是 | 是 | 否 | TSPO_Key | — | — |
| dbo.report_daily_target | PK_report_daily_target | CLUSTERED | 是 | 是 | 否 | report_date, line_no, item_code | — | — |
| dbo.report_user_pwd | PK_report_user_pwd | CLUSTERED | 是 | 是 | 否 | emp_id | — | — |

## 9. 约束与视图/约束依赖清单

### 外键

当前数据库没有 FOREIGN KEY 约束。视图 JOIN 逻辑不等价于数据库强制的数据完整性约束。

### 检查约束

| 对象 | 约束 | 定义 | 禁用 | 未信任 |
|---|---|---|---|---|
| dbo.ToIna_Employee | CK_ToIna_Employee | ([TIE_ID]>=(0) AND [TIE_ID]<=(999999)) | 否 | 否 |

### 视图直接依赖

| 引用方 | 被引用对象 | 绑定/歧义信息 |
|---|---|---|
| dbo.V_Hanger_Routing_Details | dbo.B_Colour_Master | schema_bound=否；ambiguous=否 |
| dbo.V_Hanger_Routing_Details | dbo.B_Employee_Master | schema_bound=否；ambiguous=否 |
| dbo.V_Hanger_Routing_Details | dbo.B_Line_Information | schema_bound=否；ambiguous=否 |
| dbo.V_Hanger_Routing_Details | dbo.B_Operation_Codes | schema_bound=否；ambiguous=否 |
| dbo.V_Hanger_Routing_Details | dbo.B_Primary_Codes | schema_bound=否；ambiguous=否 |
| dbo.V_Hanger_Routing_Details | dbo.B_Size_Master | schema_bound=否；ambiguous=否 |
| dbo.V_Hanger_Routing_Details | dbo.B_Style_Master | schema_bound=否；ambiguous=否 |
| dbo.V_Hanger_Routing_Details | dbo.B_Style_Operations | schema_bound=否；ambiguous=否 |
| dbo.V_Hanger_Routing_Details | dbo.B_Style_Operations_Master | schema_bound=否；ambiguous=否 |
| dbo.V_Hanger_Routing_Details | dbo.P_Hanger_Master | schema_bound=否；ambiguous=否 |
| dbo.V_Hanger_Routing_Details | dbo.P_Hanger_Routing_Details | schema_bound=否；ambiguous=否 |
| dbo.V_Hanger_Routing_Details | dbo.P_Style_Planned_Orders | schema_bound=否；ambiguous=否 |
| dbo.V_ODP_Info | dbo.B_Colour_Master | schema_bound=否；ambiguous=否 |
| dbo.V_ODP_Info | dbo.B_Employee_Master | schema_bound=否；ambiguous=否 |
| dbo.V_ODP_Info | dbo.B_Machine_Codes | schema_bound=否；ambiguous=否 |
| dbo.V_ODP_Info | dbo.B_Operation_Codes | schema_bound=否；ambiguous=否 |
| dbo.V_ODP_Info | dbo.B_Pay_Categories | schema_bound=否；ambiguous=否 |
| dbo.V_ODP_Info | dbo.B_Primary_Codes | schema_bound=否；ambiguous=否 |
| dbo.V_ODP_Info | dbo.B_Size_Master | schema_bound=否；ambiguous=否 |
| dbo.V_ODP_Info | dbo.B_Style_Master | schema_bound=否；ambiguous=否 |
| dbo.V_ODP_Info | dbo.B_Style_Operations | schema_bound=否；ambiguous=否 |
| dbo.V_ODP_Info | dbo.P_ODP_Detail | schema_bound=否；ambiguous=否 |
| dbo.V_ODP_Info | dbo.P_ODP_Master | schema_bound=否；ambiguous=否 |
| dbo.V_ODP_Info | dbo.P_Style_Planned_Orders | schema_bound=否；ambiguous=否 |
| dbo.V_QC_Rework_Info | dbo.B_Colour_Master | schema_bound=否；ambiguous=否 |
| dbo.V_QC_Rework_Info | dbo.B_Employee_Master | schema_bound=否；ambiguous=否 |
| dbo.V_QC_Rework_Info | dbo.B_Operation_Codes | schema_bound=否；ambiguous=否 |
| dbo.V_QC_Rework_Info | dbo.B_Primary_Codes | schema_bound=否；ambiguous=否 |
| dbo.V_QC_Rework_Info | dbo.B_QC_Sub_Codes | schema_bound=否；ambiguous=否 |
| dbo.V_QC_Rework_Info | dbo.B_Size_Master | schema_bound=否；ambiguous=否 |
| dbo.V_QC_Rework_Info | dbo.B_Style_Master | schema_bound=否；ambiguous=否 |
| dbo.V_QC_Rework_Info | dbo.B_Style_Operations | schema_bound=否；ambiguous=否 |
| dbo.V_QC_Rework_Info | dbo.P_QC_Rework | schema_bound=否；ambiguous=否 |
| dbo.V_QC_Rework_Info | dbo.P_Style_Planned_Orders | schema_bound=否；ambiguous=否 |
| dbo.CK_ToIna_Employee | dbo.ToIna_Employee | CHECK 约束依赖员工下发表 |

## 10. 缺失对象如何补齐：先确认类型，再决定变更方式

| 对象/字段类别 | 可考虑的实现方式 | 开工前必须确认 |
|---|---|---|
| `Tag_Info` | 不应先建同名表。已发现 IDS 内部候选表 `P_Barcode_Info` 和客户端副本中的 `Barcode_Info`→`P_Barcode_Info` 特殊同步代码；先核对实际运行客户端是否启用、字段映射是否等价，再决定复用、扩展或新建兼容对象。 | 实际运行机器的客户端配置；官方 `Tag_Info` 与内部 `P_Barcode_Info` 对应关系；颜色字段拆分、计划号、单位数和最大尾数的落点；写入/更新/删除和重复上传处理。 |
| `V_ProductData` | 先确认客户端是否支持将吊挂端视图或查询结果上传到 IDS；若不支持，再评估客户端可读源数据并生成结果，或在 IDS 建兼容视图。 | 官方公式、工资口径、工时单位、班次日期边界、返工是否计件、时区及撤销规则；ODPI 当前工价不等于历史快照。 |
| `V_ReworkData` / `V_ScrapData` | 先核对同步客户端是否有对应对象上传配置及来源；再决定由吊挂软件提供、客户端转换上传或在 IDS 发布兼容视图。 | 衣架数/返工数/修复数/报废数口径、责任员工与站位定义、质检员归属、同件多缺陷去重规则。 |
| VHRD 缺失的 7R 字段 | 视图投影缺失；`HM_ST_Key`、`HM_CM_Key`、`HM_SM_Key`、`HM_STPO_Key`、`HM_SRM_Key`、`HM_Production_Time`、`ST_Line` 底层列在本实例存在，可评估在自有兼容视图补列。 | 官方字段类型、同步工具是否依赖列序/精确列集、别名与空值语义、厂商升级是否覆盖自定义视图。 |
| QCRI 衣架关联 | `P_QC_Rework` 有 `QCR_HM_ID` 和 `QCR_HM_Key`，当前 QCRI 未投影；可评估扩展兼容视图。 | 两键在缺陷、返修、换架和复检场景的含义；是否稳定指向事件发生时衣架；消费者是否支持新增列。 |
| `ToIna_Size` 无主键/索引 | 若厂商支持自定义约束，先做重复数据清理和款号+尺码编码验证，再讨论唯一约束/索引。 | 导入工具是否依赖物理列序、重复尺码兼容要求，以及线体同步和升级影响。 |

以上是补齐路径评估，不是已授权的数据库变更。本轮没有执行加表、加列、改视图、建索引或客户端同步。运行主页面和手动同步页面截图确认客户端有线体同步活动、单库/全库同步及产量/衣架历史上传入口；参数设置截图展示通用运行选项；数据库设置截图显示按线体、按 `IHS`/`IHS_SHARED` 数据库维护源表名、服务器表名和查询语句的映射方式，并提供表映射增删改入口。新建/修改数据库窗口进一步显示连接项、线体归属、启用状态、连接测试与保存控件；“复表”进入源数据库/目标数据库复制窗口，但复制对象和覆盖规则尚未确认。用户确认各产线常规映射相同，因此无需为相同映射逐条收集其他线体截图；当前 IDS 数据也未见常规查询的线体差异。剩余重点是确认 `Barcode_Info` 是否通过独立配置启用、`PlanData`/返工/质检相关开关的作用，通过静态代码或厂商说明查明“复表”实际复制内容、同步版本游标语义及失败处理。之后再判断补表、视图或配置上传。不能仅复制 Excel 字段建表，因为客户端可能依赖查询语句、上传标志、状态位和重复记录处理规则。

## 11. 证据限制与下一步决策材料

- 本次已核对列、类型、空值、默认/计算列、数据库注释、索引、检查约束、外键、SQL 依赖及三个视图定义。对没有官方业务注释的字段，不能仅凭英文缩写声称已完全掌握语义；全字段清单保留数据库事实，缺少依据的业务含义需厂商确认。
- 未读取业务行明细、客户端日志或连接配置值，也未直接操作实际运行客户端的数据库设置页、服务器级 SQL Agent 作业、外部同步服务或 INA 产线库；数据库设置页的结论来自用户截图，且只覆盖 1 号线 `IHS`/`IHS_SHARED` 当前可见映射行和数据库新建/修改/复制对话框。运行主页面、手动同步页面、参数设置页面和数据库设置页面截图只证明截图显示内容；本轮没有执行任何同步按钮、测试连接、复制数据、保存配置或打开“配置文件”。当前 IDS 用户对象扫描未发现存储过程、函数和触发器；这些对象范围外的运行逻辑仍待查。用户已确认“对象缺失或没有同步记录代表未配置上传”及各产线的常规表映射规则一致；仍未确认的是特殊条码/视图通道、`SelectSql` 的特殊配置例外，以及同步配置的当前生效状态。
- 文档缺失的四个对象、VHRD/QCRI 投影差异与字段类型差异已分开列出。下一步要先决定是补齐 7R 通用兼容层，还是只满足 ERP 当前所需的 PO/生产追溯视图；两者变更范围和维护责任不同。

## 12. 资料来源

- [7R 标准公共数据库对接字典 V2.0](7R标准公共数据库对接字典说明V2.0.xlsx)
- [IDS 视图字段说明（2024-12-23）](IDS视图字段说明-20241223.xlsx)
- [衣拿吊挂 7R 与 IDS 中继库字段核对与理解](衣拿吊挂7R与IDS中继库字段核对与理解.md)
- [生产流程规划第 15.14 节](../../规划/系统与流程/生产流程规划.md)
