<!--
文件用途：提供 2026-11-24 当前 SQL Server IDS 中继库的完整对象、字段、依赖、索引、同步客户端配置判断及两份 Excel 说明差异快照。
文件版本：1.0
功能目的：让项目成员按实际中继库结构逐对象核对，区分物理对象缺失、视图投影缺失、字段别名/类型差异和可选接口能力。
设计思路：以 IDS 系统目录和视图定义为结构事实，以 7R/IDS Excel 为供应商文档事实；不把字段名推断写成已确认业务含义。
实现方法：只读枚举用户表、视图、列元数据、索引、约束和 SQL 依赖；比对两份 Excel 字段清单；对视图关联键做只输出聚合计数的重复组合检查；只从 IDS 同步元数据 SQL 中提取对象引用，不执行这些 SQL。
关键约束：本快照仅代表本次连接到的 IDS 实例；不查询或记录 T_DbInfo 中的连接口令值，不查询员工生产明细，不修改中继库。
依赖与引用：Docs/技术参考/数据库/7R标准公共数据库对接字典说明V2.0.xlsx；Docs/技术参考/数据库/IDS视图字段说明-20241223.xlsx；Docs/技术参考/数据库/衣拿吊挂7R与IDS中继库字段核对与理解.md；Docs/规划/系统与流程/生产流程规划.md 第 15.14 节。
当前责任人：JY
本次整理人员：Codex（智能体，依据 JY 授权核对）
本次整理时间：2026-11-24
最近修改人员：Codex（智能体）
最近修改时间：2026-11-24
修改标记：ZY-ERP-20261124-IDS中继库全量对象与字段核对；补充现场同步客户端配置口径
最近测试时间：2026-11-24
测试人员：Codex（智能体）
测试状态：数据库只读元数据与关联键聚合盘点；Excel/实际字段差异脚本比对；链接与 Markdown 差异检查通过；未进行 DDL、业务代码或产线运行测试。
-->
# IDS 中继库全量结构盘点与文档差异

> 本文是 2026-11-24 对当前 IDS 数据库所做的结构快照。用户补充确认：吊挂数据由独立客户端工具上传同步；文档对象在中继库不存在，或对象存在但没有同步记录，表示该对象尚未配置上传。后续应先核对客户端工具的上传对象/记录配置，再判断是否需要添加数据库结构。升级衣拿、同步软件或更换车间数据库后仍须重新比对。

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
- `T_TableInfo` 有 289 条同步元数据记录、43 个不同源表名、15 个数据库标识；41 个不同的 `SelectSql` 元数据文本对应常规表名，两个带 `_LS` 后缀的名称没有 `StableName`。这里只从 SQL 元数据解析对象引用名，没有执行或输出原文。
- `T_ColInfo` 当前为 0 行，不能提供逐字段映射说明。851 个字段中只有 49 个存在 SQL Server `MS_Description` 扩展属性，其余字段没有数据库注释。
- `T_DbInfo` 字段定义含 `DbPassword`，数据库注释将其标记为数据库用户密码。本次只检查字段定义，没有读取该表任何连接配置或口令值；该表应按敏感配置表处理。
- 目录中还存在 `report_user_pwd` 表。仅根据表名判断它可能承载报表账号凭据；本次未读取其任何记录，建议将其纳入敏感表权限核查。
- 系统目录登记的 34 条视图依赖都指向当前 IDS 的 `dbo` 对象，没有发现视图对其他数据库或服务器的静态依赖。外部线体同步关系另由 `T_TableInfo.SelectSql` 元数据记录。
- VHRD 关联风险已通过只返回聚合数的查询确认：底表有 `HRD_HM_Key`，但现有视图按可复用 `HM_ID + GroupId` 连接且未过滤 DelFlag；同组重复衣架号较多。视图回传的单行数、PO 和款色码需要验证后才能用于数量对账或单件追踪。

#### T_TableInfo 中登记的线体表同步名称

表中 `TableName` 是登记的源表名，`StableName` 是 IDS 侧稳定名称映射。普通映射通常在 7 个数据库标识下各登记一条；两条 `_LS` 特殊名称各一条，且没有 `StableName`。该登记表没有 `ToIna_*` 输入表、Tag_Info 或回传视图的映射记录。

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

## 2. 文档中定义但当前 IDS 实例没有的对象

| 文档对象 | 来源 | 当前 IDS 目录 | 结论与下一步判断 |
|---|---|---|---|
| `Tag_Info` | 7R Excel | 当前 IDS 数据库对象目录中不存在 | 按现场口径，说明标签表上传尚未配置。先检查同步客户端是否有该对象的上传项、映射和启用状态，再确认客户端是否负责建表，还是需先按官方结构预建。 |
| `V_ProductData` | 7R Excel | 当前 IDS 数据库对象目录中不存在 | 按现场口径，说明该数据对象上传尚未配置。检查客户端能否配置视图/查询结果上传及映射；同时核实它应是吊挂端视图、客户端生成结果还是 IDS 兼容视图。 |
| `V_ReworkData` | 7R Excel | 当前 IDS 数据库对象目录中不存在 | 按现场口径，说明返工数据上传尚未配置。除核对客户端对象映射外，还需确认返工聚合口径和字段来源。 |
| `V_ScrapData` | 7R Excel | 当前 IDS 数据库对象目录中不存在 | 按现场口径，说明报废数据上传尚未配置。除核对客户端对象映射外，还需确认报废聚合口径和字段来源。 |

这里的“对象目录中不存在”是通过 SQL Server 系统目录确认没有该表/视图；不是“对象存在但当前行数为 0”。本次未逐个查询所有现存对象的业务行数，因此不能把任何未做行数核验的空表状态写成已确认事实。用户已明确现场判断口径：对象缺失或没有同步记录，都意味着客户端上传尚未配置；实际配置项和开关需再从同步客户端核实。

### 缺失对象对应的文档字段

以下字段是 Excel 明确声明、但当前 IDS 没有同名对象承载的字段。

| 缺失对象 | 文档定义字段（含类型） |
|---|---|
| `Tag_Info` | TGI_Key (uniqueidentifier)；TGI_TagID (nvarchar(20))；TGI_PlannedOrders (nvarchar(50))；TGI_StyleID (nvarchar(25))；TGI_ColourShotDes (nvarchar(10))；TGI_ColourDes (nvarchar(50))；TGI_Size (nvarchar(10))；TGI_Dept (nvarchar(25))；TGI_InsertTime (datetime)；TGI_Used (bit)；TGI_Used_Count (int)；TGI_LotNumber (nvarchar(25))；TGI_Trim (nvarchar(25))；TGI_Quantity (int)；TGI_HM_Key (uniqueidentifier)；TGI_PerQuantity (int)；TGI_MaxMantissa (int) |
| `V_ProductData` | VPD_ProductDate (date)；VPD_EmployeeID (nvarchar(15))；VPD_EmployeeName (nvarchar(50))；VPD_LineID (nvarchar(50))；VPD_StationID (int)；VPD_StyleID (nvarchar(25))；VPD_StyleName (nvarchar(50))；VPD_StylePlanID (nvarchar(50))；VPD_ColourID (nvarchar(10))；VPD_ColourName (nvarchar(50))；VPD_SizeID (nvarchar(10))；VPD_SizeName (nvarchar(50))；VPD_ProcessID (nvarchar(25))；VPD_ProcessName (nvarchar(35))；VPD_Quantity (int)；VPD_Wages (decimal(18,2))；VPD_Hours (decimal(18,2)) |
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

前缀分类按对象名和视图引用关系做工程归类，不是厂商正式业务分类。完整对象清单、字段数和估算行数如下。

| 对象 | 类型 | 字段数 | 索引数 | 估算行数 | 初步归类 |
|---|---|---:|---:|---:|---|
| dbo.B_Classified_Scheme | 表 | 17 | 5 | 0* | 基础资料/产线配置 |
| dbo.B_Colour_Master | 表 | 6 | 2 | 118* | 基础资料/产线配置 |
| dbo.B_Employee_Master | 表 | 32 | 2 | 1,482* | 基础资料/产线配置 |
| dbo.B_Hanger_Registry | 表 | 4 | 2 | 0* | 基础资料/产线配置 |
| dbo.B_Line_Bridging_Information | 表 | 5 | 2 | 0* | 基础资料/产线配置 |
| dbo.B_Line_Information | 表 | 9 | 1 | 6* | 基础资料/产线配置 |
| dbo.B_Line_Network_Setup | 表 | 18 | 1 | 6* | 基础资料/产线配置 |
| dbo.B_Line_Pusher_Setup | 表 | 17 | 1 | 6* | 基础资料/产线配置 |
| dbo.B_Line_System_Configuration | 表 | 14 | 1 | 6* | 基础资料/产线配置 |
| dbo.B_Line_Wireless_Controller | 表 | 7 | 2 | 10* | 基础资料/产线配置 |
| dbo.B_Line_Workstation_Setup | 表 | 16 | 2 | 208* | 基础资料/产线配置 |
| dbo.B_Lines_On_Network | 表 | 5 | 1 | 6* | 基础资料/产线配置 |
| dbo.B_Machine_Codes | 表 | 9 | 2 | 30* | 基础资料/产线配置 |
| dbo.B_Operation_Codes | 表 | 14 | 2 | 4,351* | 基础资料/产线配置 |
| dbo.B_Pay_Categories | 表 | 7 | 2 | 48* | 基础资料/产线配置 |
| dbo.B_Primary_Codes | 表 | 7 | 2 | 258* | 基础资料/产线配置 |
| dbo.B_QC_Header_Codes | 表 | 6 | 2 | 12* | 基础资料/产线配置 |
| dbo.B_QC_Sub_Codes | 表 | 9 | 2 | 13* | 基础资料/产线配置 |
| dbo.B_Size_Master | 表 | 6 | 2 | 156* | 基础资料/产线配置 |
| dbo.B_Sorting_Information_By_Line | 表 | 7 | 2 | 0* | 基础资料/产线配置 |
| dbo.B_Style_Colours_Not_Used | 表 | 5 | 3 | 46* | 基础资料/产线配置 |
| dbo.B_Style_Loading | 表 | 19 | 2 | 7* | 基础资料/产线配置 |
| dbo.B_Style_Loading_History | 表 | 17 | 3 | 7,240* | 基础资料/产线配置 |
| dbo.B_Style_Loading_Sorting_Information | 表 | 9 | 2 | 0* | 基础资料/产线配置 |
| dbo.B_Style_Master | 表 | 18 | 2 | 268* | 基础资料/产线配置 |
| dbo.B_Style_Operations | 表 | 11 | 2 | 4,374* | 基础资料/产线配置 |
| dbo.B_Style_Operations_Master | 表 | 8 | 2 | 270* | 基础资料/产线配置 |
| dbo.B_Style_Purchase_Orders | 表 | 11 | 2 | 0* | 基础资料/产线配置 |
| dbo.B_Style_Route_Detail | 表 | 16 | 2 | 11,224* | 基础资料/产线配置 |
| dbo.B_Style_Route_Master | 表 | 10 | 2 | 399* | 基础资料/产线配置 |
| dbo.B_Style_Sizes_Not_Used | 表 | 5 | 3 | 7* | 基础资料/产线配置 |
| dbo.B_WorkShop_Information | 表 | 3 | 1 | 0* | 基础资料/产线配置 |
| dbo.B_Work_Codes | 表 | 12 | 2 | 36* | 基础资料/产线配置 |
| dbo.B_Workstation_Employee_Information | 表 | 20 | 3 | 0* | 基础资料/产线配置 |
| dbo.P_Barcode_Info | 表 | 16 | 2 | 0* | 生产运行与事务记录 |
| dbo.P_Bridging_Hanger_Master | 表 | 23 | 1 | 0* | 生产运行与事务记录 |
| dbo.P_Bridging_Hanger_Routing_Details | 表 | 18 | 1 | 0* | 生产运行与事务记录 |
| dbo.P_Clock_InOut_History | 表 | 8 | 2 | 0* | 生产运行与事务记录 |
| dbo.P_Hanger_Master | 表 | 29 | 4 | 222,726* | 生产运行与事务记录 |
| dbo.P_Hanger_Routing_Details | 表 | 21 | 1 | 2,976,545* | 生产运行与事务记录 |
| dbo.P_Line_Daily_Break_Times | 表 | 10 | 2 | 5,926* | 生产运行与事务记录 |
| dbo.P_Line_Daily_Shift_Times | 表 | 13 | 2 | 5,811* | 生产运行与事务记录 |
| dbo.P_ODP_Detail | 表 | 27 | 3 | 221,665* | 生产运行与事务记录 |
| dbo.P_ODP_Master | 表 | 17 | 2 | 48,509* | 生产运行与事务记录 |
| dbo.P_QC_Rework | 表 | 23 | 2 | 170,329* | 生产运行与事务记录 |
| dbo.P_QC_Scrap | 表 | 14 | 2 | 0* | 生产运行与事务记录 |
| dbo.P_Style_Planned_Orders | 表 | 20 | 2 | 859* | 生产运行与事务记录 |
| dbo.P_Style_Planned_Orders_Quantity_Loaded | 表 | 6 | 2 | 0* | 生产运行与事务记录 |
| dbo.P_Style_Planned_Orders_Quantity_Registered | 表 | 6 | 2 | 0* | 生产运行与事务记录 |
| dbo.T_ColInfo | 表 | 5 | 1 | 0* | IDS/同步元数据 |
| dbo.T_DbInfo | 表 | 9 | 1 | 14* | IDS/同步元数据 |
| dbo.T_IDS_Version | 表 | 3 | 1 | 1* | IDS/同步元数据 |
| dbo.T_TableInfo | 表 | 6 | 1 | 289* | IDS/同步元数据 |
| dbo.ToIna_Colour | 表 | 4 | 1 | 2* | ERP 向衣拿输入接口 |
| dbo.ToIna_Employee | 表 | 10 | 1 | 385* | ERP 向衣拿输入接口 |
| dbo.ToIna_Route_Map | 表 | 8 | 1 | 0* | ERP 向衣拿输入接口 |
| dbo.ToIna_Size | 表 | 4 | 0 | 2* | ERP 向衣拿输入接口 |
| dbo.ToIna_Style | 表 | 8 | 1 | 1,576* | ERP 向衣拿输入接口 |
| dbo.ToIna_Style_Operations | 表 | 10 | 1 | 578* | ERP 向衣拿输入接口 |
| dbo.ToIna_Style_Planned_Orders | 表 | 18 | 1 | 432* | ERP 向衣拿输入接口 |
| dbo.report_daily_target | 表 | 7 | 3 | 2* | 附属报表对象 |
| dbo.report_user_pwd | 表 | 6 | 1 | 6* | 附属报表对象 |
| dbo.V_Hanger_Routing_Details | 视图 | 39 | 0 | — | 视图 |
| dbo.V_ODP_Info | 视图 | 42 | 0 | — | 视图 |
| dbo.V_QC_Rework_Info | 视图 | 42 | 0 | — | 视图 |

## 7. 全字段清单

类型长度按 SQL Server 显示；NULL 表示允许为空；默认/计算表达式和 MS_Description 仅在数据库实际存在时列出。没有注释表示数据库没有注释，不表示字段没有业务意义。

### dbo.B_Classified_Scheme（表，17 字段）

| 序号 | 字段 | SQL 类型 | 可空 | 标识/计算 | 默认值或计算表达式 | 数据库注释 |
|---:|---|---|---|---|---|---|
| 1 | CS_Key | uniqueidentifier | NOT NULL | — | — | — |
| 2 | CS_CM_Key | int | NOT NULL | — | — | — |
| 3 | CS_SM_Key | int | NOT NULL | — | — | — |
| 4 | CS_Lot_Number | nvarchar(100) | NOT NULL | — | — | — |
| 5 | CS_ST_Key | int | NULL | — | — | — |
| 6 | CS_SRM_Key | int | NULL | — | — | — |
| 7 | CS_OC_Key | int | NULL | — | — | — |
| 8 | CS_Station_Number | smallint | NULL | — | — | — |
| 9 | CS_Line_Number | smallint | NULL | — | — | — |
| 10 | CS_Quantity_Loaded | int | NULL | — | — | — |
| 11 | CS_Quantity_Ordered | int | NULL | — | — | — |
| 12 | CS_Completed | bit | NULL | — | — | — |
| 13 | CS_Create_Time | datetime | NULL | — | — | — |
| 14 | CS_Classified_Order | smallint | NULL | — | — | — |
| 15 | LineId | int | NOT NULL | — | — | — |
| 16 | DelFlag | int | NULL | — | — | — |
| 17 | GroupId | int | NULL | — | — | — |

### dbo.B_Colour_Master（表，6 字段）

| 序号 | 字段 | SQL 类型 | 可空 | 标识/计算 | 默认值或计算表达式 | 数据库注释 |
|---:|---|---|---|---|---|---|
| 1 | CM_Key | int | NOT NULL | — | — | — |
| 2 | CM_Short_Description | nvarchar(10) | NULL | — | — | — |
| 3 | CM_Description | nvarchar(255) | NULL | — | — | — |
| 4 | LineId | int | NOT NULL | — | — | — |
| 5 | DelFlag | int | NULL | — | — | — |
| 6 | GroupId | int | NULL | — | DEFAULT ((0)) | — |

### dbo.B_Employee_Master（表，32 字段）

| 序号 | 字段 | SQL 类型 | 可空 | 标识/计算 | 默认值或计算表达式 | 数据库注释 |
|---:|---|---|---|---|---|---|
| 1 | EM_Key | int | NOT NULL | — | — | b'\xe5\xb7\xa5\xe5\x8f\xb7' |
| 2 | EM_FirstName | nvarchar(50) | NULL | — | — | b'\xe5\xa7\x93' |
| 3 | EM_LastName | nvarchar(50) | NULL | — | — | b'\xe5\x90\x8d' |
| 4 | EM_LCD_Name | nvarchar(15) | NULL | — | — | b'\xe5\xa7\x93\xe5\x90\x8d' |
| 5 | EM_Sex | nvarchar(1) | NULL | — | — | — |
| 6 | EM_Department | nvarchar(15) | NULL | — | — | b'\xe9\x83\xa8\xe9\x97\xa8' |
| 7 | EM_SSN | nvarchar(15) | NULL | — | — | — |
| 8 | EM_GuaranteedPay | real | NULL | — | — | — |
| 9 | EM_AveragePay | real | NULL | — | — | — |
| 10 | EM_UserOnePay | real | NULL | — | — | — |
| 11 | EM_UserTwoPay | real | NULL | — | — | — |
| 12 | EM_Incentive | real | NULL | — | — | — |
| 13 | EM_Picture | image | NULL | — | — | — |
| 14 | EM_Address | nvarchar(50) | NULL | — | — | — |
| 15 | EM_City | nvarchar(25) | NULL | — | — | — |
| 16 | EM_Province | nvarchar(25) | NULL | — | — | — |
| 17 | EM_PostalCode | nvarchar(10) | NULL | — | — | — |
| 18 | EM_HomePhoneNumber | nvarchar(20) | NULL | — | — | — |
| 19 | EM_CellPhoneNumber | nvarchar(20) | NULL | — | — | — |
| 20 | EM_EmergencyContactName | nvarchar(50) | NULL | — | — | — |
| 21 | EM_EmergencyContactRelation | nvarchar(15) | NULL | — | — | — |
| 22 | EM_EmergencyContactPhone | nvarchar(20) | NULL | — | — | — |
| 23 | EM_BloodType | nvarchar(5) | NULL | — | — | — |
| 24 | EM_BirthDate | datetime | NULL | — | — | — |
| 25 | EM_DateHired | datetime | NULL | — | — | — |
| 26 | EM_TerminationDate | datetime | NULL | — | — | — |
| 27 | EM_RFID | nvarchar(10) | NULL | — | — | — |
| 28 | EM_Supervisor | bit | NULL | — | — | — |
| 29 | EM_Default_Target_Quantity | smallint | NULL | — | — | — |
| 30 | LineId | int | NOT NULL | — | — | — |
| 31 | DelFlag | int | NULL | — | — | — |
| 32 | GroupId | int | NULL | — | DEFAULT ((0)) | — |

### dbo.B_Hanger_Registry（表，4 字段）

| 序号 | 字段 | SQL 类型 | 可空 | 标识/计算 | 默认值或计算表达式 | 数据库注释 |
|---:|---|---|---|---|---|---|
| 1 | HR_HM_ID | nvarchar(12) | NOT NULL | — | — | — |
| 2 | LineId | int | NOT NULL | — | — | — |
| 3 | DelFlag | int | NULL | — | — | — |
| 4 | GroupId | int | NULL | — | DEFAULT ((0)) | — |

### dbo.B_Line_Bridging_Information（表，5 字段）

| 序号 | 字段 | SQL 类型 | 可空 | 标识/计算 | 默认值或计算表达式 | 数据库注释 |
|---:|---|---|---|---|---|---|
| 1 | From_Line | smallint | NOT NULL | — | — | — |
| 2 | From_Station | smallint | NOT NULL | — | — | — |
| 3 | To_Line | smallint | NULL | — | — | — |
| 4 | To_Station | smallint | NULL | — | — | — |
| 5 | GroupId | int | NULL | — | — | — |

### dbo.B_Line_Information（表，9 字段）

| 序号 | 字段 | SQL 类型 | 可空 | 标识/计算 | 默认值或计算表达式 | 数据库注释 |
|---:|---|---|---|---|---|---|
| 1 | Date_Of_Configuration | datetime | NULL | — | — | — |
| 2 | Line_Number | smallint | NULL | — | — | — |
| 3 | Line_Description | nvarchar(50) | NULL | — | — | — |
| 4 | Stations | smallint | NULL | — | — | — |
| 5 | LineId | int | NOT NULL | — | — | — |
| 6 | DelFlag | int | NULL | — | — | — |
| 7 | GroupId | int | NULL | — | DEFAULT ((0)) | — |
| 8 | LI_WI_Key | uniqueidentifier | NULL | — | — | — |
| 9 | Line_TargetQuantity | int | NULL | — | — | — |

### dbo.B_Line_Network_Setup（表，18 字段）

| 序号 | 字段 | SQL 类型 | 可空 | 标识/计算 | 默认值或计算表达式 | 数据库注释 |
|---:|---|---|---|---|---|---|
| 1 | Date_Of_Configuration | datetime | NOT NULL | — | — | — |
| 2 | Operating_System | nvarchar(20) | NULL | — | — | — |
| 3 | Interface | nvarchar(20) | NULL | — | — | — |
| 4 | Device_Name | nvarchar(20) | NULL | — | — | — |
| 5 | Cimetrics_Baud_Rate | int | NULL | — | — | — |
| 6 | Cimetrics_Retries | smallint | NULL | — | — | — |
| 7 | Cimetrics_Slave_Response | smallint | NULL | — | — | — |
| 8 | Cimetrics_Transmit_Timeout | smallint | NULL | — | — | — |
| 9 | Reader_Type | nvarchar(50) | NULL | — | — | — |
| 10 | Sensor_Hanger_Scan_COM_Port | smallint | NULL | — | — | — |
| 11 | Sensor_Hanger_Scan | bit | NULL | — | — | — |
| 12 | Console_Hardware | nvarchar(8) | NULL | — | — | — |
| 13 | Use_Mobile_Console | bit | NULL | — | — | — |
| 14 | Mobile_Console_Group_Number | smallint | NULL | — | — | — |
| 15 | Mobile_Console_Device_Number | smallint | NULL | — | — | — |
| 16 | LineId | int | NOT NULL | — | — | — |
| 17 | DelFlag | int | NULL | — | — | — |
| 18 | GroupId | int | NULL | — | — | — |

### dbo.B_Line_Pusher_Setup（表，17 字段）

| 序号 | 字段 | SQL 类型 | 可空 | 标识/计算 | 默认值或计算表达式 | 数据库注释 |
|---:|---|---|---|---|---|---|
| 1 | Date_Of_Configuration | datetime | NOT NULL | — | — | — |
| 2 | Units_Of_Measurement | nvarchar(10) | NULL | — | — | — |
| 3 | Number_of_Pushers | int | NULL | — | — | — |
| 4 | Distance_Between_Pushers | real | NULL | — | — | — |
| 5 | Time_between_pushers | int | NULL | — | — | — |
| 6 | Sensor_Timeout | int | NULL | — | — | — |
| 7 | InFeed_Duration | smallint | NULL | — | — | — |
| 8 | OutFeed_Duration | smallint | NULL | — | — | — |
| 9 | Pusher_Present_On_High_Bit | bit | NOT NULL | — | — | — |
| 10 | Interface_Output_Address | int | NULL | — | — | — |
| 11 | Interface_Input_Address | int | NULL | — | — | — |
| 12 | LineId | int | NOT NULL | — | — | — |
| 13 | GroupId | int | NULL | — | — | — |
| 14 | DelFlag | int | NULL | — | — | — |
| 15 | Ignore_Pushers | int | NULL | — | — | — |
| 16 | Pusher_Sensor_Type | smallint | NULL | — | — | — |
| 17 | Pusher_RFID_COM_Port | smallint | NULL | — | — | — |

### dbo.B_Line_System_Configuration（表，14 字段）

| 序号 | 字段 | SQL 类型 | 可空 | 标识/计算 | 默认值或计算表达式 | 数据库注释 |
|---:|---|---|---|---|---|---|
| 1 | Date_Of_Configuration | datetime | NOT NULL | — | — | — |
| 2 | Company_Name | nvarchar(255) | NULL | — | — | — |
| 3 | Company_Address | nvarchar(255) | NULL | — | — | — |
| 4 | Company_URL | nvarchar(255) | NULL | — | — | — |
| 5 | Company_Email | nvarchar(255) | NULL | — | — | — |
| 6 | Company_Logo | image | NULL | — | — | — |
| 7 | Payment_Type | smallint | NULL | — | — | — |
| 8 | Standard_Piece_Rate_Amount | real | NULL | — | — | — |
| 9 | Automatically_Calculate_Makeup | bit | NOT NULL | — | — | — |
| 10 | Makeup_To | nvarchar(10) | NULL | — | — | — |
| 11 | Non_Standard_Piece_Rate_Amount | real | NULL | — | — | — |
| 12 | LineId | int | NOT NULL | — | — | — |
| 13 | DelFlag | int | NULL | — | — | — |
| 14 | GroupId | int | NULL | — | — | — |

### dbo.B_Line_Wireless_Controller（表，7 字段）

| 序号 | 字段 | SQL 类型 | 可空 | 标识/计算 | 默认值或计算表达式 | 数据库注释 |
|---:|---|---|---|---|---|---|
| 1 | LWC_Group_Id | smallint | NOT NULL | — | — | — |
| 2 | LWC_COM_Port | smallint | NOT NULL | — | — | — |
| 3 | LWC_Baud_Rate | int | NOT NULL | — | — | — |
| 4 | LWC_Default_Timeout | smallint | NOT NULL | — | — | — |
| 5 | LineId | int | NOT NULL | — | — | — |
| 6 | DelFlag | int | NULL | — | — | — |
| 7 | GroupId | int | NULL | — | — | — |

### dbo.B_Line_Workstation_Setup（表，16 字段）

| 序号 | 字段 | SQL 类型 | 可空 | 标识/计算 | 默认值或计算表达式 | 数据库注释 |
|---:|---|---|---|---|---|---|
| 1 | Workstation | smallint | NOT NULL | — | — | — |
| 2 | Hub | smallint | NULL | — | — | — |
| 3 | Position | nvarchar(1) | NULL | — | — | — |
| 4 | In_Feed_Pushers_From_Sensor | smallint | NULL | — | — | — |
| 5 | In_Feed_Distance_To_Next_Pusher | real | NULL | — | — | — |
| 6 | Out_Feed_Pushers_From_Sensor | smallint | NULL | — | — | — |
| 7 | Out_Feed_Distance_To_Next_Pusher | real | NULL | — | — | — |
| 8 | Use_Wireless_Console | bit | NULL | — | — | — |
| 9 | Wireless_Console_Group | smallint | NULL | — | — | — |
| 10 | Cable_Console_Port | smallint | NULL | — | — | — |
| 11 | GroupId | int | NULL | — | — | — |
| 12 | LineId | int | NOT NULL | — | — | — |
| 13 | DelFlag | int | NULL | — | — | — |
| 14 | Multimedia_Terminal_IP | nvarchar(25) | NULL | — | — | — |
| 15 | Additional_Wireless_Console1_Group | smallint | NULL | — | — | — |
| 16 | Additional_Wireless_Console2_Group | smallint | NULL | — | — | — |

### dbo.B_Lines_On_Network（表，5 字段）

| 序号 | 字段 | SQL 类型 | 可空 | 标识/计算 | 默认值或计算表达式 | 数据库注释 |
|---:|---|---|---|---|---|---|
| 1 | Line_Number | smallint | NOT NULL | — | — | — |
| 2 | Line_Description | nvarchar(50) | NULL | — | — | — |
| 3 | Stations | smallint | NULL | — | — | — |
| 4 | Line_Type | nvarchar(4) | NULL | — | — | — |
| 5 | GroupId | int | NULL | — | — | — |

### dbo.B_Machine_Codes（表，9 字段）

| 序号 | 字段 | SQL 类型 | 可空 | 标识/计算 | 默认值或计算表达式 | 数据库注释 |
|---:|---|---|---|---|---|---|
| 1 | MC_Key | int | NOT NULL | — | — | — |
| 2 | MC_Name | nvarchar(15) | NULL | — | — | — |
| 3 | MC_Type | nvarchar(25) | NULL | — | — | — |
| 4 | MC_Gauge | nvarchar(5) | NULL | — | — | — |
| 5 | MC_Attachments | nvarchar(25) | NULL | — | — | — |
| 6 | MC_Quantity | smallint | NULL | — | — | — |
| 7 | LineId | int | NOT NULL | — | — | — |
| 8 | DelFlag | int | NULL | — | — | — |
| 9 | GroupId | int | NULL | — | DEFAULT ((0)) | — |

### dbo.B_Operation_Codes（表，14 字段）

| 序号 | 字段 | SQL 类型 | 可空 | 标识/计算 | 默认值或计算表达式 | 数据库注释 |
|---:|---|---|---|---|---|---|
| 1 | OC_Key | int | NOT NULL | — | — | — |
| 2 | OC_Sub_Code | smallint | NOT NULL | — | — | — |
| 3 | OC_PC_Key | smallint | NULL | — | — | — |
| 4 | OC_Description | nvarchar(255) | NULL | — | — | — |
| 5 | OC_Standard_Time | real | NULL | — | — | — |
| 6 | OC_Piece_Rate | real | NULL | — | — | — |
| 7 | OC_PA_Key | nvarchar(10) | NULL | — | — | — |
| 8 | OC_Online | bit | NOT NULL | — | — | — |
| 9 | OC_Non_Standard_Time | real | NULL | — | — | — |
| 10 | OC_Piece_Rate_Additional | real | NULL | — | — | — |
| 11 | OC_ID | nvarchar(50) | NULL | — | — | — |
| 12 | LineId | int | NOT NULL | — | — | — |
| 13 | DelFlag | int | NULL | — | DEFAULT ((0)) | — |
| 14 | GroupId | int | NULL | — | DEFAULT ((0)) | — |

### dbo.B_Pay_Categories（表，7 字段）

| 序号 | 字段 | SQL 类型 | 可空 | 标识/计算 | 默认值或计算表达式 | 数据库注释 |
|---:|---|---|---|---|---|---|
| 1 | PA_Key | nvarchar(10) | NOT NULL | — | — | — |
| 2 | PA_CategoryType | nvarchar(20) | NOT NULL | — | — | — |
| 3 | PA_Description | nvarchar(25) | NULL | — | — | — |
| 4 | PA_CurrencyValue | real | NULL | — | — | — |
| 5 | LineId | int | NOT NULL | — | — | — |
| 6 | DelFlag | int | NULL | — | — | — |
| 7 | GroupId | int | NULL | — | DEFAULT ((0)) | — |

### dbo.B_Primary_Codes（表，7 字段）

| 序号 | 字段 | SQL 类型 | 可空 | 标识/计算 | 默认值或计算表达式 | 数据库注释 |
|---:|---|---|---|---|---|---|
| 1 | PC_Key | smallint | NOT NULL | — | — | — |
| 2 | PC_Description | nvarchar(25) | NULL | — | — | — |
| 3 | PC_MC_Key | int | NULL | — | — | — |
| 4 | PC_ID | nvarchar(50) | NULL | — | — | — |
| 5 | LineId | int | NOT NULL | — | — | — |
| 6 | DelFlag | int | NULL | — | — | — |
| 7 | GroupId | int | NULL | — | DEFAULT ((0)) | — |

### dbo.B_QC_Header_Codes（表，6 字段）

| 序号 | 字段 | SQL 类型 | 可空 | 标识/计算 | 默认值或计算表达式 | 数据库注释 |
|---:|---|---|---|---|---|---|
| 1 | QCHC_Key | uniqueidentifier | NOT NULL | — | — | — |
| 2 | QCHC_Description | nvarchar(255) | NULL | — | — | — |
| 3 | QCHC_LCD | nvarchar(32) | NULL | — | — | — |
| 4 | LineId | int | NOT NULL | — | — | — |
| 5 | DelFlag | int | NULL | — | — | — |
| 6 | GroupId | int | NULL | — | DEFAULT ((0)) | — |

### dbo.B_QC_Sub_Codes（表，9 字段）

| 序号 | 字段 | SQL 类型 | 可空 | 标识/计算 | 默认值或计算表达式 | 数据库注释 |
|---:|---|---|---|---|---|---|
| 1 | QCSC_Key | uniqueidentifier | NOT NULL | — | — | — |
| 2 | QCSC_QCHC_Key | uniqueidentifier | NULL | — | — | — |
| 3 | QCSC_Description | nvarchar(255) | NULL | — | — | — |
| 4 | QCSC_LCD | nvarchar(32) | NULL | — | — | — |
| 5 | QCSC_Number | int | NULL | — | — | — |
| 6 | QCSC_Is_Rework | bit | NULL | — | — | — |
| 7 | LineId | int | NOT NULL | — | — | — |
| 8 | DelFlag | int | NULL | — | — | — |
| 9 | GroupId | int | NULL | — | DEFAULT ((0)) | — |

### dbo.B_Size_Master（表，6 字段）

| 序号 | 字段 | SQL 类型 | 可空 | 标识/计算 | 默认值或计算表达式 | 数据库注释 |
|---:|---|---|---|---|---|---|
| 1 | SM_Key | int | NOT NULL | — | — | — |
| 2 | SM_Short_Description | nvarchar(10) | NULL | — | — | — |
| 3 | SM_Description | nvarchar(255) | NULL | — | — | — |
| 4 | LineId | int | NOT NULL | — | — | — |
| 5 | DelFlag | int | NULL | — | — | — |
| 6 | GroupId | int | NULL | — | DEFAULT ((0)) | — |

### dbo.B_Sorting_Information_By_Line（表，7 字段）

| 序号 | 字段 | SQL 类型 | 可空 | 标识/计算 | 默认值或计算表达式 | 数据库注释 |
|---:|---|---|---|---|---|---|
| 1 | SIBL_SLSI_Key | int | NOT NULL | — | — | — |
| 2 | SIBL_Line | smallint | NOT NULL | — | — | — |
| 3 | SIBL_Quantity | int | NOT NULL | — | — | — |
| 4 | SIBL_Min_Loaded_Time | datetime | NOT NULL | — | — | — |
| 5 | LineId | int | NOT NULL | — | — | — |
| 6 | DelFlag | int | NULL | — | — | — |
| 7 | GroupId | int | NULL | — | DEFAULT ((0)) | — |

### dbo.B_Style_Colours_Not_Used（表，5 字段）

| 序号 | 字段 | SQL 类型 | 可空 | 标识/计算 | 默认值或计算表达式 | 数据库注释 |
|---:|---|---|---|---|---|---|
| 1 | SCNU_ST_Key | int | NOT NULL | — | — | — |
| 2 | SCNU_CM_Key | int | NOT NULL | — | — | — |
| 3 | LineId | int | NOT NULL | — | — | — |
| 4 | DelFlag | int | NULL | — | — | — |
| 5 | GroupId | int | NULL | — | DEFAULT ((0)) | — |

### dbo.B_Style_Loading（表，19 字段）

| 序号 | 字段 | SQL 类型 | 可空 | 标识/计算 | 默认值或计算表达式 | 数据库注释 |
|---:|---|---|---|---|---|---|
| 1 | SL_Station | smallint | NOT NULL | — | — | — |
| 2 | SL_ST_Key | int | NULL | — | — | — |
| 3 | SL_STPO_Key | int | NULL | — | — | — |
| 4 | SL_Lot_Number | nvarchar(100) | NULL | — | — | — |
| 5 | SL_CM_Key | int | NULL | — | — | — |
| 6 | SL_SM_Key | int | NULL | — | — | — |
| 7 | SL_Garments_Per_Hanger | smallint | NULL | — | — | — |
| 8 | SL_SRM_Key | int | NULL | — | — | — |
| 9 | SL_Item_Card | nvarchar(20) | NULL | — | — | — |
| 10 | SL_Allowed_Out | bit | NULL | — | — | — |
| 11 | SL_SLSI_Key | int | NULL | — | — | — |
| 12 | SL_Sort_Before | bit | NULL | — | — | — |
| 13 | SL_Sort_Before_Operation | bit | NULL | — | — | — |
| 14 | SL_Sort_Before_OC_Key | int | NULL | — | — | — |
| 15 | SL_Manual_Bundle_Quantity | int | NULL | — | — | — |
| 16 | SL_Manual_Bundle_Remaining | int | NULL | — | — | — |
| 17 | LineId | int | NOT NULL | — | — | — |
| 18 | DelFlag | int | NULL | — | — | — |
| 19 | GroupId | int | NULL | — | DEFAULT ((0)) | — |

### dbo.B_Style_Loading_History（表，17 字段）

| 序号 | 字段 | SQL 类型 | 可空 | 标识/计算 | 默认值或计算表达式 | 数据库注释 |
|---:|---|---|---|---|---|---|
| 1 | SL_Station | smallint | NOT NULL | — | — | — |
| 2 | SL_Started_Loading_Date | datetime | NOT NULL | — | — | — |
| 3 | SL_ST_Key | int | NULL | — | — | — |
| 4 | SL_STPO_Key | int | NULL | — | — | — |
| 5 | SL_Lot_Number | nvarchar(25) | NULL | — | — | — |
| 6 | SL_CM_Key | int | NULL | — | — | — |
| 7 | SL_SM_Key | int | NULL | — | — | — |
| 8 | SL_Garments_Per_Hanger | smallint | NULL | — | — | — |
| 9 | SL_SRM_Key | int | NULL | — | — | — |
| 10 | SL_Item_Card | nvarchar(20) | NULL | — | — | — |
| 11 | SL_SLSI_Key | int | NULL | — | — | — |
| 12 | SL_Sort_Before | bit | NULL | — | — | — |
| 13 | SL_Sort_Before_Operation | bit | NULL | — | — | — |
| 14 | SL_Sort_Before_OC_Key | int | NULL | — | — | — |
| 15 | LineId | int | NOT NULL | — | — | — |
| 16 | DelFlag | int | NULL | — | — | — |
| 17 | GroupId | int | NULL | — | DEFAULT ((0)) | — |

### dbo.B_Style_Loading_Sorting_Information（表，9 字段）

| 序号 | 字段 | SQL 类型 | 可空 | 标识/计算 | 默认值或计算表达式 | 数据库注释 |
|---:|---|---|---|---|---|---|
| 1 | SLSI_Key | int | NOT NULL | — | — | — |
| 2 | SLSI_Type | smallint | NOT NULL | — | — | — |
| 3 | SLSI_Active | bit | NOT NULL | — | — | — |
| 4 | SLSI_Description | nvarchar(50) | NOT NULL | — | — | — |
| 5 | SLSI_Date_Created | datetime | NOT NULL | — | — | — |
| 6 | SLSI_Before_OC_Key | int | NULL | — | — | — |
| 7 | LineId | int | NOT NULL | — | — | — |
| 8 | DelFlag | int | NULL | — | — | — |
| 9 | GroupId | int | NULL | — | DEFAULT ((0)) | — |

### dbo.B_Style_Master（表，18 字段）

| 序号 | 字段 | SQL 类型 | 可空 | 标识/计算 | 默认值或计算表达式 | 数据库注释 |
|---:|---|---|---|---|---|---|
| 1 | ST_Key | int | NOT NULL | — | — | — |
| 2 | ST_ID | nvarchar(25) | NOT NULL | — | — | — |
| 3 | ST_Description | nvarchar(50) | NULL | — | — | — |
| 4 | ST_Collection | nvarchar(25) | NULL | — | — | — |
| 5 | ST_Fabric | nvarchar(25) | NULL | — | — | — |
| 6 | ST_Trim | nvarchar(25) | NULL | — | — | — |
| 7 | ST_Logo_Name | nvarchar(25) | NULL | — | — | — |
| 8 | ST_Logo_Placement | nvarchar(25) | NULL | — | — | — |
| 9 | ST_Comments | ntext | NULL | — | — | — |
| 10 | ST_Picture | image | NULL | — | — | — |
| 11 | ST_Date_Created | datetime | NULL | — | — | — |
| 12 | ST_Last_Updated | datetime | NULL | — | — | — |
| 13 | ST_Line | smallint | NULL | — | — | — |
| 14 | ST_Bundle_Active_SRM_Key | int | NULL | — | — | — |
| 15 | LineId | int | NOT NULL | — | — | — |
| 16 | DelFlag | int | NULL | — | — | — |
| 17 | GroupId | int | NULL | — | DEFAULT ((0)) | — |
| 18 | ST_STOPM_Key | uniqueidentifier | NULL | — | — | — |

### dbo.B_Style_Operations（表，11 字段）

| 序号 | 字段 | SQL 类型 | 可空 | 标识/计算 | 默认值或计算表达式 | 数据库注释 |
|---:|---|---|---|---|---|---|
| 1 | STOP_Key | int | NOT NULL | — | — | — |
| 2 | STOP_ST_Key | int | NULL | — | — | — |
| 3 | STOP_OC_Key | int | NULL | — | — | — |
| 4 | STOP_Order | smallint | NULL | — | — | — |
| 5 | STOP_Alternate_Order | smallint | NULL | — | — | — |
| 6 | STOP_Number | nvarchar(50) | NULL | — | — | — |
| 7 | STOP_Alternate_Order_Multiple | nvarchar(100) | NULL | — | — | — |
| 8 | STOP_STOPM_Key | uniqueidentifier | NULL | — | — | — |
| 9 | LineId | int | NOT NULL | — | — | — |
| 10 | DelFlag | int | NULL | — | — | — |
| 11 | GroupId | int | NULL | — | DEFAULT ((0)) | — |

### dbo.B_Style_Operations_Master（表，8 字段）

| 序号 | 字段 | SQL 类型 | 可空 | 标识/计算 | 默认值或计算表达式 | 数据库注释 |
|---:|---|---|---|---|---|---|
| 1 | STOPM_Key | uniqueidentifier | NOT NULL | — | — | — |
| 2 | STOPM_ID | nvarchar(50) | NULL | — | — | — |
| 3 | STOPM_Description | nvarchar(50) | NULL | — | — | — |
| 4 | STOPM_Last_Updated | datetime | NULL | — | — | — |
| 5 | LineId | int | NOT NULL | — | — | — |
| 6 | DelFlag | int | NULL | — | — | — |
| 7 | GroupId | int | NULL | — | — | — |
| 8 | STOPM_SyncTime | datetime | NULL | — | — | — |

### dbo.B_Style_Purchase_Orders（表，11 字段）

| 序号 | 字段 | SQL 类型 | 可空 | 标识/计算 | 默认值或计算表达式 | 数据库注释 |
|---:|---|---|---|---|---|---|
| 1 | STPO_Key | int | NOT NULL | — | — | — |
| 2 | STPO_ST_Key | int | NULL | — | — | — |
| 3 | STPO_Number | nvarchar(15) | NULL | — | — | — |
| 4 | STPO_Agent_Buyer | nvarchar(50) | NULL | — | — | — |
| 5 | STPO_Quantity_Ordered | int | NULL | — | — | — |
| 6 | STPO_Comments | nvarchar(255) | NULL | — | — | — |
| 7 | STPO_Date_Created | datetime | NULL | — | — | — |
| 8 | STPO_Date_Modified | datetime | NULL | — | — | — |
| 9 | LineId | int | NOT NULL | — | — | — |
| 10 | DelFlag | int | NULL | — | — | — |
| 11 | GroupId | int | NULL | — | DEFAULT ((0)) | — |

### dbo.B_Style_Route_Detail（表，16 字段）

| 序号 | 字段 | SQL 类型 | 可空 | 标识/计算 | 默认值或计算表达式 | 数据库注释 |
|---:|---|---|---|---|---|---|
| 1 | SRD_Key | int | NOT NULL | — | — | — |
| 2 | SRD_SRM_Key | int | NULL | — | — | — |
| 3 | SRD_OC_Key | int | NULL | — | — | — |
| 4 | SRD_Line_Number | int | NULL | — | — | — |
| 5 | SRD_Station_Number | int | NULL | — | — | — |
| 6 | SRD_Receive | bit | NULL | — | — | — |
| 7 | SRD_Receive_Colour_Key | int | NULL | — | — | — |
| 8 | SRD_Receive_Size_Key | int | NULL | — | — | — |
| 9 | SRD_Distribution_Percent | real | NULL | — | — | — |
| 10 | LineId | int | NOT NULL | — | — | — |
| 11 | DelFlag | int | NULL | — | — | — |
| 12 | GroupId | int | NULL | — | DEFAULT ((0)) | — |
| 13 | SRD_Use_Manual_Distribution | bit | NULL | — | — | — |
| 14 | SRD_CS_Used | bit | NULL | — | — | — |
| 15 | SRD_CS_Key | uniqueidentifier | NULL | — | — | — |
| 16 | SRD_Quantity_Sent | int | NULL | — | — | — |

### dbo.B_Style_Route_Master（表，10 字段）

| 序号 | 字段 | SQL 类型 | 可空 | 标识/计算 | 默认值或计算表达式 | 数据库注释 |
|---:|---|---|---|---|---|---|
| 1 | SRM_Key | int | NOT NULL | — | — | — |
| 2 | SRM_Description | nvarchar(255) | NULL | — | — | — |
| 3 | SRM_ST_Key | int | NULL | — | — | — |
| 4 | SRM_Line | smallint | NULL | — | — | — |
| 5 | Created_Date | datetime | NULL | — | — | — |
| 6 | Modified_Date | datetime | NULL | — | — | — |
| 7 | SRM_STOPM_Key | uniqueidentifier | NULL | — | — | — |
| 8 | LineId | int | NOT NULL | — | — | — |
| 9 | DelFlag | int | NULL | — | — | — |
| 10 | GroupId | int | NULL | — | DEFAULT ((0)) | — |

### dbo.B_Style_Sizes_Not_Used（表，5 字段）

| 序号 | 字段 | SQL 类型 | 可空 | 标识/计算 | 默认值或计算表达式 | 数据库注释 |
|---:|---|---|---|---|---|---|
| 1 | SSNU_ST_Key | int | NOT NULL | — | — | — |
| 2 | SSNU_SM_Key | int | NOT NULL | — | — | — |
| 3 | LineId | int | NOT NULL | — | — | — |
| 4 | DelFlag | int | NULL | — | — | — |
| 5 | GroupId | int | NULL | — | DEFAULT ((0)) | — |

### dbo.B_WorkShop_Information（表，3 字段）

| 序号 | 字段 | SQL 类型 | 可空 | 标识/计算 | 默认值或计算表达式 | 数据库注释 |
|---:|---|---|---|---|---|---|
| 1 | WI_Key | uniqueidentifier | NOT NULL | — | — | — |
| 2 | WI_Name | nvarchar(50) | NULL | — | — | — |
| 3 | WI_Description | nvarchar(100) | NULL | — | — | — |

### dbo.B_Work_Codes（表，12 字段）

| 序号 | 字段 | SQL 类型 | 可空 | 标识/计算 | 默认值或计算表达式 | 数据库注释 |
|---:|---|---|---|---|---|---|
| 1 | WC_Key | smallint | NOT NULL | — | — | — |
| 2 | WC_Description | nvarchar(25) | NULL | — | — | — |
| 3 | WC_IsPieceWork | bit | NOT NULL | — | — | — |
| 4 | WC_IsNonStandard | bit | NOT NULL | — | — | — |
| 5 | WC_IsDelay | bit | NOT NULL | — | — | — |
| 6 | WC_SetNoReceive | bit | NOT NULL | — | — | — |
| 7 | WC_PA_Key | nvarchar(10) | NOT NULL | — | — | — |
| 8 | WC_Supplement | real | NULL | — | — | — |
| 9 | WC_Prohibit_Outfeed | bit | NULL | — | — | — |
| 10 | LineId | int | NOT NULL | — | — | — |
| 11 | DelFlag | int | NULL | — | — | — |
| 12 | GroupId | int | NULL | — | DEFAULT ((0)) | — |

### dbo.B_Workstation_Employee_Information（表，20 字段）

| 序号 | 字段 | SQL 类型 | 可空 | 标识/计算 | 默认值或计算表达式 | 数据库注释 |
|---:|---|---|---|---|---|---|
| 1 | WEI_Workstation | smallint | NOT NULL | — | — | — |
| 2 | WEI_Position | smallint | NOT NULL | — | — | — |
| 3 | WEI_Clocked_In | bit | NOT NULL | — | — | — |
| 4 | WEI_EM_Key | int | NOT NULL | — | — | — |
| 5 | WEI_ST_Key | int | NOT NULL | — | — | — |
| 6 | WEI_STPO_Key | int | NOT NULL | — | — | — |
| 7 | WEI_Lot_Number | nvarchar(50) | NULL | — | — | — |
| 8 | WEI_SRM_Key | int | NOT NULL | — | — | — |
| 9 | WEI_Garments_Per_Hanger | smallint | NOT NULL | — | — | — |
| 10 | WEI_OC_Key | int | NOT NULL | — | — | — |
| 11 | WEI_CM_Key | int | NOT NULL | — | — | — |
| 12 | WEI_SM_Key | int | NOT NULL | — | — | — |
| 13 | WEI_Quantity_Produced | int | NOT NULL | — | — | — |
| 14 | WEI_Efficiency | real | NOT NULL | — | — | — |
| 15 | WEI_Total_Quantity_Produced | int | NOT NULL | — | — | — |
| 16 | WEI_Total_Efficiency | real | NOT NULL | — | — | — |
| 17 | WEI_LLC | datetime | NULL | — | — | — |
| 18 | LineId | int | NOT NULL | — | — | — |
| 19 | DelFlag | int | NULL | — | — | — |
| 20 | GroupId | int | NULL | — | — | — |

### dbo.P_Barcode_Info（表，16 字段）

| 序号 | 字段 | SQL 类型 | 可空 | 标识/计算 | 默认值或计算表达式 | 数据库注释 |
|---:|---|---|---|---|---|---|
| 1 | BCI_Key | uniqueidentifier | NOT NULL | — | — | — |
| 2 | BCI_LabelID | nvarchar(20) | NOT NULL | — | — | — |
| 3 | BCI_StyleID | nvarchar(25) | NOT NULL | — | — | — |
| 4 | BCI_Colour | nvarchar(20) | NULL | — | — | — |
| 5 | BCI_Size | nvarchar(10) | NULL | — | — | — |
| 6 | BCI_Dept | nvarchar(25) | NULL | — | — | — |
| 7 | BCI_InserTime | datetime | NULL | — | — | — |
| 8 | BCI_Used | bit | NULL | — | — | — |
| 9 | BCI_Used_Count | int | NULL | — | — | — |
| 10 | BCI_LotNumber | nvarchar(25) | NULL | — | — | — |
| 11 | BCI_Trim | nvarchar(25) | NULL | — | — | — |
| 12 | BCI_Quantity | int | NULL | — | — | — |
| 13 | BCI_HM_Key | uniqueidentifier | NULL | — | — | — |
| 14 | LineId | int | NOT NULL | — | — | — |
| 15 | DelFlag | int | NULL | — | — | — |
| 16 | GroupId | int | NULL | — | — | — |

### dbo.P_Bridging_Hanger_Master（表，23 字段）

| 序号 | 字段 | SQL 类型 | 可空 | 标识/计算 | 默认值或计算表达式 | 数据库注释 |
|---:|---|---|---|---|---|---|
| 1 | HM_ID | nvarchar(50) | NOT NULL | — | — | — |
| 2 | HM_Loaded_On_Line | smallint | NULL | — | — | — |
| 3 | HM_Current_Line | smallint | NULL | — | — | — |
| 4 | HM_Current_Location_Type | nvarchar(1) | NULL | — | — | — |
| 5 | HM_Current_Location | smallint | NULL | — | — | — |
| 6 | HM_SRM_Key | int | NULL | — | — | — |
| 7 | HM_ST_Key | int | NULL | — | — | — |
| 8 | HM_STPO_Key | int | NULL | — | — | — |
| 9 | HM_Lot_Number | nvarchar(100) | NULL | — | — | — |
| 10 | HM_CM_Key | int | NULL | — | — | — |
| 11 | HM_SM_Key | int | NULL | — | — | — |
| 12 | HM_Garments_Per_Hanger | smallint | NULL | — | — | — |
| 13 | HM_Production_Time | real | NULL | — | — | — |
| 14 | HM_PI_Key | int | NULL | — | — | — |
| 15 | HM_Item_Card | nvarchar(20) | NULL | — | — | — |
| 16 | HM_Routing_Info | nvarchar(50) | NULL | — | — | — |
| 17 | HM_Is_Scrapped | bit | NULL | — | — | — |
| 18 | HM_ACC_Key | uniqueidentifier | NULL | — | — | — |
| 19 | HM_Key | uniqueidentifier | NULL | — | — | — |
| 20 | HM_SLSI_Key | int | NULL | — | — | — |
| 21 | HM_Sort_Complete | bit | NULL | — | — | — |
| 22 | GroupId | int | NULL | — | DEFAULT ((0)) | — |
| 23 | HM_Label | nvarchar(50) | NULL | — | — | — |

### dbo.P_Bridging_Hanger_Routing_Details（表，18 字段）

| 序号 | 字段 | SQL 类型 | 可空 | 标识/计算 | 默认值或计算表达式 | 数据库注释 |
|---:|---|---|---|---|---|---|
| 1 | HRD_Key | uniqueidentifier | NOT NULL | — | — | — |
| 2 | HRD_HM_ID | nvarchar(12) | NULL | — | — | — |
| 3 | HRD_OC_Key | int | NULL | — | — | — |
| 4 | HRD_EM_Key | int | NULL | — | — | — |
| 5 | HRD_Line | smallint | NULL | — | — | — |
| 6 | HRD_Workstation | smallint | NULL | — | — | — |
| 7 | HRD_Completed_Time | datetime | NULL | — | — | — |
| 8 | HRD_ODP_Credited | bit | NOT NULL | — | — | — |
| 9 | HRD_Comments | nvarchar(255) | NULL | — | — | — |
| 10 | HRD_PID_Key | int | NULL | — | — | — |
| 11 | HRD_Started_Time | datetime | NULL | — | — | — |
| 12 | HRD_WC_Key | smallint | NULL | — | — | — |
| 13 | HRD_Return_For_Repair | bit | NULL | — | — | — |
| 14 | HRD_Create_Time | datetime | NULL | — | — | — |
| 15 | GroupId | int | NULL | — | DEFAULT ((0)) | — |
| 16 | HRD_OC_Completed | bit | NULL | — | — | — |
| 17 | HRD_Enter_Time | datetime | NULL | — | — | — |
| 18 | HRD_Defect_Order | smallint | NULL | — | — | — |

### dbo.P_Clock_InOut_History（表，8 字段）

| 序号 | 字段 | SQL 类型 | 可空 | 标识/计算 | 默认值或计算表达式 | 数据库注释 |
|---:|---|---|---|---|---|---|
| 1 | CIOH_Key | bigint | NOT NULL | IDENTITY | — | — |
| 2 | CIOH_ODP_Key | uniqueidentifier | NOT NULL | — | — | — |
| 3 | CIOH_Clock_In_Time | datetime | NOT NULL | — | — | — |
| 4 | CIOH_Clock_Out_Time | datetime | NULL | — | — | — |
| 5 | CIOH_Workstation | int | NULL | — | — | — |
| 6 | LineId | int | NOT NULL | — | — | — |
| 7 | DelFlag | int | NULL | — | — | — |
| 8 | GroupId | int | NULL | — | — | — |

### dbo.P_Hanger_Master（表，29 字段）

| 序号 | 字段 | SQL 类型 | 可空 | 标识/计算 | 默认值或计算表达式 | 数据库注释 |
|---:|---|---|---|---|---|---|
| 1 | HM_ID | nvarchar(50) | NOT NULL | — | — | — |
| 2 | HM_Key | uniqueidentifier | NOT NULL | — | — | — |
| 3 | HM_Loaded_On_Line | smallint | NULL | — | — | — |
| 4 | HM_Current_Line | smallint | NULL | — | — | — |
| 5 | HM_Current_Location_Type | nvarchar(1) | NULL | — | — | — |
| 6 | HM_Current_Location | smallint | NULL | — | — | — |
| 7 | HM_SRM_Key | int | NULL | — | — | — |
| 8 | HM_ST_Key | int | NULL | — | — | — |
| 9 | HM_STPO_Key | int | NULL | — | — | — |
| 10 | HM_Lot_Number | nvarchar(100) | NULL | — | — | — |
| 11 | HM_CM_Key | int | NULL | — | — | — |
| 12 | HM_SM_Key | int | NULL | — | — | — |
| 13 | HM_Garments_Per_Hanger | smallint | NULL | — | — | — |
| 14 | HM_Production_Time | real | NULL | — | — | — |
| 15 | HM_Item_Card | nvarchar(20) | NULL | — | — | — |
| 16 | HM_PI_Key | int | NULL | — | — | — |
| 17 | HM_Routing_Info | nvarchar(50) | NULL | — | — | — |
| 18 | HM_Is_Scrapped | bit | NULL | — | — | — |
| 19 | HM_ACC_Key | uniqueidentifier | NULL | — | — | — |
| 20 | HM_SLSI_Key | int | NULL | — | — | — |
| 21 | HM_Sort_Complete | bit | NULL | — | — | — |
| 22 | DelFlag | int | NULL | — | — | — |
| 23 | GroupId | int | NULL | — | DEFAULT ((0)) | — |
| 24 | HM_SP_Key | int | NULL | — | — | — |
| 25 | HM_Label | nvarchar(50) | NULL | — | — | — |
| 26 | HM_Enter_Station | int | NULL | — | — | — |
| 27 | HM_Enter_Time | datetime | NULL | — | — | — |
| 28 | HM_OC_List | nvarchar(900) | NULL | — | — | — |
| 29 | HM_Update_Time | datetime | NULL | — | — | — |

### dbo.P_Hanger_Routing_Details（表，21 字段）

| 序号 | 字段 | SQL 类型 | 可空 | 标识/计算 | 默认值或计算表达式 | 数据库注释 |
|---:|---|---|---|---|---|---|
| 1 | HRD_Key | uniqueidentifier | NOT NULL | — | — | — |
| 2 | HRD_HM_ID | nvarchar(12) | NULL | — | — | — |
| 3 | HRD_HM_Key | uniqueidentifier | NULL | — | — | — |
| 4 | HRD_OC_Key | int | NULL | — | — | — |
| 5 | HRD_EM_Key | int | NULL | — | — | — |
| 6 | HRD_Line | smallint | NULL | — | — | — |
| 7 | HRD_Workstation | smallint | NULL | — | — | — |
| 8 | HRD_Completed_Time | datetime | NULL | — | — | — |
| 9 | HRD_ODP_Credited | bit | NOT NULL | — | — | — |
| 10 | HRD_Comments | nvarchar(255) | NULL | — | — | — |
| 11 | HRD_WC_Key | smallint | NULL | — | — | — |
| 12 | HRD_Started_Time | datetime | NULL | — | — | — |
| 13 | HRD_PID_Key | int | NULL | — | — | — |
| 14 | HRD_Return_For_Repair | bit | NULL | — | — | — |
| 15 | HRD_Create_Time | datetime | NULL | — | — | — |
| 16 | DelFlag | int | NULL | — | — | — |
| 17 | HRD_AutoId | int | NOT NULL | IDENTITY | — | — |
| 18 | GroupId | int | NULL | — | DEFAULT ((0)) | — |
| 19 | HRD_OC_Completed | bit | NULL | — | — | — |
| 20 | HRD_Enter_Time | datetime | NULL | — | — | — |
| 21 | HRD_Defect_Order | smallint | NULL | — | — | — |

### dbo.P_Line_Daily_Break_Times（表，10 字段）

| 序号 | 字段 | SQL 类型 | 可空 | 标识/计算 | 默认值或计算表达式 | 数据库注释 |
|---:|---|---|---|---|---|---|
| 1 | Ldbt_Key | uniqueidentifier | NOT NULL | — | — | — |
| 2 | Break_Date | datetime | NULL | — | — | — |
| 3 | Break_Number | smallint | NULL | — | — | — |
| 4 | Break_Start_Time | datetime | NULL | — | — | — |
| 5 | Break_End_Time | datetime | NULL | — | — | — |
| 6 | Break_Allow_To_Send_Hanger | bit | NULL | — | — | — |
| 7 | Break_Automatic_Logout | bit | NULL | — | — | — |
| 8 | LineId | int | NOT NULL | — | — | — |
| 9 | DelFlag | int | NULL | — | — | — |
| 10 | GroupId | int | NULL | — | DEFAULT ((0)) | — |

### dbo.P_Line_Daily_Shift_Times（表，13 字段）

| 序号 | 字段 | SQL 类型 | 可空 | 标识/计算 | 默认值或计算表达式 | 数据库注释 |
|---:|---|---|---|---|---|---|
| 1 | Ldst_Key | uniqueidentifier | NOT NULL | — | — | — |
| 2 | Shift_Date | datetime | NULL | — | — | — |
| 3 | Shift_Number | smallint | NULL | — | — | — |
| 4 | Shift_Start_Time | datetime | NULL | — | — | — |
| 5 | Shift_End_Time | datetime | NULL | — | — | — |
| 6 | Shift_Normal_Pay_Factor | real | NULL | — | — | — |
| 7 | Shift_Hours_Before_Overtime | real | NULL | — | — | — |
| 8 | Shift_Overtime_Pay_Factor1 | real | NULL | — | — | — |
| 9 | Shift_Overtime_Pay_Factor2 | real | NULL | — | — | — |
| 10 | Shift_Overtime_Pay_Factor3 | real | NULL | — | — | — |
| 11 | LineId | int | NOT NULL | — | — | — |
| 12 | DelFlag | int | NULL | — | — | — |
| 13 | GroupId | int | NULL | — | DEFAULT ((0)) | — |

### dbo.P_ODP_Detail（表，27 字段）

| 序号 | 字段 | SQL 类型 | 可空 | 标识/计算 | 默认值或计算表达式 | 数据库注释 |
|---:|---|---|---|---|---|---|
| 1 | ODPD_Key | uniqueidentifier | NOT NULL | — | — | — |
| 2 | ODPD_ODP_Key | uniqueidentifier | NOT NULL | — | — | — |
| 3 | ODPD_Workstation | smallint | NULL | — | — | — |
| 4 | ODPD_WC_Key | smallint | NULL | — | — | — |
| 5 | ODPD_Quantity | smallint | NULL | — | — | — |
| 6 | ODPD_ST_Key | int | NULL | — | — | — |
| 7 | ODPD_Lot_Number | nvarchar(100) | NULL | — | — | — |
| 8 | ODPD_OC_Key | int | NOT NULL | — | — | — |
| 9 | ODPD_Standard | real | NULL | — | — | — |
| 10 | ODPD_Actual_Time | int | NULL | — | — | — |
| 11 | ODPD_PA_Key | nvarchar(10) | NULL | — | — | — |
| 12 | ODPD_Pay_Rate | real | NULL | — | — | — |
| 13 | ODPD_Piece_Rate | real | NULL | — | — | — |
| 14 | ODPD_Start_Time | datetime | NULL | — | — | — |
| 15 | ODPD_CM_Key | int | NULL | — | — | — |
| 16 | ODPD_SM_Key | int | NULL | — | — | — |
| 17 | ODPD_Normal_Pay_Factor | real | NULL | — | — | — |
| 18 | ODPD_Is_Overtime | bit | NOT NULL | — | — | — |
| 19 | ODPD_Overtime_Factor | real | NULL | — | — | — |
| 20 | ODPD_Edited_By | nvarchar(10) | NULL | — | — | — |
| 21 | ODPD_Edited_Date | datetime | NULL | — | — | — |
| 22 | ODPD_Actual_Time_From_Reader | int | NULL | — | — | — |
| 23 | ODPD_Sync_DateTime | datetime | NULL | — | — | — |
| 24 | LineId | int | NOT NULL | — | — | — |
| 25 | DelFlag | int | NULL | — | — | — |
| 26 | GroupId | int | NULL | — | DEFAULT ((0)) | — |
| 27 | ODPD_STPO_Key | int | NULL | — | — | — |

### dbo.P_ODP_Master（表，17 字段）

| 序号 | 字段 | SQL 类型 | 可空 | 标识/计算 | 默认值或计算表达式 | 数据库注释 |
|---:|---|---|---|---|---|---|
| 1 | ODP_Key | uniqueidentifier | NOT NULL | — | — | — |
| 2 | ODP_Date | datetime | NULL | — | — | — |
| 3 | ODP_Shift | smallint | NULL | — | — | — |
| 4 | ODP_EM_Key | int | NULL | — | — | — |
| 5 | ODP_Actual_Clock_In | datetime | NULL | — | — | — |
| 6 | ODP_Actual_Clock_Out | datetime | NULL | — | — | — |
| 7 | ODP_Shift_Clock_In | datetime | NULL | — | — | — |
| 8 | ODP_Shift_Clock_Out | datetime | NULL | — | — | — |
| 9 | ODP_First_Hanger_Time | datetime | NULL | — | — | — |
| 10 | ODP_Last_Hanger_Time | datetime | NULL | — | — | — |
| 11 | ODP_Current_Station | smallint | NULL | — | — | — |
| 12 | ODP_Lump_Sum_Payment | float | NULL | — | — | — |
| 13 | ODP_Make_Up_Pay_Rate | real | NULL | — | — | — |
| 14 | ODP_Last_Hanger_Start_Time | datetime | NULL | — | — | — |
| 15 | LineId | int | NOT NULL | — | — | — |
| 16 | DelFlag | int | NULL | — | — | — |
| 17 | GroupId | int | NULL | — | DEFAULT ((0)) | — |

### dbo.P_QC_Rework（表，23 字段）

| 序号 | 字段 | SQL 类型 | 可空 | 标识/计算 | 默认值或计算表达式 | 数据库注释 |
|---:|---|---|---|---|---|---|
| 1 | QCR_Key | uniqueidentifier | NOT NULL | — | — | — |
| 2 | QCR_Defect_DateTime | datetime | NOT NULL | — | — | — |
| 3 | QCR_Defect_EM_Key | int | NOT NULL | — | — | — |
| 4 | QCR_Defect_ST_Key | int | NOT NULL | — | — | — |
| 5 | QCR_Defect_OC_Key | int | NOT NULL | — | — | — |
| 6 | QCR_Sent_To_Rework_By_EM_Key | int | NOT NULL | — | — | — |
| 7 | QCR_Defect_Quantity | smallint | NOT NULL | — | — | — |
| 8 | QCR_From_QC_Station | smallint | NULL | — | — | — |
| 9 | QCR_HM_ID | nvarchar(12) | NULL | — | — | — |
| 10 | QCR_QC_DateTime | datetime | NULL | — | — | — |
| 11 | QCR_Repair_EM_Key | int | NULL | — | — | — |
| 12 | QCR_Repair_DateTime | datetime | NULL | — | — | — |
| 13 | QCR_Repair_Quantity | int | NULL | — | — | — |
| 14 | QCR_Defect_CM_Key | int | NULL | — | — | — |
| 15 | QCR_Defect_SM_Key | int | NULL | — | — | — |
| 16 | QCR_QCSC_Key | uniqueidentifier | NULL | — | — | — |
| 17 | QCR_HM_Key | uniqueidentifier | NULL | — | — | — |
| 18 | QCR_STPO_Key | int | NULL | — | — | — |
| 19 | LineId | int | NOT NULL | — | — | — |
| 20 | DelFlag | int | NULL | — | — | — |
| 21 | QCR_AutoId | int | NOT NULL | IDENTITY | — | — |
| 22 | GroupId | int | NULL | — | DEFAULT ((0)) | — |
| 23 | QCR_QC_OC_Key | int | NULL | — | — | — |

### dbo.P_QC_Scrap（表，14 字段）

| 序号 | 字段 | SQL 类型 | 可空 | 标识/计算 | 默认值或计算表达式 | 数据库注释 |
|---:|---|---|---|---|---|---|
| 1 | QCS_Key | uniqueidentifier | NOT NULL | — | — | — |
| 2 | QCS_Defect_DateTime | datetime | NULL | — | — | — |
| 3 | QCS_EM_Key | int | NULL | — | — | — |
| 4 | QCS_ST_Key | int | NULL | — | — | — |
| 5 | QCS_OC_Key | int | NULL | — | — | — |
| 6 | QCS_Sent_To_Scrap_By_EM_Key | int | NULL | — | — | — |
| 7 | QCS_HM_ID | nvarchar(50) | NULL | — | — | — |
| 8 | QCS_Scrap_DateTime | datetime | NULL | — | — | — |
| 9 | QCS_QCSC_Key | uniqueidentifier | NULL | — | — | — |
| 10 | QCS_STPO_Key | int | NULL | — | — | — |
| 11 | LineId | int | NOT NULL | — | — | — |
| 12 | DelFlag | int | NULL | — | — | — |
| 13 | GroupId | int | NULL | — | — | — |
| 14 | QCS_HM_Key | uniqueidentifier | NULL | — | — | — |

### dbo.P_Style_Planned_Orders（表，20 字段）

| 序号 | 字段 | SQL 类型 | 可空 | 标识/计算 | 默认值或计算表达式 | 数据库注释 |
|---:|---|---|---|---|---|---|
| 1 | STPO_Key | int | NOT NULL | — | — | — |
| 2 | STPO_ST_Key | int | NULL | — | — | — |
| 3 | STPO_ID | nvarchar(50) | NULL | — | — | — |
| 4 | STPO_CI_Name | nvarchar(50) | NULL | — | — | — |
| 5 | STPO_PlannedQuantity | int | NULL | — | — | — |
| 6 | STPO_Comment | nvarchar(50) | NULL | — | — | — |
| 7 | STPO_CreateDate | datetime | NULL | — | — | — |
| 8 | STPO_ModifiedDate | datetime | NULL | — | — | — |
| 9 | STPO_OM_ID | nvarchar(50) | NULL | — | — | — |
| 10 | STPO_PlanStartDate | datetime | NULL | — | — | — |
| 11 | STPO_PlanFinishDate | datetime | NULL | — | — | — |
| 12 | STPO_SM_Key | int | NULL | — | — | — |
| 13 | STPO_CM_Key | int | NULL | — | — | — |
| 14 | STPO_Level | int | NULL | — | — | — |
| 15 | STPO_LP_Key | uniqueidentifier | NULL | — | — | — |
| 16 | STPO_Level_Description | nvarchar(25) | NULL | — | — | — |
| 17 | STPO_Completed | bit | NULL | — | — | — |
| 18 | LineId | int | NOT NULL | — | — | — |
| 19 | DelFlag | int | NULL | — | — | — |
| 20 | GroupId | int | NULL | — | — | — |

### dbo.P_Style_Planned_Orders_Quantity_Loaded（表，6 字段）

| 序号 | 字段 | SQL 类型 | 可空 | 标识/计算 | 默认值或计算表达式 | 数据库注释 |
|---:|---|---|---|---|---|---|
| 1 | STPO_Key | int | NOT NULL | — | — | — |
| 2 | STPO_Line | smallint | NOT NULL | — | — | — |
| 3 | STPO_Quantity_Loaded | int | NULL | — | — | — |
| 4 | LineId | int | NOT NULL | — | — | — |
| 5 | DelFlag | int | NULL | — | — | — |
| 6 | GroupId | int | NULL | — | — | — |

### dbo.P_Style_Planned_Orders_Quantity_Registered（表，6 字段）

| 序号 | 字段 | SQL 类型 | 可空 | 标识/计算 | 默认值或计算表达式 | 数据库注释 |
|---:|---|---|---|---|---|---|
| 1 | STPR_Key | int | NOT NULL | — | — | — |
| 2 | STPR_Line | smallint | NOT NULL | — | — | — |
| 3 | STPR_Quantity_Registered | int | NOT NULL | — | — | — |
| 4 | LineId | int | NOT NULL | — | — | — |
| 5 | DelFlag | int | NULL | — | — | — |
| 6 | GroupId | int | NULL | — | — | — |

### dbo.T_ColInfo（表，5 字段）

| 序号 | 字段 | SQL 类型 | 可空 | 标识/计算 | 默认值或计算表达式 | 数据库注释 |
|---:|---|---|---|---|---|---|
| 1 | ScolId | nvarchar(50) | NOT NULL | — | — | — |
| 2 | DbId | nvarchar(50) | NULL | — | — | — |
| 3 | StableId | nvarchar(50) | NULL | — | — | — |
| 4 | ScolName | nvarchar(50) | NULL | — | — | — |
| 5 | ScolKey | int | NULL | — | — | — |

### dbo.T_DbInfo（表，9 字段）

| 序号 | 字段 | SQL 类型 | 可空 | 标识/计算 | 默认值或计算表达式 | 数据库注释 |
|---:|---|---|---|---|---|---|
| 1 | DbId | nvarchar(50) | NOT NULL | — | — | — |
| 2 | DbName | nvarchar(50) | NULL | — | — | b'\xe6\x95\xb0\xe6\x8d\xae\xe5\xba\x93\xe5\x90\x8d\xe7\xa7\xb0' |
| 3 | DbSource | nvarchar(50) | NULL | — | — | b'\xe6\x95\xb0\xe6\x8d\xae\xe5\xba\x93\xe5\x9c\xb0\xe5\x9d\x80' |
| 4 | DbUser | nvarchar(50) | NULL | — | — | b'\xe6\x95\xb0\xe6\x8d\xae\xe5\xba\x93\xe7\x94\xa8\xe6\x88\xb7' |
| 5 | DbPassword | nvarchar(50) | NULL | — | — | b'\xe6\x95\xb0\xe6\x8d\xae\xe5\xba\x93\xe7\x94\xa8\xe6\x88\xb7\xe5\xaf\x86\xe7\xa0\x81' |
| 6 | LineId | int | NULL | — | — | b'\xe7\xba\xbf\xe5\x8f\xb7' |
| 7 | LineName | nvarchar(50) | NULL | — | — | b'\xe7\xba\xbf\xe5\x90\x8d\xe7\xa7\xb0' |
| 8 | Enable | int | NULL | — | DEFAULT ((0)) | — |
| 9 | GroupId | int | NULL | — | DEFAULT ((0)) | — |

### dbo.T_IDS_Version（表，3 字段）

| 序号 | 字段 | SQL 类型 | 可空 | 标识/计算 | 默认值或计算表达式 | 数据库注释 |
|---:|---|---|---|---|---|---|
| 1 | DV_IDS_Version | varchar(50) | NOT NULL | — | — | — |
| 2 | DV_From_LineDb_Version | varchar(50) | NULL | — | — | — |
| 3 | DV_To_LineDb_Version | varchar(50) | NULL | — | — | — |

### dbo.T_TableInfo（表，6 字段）

| 序号 | 字段 | SQL 类型 | 可空 | 标识/计算 | 默认值或计算表达式 | 数据库注释 |
|---:|---|---|---|---|---|---|
| 1 | TableId | nvarchar(50) | NOT NULL | — | — | — |
| 2 | DbId | nvarchar(50) | NULL | — | — | — |
| 3 | TableName | nvarchar(50) | NULL | — | — | — |
| 4 | SelectSql | text | NULL | — | — | — |
| 5 | StableName | nvarchar(50) | NULL | — | — | — |
| 6 | SyncVersion | bigint | NULL | — | — | — |

### dbo.ToIna_Colour（表，4 字段）

| 序号 | 字段 | SQL 类型 | 可空 | 标识/计算 | 默认值或计算表达式 | 数据库注释 |
|---:|---|---|---|---|---|---|
| 1 | TCO_Key | int | NOT NULL | IDENTITY | — | — |
| 2 | TCO_StyleID | nvarchar(25) | NOT NULL | — | — | — |
| 3 | TCO_ColourID | nvarchar(10) | NULL | — | — | — |
| 4 | TCO_Colour_Description | nvarchar(50) | NULL | — | — | — |

### dbo.ToIna_Employee（表，10 字段）

| 序号 | 字段 | SQL 类型 | 可空 | 标识/计算 | 默认值或计算表达式 | 数据库注释 |
|---:|---|---|---|---|---|---|
| 1 | TIE_Key | int | NOT NULL | IDENTITY | — | b'Key\xe7\xa0\x81\r\n' |
| 2 | TIE_ID | int | NULL | — | — | b'\xe5\x91\x98\xe5\xb7\xa5\xe7\xbc\x96\xe5\x8f\xb7\r\n' |
| 3 | TIE_Name | nvarchar(50) | NULL | — | — | b'\xe5\x91\x98\xe5\xb7\xa5\xe5\xa7\x93\xe5\x90\x8d\r\n' |
| 4 | TIE_Sex | nvarchar(1) | NULL | — | — | b'\xe5\x91\x98\xe5\xb7\xa5\xe6\x80\xa7\xe5\x88\xab\r\n' |
| 5 | TIE_RFID | nvarchar(10) | NULL | — | — | b'\xe5\x91\x98\xe5\xb7\xa5\xe5\x8d\xa1\xe5\x8f\xb7\r\n' |
| 6 | TIE_Line | nvarchar(50) | NULL | — | — | b'\xe5\x91\x98\xe5\xb7\xa5\xe6\x89\x80\xe5\xb1\x9e\xe7\x94\x9f\xe4\xba\xa7\xe7\xba\xbf\xe5\x8f\xb7\r\n' |
| 7 | TIE_Workshop | nvarchar(25) | NULL | — | — | b'\xe5\x91\x98\xe5\xb7\xa5\xe6\x89\x80\xe5\xb1\x9e\xe7\x94\x9f\xe4\xba\xa7\xe8\xbd\xa6\xe9\x97\xb4\r\n' |
| 8 | TIE_Group | nvarchar(15) | NULL | — | — | b'\xe5\x91\x98\xe5\xb7\xa5\xe6\x89\x80\xe5\xb1\x9e\xe7\xbb\x84\xe5\x88\xab\r\n' |
| 9 | TIE_Department | nvarchar(15) | NULL | — | — | b'\xe5\x91\x98\xe5\xb7\xa5\xe6\x89\x80\xe5\xb1\x9e\xe9\x83\xa8\xe9\x97\xa8\r\n' |
| 10 | TIE_SSN | nvarchar(15) | NULL | — | — | b'\xe5\x91\x98\xe5\xb7\xa5\xe8\xba\xab\xe4\xbb\xbd\xe5\x94\xaf\xe4\xb8\x80\xe8\xaf\x86\xe5\x88\xab\xe4\xbf\xa1\xe6\x81\xaf\r\n' |

### dbo.ToIna_Route_Map（表，8 字段）

| 序号 | 字段 | SQL 类型 | 可空 | 标识/计算 | 默认值或计算表达式 | 数据库注释 |
|---:|---|---|---|---|---|---|
| 1 | TIRM_Key | int | NOT NULL | IDENTITY | — | b'Key\xe7\xa0\x81\r\n' |
| 2 | TIRM_TIS_StyleID | nvarchar(25) | NOT NULL | — | — | b'\xe6\xac\xbe\xe5\xbc\x8fID' |
| 3 | TIRM_TISO_Op_Code | nvarchar(25) | NOT NULL | — | — | b'\xe5\xb7\xa5\xe5\xba\x8fID' |
| 4 | TIRM_Line | nvarchar(50) | NULL | — | — | b'\xe7\x94\x9f\xe4\xba\xa7\xe7\xba\xbf\xe5\x8f\xb7\r\n' |
| 5 | TIRM_Station_Number | int | NOT NULL | — | — | b'\xe5\xb7\xa5\xe4\xbd\x9c\xe7\xab\x99\xe5\x8f\xb7\xef\xbc\x88\xe9\xa1\xbb\xe5\x92\x8c\xe5\xaf\xb9\xe5\xba\x94\xe7\xba\xbf\xe5\x8f\xb7\xe7\x9a\x84\xe8\xa1\xa3\xe6\x8b\xbf\xe5\x90\x8a\xe6\x8c\x82\xe7\xb3\xbb\xe7\xbb\x9f\xe5\xb7\xa5\xe4\xbd\x9c\xe7\xab\x99\xe5\x8f\xb7\xe4\xb8\xa5\xe6\xa0\xbc\xe5\xaf\xb9\xe5\xba\x94\xef\xbc\x89' |
| 6 | TIRM_Created_Date | datetime | NULL | — | — | b'\xe5\x88\x9b\xe5\xbb\xba\xe6\x97\xb6\xe9\x97\xb4\r\n' |
| 7 | TIRM_Loading_Line | nvarchar(50) | NULL | — | — | b'\xe4\xb8\x8a\xe8\xa3\x81\xe7\xba\xbf\xe5\x8f\xb7\r\n' |
| 8 | TIRM_MapKey | nvarchar(50) | NULL | — | — | b'\xe8\xb7\xaf\xe7\xba\xbf\xe5\x9b\xbe\xe5\x94\xaf\xe4\xb8\x80\xe7\xa0\x81\r\n' |

### dbo.ToIna_Size（表，4 字段）

| 序号 | 字段 | SQL 类型 | 可空 | 标识/计算 | 默认值或计算表达式 | 数据库注释 |
|---:|---|---|---|---|---|---|
| 1 | TSI_Key | int | NOT NULL | IDENTITY | — | — |
| 2 | TSI_StyleID | nvarchar(25) | NOT NULL | — | — | — |
| 3 | TSI_SizeID | nvarchar(10) | NULL | — | — | — |
| 4 | TSI_Size_Description | nvarchar(50) | NULL | — | — | — |

### dbo.ToIna_Style（表，8 字段）

| 序号 | 字段 | SQL 类型 | 可空 | 标识/计算 | 默认值或计算表达式 | 数据库注释 |
|---:|---|---|---|---|---|---|
| 1 | TIS_Key | int | NOT NULL | IDENTITY | — | b'Key\xe7\xa0\x81\r\n' |
| 2 | TIS_StyleID | nvarchar(25) | NOT NULL | — | — | b'\xe6\xac\xbe\xe5\x8f\xb7\xef\xbc\x9a\r\n\xe2\x80\x9c\xe6\xac\xbe\xe5\xbc\x8f\xe2\x80\x9d\xe5\x94\xaf\xe4\xb8\x80\xe8\xaf\x86\xe5\x88\xab\xe5\xad\x97\xe6\xae\xb5\xef\xbc\x8c\xe4\xb8\x8d\xe5\x90\x8c\xe6\xac\xbe\xe5\xbc\x8f\xe4\xb8\x8d\xe5\x8f\xaf\xe9\x87\x8d\xe5\xa4\x8d' |
| 3 | TIS_StyleDescription | nvarchar(50) | NULL | — | — | b'\xe6\xac\xbe\xe5\xbc\x8f\xe6\x8f\x8f\xe8\xbf\xb0\xef\xbc\x9a\r\n\xe2\x80\x9c\xe6\xac\xbe\xe5\xbc\x8f\xe2\x80\x9d\xe9\x99\x84\xe5\x8a\xa0\xe5\xad\x97\xe6\xae\xb5\xef\xbc\x8c\xe4\xbe\x8b\xe5\xa6\x82\xe4\xb8\xad\xe6\x96\x87\xe6\x8f\x8f\xe8\xbf\xb0' |
| 4 | TIS_StyleCollection | nvarchar(25) | NULL | — | — | b'\xe2\x80\x9c\xe6\xac\xbe\xe5\xbc\x8f\xe2\x80\x9d\xe9\x99\x84\xe5\x8a\xa0\xe5\xad\x97\xe6\xae\xb5\xef\xbc\x8c\xe4\xbe\x8b\xe5\xa6\x82\xe6\xac\xbe\xe5\xbc\x8f\xe9\x9a\xb6\xe5\xb1\x9e\xe7\x9a\x84\xe8\xae\xa2\xe5\x8d\x95/\xe5\x88\xb6\xe5\x8d\x95\xe5\x8f\xb7' |
| 5 | TIS_Used | bit | NULL | — | — | b'\xe6\x98\xaf\xe5\x90\xa6\xe5\xb7\xb2\xe5\xaf\xbc\xe5\x85\xa5\r\n0\xef\xbc\x9a\xe6\x9c\xaa\xe5\xaf\xbc\xe5\x85\xa5\xe8\xbf\x87\xef\xbc\x9b1\xef\xbc\x9a\xe5\xb7\xb2\xe5\xaf\xbc\xe5\x85\xa5\xe8\xbf\x87\r\n' |
| 6 | TIS_UsedTime | datetime | NULL | — | — | b'\xe5\xaf\xbc\xe5\x85\xa5\xe6\x97\xb6\xe9\x97\xb4\r\n' |
| 7 | TIS_Quantity | int | NULL | — | — | b'\xe6\xac\xbe\xe5\xbc\x8f\xe7\x94\x9f\xe4\xba\xa7\xe6\x95\xb0\xe9\x87\x8f' |
| 8 | TIS_Line | nvarchar(50) | NULL | — | — | b'\xe7\x94\x9f\xe4\xba\xa7\xe7\xba\xbf\xe5\x8f\xb7\xe7\xa0\x81' |

### dbo.ToIna_Style_Operations（表，10 字段）

| 序号 | 字段 | SQL 类型 | 可空 | 标识/计算 | 默认值或计算表达式 | 数据库注释 |
|---:|---|---|---|---|---|---|
| 1 | TISO_Key | int | NOT NULL | IDENTITY | — | b'Key\xe7\xa0\x81\r\n' |
| 2 | TISO_StyleID | nvarchar(25) | NOT NULL | — | — | b'\xe2\x80\x9c\xe6\xac\xbe\xe5\xbc\x8f\xe2\x80\x9d\xe5\x94\xaf\xe4\xb8\x80\xe8\xaf\x86\xe5\x88\xab\xe5\xad\x97\xe6\xae\xb5\xef\xbc\x8c\xe4\xb8\x8d\xe5\x90\x8c\xe6\xac\xbe\xe5\xbc\x8f\xe4\xb8\x8d\xe5\x8f\xaf\xe9\x87\x8d\xe5\xa4\x8d' |
| 3 | TISO_Op_Code | nvarchar(25) | NOT NULL | — | — | b'\xe2\x80\x9c\xe5\xb7\xa5\xe5\xba\x8f\xe2\x80\x9d\xe5\x94\xaf\xe4\xb8\x80\xe8\xaf\x86\xe5\x88\xab\xe5\xad\x97\xe6\xae\xb5\xef\xbc\x8c\xe4\xb8\x8d\xe5\x90\x8c\xe5\xb7\xa5\xe5\xba\x8f\xe4\xb8\x8d\xe5\x8f\xaf\xe9\x87\x8d\xe5\xa4\x8d' |
| 4 | TISO_Op_Order | smallint | NOT NULL | — | — | b'\xe2\x80\x9c\xe6\xac\xbe\xe5\xbc\x8f\xe7\x94\x9f\xe4\xba\xa7\xe5\xb7\xa5\xe5\xba\x8f\xe8\xa1\xa8\xe2\x80\x9d\xe6\x8e\x92\xe5\xba\x8f\xe5\xad\x97\xe6\xae\xb5\xef\xbc\x8c\xe6\x88\x96\xe6\xac\xbe\xe5\xbc\x8f\xe5\xb7\xa5\xe5\xba\x8f\xe8\xa1\xa8\xe5\x86\x85\xe5\x94\xaf\xe4\xb8\x80\xe8\xaf\x86\xe5\x88\xab' |
| 5 | TISO_Op_Name | nvarchar(35) | NULL | — | — | b'\xe2\x80\x9c\xe5\xb7\xa5\xe5\xba\x8f\xe2\x80\x9d\xe9\x99\x84\xe5\x8a\xa0\xe5\xad\x97\xe6\xae\xb5\xef\xbc\x8c\xe5\xb7\xa5\xe5\xba\x8f\xe6\x8f\x8f\xe8\xbf\xb0' |
| 6 | TISO_Op_PieceRate | real | NULL | — | — | b'\xe2\x80\x9c\xe5\xb7\xa5\xe5\xba\x8f\xe2\x80\x9d\xe9\x99\x84\xe5\x8a\xa0\xe5\xad\x97\xe6\xae\xb5,\xe5\xb7\xa5\xe5\xba\x8f\xe5\x8d\x95\xe4\xbb\xb7' |
| 7 | TISO_Op_StandardTime | real | NULL | — | — | b'\xe2\x80\x9c\xe5\xb7\xa5\xe5\xba\x8f\xe2\x80\x9d\xe9\x99\x84\xe5\x8a\xa0\xe5\xad\x97\xe6\xae\xb5,\xe6\xa0\x87\xe5\x87\x86\xe5\xb7\xa5\xe6\x97\xb6' |
| 8 | TISO_Op_MachineType | nvarchar(25) | NULL | — | — | b'\xe2\x80\x9c\xe5\xb7\xa5\xe5\xba\x8f\xe2\x80\x9d\xe9\x99\x84\xe5\x8a\xa0\xe5\xad\x97\xe6\xae\xb5\xef\xbc\x8c\xe4\xbd\xbf\xe7\x94\xa8\xe8\xbd\xa6\xe5\x8f\xb0\xe7\xb1\xbb\xe5\x9e\x8b\xe3\x80\x82' |
| 9 | TISO_Op_IsOnline | bit | NULL | — | DEFAULT ((0)) | b'\xe7\xba\xbf\xe5\x86\x85/\xe7\xba\xbf\xe5\xa4\x96\xe5\xb7\xa5\xe5\xba\x8f\xe8\xaf\x86\xe5\x88\xab' |
| 10 | TISO_Line | nvarchar(50) | NULL | — | — | b'\xe7\xba\xbf\xe5\x86\x85/\xe7\xba\xbf\xe5\xa4\x96\xe5\xb7\xa5\xe5\xba\x8f\xe8\xaf\x86\xe5\x88\xab\r\n' |

### dbo.ToIna_Style_Planned_Orders（表，18 字段）

| 序号 | 字段 | SQL 类型 | 可空 | 标识/计算 | 默认值或计算表达式 | 数据库注释 |
|---:|---|---|---|---|---|---|
| 1 | TSPO_Key | int | NOT NULL | IDENTITY | — | b'\xe8\x87\xaa\xe5\xa2\x9eKey' |
| 2 | TSPO_ID | nvarchar(50) | NOT NULL | — | — | — |
| 3 | TSPO_StyleID | nvarchar(25) | NOT NULL | — | — | — |
| 4 | TSPO_CI_Name | nvarchar(50) | NULL | — | — | b'Customer Name' |
| 5 | TSPO_PlannedQuantity | int | NULL | — | — | — |
| 6 | TSPO_Comments | nvarchar(255) | NULL | — | — | — |
| 7 | TSPO_PlanStartDate | datetime | NULL | — | — | — |
| 8 | TSPO_PlanFinishDate | datetime | NULL | — | — | — |
| 9 | TSPO_Level | int | NULL | — | — | — |
| 10 | TSPO_ColourID | nvarchar(10) | NULL | — | — | — |
| 11 | TSPO_Colour_Description | nvarchar(50) | NULL | — | — | — |
| 12 | TSPO_SizeID | nvarchar(10) | NULL | — | — | — |
| 13 | TSPO_Size_Description | nvarchar(50) | NULL | — | — | — |
| 14 | TSPO_CreateDate | datetime | NULL | — | — | — |
| 15 | TSPO_ModifiedDate | datetime | NULL | — | — | — |
| 16 | TSPO_Line | nvarchar(50) | NULL | — | — | — |
| 17 | TSPO_Used | bit | NULL | — | DEFAULT ((0)) | — |
| 18 | TSPO_UsedTime | datetime | NULL | — | — | — |

### dbo.report_daily_target（表，7 字段）

| 序号 | 字段 | SQL 类型 | 可空 | 标识/计算 | 默认值或计算表达式 | 数据库注释 |
|---:|---|---|---|---|---|---|
| 1 | report_date | date | NOT NULL | — | — | — |
| 2 | line_no | smallint | NOT NULL | — | — | — |
| 3 | item_code | nvarchar(100) | NOT NULL | — | — | — |
| 4 | target_qty | int | NULL | — | — | — |
| 5 | emp_cnt | int | NULL | — | — | — |
| 6 | leader_name | nvarchar(50) | NULL | — | — | — |
| 7 | update_time | datetime | NOT NULL | — | DEFAULT (getdate()) | — |

### dbo.report_user_pwd（表，6 字段）

| 序号 | 字段 | SQL 类型 | 可空 | 标识/计算 | 默认值或计算表达式 | 数据库注释 |
|---:|---|---|---|---|---|---|
| 1 | emp_id | int | NOT NULL | — | — | — |
| 2 | pwd | varchar(100) | NOT NULL | — | — | — |
| 3 | is_leader | bit | NOT NULL | — | DEFAULT ((0)) | — |
| 4 | group_name | nvarchar(50) | NULL | — | — | — |
| 5 | display_name | nvarchar(50) | NULL | — | — | — |
| 6 | update_time | datetime | NOT NULL | — | DEFAULT (getdate()) | — |

### dbo.V_Hanger_Routing_Details（视图，39 字段）

| 序号 | 字段 | SQL 类型 | 可空 | 标识/计算 | 默认值或计算表达式 | 数据库注释 |
|---:|---|---|---|---|---|---|
| 1 | VHRD_HRD_Key | uniqueidentifier | NOT NULL | — | — | — |
| 2 | VHRD_HM_Lot_Number | nvarchar(100) | NULL | — | — | — |
| 3 | VHRD_HM_Item_Card | nvarchar(20) | NULL | — | — | — |
| 4 | VHRD_HM_Garments_Per_Hanger | smallint | NULL | — | — | — |
| 5 | VHRD_HRD_OC_Key | int | NULL | — | — | — |
| 6 | VHRD_HRD_EM_Key | int | NULL | — | — | — |
| 7 | VHRD_HRD_Line | smallint | NULL | — | — | — |
| 8 | VHRD_HRD_Workstation | smallint | NULL | — | — | — |
| 9 | VHRD_HRD_ODP_Credited | bit | NOT NULL | — | — | — |
| 10 | VHRD_HRD_Completed_Time | datetime | NULL | — | — | — |
| 11 | VHRD_Return_For_Repair | bit | NULL | — | — | — |
| 12 | VHRD_HRD_Started_Time | datetime | NULL | — | — | — |
| 13 | VHRD_ST_ID | nvarchar(25) | NULL | — | — | — |
| 14 | VHRD_ST_Description | nvarchar(50) | NULL | — | — | — |
| 15 | VHRD_ST_Collection | nvarchar(25) | NULL | — | — | — |
| 16 | VHRD_STPO_ID | nvarchar(50) | NULL | — | — | — |
| 17 | VHRD_ST_Fabric | nvarchar(25) | NULL | — | — | — |
| 18 | VHRD_ST_Trim | nvarchar(25) | NULL | — | — | — |
| 19 | VHRD_OC_Description | nvarchar(255) | NULL | — | — | — |
| 20 | VHRD_OC_Standard_Time | real | NULL | — | — | — |
| 21 | VHRD_OC_Piece_Rate | real | NULL | — | — | — |
| 22 | VHRD_PC_Description | nvarchar(25) | NULL | — | — | — |
| 23 | VHRD_EM_FirstName | nvarchar(50) | NULL | — | — | — |
| 24 | VHRD_EM_LastName | nvarchar(50) | NULL | — | — | — |
| 25 | VHRD_EM_LCD_Name | nvarchar(15) | NULL | — | — | — |
| 26 | VHRD_STOP_Number | nvarchar(50) | NULL | — | — | — |
| 27 | VHRD_HM_ID | nvarchar(50) | NOT NULL | — | — | — |
| 28 | VHRD_HM_Loaded_On_Line | smallint | NULL | — | — | — |
| 29 | VHRD_HM_Current_Line | smallint | NULL | — | — | — |
| 30 | VHRD_HRD_Comments | nvarchar(255) | NULL | — | — | — |
| 31 | VHRD_CM_Short_Description | nvarchar(10) | NULL | — | — | — |
| 32 | VHRD_CM_Description | nvarchar(255) | NULL | — | — | — |
| 33 | VHRD_SM_Short_Description | nvarchar(10) | NULL | — | — | — |
| 34 | VHRD_SM_Description | nvarchar(255) | NULL | — | — | — |
| 35 | VHRD_HM_Current_Location_Type | nvarchar(1) | NULL | — | — | — |
| 36 | VHRD_HM_Current_Location | smallint | NULL | — | — | — |
| 37 | VHRD_STOP_Order | smallint | NULL | — | — | — |
| 38 | VHRD_HM_Key | uniqueidentifier | NOT NULL | — | — | — |
| 39 | VHRD_EM_SSN | nvarchar(15) | NULL | — | — | — |

### dbo.V_ODP_Info（视图，42 字段）

| 序号 | 字段 | SQL 类型 | 可空 | 标识/计算 | 默认值或计算表达式 | 数据库注释 |
|---:|---|---|---|---|---|---|
| 1 | ODPI_Key | uniqueidentifier | NOT NULL | — | — | — |
| 2 | ODPI_Date | datetime | NULL | — | — | — |
| 3 | ODPI_EM_Key | int | NULL | — | — | — |
| 4 | ODPI_EM_FirstName | nvarchar(50) | NULL | — | — | — |
| 5 | ODPI_EM_LastName | nvarchar(50) | NULL | — | — | — |
| 6 | ODPI_EM_LCD_Name | nvarchar(15) | NULL | — | — | — |
| 7 | ODPI_Workstation | smallint | NULL | — | — | — |
| 8 | ODPI_Quantity | smallint | NULL | — | — | — |
| 9 | ODPI_ST_ID | nvarchar(25) | NULL | — | — | — |
| 10 | ODPI_ST_Description | nvarchar(50) | NULL | — | — | — |
| 11 | ODPI_Lot_Number | nvarchar(100) | NULL | — | — | — |
| 12 | ODPI_OC_Key | int | NOT NULL | — | — | — |
| 13 | ODPI_OC_Description | nvarchar(255) | NULL | — | — | — |
| 14 | ODPI_CM_Short_Description | nvarchar(10) | NULL | — | — | — |
| 15 | ODPI_SM_Short_Description | nvarchar(10) | NULL | — | — | — |
| 16 | ODPI_Piece_Rate | real | NULL | — | — | — |
| 17 | ODPI_OC_Standard_Time | real | NULL | — | — | — |
| 18 | ODPI_Actual_Time | int | NULL | — | — | — |
| 19 | ODPI_STOP_Order | smallint | NULL | — | — | — |
| 20 | ODPI_PC_Description | nvarchar(25) | NULL | — | — | — |
| 21 | ODPI_ST_Collection | nvarchar(25) | NULL | — | — | — |
| 22 | ODPI_CM_Description | nvarchar(255) | NULL | — | — | — |
| 23 | ODPI_SM_Description | nvarchar(255) | NULL | — | — | — |
| 24 | ODPI_OC_PA_Key | nvarchar(10) | NULL | — | — | — |
| 25 | ODPI_ODPD_PA_Key | nvarchar(10) | NULL | — | — | — |
| 26 | ODPI_PA_CurrencyValue | real | NULL | — | — | — |
| 27 | ODPI_ODPD_Is_Overtime | bit | NOT NULL | — | — | — |
| 28 | ODPI_MC_Type | nvarchar(25) | NULL | — | — | — |
| 29 | ODPI_STOP_Number | nvarchar(50) | NULL | — | — | — |
| 30 | ODPI_OC_Piece_Rate_Additional | real | NULL | — | — | — |
| 31 | ODPI_ODP_Actual_Clock_In | datetime | NULL | — | — | — |
| 32 | ODPI_ODP_Actual_Clock_Out | datetime | NULL | — | — | — |
| 33 | ODPI_EM_City | nvarchar(25) | NULL | — | — | — |
| 34 | ODPI_ST_Fabric | nvarchar(25) | NULL | — | — | — |
| 35 | ODPI_EM_RFID | nvarchar(10) | NULL | — | — | — |
| 36 | ODPI_EM_Department | nvarchar(15) | NULL | — | — | — |
| 37 | ODPI_EM_SSN | nvarchar(15) | NULL | — | — | — |
| 38 | ODPI_Start_Time | datetime | NULL | — | — | — |
| 39 | ODPI_ODP_LineNumber | int | NULL | — | — | — |
| 40 | ODPI_ODP_Shift | smallint | NULL | — | — | — |
| 41 | ODPI_STPO_ID | nvarchar(50) | NULL | — | — | — |
| 42 | GroupId | int | NULL | — | — | — |

### dbo.V_QC_Rework_Info（视图，42 字段）

| 序号 | 字段 | SQL 类型 | 可空 | 标识/计算 | 默认值或计算表达式 | 数据库注释 |
|---:|---|---|---|---|---|---|
| 1 | QCRI_Key | uniqueidentifier | NOT NULL | — | — | — |
| 2 | QCRI_Defect_EM_Key | int | NOT NULL | — | — | — |
| 3 | QCRI_Defect_EM_FirstName | nvarchar(50) | NULL | — | — | — |
| 4 | QCRI_Defect_EM_LastName | nvarchar(50) | NULL | — | — | — |
| 5 | QCRI_Defect_DateTime | datetime | NOT NULL | — | — | — |
| 6 | QCRI_Defect_Quantity | smallint | NOT NULL | — | — | — |
| 7 | QCRI_ST_ID | nvarchar(25) | NULL | — | — | — |
| 8 | QCRI_ST_Description | nvarchar(50) | NULL | — | — | — |
| 9 | QCRI_ST_Collection | nvarchar(25) | NULL | — | — | — |
| 10 | QCRI_STPO_ID | nvarchar(50) | NULL | — | — | — |
| 11 | QCRI_OC_Key | int | NOT NULL | — | — | — |
| 12 | QCRI_OC_Description | nvarchar(255) | NULL | — | — | — |
| 13 | QCRI_PC_Description | nvarchar(25) | NULL | — | — | — |
| 14 | QCRI_STOP_Number | nvarchar(50) | NULL | — | — | — |
| 15 | QCRI_CM_Short_Description | nvarchar(10) | NULL | — | — | — |
| 16 | QCRI_CM_Description | nvarchar(255) | NULL | — | — | — |
| 17 | QCRI_SM_Short_Description | nvarchar(10) | NULL | — | — | — |
| 18 | QCRI_SM_Description | nvarchar(255) | NULL | — | — | — |
| 19 | QCRI_QCSC_Key | uniqueidentifier | NULL | — | — | — |
| 20 | QCRI_QCSC_Description | nvarchar(255) | NULL | — | — | — |
| 21 | QCRI_QCSC_Is_Rework | bit | NULL | — | — | — |
| 22 | QCRI_QC_EM_Key | int | NOT NULL | — | — | — |
| 23 | QCRI_QC_EM_FirstName | nvarchar(50) | NULL | — | — | — |
| 24 | QCRI_QC_EM_LastName | nvarchar(50) | NULL | — | — | — |
| 25 | QCRI_QC_Station | smallint | NULL | — | — | — |
| 26 | QCRI_QC_DateTime | datetime | NULL | — | — | — |
| 27 | QCRI_Repair_EM_Key | int | NULL | — | — | — |
| 28 | QCRI_Repair_EM_FirstName | nvarchar(50) | NULL | — | — | — |
| 29 | QCRI_Repair_EM_LastName | nvarchar(50) | NULL | — | — | — |
| 30 | QCRI_Repair_DateTime | datetime | NULL | — | — | — |
| 31 | QCRI_Repair_Quantity | int | NULL | — | — | — |
| 32 | QCRI_QC_EM_Department | nvarchar(15) | NULL | — | — | — |
| 33 | QCRI_QC_EM_SSN | nvarchar(15) | NULL | — | — | — |
| 34 | QCRI_Defect_EM_Department | nvarchar(15) | NULL | — | — | — |
| 35 | QCRI_Defect_EM_SSN | nvarchar(15) | NULL | — | — | — |
| 36 | QCRI_Repair_EM_Department | nvarchar(15) | NULL | — | — | — |
| 37 | QCRI_Repair_EM_SSN | nvarchar(15) | NULL | — | — | — |
| 38 | QCRI_Defect_EM_LCD_Name | nvarchar(15) | NULL | — | — | — |
| 39 | QCRI_QC_EM_LCD_Name | nvarchar(15) | NULL | — | — | — |
| 40 | QCRI_Repair_EM_LCD_Name | nvarchar(15) | NULL | — | — | — |
| 41 | QCRI_LineNumber | smallint | NULL | — | — | — |
| 42 | GroupId | int | NULL | — | — | — |


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
| `Tag_Info` | 先在同步客户端确认上传来源、目标库、字段映射、启用状态及写入策略；再判断该客户端会自动建表还是要求按官方 DDL 预建。 | 客户端配置界面/配置文件、目标数据库、写入/更新方式、主键索引、重复上传处理和客户端日志。 |
| `V_ProductData` | 先确认客户端是否支持将吊挂端视图或查询结果上传到 IDS；若不支持，再评估客户端可读源数据并生成结果，或在 IDS 建兼容视图。 | 官方公式、工资口径、工时单位、班次日期边界、返工是否计件、时区及撤销规则；ODPI 当前工价不等于历史快照。 |
| `V_ReworkData` / `V_ScrapData` | 先核对同步客户端是否有对应对象上传配置及来源；再决定由吊挂软件提供、客户端转换上传或在 IDS 发布兼容视图。 | 衣架数/返工数/修复数/报废数口径、责任员工与站位定义、质检员归属、同件多缺陷去重规则。 |
| VHRD 缺失的 7R 字段 | 视图投影缺失；`HM_ST_Key`、`HM_CM_Key`、`HM_SM_Key`、`HM_STPO_Key`、`HM_SRM_Key`、`HM_Production_Time`、`ST_Line` 底层列在本实例存在，可评估在自有兼容视图补列。 | 官方字段类型、同步工具是否依赖列序/精确列集、别名与空值语义、厂商升级是否覆盖自定义视图。 |
| QCRI 衣架关联 | `P_QC_Rework` 有 `QCR_HM_ID` 和 `QCR_HM_Key`，当前 QCRI 未投影；可评估扩展兼容视图。 | 两键在缺陷、返修、换架和复检场景的含义；是否稳定指向事件发生时衣架；消费者是否支持新增列。 |
| `ToIna_Size` 无主键/索引 | 若厂商支持自定义约束，先做重复数据清理和款号+尺码编码验证，再讨论唯一约束/索引。 | 导入工具是否依赖物理列序、重复尺码兼容要求，以及线体同步和升级影响。 |

以上是补齐路径评估，不是已授权的数据库变更。本轮没有执行加表、加列、改视图或建索引。下一步应先检查吊挂同步客户端的上传对象、映射、启停、目标库及执行日志；确认客户端确实会写入且目标结构缺失后，再按其工作方式补表/视图或配置上传。不能仅复制 Excel 字段建表，因为客户端可能依赖字段映射、上传标志、状态位和重复记录处理规则。

## 11. 证据限制与下一步决策材料

- 本次已核对列、类型、空值、默认/计算列、数据库注释、索引、检查约束、外键、SQL 依赖及三个视图定义。对没有官方业务注释的字段，不能仅凭英文缩写声称已完全掌握语义；全字段清单保留数据库事实，缺少依据的业务含义需厂商确认。
- 未读取业务行明细，也未检查吊挂同步客户端的实际配置、上传日志、服务器级 SQL Agent 作业、外部同步服务或 INA 产线库。当前 IDS 用户对象扫描未发现存储过程、函数和触发器；这些对象范围外的运行逻辑仍待查。用户已确认“对象缺失或没有同步记录代表未配置上传”，但具体缺项和客户端配置位置尚未现场核实。
- 文档缺失的四个对象、VHRD/QCRI 投影差异与字段类型差异已分开列出。下一步要先决定是补齐 7R 通用兼容层，还是只满足 ERP 当前所需的 PO/生产追溯视图；两者变更范围和维护责任不同。

## 12. 资料来源

- [7R 标准公共数据库对接字典 V2.0](7R标准公共数据库对接字典说明V2.0.xlsx)
- [IDS 视图字段说明（2024-12-23）](IDS视图字段说明-20241223.xlsx)
- [衣拿吊挂 7R 与 IDS 中继库字段核对与理解](衣拿吊挂7R与IDS中继库字段核对与理解.md)
- [生产流程规划第 15.14 节](../../规划/系统与流程/生产流程规划.md)
