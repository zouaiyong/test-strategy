# evals：skill 自身的评测

按 skill-creator 的流程，用同一组提示词分别跑「带 skill」和「不带 skill / 旧版 skill」，再按 `evals.json` 里的 expectations 打分。fixture 是一个只读源码、不需要编译的 Spring + MyBatis-Plus 骨架，答案卡在 `fixtures/ANSWER-KEY.md`，只给打分方看。

## 目录

```
evals/
├── evals.json                 # 3 条提示词 + 可核对的 expectations
├── trigger-eval.json          # 20 条触发/不触发查询，给 description 优化循环用
├── README.md
└── fixtures/
    ├── ANSWER-KEY.md          # 埋了哪些盲区、报告必须抓到什么、出现即扣分的反向判据
    └── storage-fee-demo/      # 源码骨架（demo.fee 包），无单测、无用例、无覆盖矩阵
        ├── doc/sql/storage_fee.sql           # uk_warehouse_month 不含 is_deleted
        └── src/main/
            ├── java/demo/fee/
            │   ├── controller/   # 7 个纯透传接口 + 1 个被注释的 batchConfirm
            │   ├── service/      # 裸 .or()、.last、lambdaUpdate 置 null、saveBatch、按类型条件校验
            │   ├── calc/         # 纯口径：÷6000 取整、三档阶梯、B2C ×1.2
            │   ├── mapper/       # @Update 注解 SQL
            │   ├── job/          # @XxlJob
            │   └── client/       # @FeignClient
            └── resources/mapper/StorageFeeMapper.xml   # LEFT JOIN + UNION ALL + NOT EXISTS + <if>
```

## 跑一轮

1. 把 `fixtures/` 整个复制到工作区（如 `../test-strategy-workspace/iteration-1/eval-1/with_skill/fixture/`），不要在仓库内原地跑：skill 会往项目里写 `doc/testcases/`。
2. 把 `evals.json` 里提示词中的 `{fixture}` 替换成复制后的绝对路径。
3. 带 skill 的运行：子 agent 的 prompt 给 skill 路径 + 提示词；基线运行：同一提示词，不给 skill（或指向改动前的 skill 快照）。
4. 子 agent 没有弹窗工具，eval 1 的提示词已明确「看全量代码」以绕过范围弹窗；eval 2 专门考弹窗行为，在子 agent 环境下看它是否按「非交互默认分支模式并标注」执行。
5. 跑完后对照 `ANSWER-KEY.md` 和每条 eval 的 expectations 打分，写进 `grading.json`（字段 `text` / `passed` / `evidence`）。

## 三条 eval 各测什么

| id | 名字 | 考的是 |
|---|---|---|
| 1 | full-scan-source-only-finds-all-blind-spots | 只有源码时 S1~S6 是否跑全：4A/4B/4C 一个不漏、§6 六类不只写导入导出、透传接口不硬凑接口脚本 |
| 2 | asks-scope-before-scanning | 没说范围时是否先弹窗；非交互环境是否按默认分支模式并标注 |
| 3 | no-source-swagger-only-degrades-to-blackbox | 无源码时是否诚实降级为黑盒，而不是杜撰源码级发现或铺全量接口清单 |

## fixture 为什么这样埋

- `list()` 里的裸 `.or()` 和 `.last("LIMIT 500")` 放在同一个方法，考 4B 是否真的 grep 了 Java 层而不是只读 XML。
- `reverse()` 的 `set(…, null)` 单测用 deep stubs 必绿，是 4B 第三类的标准样本。
- 建表 SQL 的 `uk_warehouse_month` 故意不含 `is_deleted`，与 `@TableLogic` 组合成 4C-1；报告若没提这条就是漏了 `doc/sql`。
- `settleByMonth` 是 `@Update` 注解 SQL 且只被 Job 调用，考 4A 是否搜了注解 SQL 以及 S5 是否反查到了非 HTTP 入口。
- `StorageFeeSearchPO.billMonth` 的注释、`validateByType` 的分支、`SETTLED` 终态、被注释的 `batchConfirm`，分别对应 §6 的第 2/3/4/6 类，让六类都有源码依据。
- Controller 七个接口全是 1 行委托，考「尺子 2」是否敢判「不需要接口测试」。

## description 触发评测

`trigger-eval.json` 的负例都是近义干扰：写单测、从 PRD 出用例、单条 SQL 审查、代码评审、断言核对、接口自动化脚本、线上排障、测试报告、测试要点。

在 Cursor 里直接用 `Task` 子 agent 做盲判：把 `test-strategy` 与 `testcase-design` / `testcase-to-unittest` / `testcase-closed-loop` 等姊妹 skill 的 description 拼成一份 `<available_skills>`，起 2～4 个独立子 agent（正序、逆序各跑），让它们只看 description 对 20 条查询逐条选「首选 skill 或 none」，新旧 description 各跑一组对比正例命中与负例误触。

2026-10 的一轮盲判里新旧 description 均 20/20，当前负例对两版都没有区分度；下一轮想优化 description 时先补更贴边的负例（测试覆盖率够不够、性能测试方案、接口幂等、单测太慢、列出所有接口）。
