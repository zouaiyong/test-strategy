# test-strategy

分析项目或模块的测试策略与分层取舍（单测 vs 真库 SQL / 接口联调 vs 页面手工测试），产出可执行测试策略报告。本仓库是给 AI Agent 读的规则，不是测试执行框架。

面向 **Java Spring + MyBatis-Plus** 后端项目设计；其他技术栈可用，`references/whitebox-recon.md` 末尾有关键字映射表。

装好后对 Agent 说「出个测试策略」「还需要做哪些测试」「要不要做接口测试」「哪些用单测哪些用手工」「接下来怎么开始测」即可。Agent 会先让你选评估范围，再按 `SKILL.md` 与 `references/report-template.md` 生成测试策略文档。

核心原则是不硬凑：不需要做的测试直接判「不需要」，不照着接口文档把所有接口列一遍；凡是列出的待测项，都写清前置数据、操作步骤、预期结果。

不覆盖：从 PRD 设计功能用例（用 `testcase-design`）、编写自动化单测代码（用 `testcase-to-unittest`）、单条 SQL 审查、代码评审、线上问题排查。细则以 `SKILL.md` 为准。

---

## 安装

```bash
npx skills add zouaiyong/test-strategy
```

会装到 `~/.agents/skills/test-strategy`。WorkBuddy、Claude Code 不读这个目录，装完后让对应 Agent 把该路径软链接到自己的 skills 目录（WorkBuddy：`~/.workbuddy/skills/test-strategy`；Claude Code：`~/.claude/skills/test-strategy`）。Windows 建不了软链接就用目录联接。链好后新开一轮对话即可被发现。

也可以把本仓库直接放到上述目录。目录里必须有 `SKILL.md`。

---

## 使用

把被测代码所在的项目交给 Agent（可附带 Swagger / Knife4j / OpenAPI 接口文档或 `*-自动化覆盖矩阵.md`），并指定一种评估范围：

1. **仅评估当前分支改动**（常用）：对比主干分支（`origin/master` 或 `origin/main`）的增量与公共代码调用波及面，剔除无关老模块
2. **评估全量代码**：扫描整个仓库所有模块，适合新项目整体提测或老项目全盘摸底

没说评估范围时 Agent 会先弹窗询问，不会擅自开扫；无交互环境下默认按分支模式并在报告首行标注。没给接口文档或功能用例时，Agent 直接读本地 `Controller` 与源码，不会停步向你索要文档。

已有单测和覆盖矩阵时，Agent 会扫「四态清单表」和「半自动化 TC 备注缺口」分流剩余工作；还没写单测时，会从源码中挑出建议优先补单测的核心类与方法，并反推页面人工必测项。

默认写到被测项目的 `doc/testcases/{项目或模块}-测试策略.md`（分支模式建议带分支名）。指定了目录就写到该目录。

---

## 交付物

报告固定 7 节，完整模板见 [references/report-template.md](references/report-template.md)：

`§1 评估结论与测试取舍` → `§2 现状与范围盘点` → `§3 核心业务场景验证（场景 1 ~ 场景 N）` → `§4 纯人工与阻塞专项（六大类）` → `§5 真库与外部联调必验清单（4A 手写 SQL / 4B Wrapper / 4C 软删唯一键与事务 / Job 与 Client）` → `§6 规则与纯口径单测` → `§7 下一步行动顺序`。

随 `§3 核心业务场景` 顺手触发的页面项或真库盲区，只在 `§3` 写一次页面点击步骤，`§4 / §5` 引用对应 `场景 X` 编号并只写增量核对点，不重复抄写同一套操作。

---

## 仓库结构

```text
.
├── SKILL.md                        # Agent 入口：选范围、S0~S6 目的表、三层模型与五条尺子、自检清单
├── references/
│   ├── whitebox-recon.md           # S0~S6 操作细则、L3 六大类识别字典、技术栈映射表
│   └── report-template.md          # 报告 §1~§7 落盘模板
└── evals/
    ├── evals.json                  # 3 条评测提示词与 expectations
    ├── trigger-eval.json           # 20 条触发/不触发查询
    ├── README.md                   # 怎么跑一轮评测
    └── fixtures/
        ├── ANSWER-KEY.md           # fixture 埋了什么、报告必须抓到什么
        └── storage-fee-demo/       # 只读源码骨架（无单测、无用例），埋有 4A/4B/4C/Job/Client 样本
```

编号约定：`§N` 报告节、`SN` 摸底步、`L1/L2/L3` 三层，三套编号在所有文件中统一使用。

---

## 常见问题

**还没有写单测或功能用例能用吗？** 能。Agent 会直接扫描源码揪出高价值单测目标（算钱算量、精度进位、状态拦截、导入校验），并从源码条件分支与 PO/Enum 特征反推出六大类页面人工必测项。

**没给 Swagger / 接口文档会卡住吗？** 不会。未提供线上接口文档时直接读本地 `Controller` 注解提取接口；提供了线上文档则会额外比对本地与线上的接口漂移（如提示测试环境尚未部署最新代码）。

**为什么很多接口不建议写接口自动化？** 算钱算量与复杂校验下沉到单测几秒跑完且幂等；纯透传 Controller 的内部管理后台接口直接走页面手工主链路就能连路由、JSON 序列化、真库 SQL 一起验完，不重复造接口脚本。

**单测里有红灯（失败用例）时怎么处理？** 保持断言跟用例走、不改绿。报告会把 `*-判据偏差清单.md` 列为第一步最高优动作（先找开发修代码或找产品定基线），不会用新增其他测试来掩盖偏差。

**不是 Java / MyBatis-Plus 项目怎么办？** 判断逻辑（手写 SQL、复杂查询条件、置空更新、软删唯一键、批量事务、非 HTTP 入口、外部依赖）与技术栈无关，`references/whitebox-recon.md` 第三节给了 JPA / GORM / Django / SQLAlchemy / Prisma 等的等价关键字。
