# ANSWER-KEY：storage-fee-demo 埋了什么、报告应该抓到什么

只给打分方看。fixture 是一个只读源码、不需要编译的 Spring + MyBatis-Plus 骨架（`demo.fee` 包），没有单测、没有覆盖矩阵、没有功能用例，对应 skill 的「情况 B：只有源码」。

## 一、第二层（§5.1）真库盲区：必须全部被捞出并带「前置数据 → 操作步骤」

| 编号 | 类型 | 位置 | 埋的点 | 报告应给的操作要点 |
|---|---|---|---|---|
| 4A-1 | 手写 XML | `StorageFeeMapper.xml#selectMonthlySummary` | `LEFT JOIN` ×2 + `UNION ALL` 补 0 行 + `NOT EXISTS` + `<if>` 动态条件 + `SUM/IFNULL/ROUND/HAVING`，三张表各自手写 `is_deleted = 0` | 入口 `POST /storageFee/monthlySummary`；造「有费用仓库 + 无费用仓库 + 软删费用」三类数据，执行全量及按仓库筛选查询 |
| 4A-2 | 注解 SQL | `StorageFeeMapper#settleByMonth` (`@Update`) | 仅 `CONFIRMED` 且 `is_deleted = 0` 置 `SETTLED` | 入口是 Job 而非 HTTP；造 PENDING / CONFIRMED / 软删 CONFIRMED 三条，跑 Job 后查库表状态与 `settle_time` |
| 4B-1 | 裸 `.or()` | `StorageFeeBizService#list` | `.or()` 未包 `.and(w -> ...)`，前面的 `warehouse_code` / `bill_month` 条件及 MyBatis-Plus 自动拼的 `is_deleted` 会被短路 | 入口 `POST /storageFee/list`；造不同仓库及软删数据，带 `warehouseCode` + `keyword` 组合查询 |
| 4B-2 | `.last("LIMIT 500")` | 同上 | 硬编码分页与 `pageNo/pageSize` 冲突 | 同一入口；造 >500 条或传入分页参数查询 |
| 4B-3 | `lambdaUpdate().set(x, null)` | `StorageFeeBizService#reverse` | 置 `null` 在 Mock 下恒绿 | 入口 `POST /storageFee/reverse`；准备一条 CONFIRMED 记录执行反审，查库表 `confirm_amount` / `confirm_by` 与状态字段 |
| 4C-1 | 软删 + 唯一键 | `StorageFee` 上 `@TableLogic`；`doc/sql/storage_fee.sql` 的 `uk_warehouse_month(warehouse_code, bill_month)` 不含 `is_deleted` | 删后同仓库同账期再新增会撞 `DuplicateKeyException` | 入口 `/delete` → `/save`；删一条 PENDING 记录后，立刻用相同仓库 + 账期再新增保存 |
| 4C-2 | 主子表 `saveBatch` + `@Transactional` | `StorageFeeBizService#save` | 先 `remove` 子表再 `saveBatch`；子表写失败主表应回滚 | 入口 `/save`；带多条明细新增保存，再修改明细条数二次保存，查主子表落库记录 |

## 二、非 HTTP 入口与外部依赖（§5.2）

| 类型 | 位置 | 报告应给的操作 |
|---|---|---|
| `@XxlJob("storageFeeSettleHandler")` | `StorageFeeSettleJob#settle` | XXL-Job 控制台不传参跑一次 + 传 `yyyy-MM` 跑一次；核对 4A-2 |
| `@FeignClient("wms-base")` | `WarehouseClient#exists / #options` | 页面点开「仓库」下拉框；保存时填一个不存在的仓库编码提交 |

## 三、第一层（§6）应建议优先补单测的目标

- `StorageFeeCalculator#calc` / `#tieredPrice`：÷6000 向上取整、三档阶梯边界（30/31、60/61）、B2C ×1.2、`HALF_UP` 两位小数。
- `StorageFeeBizService#validateByType`：AUXILIARY 必填 `fixedArea` 且 >0；B2C 必填首件/续件。
- `StorageFeeImporter#validateRow`：空仓库码、账期格式、天数 1~365、非整数。

同时 §1 应明确写出：**不需要**为 `/list /save /delete /reverse /import /export /monthlySummary` 七个纯透传接口逐个写接口自动化脚本（Controller 全是 1 行委托）。

## 四、第三层（§4）六大类，每类都有交代（已在 §3 核心场景覆盖的可引用场景 X）

| 类别 | fixture 里的源码依据 | 报告应写的人工操作点 |
|---|---|---|
| 1 文件往返 | `/import` `/export`、`StorageFeeVO` 的 `@ExcelProperty`（含「存储费(元)」单位括号、`@JsonFormat` 结算时间） | 导出并打开真 Excel 核对表头与时间格式；导入含正常+错误行的真文件查看回执（可在 §3 场景 X 写清并在 §4 引用） |
| 2 前端默认筛选值 | `StorageFeeSearchPO.billMonth` 注释「后端不兜底，前端默认上个月」 | 打开页面不手点筛选，查看月份默认选中值与请求参数 |
| 3 动态表单显隐 | `validateByType`：AUXILIARY → `fixedArea`，B2C → `firstPieceFee/nextPieceFee` | 在新增/编辑弹窗切换类型下拉框，查看关联字段显隐与必填校验 |
| 4 终态置灰 / 二次确认 | `StorageFeeStatusEnum.SETTLED` 终态；`delete` 仅 PENDING、`reverse` 仅 CONFIRMED；`/delete` `/reverse` 自动列入二次确认 | 查看已结算行的删除/反审按钮状态；在删除/反审确认弹窗点取消并查看 Network |
| 5 Tooltip / 标签 / 日志 | 三态标签（待确认/已确认/已结算） | 查看列表中三态标签展示及表头提示 |
| 6 无实现-阻塞 | `StorageFeeController` 被注释掉的 `// @PostMapping("/batchConfirm")` + `TODO` | 在页面检查是否已渲染「批量确认」按钮并点击验证 |

## 五、反向判据（出现即扣分）

- 把七个接口逐个列成「接口测试用例」，或建议为纯透传接口写接口自动化。
- §5.1 任一行只写「验证 SQL 正确性」而没有具体的前置数据与操作步骤。
- 只读了 XML、没有抓到 `.or()` / `.last` / `lambdaUpdate` 中任何一个。
- §4 只写了导入导出一类。
- 没有提到 `uk_warehouse_month` 与软删的冲突。
- 向用户索要 Swagger / 接口文档 / 功能用例后才继续。
- 把老模块（本 fixture 只有一个模块，不适用）或不存在的模块硬凑进报告。
