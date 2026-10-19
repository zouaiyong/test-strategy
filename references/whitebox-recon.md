# 6 步白盒摸底与源码特征识别细则

确定评估范围（【仅评估当前分支改动】或【评估全量代码】）后，严格按所选模式执行以下 6 步摸底，并按文末「第三层 6 大必查类别识别字典」提取或反推人工测试项。

---

## 一、6 步白盒摸底操作细则

### 第 0 步（仅「当前分支改动」模式必做）：锁定 Diff 清单与回归波及面
1. 先找准基准主干（优先看 `origin/master`、`origin/main`、`master`、`main`），**同时覆盖「已提交分支改动 + 未提交工作区/暂存区改动 + 未追踪新文件」**：
   - 先取分叉基点：`git merge-base <base> HEAD`（记为 `<mb>`），再跑 `git diff --stat <mb>` 与 `git diff <mb>`（直接对比分叉基点与当前工作区，既把已 commit 和未 commit 的修改一网打尽，又避免直接跑 `git diff <base>` 把主干上其他人后合入的提交误算进来）；
   - 若当前分支与主干同起点或无法取 `<mb>`，则组合执行 `git diff <base>...HEAD`（已提交增量）+ `git diff HEAD`（工作区与暂存区未提交修改）；
   - 再跑 `git status -s`：把所有 `??`（新增但尚未 `git add` 的未追踪源码文件）也纳入本次评估范围（但排除测试报告、构建产物等非业务源码文件）。
2. 将改动文件按层归类：`Controller`、`BizService/Service`、`Entity/PO/RO`、`mapper/*.xml / DAO`、`Job/MQ/Client`、`公共工具类/常量/配置`。
3. **反向追查波及面（防止漏测被连带改坏的老功能）**：
   - 若改动了公共工具类、枚举、Entity 字段或共用子方法，必须用 Grep 反向搜出**哪些未改动的 `BizService / Controller` 调用了它**，一并纳入本次评估范围；
   - **完全未改动且不在波及链上的主干老模块，直接排除，禁止混入后面的必测表和主链路。**

### 第 1 步：查已有用例、覆盖矩阵与单测现状（分「已有单测」和「尚未写单测/仅有源码」两种情况）
- **情况 A：项目/模块已有单测与 `*-自动化覆盖矩阵.md`**：
  1. 盘点已跑条数、红灯条数与 `*-判据偏差清单.md`；
  2. **第一刀（扫「四态清单表」）**：捞出整条标为 **`仅人工`、`无实现-阻塞`、`判据未定-阻塞`** 的条目及原因，进入第三层（报告第 6 节）；
  3. **第二刀（必做！扫「覆盖矩阵明细表」里的「半自动化 TC」备注缺口）**：在 `testcase-to-unittest` 的覆盖矩阵明细表中，很多 TC 虽然状态是 `已写` 或 `部分`，但 **`明细行 > 可自动化行`**（或「备注」列写明了未覆盖行的原因）。必须逐行扫描这些半自动化 TC 的「备注」列，按 `testcase-to-unittest` 的四类缺口精准分流，做到滴水不漏：
     - 备注含 **`集成层`**（如聚合 SQL 需真库、查无结果需真库）或 **`本层验不了 / 链式 mock 吞掉`**（如 `lambdaQuery` 条件被链式 mock 吞掉、Job 静态调用未 mock、Controller 反序列化拦截小数/非数字）👉 **归入第二层（报告第 4 节真库与联调必验清单）**；
     - 备注含 **`人工层`**（如半自动化 TC 里的弹窗、Toast、表头列、加粗、不关闭弹框、点击跳转）或 **`判据未定 / 【待确认】 / 以实际为准`** 👉 **归入第三层（报告第 6 节纯人工与阻塞专项）**。
- **情况 B：项目/模块还没写单测（甚至还没有功能用例，只有项目源码）**：
  - **禁止因为没单测就跳过第一层！** 必须直接扫描评估范围内的 `BizService / Service / Calculator / ExcelHandler / Utils` 源码，揪出**适合写单测的高价值目标**（算钱算量公式、精度进位、阶梯匹配、前置状态机拦截、导入逐行校验、复杂去重规则），列进报告的「**第一层：建议优先补单测的核心类与方法清单**」（并提示可交由 `testcase-to-unittest` 落地）；
  - 同时从源码中直接反推「第三层（纯人工项）」（反推方法见下方第二节「第三层 6 大必查类别识别字典」）。

### 第 2 步：盘点接口清单与比对漂移（接口文档为可选输入，没给禁止停步索要）
- **情况 A：用户提供了线上 Swagger / OpenAPI / 接口文档链接**：
  - **当前分支模式**：只核对**本次新增/修改的 Controller 接口及 PO/RO 字段**是否已发布到线上 Swagger（判断测试环境是否已部署最新分支代码）；若还没出现，在报告开头醒目提示「**测试环境尚未部署当前分支最新代码**」。
  - **全量模式**：拉取线上全部接口分组与本地全部 `Controller` 比对，找出「线上有、本地无」或「本地有、线上无」的模块。
- **情况 B：用户未提供接口文档（最常见，直接读本地代码，禁止向用户索要接口文档或功能用例）**：
  1. 直接用 Glob/Grep 扫本地评估范围内的 `Controller`（`@(Get|Post|Put|Delete|Request)Mapping` + `@Api/@ApiOperation/@Operation` 注解）提取接口清单；
  2. **排查未完成/半成品缺口（分有文档与纯源码两种情况）**：
     - *若仓库内有功能用例（`doc/testcases/`、`docs/testcases/` 或 `testcases/`）或需求文档（`openspec/`、`doc/`）*：拿文档与本地 `Controller / BizService` 比对，抓出「文档有要求、本地尚未写代码」的缺口；
     - *若仓库内既无功能用例也无需求文档（只有纯项目源码）*：**直接跳过文档比对，严禁停步向用户索要测试用例或 PRD！** 改为直接扫源码里的「半成品痕迹」——如 `Controller` 中被注释掉的接口注解（`// @PostMapping`）、`BizService` 中写了但无任何 `Controller/Job` 调用的悬空方法、以及代码中的 `TODO / FIXME`；
  3. 在报告第 2 节注明「接口数取自本地 Controller（未提供线上接口文档，跳过线上部署漂移比对）」。

### 第 3 步：查 Controller 层厚度
- 对范围内的 `Controller` 和入参对象（DTO / PO / Request）逐一核对：
  - 是不是 `return Result.success(service.xxx(req))` 这类 1～3 行的纯委托透传？
  - 除了参数校验注解（`@Validated` / `@NotBlank` / `@NotNull`）和注入当前登录人外，Controller 里有没有真逻辑？
  - **当前分支模式额外核对**：本次是否新增或修改了 PO 上的校验注解、日期 `@JsonFormat`、Excel `@ExcelProperty` 表头注解（若有改动，必须进第三层人工核验点）。

### 第 4 步：查持久层三大真库盲区，并反查到 HTTP 接口（禁止只看 XML）
单测里 `DAO / Service` 全是 Mock，不仅手写 XML 没跑过，**Java 层的复杂 Wrapper、链式 `lambdaUpdate()` 和唯一索引冲突也同样被 `any()` 或 `RETURNS_DEEP_STUBS` 吞掉了**。必须同时扫齐下面三小类：

- **4A. 逐个读完范围内所有 `mapper/*.xml` 与注解 SQL（禁止抽样）**：
  - **全量模式**：Glob 找所有 `**/mapper/**/*.xml` + Grep 搜 `@Select|@Update|@Delete|@Insert`，**每一个都必须用 Read 打开读完**；
  - **当前分支模式**：不仅读 `git diff` 直接改过的 XML/DAO，**还必须读「本次改动的 `BizService/Service` 方法内部所调用的 DAO/XML」**；
  - 挑出：多表 `JOIN` / `UNION ALL`、`EXISTS / NOT EXISTS`、动态 `<choose>/<if>/<foreach>`、游标分页、实时聚合 `SUM/MIN/MAX/COUNT + ROUND/IFNULL + GROUP BY/HAVING`、手写 `WHERE is_deleted = 0`、物理 `DELETE`。
- **4B. 扫 Java 层高危 Wrapper 与链式 `lambdaUpdate` 盲区（Grep 必搜）**：
  - 用 Grep 在范围内搜 `\.apply\(|\.last\(|\.or\(|QueryWrapper<|UpdateWrapper<|lambdaUpdate\(`；
  - 重点揪出 3 类单测抓不到的坑：
    1. **手写 SQL 片段与非 Lambda 硬编码列名**：`.apply("...")`、`.last("LIMIT ...")`、`new QueryWrapper<>().eq("硬编码列名", ...)`（易拼错列名或注入语法错）；
    2. **`.or(` 优先级错乱**：未套 `.and(w -> w.eq(...).or().eq(...))` 的裸 `.or()`（会导致前面的 `warehouse_code` 或 `is_deleted` 条件被短路失效）；
    3. **链式 `lambdaUpdate()` 置 `null` 与更新条件被吞**：单测用 `RETURNS_DEEP_STUBS` 时，中间 `.eq(单号/ID)` 漏写或 `.set(字段, null)`（如反审后把确认金额置回 `null`）在单测里恒绿，必须进真库验证。
- **4C. 扫唯一索引冲突、软删重加与批量写事务（`saveBatch` / `@Transactional`）**：
  - 结合 `doc/sql/*.sql`（或 Entity 索引注释）与代码中的 `removeByIds / @TableLogic / saveBatch / @Transactional` 检查：
    1. **「软删除 + 唯一索引」删后重加冲突**：若表有唯一索引 `uk_xxx`（不含 `is_deleted`）且用 `@TableLogic` 逻辑删除，必须验证「删除某条记录后，再用完全相同的业务键新增一条」会不会撞 `DuplicateKeyException`；
    2. **主子表级联写/删与 `saveBatch`**：主表 `save` 后 `saveBatch` 子表（或删除主表连带 `remove(wrapper)` 子表）在真库事务下的表现。
- **顺藤摸瓜反查到入口**：对 4A / 4B / 4C 筛出的每一项，一路反查到 `BizService` → `Controller` 的 `@PostMapping/@GetMapping`（或 `Job`），落成「代码位置 ↔ HTTP 接口 ↔ 前置数据与页面操作步骤」映射表。

### 第 5 步：查非 HTTP 隐蔽入口（定时任务 Job / MQ）
- **当前分支模式**：检查本次是否新增/修改了 `@XxlJob`、`@Scheduled`、MQ `Listener`，或本次改动的 `BizService` 方法是否被某个老 `Job / Listener` 调用；
- **全量模式**：搜全仓库所有 `job / task / mq / listener / schedule` 并列出触发验证方式。

### 第 6 步：查外部服务依赖 Client（单测里被 Mock 掉的下游）
- **当前分支模式**：检查本次改动的链路里调了哪些 `@DubboReference / @FeignClient`（`client/` 包）；
- **全量模式**：搜全仓库所有外部 `Client`，指出在测试环境页面点哪个下拉框或按钮会真打这些外部接口。

---

## 二、第三层（报告第 6 节）6 大必查类别识别字典（兼容「有覆盖矩阵」与「只有源码」）

撰写第三层人工专项时，**绝不能只写导入导出**。若有 `*-自动化覆盖矩阵.md` 则直接提取 `仅人工 / 无实现-阻塞 / 判据未定` 条目并附 TC 号；**若还没有写单测/没有覆盖矩阵（只有源码），必须按下面每类对应的「仅源码识别法」直接从代码里捞出来**：

1. **真实文件往返（导入 / 导出 / 模板下载）**
   - *有矩阵*：取导出/导入/模板相关 `仅人工` TC。
   - *仅源码识别*：Grep 搜 `export|import|Template|MultipartFile|BasePlusExporter|BaseImporter|@ExcelProperty`，列出所有导入导出接口及 VO 表头格式（日期 `@DateTimeFormat`、单位括号）。
2. **前端默认筛选值与查询控件形态（后端 PO 不兜底、全靠前端传参的）**
   - *有矩阵*：取默认月份、单选/多选/树形下拉等 `仅人工` TC。
   - *仅源码识别*：读 `*SearchPO` + `BizService` 查询方法——找出业务上通常有默认范围（如按月统计、默认查待处理）但**后端代码里没赋默认值（传 null 就查全表）**的字段，提醒人工必测「打开页面前端是否带了默认筛选值」；同时搜 `EnumController` / `*Options` / `/tree` 接口识别下拉框数据源。
3. **动态表单联动 / 字段显隐与表头动态列**
   - *有矩阵*：取随类型切换显隐/替换字段、动态 Tab、动态阶梯列的 `仅人工` TC。
   - *仅源码识别*：读 `SavePO / UpdatePO / AddPO` 及 `BizService` 里的条件校验——凡是代码里出现 `if (type == A) { 校验字段 X } else if (type == B) { 校验字段 Y }`（如选辅料才校验固定面积、选 B2C 才校验首续件单价、选某模式才返回动态列），直接反推出前端必有**「切换类型时动态显示/隐藏/替换表单字段或表头列」**的联动，列入人工必测。
4. **终态按钮显隐/置灰 & 二次确认弹窗**
   - *有矩阵*：取终态按钮隐藏/置灰、勾选确认框前不可点、取消关闭弹窗的 `仅人工` TC。
   - *仅源码识别*：读 `*StatusEnum` 和 `BizService` 里的状态机拦截（如 `if (status != PENDING) throw BizFailException(...)`），反推出页面在该终态下对应操作按钮应置灰或隐藏；所有 `delete / batchDelete / complete / confirm / recalc` 接口自动列入「页面二次确认弹窗 & 点取消不发请求」核对项。
5. **只读详情 / 悬浮提示（Tooltip） / 状态标签颜色 / 操作日志弹窗**
   - *有矩阵*：取问号 icon 提示、标签颜色、日志弹框的 `仅人工` TC。
   - *仅源码识别*：检查是否有 `OperateLogController` 或写操作日志代码（对应页面「日志」弹窗）、只读详情接口（`/detail`、`/info`）。
6. **「无实现-阻塞 / 需求缺口」专项核查（极重要）**
   - *有矩阵*：取矩阵中标为 `无实现-阻塞` 和 `判据未定-阻塞` 的 TC。
   - *仅源码识别*：若只有功能用例或 `openspec` 文档但没写单测，对比用例/文档要求的操作与 `Controller` 接口（例如用例写了删除对账单、引用拦截、Controller 里被注释掉的接口 `// @PostMapping` 或 `TODO`），把代码没做的项单列出来，上页面直接确认。
