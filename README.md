# platform-java — IoT 后台业务平台

通用 IoT 平台的**后台业务平台**：设备管理、上行数据接入与持久化、规则/告警、对外 REST API，并通过 `dtm` 与 `ProxyTcp` 完成上下行。

> 设计基线见文档仓 [`12_design/platform_java/design.md`](https://github.com/johnwei2006/iot-platform-docs/blob/main/12_design/platform_java/design.md) 与 [`12_design/api-design.md`](https://github.com/johnwei2006/iot-platform-docs/blob/main/12_design/api-design.md)。

## 功能

- **设备管理**：注册、查询、状态维护（MongoDB）。
- **上行接入**：通过 `platform.uplink` 消费设备数据，持久化 `data_records`，刷新设备在线状态。
- **业务转换**：上行按 `serviceType` 分派给业务转换器（扩展点 `UplinkBusinessConverter`），本期实现传感器配置元数据与实时读数。
- **时序归档**：`0X18 GATEWAY_SENSOR_DATA_REPORT` 的多通道读数写入 TDengine 超级表 `sensordata`，按 `sensordata_{sensorcode}` 分表。
- **实时状态**：上行旁路把「在线状态 + 最新业务数据」写入 Redis（String JSON + TTL），提供只读 REST 查询。
- **规则/告警**：简单规则示例（serviceType=99 生成 ERROR 告警），可扩展。
- **下行指令**：`POST /api/devices/{id}/downlink` → 发布到 `platform.downlink`（经 dtm 透传至网关）。
- **鉴权**：HMAC 签名令牌（脚手架实现，生产请替换标准 JWT）。

## 技术栈

- Spring Boot 2.1.6（与 `ProxyTcp`/`dtm` 对齐，Java 8）
- Spring AMQP（RabbitMQ）、Spring Data MongoDB、Spring Data Redis（Lettuce）、Actuator
- TDengine（原生 JDBC `taos-jdbcdriver` + `JdbcTemplate`，仅用于传感器时序数据）

## 主要接口

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/auth/login` | 登录获取令牌（白名单） |
| GET | `/api/devices` | 设备列表 |
| GET | `/api/devices/realtime` | 实时状态列表（TTL 内在线的设备） |
| GET | `/api/devices/realtime/{deviceId}` | 单设备实时状态，无 key 返回业务码 404 |
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
- 依赖中间件：`MongoDB`（`MONGO_URI`）、`RabbitMQ`（`RABBITMQ_*`）、JWT 密钥（`JWT_SECRET`）、时序库（`TDENGINE_*`，可选）、缓存（`REDIS_*`，可选）。

## 传感器时序数据（TDengine）

### 分派与转换

`DataIngestionService.onUplink` 完成原有 Mongo 持久化、设备在线刷新和告警后，把 `UplinkMessage` 交给 `UplinkBusinessDispatcher`。分派器按 `serviceType` 选择 `UplinkBusinessConverter` Bean：

| serviceType | 协议 | 转换器 | 行为 |
| --- | --- | --- | --- |
| `0X10`(16) | GATEWAY_CONFIG_SENSOR_REPORT | `SensorConfigConverter` | 登记 `(stationcode, nodecode, channel) -> (sensorCode, sensorType)` |
| `0X11`(17) | GATEWAY_CONFIG_SENSOR | `SensorConfigConverter` | 同上 |
| `0X18`(24) | GATEWAY_SENSOR_DATA_REPORT | `SensorDataConverter` | 生成多通道读数并写入 TDengine |
| 其他 | — | 无 | 保持原有入库 / 在线 / 告警行为 |

新增业务类型只需实现 `UplinkBusinessConverter` 并声明为 Spring Bean，分派器与接入服务无需改动。只有 `decodeStatus` 为 `OK`（或老消息未携带该字段）且 `serviceDataDecoded` 为对象时才做业务转换。

### 表结构

```sql
CREATE STABLE IF NOT EXISTS iot.sensordata
  (ts TIMESTAMP, sensorvalue DOUBLE)
  TAGS (sensortype NCHAR(64), stationcode NCHAR(64), nodecode NCHAR(64),
        sensorcode NCHAR(64), unit NCHAR(64));

CREATE TABLE IF NOT EXISTS iot.sensordata_{sensorcode}
  USING iot.sensordata TAGS (...);
```

- 普通列只有 `ts`、`sensorvalue`；其余 5 个业务维度为 TAGS。子表名 `sensordata_{sensorcode}`，`sensorcode` 只保留字母数字下划线并限长。
- `stationcode` 取上行信封 `deviceId`（原始需求中的 `gatewaycode` 为旧称，标签名集中在 `TdEngineWriter` 常量中定义）。
- `nodecode` 取 `serviceDataDecoded.nodeId`，缺失时回退信封 `nodeId`。
- `sensortype` / `sensorcode` 来自 `0X10/0X11` 配置报文按 channel 关联；未命中时 `sensorcode` 用 `CH{channel}` 兜底并汇总告警，不丢数据。
- `unit` 由 `tdengine.unit-by-sensor-type` 按 sensorType 映射，未配置为空串。
- `ts` 优先 `collectTime`（time6，两位年份按 2000+ 处理），否则上行 `timestamp`，再否则服务当前时间；`channelResult != 0` 的无效读数跳过。
- 首次写入时幂等建库、建超级表；每个子表建一次后缓存，TAGS 以首次建表时的元数据为准。

### 配置

| 环境变量 | 默认值 | 说明 |
| --- | --- | --- |
| `TDENGINE_ENABLED` | `true` | 置 `false` 完全跳过时序库写入 |
| `TDENGINE_HOST` / `TDENGINE_PORT` | `localhost` / `6030` | 原生 JDBC 地址 |
| `TDENGINE_USER` / `TDENGINE_PASSWORD` | `root` / `taosdata` | 连接凭据，不写日志 |
| `TDENGINE_DATABASE` | `iot` | 库名 |
| `tdengine.stable` | `sensordata` | 超级表名 |
| `tdengine.unit-by-sensor-type` | 见 `application.yml` | sensorType -> 单位映射示例 |

默认 `enabled=true` 但采用**懒连接**：启动时不会建连，因此未部署 TDengine 时应用与单元测试仍可正常启动。写入失败（含连接不可用、DDL/INSERT 异常）只在分派旁路内做限频告警并累计 `totalWritten/totalFailed`，**不抛出、不触发 RabbitMQ 重试或死信**；MongoDB 写入仍保持原有语义。

## 设备实时状态（Redis）

### 写入链路

`DataIngestionService.onUplink` 完成 Mongo 持久化、设备在线刷新与告警后，依次调用两个互相独立的旁路：`UplinkBusinessDispatcher`（业务转换 / TDengine）和 `DeviceRealtimeStatusService`（Redis 实时状态）。每收到一条上行就刷新一次状态，因此 key 的 TTL 就是「最近活跃窗口」：TTL 内存在即 `ONLINE`，过期后 key 自动消失，查询方视为 `OFFLINE`，无需定时扫描或显式下线。

Redis 采用一个设备一个 String key：`<prefix><deviceId>`，值为 `DeviceRealtimeStatus` 的 JSON：

| 字段 | 说明 |
| --- | --- |
| `deviceId` / `nodeId` / `status` | 设备标识与 `ONLINE` 状态；`nodeId` 优先取 `serviceDataDecoded.nodeId` |
| `lastSeen` / `updatedAt` / `ttlSeconds` | 平台接收时间（epoch millis）与写入时使用的 TTL |
| `serviceType` / `protocolName` / `transactionType` / `decodeStatus` / `eventId` / `deviceTimestamp` | 最近一条上行的信封信息 |
| `latestBusinessData` | 最近一次 `serviceDataDecoded` 的全量结构化业务数据 |
| `sensors` | 最近一次有效通道读数摘要（过滤 channel 缺失、`channelResult != 0`、NaN/Inf） |
| `truncated` | 是否因超过字节上限被裁剪 |

`sensors` 摘要直接来自解码后的 `sensors` 数组，不查传感器配置元数据；`sensorCode` / `sensorType` / `unit` 仅在报文本身携带时填充，完整元数据关联以 TDengine 子表为准。

### 读接口与枚举

- `GET /api/devices/realtime`：用 **SCAN**（非 `KEYS`）枚举 `<prefix>*` 后批量取值；单条坏 JSON 跳过，最多返回 `realtime.max-list-size` 条。静态路径 `/realtime` 比 `DeviceController` 的 `/{id}` 更具体，由 Spring MVC 优先匹配（有 MockMvc 单测覆盖）。
- `GET /api/devices/realtime/{deviceId}`：读取单个 key，不存在时返回业务码 `404`。
- 两个接口都不在 `JwtFilter` 白名单内，需要 `Authorization: Bearer <token>`。

### 配置

| 环境变量 | 默认值 | 说明 |
| --- | --- | --- |
| `REDIS_ENABLED` | `true` | 置 `false` 完全跳过状态写入与读取 |
| `REDIS_HOST` / `REDIS_PORT` | `localhost` / `6379` | Redis 地址 |
| `REDIS_PASSWORD` | 空 | 连接口令，不写日志 |
| `REDIS_DATABASE` / `REDIS_TIMEOUT` | `0` / `2000` | 库编号与超时（ms） |
| `REDIS_TTL_SECONDS` | `120` | 状态 key TTL，应大于设备上行周期 |
| `REDIS_KEY_PREFIX` | `device:status:` | key 前缀 |
| `REDIS_MAX_JSON_BYTES` | `8192` | 单条状态 JSON 上限；超出时按 `latestBusinessData` → `sensors` → 最小在线骨架逐级裁剪 |
| `REDIS_SCAN_COUNT` / `REDIS_MAX_LIST_SIZE` | `200` / `1000` | SCAN 每批数量与列表返回上限（对应 `realtime.scan-count` / `realtime.max-list-size`） |

### 错误隔离

Lettuce 为懒连接，未部署 Redis 时应用与单元测试仍可启动。所有 Redis 读写异常都在 `RedisDeviceStatusStore` 内限频告警并降级，并由 `DeviceRealtimeStatusService` 二次兜底：写失败只丢缓存，读失败返回空列表 / `404`，**不影响 Mongo 写入、设备在线刷新、告警、业务转换和 RabbitMQ ACK**。

## 与网关/传输的契约

- 上行：设备 → ProxyTcp → `proxy.uplink` → dtm → `platform.uplink`（JSON：`UplinkMessage`，即网关 `ServiceRequest` 信封，字段对齐：deviceId/nodeId/serviceType/serviceVersion/serviceDataProtocolVersion/serviceDataEncoding/serviceData(原始字节 base64)/serviceEncryption）。
- 下行：后台 → `platform.downlink` → dtm → `proxy.downlink` → ProxyTcp（JSON：`DownlinkMessage`，对齐网关 `ServiceRequest`）。
