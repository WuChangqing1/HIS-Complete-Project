# HIS 门诊管理系统

一个用于软件工程课程和门诊流程仿真的医院信息系统。系统将患者档案、挂号、医生接诊、医技执行、收费与药房串联为可演示的业务流程，支持中文和 English。可用不同岗位账号体验权限边界，也可用管理员查看跨岗位流程及操作日志。

**验收状态（2026-11-04）：课程演示有条件通过。真实多用户挂号暂不通过，阻断项为 BUG-011 并发挂号超卖。**

## 功能与角色

| 演示账号 | 角色 | 主要功能 |
| --- | --- | --- |
| `admin` | 系统管理员 | 查看和操作全部业务模块，查阅操作审计 |
| `registrar` | 挂号收费员 | 建档、挂号、退号、收费和退费 |
| `doctor` | 门诊医生 | 查看接诊队列，填写病历、提交确诊、发起医技申请、开立处方 |
| `exam` | 检查科医生 | 检查登记、执行、录入结果 |
| `lab` | 检验科医生 | 检验登记、执行、录入结果 |
| `procedure` | 处置科医生 | 处置登记、执行、录入结果 |
| `pharmacy` | 药房管理员 | 发药、退药、管理药品及库存 |

前端按角色展示菜单和操作按钮，后端独立校验操作权限。门诊医生的诊疗相关记录按接诊医生过滤；患者基础档案和工作台汇总属于共享数据，管理员可访问全部业务模块。

建议演示顺序：挂号收费员建档和挂号，门诊医生接诊并申请检查或检验，收费员收费，医技科室执行并录入结果，医生确诊并开具处方，收费后由药房发药。处置申请在确诊后开立。点击患者行的「诊疗工作台」可直接进入该患者的工作区，并从「返回患者列表」切回队列。

## 技术结构

浏览器加载 Vue 前端并调用同源 `/api` 接口。生产构建时，前端静态资源放入 Spring Boot JAR，由一个 HTTP 服务对外提供页面和接口。后端通过 JDBC 访问 MySQL，Flyway 在启动时管理数据库结构。

| 路径 | 内容 |
| --- | --- |
| [`frontend`](frontend/) | Vue 2、Element UI、页面、双语资源及前端回归测试 |
| [`backend`](backend/) | Spring Boot 2.7、接口、认证授权、业务规则和单元测试 |
| [`backend/src/main/resources/db/migration`](backend/src/main/resources/db/migration/) | Flyway 数据库迁移 |
| [`windows`](windows/) | Windows 运行时检查和部署辅助脚本 |
| [`scripts`](scripts/) | 本地数据库备份与恢复脚本 |
| [`tests`](tests/) | 跨角色冒烟脚本和测试用例矩阵 |
| [`docs`](docs/) | 架构、接口、部署与测试资料 |

## Windows 快速启动

验证过的开发环境为 Windows 11 x64、Java 21.0.10、Maven 3.9.9、Node.js 22.19.0 和 MySQL 8.4.4。项目编译目标为 Java 21，前端使用 Node.js 22，Maven 使用 3.9 系列。首次下载工具与构建依赖需要联网。

1. 克隆仓库并进入根目录。

   ```powershell
   git clone https://github.com/WuChangqing1/HIS-Complete-Project.git
   cd HIS-Complete-Project
   ```

2. 执行 `.\start-his.bat`。脚本优先查找可用工具，缺少时准备项目专用运行时；首次运行生成 `.env` 并初始化独立数据库。
3. 默认打开 `http://127.0.0.1:8088/`。健康检查位于 `/actuator/health`。默认独立 MySQL 端口为 `33306`，不占用系统常用的 `3306`。
4. `admin` 的初始密码取本机 `.env` 的 `BOOTSTRAP_PASSWORD`；启用 `DEMO_MODE` 时，其余六个演示账号使用 `DEMO_PASSWORD`。这些值只用于创建不存在的账号，修改变量不会重置已有账号。

| 日常操作 | 命令 |
| --- | --- |
| 启动 | `.\start-his.bat` |
| 查看状态 | `.\check-his.bat` |
| 重建前后端并启动 | `.\rebuild-his.bat` |
| 停止本项目进程 | `.\stop-his.bat` |

首次生成配置可参考 [`.env.example`](.env.example)。端口冲突时先确认端口归属，再调整项目 `.env` 的 `WEB_PORT` 或 `DB_PORT`，不要停止其他项目进程。日志和运行数据位于 `.runtime/`；它不是 Git 工作目录。可通过 [`scripts/backup.ps1`](scripts/backup.ps1) 备份，恢复前先核对目标数据库与对应密钥。

## 手动构建与运行

在仓库根目录执行以下 PowerShell 命令。构建过程需要可用的 Maven 和 npm 依赖仓库。

```powershell
Push-Location frontend
npm.cmd ci
npm.cmd run lint
npm.cmd run test:medical-navigation
npm.cmd run build
Pop-Location
New-Item -ItemType Directory -Force backend/src/main/resources/static | Out-Null
Copy-Item frontend/dist/* backend/src/main/resources/static -Recurse -Force
mvn -f backend/pom.xml clean verify
```

打包前确认前端静态目录不含旧构建残留。JAR 位于 `backend/target/his-1.0.0.jar`。手动运行前先建好 MySQL 数据库和应用账号，并在进程环境中设置：

| 变量 | 含义 |
| --- | --- |
| `DB_URL` | JDBC 地址，例如 `jdbc:mysql://127.0.0.1:33306/his?serverTimezone=Asia/Shanghai` |
| `DB_USER` / `DB_PASSWORD` | 应用数据库连接账号和密码 |
| `DATA_ENCRYPTION_KEY` | 64 位十六进制字符；创建数据后应保留同一密钥，并与数据库配套备份 |
| `BOOTSTRAP_ADMIN` / `BOOTSTRAP_PASSWORD` | 首次创建的管理员账号和密码，账号默认 `admin` |
| `DEMO_MODE` / `DEMO_PASSWORD` | 是否初始化业务演示账号及其初始密码；直接运行时默认关闭演示模式 |
| `SERVER_ADDRESS` / `SERVER_PORT` | HTTP 监听地址与端口；本机手动演示可设为 `127.0.0.1` 和 `8088` |

```powershell
java '-Duser.timezone=Asia/Shanghai' -jar backend/target/his-1.0.0.jar
```

直接运行 Java 不会自动加载 `.env`。应用配置的默认 HTTP 端口为 `8080`，Windows 一键启动会指定 `8088`。如单独开发前端，可在 `frontend` 中运行 `npm.cmd run serve -- --host 127.0.0.1`，当前 Vue 开发代理将 `/api` 和 `/actuator` 转发到后端 `8080`；若使用其他后端端口，需要同步调整 `frontend/vue.config.js`。

Linux 环境可参考[独立部署与运维](docs/deployment/isolated-linux.md)。建议为 HIS 使用独立数据库、端口、数据目录和服务名。现有 Flyway 8.5.13 对 MySQL 8.4 可能提示超出已测试范围；本项目在上述环境通过了迁移检查，不代表任意 MySQL 版本均已验证。

## 测试结果与已知问题

下表为 2026-11-04 两轮测试的历史记录，第二轮被测源码为 `86ad20c`，包含修复提交 `85f6c83`。各套件可有重叠场景，不能把执行次数直接累加为主用例覆盖数。

| 范围 | 第二轮结果 |
| --- | --- |
| 主用例 335 条 | 192 通过、8 失败、5 阻塞、130 未复测 |
| 专项 48 条 | 4 通过、1 失败、3 未执行、40 未复测 |
| 权限矩阵 | 91/91 通过 |
| 构建及回归 | 后端 25/25、前端 2/2，lint 无错误；只读接口 141/141、浏览器界面 36/36 |
| 后端行覆盖率 | 23/253，约 9.1%；API 与认证包覆盖仍不足 |

七项已修缺陷已独立复测关闭，涉及 HTTP 400/405 响应、移动端登录溢出、导航可访问名称、审计时间、lint 及新建患者弹窗文案。首轮通过、第二轮未运行的用例保留历史结论，不视为新版通过。远程验证为匿名只读，不包含远程登录后业务闭环。

| 已知问题 | 当前影响 |
| --- | --- |
| BUG-011 并发挂号超卖 | 隔离库中 40 个号源收到 50 个并发请求，全部成功且实际占用 50；阻断真实多用户挂号验收。 |
| BUG-008 覆盖不足 | 需继续补充业务服务、认证及事务相关测试。 |
| BUG-012、BUG-013 | 表单校验提示可能显示函数文本；下拉选项与输入框尺寸不一致。 |

[`tests/api_smoke.py`](tests/api_smoke.py) 会创建仿真业务数据，仅在获得授权的隔离演示库运行，不用于共享演示库或远程站点。构建通过、接口通过与完整业务验收是不同结论。

## 设计与接口资料

- [架构与数据设计](docs/architecture/架构与数据设计.md)
- [API 接口清单](docs/architecture/API接口清单.md)
- [测试策略与已知限制](docs/tests/测试策略与已知限制.md)
- [历史缺陷防回归规范](docs/quality/历史缺陷防回归规范.md)

本仓库公开源码、锁文件、数据库迁移和测试脚本。运行实例的 `.env`、数据库、登录会话、备份、日志及团队私有交付件不随仓库发布。项目使用仿真数据，未经过真实医疗场景的生产验收。

## 许可证

本项目按 [MIT 许可证](LICENSE) 发布。第三方依赖沿用各自的许可证。
