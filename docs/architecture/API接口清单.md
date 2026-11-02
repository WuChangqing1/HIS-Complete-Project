# API 接口清单

API 前缀 `/api`，数据为 JSON，除登录外必须在 Header 提供 `Authorization: Bearer <token>`。错误响应结构：`{"error":"说明"}`，错误通常返回 400/401/403/404/409/500。具体字段以源码中的控制器与 `HisService` 为准。

| 方法 | 路径 | 功能 | 主要可写角色 |
|---|---|---|---|
| POST | /auth/login | 登录，获取 token | 公开 |
| GET | /auth/me | 当前用户 | 任意已认证 |
| POST | /auth/logout | 注销 | 任意已认证 |
| GET | /meta | 科室、号别、结算类别、医生列表 | 任意已认证 |
| GET | /schedules?date=&period= | 查询医生出诊及余号 | REGISTRAR、DOCTOR |
| GET | /patients?q= | 患者查找 | 任意已认证 |
| POST | /patients | 创建患者（含可选 requestId） | REGISTRAR |
| PUT | /patients/{id} | 编辑患者（病历号不可修改） | REGISTRAR |
| GET | /registrations?q= | 挂号队列 | REGISTRAR、DOCTOR |
| POST | /registrations | 挂号收费（含可选 requestId） | REGISTRAR |
| POST | /registrations/{id}/cancel | 退号并退款 | REGISTRAR |
| GET | /records/{registrationId} | 查询本人病历 | DOCTOR |
| PUT | /records/{registrationId} | 保存病历首页 | DOCTOR |
| POST | /records/{registrationId}/finalize | 提交确诊 | DOCTOR |
| GET | /catalog?type=CHECK/LAB/PROCEDURE | 项目目录 | 任意已认证 |
| GET | /services?type=&q= | 项目申请及结果 | 对应医技科室或本人 DOCTOR |
| POST | /services | 开立医技申请（含可选 requestId） | DOCTOR |
| POST | /services/{type}/{id}/start | 登记/开始项目 | 对应医技科室 |
| POST | /services/{type}/{id}/complete | 录入结果 | 对应医技科室 |
| POST | /services/{type}/{id}/cancel | 撤销未执行申请 | DOCTOR |
| GET | /drugs?q= | 药品查询 | 任意已认证 |
| POST | /drugs | 新增药品 | PHARMACIST |
| PUT | /drugs/{id} | 编辑药品信息 | PHARMACIST |
| POST | /drugs/{id}/stock | 库存调整 | PHARMACIST |
| GET | /prescriptions?q= | 药品处方 | DOCTOR、REGISTRAR、PHARMACIST |
| POST | /prescriptions | 创建处方与收费明细（含可选 requestId） | DOCTOR |
| POST | /prescriptions/{itemId}/dispense | 发药（扣库存） | PHARMACIST |
| POST | /prescriptions/{itemId}/return | 退药（还库存） | PHARMACIST |
| GET | /charges?q= | 收费项目及状态 | DOCTOR、REGISTRAR、PHARMACIST |
| POST | /charges/{id}/pay | 收费；method/received，返回 change | REGISTRAR |
| POST | /charges/{id}/refund | 退费；受服务执行状态约束 | REGISTRAR |
| GET | /dashboard | 门诊指标 | 任意已认证 |
| GET | /audit | 最近操作审计 | ADMIN |

`ADMIN` 可执行上述业务角色操作，但演示数据权限保护不构成生产级细粒度授权。
