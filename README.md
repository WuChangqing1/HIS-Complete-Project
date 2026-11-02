# HIS 门诊管理系统

基于 Spring Boot、Vue 2 和 Element UI 的门诊业务仿真项目，支持中文 / English。

## 功能

- 患者档案、挂号与退号、医生接诊和病历管理。
- 检查、检验、处置申请与执行，药品处方、发药退药、库存管理。
- 收费退费、操作审计、实时业务提醒。
- 七类角色：管理员、挂号收费员、门诊医生、检查科、检验科、处置科、药房。
- 前端菜单及按钮权限、后端角色校验、医生所属患者记录过滤。

## 环境

Java 21、Maven 3.9+、Node.js 22、MySQL 8.x。当前验证环境为 Java 21.0.10、Maven 3.9.9、Node 22.19.0、MySQL 8.4.4。

## Windows 启动

运行 `start-his.bat`，脚本检查已有工具，缺少时下载到项目 `.runtime`。首次启动生成 `.env` 和独立数据库目录；默认访问 `http://127.0.0.1:8088/`。此公开版通用脚本经过语法检查，个人电脑专用脚本与环境配置不随仓库发布。

## 手动构建

```powershell
cd frontend
npm.cmd ci
npm.cmd run test:medical-navigation
npm.cmd run build
cd ..
New-Item -ItemType Directory -Force backend/src/main/resources/static
Copy-Item frontend/dist/* backend/src/main/resources/static -Recurse -Force
cd backend
mvn clean verify
```

运行 `backend/target/his-1.0.0.jar` 前，请配置以下环境变量：

| 变量 | 用途 |
|---|---|
| `DB_URL` | MySQL JDBC URL，包含数据库名与时区 |
| `DB_USER` / `DB_PASSWORD` | 应用数据库账号 |
| `DATA_ENCRYPTION_KEY` | 64 个十六进制字符，创建数据后应保持不变 |
| `BOOTSTRAP_PASSWORD` | 首次创建管理员的密码 |
| `DEMO_MODE` / `DEMO_PASSWORD` | 是否创建业务演示账号及其初始密码 |
| `SERVER_PORT` / `SERVER_ADDRESS` | HTTP 端口与监听地址 |

```sh
java -Duser.timezone=Asia/Shanghai -jar backend/target/his-1.0.0.jar
```

`.env.example` 由部署脚本读取，直接运行 Java 不会自动加载 `.env`。数据库需要先创建，表结构由 Flyway 自动迁移。

## 账号与权限

| 账号 | 身份 |
|---|---|
| admin | 管理员 |
| registrar | 挂号收费员 |
| doctor | 门诊医生 |
| exam | 检查科医生 |
| lab | 检验科医生 |
| procedure | 处置科医生 |
| pharmacy | 药房管理员 |

初始密码由部署配置指定，源码不包含任何正在运行实例的密码。改变初始化变量不会自动重置已有账号。患者基础档案和汇总统计属于共享数据；管理员拥有全部业务权限。

## 验证与边界

- 后端现有 23 项单元测试通过。
- 诊疗工作台导航的 2 项前端回归检查通过。
- 本机已验证登录、角色接口隔离、跨角色业务流程、中英文切换及工作台导航；这些记录不代表全部业务用例或生产验收。
- 现有 Flyway 8.5.13 对 MySQL 8.4 提示版本超出已测试范围，本机迁移和业务验证通过。
- Vue 2 和 Spring Boot 2.7 技术栈用于本项目演示；本项目没有经过真实医疗场景的生产验收。

## 仓库范围

公开源码、依赖锁文件、迁移 SQL、自动化测试及项目架构资料。不包含运行实例的 `.env`、数据库、患者记录、登录会话、日志、备份、下载的工具、个人机器配置或原始上传的课程附件。演示数据由独立部署流程管理。

## License

MIT，见 [LICENSE](LICENSE)。第三方依赖沿用各自许可证。
