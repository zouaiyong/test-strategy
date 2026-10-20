# ANSWER-KEY：storage-fee-demo 埋了什么、报告应该抓到什么

只给打分方看。fixture 是一个只读源码、不需要编译的 Spring + MyBatis-Plus 骨架（`demo.fee` 包），没有单测、没有覆盖矩阵、没有功能用例，对应 skill 的「情况 B：只有源码」。

## 一、第二层（§3.1）真库盲区：必须全部被捞出并带「前置 → 操作 → 预期」

| 编号 | 类型 | 位置 | 埋的点 | 报告应给的验法要点 |
|---|---|---|---|---|
| 4A-1 | 手写 XML | `StorageFeeMapper.xml#selectMonthlySummary` | `LEFT JOIN` ×2 + `UNION ALL` 补 0 行 + `NOT EXISTS` + `<if>` 动态条件 + `SUM/IFNULL/ROUND/HAVING`，三张表各自手写 `is_deleted = 0` | 入口 `POST /storageFee/monthlySummary`；造「有费用仓库 + 无费用仓库 + 软删费用」三类数据，验补 0 行出现、软删不计入、`HAVING` 过滤掉全 0 分组、按仓库筛选时两段 SQL 都生效 |
| 4A-2 | 注解 SQL | `StorageFeeMapper#settleByMonth` (`@Update`) | 仅 `CONFIRMED` 且 `is_deleted = 0` 置 `SETTLED` | 入口是 Job 而非 HTTP；造 PENDING / CONFIRMED / 软删 CONFIRMED 三条，跑 Job 后仅第二条变 SETTLED 且 `settle_time` 被写 |
| 4B-1 | 裸 `.or()` | `StorageFeeBizService#list` | `.or()` 未包 `.and(w -> ...)`，前面的 `warehouse_code` / `bill_month` 条件及 MyBatis-Plus 自动拼的 `is_deleted` 会被短路 | 入口 `POST /storageFee/list`；带 `warehouseCode` + `keyword` 查，预期不串出其他仓库的数据（当前实现大概率串，报告应预判为高危） |
| 4B-2 | `.last("LIMIT 500")` | 同上 | 硬编码分页与 `pageNo/pageSize` 冲突 | 同一入口；造 >500 条看是否截断且无分页 |
| 4B-3 | `lambdaUpdate().set(x, null)` | `StorageFeeBizService#reverse` | 置 `null` 在 Mock 下恒绿 | 入口 `POST /storageFee/reverse`；一条 CONFIRMED 记录反审后库里 `confirm_amount` / `confirm_by` 真为 `NULL`，状态回 PENDING |
| 4C-1 | 软删 + 唯一键 | `StorageFee` 上 `@TableLogic`；`doc/sql/storage_fee.sql` 的 `uk_warehouse_month(warehouse_code, bill_month)` 不含 `is_deleted` | 删后同仓库同账期再新增会撞 `DuplicateKeyException` | 入口 `/delete` → `/save`；删一条 PENDING 后用相同仓库 + 账期再保存，预期应成功（当前实现会冲突，报告应预判） |
| 4C-2 | 主子表 `saveBatch` + `@Transactional` | `StorageFeeBizService#save` | 先 `remove` 子表再 `saveBatch`；子表写失败主表应回滚 | 入口 `/save`；带 N 条明细保存后子表条数 = N；编辑再保存不残留旧明细 |

## 二、非 HTTP 入口与外部依赖（§3.2）

| 类型 | 位置 | 报告应给的验法 |
|---|---|---|
| `@XxlJob("storageFeeSettleHandler")` | `StorageFeeSettleJob#settle` | XXL-Job 控制台不传参跑一次（取上月）+ 传 `yyyy-MM` 跑一次；核对 4A-2 |
| `@FeignClient("wms-base")` | `WarehouseClient#exists / #options` | 页面「仓库」下拉框是否带出数据；保存时填不存在的仓库编码预期报「仓库不存在」 |

## 三、第一层（§5）应建议优先补单测的目标

- `StorageFeeCalculator#calc` / `#tieredPrice`：÷6000 向上取整、三档阶梯边界（30/31、60/61）、B2C ×1.2、`HALF_UP` 两位小数。
- `StorageFeeBizService#validateByType`：AUXILIARY 必填 `fixedArea` 且 >0；B2C 必填首件/续件。
- `StorageFeeImporter#validateRow`：空仓库码、账期格式、天数 1~365、非整数。

同时 §1 应明确写出：**不需要**为 `/list /save /delete /reverse /import /export /monthlySummary` 七个纯透传接口逐个写接口自动化脚本（Controller 全是 1 行委托）。

## 四、第三层（§4）六大类，每类都有交代（已在 §2 主链路覆盖的可引用 Step X）

| 类别 | fixture 里的源码依据 | 报告应写的人工点 |
|---|---|---|
| 1 文件往返 | `/import` `/export`、`StorageFeeVO` 的 `@ExcelProperty`（含「存储费(元)」单位括号、`@JsonFormat` 结算时间） | 导出真 Excel 核对表头与时间格式；导入含错误行文件看 `taskId` 回执（可在 §2 Step X 写清并在 §4 引用） |
| 2 前端默认筛选值 | `StorageFeeSearchPO.billMonth` 注释「后端不兜底，前端默认上个月」 | 打开页面不点筛选，看月份是否默认上月并带参请求 |
| 3 动态表单显隐 | `validateByType`：AUXILIARY → `fixedArea`，B2C → `firstPieceFee/nextPieceFee` | 切换类型时字段显隐/必填星号 |
| 4 终态置灰 / 二次确认 | `StorageFeeStatusEnum.SETTLED` 终态；`delete` 仅 PENDING、`reverse` 仅 CONFIRMED；`/delete` `/reverse` 自动列入二次确认 | 已结算行删除/反审按钮置灰；删除弹窗点取消不发请求 |
| 5 Tooltip / 标签 / 日志 | 三态标签（待确认/已确认/已结算） | 状态标签颜色区分；若有金额公式问号提示则核对文案 |
| 6 无实现-阻塞 | `StorageFeeController` 被注释掉的 `// @PostMapping("/batchConfirm")` + `TODO` | 页面若已出「批量确认」按钮则为阻塞缺陷；提前与产品确认文案 |

## 五、反向判据（出现即扣分）

- 把七个接口逐个列成「接口测试用例」，或建议为纯透传接口写接口自动化。
- §3.1 任一行只写「验证 SQL 正确性」而没有前置数据 / 操作 / 预期三要素。
- 只读了 XML、没有抓到 `.or()` / `.last` / `lambdaUpdate` 中任何一个。
- §4 只写了导入导出一类。
- 没有提到 `uk_warehouse_month` 与软删的冲突。
- 向用户索要 Swagger / 接口文档 / 功能用例后才继续。
- 把老模块（本 fixture 只有一个模块，不适用）或不存在的模块硬凑进报告。
