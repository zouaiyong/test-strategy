# S0～S6 白盒摸底与源码特征识别细则

确定评估范围（【仅评估当前分支改动】或【评估全量代码】）后，按所选模式执行以下 7 步，并按第二节「L3 六大类识别字典」提取或反推人工测试项。

编号约定：`SN` = 本文第 N 步；`§N` = 报告第 N 节（见 report-template.md）；`L1/L2/L3` = 三层测试模型。

- 第一节：S0～S6 操作细则
- 第二节：L3（§6）六大类识别字典
- 第三节：技术栈映射表（非 MyBatis-Plus / 非 Java 项目用）

---

## 一、S0～S6 操作细则

### S0（仅「当前分支改动」模式）：锁定 Diff 清单与回归波及面

1. 找基准主干（依次看 `origin/master`、`origin/main`、`master`、`main`），同时覆盖「已提交分支改动 + 未提交工作区/暂存区 + 未追踪新文件」：
   - 取分叉基点 `git merge-base <base> HEAD`（记为 `<mb>`），再跑 `git diff --stat <mb>` 与 `git diff <mb>`。对比分叉基点而不是直接对比 `<base>`，是为了避免把主干上别人后合入的提交误算成本次改动。
   - 若无法取到 `<mb>`，则 `git diff <base>...HEAD`（已提交增量）+ `git diff HEAD`（未提交修改）。
   - 再跑 `git status -s`，把 `??` 未追踪源码文件纳入范围（排除测试报告、构建产物等非业务源码）。
2. 把改动文件按层归类：`Controller`、`BizService/Service`、`Entity/PO/RO`、`mapper/*.xml / DAO`、`Job/MQ/Client`、`公共工具类/常量/配置`。
3. 反向追查波及面：改了公共工具类、枚举、Entity 字段或共用子方法时，Grep 反向搜出哪些未改动的 `BizService / Controller` 调用了它，一并纳入范围。这是防止被连带改坏的老功能漏测。
4. 完全未改动且不在波及链上的老模块排除出报告，不进必测表也不进主链路。

### S1：查已有用例、覆盖矩阵与单测现状

**情况 A：已有单测与 `*-自动化覆盖矩阵.md`**（`testcase-to-unittest` 产物）

1. 盘点已跑条数、红灯条数与 `*-判据偏差清单.md`。
2. 第一刀——扫「四态清单表」：整条标为 `仅人工`、`无实现-阻塞`、`判据未定-阻塞` 的条目及原因 → L3（§6）。
3. 第二刀——扫「覆盖矩阵明细表」里的半自动化 TC：状态是 `已写` 或 `部分` 但 **`明细行 > 可自动化行`** 的 TC，逐行看「备注」列，按缺口类型分流：
   - 备注含 `集成层`（聚合 SQL 需真库、查无结果需真库）或 `本层验不了 / 链式 mock 吞掉`（`lambdaQuery` 条件被 mock 吞、Job 静态调用未 mock、Controller 反序列化拦截）→ L2（§4）。
   - 备注含 `人工层`（弹窗、Toast、表头列、加粗、不关闭弹框、点击跳转）或 `判据未定 / 【待确认】 / 以实际为准` → L3（§6）。
   
   这一刀容易漏：矩阵状态看起来是绿的，缺口藏在备注里。

**情况 B：还没写单测（甚至没有功能用例，只有源码）**

- 没单测不等于 L1 为空。扫范围内的 `BizService / Service / Calculator / ExcelHandler / Utils`，挑出**适合写单测的高价值目标**（算钱算量公式、精度进位、阶梯匹配、前置状态机拦截、导入逐行校验、复杂去重规则），列进 §3「建议优先补单测的核心类与方法」，并提示可交由 `testcase-to-unittest` 落地。
- 同时按第二节的「仅源码识别法」反推 L3 人工项。

### S2：盘点接口清单与比对漂移（接口文档为可选输入）

**情况 A：用户提供了线上 Swagger / OpenAPI / 接口文档**

- 分支模式：只核对本次新增/修改的 Controller 接口及 PO/RO 字段是否已出现在线上文档；没出现则在报告开头提示「测试环境尚未部署当前分支最新代码」。
- 全量模式：线上全部分组与本地全部 `Controller` 比对，找出「线上有、本地无」或「本地有、线上无」。

**情况 B：未提供接口文档（最常见）**

接口文档和功能用例都不是必需输入，源码本身就足够提取接口清单；停下来向用户索要只会拖慢流程。

1. Glob/Grep 扫范围内 `Controller`（`@(Get|Post|Put|Delete|Request)Mapping` + `@Api/@ApiOperation/@Operation`）提取接口清单。
2. 找半成品缺口：
   - 仓库内有功能用例（`doc/testcases/`、`docs/testcases/`、`testcases/`）或需求文档（`openspec/`、`doc/`）：拿文档与 `Controller / BizService` 比对，抓「文档有要求、代码尚未写」的缺口。
   - 仓库内只有源码：直接扫半成品痕迹——被注释掉的接口注解（`// @PostMapping`）、`BizService` 中无任何 `Controller/Job` 调用的悬空方法、`TODO / FIXME`。
3. 报告 §2 注明「接口数取自本地 Controller（未提供线上接口文档，跳过部署漂移比对）」。

### S3：查 Controller 层厚度

对范围内的 `Controller` 和入参对象（DTO / PO / Request）逐一核对：

- 是不是 `return Result.success(service.xxx(req))` 这类 1～3 行纯委托？
- 除参数校验注解（`@Validated` / `@NotBlank` / `@NotNull`）和注入登录人外，有没有真逻辑？
- 分支模式额外核对：本次是否新增/修改了 PO 校验注解、`@JsonFormat`、`@ExcelProperty` 表头注解。这些改动在单测里看不到，有改动就进 §6 人工核验。

### S4：查持久层三类真库盲区，并反查到 HTTP 入口

单测里 `DAO / Service` 全是 Mock，不只手写 XML 没跑过，Java 层的复杂 Wrapper、链式 `lambdaUpdate()` 和唯一索引冲突同样被 `any()` 或 `RETURNS_DEEP_STUBS` 吞掉了，所以 XML 和 Java 两边都要扫。

**4A. 逐个读完范围内所有 `mapper/*.xml` 与注解 SQL**

- 全量模式：Glob `**/mapper/**/*.xml` + Grep `@Select|@Update|@Delete|@Insert`，每一个用 Read 打开读完。抽样读会漏掉恰好出问题的那一个。
- 分支模式：不仅读 `git diff` 直接改过的 XML/DAO，还要读「本次改动的 `BizService/Service` 方法内部所调用的 DAO/XML」。
- 挑出：多表 `JOIN` / `UNION ALL`、`EXISTS / NOT EXISTS`、动态 `<choose>/<if>/<foreach>`、游标分页、实时聚合 `SUM/MIN/MAX/COUNT + ROUND/IFNULL + GROUP BY/HAVING`、手写 `WHERE is_deleted = 0`、物理 `DELETE`。

**4B. Grep Java 层高危 Wrapper 与链式 `lambdaUpdate`**

搜 `\.apply\(|\.last\(|\.or\(|QueryWrapper<|UpdateWrapper<|lambdaUpdate\(`，重点三类：

1. 手写 SQL 片段与硬编码列名：`.apply("...")`、`.last("LIMIT ...")`、`new QueryWrapper<>().eq("列名", ...)`（易拼错列名或语法错）。
2. `.or(` 优先级：未套 `.and(w -> w.eq(...).or().eq(...))` 的裸 `.or()`，会让前面的 `warehouse_code` 或 `is_deleted` 条件被短路。
3. 链式 `lambdaUpdate()` 置 `null` 与更新条件：`RETURNS_DEEP_STUBS` 下中间 `.eq(单号/ID)` 漏写或 `.set(字段, null)` 在单测里恒绿，必须进真库验证。

**4C. 唯一索引冲突、软删重加与批量写事务**

结合 `doc/sql/*.sql`（或 Entity 索引注释）与代码中的 `removeByIds / @TableLogic / saveBatch / @Transactional`：

1. 软删 + 唯一索引：表有 `uk_xxx`（不含 `is_deleted`）且用 `@TableLogic`，验证「删除后用相同业务键再新增」会不会撞 `DuplicateKeyException`。
2. 主子表级联：主表 `save` 后 `saveBatch` 子表，或删主表连带 `remove(wrapper)` 子表，在真库事务下的表现。

**反查入口**：对 4A/4B/4C 每一项，一路反查 `BizService` → `Controller` 的 `@PostMapping/@GetMapping`（或 `Job`），落成「代码位置 ↔ HTTP 接口 ↔ 前置数据与页面操作步骤」映射表，填入 §4.1。

### S5：查非 HTTP 隐蔽入口（Job / MQ）

- 分支模式：本次是否新增/修改了 `@XxlJob`、`@Scheduled`、MQ `Listener`，或本次改动的 `BizService` 方法是否被某个老 `Job / Listener` 调用。
- 全量模式：搜全仓库 `job / task / mq / listener / schedule` 并列出触发验证方式。

### S6：查外部服务依赖 Client

- 分支模式：本次改动链路里调了哪些 `@DubboReference / @FeignClient`（`client/` 包）。
- 全量模式：搜全仓库外部 `Client`，指出在测试环境页面点哪个下拉框或按钮会真打这些外部接口。

---

## 二、L3（§6）六大类识别字典

写 §6 时六类都要过一遍，每类至少给一条或明确写「本范围无此类」。只写导入导出是最常见的漏法。有 `*-自动化覆盖矩阵.md` 时直接提取 `仅人工 / 无实现-阻塞 / 判据未定` 条目并附 TC 号；只有源码时按「仅源码识别」从代码里捞。

1. **真实文件往返（导入 / 导出 / 模板下载）**
   - 有矩阵：取导出/导入/模板相关 `仅人工` TC。
   - 仅源码：Grep `export|import|Template|MultipartFile|BasePlusExporter|BaseImporter|@ExcelProperty`，列出导入导出接口及 VO 表头格式（日期 `@DateTimeFormat`、单位括号）。
2. **前端默认筛选值与查询控件形态（后端 PO 不兜底）**
   - 有矩阵：取默认月份、单选/多选/树形下拉等 `仅人工` TC。
   - 仅源码：读 `*SearchPO` + `BizService` 查询方法，找出业务上通常有默认范围（按月统计、默认查待处理）但后端没赋默认值（传 null 查全表）的字段；同时搜 `EnumController` / `*Options` / `/tree` 接口识别下拉框数据源。
3. **动态表单联动 / 字段显隐与表头动态列**
   - 有矩阵：取随类型切换显隐/替换字段、动态 Tab、动态列的 `仅人工` TC。
   - 仅源码：读 `SavePO / UpdatePO / AddPO` 及 `BizService` 里的条件校验——出现 `if (type == A) { 校验字段 X } else if (type == B) { 校验字段 Y }` 即可反推前端有「切换类型时动态显示/隐藏/替换字段或列」的联动。
4. **终态按钮显隐/置灰 & 二次确认弹窗**
   - 有矩阵：取终态按钮隐藏/置灰、勾选确认框前不可点、取消关闭弹窗的 `仅人工` TC。
   - 仅源码：读 `*StatusEnum` 和 `BizService` 状态机拦截（`if (status != PENDING) throw ...`），反推终态下对应按钮应置灰或隐藏；所有 `delete / batchDelete / complete / confirm / recalc` 接口自动列入「二次确认弹窗 & 点取消不发请求」核对项。
5. **只读详情 / Tooltip / 状态标签颜色 / 操作日志弹窗**
   - 有矩阵：取问号 icon 提示、标签颜色、日志弹框的 `仅人工` TC。
   - 仅源码：是否有 `OperateLogController` 或写操作日志代码（对应「日志」弹窗）、只读详情接口（`/detail`、`/info`）、多态枚举标签。
6. **无实现-阻塞 / 需求缺口核查**
   - 有矩阵：取 `无实现-阻塞` 和 `判据未定-阻塞` 的 TC。
   - 仅源码：对比用例/文档要求与 `Controller` 接口；S2 找到的被注释接口（`// @PostMapping`）、`TODO`、悬空方法单列出来，上页面确认有没有入口。

---

## 三、技术栈映射表

本文关键字默认是 Java Spring + MyBatis-Plus + Mockito + XXL-Job + Dubbo/Feign。遇到其他技术栈，按下表取语义等价物，S0～S6 的判断逻辑不变。

| 本文关键字 | 判断的实际是什么 | 其他栈的等价物（示例） |
|---|---|---|
| `mapper/*.xml`、`@Select/@Update` | 手写 SQL，ORM 不校验 | JPA `@Query(nativeQuery=true)`；GORM `db.Raw()` / `db.Exec()`；Django `raw()` / `cursor.execute()`；SQLAlchemy `text()`；Prisma `$queryRaw` |
| `.apply/.last/.or`、`QueryWrapper` | 用链式/字符串拼出来的查询条件，单测 Mock 后不会真执行 | JPA `Specification` / QueryDSL 复杂谓词；GORM `Where("...")` 字符串条件 + `Or()`；Django `Q()` 组合；SQLAlchemy `or_()` 嵌套 |
| `lambdaUpdate().set(字段, null)` | 把字段置空的部分更新 | JPA `@Modifying @Query`；GORM `Updates(map)` 对零值的忽略；Django `update(field=None)`；Prisma `update({data:{field:null}})` |
| `@TableLogic` + `uk_*` | 逻辑删除与唯一索引不含删除标记 | 任何 soft-delete 插件（GORM `gorm.DeletedAt`、Django `is_deleted`、Prisma 中间件）+ 建表 SQL 的 `UNIQUE` |
| `saveBatch` + `@Transactional` | 主子表批量写与事务边界 | JPA `saveAll` + `@Transactional`；GORM `CreateInBatches` + `db.Transaction`；Django `bulk_create` + `atomic()` |
| `@XxlJob/@Scheduled`、MQ `Listener` | 非 HTTP 入口 | `@Scheduled` / Quartz；Go `cron`；Celery beat / task；Node `node-cron` / BullMQ worker；Kafka/RabbitMQ consumer |
| `@DubboReference/@FeignClient` | 单测里被 Mock 的下游服务 | gRPC stub；`RestTemplate/WebClient` 封装；Go HTTP client 接口；Python `requests` 封装类 |
| `Controller` 1～3 行透传 | 入口层无业务逻辑 | Gin/Echo handler；Django view / DRF ViewSet；Express/NestJS controller |
| `RETURNS_DEEP_STUBS` | 链式调用被 Mock 吞掉 | gomock 链式返回；`unittest.mock.MagicMock` 自动属性链；Jest `mockReturnThis()` |
