# 可运行源码：运行与验收说明

## 1. 源码交付范围

本目录为说明入口，实际源码以仓库中的以下目录为准，避免复制后产生
不一致版本：

| 路径 | 说明 |
|---|---|
| `backend/` | Spring Boot 后端、Web API、CLI、规则执行与测试 |
| `frontend/` | Vue 3 Web 工作台 |
| `bin/data-validator`、`bin/data-validator.cmd` | Linux/macOS 与 Windows CLI 启动脚本 |
| `examples/case5-seed/` | 赛题5固定复现样例 |
| `examples/generic-jdbc/` | 非订单 JDBC 通用扩展示例 |
| `examples/generic-validation/` | 最小通用规则执行示例 |

除特别标注的 JDBC 样例外，运行命令从仓库根目录
`/Users/dum/google/proj/competition` 理解相对路径。

## 2. 环境要求

| 软件 | 用途 |
|---|---|
| JDK 11 或以上 | 运行后端与 CLI Jar |
| Maven 3.8 或以上 | 构建和测试后端 |
| Node.js 与 npm | 构建和运行前端 |

核心校验不依赖 AI 模型或 API Key。未启用模型时，AI 辅助位置可返回本地
规则化结果，便于稳定演示。

本目录交付的是源码，`backend/target/data-validator-0.1.0.jar` 不属于源码
文件。将源码 zip 解压到另一台机器后，必须先执行后文 Maven 构建命令；
若需交付“解压即运行”的压缩包，则应在构建后将该 Jar 一并放入
`backend/target/`。

## 3. Web 工作台验收路径

启动后端：

```bash
cd backend
mvn spring-boot:run
```

在另一终端启动前端：

```bash
cd frontend
npm install
npm run dev
```

浏览器访问 `http://localhost:5173`，执行以下任务：

1. 选择赛题5 Excel 输入文件并加载数据集。
2. 点击 `开始校验`，观察异常汇总。
3. 按规则编号定位 `R020`，查看订单实付金额与支付金额不一致的证据。
4. 定位 `R029`，查看重复支付风险。
5. 点击 `AI 分析`、`只读校验 SQL` 或 `人工核查 SQL`，确认结果仅供核查。
6. 点击 `导出报告`，保存用于评审的报告文件。

Web 当前支持 Markdown/HTML 报告生成；Excel 报告不作为本次成果承诺。

## 4. CLI 通用扩展验收路径

先构建后端 Jar：

```bash
cd backend
mvn package -DskipTests
cd ..
```

当前 `examples/generic-jdbc/source.yml` 的 H2 初始化脚本相对路径以
`backend/` 为运行目录解析，因此 JDBC 样例请在 `backend/` 目录执行。
先确认 shell 中 `java -version` 可用；如使用 Homebrew 安装的 JDK，请将
对应 `bin` 目录加入 `PATH`。

验证非订单 JDBC 样例配置：

```bash
cd backend
../bin/data-validator lint --config ../examples/generic-jdbc/validator.yml
```

执行 JDBC 样例并输出机器可读摘要：

```bash
../bin/data-validator run --config ../examples/generic-jdbc/validator.yml --json --no-report
```

Windows PowerShell 或 `cmd.exe` 请使用 `.cmd` 入口，不能直接执行无扩展名
的 Unix shell 脚本：

```bat
cd backend
..\bin\data-validator.cmd lint --config ..\examples\generic-jdbc\validator.yml
..\bin\data-validator.cmd run --config ..\examples\generic-jdbc\validator.yml --json --no-report
```

该样例使用 `contract_bill` 与 `payment` 表以及通用规则包；预期命中
`C960` 应收金额严重异常。退出码 `2` 表示发现达到失败等级的数据异常，
不表示工具执行错误。

返回仓库根目录后生成规则候选建议：

```bash
cd ..
bin/data-validator recommend \
  --rules examples/generic-validation/rules.yml \
  --metadata examples/generic-validation/source.yml \
  --output /private/tmp/data-validator-recommendations.json \
  --candidate-rules /private/tmp/data-validator-rules.recommended.yml
```

候选文件需要人工审阅，不会自动覆盖正式规则包。

## 5. CLI Case5 复现验收路径

```bash
bin/data-validator lint --config examples/case5-seed/validator.yml
bin/data-validator run --config examples/case5-seed/validator.yml --json --no-report
```

固定验收值：

| 指标 | 期望值 |
|---|---:|
| 执行规则数 | 30 |
| 数据表数 | 5 |
| 总行数 | 70 |
| 异常数 | 68 |
| 严重异常 | 60 |
| 警告异常 | 8 |
| `run` 退出码 | 2 |

## 6. 工程验证命令

```bash
cd backend
mvn test
cd ../frontend
npm run build
```

本次提交材料中的真实执行结果记录在
`../01-研发过程材料/05-测试复现与易用性验收记录.md`。

## 7. 安全边界

- AI 不参与确定性判定，不自动修改业务数据。
- SQL 草案只用于人工核查，系统不自动执行。
- JDBC 真实环境应使用只读账号，密码通过环境变量提供。
- Web 真实生产业务库接入和完整质量门禁仍属于后续演进。
- 当前 JDBC 演示样例存在运行目录约束，现场操作应按第 4 节执行；后续
  可将样例资源路径进一步调整为不依赖工作目录的分发形式。
