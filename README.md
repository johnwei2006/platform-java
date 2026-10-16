# platform-java — IoT 后台业务平台

通用 IoT 平台的**后台业务平台**：设备管理、上行数据接入与持久化、规则/告警、对外 REST API，并通过 `dtm` 与 `ProxyTcp` 完成上下行。

> 设计基线见文档仓 [`12_design/platform_java/design.md`](https://github.com/johnwei2006/iot-platform-docs/blob/main/12_design/platform_java/design.md) 与 [`12_design/api-design.md`](https://github.com/johnwei2006/iot-platform-docs/blob/main/12_design/api-design.md)。

## 功能

- **设备管理**：注册、查询、状态维护（MongoDB）。
- **上行接入**：通过 `platform.uplink` 消费设备数据，持久化 `data_records`，刷新设备在线状态。
- **规则/告警**：简单规则示例（serviceType=99 生成 ERROR 告警），可扩展。
- **下行指令**：`POST /api/devices/{id}/downlink` → 发布到 `platform.downlink`（经 dtm 透传至网关）。
- **鉴权**：HMAC 签名令牌（脚手架实现，生产请替换标准 JWT）。

## 技术栈

- Spring Boot 2.1.6（与 `ProxyTcp`/`dtm` 对齐，Java 8）
- Spring AMQP（RabbitMQ）、Spring Data MongoDB、Actuator

## 主要接口

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/auth/login` | 登录获取令牌（白名单） |
| GET | `/api/devices` | 设备列表 |
| GET/PUT/DELETE | `/api/devices/{id}` | 设备详情/更新/删除 |
| POST | `/api/devices/{id}/downlink` | 下发下行指令 |
| GET | `/api/data?deviceId=` | 设备数据查询 |
| GET | `/api/data/latest?deviceId=` | 最新数据 |
| GET | `/api/alarms?status=` | 告警列表 |
| POST | `/api/alarms/{id}/resolve` | 处理告警 |
| GET | `/health`, `/actuator/health` | 健康检查 |

所有受保护接口需在请求头携带 `Authorization: Bearer <token>`。

## 构建与运行

```bash
mvn clean package
java -jar target/platform-java-0.0.1-SNAPSHOT.jar
# 或开发运行
mvn spring-boot:run
```

- 端口：`8081`。
- 依赖中间件：`MongoDB`（`MONGO_URI`）、`RabbitMQ`（`RABBITMQ_*`）、JWT 密钥（`JWT_SECRET`）。

## 与网关/传输的契约

- 上行：设备 → ProxyTcp → `proxy.uplink` → dtm → `platform.uplink`（JSON：`UplinkMessage`）。
- 下行：后台 → `platform.downlink` → dtm → `proxy.downlink` → ProxyTcp（JSON：`DownlinkMessage`，对齐网关 `ServiceRequest`）。
