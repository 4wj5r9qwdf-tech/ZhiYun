<!--
文件用途：记录衣拿吊挂三号线四个 SQL Server 数据库的只读结构盘点、关键业务关联和 IDS 中继映射差异。
文件版本：1.2
功能目的：让 ERP 吊挂对接和 PO 标签打印设计基于三号线实际数据库结构，明确数据来源与尚待业务验证的状态语义。
设计思路：以 SQL Server 系统目录中的对象、字段、索引、约束和视图定义为结构事实；不把字段名或视图名推断成已经验证的业务行为。
实现方法：只读读取四个数据库的目录元数据和视图 SQL 定义，并与 IDS 中继库的 T_TableInfo 和视图字段清单交叉核对；补充静态检查用户提供的同步客户端本地编译副本以确认上传路径；不读取员工生产明细或连接配置值。
关键约束：本快照代表 2026-11-25 核对到的三号线数据库实例；不记录 IP、用户名、密码，不执行任何写入、同步或业务查询。
依赖与引用：Docs/技术参考/数据库/IDS中继库全量结构盘点与文档差异.md；Docs/技术参考/数据库/衣拿吊挂7R与IDS中继库字段核对与理解.md；Docs/技术参考/数据库/7R标准公共数据库对接字典说明V2.0.xlsx；Docs/技术参考/数据库/IDS视图字段说明-20241223.xlsx。
当前责任人：JY
本次整理人员：Codex（智能体，依据 JY 提供的只读连接资料核对）
本次整理时间：2026-11-25
最近修改人员：Codex（智能体）
最近修改时间：2026-11-25
修改标记：ZY-ERP-20261125-三号线四库结构与 IDS 视图差异、同步客户端和核心回传视图组成核对
最近测试时间：2026-11-25
测试人员：Codex（智能体）
测试状态：SQL Server 2016 SP2 四库系统目录只读核对；字段、索引、外键、检查约束、存储过程、视图依赖与 IDS 映射比对通过；补充检查本地同步客户端编译程序集与配置键；未读取生产数据、未执行写入或同步。
追加视图核对时间：2026-11-25
追加视图核对状态：展开 IHS_OPEN 两个视图的实际 SQL SELECT/JOIN 组成，与 IDS 中继库视图字段清单和已记录的 IDS 视图过滤/关联进行对照；识别跨库来源、员工字段继承位置、PO键投影差异、无 WHERE/GROUP BY，以及按款式+工序编码连接可能匹配多个路线节点的结构风险。未读取业务行验证风险是否已实际触发。
-->
# 三号线吊挂数据库结构与 IDS 映射核对

> 核对日期：2026-11-25。只读核对用户提供的三号线 SQL Server；数据库引擎版本与实际连接返回的版本行一致。本文只记录结构和业务关系线索，不记录连接 IP、账号或密码。

## 1. 实例与四库结构概览

实际 SQL Server 版本为 Microsoft SQL Server 2016 SP2（13.0.5026.0）。四个数据库的兼容级别均为 100，排序规则均为 `Latin1_General_CI_AS`。数据库兼容级别与引擎版本是两个不同信息，后续 SQL 兼容性应同时考虑。

| 数据库 | 用户表 | 视图 | 表/视图字段 | 索引 | 外键 | 检查约束 | 存储过程 | SQL 注释字段 |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| `IHS` | 47 | 0 | 377 | 91 | 4 | 0 | 15 | 23 |
| `IHS_HISTORY` | 2 | 0 | 31 | 13 | 0 | 0 | 0 | 10 |
| `IHS_OPEN` | 3 | 2 | 133 | 3 | 0 | 0 | 7 | 10 |
| `IHS_SHARED` | 42 | 0 | 335 | 85 | 22 | 5 | 3 | 18 |
| **合计** | **94** | **2** | **876** | **192** | **26** | **5** | **25** | **61** |

四库共盘点到 94 张用户表、2 个视图、876 个字段、192 个索引、26 个外键、5 个检查约束和 25 个存储过程。字段 SQL 注释仅覆盖 61 列；其余字段的业务含义不能仅凭名称视为已确认。四库未发现用户触发器或同义词。

### 四库职责线索

- `IHS`：吊架当前状态、吊架工序事件、生产报工明细以及线体运行配置表。
- `IHS_HISTORY`：吊架及其工序路线历史归档表。库名与表结构显示其用于历史记录；本轮没有读取历史业务行或验证归档时机。
- `IHS_OPEN`：包含对外产量/返修视图和生产相关表；其中两个视图跨库读取 `IHS` 与 `IHS_SHARED`。
- `IHS_SHARED`：款式、PO/线计划、员工、工序、颜色尺码、返工/返修/报废等共享资料与业务表。

## 2. 对 PO 标签和衣架追溯有用的关系

### 2.1 当前吊架与 PO/线计划

`IHS.dbo.Hanger_Master` 有 `HM_ID`、`HM_Key`、`HM_ST_Key`、`HM_STPO_Key`、`HM_CM_Key`、`HM_SM_Key`、`HM_Lot_Number`、`HM_Current_Line`、`HM_Enter_Station` 和 `HM_Enter_Time` 等字段。`IHS_SHARED.dbo.Style_Planned_Orders` 以 `STPO_Key` 为身份键，包含 `STPO_ID`、款式键、颜色/尺码键、计划数量及计划日期。因此结构上可通过 `HM_STPO_Key → STPO_Key` 取得吊架当前关联的 PO/线计划编号 `STPO_ID`，款号/颜色/尺码也有对应键。该跨数据库关系没有 SQL 外键，本轮只确认字段类型和对象定义，未读取记录验证实际编码/空值。

### 2.2 吊架上的工序事件

`IHS.dbo.Hanger_Routing_Details` 记录 `HRD_HM_ID`、工序键 `HRD_OC_Key`、员工键 `HRD_EM_Key`、产线 `HRD_Line`、工位 `HRD_Workstation`、开始/完成时间、`HRD_OC_Completed`、返修标志和质量相关标记。运行中的事件可尝试通过 `HRD_HM_ID → Hanger_Master.HM_ID` 找到当前吊架状态，再由吊架主表关联到计划单。事件表没有 `HM_Key`/`STPO_Key` 字段，且未发现该跨表关系的外键；如果实体吊架在一件衣服完成后复用，延迟回查历史事件时不能只凭当前 `HM_ID` 假定仍指向原衣服。

### 2.3 历史归档中的稳定关联键

`IHS_HISTORY.dbo.Hanger_Master_History` 有归档键 `HMH_Key` 和 `HMH_STPO_Key`；`Hanger_Routing_Details_History` 有 `HRDH_HMH_Key`、工序、员工、工位和时间字段。结构上可按 `HRDH_HMH_Key → HMH_Key` 将归档后的工序事件关联到当时保存的吊架历史主记录，避免只用可复用的物理吊架号。两个历史表未建 SQL 外键；是否每次归档都完整写入此关联，仍需用授权的脱敏记录验证。

### 2.4 产量和返修视图的粒度限制

`IHS_OPEN.dbo.V_ODP_Info` 显式从 `IHS_SHARED.dbo.Style_Planned_Orders.STPO_ID` 输出 `ODPI_STPO_Number`，也输出计划内部键 `ODPI_ODPD_STPO_Key`、款色码、工序、报工数量、员工/工位/线路及时间。它可提供 PO 维度的工序报工信息，但视图没有吊架 ID/Key，粒度是报工/工序明细，不能仅凭该视图确定某一件吊架的标签。

`IHS_SHARED.dbo.QC_Rework`、`QC_Redo`、`QC_Scrap` 表含吊架/计划相关字段；`IHS_OPEN.dbo.V_QC_Rework_Info` 输出返修/缺陷、员工、数量、时间和 `QCRI_STPO_Key`，但没有投影 `QCR_HM_ID` 或 `QCR_HM_Key`，也没有明确的尾查通过字段。`IHS.dbo.Hanger_Quality_Control_Incident` 的字段描述缺陷、维修/报废和返工数量；结构中未发现一张明确的“尾查合格/放行事件”表。尾查是否通过某个工序完成事件表示，必须结合工序主数据和真实流程确认。

`IHS_OPEN` 的两个视图还含员工城市、部门、RFID、社会保险号等个人信息字段。PO 标签工具应按需投影少量字段，避免整视图读取、保存或打印与标签无关的个人资料。

## 3. 与当前 IDS 中继库的字段/映射差异

### 3.1 常规表映射覆盖范围

按当前 IDS `T_TableInfo` 聚合名称清单对照，三号线 `IHS` 有 14 个表名出现在常规映射中，`IHS_SHARED` 有 27 个；合计 41 个常规源表名。这个结果符合“各产线常规映射相同”的用户口径。

`IHS_HISTORY` 的两张历史表和 `IHS_OPEN` 的三张表/两个视图都不在 IDS 当前 `T_TableInfo` 常规源表名清单中。对本地同步客户端编译副本做静态检查后，已确认它存在独立的历史衣架上传方法，并存在使用专用查询模板的 OPEN 类上传线程；具体入口和限制见第 9 节。`PlanData`、`ReworkTable`、`QCDownLoad` 等配置开关与这些通道的逐项关系仍需结合运行配置或厂商说明确认。

### 3.2 同名视图不是同一字段契约

当前 IDS 中继库的 `V_ODP_Info` 有 42 字段，三号线 `IHS_OPEN.V_ODP_Info` 有 47 字段；`V_QC_Rework_Info` 也分别为 42 和 47 字段。逐字段比较如下：

| 视图 | 三号线 IHS_OPEN 独有字段 | IDS 中继库独有字段 | 同名字段类型差异 |
|---|---|---|---|
| `V_ODP_Info` | `ODPI_Line_Number`, `ODPI_ODPD_STPO_Key`, `ODPI_PA_CategoryType`, `ODPI_PA_Description`, `ODPI_STPO_Number`, `ODPI_ST_Key`, `ODPI_ST_Trim`, `ODPI_WC_Description`, `ODPI_WC_Key` | `GroupId`, `ODPI_OC_PA_Key`, `ODPI_ODP_LineNumber`, `ODPI_STPO_ID` | `ODPI_Lot_Number`：nvarchar(50) vs nvarchar(100) |
| `V_QC_Rework_Info` | `QCRI_CM_Key`, `QCRI_Defect_EM_City`, `QCRI_QC_EM_City`, `QCRI_Repair_EM_City`, `QCRI_SM_Key`, `QCRI_STPO_Key`, `QCRI_ST_Key` | `GroupId`, `QCRI_STPO_ID` | — |

PO 编号字段尤其需要区分：三号线产量视图字段叫 `ODPI_STPO_Number`，来源明确为 `Style_Planned_Orders.STPO_ID`；IDS 中继视图字段叫 `ODPI_STPO_ID`。这两个字段可能对应同一业务编号，但中继映射/转换关系需以同步客户端实现或实际脱敏行验证。三号线返修视图输出的是整数 `QCRI_STPO_Key`；IDS 视图字段为 `QCRI_STPO_ID`，类型与名称都不同，不能按字段名直接映射。

## 4. 数据库对象与字段全量清单

下列字段及 SQL 属性来自系统目录。`MS_Description` 有值时保留原注释；无注释时写“—”，代表本实例没有字段级 SQL 注释，不代表字段没有业务用途。行数、员工和生产业务数据均未查询。

## 4.1 `IHS`

兼容级别：{d["info"]["compatibility_level"]}；排序规则：`{d["info"]["collation_name"]}`。

### dbo.Accessories_Routing（表，7 字段）

| 序号 | 字段 | SQL 类型 | 可空 | 标识列 | 计算列 | 默认表达式 | SQL 注释 |
|---:|---|---|---|---|---|---|---|
| 1 | `ACC_Key` | `uniqueidentifier` | 否 | 否 | 否 | — | — |
| 2 | `ACC_Send_Time` | `datetime` | 否 | 否 | 否 | — | — |
| 3 | `ACC_Send_Station` | `smallint` | 否 | 否 | 否 | — | — |
| 4 | `ACC_Sent_By_EM_Key` | `int` | 否 | 否 | 否 | — | — |
| 5 | `ACC_Destination_Station` | `smallint` | 否 | 否 | 否 | — | — |
| 6 | `ACC_Destination_Send_Time` | `datetime` | 是 | 否 | 否 | — | — |
| 7 | `ACC_QCSC_Key` | `uniqueidentifier` | 否 | 否 | 否 | — | — |

### dbo.Clock_InOut_History（表，5 字段）

| 序号 | 字段 | SQL 类型 | 可空 | 标识列 | 计算列 | 默认表达式 | SQL 注释 |
|---:|---|---|---|---|---|---|---|
| 1 | `CIOH_Key` | `bigint` | 否 | 是 | 否 | — | — |
| 2 | `CIOH_ODP_Key` | `uniqueidentifier` | 否 | 否 | 否 | — | — |
| 3 | `CIOH_Clock_In_Time` | `datetime` | 否 | 否 | 否 | — | — |
| 4 | `CIOH_Clock_Out_Time` | `datetime` | 是 | 否 | 否 | — | — |
| 5 | `CIOH_Workstation` | `int` | 是 | 否 | 否 | — | — |

### dbo.Database_Tables_Last_Modified（表，6 字段）

| 序号 | 字段 | SQL 类型 | 可空 | 标识列 | 计算列 | 默认表达式 | SQL 注释 |
|---:|---|---|---|---|---|---|---|
| 1 | `Row_Index` | `int` | 否 | 否 | 否 | — | — |
| 2 | `Style_Loading` | `datetime` | 是 | 否 | 否 | — | — |
| 6 | `Quality_Control_Random` | `datetime` | 是 | 否 | 否 | — | — |
| 7 | `Quality_Control_First` | `datetime` | 是 | 否 | 否 | — | — |
| 8 | `Office_Computers_Connected` | `datetime` | 是 | 否 | 否 | — | — |
| 19 | `Hanger_Master` | `datetime` | 是 | 否 | 否 | — | — |

### dbo.Database_Version（表，1 字段）

| 序号 | 字段 | SQL 类型 | 可空 | 标识列 | 计算列 | 默认表达式 | SQL 注释 |
|---:|---|---|---|---|---|---|---|
| 4 | `Database_Version` | `nvarchar(10)` | 是 | 否 | 否 | — | — |

### dbo.Double_Screen_Styles（表，9 字段）

| 序号 | 字段 | SQL 类型 | 可空 | 标识列 | 计算列 | 默认表达式 | SQL 注释 |
|---:|---|---|---|---|---|---|---|
| 1 | `DSS_Key` | `uniqueidentifier` | 否 | 否 | 否 | (newid()) | — |
| 2 | `DSS_ST_Key` | `int` | 否 | 否 | 否 | — | — |
| 3 | `DSS_Target` | `int` | 是 | 否 | 否 | — | — |
| 4 | `DSS_Show_Average_Salary` | `bit` | 是 | 否 | 否 | — | — |
| 5 | `DSS_Number_Of_Operators` | `int` | 是 | 否 | 否 | — | — |
| 6 | `DSS_Cost_Per_Garment` | `real` | 是 | 否 | 否 | — | — |
| 7 | `DSS_Calculation_Type` | `int` | 是 | 否 | 否 | — | — |
| 8 | `DSS_Start_Time` | `datetime` | 是 | 否 | 否 | — | — |
| 9 | `DSS_End_Time` | `datetime` | 是 | 否 | 否 | — | — |

### dbo.Hanger_Details_Export（表，2 字段）

| 序号 | 字段 | SQL 类型 | 可空 | 标识列 | 计算列 | 默认表达式 | SQL 注释 |
|---:|---|---|---|---|---|---|---|
| 1 | `HDE_HRD_Key` | `uniqueidentifier` | 否 | 否 | 否 | — | — |
| 2 | `HDE_DateTime` | `datetime` | 否 | 否 | 否 | — | — |

### dbo.Hanger_Master（表，24 字段）

| 序号 | 字段 | SQL 类型 | 可空 | 标识列 | 计算列 | 默认表达式 | SQL 注释 |
|---:|---|---|---|---|---|---|---|
| 1 | `HM_ID` | `nvarchar(12)` | 否 | 否 | 否 | — | — |
| 2 | `HM_Loaded_On_Line` | `smallint` | 是 | 否 | 否 | ((0)) | — |
| 3 | `HM_Current_Line` | `smallint` | 是 | 否 | 否 | ((0)) | — |
| 4 | `HM_Current_Location_Type` | `nvarchar(1)` | 是 | 否 | 否 | — | P = Pusher, W = Workstation, S = workstation Staging area |
| 5 | `HM_Current_Location` | `smallint` | 是 | 否 | 否 | ((0)) | — |
| 6 | `HM_SRM_Key` | `int` | 是 | 否 | 否 | ((0)) | Style Route Master Key that this is following |
| 7 | `HM_ST_Key` | `int` | 是 | 否 | 否 | ((0)) | Style Key for the style taht was loaded on this hanger |
| 8 | `HM_STPO_Key` | `int` | 是 | 否 | 否 | — | Style PO Key |
| 9 | `HM_Lot_Number` | `nvarchar(50)` | 是 | 否 | 否 | — | Style Lot number |
| 10 | `HM_CM_Key` | `int` | 是 | 否 | 否 | — | Colour Master Key |
| 11 | `HM_SM_Key` | `int` | 是 | 否 | 否 | — | Size Master Key |
| 12 | `HM_Garments_Per_Hanger` | `smallint` | 是 | 否 | 否 | — | — |
| 13 | `HM_Production_Time` | `real` | 是 | 否 | 否 | — | — |
| 18 | `HM_Item_Card` | `nvarchar(20)` | 是 | 否 | 否 | — | — |
| 21 | `HM_PI_Key` | `int` | 是 | 否 | 否 | — | — |
| 22 | `HM_Routing_Info` | `nvarchar(50)` | 是 | 否 | 否 | — | — |
| 23 | `HM_Is_Scrapped` | `bit` | 是 | 否 | 否 | — | — |
| 24 | `HM_ACC_Key` | `uniqueidentifier` | 是 | 否 | 否 | — | — |
| 25 | `HM_Key` | `uniqueidentifier` | 是 | 否 | 否 | — | — |
| 28 | `HM_SP_Key` | `int` | 是 | 否 | 否 | — | — |
| 33 | `HM_Label` | `nvarchar(12)` | 是 | 否 | 否 | — | — |
| 34 | `HM_Enter_Station` | `int` | 是 | 否 | 否 | — | — |
| 35 | `HM_Enter_Time` | `datetime` | 是 | 否 | 否 | — | — |
| 36 | `HM_OC_List` | `nvarchar(900)` | 是 | 否 | 否 | — | — |

### dbo.Hanger_Quality_Control_Incident（表，10 字段）

| 序号 | 字段 | SQL 类型 | 可空 | 标识列 | 计算列 | 默认表达式 | SQL 注释 |
|---:|---|---|---|---|---|---|---|
| 1 | `HQCI_Key` | `uniqueidentifier` | 否 | 否 | 否 | (newid()) | — |
| 2 | `HQCI_HM_ID` | `nvarchar(12)` | 是 | 否 | 否 | — | — |
| 3 | `HQCI_Date` | `datetime` | 是 | 否 | 否 | — | — |
| 4 | `HQCI_EM_Key` | `int` | 是 | 否 | 否 | — | — |
| 5 | `HQCI_ST_Key` | `int` | 是 | 否 | 否 | — | — |
| 6 | `HQCI_OC_Key` | `int` | 是 | 否 | 否 | — | — |
| 7 | `HQCI_QCSC_Key` | `uniqueidentifier` | 是 | 否 | 否 | (newid()) | — |
| 8 | `HQCI_Repair` | `bit` | 否 | 否 | 否 | ((0)) | — |
| 9 | `HQCI_Scrap` | `bit` | 是 | 否 | 否 | — | — |
| 10 | `HQCI_Rework_Quantity` | `smallint` | 是 | 否 | 否 | — | — |

### dbo.Hanger_Routing_Details（表，17 字段）

| 序号 | 字段 | SQL 类型 | 可空 | 标识列 | 计算列 | 默认表达式 | SQL 注释 |
|---:|---|---|---|---|---|---|---|
| 1 | `HRD_Key` | `uniqueidentifier` | 否 | 否 | 否 | (newid()) | — |
| 2 | `HRD_HM_ID` | `nvarchar(12)` | 是 | 否 | 否 | — | The Hanger Id |
| 3 | `HRD_OC_Key` | `int` | 是 | 否 | 否 | ((0)) | The Operation Code that was completed |
| 4 | `HRD_EM_Key` | `int` | 是 | 否 | 否 | ((0)) | The Employee that completed the operation |
| 5 | `HRD_Line` | `smallint` | 是 | 否 | 否 | ((0)) | The Line it was completed on |
| 6 | `HRD_Workstation` | `smallint` | 是 | 否 | 否 | ((0)) | The station it was completed at |
| 7 | `HRD_Completed_Time` | `datetime` | 是 | 否 | 否 | — | — |
| 8 | `HRD_ODP_Credited` | `bit` | 否 | 否 | 否 | ((0)) | — |
| 9 | `HRD_Comments` | `nvarchar(255)` | 是 | 否 | 否 | — | — |
| 10 | `HRD_WC_Key` | `smallint` | 是 | 否 | 否 | — | — |
| 11 | `HRD_Started_Time` | `datetime` | 是 | 否 | 否 | — | — |
| 14 | `HRD_PID_Key` | `int` | 是 | 否 | 否 | — | — |
| 15 | `HRD_Return_For_Repair` | `bit` | 是 | 否 | 否 | — | — |
| 16 | `HRD_Create_Time` | `datetime` | 是 | 否 | 否 | — | — |
| 17 | `HRD_OC_Completed` | `bit` | 是 | 否 | 否 | — | — |
| 18 | `HRD_Enter_Time` | `datetime` | 是 | 否 | 否 | — | — |
| 19 | `HRD_Defect_Order` | `smallint` | 是 | 否 | 否 | — | — |

### dbo.Hangers_Dispatched（表，3 字段）

| 序号 | 字段 | SQL 类型 | 可空 | 标识列 | 计算列 | 默认表达式 | SQL 注释 |
|---:|---|---|---|---|---|---|---|
| 1 | `HD_HM_ID` | `nvarchar(12)` | 否 | 否 | 否 | — | — |
| 2 | `HD_Station` | `smallint` | 否 | 否 | 否 | — | — |
| 3 | `HD_OC_Key` | `int` | 否 | 否 | 否 | — | — |

### dbo.Hangers_To_Synch（表，2 字段）

| 序号 | 字段 | SQL 类型 | 可空 | 标识列 | 计算列 | 默认表达式 | SQL 注释 |
|---:|---|---|---|---|---|---|---|
| 1 | `Hanger_ID` | `nvarchar(12)` | 否 | 否 | 否 | — | — |
| 3 | `Garments_Per_Hanger` | `smallint` | 是 | 否 | 否 | — | — |

### dbo.Inhibition_Interface（表，1 字段）

| 序号 | 字段 | SQL 类型 | 可空 | 标识列 | 计算列 | 默认表达式 | SQL 注释 |
|---:|---|---|---|---|---|---|---|
| 1 | `Inhibit_Interface` | `bit` | 否 | 否 | 否 | ((0)) | — |

### dbo.Line_Break_Times（表，5 字段）

| 序号 | 字段 | SQL 类型 | 可空 | 标识列 | 计算列 | 默认表达式 | SQL 注释 |
|---:|---|---|---|---|---|---|---|
| 1 | `Break_Number` | `smallint` | 否 | 否 | 否 | ((0)) | — |
| 2 | `Break_Start_Time` | `datetime` | 是 | 否 | 否 | — | — |
| 3 | `Break_End_Time` | `datetime` | 是 | 否 | 否 | — | — |
| 4 | `Break_Allow_To_Send_Hanger` | `bit` | 否 | 否 | 否 | ((0)) | — |
| 5 | `Break_Automatic_Logout` | `bit` | 是 | 否 | 否 | — | — |

### dbo.Line_Daily_Break_Times（表，6 字段）

| 序号 | 字段 | SQL 类型 | 可空 | 标识列 | 计算列 | 默认表达式 | SQL 注释 |
|---:|---|---|---|---|---|---|---|
| 1 | `Break_Date` | `datetime` | 否 | 否 | 否 | — | — |
| 2 | `Break_Number` | `smallint` | 否 | 否 | 否 | ((0)) | — |
| 3 | `Break_Start_Time` | `datetime` | 是 | 否 | 否 | — | — |
| 4 | `Break_End_Time` | `datetime` | 是 | 否 | 否 | — | — |
| 5 | `Break_Allow_To_Send_Hanger` | `bit` | 否 | 否 | 否 | ((0)) | — |
| 6 | `Break_Automatic_Logout` | `bit` | 是 | 否 | 否 | — | — |

### dbo.Line_Daily_Shift_Times（表，9 字段）

| 序号 | 字段 | SQL 类型 | 可空 | 标识列 | 计算列 | 默认表达式 | SQL 注释 |
|---:|---|---|---|---|---|---|---|
| 1 | `Shift_Date` | `datetime` | 否 | 否 | 否 | — | — |
| 2 | `Shift_Number` | `smallint` | 否 | 否 | 否 | ((0)) | — |
| 3 | `Shift_Start_Time` | `datetime` | 是 | 否 | 否 | — | — |
| 4 | `Shift_End_Time` | `datetime` | 是 | 否 | 否 | — | — |
| 5 | `Shift_Normal_Pay_Factor` | `real` | 是 | 否 | 否 | ((0)) | — |
| 6 | `Shift_Hours_Before_Overtime` | `real` | 是 | 否 | 否 | ((8)) | — |
| 7 | `Shift_Overtime_Pay_Factor1` | `real` | 是 | 否 | 否 | ((1)) | — |
| 8 | `Shift_Overtime_Pay_Factor2` | `real` | 是 | 否 | 否 | ((1)) | — |
| 9 | `Shift_Overtime_Pay_Factor3` | `real` | 是 | 否 | 否 | ((1)) | — |

### dbo.Line_Detour_Setup（表，15 字段）

| 序号 | 字段 | SQL 类型 | 可空 | 标识列 | 计算列 | 默认表达式 | SQL 注释 |
|---:|---|---|---|---|---|---|---|
| 1 | `LD_Station` | `smallint` | 否 | 否 | 否 | — | — |
| 2 | `LD_Description` | `nvarchar(20)` | 否 | 否 | 否 | — | — |
| 3 | `LD_LQT_Station` | `smallint` | 否 | 否 | 否 | — | — |
| 4 | `LD_Type` | `smallint` | 否 | 否 | 否 | — | — |
| 5 | `LD_HubOrGroup` | `smallint` | 否 | 否 | 否 | — | — |
| 6 | `LD_PositionOrDevice` | `smallint` | 否 | 否 | 否 | — | — |
| 7 | `LD_In_Feed_Pushers_From_Sensor` | `smallint` | 否 | 否 | 否 | — | — |
| 8 | `LD_In_Feed_Distance_To_Next_Pusher` | `real` | 否 | 否 | 否 | — | — |
| 9 | `LD_Out_Feed_Pushers_From_Sensor` | `smallint` | 否 | 否 | 否 | — | — |
| 10 | `LD_Out_Feed_Distance_To_Next_Pusher` | `real` | 否 | 否 | 否 | — | — |
| 11 | `LD_Number_Of_Pushers_Before_QT_Infeed` | `smallint` | 否 | 否 | 否 | — | — |
| 12 | `LD_Full_Quantity` | `smallint` | 否 | 否 | 否 | — | — |
| 13 | `LD_Quantity_At_Station` | `int` | 否 | 否 | 否 | — | — |
| 14 | `LD_Is_Hardware_Full` | `bit` | 否 | 否 | 否 | — | — |
| 15 | `LD_Enabled` | `bit` | 否 | 否 | 否 | — | — |

### dbo.Line_Information（表，4 字段）

| 序号 | 字段 | SQL 类型 | 可空 | 标识列 | 计算列 | 默认表达式 | SQL 注释 |
|---:|---|---|---|---|---|---|---|
| 1 | `Date_Of_Configuration` | `datetime` | 否 | 否 | 否 | — | — |
| 2 | `Line_Number` | `smallint` | 是 | 否 | 否 | — | — |
| 3 | `Line_Description` | `nvarchar(50)` | 是 | 否 | 否 | — | — |
| 4 | `Stations` | `smallint` | 是 | 否 | 否 | — | — |

### dbo.Line_Network_Setup（表，15 字段）

| 序号 | 字段 | SQL 类型 | 可空 | 标识列 | 计算列 | 默认表达式 | SQL 注释 |
|---:|---|---|---|---|---|---|---|
| 1 | `Date_Of_Configuration` | `datetime` | 否 | 否 | 否 | — | — |
| 2 | `Operating_System` | `nvarchar(20)` | 是 | 否 | 否 | — | — |
| 3 | `Interface` | `nvarchar(20)` | 是 | 否 | 否 | — | — |
| 4 | `Device_Name` | `nvarchar(20)` | 是 | 否 | 否 | — | — |
| 5 | `Cimetrics_Baud_Rate` | `int` | 是 | 否 | 否 | ((0)) | — |
| 6 | `Cimetrics_Retries` | `smallint` | 是 | 否 | 否 | ((0)) | — |
| 7 | `Cimetrics_Slave_Response` | `smallint` | 是 | 否 | 否 | ((0)) | — |
| 8 | `Cimetrics_Transmit_Timeout` | `smallint` | 是 | 否 | 否 | ((0)) | — |
| 9 | `Reader_Type` | `nvarchar(50)` | 是 | 否 | 否 | — | — |
| 10 | `Sensor_Hanger_Scan_COM_Port` | `smallint` | 是 | 否 | 否 | — | — |
| 11 | `Sensor_Hanger_Scan` | `bit` | 是 | 否 | 否 | — | — |
| 12 | `Console_Hardware` | `nvarchar(8)` | 是 | 否 | 否 | — | — |
| 13 | `Use_Mobile_Console` | `bit` | 是 | 否 | 否 | — | — |
| 14 | `Mobile_Console_Group_Number` | `smallint` | 是 | 否 | 否 | — | — |
| 15 | `Mobile_Console_Device_Number` | `smallint` | 是 | 否 | 否 | — | — |

### dbo.Line_OverLoad_Stations（表，1 字段）

| 序号 | 字段 | SQL 类型 | 可空 | 标识列 | 计算列 | 默认表达式 | SQL 注释 |
|---:|---|---|---|---|---|---|---|
| 1 | `OverLoad_Station` | `smallint` | 否 | 否 | 否 | ((0)) | — |

### dbo.Line_Overload_Stations_Specific_Operation（表，1 字段）

| 序号 | 字段 | SQL 类型 | 可空 | 标识列 | 计算列 | 默认表达式 | SQL 注释 |
|---:|---|---|---|---|---|---|---|
| 1 | `Overload_Station` | `smallint` | 否 | 否 | 否 | — | — |

### dbo.Line_Pusher_RFID（表，2 字段）

| 序号 | 字段 | SQL 类型 | 可空 | 标识列 | 计算列 | 默认表达式 | SQL 注释 |
|---:|---|---|---|---|---|---|---|
| 1 | `PR_RFID` | `nvarchar(8)` | 否 | 否 | 否 | — | — |
| 2 | `PR_Pusher` | `smallint` | 否 | 否 | 否 | — | — |

### dbo.Line_Pusher_Setup（表，14 字段）

| 序号 | 字段 | SQL 类型 | 可空 | 标识列 | 计算列 | 默认表达式 | SQL 注释 |
|---:|---|---|---|---|---|---|---|
| 1 | `Date_Of_Configuration` | `datetime` | 否 | 否 | 否 | — | — |
| 2 | `Units_Of_Measurement` | `nvarchar(10)` | 是 | 否 | 否 | — | — |
| 3 | `Number_of_Pushers` | `int` | 是 | 否 | 否 | — | — |
| 4 | `Distance_Between_Pushers` | `real` | 是 | 否 | 否 | — | — |
| 5 | `Time_between_pushers` | `int` | 是 | 否 | 否 | — | — |
| 6 | `Sensor_Timeout` | `int` | 是 | 否 | 否 | — | — |
| 7 | `InFeed_Duration` | `smallint` | 是 | 否 | 否 | — | — |
| 8 | `OutFeed_Duration` | `smallint` | 是 | 否 | 否 | — | — |
| 9 | `Pusher_Present_On_High_Bit` | `bit` | 否 | 否 | 否 | — | — |
| 10 | `Interface_Output_Address` | `int` | 是 | 否 | 否 | — | — |
| 11 | `Interface_Input_Address` | `int` | 是 | 否 | 否 | — | — |
| 12 | `Ignore_Pushers` | `int` | 是 | 否 | 否 | — | — |
| 13 | `Pusher_Sensor_Type` | `smallint` | 是 | 否 | 否 | — | — |
| 14 | `Pusher_RFID_COM_Port` | `smallint` | 是 | 否 | 否 | — | — |

### dbo.Line_QC_Stations（表，1 字段）

| 序号 | 字段 | SQL 类型 | 可空 | 标识列 | 计算列 | 默认表达式 | SQL 注释 |
|---:|---|---|---|---|---|---|---|
| 1 | `QC_Station` | `smallint` | 否 | 否 | 否 | ((0)) | — |

### dbo.Line_Quickturn_Setup（表，13 字段）

| 序号 | 字段 | SQL 类型 | 可空 | 标识列 | 计算列 | 默认表达式 | SQL 注释 |
|---:|---|---|---|---|---|---|---|
| 1 | `LQT_Station` | `smallint` | 否 | 否 | 否 | — | — |
| 2 | `LQT_Description` | `nvarchar(20)` | 否 | 否 | 否 | — | — |
| 3 | `LQT_Type` | `smallint` | 否 | 否 | 否 | — | — |
| 4 | `LQT_HubOrGroup` | `smallint` | 否 | 否 | 否 | — | — |
| 5 | `LQT_PositionOrDevice` | `smallint` | 否 | 否 | 否 | — | — |
| 6 | `LQT_In_Feed_Pushers_From_Sensor` | `smallint` | 否 | 否 | 否 | — | — |
| 7 | `LQT_In_Feed_Distance_To_Next_Pusher` | `real` | 否 | 否 | 否 | — | — |
| 8 | `LQT_Out_Feed_Pushers_From_Sensor` | `smallint` | 否 | 否 | 否 | — | — |
| 9 | `LQT_Out_Feed_Distance_To_Next_Pusher` | `real` | 否 | 否 | 否 | — | — |
| 10 | `LQT_Full_Quantity` | `smallint` | 否 | 否 | 否 | — | — |
| 11 | `LQT_Quantity_At_Station` | `int` | 否 | 否 | 否 | — | — |
| 12 | `LQT_Is_Hardware_Full` | `bit` | 否 | 否 | 否 | — | — |
| 13 | `LQT_Enabled` | `bit` | 否 | 否 | 否 | — | — |

### dbo.Line_Rework_Stations（表，2 字段）

| 序号 | 字段 | SQL 类型 | 可空 | 标识列 | 计算列 | 默认表达式 | SQL 注释 |
|---:|---|---|---|---|---|---|---|
| 1 | `LRS_Workstation` | `smallint` | 否 | 否 | 否 | — | — |
| 2 | `LRS_QCHC_Key` | `uniqueidentifier` | 否 | 否 | 否 | — | — |

### dbo.Line_Shift_Times（表，8 字段）

| 序号 | 字段 | SQL 类型 | 可空 | 标识列 | 计算列 | 默认表达式 | SQL 注释 |
|---:|---|---|---|---|---|---|---|
| 1 | `Shift_Number` | `smallint` | 否 | 否 | 否 | ((0)) | — |
| 2 | `Shift_Start_Time` | `datetime` | 是 | 否 | 否 | — | — |
| 3 | `Shift_End_Time` | `datetime` | 是 | 否 | 否 | — | — |
| 4 | `Shift_Normal_Pay_Factor` | `real` | 是 | 否 | 否 | ((0)) | — |
| 5 | `Shift_Hours_Before_Overtime` | `real` | 是 | 否 | 否 | ((8)) | — |
| 6 | `Shift_Overtime_Pay_Factor1` | `real` | 是 | 否 | 否 | ((1)) | — |
| 7 | `Shift_Overtime_Pay_Factor2` | `real` | 是 | 否 | 否 | ((1)) | — |
| 8 | `Shift_Overtime_Pay_Factor3` | `real` | 是 | 否 | 否 | ((1)) | — |

### dbo.Line_Stations_To_Use_Colour_And_Size_Menu_With_Console（表，1 字段）

| 序号 | 字段 | SQL 类型 | 可空 | 标识列 | 计算列 | 默认表达式 | SQL 注释 |
|---:|---|---|---|---|---|---|---|
| 1 | `Use_At_Station` | `smallint` | 否 | 否 | 否 | — | — |

### dbo.Line_System_Configuration（表，11 字段）

| 序号 | 字段 | SQL 类型 | 可空 | 标识列 | 计算列 | 默认表达式 | SQL 注释 |
|---:|---|---|---|---|---|---|---|
| 1 | `Date_Of_Configuration` | `datetime` | 否 | 否 | 否 | — | — |
| 2 | `Company_Name` | `nvarchar(255)` | 是 | 否 | 否 | — | — |
| 3 | `Company_Address` | `nvarchar(255)` | 是 | 否 | 否 | — | — |
| 4 | `Company_URL` | `nvarchar(255)` | 是 | 否 | 否 | — | — |
| 5 | `Company_Email` | `nvarchar(255)` | 是 | 否 | 否 | — | — |
| 6 | `Company_Logo` | `image` | 是 | 否 | 否 | — | — |
| 7 | `Payment_Type` | `smallint` | 是 | 否 | 否 | ((0)) | — |
| 8 | `Standard_Piece_Rate_Amount` | `real` | 是 | 否 | 否 | — | — |
| 9 | `Automatically_Calculate_Makeup` | `bit` | 否 | 否 | 否 | ((0)) | — |
| 10 | `Makeup_To` | `nvarchar(10)` | 是 | 否 | 否 | — | — |
| 12 | `Non_Standard_Piece_Rate_Amount` | `real` | 是 | 否 | 否 | — | — |

### dbo.Line_Wireless_Controller（表，4 字段）

| 序号 | 字段 | SQL 类型 | 可空 | 标识列 | 计算列 | 默认表达式 | SQL 注释 |
|---:|---|---|---|---|---|---|---|
| 1 | `LWC_Group_Id` | `smallint` | 否 | 否 | 否 | — | — |
| 2 | `LWC_COM_Port` | `smallint` | 否 | 否 | 否 | — | — |
| 3 | `LWC_Baud_Rate` | `int` | 否 | 否 | 否 | — | — |
| 4 | `LWC_Default_Timeout` | `smallint` | 否 | 否 | 否 | — | — |

### dbo.Line_Workstation_Setup（表，13 字段）

| 序号 | 字段 | SQL 类型 | 可空 | 标识列 | 计算列 | 默认表达式 | SQL 注释 |
|---:|---|---|---|---|---|---|---|
| 1 | `Workstation` | `smallint` | 否 | 否 | 否 | — | — |
| 2 | `Hub` | `smallint` | 是 | 否 | 否 | — | — |
| 3 | `Position` | `nvarchar(1)` | 是 | 否 | 否 | — | — |
| 4 | `In_Feed_Pushers_From_Sensor` | `smallint` | 是 | 否 | 否 | — | — |
| 5 | `In_Feed_Distance_To_Next_Pusher` | `real` | 是 | 否 | 否 | — | — |
| 6 | `Out_Feed_Pushers_From_Sensor` | `smallint` | 是 | 否 | 否 | — | — |
| 7 | `Out_Feed_Distance_To_Next_Pusher` | `real` | 是 | 否 | 否 | — | — |
| 8 | `Use_Wireless_Console` | `bit` | 是 | 否 | 否 | — | — |
| 9 | `Wireless_Console_Group` | `smallint` | 是 | 否 | 否 | — | — |
| 10 | `Cable_Console_Port` | `smallint` | 是 | 否 | 否 | — | — |
| 11 | `Multimedia_Terminal_IP` | `nvarchar(25)` | 是 | 否 | 否 | — | — |
| 12 | `Additional_Wireless_Console1_Group` | `smallint` | 是 | 否 | 否 | — | — |
| 13 | `Additional_Wireless_Console2_Group` | `smallint` | 是 | 否 | 否 | — | — |

### dbo.ODP_Detail（表，23 字段）

| 序号 | 字段 | SQL 类型 | 可空 | 标识列 | 计算列 | 默认表达式 | SQL 注释 |
|---:|---|---|---|---|---|---|---|
| 1 | `ODPD_Key` | `uniqueidentifier` | 否 | 否 | 否 | (newid()) | — |
| 2 | `ODPD_ODP_Key` | `uniqueidentifier` | 是 | 否 | 否 | (newid()) | Link to the master record |
| 3 | `ODPD_Workstation` | `smallint` | 是 | 否 | 否 | ((0)) | — |
| 4 | `ODPD_WC_Key` | `smallint` | 是 | 否 | 否 | ((0)) | Work Code that this ODP record applies to (ie PW, MACHDN etc) |
| 5 | `ODPD_Quantity` | `smallint` | 是 | 否 | 否 | ((0)) | — |
| 6 | `ODPD_ST_Key` | `int` | 是 | 否 | 否 | ((0)) | — |
| 7 | `ODPD_Lot_Number` | `nvarchar(50)` | 是 | 否 | 否 | — | — |
| 8 | `ODPD_OC_Key` | `int` | 否 | 否 | 否 | ((0)) | — |
| 9 | `ODPD_Standard` | `real` | 是 | 否 | 否 | ((0)) | — |
| 10 | `ODPD_Actual_Time` | `int` | 是 | 否 | 否 | ((0)) | Number of seconds spent on the work code for this day |
| 11 | `ODPD_PA_Key` | `nvarchar(10)` | 是 | 否 | 否 | — | — |
| 12 | `ODPD_Pay_Rate` | `real` | 是 | 否 | 否 | ((0)) | — |
| 13 | `ODPD_Piece_Rate` | `real` | 是 | 否 | 否 | ((0)) | — |
| 14 | `ODPD_Start_Time` | `datetime` | 是 | 否 | 否 | — | — |
| 15 | `ODPD_CM_Key` | `int` | 是 | 否 | 否 | ((0)) | — |
| 16 | `ODPD_SM_Key` | `int` | 是 | 否 | 否 | ((0)) | — |
| 17 | `ODPD_Normal_Pay_Factor` | `real` | 是 | 否 | 否 | ((1)) | — |
| 18 | `ODPD_Is_Overtime` | `bit` | 否 | 否 | 否 | ((0)) | — |
| 19 | `ODPD_Overtime_Factor` | `real` | 是 | 否 | 否 | ((0)) | — |
| 20 | `ODPD_Edited_By` | `nvarchar(10)` | 是 | 否 | 否 | — | — |
| 21 | `ODPD_Edited_Date` | `datetime` | 是 | 否 | 否 | — | — |
| 23 | `ODPD_Actual_Time_From_Reader` | `int` | 是 | 否 | 否 | — | — |
| 24 | `ODPD_STPO_Key` | `int` | 是 | 否 | 否 | — | — |

### dbo.ODP_Master（表，14 字段）

| 序号 | 字段 | SQL 类型 | 可空 | 标识列 | 计算列 | 默认表达式 | SQL 注释 |
|---:|---|---|---|---|---|---|---|
| 1 | `ODP_Key` | `uniqueidentifier` | 否 | 否 | 否 | (newid()) | — |
| 2 | `ODP_Date` | `datetime` | 是 | 否 | 否 | — | Date that the ODP record was created |
| 3 | `ODP_Shift` | `smallint` | 是 | 否 | 否 | ((0)) | — |
| 4 | `ODP_EM_Key` | `int` | 是 | 否 | 否 | ((0)) | Employee that the ODP record applies to |
| 5 | `ODP_Actual_Clock_In` | `datetime` | 是 | 否 | 否 | — | — |
| 6 | `ODP_Actual_Clock_Out` | `datetime` | 是 | 否 | 否 | — | — |
| 7 | `ODP_Shift_Clock_In` | `datetime` | 是 | 否 | 否 | — | — |
| 8 | `ODP_Shift_Clock_Out` | `datetime` | 是 | 否 | 否 | — | — |
| 9 | `ODP_First_Hanger_Time` | `datetime` | 是 | 否 | 否 | — | — |
| 10 | `ODP_Last_Hanger_Time` | `datetime` | 是 | 否 | 否 | — | — |
| 11 | `ODP_Current_Station` | `smallint` | 是 | 否 | 否 | ((0)) | — |
| 12 | `ODP_Lump_Sum_Payment` | `float` | 是 | 否 | 否 | ((0)) | — |
| 13 | `ODP_Make_Up_Pay_Rate` | `real` | 是 | 否 | 否 | ((0)) | — |
| 15 | `ODP_Last_Hanger_Start_Time` | `datetime` | 是 | 否 | 否 | — | — |

### dbo.ODP_StartStop_History（表，5 字段）

| 序号 | 字段 | SQL 类型 | 可空 | 标识列 | 计算列 | 默认表达式 | SQL 注释 |
|---:|---|---|---|---|---|---|---|
| 1 | `OSSH_Key` | `bigint` | 否 | 是 | 否 | — | — |
| 2 | `OSSH_ODPD_Key` | `uniqueidentifier` | 否 | 否 | 否 | — | — |
| 3 | `OSSH_Start_Time` | `datetime` | 否 | 否 | 否 | — | — |
| 4 | `OSSH_Stop_Time` | `datetime` | 是 | 否 | 否 | — | — |
| 5 | `OSSH_Quantity` | `int` | 是 | 否 | 否 | — | — |

### dbo.Office_Computers_Connected（表，2 字段）

| 序号 | 字段 | SQL 类型 | 可空 | 标识列 | 计算列 | 默认表达式 | SQL 注释 |
|---:|---|---|---|---|---|---|---|
| 1 | `Primary_Key` | `uniqueidentifier` | 否 | 否 | 否 | (newid()) | — |
| 2 | `Refresh_Rate` | `int` | 是 | 否 | 否 | — | — |

### dbo.Quality_Control_First（表，11 字段）

| 序号 | 字段 | SQL 类型 | 可空 | 标识列 | 计算列 | 默认表达式 | SQL 注释 |
|---:|---|---|---|---|---|---|---|
| 1 | `QCF_Key` | `uniqueidentifier` | 否 | 否 | 否 | (newid()) | — |
| 2 | `QCF_Enabled` | `bit` | 否 | 否 | 否 | ((0)) | — |
| 3 | `QCF_By_Style` | `bit` | 否 | 否 | 否 | ((0)) | — |
| 4 | `QCF_Style_Id` | `int` | 是 | 否 | 否 | — | — |
| 5 | `QCF_Operation_Code` | `int` | 是 | 否 | 否 | — | — |
| 6 | `QCF_Quantity_To_Check` | `int` | 是 | 否 | 否 | — | — |
| 7 | `QCF_Check_Frequency` | `smallint` | 是 | 否 | 否 | — | — |
| 8 | `QCF_Quantity_Checked` | `int` | 是 | 否 | 否 | — | — |
| 9 | `QCF_Date_Created` | `datetime` | 是 | 否 | 否 | — | — |
| 10 | `QCF_Date_Modified` | `datetime` | 是 | 否 | 否 | — | — |
| 11 | `QCF_Check_All` | `bit` | 是 | 否 | 否 | — | — |

### dbo.Quality_Control_Random（表，8 字段）

| 序号 | 字段 | SQL 类型 | 可空 | 标识列 | 计算列 | 默认表达式 | SQL 注释 |
|---:|---|---|---|---|---|---|---|
| 1 | `QCR_Workstation` | `smallint` | 否 | 否 | 否 | — | — |
| 2 | `QCR_Enabled` | `bit` | 否 | 否 | 否 | ((0)) | — |
| 3 | `QCR_Quantity_To_Check` | `int` | 是 | 否 | 否 | — | — |
| 4 | `QCR_Check_Frequency` | `smallint` | 是 | 否 | 否 | — | — |
| 5 | `QCR_Quantity_Checked` | `int` | 是 | 否 | 否 | — | — |
| 6 | `QCR_Date_Created` | `datetime` | 是 | 否 | 否 | — | — |
| 7 | `QCR_Date_Modified` | `datetime` | 是 | 否 | 否 | — | — |
| 8 | `QCR_Check_All` | `bit` | 是 | 否 | 否 | — | — |

### dbo.RTH_Time_Segments（表，3 字段）

| 序号 | 字段 | SQL 类型 | 可空 | 标识列 | 计算列 | 默认表达式 | SQL 注释 |
|---:|---|---|---|---|---|---|---|
| 1 | `RTHS_Segment` | `smallint` | 否 | 否 | 否 | — | — |
| 2 | `RTHS_Segment_Start_Time` | `datetime` | 否 | 否 | 否 | — | — |
| 3 | `RTHS_Segment_End_Time` | `datetime` | 否 | 否 | 否 | — | — |

### dbo.Roll_Titles（表，2 字段）

| 序号 | 字段 | SQL 类型 | 可空 | 标识列 | 计算列 | 默认表达式 | SQL 注释 |
|---:|---|---|---|---|---|---|---|
| 1 | `RT_Key` | `uniqueidentifier` | 否 | 否 | 否 | (newid()) | — |
| 2 | `RT_Text` | `nvarchar(255)` | 否 | 否 | 否 | — | — |

### dbo.Roll_Titles_Archive（表，4 字段）

| 序号 | 字段 | SQL 类型 | 可空 | 标识列 | 计算列 | 默认表达式 | SQL 注释 |
|---:|---|---|---|---|---|---|---|
| 1 | `RTA_Key` | `uniqueidentifier` | 否 | 否 | 否 | (newid()) | — |
| 2 | `RTA_Text` | `nvarchar(255)` | 否 | 否 | 否 | — | — |
| 3 | `RTA_Date_Created` | `datetime` | 是 | 否 | 否 | — | — |
| 4 | `RTA_Date_Last_Used` | `datetime` | 是 | 否 | 否 | — | — |

### dbo.Route Tree（表，6 字段）

| 序号 | 字段 | SQL 类型 | 可空 | 标识列 | 计算列 | 默认表达式 | SQL 注释 |
|---:|---|---|---|---|---|---|---|
| 1 | `ParentID` | `int` | 是 | 否 | 否 | ((0)) | — |
| 2 | `Key` | `int` | 是 | 否 | 否 | ((0)) | — |
| 3 | `Description` | `nvarchar(50)` | 是 | 否 | 否 | — | — |
| 4 | `ImageIndex` | `smallint` | 是 | 否 | 否 | ((0)) | — |
| 5 | `Line` | `smallint` | 是 | 否 | 否 | ((0)) | — |
| 6 | `Station` | `smallint` | 是 | 否 | 否 | ((0)) | — |

### dbo.Run_At_Rate（表，5 字段）

| 序号 | 字段 | SQL 类型 | 可空 | 标识列 | 计算列 | 默认表达式 | SQL 注释 |
|---:|---|---|---|---|---|---|---|
| 1 | `RAR_Workstation` | `smallint` | 否 | 否 | 否 | — | — |
| 2 | `RAR_Participate` | `bit` | 否 | 否 | 否 | — | — |
| 3 | `RAR_Period` | `int` | 否 | 否 | 否 | — | — |
| 4 | `RAR_Target` | `int` | 否 | 否 | 否 | — | — |
| 5 | `RAR_Action` | `smallint` | 否 | 否 | 否 | — | — |

### dbo.Station_To_Station_Setup（表，12 字段）

| 序号 | 字段 | SQL 类型 | 可空 | 标识列 | 计算列 | 默认表达式 | SQL 注释 |
|---:|---|---|---|---|---|---|---|
| 1 | `S2SS_Key` | `uniqueidentifier` | 否 | 否 | 否 | — | — |
| 2 | `S2SS_From_Station` | `smallint` | 否 | 否 | 否 | — | — |
| 3 | `S2SS_To_Station` | `smallint` | 否 | 否 | 否 | — | — |
| 4 | `S2SS_Enabled` | `bit` | 否 | 否 | 否 | — | — |
| 5 | `S2SS_Send_All` | `bit` | 否 | 否 | 否 | — | — |
| 6 | `S2SS_Quantity_To_Send` | `int` | 否 | 否 | 否 | — | — |
| 7 | `S2SS_Quantity_Sent` | `int` | 否 | 否 | 否 | — | — |
| 8 | `S2SS_Apply_QC` | `bit` | 否 | 否 | 否 | — | — |
| 9 | `S2SS_QCHC_Key` | `uniqueidentifier` | 否 | 否 | 否 | — | — |
| 10 | `S2SS_QCSC_Key` | `uniqueidentifier` | 否 | 否 | 否 | — | — |
| 11 | `S2SS_Date_Created` | `datetime` | 否 | 否 | 否 | — | — |
| 12 | `S2SS_Date_Modified` | `datetime` | 否 | 否 | 否 | — | — |

### dbo.Style_Loading（表，13 字段）

| 序号 | 字段 | SQL 类型 | 可空 | 标识列 | 计算列 | 默认表达式 | SQL 注释 |
|---:|---|---|---|---|---|---|---|
| 1 | `SL_Station` | `smallint` | 否 | 否 | 否 | ((0)) | — |
| 2 | `SL_ST_Key` | `int` | 是 | 否 | 否 | ((0)) | — |
| 3 | `SL_STPO_Key` | `int` | 是 | 否 | 否 | — | — |
| 4 | `SL_Lot_Number` | `nvarchar(50)` | 是 | 否 | 否 | — | — |
| 5 | `SL_CM_Key` | `int` | 是 | 否 | 否 | ((0)) | — |
| 6 | `SL_SM_Key` | `int` | 是 | 否 | 否 | ((0)) | — |
| 7 | `SL_Garments_Per_Hanger` | `smallint` | 是 | 否 | 否 | ((0)) | — |
| 8 | `SL_SRM_Key` | `int` | 是 | 否 | 否 | ((0)) | — |
| 9 | `SL_Item_Card` | `nvarchar(20)` | 是 | 否 | 否 | — | — |
| 10 | `SL_Allowed_Out` | `bit` | 是 | 否 | 否 | — | — |
| 11 | `SL_SLSI_Key` | `int` | 是 | 否 | 否 | — | — |
| 15 | `SL_Manual_Bundle_Quantity` | `int` | 是 | 否 | 否 | — | — |
| 16 | `SL_Manual_Bundle_Remaining` | `int` | 是 | 否 | 否 | — | — |

### dbo.Style_Loading_History（表，11 字段）

| 序号 | 字段 | SQL 类型 | 可空 | 标识列 | 计算列 | 默认表达式 | SQL 注释 |
|---:|---|---|---|---|---|---|---|
| 1 | `SL_Station` | `smallint` | 否 | 否 | 否 | ((0)) | — |
| 2 | `SL_Started_Loading_Date` | `datetime` | 否 | 否 | 否 | — | — |
| 3 | `SL_ST_Key` | `int` | 是 | 否 | 否 | ((0)) | — |
| 4 | `SL_STPO_Key` | `int` | 是 | 否 | 否 | — | — |
| 5 | `SL_Lot_Number` | `nvarchar(50)` | 是 | 否 | 否 | — | — |
| 6 | `SL_CM_Key` | `int` | 是 | 否 | 否 | ((0)) | — |
| 7 | `SL_SM_Key` | `int` | 是 | 否 | 否 | ((0)) | — |
| 8 | `SL_Garments_Per_Hanger` | `smallint` | 是 | 否 | 否 | ((0)) | — |
| 9 | `SL_SRM_Key` | `int` | 是 | 否 | 否 | ((0)) | — |
| 10 | `SL_Item_Card` | `nvarchar(20)` | 是 | 否 | 否 | — | — |
| 11 | `SL_SLSI_Key` | `int` | 是 | 否 | 否 | — | — |

### dbo.Wireless_Console_Rework_Additional_Defects（表，10 字段）

| 序号 | 字段 | SQL 类型 | 可空 | 标识列 | 计算列 | 默认表达式 | SQL 注释 |
|---:|---|---|---|---|---|---|---|
| 1 | `WCRAD_HM_ID` | `nvarchar(12)` | 否 | 否 | 否 | — | — |
| 2 | `WCRAD_Defect_NUM` | `int` | 否 | 否 | 否 | — | — |
| 3 | `WCRAD_To_Operation` | `int` | 否 | 否 | 否 | — | — |
| 4 | `WCRAD_To_Station` | `smallint` | 否 | 否 | 否 | — | — |
| 5 | `WCRAD_To_Operation_Order` | `smallint` | 否 | 否 | 否 | — | — |
| 6 | `WCRAD_To_Previous` | `bit` | 否 | 否 | 否 | — | — |
| 7 | `WCRAD_QCSC` | `uniqueidentifier` | 是 | 否 | 否 | — | — |
| 8 | `WCRAD_Quantity_Per_Hanger` | `smallint` | 否 | 否 | 否 | — | — |
| 9 | `WCRAD_From_Station` | `smallint` | 是 | 否 | 否 | — | — |
| 10 | `WCRAD_From_Employee` | `int` | 是 | 否 | 否 | — | — |

### dbo.Workstation_Employee_Information（表，17 字段）

| 序号 | 字段 | SQL 类型 | 可空 | 标识列 | 计算列 | 默认表达式 | SQL 注释 |
|---:|---|---|---|---|---|---|---|
| 1 | `WEI_Workstation` | `smallint` | 否 | 否 | 否 | — | — |
| 2 | `WEI_Position` | `smallint` | 否 | 否 | 否 | — | — |
| 3 | `WEI_Clocked_In` | `bit` | 否 | 否 | 否 | — | — |
| 4 | `WEI_EM_Key` | `int` | 否 | 否 | 否 | — | — |
| 5 | `WEI_ST_Key` | `int` | 否 | 否 | 否 | — | — |
| 6 | `WEI_STPO_Key` | `int` | 否 | 否 | 否 | — | — |
| 7 | `WEI_Lot_Number` | `nvarchar(50)` | 是 | 否 | 否 | — | — |
| 8 | `WEI_SRM_Key` | `int` | 否 | 否 | 否 | — | — |
| 9 | `WEI_Garments_Per_Hanger` | `smallint` | 否 | 否 | 否 | — | — |
| 10 | `WEI_OC_Key` | `int` | 否 | 否 | 否 | — | — |
| 11 | `WEI_CM_Key` | `int` | 否 | 否 | 否 | — | — |
| 12 | `WEI_SM_Key` | `int` | 否 | 否 | 否 | — | — |
| 13 | `WEI_Quantity_Produced` | `int` | 否 | 否 | 否 | — | — |
| 14 | `WEI_Efficiency` | `real` | 否 | 否 | 否 | — | — |
| 15 | `WEI_Total_Quantity_Produced` | `int` | 否 | 否 | 否 | — | — |
| 16 | `WEI_Total_Efficiency` | `real` | 否 | 否 | 否 | — | — |
| 18 | `WEI_LLC` | `datetime` | 是 | 否 | 否 | — | — |

### dbo.Workstation_Saved_Information（表，19 字段）

| 序号 | 字段 | SQL 类型 | 可空 | 标识列 | 计算列 | 默认表达式 | SQL 注释 |
|---:|---|---|---|---|---|---|---|
| 1 | `WI_Workstation` | `smallint` | 否 | 否 | 否 | ((0)) | Idicates what workstation this record represents |
| 2 | `WI_RTD_Status_Key` | `smallint` | 是 | 否 | 否 | ((0)) | — |
| 3 | `WI_IsFull` | `bit` | 否 | 否 | 否 | ((0)) | — |
| 4 | `WI_WC_Key` | `smallint` | 是 | 否 | 否 | ((0)) | Work Code Key |
| 5 | `WI_Clocked_In` | `bit` | 否 | 否 | 否 | ((0)) | — |
| 6 | `WI_EM_Key` | `int` | 是 | 否 | 否 | ((0)) | Employee Key |
| 7 | `WI_ST_Key` | `int` | 是 | 否 | 否 | ((0)) | Style Key |
| 8 | `WI_STPO_Key` | `int` | 是 | 否 | 否 | — | — |
| 10 | `WI_SRM_Key` | `int` | 是 | 否 | 否 | ((0)) | Route Master Key |
| 12 | `WI_OC_Key` | `int` | 是 | 否 | 否 | ((0)) | Operation Code Key |
| 13 | `WI_CM_Key` | `int` | 是 | 否 | 否 | — | — |
| 14 | `WI_SM_Key` | `int` | 是 | 否 | 否 | — | — |
| 15 | `setQuantity_Produced` | `int` | 是 | 否 | 否 | ((0)) | — |
| 16 | `setEfficiency` | `real` | 是 | 否 | 否 | ((0)) | — |
| 17 | `setTotal_Quantity_Produced` | `int` | 是 | 否 | 否 | ((0)) | — |
| 18 | `setTotal_Efficiency` | `real` | 是 | 否 | 否 | ((0)) | — |
| 20 | `WI_Full_Quantity` | `smallint` | 是 | 否 | 否 | — | — |
| 23 | `WI_IsSoftwareFull` | `bit` | 是 | 否 | 否 | — | — |
| 24 | `WI_Request_For_Help` | `nvarchar(5)` | 是 | 否 | 否 | — | — |

## 4.2 `IHS_HISTORY`

兼容级别：{d["info"]["compatibility_level"]}；排序规则：`{d["info"]["collation_name"]}`。

### dbo.Hanger_Master_History（表，18 字段）

| 序号 | 字段 | SQL 类型 | 可空 | 标识列 | 计算列 | 默认表达式 | SQL 注释 |
|---:|---|---|---|---|---|---|---|
| 1 | `HMH_Key` | `uniqueidentifier` | 否 | 否 | 否 | (newid()) | — |
| 2 | `HMH_HM_ID` | `nvarchar(12)` | 是 | 否 | 否 | — | — |
| 3 | `HMH_Loaded_On_Line` | `smallint` | 是 | 否 | 否 | ((0)) | — |
| 4 | `HMH_SRM_Key` | `int` | 是 | 否 | 否 | ((0)) | Style Route Master Key that this is following |
| 5 | `HMH_ST_Key` | `int` | 是 | 否 | 否 | ((0)) | Style Key for the style taht was loaded on this hanger |
| 6 | `HMH_STPO_Key` | `int` | 是 | 否 | 否 | — | Style PO Key |
| 7 | `HMH_Lot_Number` | `nvarchar(50)` | 是 | 否 | 否 | — | Style Lot number |
| 8 | `HMH_CM_Key` | `int` | 是 | 否 | 否 | — | Colour Master Key |
| 9 | `HMH_SM_Key` | `int` | 是 | 否 | 否 | — | Size Master Key |
| 10 | `HMH_Garments_Per_Hanger` | `smallint` | 是 | 否 | 否 | — | — |
| 11 | `HMH_Started_Loading_Time` | `datetime` | 是 | 否 | 否 | — | — |
| 12 | `HMH_Completed_Time` | `datetime` | 是 | 否 | 否 | — | — |
| 13 | `HMH_Style_Standard_Minutes` | `real` | 是 | 否 | 否 | ((0)) | — |
| 14 | `HMH_Style_Production_Minutes` | `real` | 是 | 否 | 否 | ((0)) | — |
| 15 | `HMH_Moved_To_History_Time` | `datetime` | 是 | 否 | 否 | — | — |
| 16 | `HMH_Item_Card` | `nvarchar(20)` | 是 | 否 | 否 | — | — |
| 17 | `HMH_Is_Scrapped` | `bit` | 是 | 否 | 否 | — | — |
| 27 | `HMH_Label` | `nvarchar(12)` | 是 | 否 | 否 | — | — |

### dbo.Hanger_Routing_Details_History（表，13 字段）

| 序号 | 字段 | SQL 类型 | 可空 | 标识列 | 计算列 | 默认表达式 | SQL 注释 |
|---:|---|---|---|---|---|---|---|
| 1 | `HRDH_Key` | `uniqueidentifier` | 否 | 否 | 否 | (newid()) | — |
| 2 | `HRDH_HMH_Key` | `uniqueidentifier` | 是 | 否 | 否 | (newid()) | — |
| 3 | `HRDH_OC_Key` | `int` | 是 | 否 | 否 | ((0)) | The Operation Code that was completed |
| 4 | `HRDH_EM_Key` | `int` | 是 | 否 | 否 | ((0)) | The Employee that completed the operation |
| 5 | `HRDH_Line` | `smallint` | 是 | 否 | 否 | ((0)) | The Line it was completed on |
| 6 | `HRDH_Workstation` | `smallint` | 是 | 否 | 否 | ((0)) | The station it was completed at |
| 7 | `HRDH_Completed_Time` | `datetime` | 是 | 否 | 否 | — | — |
| 8 | `HRDH_ODP_Credited` | `bit` | 否 | 否 | 否 | ((0)) | — |
| 9 | `HRDH_Comments` | `nvarchar(255)` | 是 | 否 | 否 | — | — |
| 10 | `HRDH_Started_Time` | `datetime` | 是 | 否 | 否 | — | — |
| 11 | `HRDH_Create_Time` | `datetime` | 是 | 否 | 否 | — | — |
| 12 | `HRDH_OC_Completed` | `bit` | 是 | 否 | 否 | — | — |
| 45 | `HRDH_Enter_Time` | `datetime` | 是 | 否 | 否 | — | — |

## 4.3 `IHS_OPEN`

兼容级别：{d["info"]["compatibility_level"]}；排序规则：`{d["info"]["collation_name"]}`。

### dbo.ICR_Exception_Date（表，2 字段）

| 序号 | 字段 | SQL 类型 | 可空 | 标识列 | 计算列 | 默认表达式 | SQL 注释 |
|---:|---|---|---|---|---|---|---|
| 1 | `ICRED_Key` | `int` | 否 | 是 | 否 | — | — |
| 2 | `ICRED_Exception_Date` | `date` | 否 | 否 | 否 | — | — |

### dbo.ODP_Info（表，25 字段）

| 序号 | 字段 | SQL 类型 | 可空 | 标识列 | 计算列 | 默认表达式 | SQL 注释 |
|---:|---|---|---|---|---|---|---|
| 1 | `ODPI_Key` | `uniqueidentifier` | 否 | 否 | 否 | (newid()) | Just primary key ... not used for anything else |
| 2 | `ODPI_Date` | `datetime` | 是 | 否 | 否 | — | Date that the ODP record was created |
| 3 | `ODPI_Shift` | `smallint` | 是 | 否 | 否 | ((0)) | Shift that this ODP record applies to |
| 4 | `ODPI_EM_Key` | `int` | 是 | 否 | 否 | ((0)) | Employee that the ODP record applies to |
| 5 | `ODPI_EM_FirstName` | `nvarchar(50)` | 是 | 否 | 否 | — | — |
| 6 | `ODPI_EM_LastName` | `nvarchar(50)` | 是 | 否 | 否 | — | — |
| 7 | `ODPI_Actual_Clock_In` | `datetime` | 是 | 否 | 否 | — | Time that Employee clocked in |
| 8 | `ODPI_Actual_Clock_Out` | `datetime` | 是 | 否 | 否 | — | Time that employee clocked out |
| 9 | `ODPI_Workstation` | `smallint` | 是 | 否 | 否 | ((0)) | Workstation that employee was at for this Style/Operation |
| 10 | `ODPI_Quantity` | `smallint` | 是 | 否 | 否 | ((0)) | — |
| 11 | `ODPI_ST_ID` | `nvarchar(11)` | 是 | 否 | 否 | — | — |
| 12 | `ODPI_ST_Description` | `nvarchar(50)` | 是 | 否 | 否 | — | — |
| 13 | `ODPI_Lot_Number` | `nvarchar(8)` | 否 | 否 | 否 | — | — |
| 14 | `ODPI_OC_Key` | `int` | 否 | 否 | 否 | ((0)) | — |
| 15 | `ODPI_OC_Description` | `nvarchar(35)` | 是 | 否 | 否 | — | — |
| 16 | `ODPI_CM_Short_Description` | `nvarchar(10)` | 是 | 否 | 否 | — | — |
| 17 | `ODPI_SM_Short_Description` | `nvarchar(5)` | 是 | 否 | 否 | — | — |
| 18 | `ODPI_Piece_Rate` | `real` | 是 | 否 | 否 | ((0)) | — |
| 19 | `OPDI_OC_Standard_Time` | `real` | 是 | 否 | 否 | ((0)) | Operation Code standard time |
| 20 | `ODPI_Actual_Time` | `int` | 是 | 否 | 否 | ((0)) | Number of seconds spent on the work code for this day |
| 21 | `ODPI_WC_Key` | `smallint` | 是 | 否 | 否 | ((0)) | Work Code that this ODP record applies to (ie PW, MACHDN etc) |
| 22 | `ODPI_Normal_Pay_Factor` | `real` | 是 | 否 | 否 | ((1)) | — |
| 23 | `ODPI_Is_Overtime` | `bit` | 否 | 否 | 否 | ((0)) | — |
| 24 | `ODPI_Overtime_Factor` | `real` | 是 | 否 | 否 | ((0)) | — |
| 28 | `ODPI_Last_Operation` | `bit` | 是 | 否 | 否 | — | — |

### dbo.Production_History（表，12 字段）

| 序号 | 字段 | SQL 类型 | 可空 | 标识列 | 计算列 | 默认表达式 | SQL 注释 |
|---:|---|---|---|---|---|---|---|
| 1 | `WTH_key` | `int` | 否 | 是 | 否 | — | — |
| 2 | `WTH_Date` | `datetime` | 是 | 否 | 否 | — | — |
| 3 | `WTH_ST_Key` | `int` | 是 | 否 | 否 | — | — |
| 4 | `WTH_TargetQuantity` | `int` | 是 | 否 | 否 | — | — |
| 5 | `WTH_OrderNum` | `int` | 是 | 否 | 否 | — | — |
| 6 | `WTH_CompleteTime` | `datetime` | 是 | 否 | 否 | — | — |
| 7 | `WTH_ActualQuantity` | `int` | 是 | 否 | 否 | — | — |
| 8 | `WTH_ReworwQuantity` | `int` | 是 | 否 | 否 | — | — |
| 9 | `WTH_AllQuantity` | `int` | 是 | 否 | 否 | — | — |
| 10 | `WTH_NeedTocompleteQty` | `int` | 是 | 否 | 否 | — | — |
| 11 | `WTH_LastUpdtaTime` | `datetime` | 是 | 否 | 否 | — | — |
| 12 | `WTH_Complete_Operation` | `nvarchar(50)` | 是 | 否 | 否 | ('') | — |

### dbo.V_ODP_Info（视图，47 字段）

| 序号 | 字段 | SQL 类型 | 可空 | 标识列 | 计算列 | 默认表达式 | SQL 注释 |
|---:|---|---|---|---|---|---|---|
| 1 | `ODPI_Key` | `uniqueidentifier` | 否 | 否 | 否 | — | — |
| 2 | `ODPI_Date` | `datetime` | 是 | 否 | 否 | — | — |
| 3 | `ODPI_EM_Key` | `int` | 是 | 否 | 否 | — | — |
| 4 | `ODPI_EM_FirstName` | `nvarchar(50)` | 是 | 否 | 否 | — | — |
| 5 | `ODPI_EM_LastName` | `nvarchar(50)` | 是 | 否 | 否 | — | — |
| 6 | `ODPI_Workstation` | `smallint` | 是 | 否 | 否 | — | — |
| 7 | `ODPI_Quantity` | `smallint` | 是 | 否 | 否 | — | — |
| 8 | `ODPI_ST_ID` | `nvarchar(25)` | 是 | 否 | 否 | — | — |
| 9 | `ODPI_ST_Description` | `nvarchar(50)` | 是 | 否 | 否 | — | — |
| 10 | `ODPI_Lot_Number` | `nvarchar(50)` | 是 | 否 | 否 | — | — |
| 11 | `ODPI_OC_Key` | `int` | 否 | 否 | 否 | — | — |
| 12 | `ODPI_OC_Description` | `nvarchar(255)` | 是 | 否 | 否 | — | — |
| 13 | `ODPI_CM_Short_Description` | `nvarchar(10)` | 是 | 否 | 否 | — | — |
| 14 | `ODPI_SM_Short_Description` | `nvarchar(10)` | 是 | 否 | 否 | — | — |
| 15 | `ODPI_OC_Standard_Time` | `real` | 是 | 否 | 否 | — | — |
| 16 | `ODPI_Piece_Rate` | `real` | 是 | 否 | 否 | — | — |
| 17 | `ODPI_Actual_Time` | `int` | 是 | 否 | 否 | — | — |
| 18 | `ODPI_STOP_Order` | `smallint` | 是 | 否 | 否 | — | — |
| 19 | `ODPI_PC_Description` | `nvarchar(25)` | 是 | 否 | 否 | — | — |
| 20 | `ODPI_ST_Collection` | `nvarchar(25)` | 是 | 否 | 否 | — | — |
| 21 | `ODPI_ST_Key` | `int` | 是 | 否 | 否 | — | — |
| 22 | `ODPI_CM_Description` | `nvarchar(255)` | 是 | 否 | 否 | — | — |
| 23 | `ODPI_SM_Description` | `nvarchar(255)` | 是 | 否 | 否 | — | — |
| 24 | `ODPI_ODPD_PA_Key` | `nvarchar(10)` | 是 | 否 | 否 | — | — |
| 25 | `ODPI_PA_CurrencyValue` | `real` | 是 | 否 | 否 | — | — |
| 26 | `ODPI_PA_CategoryType` | `nvarchar(20)` | 是 | 否 | 否 | — | — |
| 27 | `ODPI_PA_Description` | `nvarchar(25)` | 是 | 否 | 否 | — | — |
| 28 | `ODPI_ODPD_Is_Overtime` | `bit` | 否 | 否 | 否 | — | — |
| 29 | `ODPI_MC_Type` | `nvarchar(25)` | 是 | 否 | 否 | — | — |
| 30 | `ODPI_STOP_Number` | `nvarchar(50)` | 是 | 否 | 否 | — | — |
| 31 | `ODPI_OC_Piece_Rate_Additional` | `real` | 是 | 否 | 否 | — | — |
| 32 | `ODPI_ODP_Actual_Clock_In` | `datetime` | 是 | 否 | 否 | — | — |
| 33 | `ODPI_ODP_Actual_Clock_Out` | `datetime` | 是 | 否 | 否 | — | — |
| 34 | `ODPI_EM_City` | `nvarchar(25)` | 是 | 否 | 否 | — | — |
| 35 | `ODPI_ST_Fabric` | `nvarchar(25)` | 是 | 否 | 否 | — | — |
| 36 | `ODPI_EM_RFID` | `nvarchar(10)` | 是 | 否 | 否 | — | — |
| 37 | `ODPI_EM_Department` | `nvarchar(15)` | 是 | 否 | 否 | — | — |
| 38 | `ODPI_EM_SSN` | `nvarchar(15)` | 是 | 否 | 否 | — | — |
| 39 | `ODPI_Start_Time` | `datetime` | 是 | 否 | 否 | — | — |
| 40 | `ODPI_EM_LCD_Name` | `nvarchar(15)` | 是 | 否 | 否 | — | — |
| 41 | `ODPI_WC_Key` | `smallint` | 是 | 否 | 否 | — | — |
| 42 | `ODPI_WC_Description` | `nvarchar(25)` | 是 | 否 | 否 | — | — |
| 43 | `ODPI_ODPD_STPO_Key` | `int` | 是 | 否 | 否 | — | — |
| 44 | `ODPI_ODP_Shift` | `smallint` | 是 | 否 | 否 | — | — |
| 45 | `ODPI_STPO_Number` | `nvarchar(50)` | 是 | 否 | 否 | — | — |
| 46 | `ODPI_ST_Trim` | `nvarchar(25)` | 是 | 否 | 否 | — | — |
| 47 | `ODPI_Line_Number` | `smallint` | 是 | 否 | 否 | — | — |

### dbo.V_QC_Rework_Info（视图，47 字段）

| 序号 | 字段 | SQL 类型 | 可空 | 标识列 | 计算列 | 默认表达式 | SQL 注释 |
|---:|---|---|---|---|---|---|---|
| 1 | `QCRI_Key` | `uniqueidentifier` | 否 | 否 | 否 | — | — |
| 2 | `QCRI_Defect_EM_Key` | `int` | 否 | 否 | 否 | — | — |
| 3 | `QCRI_Defect_EM_FirstName` | `nvarchar(50)` | 是 | 否 | 否 | — | — |
| 4 | `QCRI_Defect_EM_LastName` | `nvarchar(50)` | 是 | 否 | 否 | — | — |
| 5 | `QCRI_Defect_DateTime` | `datetime` | 否 | 否 | 否 | — | — |
| 6 | `QCRI_Defect_Quantity` | `smallint` | 否 | 否 | 否 | — | — |
| 7 | `QCRI_ST_Key` | `int` | 否 | 否 | 否 | — | — |
| 8 | `QCRI_ST_ID` | `nvarchar(25)` | 是 | 否 | 否 | — | — |
| 9 | `QCRI_ST_Description` | `nvarchar(50)` | 是 | 否 | 否 | — | — |
| 10 | `QCRI_ST_Collection` | `nvarchar(25)` | 是 | 否 | 否 | — | — |
| 11 | `QCRI_OC_Key` | `int` | 否 | 否 | 否 | — | — |
| 12 | `QCRI_OC_Description` | `nvarchar(255)` | 是 | 否 | 否 | — | — |
| 13 | `QCRI_PC_Description` | `nvarchar(25)` | 是 | 否 | 否 | — | — |
| 14 | `QCRI_STOP_Number` | `nvarchar(50)` | 是 | 否 | 否 | — | — |
| 15 | `QCRI_CM_Key` | `int` | 是 | 否 | 否 | — | — |
| 16 | `QCRI_CM_Short_Description` | `nvarchar(10)` | 是 | 否 | 否 | — | — |
| 17 | `QCRI_CM_Description` | `nvarchar(255)` | 是 | 否 | 否 | — | — |
| 18 | `QCRI_SM_Key` | `int` | 是 | 否 | 否 | — | — |
| 19 | `QCRI_SM_Short_Description` | `nvarchar(10)` | 是 | 否 | 否 | — | — |
| 20 | `QCRI_SM_Description` | `nvarchar(255)` | 是 | 否 | 否 | — | — |
| 21 | `QCRI_QCSC_Key` | `uniqueidentifier` | 是 | 否 | 否 | — | — |
| 22 | `QCRI_QCSC_Description` | `nvarchar(255)` | 是 | 否 | 否 | — | — |
| 23 | `QCRI_QCSC_Is_Rework` | `bit` | 是 | 否 | 否 | — | — |
| 24 | `QCRI_QC_EM_Key` | `int` | 否 | 否 | 否 | — | — |
| 25 | `QCRI_QC_EM_FirstName` | `nvarchar(50)` | 是 | 否 | 否 | — | — |
| 26 | `QCRI_QC_EM_LastName` | `nvarchar(50)` | 是 | 否 | 否 | — | — |
| 27 | `QCRI_QC_Station` | `smallint` | 是 | 否 | 否 | — | — |
| 28 | `QCRI_QC_DateTime` | `datetime` | 是 | 否 | 否 | — | — |
| 29 | `QCRI_Repair_EM_Key` | `int` | 是 | 否 | 否 | — | — |
| 30 | `QCRI_Repair_EM_FirstName` | `nvarchar(50)` | 是 | 否 | 否 | — | — |
| 31 | `QCRI_Repair_EM_LastName` | `nvarchar(50)` | 是 | 否 | 否 | — | — |
| 32 | `QCRI_Repair_DateTime` | `datetime` | 是 | 否 | 否 | — | — |
| 33 | `QCRI_Repair_Quantity` | `int` | 是 | 否 | 否 | — | — |
| 34 | `QCRI_QC_EM_Department` | `nvarchar(15)` | 是 | 否 | 否 | — | — |
| 35 | `QCRI_QC_EM_SSN` | `nvarchar(15)` | 是 | 否 | 否 | — | — |
| 36 | `QCRI_QC_EM_City` | `nvarchar(25)` | 是 | 否 | 否 | — | — |
| 37 | `QCRI_Defect_EM_Department` | `nvarchar(15)` | 是 | 否 | 否 | — | — |
| 38 | `QCRI_Defect_EM_SSN` | `nvarchar(15)` | 是 | 否 | 否 | — | — |
| 39 | `QCRI_Defect_EM_City` | `nvarchar(25)` | 是 | 否 | 否 | — | — |
| 40 | `QCRI_Repair_EM_Department` | `nvarchar(15)` | 是 | 否 | 否 | — | — |
| 41 | `QCRI_Repair_EM_SSN` | `nvarchar(15)` | 是 | 否 | 否 | — | — |
| 42 | `QCRI_Repair_EM_City` | `nvarchar(25)` | 是 | 否 | 否 | — | — |
| 43 | `QCRI_Defect_EM_LCD_Name` | `nvarchar(15)` | 是 | 否 | 否 | — | — |
| 44 | `QCRI_QC_EM_LCD_Name` | `nvarchar(15)` | 是 | 否 | 否 | — | — |
| 45 | `QCRI_Repair_EM_LCD_Name` | `nvarchar(15)` | 是 | 否 | 否 | — | — |
| 46 | `QCRI_LineNumber` | `smallint` | 是 | 否 | 否 | — | — |
| 47 | `QCRI_STPO_Key` | `int` | 是 | 否 | 否 | — | — |

## 4.4 `IHS_SHARED`

兼容级别：{d["info"]["compatibility_level"]}；排序规则：`{d["info"]["collation_name"]}`。

### dbo.Bridging_Hanger_Master（表，21 字段）

| 序号 | 字段 | SQL 类型 | 可空 | 标识列 | 计算列 | 默认表达式 | SQL 注释 |
|---:|---|---|---|---|---|---|---|
| 1 | `HM_ID` | `nvarchar(12)` | 否 | 否 | 否 | — | — |
| 2 | `HM_Loaded_On_Line` | `smallint` | 是 | 否 | 否 | ((0)) | — |
| 3 | `HM_Current_Line` | `smallint` | 是 | 否 | 否 | ((0)) | — |
| 4 | `HM_Current_Location_Type` | `nvarchar(1)` | 是 | 否 | 否 | — | P = Pusher, W = Workstation, S = workstation Staging area |
| 5 | `HM_Current_Location` | `smallint` | 是 | 否 | 否 | ((0)) | — |
| 6 | `HM_SRM_Key` | `int` | 是 | 否 | 否 | ((0)) | Style Route Master Key that this is following |
| 7 | `HM_ST_Key` | `int` | 是 | 否 | 否 | ((0)) | Style Key for the style taht was loaded on this hanger |
| 8 | `HM_STPO_Key` | `int` | 是 | 否 | 否 | — | Style PO Key |
| 9 | `HM_Lot_Number` | `nvarchar(50)` | 是 | 否 | 否 | — | Style Lot number |
| 10 | `HM_CM_Key` | `int` | 是 | 否 | 否 | — | Colour Master Key |
| 11 | `HM_SM_Key` | `int` | 是 | 否 | 否 | — | Size Master Key |
| 12 | `HM_Garments_Per_Hanger` | `smallint` | 是 | 否 | 否 | — | — |
| 13 | `HM_Production_Time` | `real` | 是 | 否 | 否 | — | — |
| 17 | `HM_PI_Key` | `int` | 是 | 否 | 否 | — | — |
| 18 | `HM_Item_Card` | `nvarchar(20)` | 是 | 否 | 否 | — | — |
| 19 | `HM_Routing_Info` | `nvarchar(50)` | 是 | 否 | 否 | — | — |
| 20 | `HM_Is_Scrapped` | `bit` | 是 | 否 | 否 | — | — |
| 21 | `HM_ACC_Key` | `uniqueidentifier` | 是 | 否 | 否 | — | — |
| 22 | `HM_Key` | `uniqueidentifier` | 是 | 否 | 否 | — | — |
| 25 | `HM_SP_Key` | `int` | 是 | 否 | 否 | — | — |
| 30 | `HM_Label` | `nvarchar(12)` | 是 | 否 | 否 | — | — |

### dbo.Bridging_Hanger_Routing_Details（表，17 字段）

| 序号 | 字段 | SQL 类型 | 可空 | 标识列 | 计算列 | 默认表达式 | SQL 注释 |
|---:|---|---|---|---|---|---|---|
| 1 | `HRD_Key` | `uniqueidentifier` | 否 | 否 | 否 | (newid()) | — |
| 2 | `HRD_HM_ID` | `nvarchar(12)` | 是 | 否 | 否 | — | The Hanger Id |
| 3 | `HRD_OC_Key` | `int` | 是 | 否 | 否 | ((0)) | The Operation Code that was completed |
| 4 | `HRD_EM_Key` | `int` | 是 | 否 | 否 | ((0)) | The Employee that completed the operation |
| 5 | `HRD_Line` | `smallint` | 是 | 否 | 否 | ((0)) | The Line it was completed on |
| 6 | `HRD_Workstation` | `smallint` | 是 | 否 | 否 | ((0)) | The station it was completed at |
| 7 | `HRD_Completed_Time` | `datetime` | 是 | 否 | 否 | — | — |
| 8 | `HRD_ODP_Credited` | `bit` | 否 | 否 | 否 | ((0)) | — |
| 9 | `HRD_Comments` | `nvarchar(255)` | 是 | 否 | 否 | — | — |
| 10 | `HRD_PID_Key` | `int` | 是 | 否 | 否 | — | — |
| 11 | `HRD_Started_Time` | `datetime` | 是 | 否 | 否 | — | — |
| 12 | `HRD_WC_Key` | `smallint` | 是 | 否 | 否 | — | — |
| 13 | `HRD_Return_For_Repair` | `bit` | 是 | 否 | 否 | — | — |
| 14 | `HRD_Create_Time` | `datetime` | 是 | 否 | 否 | — | — |
| 15 | `HRD_OC_Completed` | `bit` | 是 | 否 | 否 | — | — |
| 16 | `HRD_Enter_Time` | `datetime` | 是 | 否 | 否 | — | — |
| 17 | `HRD_Defect_Order` | `smallint` | 是 | 否 | 否 | — | — |

### dbo.Classified_Scheme（表，14 字段）

| 序号 | 字段 | SQL 类型 | 可空 | 标识列 | 计算列 | 默认表达式 | SQL 注释 |
|---:|---|---|---|---|---|---|---|
| 1 | `CS_Key` | `uniqueidentifier` | 否 | 否 | 否 | — | — |
| 2 | `CS_CM_Key` | `int` | 否 | 否 | 否 | — | — |
| 3 | `CS_SM_Key` | `int` | 否 | 否 | 否 | — | — |
| 4 | `CS_Lot_Number` | `nvarchar(50)` | 否 | 否 | 否 | — | — |
| 5 | `CS_ST_Key` | `int` | 否 | 否 | 否 | — | — |
| 6 | `CS_SRM_Key` | `int` | 否 | 否 | 否 | — | — |
| 7 | `CS_OC_Key` | `int` | 否 | 否 | 否 | — | — |
| 8 | `CS_Station_Number` | `smallint` | 否 | 否 | 否 | — | — |
| 9 | `CS_Line_Number` | `smallint` | 否 | 否 | 否 | — | — |
| 10 | `CS_Quantity_Loaded` | `int` | 否 | 否 | 否 | — | — |
| 11 | `CS_Quantity_Ordered` | `int` | 否 | 否 | 否 | — | — |
| 12 | `CS_Completed` | `bit` | 否 | 否 | 否 | — | — |
| 13 | `CS_Create_Time` | `datetime` | 否 | 否 | 否 | — | — |
| 14 | `CS_Classified_Order` | `smallint` | 是 | 否 | 否 | — | — |

### dbo.Colour_Master（表，4 字段）

| 序号 | 字段 | SQL 类型 | 可空 | 标识列 | 计算列 | 默认表达式 | SQL 注释 |
|---:|---|---|---|---|---|---|---|
| 1 | `CM_Key` | `int` | 否 | 是 | 否 | — | — |
| 2 | `CM_Short_Description` | `nvarchar(10)` | 是 | 否 | 否 | — | — |
| 3 | `CM_Description` | `nvarchar(255)` | 是 | 否 | 否 | — | — |
| 4 | `CM_GKey` | `uniqueidentifier` | 是 | 否 | 否 | — | — |

### dbo.Database_Shared_Tables_Last_Modified（表，18 字段）

| 序号 | 字段 | SQL 类型 | 可空 | 标识列 | 计算列 | 默认表达式 | SQL 注释 |
|---:|---|---|---|---|---|---|---|
| 1 | `Row_Index` | `int` | 否 | 否 | 否 | — | — |
| 2 | `Employee_Master` | `datetime` | 是 | 否 | 否 | — | — |
| 3 | `Style_Route_Master` | `datetime` | 是 | 否 | 否 | — | — |
| 4 | `Style_Route_Detail` | `datetime` | 是 | 否 | 否 | — | — |
| 5 | `Machine_Codes` | `datetime` | 是 | 否 | 否 | — | — |
| 6 | `Primary_Codes` | `datetime` | 是 | 否 | 否 | — | — |
| 7 | `Operation_Codes` | `datetime` | 是 | 否 | 否 | — | — |
| 8 | `Style_Master` | `datetime` | 是 | 否 | 否 | — | — |
| 9 | `Style_Operations` | `datetime` | 是 | 否 | 否 | — | — |
| 10 | `Style_Planned_Orders` | `datetime` | 是 | 否 | 否 | — | — |
| 11 | `Colour_Master` | `datetime` | 是 | 否 | 否 | — | — |
| 12 | `Size_Master` | `datetime` | 是 | 否 | 否 | — | — |
| 13 | `Pay_Categories` | `datetime` | 是 | 否 | 否 | — | — |
| 14 | `Work_Codes` | `datetime` | 是 | 否 | 否 | — | — |
| 15 | `Employee_Target_Quantity` | `datetime` | 是 | 否 | 否 | — | — |
| 16 | `Style_Colours_Or_Sizes` | `datetime` | 是 | 否 | 否 | — | — |
| 17 | `Hanger_Registry` | `datetime` | 是 | 否 | 否 | — | — |
| 19 | `Sequential_Production` | `datetime` | 是 | 否 | 否 | — | — |

### dbo.Database_Version（表，1 字段）

| 序号 | 字段 | SQL 类型 | 可空 | 标识列 | 计算列 | 默认表达式 | SQL 注释 |
|---:|---|---|---|---|---|---|---|
| 1 | `Database_Version` | `int` | 是 | 否 | 否 | — | — |

### dbo.Employee_Master（表，32 字段）

| 序号 | 字段 | SQL 类型 | 可空 | 标识列 | 计算列 | 默认表达式 | SQL 注释 |
|---:|---|---|---|---|---|---|---|
| 1 | `EM_Key` | `int` | 否 | 否 | 否 | ((0)) | — |
| 2 | `EM_FirstName` | `nvarchar(50)` | 是 | 否 | 否 | — | — |
| 3 | `EM_LastName` | `nvarchar(50)` | 是 | 否 | 否 | — | — |
| 4 | `EM_LCD_Name` | `nvarchar(15)` | 是 | 否 | 否 | — | — |
| 5 | `EM_Sex` | `nvarchar(1)` | 是 | 否 | 否 | — | — |
| 6 | `EM_Department` | `nvarchar(15)` | 是 | 否 | 否 | — | — |
| 7 | `EM_SSN` | `nvarchar(15)` | 是 | 否 | 否 | — | — |
| 8 | `EM_GuaranteedPay` | `real` | 是 | 否 | 否 | ((0)) | — |
| 9 | `EM_AveragePay` | `real` | 是 | 否 | 否 | ((0)) | — |
| 10 | `EM_UserOnePay` | `real` | 是 | 否 | 否 | ((0)) | — |
| 11 | `EM_UserTwoPay` | `real` | 是 | 否 | 否 | ((0)) | — |
| 12 | `EM_Incentive` | `real` | 是 | 否 | 否 | ((1)) | — |
| 13 | `EM_Picture` | `image` | 是 | 否 | 否 | — | — |
| 14 | `EM_Address` | `nvarchar(50)` | 是 | 否 | 否 | — | — |
| 15 | `EM_City` | `nvarchar(25)` | 是 | 否 | 否 | — | — |
| 16 | `EM_Province` | `nvarchar(25)` | 是 | 否 | 否 | — | — |
| 17 | `EM_PostalCode` | `nvarchar(10)` | 是 | 否 | 否 | — | — |
| 18 | `EM_HomePhoneNumber` | `nvarchar(20)` | 是 | 否 | 否 | — | — |
| 19 | `EM_CellPhoneNumber` | `nvarchar(20)` | 是 | 否 | 否 | — | — |
| 20 | `EM_EmergencyContactName` | `nvarchar(50)` | 是 | 否 | 否 | — | — |
| 21 | `EM_EmergencyContactRelation` | `nvarchar(15)` | 是 | 否 | 否 | — | — |
| 22 | `EM_EmergencyContactPhone` | `nvarchar(20)` | 是 | 否 | 否 | — | — |
| 23 | `EM_BloodType` | `nvarchar(5)` | 是 | 否 | 否 | — | — |
| 24 | `EM_BirthDate` | `datetime` | 是 | 否 | 否 | (CONVERT([datetime],CONVERT([varchar],getdate(),(1)),(1))) | — |
| 25 | `EM_DateHired` | `datetime` | 是 | 否 | 否 | (CONVERT([datetime],CONVERT([varchar],getdate(),(1)),(1))) | — |
| 26 | `EM_TerminationDate` | `datetime` | 是 | 否 | 否 | — | — |
| 27 | `EM_RFID` | `nvarchar(10)` | 是 | 否 | 否 | — | — |
| 28 | `EM_Supervisor` | `bit` | 是 | 否 | 否 | — | — |
| 29 | `EM_Default_Target_Quantity` | `smallint` | 是 | 否 | 否 | — | — |
| 30 | `EM_GKey` | `uniqueidentifier` | 是 | 否 | 否 | — | — |
| 31 | `EM_ID` | `nvarchar(255)` | 是 | 否 | 否 | — | — |
| 32 | `EM_Resigned` | `bit` | 是 | 否 | 否 | — | — |

### dbo.Employee_Target_Quantity（表，3 字段）

| 序号 | 字段 | SQL 类型 | 可空 | 标识列 | 计算列 | 默认表达式 | SQL 注释 |
|---:|---|---|---|---|---|---|---|
| 1 | `ETQ_EM_Key` | `int` | 否 | 否 | 否 | — | — |
| 2 | `ETQ_ODP_Date` | `datetime` | 否 | 否 | 否 | — | — |
| 3 | `ETQ_Target` | `smallint` | 否 | 否 | 否 | — | — |

### dbo.Hanger_Registry（表，1 字段）

| 序号 | 字段 | SQL 类型 | 可空 | 标识列 | 计算列 | 默认表达式 | SQL 注释 |
|---:|---|---|---|---|---|---|---|
| 1 | `HR_HM_ID` | `nvarchar(12)` | 否 | 否 | 否 | — | — |

### dbo.Line_Bridging_Information（表，4 字段）

| 序号 | 字段 | SQL 类型 | 可空 | 标识列 | 计算列 | 默认表达式 | SQL 注释 |
|---:|---|---|---|---|---|---|---|
| 1 | `From_Line` | `smallint` | 否 | 否 | 否 | ((0)) | — |
| 2 | `From_Station` | `smallint` | 否 | 否 | 否 | ((0)) | — |
| 3 | `To_Line` | `smallint` | 是 | 否 | 否 | ((0)) | — |
| 4 | `To_Station` | `smallint` | 是 | 否 | 否 | ((0)) | — |

### dbo.Line_Sorting_Stations（表，2 字段）

| 序号 | 字段 | SQL 类型 | 可空 | 标识列 | 计算列 | 默认表达式 | SQL 注释 |
|---:|---|---|---|---|---|---|---|
| 1 | `Line_Number` | `smallint` | 否 | 否 | 否 | — | — |
| 2 | `Sorting_Station` | `smallint` | 否 | 否 | 否 | — | — |

### dbo.Lines_On_Network（表，4 字段）

| 序号 | 字段 | SQL 类型 | 可空 | 标识列 | 计算列 | 默认表达式 | SQL 注释 |
|---:|---|---|---|---|---|---|---|
| 1 | `Line_Number` | `smallint` | 否 | 否 | 否 | ((0)) | — |
| 2 | `Line_Description` | `nvarchar(50)` | 是 | 否 | 否 | — | — |
| 3 | `Stations` | `smallint` | 是 | 否 | 否 | ((0)) | — |
| 5 | `Line_Type` | `nvarchar(4)` | 是 | 否 | 否 | — | — |

### dbo.Machine_Codes（表，6 字段）

| 序号 | 字段 | SQL 类型 | 可空 | 标识列 | 计算列 | 默认表达式 | SQL 注释 |
|---:|---|---|---|---|---|---|---|
| 1 | `MC_Key` | `int` | 否 | 是 | 否 | — | — |
| 2 | `MC_Name` | `nvarchar(15)` | 是 | 否 | 否 | — | — |
| 3 | `MC_Type` | `nvarchar(25)` | 是 | 否 | 否 | — | — |
| 4 | `MC_Gauge` | `nvarchar(5)` | 是 | 否 | 否 | — | — |
| 5 | `MC_Attachments` | `nvarchar(25)` | 是 | 否 | 否 | — | — |
| 6 | `MC_Quantity` | `smallint` | 是 | 否 | 否 | ((0)) | — |

### dbo.Operation_Codes（表，13 字段）

| 序号 | 字段 | SQL 类型 | 可空 | 标识列 | 计算列 | 默认表达式 | SQL 注释 |
|---:|---|---|---|---|---|---|---|
| 1 | `OC_Key` | `int` | 否 | 否 | 否 | ((0)) | Must be a  number between 0 an 99999999  made up of 4 digit Primary code and 4 digit operation sub code |
| 2 | `OC_Sub_Code` | `smallint` | 否 | 否 | 否 | ((0)) | Must be a  number between 0 an 9999 |
| 3 | `OC_PC_Key` | `smallint` | 是 | 否 | 否 | ((0)) | — |
| 4 | `OC_Description` | `nvarchar(255)` | 是 | 否 | 否 | — | — |
| 5 | `OC_Standard_Time` | `real` | 是 | 否 | 否 | ((0)) | — |
| 6 | `OC_Piece_Rate` | `real` | 是 | 否 | 否 | ((0)) | — |
| 7 | `OC_PA_Key` | `nvarchar(10)` | 是 | 否 | 否 | — | — |
| 8 | `OC_Online` | `bit` | 否 | 否 | 否 | ((1)) | — |
| 9 | `OC_Non_Standard_Time` | `real` | 是 | 否 | 否 | — | — |
| 10 | `OC_Piece_Rate_Additional` | `real` | 是 | 否 | 否 | — | — |
| 11 | `OC_ID` | `nvarchar(50)` | 是 | 否 | 否 | — | — |
| 12 | `OC_GKey` | `uniqueidentifier` | 是 | 否 | 否 | — | — |
| 13 | `OC_Code` | `nvarchar(50)` | 是 | 否 | 否 | — | — |

### dbo.Pay_Categories（表，4 字段）

| 序号 | 字段 | SQL 类型 | 可空 | 标识列 | 计算列 | 默认表达式 | SQL 注释 |
|---:|---|---|---|---|---|---|---|
| 1 | `PA_Key` | `nvarchar(10)` | 否 | 否 | 否 | — | Eg BASE, EMPAVG, MINIMUM |
| 2 | `PA_CategoryType` | `nvarchar(20)` | 否 | 否 | 否 | ('Currency Rate') | eg Currency, Employee Average, Employee Guarantee etc |
| 3 | `PA_Description` | `nvarchar(25)` | 是 | 否 | 否 | — | — |
| 4 | `PA_CurrencyValue` | `real` | 是 | 否 | 否 | ((0)) | 0 if from the Employee database otherwise the actual value, eg |

### dbo.Primary_Codes（表，4 字段）

| 序号 | 字段 | SQL 类型 | 可空 | 标识列 | 计算列 | 默认表达式 | SQL 注释 |
|---:|---|---|---|---|---|---|---|
| 1 | `PC_Key` | `smallint` | 否 | 否 | 否 | ((0)) | Must be a  number between 0 an 9999 |
| 2 | `PC_Description` | `nvarchar(25)` | 是 | 否 | 否 | — | — |
| 3 | `PC_MC_Key` | `int` | 是 | 否 | 否 | ((0)) | — |
| 4 | `PC_ID` | `nvarchar(50)` | 是 | 否 | 否 | — | — |

### dbo.QC_Header_Codes（表，3 字段）

| 序号 | 字段 | SQL 类型 | 可空 | 标识列 | 计算列 | 默认表达式 | SQL 注释 |
|---:|---|---|---|---|---|---|---|
| 1 | `QCHC_Key` | `uniqueidentifier` | 否 | 否 | 否 | (newid()) | — |
| 2 | `QCHC_Description` | `nvarchar(255)` | 是 | 否 | 否 | — | — |
| 3 | `QCHC_LCD` | `nvarchar(32)` | 是 | 否 | 否 | — | — |

### dbo.QC_Incidents（表，9 字段）

| 序号 | 字段 | SQL 类型 | 可空 | 标识列 | 计算列 | 默认表达式 | SQL 注释 |
|---:|---|---|---|---|---|---|---|
| 1 | `QCI_Key` | `uniqueidentifier` | 否 | 否 | 否 | (newid()) | — |
| 2 | `QCI_Date` | `datetime` | 是 | 否 | 否 | — | — |
| 3 | `QCI_Line` | `smallint` | 是 | 否 | 否 | — | — |
| 4 | `QCI_Workstation` | `smallint` | 是 | 否 | 否 | — | — |
| 5 | `QCI_EM_Key` | `int` | 是 | 否 | 否 | — | — |
| 6 | `QCI_ST_Key` | `int` | 是 | 否 | 否 | — | — |
| 7 | `QCI_OC_Key` | `int` | 是 | 否 | 否 | — | — |
| 8 | `QCI_QCSC_Key` | `uniqueidentifier` | 是 | 否 | 否 | (newid()) | — |
| 9 | `QCI_Quantity` | `smallint` | 是 | 否 | 否 | — | — |

### dbo.QC_ODP（表，9 字段）

| 序号 | 字段 | SQL 类型 | 可空 | 标识列 | 计算列 | 默认表达式 | SQL 注释 |
|---:|---|---|---|---|---|---|---|
| 1 | `QCO_Key` | `int` | 否 | 否 | 否 | — | — |
| 2 | `QCO_ST_Key` | `int` | 否 | 否 | 否 | — | — |
| 3 | `QCO_Use_Defect_OC` | `bit` | 否 | 否 | 否 | — | — |
| 4 | `QCO_PC_Key` | `int` | 否 | 否 | 否 | — | — |
| 5 | `QCO_PC_Key_With_Defects` | `int` | 否 | 否 | 否 | — | — |
| 6 | `QCO_From_Qty` | `smallint` | 否 | 否 | 否 | — | — |
| 7 | `QCO_To_Qty` | `smallint` | 否 | 否 | 否 | — | — |
| 8 | `QCO_OC_Key` | `int` | 否 | 否 | 否 | — | — |
| 9 | `QCO_OC_Key_With_Defects` | `int` | 否 | 否 | 否 | — | — |

### dbo.QC_Redo（表，11 字段）

| 序号 | 字段 | SQL 类型 | 可空 | 标识列 | 计算列 | 默认表达式 | SQL 注释 |
|---:|---|---|---|---|---|---|---|
| 1 | `QR_Key` | `uniqueidentifier` | 否 | 否 | 否 | — | — |
| 2 | `QR_Create_Time` | `datetime` | 否 | 否 | 否 | — | — |
| 3 | `QR_ST_Key` | `int` | 否 | 否 | 否 | — | — |
| 4 | `QR_OC_Key` | `int` | 否 | 否 | 否 | — | — |
| 5 | `QR_EM_Key` | `int` | 否 | 否 | 否 | — | — |
| 6 | `QR_Line` | `smallint` | 否 | 否 | 否 | — | — |
| 7 | `QR_Station` | `smallint` | 否 | 否 | 否 | — | — |
| 8 | `QR_HM_ID` | `nvarchar(12)` | 否 | 否 | 否 | — | — |
| 9 | `QR_HM_Key` | `uniqueidentifier` | 否 | 否 | 否 | — | — |
| 10 | `QR_STPO_Key` | `int` | 是 | 否 | 否 | — | — |
| 11 | `QR_Handled` | `bit` | 否 | 否 | 否 | — | — |

### dbo.QC_Rework（表，19 字段）

| 序号 | 字段 | SQL 类型 | 可空 | 标识列 | 计算列 | 默认表达式 | SQL 注释 |
|---:|---|---|---|---|---|---|---|
| 1 | `QCR_Key` | `uniqueidentifier` | 否 | 否 | 否 | — | — |
| 2 | `QCR_Defect_DateTime` | `datetime` | 否 | 否 | 否 | — | — |
| 4 | `QCR_Defect_EM_Key` | `int` | 否 | 否 | 否 | — | — |
| 5 | `QCR_Defect_ST_Key` | `int` | 否 | 否 | 否 | — | — |
| 6 | `QCR_Defect_OC_Key` | `int` | 否 | 否 | 否 | — | — |
| 7 | `QCR_Sent_To_Rework_By_EM_Key` | `int` | 否 | 否 | 否 | — | — |
| 8 | `QCR_Defect_Quantity` | `smallint` | 否 | 否 | 否 | — | — |
| 9 | `QCR_From_QC_Station` | `smallint` | 是 | 否 | 否 | — | — |
| 10 | `QCR_HM_ID` | `nvarchar(12)` | 是 | 否 | 否 | — | — |
| 11 | `QCR_QC_DateTime` | `datetime` | 是 | 否 | 否 | — | — |
| 12 | `QCR_Repair_EM_Key` | `int` | 是 | 否 | 否 | — | — |
| 13 | `QCR_Repair_DateTime` | `datetime` | 是 | 否 | 否 | — | — |
| 14 | `QCR_Repair_Quantity` | `int` | 是 | 否 | 否 | — | — |
| 15 | `QCR_Defect_CM_Key` | `int` | 是 | 否 | 否 | — | — |
| 16 | `QCR_Defect_SM_Key` | `int` | 是 | 否 | 否 | — | — |
| 17 | `QCR_QCSC_Key` | `uniqueidentifier` | 是 | 否 | 否 | — | — |
| 18 | `QCR_HM_Key` | `uniqueidentifier` | 是 | 否 | 否 | — | — |
| 19 | `QCR_STPO_Key` | `int` | 是 | 否 | 否 | — | — |
| 20 | `QCR_QC_OC_Key` | `int` | 是 | 否 | 否 | — | — |

### dbo.QC_Scrap（表，12 字段）

| 序号 | 字段 | SQL 类型 | 可空 | 标识列 | 计算列 | 默认表达式 | SQL 注释 |
|---:|---|---|---|---|---|---|---|
| 1 | `QCS_Key` | `uniqueidentifier` | 否 | 否 | 否 | — | — |
| 2 | `QCS_Defect_DateTime` | `datetime` | 否 | 否 | 否 | — | — |
| 4 | `QCS_EM_Key` | `int` | 否 | 否 | 否 | — | — |
| 5 | `QCS_ST_Key` | `int` | 否 | 否 | 否 | — | — |
| 6 | `QCS_OC_Key` | `int` | 否 | 否 | 否 | — | — |
| 7 | `QCS_Sent_To_Scrap_By_EM_Key` | `int` | 否 | 否 | 否 | — | — |
| 9 | `QCS_HM_ID` | `nvarchar(12)` | 是 | 否 | 否 | — | — |
| 10 | `QCS_Scrap_DateTime` | `datetime` | 是 | 否 | 否 | — | — |
| 11 | `QCS_QCSC_Key` | `uniqueidentifier` | 是 | 否 | 否 | — | — |
| 12 | `QCS_STPO_Key` | `int` | 是 | 否 | 否 | — | — |
| 13 | `QCS_HM_Key` | `uniqueidentifier` | 是 | 否 | 否 | — | — |
| 14 | `QCS_Scrap_Quantity` | `smallint` | 否 | 否 | 否 | — | — |

### dbo.QC_Station_To_Station_Routing（表，4 字段）

| 序号 | 字段 | SQL 类型 | 可空 | 标识列 | 计算列 | 默认表达式 | SQL 注释 |
|---:|---|---|---|---|---|---|---|
| 1 | `S2S_HM_ID` | `nvarchar(12)` | 否 | 否 | 否 | — | — |
| 2 | `S2S_On_Line` | `int` | 否 | 否 | 否 | — | — |
| 3 | `S2S_To_Station` | `int` | 否 | 否 | 否 | — | — |
| 4 | `S2S_Scheduled_Time` | `datetime` | 否 | 否 | 否 | — | — |

### dbo.QC_Sub_Codes（表，6 字段）

| 序号 | 字段 | SQL 类型 | 可空 | 标识列 | 计算列 | 默认表达式 | SQL 注释 |
|---:|---|---|---|---|---|---|---|
| 1 | `QCSC_Key` | `uniqueidentifier` | 否 | 否 | 否 | (newid()) | — |
| 2 | `QCSC_QCHC_Key` | `uniqueidentifier` | 是 | 否 | 否 | (newid()) | — |
| 3 | `QCSC_Description` | `nvarchar(255)` | 是 | 否 | 否 | — | — |
| 4 | `QCSC_LCD` | `nvarchar(32)` | 是 | 否 | 否 | — | — |
| 5 | `QCSC_Number` | `int` | 是 | 否 | 否 | — | — |
| 6 | `QCSC_Is_Rework` | `bit` | 是 | 否 | 否 | — | — |

### dbo.Sequential_Production（表，7 字段）

| 序号 | 字段 | SQL 类型 | 可空 | 标识列 | 计算列 | 默认表达式 | SQL 注释 |
|---:|---|---|---|---|---|---|---|
| 1 | `SP_Key` | `int` | 否 | 否 | 否 | — | — |
| 2 | `SP_Line` | `smallint` | 否 | 否 | 否 | — | — |
| 3 | `SP_Station` | `smallint` | 否 | 否 | 否 | — | — |
| 4 | `SP_Active` | `bit` | 否 | 否 | 否 | — | — |
| 5 | `SP_ST_Key` | `int` | 否 | 否 | 否 | — | — |
| 6 | `SP_Type` | `nvarchar(12)` | 否 | 否 | 否 | — | — |
| 7 | `SP_Value` | `nvarchar(25)` | 否 | 否 | 否 | — | — |

### dbo.Size_Master（表，4 字段）

| 序号 | 字段 | SQL 类型 | 可空 | 标识列 | 计算列 | 默认表达式 | SQL 注释 |
|---:|---|---|---|---|---|---|---|
| 1 | `SM_Key` | `int` | 否 | 是 | 否 | — | — |
| 2 | `SM_Short_Description` | `nvarchar(10)` | 是 | 否 | 否 | — | — |
| 3 | `SM_Description` | `nvarchar(255)` | 是 | 否 | 否 | — | — |
| 4 | `SM_GKey` | `uniqueidentifier` | 是 | 否 | 否 | — | — |

### dbo.Sorting_Information_By_Line（表，4 字段）

| 序号 | 字段 | SQL 类型 | 可空 | 标识列 | 计算列 | 默认表达式 | SQL 注释 |
|---:|---|---|---|---|---|---|---|
| 1 | `SIBL_SLSI_Key` | `int` | 否 | 否 | 否 | — | — |
| 2 | `SIBL_Line` | `smallint` | 否 | 否 | 否 | — | — |
| 3 | `SIBL_Quantity` | `int` | 否 | 否 | 否 | — | — |
| 4 | `SIBL_Min_Loaded_Time` | `datetime` | 否 | 否 | 否 | — | — |

### dbo.Style_Colours_Not_Used（表，2 字段）

| 序号 | 字段 | SQL 类型 | 可空 | 标识列 | 计算列 | 默认表达式 | SQL 注释 |
|---:|---|---|---|---|---|---|---|
| 1 | `SCNU_ST_Key` | `int` | 否 | 否 | 否 | — | — |
| 2 | `SCNU_CM_Key` | `int` | 否 | 否 | 否 | — | — |

### dbo.Style_Loading_Sorting_Information（表，6 字段）

| 序号 | 字段 | SQL 类型 | 可空 | 标识列 | 计算列 | 默认表达式 | SQL 注释 |
|---:|---|---|---|---|---|---|---|
| 1 | `SLSI_Key` | `int` | 否 | 是 | 否 | — | — |
| 2 | `SLSI_Type` | `smallint` | 否 | 否 | 否 | — | — |
| 3 | `SLSI_Active` | `bit` | 否 | 否 | 否 | — | — |
| 4 | `SLSI_Description` | `nvarchar(50)` | 否 | 否 | 否 | — | — |
| 5 | `SLSI_Date_Created` | `datetime` | 否 | 否 | 否 | — | — |
| 6 | `SLSI_Before_OC_Key` | `int` | 是 | 否 | 否 | — | — |

### dbo.Style_Master（表，16 字段）

| 序号 | 字段 | SQL 类型 | 可空 | 标识列 | 计算列 | 默认表达式 | SQL 注释 |
|---:|---|---|---|---|---|---|---|
| 1 | `ST_Key` | `int` | 否 | 是 | 否 | — | — |
| 2 | `ST_ID` | `nvarchar(50)` | 是 | 否 | 否 | — | — |
| 3 | `ST_Description` | `nvarchar(50)` | 是 | 否 | 否 | — | — |
| 4 | `ST_Collection` | `nvarchar(25)` | 是 | 否 | 否 | — | — |
| 5 | `ST_Fabric` | `nvarchar(25)` | 是 | 否 | 否 | — | — |
| 6 | `ST_Trim` | `nvarchar(25)` | 是 | 否 | 否 | — | — |
| 7 | `ST_Logo_Name` | `nvarchar(25)` | 是 | 否 | 否 | — | — |
| 8 | `ST_Logo_Placement` | `nvarchar(25)` | 是 | 否 | 否 | — | — |
| 9 | `ST_Comments` | `ntext` | 是 | 否 | 否 | — | — |
| 10 | `ST_Picture` | `image` | 是 | 否 | 否 | — | — |
| 11 | `ST_Date_Created` | `datetime` | 是 | 否 | 否 | (CONVERT([datetime],CONVERT([varchar],getdate(),(1)),(1))) | — |
| 12 | `ST_Last_Updated` | `datetime` | 是 | 否 | 否 | — | — |
| 13 | `ST_Line` | `smallint` | 是 | 否 | 否 | — | — |
| 14 | `ST_Bundle_Active_SRM_Key` | `int` | 是 | 否 | 否 | — | — |
| 15 | `ST_STOPM_Key` | `uniqueidentifier` | 是 | 否 | 否 | — | — |
| 16 | `ST_GKey` | `uniqueidentifier` | 是 | 否 | 否 | — | — |

### dbo.Style_Operations（表，7 字段）

| 序号 | 字段 | SQL 类型 | 可空 | 标识列 | 计算列 | 默认表达式 | SQL 注释 |
|---:|---|---|---|---|---|---|---|
| 1 | `STOP_Key` | `int` | 否 | 是 | 否 | — | — |
| 3 | `STOP_OC_Key` | `int` | 是 | 否 | 否 | ((0)) | — |
| 4 | `STOP_Order` | `smallint` | 是 | 否 | 否 | ((0)) | — |
| 5 | `STOP_Alternate_Order` | `smallint` | 是 | 否 | 否 | ((0)) | — |
| 10 | `STOP_Number` | `nvarchar(50)` | 是 | 否 | 否 | — | — |
| 11 | `STOP_Alternate_Order_Multiple` | `nvarchar(100)` | 是 | 否 | 否 | — | — |
| 12 | `STOP_STOPM_Key` | `uniqueidentifier` | 是 | 否 | 否 | — | — |

### dbo.Style_Operations_Master（表，4 字段）

| 序号 | 字段 | SQL 类型 | 可空 | 标识列 | 计算列 | 默认表达式 | SQL 注释 |
|---:|---|---|---|---|---|---|---|
| 1 | `STOPM_Key` | `uniqueidentifier` | 否 | 否 | 否 | — | — |
| 2 | `STOPM_ID` | `nvarchar(50)` | 是 | 否 | 否 | — | — |
| 3 | `STOPM_Description` | `nvarchar(50)` | 否 | 否 | 否 | — | — |
| 4 | `STOPM_Last_Updated` | `datetime` | 否 | 否 | 否 | — | — |

### dbo.Style_Planned_Orders（表，17 字段）

| 序号 | 字段 | SQL 类型 | 可空 | 标识列 | 计算列 | 默认表达式 | SQL 注释 |
|---:|---|---|---|---|---|---|---|
| 1 | `STPO_Key` | `int` | 否 | 是 | 否 | — | — |
| 2 | `STPO_ST_Key` | `int` | 是 | 否 | 否 | — | — |
| 3 | `STPO_ID` | `nvarchar(50)` | 是 | 否 | 否 | — | — |
| 4 | `STPO_CI_Name` | `nvarchar(50)` | 是 | 否 | 否 | — | — |
| 5 | `STPO_PlannedQuantity` | `int` | 是 | 否 | 否 | — | — |
| 6 | `STPO_Comment` | `nvarchar(50)` | 是 | 否 | 否 | — | — |
| 7 | `STPO_CreateDate` | `datetime` | 是 | 否 | 否 | — | — |
| 8 | `STPO_ModifiedDate` | `datetime` | 是 | 否 | 否 | — | — |
| 9 | `STPO_OM_ID` | `nvarchar(50)` | 是 | 否 | 否 | — | — |
| 10 | `STPO_PlanStartDate` | `datetime` | 是 | 否 | 否 | — | — |
| 11 | `STPO_PlanFinishDate` | `datetime` | 是 | 否 | 否 | — | — |
| 12 | `STPO_SM_Key` | `int` | 是 | 否 | 否 | — | — |
| 13 | `STPO_CM_Key` | `int` | 是 | 否 | 否 | — | — |
| 14 | `STPO_Level` | `int` | 是 | 否 | 否 | — | — |
| 15 | `STPO_LP_Key` | `uniqueidentifier` | 是 | 否 | 否 | — | — |
| 16 | `STPO_Level_Description` | `nvarchar(25)` | 是 | 否 | 否 | — | — |
| 17 | `STPO_Completed` | `bit` | 是 | 否 | 否 | — | — |

### dbo.Style_Planned_Orders_Quantity_Loaded（表，3 字段）

| 序号 | 字段 | SQL 类型 | 可空 | 标识列 | 计算列 | 默认表达式 | SQL 注释 |
|---:|---|---|---|---|---|---|---|
| 1 | `STPO_Key` | `int` | 否 | 否 | 否 | — | — |
| 2 | `STPO_Line` | `smallint` | 否 | 否 | 否 | — | — |
| 3 | `STPO_Quantity_Loaded` | `int` | 是 | 否 | 否 | — | — |

### dbo.Style_Planned_Orders_Quantity_Registered（表，3 字段）

| 序号 | 字段 | SQL 类型 | 可空 | 标识列 | 计算列 | 默认表达式 | SQL 注释 |
|---:|---|---|---|---|---|---|---|
| 1 | `STPR_Key` | `int` | 否 | 否 | 否 | — | — |
| 2 | `STPR_Line` | `smallint` | 否 | 否 | 否 | — | — |
| 3 | `STPR_Quantity_Registered` | `int` | 否 | 否 | 否 | — | — |

### dbo.Style_Route_Detail（表，13 字段）

| 序号 | 字段 | SQL 类型 | 可空 | 标识列 | 计算列 | 默认表达式 | SQL 注释 |
|---:|---|---|---|---|---|---|---|
| 1 | `SRD_Key` | `int` | 否 | 是 | 否 | — | — |
| 2 | `SRD_SRM_Key` | `int` | 是 | 否 | 否 | ((0)) | — |
| 3 | `SRD_OC_Key` | `int` | 是 | 否 | 否 | ((0)) | — |
| 4 | `SRD_Line_Number` | `int` | 是 | 否 | 否 | ((0)) | — |
| 5 | `SRD_Station_Number` | `int` | 是 | 否 | 否 | ((0)) | — |
| 6 | `SRD_Receive` | `bit` | 是 | 否 | 否 | — | — |
| 7 | `SRD_Receive_Colour_Key` | `int` | 是 | 否 | 否 | — | — |
| 8 | `SRD_Receive_Size_Key` | `int` | 是 | 否 | 否 | — | — |
| 9 | `SRD_Distribution_Percent` | `real` | 是 | 否 | 否 | — | — |
| 10 | `SRD_Use_Manual_Distribution` | `bit` | 是 | 否 | 否 | — | — |
| 11 | `SRD_CS_Used` | `bit` | 是 | 否 | 否 | — | — |
| 12 | `SRD_CS_Key` | `uniqueidentifier` | 是 | 否 | 否 | — | — |
| 13 | `SRD_Quantity_Sent` | `int` | 是 | 否 | 否 | — | — |

### dbo.Style_Route_Master（表，7 字段）

| 序号 | 字段 | SQL 类型 | 可空 | 标识列 | 计算列 | 默认表达式 | SQL 注释 |
|---:|---|---|---|---|---|---|---|
| 1 | `SRM_Key` | `int` | 否 | 是 | 否 | — | — |
| 2 | `SRM_Description` | `nvarchar(255)` | 是 | 否 | 否 | — | — |
| 4 | `SRM_Line` | `smallint` | 是 | 否 | 否 | — | — |
| 5 | `Created_Date` | `datetime` | 是 | 否 | 否 | ((1)) | — |
| 6 | `Modified_Date` | `datetime` | 是 | 否 | 否 | — | — |
| 7 | `SRM_STOPM_Key` | `uniqueidentifier` | 是 | 否 | 否 | — | — |
| 8 | `SRM_Locked_By_Line` | `smallint` | 是 | 否 | 否 | — | — |

### dbo.Style_Route_Skipped（表，2 字段）

| 序号 | 字段 | SQL 类型 | 可空 | 标识列 | 计算列 | 默认表达式 | SQL 注释 |
|---:|---|---|---|---|---|---|---|
| 1 | `SRS_SRM_Key` | `int` | 否 | 否 | 否 | — | — |
| 2 | `SRS_OC_Key ` | `int` | 否 | 否 | 否 | — | — |

### dbo.Style_Route_Specific_Operation_Overload（表，3 字段）

| 序号 | 字段 | SQL 类型 | 可空 | 标识列 | 计算列 | 默认表达式 | SQL 注释 |
|---:|---|---|---|---|---|---|---|
| 1 | `SRO_SRM_Key` | `int` | 否 | 否 | 否 | — | — |
| 2 | `SRO_OC_Key` | `int` | 否 | 否 | 否 | — | — |
| 3 | `SRO_OverLoad_Station` | `smallint` | 否 | 否 | 否 | — | — |

### dbo.Style_Sizes_Not_Used（表，2 字段）

| 序号 | 字段 | SQL 类型 | 可空 | 标识列 | 计算列 | 默认表达式 | SQL 注释 |
|---:|---|---|---|---|---|---|---|
| 1 | `SSNU_ST_Key` | `int` | 否 | 否 | 否 | — | — |
| 2 | `SSNU_SM_Key` | `int` | 否 | 否 | 否 | — | — |

### dbo.User_Id（表，5 字段）

| 序号 | 字段 | SQL 类型 | 可空 | 标识列 | 计算列 | 默认表达式 | SQL 注释 |
|---:|---|---|---|---|---|---|---|
| 1 | `UI_UserId` | `nvarchar(20)` | 否 | 否 | 否 | — | — |
| 2 | `UI_PasswordHash` | `int` | 是 | 否 | 否 | ((0)) | — |
| 3 | `UI_Description` | `nvarchar(255)` | 是 | 否 | 否 | — | — |
| 5 | `UI_Active` | `bit` | 否 | 否 | 否 | ((1)) | — |
| 6 | `UI_Access_Code` | `nvarchar(100)` | 是 | 否 | 否 | — | — |

### dbo.Work_Codes（表，9 字段）

| 序号 | 字段 | SQL 类型 | 可空 | 标识列 | 计算列 | 默认表达式 | SQL 注释 |
|---:|---|---|---|---|---|---|---|
| 1 | `WC_Key` | `smallint` | 否 | 否 | 否 | ((0)) | — |
| 2 | `WC_Description` | `nvarchar(25)` | 是 | 否 | 否 | — | — |
| 3 | `WC_IsPieceWork` | `bit` | 否 | 否 | 否 | ((0)) | — |
| 4 | `WC_IsNonStandard` | `bit` | 否 | 否 | 否 | ((0)) | — |
| 5 | `WC_IsDelay` | `bit` | 否 | 否 | 否 | ((1)) | — |
| 6 | `WC_SetNoReceive` | `bit` | 否 | 否 | 否 | ((0)) | — |
| 7 | `WC_PA_Key` | `nvarchar(10)` | 否 | 否 | 否 | ('BASE') | — |
| 8 | `WC_Supplement` | `real` | 是 | 否 | 否 | ((0)) | — |
| 9 | `WC_Prohibit_Outfeed` | `bit` | 是 | 否 | 否 | — | — |

## 5. 索引清单

索引的键列、包含列和过滤定义来自系统目录；“—”表示无过滤条件或无包含列。

### `IHS`

| 对象 | 索引 | 类型 | 主键 | 唯一 | 停用 | 键列 | 包含列 | 过滤条件 |
|---|---|---|---|---|---|---|---|---|
| `dbo.Accessories_Routing` | `PK_ACC_Key` | CLUSTERED | 是 | 是 | 否 | `ACC_Key` | — | — |
| `dbo.Clock_InOut_History` | `PK_Clock_InOut_History` | CLUSTERED | 是 | 是 | 否 | `CIOH_Key` | — | — |
| `dbo.Database_Tables_Last_Modified` | `Primary_Key` | NONCLUSTERED | 否 | 否 | 否 | `Row_Index` | — | — |
| `dbo.Database_Tables_Last_Modified` | `aaaaaDatabase_Tables_Last_Modified_PK` | NONCLUSTERED | 是 | 是 | 否 | `Row_Index` | — | — |
| `dbo.Double_Screen_Styles` | `PK__Double_Screen_St__56E8E7AB` | CLUSTERED | 是 | 是 | 否 | `DSS_Key` | — | — |
| `dbo.Hanger_Details_Export` | `PK_Hanger_Details_Export` | CLUSTERED | 是 | 是 | 否 | `HDE_HRD_Key` | — | — |
| `dbo.Hanger_Master` | `HM_CM_Key` | NONCLUSTERED | 否 | 否 | 否 | `HM_CM_Key` | — | — |
| `dbo.Hanger_Master` | `HM_ID` | NONCLUSTERED | 否 | 否 | 否 | `HM_ID` | — | — |
| `dbo.Hanger_Master` | `HM_SM_Key` | NONCLUSTERED | 否 | 否 | 否 | `HM_SM_Key` | — | — |
| `dbo.Hanger_Master` | `HM_SRM_Key` | NONCLUSTERED | 否 | 否 | 否 | `HM_SRM_Key` | — | — |
| `dbo.Hanger_Master` | `HM_STPO_Key` | NONCLUSTERED | 否 | 否 | 否 | `HM_STPO_Key` | — | — |
| `dbo.Hanger_Master` | `aaaaaHanger_Master_PK` | NONCLUSTERED | 是 | 是 | 否 | `HM_ID` | — | — |
| `dbo.Hanger_Quality_Control_Incident` | `HQCI_EM_Key` | NONCLUSTERED | 否 | 否 | 否 | `HQCI_EM_Key` | — | — |
| `dbo.Hanger_Quality_Control_Incident` | `HQCI_Key` | NONCLUSTERED | 否 | 否 | 否 | `HQCI_Key` | — | — |
| `dbo.Hanger_Quality_Control_Incident` | `HQCI_OC_Key` | NONCLUSTERED | 否 | 否 | 否 | `HQCI_OC_Key` | — | — |
| `dbo.Hanger_Quality_Control_Incident` | `HQCI_QCSC_Key` | NONCLUSTERED | 否 | 否 | 否 | `HQCI_QCSC_Key` | — | — |
| `dbo.Hanger_Quality_Control_Incident` | `HQCI_ST_Key` | NONCLUSTERED | 否 | 否 | 否 | `HQCI_ST_Key` | — | — |
| `dbo.Hanger_Quality_Control_Incident` | `aaaaaHanger_Quality_Control_Incident_PK` | NONCLUSTERED | 是 | 是 | 否 | `HQCI_Key` | — | — |
| `dbo.Hanger_Routing_Details` | `HRD_EM_Key` | NONCLUSTERED | 否 | 否 | 否 | `HRD_EM_Key` | — | — |
| `dbo.Hanger_Routing_Details` | `HRD_HM_ID` | NONCLUSTERED | 否 | 否 | 否 | `HRD_HM_ID` | — | — |
| `dbo.Hanger_Routing_Details` | `HRD_Key` | NONCLUSTERED | 否 | 否 | 否 | `HRD_Key` | — | — |
| `dbo.Hanger_Routing_Details` | `HRD_OC_Key` | NONCLUSTERED | 否 | 否 | 否 | `HRD_OC_Key` | — | — |
| `dbo.Hanger_Routing_Details` | `Hanger_Routing_Details_Hanger_Master` | NONCLUSTERED | 否 | 否 | 否 | `HRD_HM_ID` | — | — |
| `dbo.Hanger_Routing_Details` | `aaaaaHanger_Routing_Details_PK` | NONCLUSTERED | 是 | 是 | 否 | `HRD_Key` | — | — |
| `dbo.Hangers_Dispatched` | `PK_Hangers_Dispatched` | CLUSTERED | 是 | 是 | 否 | `HD_HM_ID` | — | — |
| `dbo.Hangers_To_Synch` | `PK__Hangers___F4FCABB75E8A0973` | CLUSTERED | 是 | 是 | 否 | `Hanger_ID` | — | — |
| `dbo.Inhibition_Interface` | `PK_Inhibition_Interface` | CLUSTERED | 是 | 是 | 否 | `Inhibit_Interface` | — | — |
| `dbo.Line_Break_Times` | `aaaaaLine_Break_Times_PK` | NONCLUSTERED | 是 | 是 | 否 | `Break_Number` | — | — |
| `dbo.Line_Daily_Break_Times` | `aaaaaLine_Daily_Break_Times_PK` | NONCLUSTERED | 是 | 是 | 否 | `Break_Date`, `Break_Number` | — | — |
| `dbo.Line_Daily_Shift_Times` | `aaaaaLine_Daily_Shift_Times_PK` | NONCLUSTERED | 是 | 是 | 否 | `Shift_Date`, `Shift_Number` | — | — |
| `dbo.Line_Detour_Setup` | `PK_Line_Detour_Setup` | CLUSTERED | 是 | 是 | 否 | `LD_Station` | — | — |
| `dbo.Line_Information` | `PK__Line_Inf__5892DEF75119F053` | CLUSTERED | 是 | 是 | 否 | `Date_Of_Configuration` | — | — |
| `dbo.Line_Network_Setup` | `Date Of Configuration` | NONCLUSTERED | 否 | 是 | 否 | `Date_Of_Configuration` | — | — |
| `dbo.Line_Network_Setup` | `aaaaaLine_Network_Setup_PK` | NONCLUSTERED | 是 | 是 | 否 | `Date_Of_Configuration` | — | — |
| `dbo.Line_OverLoad_Stations` | `aaaaaLine_OverLoad_Stations_PK` | NONCLUSTERED | 是 | 是 | 否 | `OverLoad_Station` | — | — |
| `dbo.Line_Overload_Stations_Specific_Operation` | `PK_Overload_Station` | CLUSTERED | 是 | 是 | 否 | `Overload_Station` | — | — |
| `dbo.Line_Pusher_RFID` | `PK_Line_Pusher_RFID` | CLUSTERED | 是 | 是 | 否 | `PR_RFID` | — | — |
| `dbo.Line_Pusher_Setup` | `PK__Line_Pus__5892DEF7F1BCD657` | CLUSTERED | 是 | 是 | 否 | `Date_Of_Configuration` | — | — |
| `dbo.Line_QC_Stations` | `aaaaaLine_QC_Stations_PK` | NONCLUSTERED | 是 | 是 | 否 | `QC_Station` | — | — |
| `dbo.Line_Quickturn_Setup` | `PK_Line_Quickturn_Setup` | CLUSTERED | 是 | 是 | 否 | `LQT_Station` | — | — |
| `dbo.Line_Rework_Stations` | `PK_Line_Rework_Stations` | CLUSTERED | 是 | 是 | 否 | `LRS_Workstation` | — | — |
| `dbo.Line_Shift_Times` | `aaaaaLine_Shift_Times_PK` | NONCLUSTERED | 是 | 是 | 否 | `Shift_Number` | — | — |
| `dbo.Line_Stations_To_Use_Colour_And_Size_Menu_With_Console` | `PK_Use_At_Station` | CLUSTERED | 是 | 是 | 否 | `Use_At_Station` | — | — |
| `dbo.Line_System_Configuration` | `Date_Of_Configuration` | NONCLUSTERED | 否 | 是 | 否 | `Date_Of_Configuration` | — | — |
| `dbo.Line_System_Configuration` | `aaaaaLine_System_Configuration_PK` | NONCLUSTERED | 是 | 是 | 否 | `Date_Of_Configuration` | — | — |
| `dbo.Line_Wireless_Controller` | `PK_Line_Wireless_Controller` | CLUSTERED | 是 | 是 | 否 | `LWC_Group_Id` | — | — |
| `dbo.Line_Workstation_Setup` | `PK__Line_Wor__0674D7F94A4569CB` | CLUSTERED | 是 | 是 | 否 | `Workstation` | — | — |
| `dbo.ODP_Detail` | `Daily ODP DetailStyle ID` | NONCLUSTERED | 否 | 否 | 否 | `ODPD_ST_Key` | — | — |
| `dbo.ODP_Detail` | `ODPD_ODP_Key` | NONCLUSTERED | 否 | 否 | 否 | `ODPD_ODP_Key` | — | — |
| `dbo.ODP_Detail` | `ODP_MasterODP_Detail` | NONCLUSTERED | 否 | 否 | 否 | `ODPD_ODP_Key` | — | — |
| `dbo.ODP_Detail` | `Operation Code` | NONCLUSTERED | 否 | 否 | 否 | `ODPD_OC_Key` | — | — |
| `dbo.ODP_Detail` | `Work Code` | NONCLUSTERED | 否 | 否 | 否 | `ODPD_WC_Key` | — | — |
| `dbo.ODP_Detail` | `aaaaaODP_Detail_PK` | NONCLUSTERED | 是 | 是 | 否 | `ODPD_Key` | — | — |
| `dbo.ODP_Master` | `Employee Id` | NONCLUSTERED | 否 | 否 | 否 | `ODP_EM_Key` | — | — |
| `dbo.ODP_Master` | `ODP_Key` | NONCLUSTERED | 否 | 否 | 否 | `ODP_Key` | — | — |
| `dbo.ODP_Master` | `aaaaaODP_Master_PK` | NONCLUSTERED | 是 | 是 | 否 | `ODP_Key` | — | — |
| `dbo.ODP_StartStop_History` | `PK_ODP_StartStop_History` | CLUSTERED | 是 | 是 | 否 | `OSSH_Key` | — | — |
| `dbo.Office_Computers_Connected` | `Primary_Key` | NONCLUSTERED | 否 | 否 | 否 | `Primary_Key` | — | — |
| `dbo.Office_Computers_Connected` | `aaaaaOffice_Computers_Connected_PK` | NONCLUSTERED | 是 | 是 | 否 | `Primary_Key` | — | — |
| `dbo.Quality_Control_First` | `QCF_Key` | NONCLUSTERED | 否 | 否 | 否 | `QCF_Key` | — | — |
| `dbo.Quality_Control_First` | `QCF_Operation_Code` | NONCLUSTERED | 否 | 否 | 否 | `QCF_Operation_Code` | — | — |
| `dbo.Quality_Control_First` | `QCF_Style_Id` | NONCLUSTERED | 否 | 否 | 否 | `QCF_Style_Id` | — | — |
| `dbo.Quality_Control_First` | `aaaaaQuality_Control_First_PK` | NONCLUSTERED | 是 | 是 | 否 | `QCF_Key` | — | — |
| `dbo.Quality_Control_Random` | `aaaaaQuality_Control_Random_PK` | NONCLUSTERED | 是 | 是 | 否 | `QCR_Workstation` | — | — |
| `dbo.RTH_Time_Segments` | `PK_RTH_Time_Segments` | CLUSTERED | 是 | 是 | 否 | `RTHS_Segment` | — | — |
| `dbo.Roll_Titles` | `PK_Roll_Titles` | CLUSTERED | 是 | 是 | 否 | `RT_Key` | — | — |
| `dbo.Roll_Titles_Archive` | `PK__Roll_Titles_Arch__540C7B00` | CLUSTERED | 是 | 是 | 否 | `RTA_Key` | — | — |
| `dbo.Route Tree` | `Key` | NONCLUSTERED | 否 | 否 | 否 | `Key` | — | — |
| `dbo.Run_At_Rate` | `PK_Run_At_Rate` | CLUSTERED | 是 | 是 | 否 | `RAR_Workstation` | — | — |
| `dbo.Station_To_Station_Setup` | `PK_Station_To_Station_Setup` | CLUSTERED | 是 | 是 | 否 | `S2SS_Key` | — | — |
| `dbo.Style_Loading` | `SI_CM_Key` | NONCLUSTERED | 否 | 否 | 否 | `SL_CM_Key` | — | — |
| `dbo.Style_Loading` | `SI_SM_Key` | NONCLUSTERED | 否 | 否 | 否 | `SL_SM_Key` | — | — |
| `dbo.Style_Loading` | `SI_SRM_Key` | NONCLUSTERED | 否 | 否 | 否 | `SL_SRM_Key` | — | — |
| `dbo.Style_Loading` | `SI_ST_Key` | NONCLUSTERED | 否 | 否 | 否 | `SL_ST_Key` | — | — |
| `dbo.Style_Loading` | `SL_Key` | NONCLUSTERED | 否 | 否 | 否 | `SL_Station` | — | — |
| `dbo.Style_Loading` | `aaaaaStyle_Loading_PK` | NONCLUSTERED | 是 | 是 | 否 | `SL_Station` | — | — |
| `dbo.Style_Loading_History` | `SI_CM_Key` | NONCLUSTERED | 否 | 否 | 否 | `SL_CM_Key` | — | — |
| `dbo.Style_Loading_History` | `SI_SM_Key` | NONCLUSTERED | 否 | 否 | 否 | `SL_SM_Key` | — | — |
| `dbo.Style_Loading_History` | `SI_SRM_Key` | NONCLUSTERED | 否 | 否 | 否 | `SL_SRM_Key` | — | — |
| `dbo.Style_Loading_History` | `SI_ST_Key` | NONCLUSTERED | 否 | 否 | 否 | `SL_ST_Key` | — | — |
| `dbo.Style_Loading_History` | `SL_Key` | NONCLUSTERED | 否 | 否 | 否 | `SL_Station` | — | — |
| `dbo.Style_Loading_History` | `aaaaaStyle_Loading_History_PK` | NONCLUSTERED | 是 | 是 | 否 | `SL_Station`, `SL_Started_Loading_Date` | — | — |
| `dbo.Wireless_Console_Rework_Additional_Defects` | `PK_Wireless_Console_Rework_Additional_Defects` | CLUSTERED | 是 | 是 | 否 | `WCRAD_HM_ID`, `WCRAD_Defect_NUM` | — | — |
| `dbo.Workstation_Employee_Information` | `PK_Workstation_Employee_Information` | CLUSTERED | 是 | 是 | 否 | `WEI_Workstation`, `WEI_Position` | — | — |
| `dbo.Workstation_Saved_Information` | `Current Operation Code` | NONCLUSTERED | 否 | 否 | 否 | `WI_OC_Key` | — | — |
| `dbo.Workstation_Saved_Information` | `Current Style ID` | NONCLUSTERED | 否 | 否 | 否 | `WI_ST_Key` | — | — |
| `dbo.Workstation_Saved_Information` | `Route Id` | NONCLUSTERED | 否 | 否 | 否 | `WI_SRM_Key` | — | — |
| `dbo.Workstation_Saved_Information` | `WI_CM_Key` | NONCLUSTERED | 否 | 否 | 否 | `WI_CM_Key` | — | — |
| `dbo.Workstation_Saved_Information` | `WI_SM_Key` | NONCLUSTERED | 否 | 否 | 否 | `WI_SM_Key` | — | — |
| `dbo.Workstation_Saved_Information` | `WI_STPO_Key` | NONCLUSTERED | 否 | 否 | 否 | `WI_STPO_Key` | — | — |
| `dbo.Workstation_Saved_Information` | `aaaaaWorkstation_Saved_Information_PK` | NONCLUSTERED | 是 | 是 | 否 | `WI_Workstation` | — | — |

### `IHS_HISTORY`

| 对象 | 索引 | 类型 | 主键 | 唯一 | 停用 | 键列 | 包含列 | 过滤条件 |
|---|---|---|---|---|---|---|---|---|
| `dbo.Hanger_Master_History` | `HMH_Key` | NONCLUSTERED | 否 | 否 | 否 | `HMH_Key` | — | — |
| `dbo.Hanger_Master_History` | `HM_CM_Key` | NONCLUSTERED | 否 | 否 | 否 | `HMH_CM_Key` | — | — |
| `dbo.Hanger_Master_History` | `HM_ID` | NONCLUSTERED | 否 | 否 | 否 | `HMH_HM_ID` | — | — |
| `dbo.Hanger_Master_History` | `HM_SM_Key` | NONCLUSTERED | 否 | 否 | 否 | `HMH_SM_Key` | — | — |
| `dbo.Hanger_Master_History` | `HM_SRM_Key` | NONCLUSTERED | 否 | 否 | 否 | `HMH_SRM_Key` | — | — |
| `dbo.Hanger_Master_History` | `HM_STPO_Key` | NONCLUSTERED | 否 | 否 | 否 | `HMH_STPO_Key` | — | — |
| `dbo.Hanger_Master_History` | `aaaaaHanger_Master_History_PK` | NONCLUSTERED | 是 | 是 | 否 | `HMH_Key` | — | — |
| `dbo.Hanger_Routing_Details_History` | `HRDH_HMH_Key` | NONCLUSTERED | 否 | 否 | 否 | `HRDH_HMH_Key` | — | — |
| `dbo.Hanger_Routing_Details_History` | `HRD_Completed_Time` | NONCLUSTERED | 否 | 否 | 否 | `HRDH_Completed_Time` | — | — |
| `dbo.Hanger_Routing_Details_History` | `HRD_EM_Key` | NONCLUSTERED | 否 | 否 | 否 | `HRDH_EM_Key` | — | — |
| `dbo.Hanger_Routing_Details_History` | `HRD_Key` | NONCLUSTERED | 否 | 否 | 否 | `HRDH_Key` | — | — |
| `dbo.Hanger_Routing_Details_History` | `HRD_OC_Key` | NONCLUSTERED | 否 | 否 | 否 | `HRDH_OC_Key` | — | — |
| `dbo.Hanger_Routing_Details_History` | `aaaaaHanger_Routing_Details_History_PK` | NONCLUSTERED | 是 | 是 | 否 | `HRDH_Key` | — | — |

### `IHS_OPEN`

| 对象 | 索引 | 类型 | 主键 | 唯一 | 停用 | 键列 | 包含列 | 过滤条件 |
|---|---|---|---|---|---|---|---|---|
| `dbo.ICR_Exception_Date` | `PK_ICR_Exception_Date` | CLUSTERED | 是 | 是 | 否 | `ICRED_Key` | — | — |
| `dbo.ODP_Info` | `aaaaaODP_Info_PK` | NONCLUSTERED | 是 | 是 | 否 | `ODPI_Key` | — | — |
| `dbo.Production_History` | `PK_Weekday_ST_Histroy` | CLUSTERED | 是 | 是 | 否 | `WTH_key` | — | — |

### `IHS_SHARED`

| 对象 | 索引 | 类型 | 主键 | 唯一 | 停用 | 键列 | 包含列 | 过滤条件 |
|---|---|---|---|---|---|---|---|---|
| `dbo.Bridging_Hanger_Master` | `HM_CM_Key` | NONCLUSTERED | 否 | 否 | 否 | `HM_CM_Key` | — | — |
| `dbo.Bridging_Hanger_Master` | `HM_ID` | NONCLUSTERED | 否 | 否 | 否 | `HM_ID` | — | — |
| `dbo.Bridging_Hanger_Master` | `HM_SM_Key` | NONCLUSTERED | 否 | 否 | 否 | `HM_SM_Key` | — | — |
| `dbo.Bridging_Hanger_Master` | `HM_SRM_Key` | NONCLUSTERED | 否 | 否 | 否 | `HM_SRM_Key` | — | — |
| `dbo.Bridging_Hanger_Master` | `HM_STPO_Key` | NONCLUSTERED | 否 | 否 | 否 | `HM_STPO_Key` | — | — |
| `dbo.Bridging_Hanger_Master` | `aaaaaBridging_Hanger_Master_PK` | NONCLUSTERED | 是 | 是 | 否 | `HM_ID` | — | — |
| `dbo.Bridging_Hanger_Routing_Details` | `Bridging_Hanger_MasterBridging_Hanger_Routing_Details` | NONCLUSTERED | 否 | 否 | 否 | `HRD_HM_ID` | — | — |
| `dbo.Bridging_Hanger_Routing_Details` | `HRD_EM_Key` | NONCLUSTERED | 否 | 否 | 否 | `HRD_EM_Key` | — | — |
| `dbo.Bridging_Hanger_Routing_Details` | `HRD_HM_ID` | NONCLUSTERED | 否 | 否 | 否 | `HRD_HM_ID` | — | — |
| `dbo.Bridging_Hanger_Routing_Details` | `HRD_Key` | NONCLUSTERED | 否 | 否 | 否 | `HRD_Key` | — | — |
| `dbo.Bridging_Hanger_Routing_Details` | `HRD_OC_Key` | NONCLUSTERED | 否 | 否 | 否 | `HRD_OC_Key` | — | — |
| `dbo.Bridging_Hanger_Routing_Details` | `aaaaaBridging_Hanger_Routing_Details_PK` | NONCLUSTERED | 是 | 是 | 否 | `HRD_Key` | — | — |
| `dbo.Classified_Scheme` | `PK_Classified_Scheme` | CLUSTERED | 是 | 是 | 否 | `CS_Key`, `CS_CM_Key`, `CS_SM_Key`, `CS_Lot_Number` | — | — |
| `dbo.Colour_Master` | `CM_Key1` | NONCLUSTERED | 否 | 否 | 否 | `CM_Key` | — | — |
| `dbo.Colour_Master` | `aaaaaColour_Master_PK` | NONCLUSTERED | 是 | 是 | 否 | `CM_Key` | — | — |
| `dbo.Database_Shared_Tables_Last_Modified` | `PK__Database_Shared___1BC821DD` | CLUSTERED | 是 | 是 | 否 | `Row_Index` | — | — |
| `dbo.Employee_Master` | `aaaaaEmployee_Master_PK` | NONCLUSTERED | 是 | 是 | 否 | `EM_Key` | — | — |
| `dbo.Employee_Target_Quantity` | `PK_Employee_Target_Quantity` | CLUSTERED | 是 | 是 | 否 | `ETQ_EM_Key`, `ETQ_ODP_Date` | — | — |
| `dbo.Hanger_Registry` | `PK_Hanger_Registry` | CLUSTERED | 是 | 是 | 否 | `HR_HM_ID` | — | — |
| `dbo.Line_Bridging_Information` | `aaaaaLine_Bridging_Information_PK` | NONCLUSTERED | 是 | 是 | 否 | `From_Line`, `From_Station` | — | — |
| `dbo.Line_Sorting_Stations` | `PK_Line_Sorting_Stations` | CLUSTERED | 是 | 是 | 否 | `Line_Number`, `Sorting_Station` | — | — |
| `dbo.Lines_On_Network` | `aaaaaLines_On_Network_PK` | NONCLUSTERED | 是 | 是 | 否 | `Line_Number` | — | — |
| `dbo.Machine_Codes` | `Machine Code` | NONCLUSTERED | 否 | 否 | 否 | `MC_Key` | — | — |
| `dbo.Machine_Codes` | `aaaaaMachine_Codes_PK` | NONCLUSTERED | 是 | 是 | 否 | `MC_Key` | — | — |
| `dbo.Operation_Codes` | `OC_Key` | NONCLUSTERED | 否 | 否 | 否 | `OC_Sub_Code` | — | — |
| `dbo.Operation_Codes` | `Pay Category` | NONCLUSTERED | 否 | 否 | 否 | `OC_PA_Key` | — | — |
| `dbo.Operation_Codes` | `Pay_CategoriesOperation_Codes` | NONCLUSTERED | 否 | 否 | 否 | `OC_PA_Key` | — | — |
| `dbo.Operation_Codes` | `Primary Code` | NONCLUSTERED | 否 | 否 | 否 | `OC_PC_Key` | — | — |
| `dbo.Operation_Codes` | `Primary_CodesOperation_Codes` | NONCLUSTERED | 否 | 否 | 否 | `OC_PC_Key` | — | — |
| `dbo.Operation_Codes` | `aaaaaOperation_Codes_PK` | NONCLUSTERED | 是 | 是 | 否 | `OC_Key` | — | — |
| `dbo.Pay_Categories` | `aaaaaPay_Categories_PK` | NONCLUSTERED | 是 | 是 | 否 | `PA_Key` | — | — |
| `dbo.Primary_Codes` | `Machine Code` | NONCLUSTERED | 否 | 否 | 否 | `PC_MC_Key` | — | — |
| `dbo.Primary_Codes` | `Machine_CodesPrimary_Codes` | NONCLUSTERED | 否 | 否 | 否 | `PC_MC_Key` | — | — |
| `dbo.Primary_Codes` | `aaaaaPrimary_Codes_PK` | NONCLUSTERED | 是 | 是 | 否 | `PC_Key` | — | — |
| `dbo.QC_Header_Codes` | `aaaaaQC_Header_Codes_PK` | NONCLUSTERED | 是 | 是 | 否 | `QCHC_Key` | — | — |
| `dbo.QC_Incidents` | `QCI_Employee_Id` | NONCLUSTERED | 否 | 否 | 否 | `QCI_EM_Key` | — | — |
| `dbo.QC_Incidents` | `QCI_Key` | NONCLUSTERED | 否 | 否 | 否 | `QCI_Key` | — | — |
| `dbo.QC_Incidents` | `QCI_OC_Key` | NONCLUSTERED | 否 | 否 | 否 | `QCI_OC_Key` | — | — |
| `dbo.QC_Incidents` | `QCI_QCSC_Key` | NONCLUSTERED | 否 | 否 | 否 | `QCI_QCSC_Key` | — | — |
| `dbo.QC_Incidents` | `QC_Sub_CodesQC_Incidents` | NONCLUSTERED | 否 | 否 | 否 | `QCI_QCSC_Key` | — | — |
| `dbo.QC_Incidents` | `aaaaaQC_Incidents_PK` | NONCLUSTERED | 是 | 是 | 否 | `QCI_Key` | — | — |
| `dbo.QC_ODP` | `PK_QC_ODP` | CLUSTERED | 是 | 是 | 否 | `QCO_Key` | — | — |
| `dbo.QC_Redo` | `PK_QC_Redo` | CLUSTERED | 是 | 是 | 否 | `QR_Key` | — | — |
| `dbo.QC_Rework` | `PK_QCR_Key` | CLUSTERED | 是 | 是 | 否 | `QCR_Key` | — | — |
| `dbo.QC_Scrap` | `PK_QCS_Key` | CLUSTERED | 是 | 是 | 否 | `QCS_Key` | — | — |
| `dbo.QC_Station_To_Station_Routing` | `PK_QC_Station_To_Station_Routing` | CLUSTERED | 是 | 是 | 否 | `S2S_HM_ID` | — | — |
| `dbo.QC_Sub_Codes` | `QC_Header_CodesQC_Sub_Codes` | NONCLUSTERED | 否 | 否 | 否 | `QCSC_QCHC_Key` | — | — |
| `dbo.QC_Sub_Codes` | `QC_Sub_Code` | NONCLUSTERED | 否 | 否 | 否 | `QCSC_Key` | — | — |
| `dbo.QC_Sub_Codes` | `aaaaaQC_Sub_Codes_PK` | NONCLUSTERED | 是 | 是 | 否 | `QCSC_Key` | — | — |
| `dbo.Sequential_Production` | `PK_Sequential_Production` | CLUSTERED | 是 | 是 | 否 | `SP_Key` | — | — |
| `dbo.Size_Master` | `SM_Key1` | NONCLUSTERED | 否 | 否 | 否 | `SM_Key` | — | — |
| `dbo.Size_Master` | `aaaaaSize_Master_PK` | NONCLUSTERED | 是 | 是 | 否 | `SM_Key` | — | — |
| `dbo.Sorting_Information_By_Line` | `PK_Sorting_Information_By_Line` | CLUSTERED | 是 | 是 | 否 | `SIBL_SLSI_Key`, `SIBL_Line` | — | — |
| `dbo.Style_Colours_Not_Used` | `PK_Style_Colours_Not_Used` | CLUSTERED | 是 | 是 | 否 | `SCNU_ST_Key`, `SCNU_CM_Key` | — | — |
| `dbo.Style_Loading_Sorting_Information` | `PK_Style_Loading_Sorting_Information` | CLUSTERED | 是 | 是 | 否 | `SLSI_Key` | — | — |
| `dbo.Style_Master` | `ST_Key` | NONCLUSTERED | 否 | 否 | 否 | `ST_Key` | — | — |
| `dbo.Style_Master` | `Style ID` | NONCLUSTERED | 否 | 是 | 否 | `ST_ID`, `ST_Line` | — | — |
| `dbo.Style_Master` | `aaaaaStyle_Master_PK` | NONCLUSTERED | 是 | 是 | 否 | `ST_Key` | — | — |
| `dbo.Style_Operations` | `Operation_CodesStyle_Operations` | NONCLUSTERED | 否 | 否 | 否 | `STOP_OC_Key` | — | — |
| `dbo.Style_Operations` | `STOP_Key` | NONCLUSTERED | 否 | 否 | 否 | `STOP_Key` | — | — |
| `dbo.Style_Operations` | `STOP_OC_Key` | NONCLUSTERED | 否 | 否 | 否 | `STOP_OC_Key` | — | — |
| `dbo.Style_Operations` | `aaaaaStyle_Operations_PK` | NONCLUSTERED | 是 | 是 | 否 | `STOP_Key` | — | — |
| `dbo.Style_Operations_Master` | `PK_Style_Operations_Master` | CLUSTERED | 是 | 是 | 否 | `STOPM_Key` | — | — |
| `dbo.Style_Planned_Orders` | `STPO_ST_Key` | NONCLUSTERED | 否 | 否 | 否 | `STPO_ST_Key` | — | — |
| `dbo.Style_Planned_Orders` | `Style_MasterStyle_Purchase_Orders` | NONCLUSTERED | 否 | 否 | 否 | `STPO_ST_Key` | — | — |
| `dbo.Style_Planned_Orders` | `aaaaaStyle_Purchase_Orders_PK` | NONCLUSTERED | 是 | 是 | 否 | `STPO_Key` | — | — |
| `dbo.Style_Planned_Orders_Quantity_Loaded` | `STPO_ST_Key` | NONCLUSTERED | 否 | 否 | 否 | `STPO_Quantity_Loaded` | — | — |
| `dbo.Style_Planned_Orders_Quantity_Loaded` | `Style_Purchase_OrdersStyle_Purchase_Orders_Quantity_Loaded` | NONCLUSTERED | 否 | 否 | 否 | `STPO_Key` | — | — |
| `dbo.Style_Planned_Orders_Quantity_Loaded` | `aaaaaStyle_Purchase_Orders_Quantity_Loaded_PK` | NONCLUSTERED | 是 | 是 | 否 | `STPO_Key`, `STPO_Line` | — | — |
| `dbo.Style_Planned_Orders_Quantity_Registered` | `PK_Style_Purchase_Orders_Quantity_Registered` | CLUSTERED | 是 | 是 | 否 | `STPR_Key`, `STPR_Line` | — | — |
| `dbo.Style_Route_Detail` | `RSD_OC_Key` | NONCLUSTERED | 否 | 否 | 否 | `SRD_OC_Key` | — | — |
| `dbo.Style_Route_Detail` | `SRD_Key` | NONCLUSTERED | 否 | 否 | 否 | `SRD_Key` | — | — |
| `dbo.Style_Route_Detail` | `SRD_SRM_Key` | NONCLUSTERED | 否 | 否 | 否 | `SRD_SRM_Key` | — | — |
| `dbo.Style_Route_Detail` | `Style_Route_MasterStyle_Route_Detail` | NONCLUSTERED | 否 | 否 | 否 | `SRD_SRM_Key` | — | — |
| `dbo.Style_Route_Detail` | `aaaaaStyle_Route_Detail_PK` | NONCLUSTERED | 是 | 是 | 否 | `SRD_Key` | — | — |
| `dbo.Style_Route_Master` | `SRM_Key1` | NONCLUSTERED | 否 | 否 | 否 | `SRM_Key` | — | — |
| `dbo.Style_Route_Master` | `aaaaaStyle_Route_Master_PK` | NONCLUSTERED | 是 | 是 | 否 | `SRM_Key` | — | — |
| `dbo.Style_Route_Skipped` | `PK_Style_Route_Skipped` | CLUSTERED | 是 | 是 | 否 | `SRS_SRM_Key`, `SRS_OC_Key ` | — | — |
| `dbo.Style_Route_Specific_Operation_Overload` | `PK_Style_Route_Specific_Operation_Overload` | CLUSTERED | 是 | 是 | 否 | `SRO_SRM_Key`, `SRO_OC_Key`, `SRO_OverLoad_Station` | — | — |
| `dbo.Style_Sizes_Not_Used` | `PK_Style_Sizes_Not_Used` | CLUSTERED | 是 | 是 | 否 | `SSNU_ST_Key`, `SSNU_SM_Key` | — | — |
| `dbo.User_Id` | `UI_UserId` | NONCLUSTERED | 否 | 否 | 否 | `UI_UserId` | — | — |
| `dbo.User_Id` | `aaaaaUser_Id_PK` | NONCLUSTERED | 是 | 是 | 否 | `UI_UserId` | — | — |
| `dbo.Work_Codes` | `Pay_CategoriesWork_Codes` | NONCLUSTERED | 否 | 否 | 否 | `WC_PA_Key` | — | — |
| `dbo.Work_Codes` | `Work CodesPay Category` | NONCLUSTERED | 否 | 否 | 否 | `WC_PA_Key` | — | — |
| `dbo.Work_Codes` | `aaaaaWork_Codes_PK` | NONCLUSTERED | 是 | 是 | 否 | `WC_Key` | — | — |

## 6. 外键与检查约束

### 6.1 外键

#### `IHS`

| 外键 | 来源列 | 目标列 | 删除动作 | 更新动作 |
|---|---|---|---|---|
| `Clock_InOut_History_FK00` | `Clock_InOut_History.CIOH_ODP_Key` | `ODP_Master.ODP_Key` | CASCADE | CASCADE |
| `Hanger_Routing_Details_FK00` | `Hanger_Routing_Details.HRD_HM_ID` | `Hanger_Master.HM_ID` | CASCADE | CASCADE |
| `ODP_Detail_FK00` | `ODP_Detail.ODPD_ODP_Key` | `ODP_Master.ODP_Key` | CASCADE | CASCADE |
| `ODP_StartStop_History_FK00` | `ODP_StartStop_History.OSSH_ODPD_Key` | `ODP_Detail.ODPD_Key` | CASCADE | CASCADE |

#### `IHS_SHARED`

| 外键 | 来源列 | 目标列 | 删除动作 | 更新动作 |
|---|---|---|---|---|
| `Bridging_Hanger_Routing_D_FK00` | `Bridging_Hanger_Routing_Details.HRD_HM_ID` | `Bridging_Hanger_Master.HM_ID` | CASCADE | NO_ACTION |
| `FK_QCR_STPO_Key` | `QC_Rework.QCR_STPO_Key` | `Style_Planned_Orders.STPO_Key` | SET_NULL | CASCADE |
| `FK_QCS_STPO_Key` | `QC_Scrap.QCS_STPO_Key` | `Style_Planned_Orders.STPO_Key` | SET_NULL | CASCADE |
| `FK_StyleMaster_StyleOperationsMaster` | `Style_Master.ST_STOPM_Key` | `Style_Operations_Master.STOPM_Key` | SET_NULL | CASCADE |
| `FK_StyleOperations_StyleOperationsMaster` | `Style_Operations.STOP_STOPM_Key` | `Style_Operations_Master.STOPM_Key` | SET_NULL | CASCADE |
| `FK_StyleRouteMaster_StyleOperationsMaster` | `Style_Route_Master.SRM_STOPM_Key` | `Style_Operations_Master.STOPM_Key` | SET_NULL | CASCADE |
| `Operation_Codes_FK00` | `Operation_Codes.OC_PA_Key` | `Pay_Categories.PA_Key` | NO_ACTION | CASCADE |
| `Operation_Codes_FK01` | `Operation_Codes.OC_PC_Key` | `Primary_Codes.PC_Key` | NO_ACTION | NO_ACTION |
| `Primary_Codes_FK00` | `Primary_Codes.PC_MC_Key` | `Machine_Codes.MC_Key` | NO_ACTION | NO_ACTION |
| `QC_Incidents_FK00` | `QC_Incidents.QCI_QCSC_Key` | `QC_Sub_Codes.QCSC_Key` | NO_ACTION | NO_ACTION |
| `QC_Sub_Codes_FK00` | `QC_Sub_Codes.QCSC_QCHC_Key` | `QC_Header_Codes.QCHC_Key` | NO_ACTION | NO_ACTION |
| `Sorting_Information_By_Line_FK00` | `Sorting_Information_By_Line.SIBL_SLSI_Key` | `Style_Loading_Sorting_Information.SLSI_Key` | CASCADE | CASCADE |
| `Style_Colours_Not_Used_FK00` | `Style_Colours_Not_Used.SCNU_ST_Key` | `Style_Master.ST_Key` | CASCADE | CASCADE |
| `Style_Colours_Not_Used_FK01` | `Style_Colours_Not_Used.SCNU_CM_Key` | `Colour_Master.CM_Key` | CASCADE | CASCADE |
| `Style_Operations_FK00` | `Style_Operations.STOP_OC_Key` | `Operation_Codes.OC_Key` | NO_ACTION | CASCADE |
| `Style_Purchase_Orders_FK00` | `Style_Planned_Orders.STPO_ST_Key` | `Style_Master.ST_Key` | CASCADE | CASCADE |
| `Style_Purchase_Orders_Qua_FK00` | `Style_Planned_Orders_Quantity_Loaded.STPO_Key` | `Style_Planned_Orders.STPO_Key` | CASCADE | CASCADE |
| `Style_Purchase_Orders_Quantity_Registered_FK01` | `Style_Planned_Orders_Quantity_Registered.STPR_Key` | `Style_Planned_Orders.STPO_Key` | CASCADE | CASCADE |
| `Style_Route_Detail_FK00` | `Style_Route_Detail.SRD_SRM_Key` | `Style_Route_Master.SRM_Key` | CASCADE | CASCADE |
| `Style_Sizes_Not_Used_FK00` | `Style_Sizes_Not_Used.SSNU_ST_Key` | `Style_Master.ST_Key` | CASCADE | CASCADE |
| `Style_Sizes_Not_Used_FK01` | `Style_Sizes_Not_Used.SSNU_SM_Key` | `Size_Master.SM_Key` | CASCADE | CASCADE |
| `Work_Codes_FK00` | `Work_Codes.WC_PA_Key` | `Pay_Categories.PA_Key` | NO_ACTION | CASCADE |

### 6.2 检查约束

| 数据库 | 表 | 约束 | 定义 | 禁用 | 未信任 |
|---|---|---|---|---|---|
| `IHS_SHARED` | `dbo.Employee_Master` | `CK Employee_Master EM_Key` | `([EM_Key]>=(0) AND [EM_Key]<=(999999))` | 否 | 否 |
| `IHS_SHARED` | `dbo.Operation_Codes` | `CK Operation_Codes OC_Key` | `([OC_Key]>=(0) AND [OC_Key]<=(99999999))` | 否 | 否 |
| `IHS_SHARED` | `dbo.Operation_Codes` | `CK Operation_Codes OC_Sub_Code` | `([OC_Sub_Code]>=(0) AND [OC_Sub_Code]<=(9999))` | 否 | 否 |
| `IHS_SHARED` | `dbo.Primary_Codes` | `CK Primary_Codes PC_Key` | `([PC_Key]>=(0) AND [PC_Key]<=(9999))` | 否 | 否 |
| `IHS_SHARED` | `dbo.Work_Codes` | `CK Work_Codes WC_Key` | `([WC_Key]<=(99) AND [WC_Key]>=(0))` | 否 | 否 |

## 7. 视图与程序对象依赖

### 7.1 视图跨库依赖

| 数据库 | 视图 | 依赖数据库.架构.对象 |
|---|---|---|
| `IHS_OPEN` | `V_ODP_Info` | `IHS.dbo.ODP_Detail` |
| `IHS_OPEN` | `V_ODP_Info` | `IHS.dbo.ODP_Master` |
| `IHS_OPEN` | `V_ODP_Info` | `IHS_SHARED.dbo.Colour_Master` |
| `IHS_OPEN` | `V_ODP_Info` | `IHS_SHARED.dbo.Employee_Master` |
| `IHS_OPEN` | `V_ODP_Info` | `IHS_SHARED.dbo.Machine_Codes` |
| `IHS_OPEN` | `V_ODP_Info` | `IHS_SHARED.dbo.Operation_Codes` |
| `IHS_OPEN` | `V_ODP_Info` | `IHS_SHARED.dbo.Pay_Categories` |
| `IHS_OPEN` | `V_ODP_Info` | `IHS_SHARED.dbo.Primary_Codes` |
| `IHS_OPEN` | `V_ODP_Info` | `IHS_SHARED.dbo.Size_Master` |
| `IHS_OPEN` | `V_ODP_Info` | `IHS_SHARED.dbo.Style_Master` |
| `IHS_OPEN` | `V_ODP_Info` | `IHS_SHARED.dbo.Style_Operations` |
| `IHS_OPEN` | `V_ODP_Info` | `IHS_SHARED.dbo.Style_Operations_Master` |
| `IHS_OPEN` | `V_ODP_Info` | `IHS_SHARED.dbo.Style_Planned_Orders` |
| `IHS_OPEN` | `V_ODP_Info` | `IHS_SHARED.dbo.Work_Codes` |
| `IHS_OPEN` | `V_QC_Rework_Info` | `IHS_SHARED.dbo.Colour_Master` |
| `IHS_OPEN` | `V_QC_Rework_Info` | `IHS_SHARED.dbo.Employee_Master` |
| `IHS_OPEN` | `V_QC_Rework_Info` | `IHS_SHARED.dbo.Operation_Codes` |
| `IHS_OPEN` | `V_QC_Rework_Info` | `IHS_SHARED.dbo.Primary_Codes` |
| `IHS_OPEN` | `V_QC_Rework_Info` | `IHS_SHARED.dbo.QC_Rework` |
| `IHS_OPEN` | `V_QC_Rework_Info` | `IHS_SHARED.dbo.QC_Sub_Codes` |
| `IHS_OPEN` | `V_QC_Rework_Info` | `IHS_SHARED.dbo.Size_Master` |
| `IHS_OPEN` | `V_QC_Rework_Info` | `IHS_SHARED.dbo.Style_Master` |
| `IHS_OPEN` | `V_QC_Rework_Info` | `IHS_SHARED.dbo.Style_Operations` |
| `IHS_OPEN` | `V_QC_Rework_Info` | `IHS_SHARED.dbo.Style_Operations_Master` |

### 7.2 存储过程和直接目录依赖

| 数据库 | 存储过程 | 直接引用对象 |
|---|---|---|
| `IHS` | `dbo.Clock_In_Out_History_Get_Time_Logged_Out` | `Clock_InOut_History` |
| `IHS` | `dbo.Hanger_Master_Delete` | `Hanger_Master` |
| `IHS` | `dbo.Hanger_Master_Insert` | `Hanger_Master` |
| `IHS` | `dbo.Hanger_Master_Update` | `Hanger_Master` |
| `IHS` | `dbo.Hanger_Routing_Details_Delete` | `Hanger_Routing_Details` |
| `IHS` | `dbo.Hanger_Routing_Details_Insert` | `Hanger_Routing_Details` |
| `IHS` | `dbo.Hanger_Routing_Details_Update` | `Hanger_Routing_Details` |
| `IHS` | `dbo.ODP_Detail_Delete` | `ODP_Detail` |
| `IHS` | `dbo.ODP_Detail_Insert` | `ODP_Detail` |
| `IHS` | `dbo.ODP_Detail_Update` | `ODP_Detail` |
| `IHS` | `dbo.ODP_Lost_Get_Time_On_Delay` | `ODP_Detail`, `ODP_StartStop_History` |
| `IHS` | `dbo.ODP_Master_Delete` | `ODP_Master` |
| `IHS` | `dbo.ODP_Master_Insert` | `ODP_Master` |
| `IHS` | `dbo.ODP_Master_Update` | `ODP_Master` |
| `IHS` | `dbo.Style_Loading_Get_Allowed_Out` | `Style_Loading` |
| `IHS_OPEN` | `dbo.ODP_Info_Delete` | `ODP_Info` |
| `IHS_OPEN` | `dbo.ODP_Info_Insert` | `ODP_Info` |
| `IHS_OPEN` | `dbo.ODP_Info_Update` | `ODP_Info` |
| `IHS_OPEN` | `dbo.Production_Info_Detail_Insert` | `Production_Info_Detail` |
| `IHS_OPEN` | `dbo.Production_Info_Detail_Update_Completed_Time` | `Production_Info_Detail` |
| `IHS_OPEN` | `dbo.Production_Info_Insert` | `Production_Info` |
| `IHS_OPEN` | `dbo.Production_Info_Update_Completed_Time` | `Production_Info` |
| `IHS_SHARED` | `dbo.QC_Redo_GetNextUnhandledStation` | `QC_Redo`, `Style_Master`, `Style_Operations`, `Style_Operations_Master` |
| `IHS_SHARED` | `dbo.Sorting_Information_Update_Quantity` | `Sorting_Information_By_Line` |
| `IHS_SHARED` | `dbo.Style_Route_Master_Get_Lock` | `Style_Route_Master` |

## 8. 未决事项与安全边界

- 本次只读 SQL Server 元数据，包括视图定义、字段、索引、外键、检查约束、程序对象名称和依赖；没有 SELECT 吊架、报工、员工或质量业务行，也没有执行客户端、同步按钮、测试连接、DDL 或写操作。
- 结构上可从吊架主表取得当前 PO/线计划键，也可从吊架路线事件读取员工、工序、工位、产线及完成时间；但尾查合格状态的具体事件码/字段未确认，需通过授权的流程观察和脱敏测试确认。
- `IHS_HISTORY` 与 `IHS_OPEN` 不在 IDS 当前常规 `T_TableInfo` 源表映射中；本地客户端静态代码已确认历史衣架数据和 OPEN 类数据有专用上传路径，但实际部署配置是否启用、运行时会选择哪些表仍需核实。
- 数据库设置里的“查询语句”字段会被界面回填并保存；在当前本地编译副本中，自动上传路径可见按表名构造的 SQL 模板，未发现同步业务方法读取 `T_TableInfo.SelectSql` 属性。不能据此断言所有版本或所有线程都忽略该字段，需在隔离测试配置中验证其实际作用后再决定是否将其作为有效映射契约。
- 三号线 `IHS_OPEN` 与中继 IDS 的两个同名视图字段数量及别名不同。应核对同步工具如何将 `ODPI_STPO_Number`/`QCRI_STPO_Key` 转为中继字段，尤其不能把整数 PO 键当作 PO 号。
- `HM_ID` 是可复用物理吊架标识；历史视图链路建议使用 `HMH_Key` 与 `HRDH_HMH_Key`。真实归档完整性、数据保留期限和标签工具的打印触发时序仍待业务验证。
- SQL 注释仅覆盖 61/876 个字段。字段结构已全量登记，但业务含义尚未全量确认；后续按 ERP/标签工具实际需要逐字段补充定义。
- 本文不保存数据库 IP、账号、密码、员工记录、吊架记录或报工明细；标签工具设计应使用只读连接和最小字段投影。

## 9. 同步客户端本地副本静态分析

### 9.1 分析材料与边界

用户提供的同步客户端副本包含 Windows .NET 编译程序、若干程序集、配置文件和日志目录，没有 C# 源码工程。本次仅静态读取编译程序集中的类型/方法/字符串常量及配置键名称；未运行客户端、未读日志内容、未调用任何连接或同步功能，也未记录配置值、连接信息或密码。因此以下结论仅适用于这份本地副本，不能无条件推广到生产机器上可能不同的程序版本。

### 9.2 常规表同步路径

- 普通同步线程读取数据库配置记录中的 `DbId`、线名、线路号、数据源、库名、用户、启用状态和组号，再按 `DbId` 找到 `T_TableInfo` 的表配置，并将查询结果交给服务端上传逻辑。
- 编译代码中存在根据表名构造查询的 `GetSqlString`：`Hanger_Routing_Details` 会额外关联 `Hanger_Master` 并带出 `HM_Key`；部分班次/休息时间表会补充线路号、组号和删除标志；其他常见表走通用查询分支。
- `LineSqlConfig.GetLineSelectSql` 中静态包含 54 条变更跟踪查询模板，涉及 52 个不同源表名，使用 SQL Server `CHANGETABLE(CHANGES ...)` 并按主键回连源表；模板包含款式/工序/PO、吊架、报工、质量、线体配置、历史吊架和生产历史等类别。部分同名表存在多种查询变体，因此模板条数多于不同表名数。
- 表设置界面的 `SelectSql` 字段会被保存到表配置模型；但在本次分析覆盖的程序集里，`get_SelectSql` 仅被表编辑窗口用于回填输入框，未找到同步业务方法读取该属性。常规路径可见按表名选择 SQL 模板。该证据尚不足以判定所有线程、版本或外部组件都不使用该字段，需用隔离测试库验证。

### 9.3 独立历史衣架上传路径

客户端包含单独的历史衣架数据上传方法，先读取该线路的 `IHS` 配置，再将源库名切换到 `IHS_HISTORY`，按界面选择的日期区间执行查询：

- `Hanger_Master_History` 按 `HMH_Completed_Time` 过滤，并关联 `IHS_SHARED` 中的款式、颜色和尺码资料，补充相应共享库键值。
- `Hanger_Routing_Details_History` 按 `HRDH_Completed_Time` 过滤，并关联历史衣架主表补出历史吊架号，再关联共享工序资料取得共享工序键。
- 两个查询结果进入单独的服务端上传调用。因此这两张历史表虽不在常规 `T_TableInfo` 源表列表，仍有代码路径可将其历史记录上传；实际是否使用以及日期区间由现场操作决定。

这进一步支持历史路线记录通过历史主表键关联，而不是只依赖可复用的物理吊架号。静态代码不能证明每条历史主/明细都完整配对，也没有验证上传后的 IDS 记录。

### 9.4 其他专用同步入口

- 客户端另有按日期区间触发的产量/生产数据手动同步方法，组合读取线路的 `IHS` 与 `IHS_SHARED` 配置后交给专用 `SyncODPData` 处理。该入口与普通表映射上传分开；详细源字段变换尚需继续从该方法及中继表结构逐项核对。
- 还有一个 OPEN 类上传线程，会根据线路数据库配置和专用查询模板处理表数据，并维护中继侧同步版本。其具体启用库、`Production_History` 及其他配置表间的对应关系，需以当前运行配置核实。
- “复表”对话框对应的编译方法读取源 `DbId` 的 `T_TableInfo` 行，再为目标 `DbId` 新建表配置记录，复制表名、服务端表名、查询文本和同步版本等配置字段。静态方法中没有发现复制源业务表记录的调用；故截图按钮文字“复制表数据”在此实现中更像复制表映射配置，实际 UI 用词/完整行为仍需隔离环境验证。

### 9.5 追加核对记录

- 2026-11-25：在首次四库元数据盘点后，追加检查同步客户端本地编译副本。确认客户端包含普通表上传、专用历史衣架上传、产量手动同步和 OPEN 类同步路径；提取了按表名构造的变更跟踪 SQL 模板；没有运行客户端、读取日志或访问生产业务行。`SelectSql` 的运行时作用、专用入口与当前现场配置的对应关系仍列为待验证事项。

## 10. 核心回传视图的 SQL 组成与中继差异

本节依据 `IHS_OPEN.dbo.V_ODP_Info`、`IHS_OPEN.dbo.V_QC_Rework_Info` 的当前视图定义，以及 IDS 中继库同名视图的已核对字段/连接信息整理。这里只分析 SQL 结构，不查询业务行；“可能重复”表示连接条件与唯一约束无法排除此情形，不代表已在生产数据中观察到重复。

### 10.1 `IHS_OPEN.dbo.V_ODP_Info`：员工报工明细

- 视图共 47 列，`ODPI_Key` 来自 `IHS.dbo.ODP_Detail.ODPD_Key`，因此 SQL 粒度是一条 ODP 明细，而不是按员工/日期/工序汇总后的统计行。SQL 没有 `GROUP BY`、聚合或 `WHERE`；不因款式、路线或计划字典缺失而主动过滤掉 ODP 明细，相关描述列可能为空。
- 主从关系为 `ODP_Master.ODP_Key = ODP_Detail.ODPD_ODP_Key`。款式计划通过 `ODPD_STPO_Key = Style_Planned_Orders.STPO_Key` 左连接，视图中的 `ODPI_STPO_Number` 来自计划表的字符串 `STPO_ID`；款式和颜色尺码则来自明细保存的内部 Key，再关联 `IHS_SHARED` 字典。
- 员工姓名、RFID、部门、城市和 SSN 都由 `ODP_Master.ODP_EM_Key` 关联共享员工表得出；员工不是从每条 ODP 明细的独立员工键读取。因此员工字段表达“所属报工主单的员工”。若一个主单可以包含多人各自的明细，仅凭当前视图会把主单员工附到其全部明细上；是否允许这种录入方式应向现场确认。
- 工序主数据通过款式路线主表/工序表关联，最终用 `ODPD_ST_Key = ST_Key`、`ODPD_OC_Key = STOP_OC_Key` 配对，未使用单独的款式工序节点 Key 或 `STOP_Order` 限定匹配。`Style_Operations` 只看到单列主键 `STOP_Key`，没有 `(STOPM_Key, STOP_OC_Key)` 唯一约束；若同款路线允许同一工序编码出现多次，一条 ODP 明细可能展开成多行。此风险由定义和约束结构推得，未读取路线数据验证。
- `ODPI_Quantity`、`ODPI_Start_Time`、`ODPI_Actual_Time`、`ODPI_Piece_Rate` 来自 ODP 明细；其中实际用时单位尚未确认，计件单价是写入 ODP 明细的单价字段。`ODPI_Date`、员工及上下班时间、班次来自 ODP 主单。薪资类别金额来自共享薪资类别表，工序补贴字段来自工序基础资料；因此视图同时混有报工明细字段和当前共享字典/工序字段，不能将所有工资相关字段都视为同一时间点的历史快照。
- 该视图定义没有软删除过滤条件。与之相比，当前 IDS 中继库 `V_ODP_Info` 明确过滤 `P_ODP_Detail.DelFlag = 0`，而且输出 42 列，不是 IHS_OPEN 47 列的逐列原样复制。手动 ODP 上传线程如何处理删除、字段映射及重复上传，仍需继续从同步方法与目标写入契约确认。

### 10.2 `IHS_OPEN.dbo.V_QC_Rework_Info`：质检缺陷与修复明细

- 视图共 47 列，以 `IHS_SHARED.dbo.QC_Rework` 为质量事件主记录，SQL 无 `WHERE`、无 `GROUP BY`。主键/事件键 `QCRI_Key` 来自 `QCR_Key`；缺陷人员/时间/数量、缺陷工序、款色码和原因字段直接来自 QCR 及对应的共享字典。
- 视图将三个员工角色分别命名为缺陷人员、送返修人员和修复人员：`QCR_Defect_EM_Key`、`QCR_Sent_To_Rework_By_EM_Key`、`QCR_Repair_EM_Key`。`QCRI_QC_EM_*` 一组实际关联的是 `QCR_Sent_To_Rework_By_EM_Key`，其是否必然是执行质检的人，视图名称本身不能证明。
- 计划只输出 `QCR_STPO_Key` 的内部 Key 别名 `QCRI_STPO_Key`，没有关联 `Style_Planned_Orders` 取出人可读的 PO/线计划号；它与 IDS 中继视图的 `QCRI_STPO_ID` 字符串并非同一字段契约。同步转换路径必须显式查清，不能把内部 Key 当 PO 文本。
- `QC_Rework` 底表有 `QCR_HM_ID`、`QCR_HM_Key` 字段，但该视图 SELECT 未输出它们；因此仅依赖这个视图不能将缺陷/修复事件可靠绑定到某一件衣服或一只衣架。它也不提供“检查合格”事件，记录缺失不能当作通过。
- 视图通过“款式 Key + 缺陷工序 Key”关联款式路线，再取 `STOP_Number`。同一款式若同一工序编码能出现多个路线节点，则可能让一个 QCR 事件匹配多行；当前 `Style_Operations` 的索引/约束未建立该字段组唯一性。实际数据是否存在这类重复路线仍需脱敏聚合核验。
- 视图还投影员工 RFID、SSN、部门和城市等个人资料。ERP 获取员工生产/质量记录时应按业务最小字段读取，不要因为视图提供了字段就整行长期复制。

### 10.3 三个 IDS 视图在 ERP 中的职责边界

| 视图 | 事实记录 | 人员来源 | 数量含义边界 | 与单件衣服/吊架的关联 |
|---|---|---|---|---|
| `V_ODP_Info` | ODP 工序报工明细 | ODP 主单关联员工 | `ODPI_Quantity` 是报工数量；需确认单位及记账规则 | 无吊架键，不能定位单件衣服 |
| `V_Hanger_Routing_Details` | 吊架经历工位/工序的路线事件 | 路线明细员工 | 含记入产量标志和衣架件数等字段，不应不经核实地与 ODPI 数量相加 | 有吊架 ID/Key，但物理吊架会复用；当前 IDS JOIN 有重复放大风险 |
| `V_QC_Rework_Info` | 缺陷、质检送返修、修复等质量事件 | 缺陷人、送返修人、修复人三个角色 | 缺陷数/修复数应分字段统计；不能当成生产完成量 | IDS 视图未提供底层衣架键，不能与单件直接关联 |

这三个视图之间没有可证明通用的一对一事件连接键。以 PO、款式、颜色、尺码、员工和相近时间做组合匹配只能得到候选关联，不能宣称是单件生产履历。对于 ERP 的产量、员工生产和返修统计，应分别按各自事实粒度入库、去重和对账，避免把 ODPI 报工明细、VHRD 吊架路线事件和 QCRI 质量事件混成同一类数量。
