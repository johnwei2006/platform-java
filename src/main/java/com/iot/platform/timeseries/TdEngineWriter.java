package com.iot.platform.timeseries;

import com.iot.platform.config.TdEngineConfig;
import com.iot.platform.config.TdEngineProperties;
import com.iot.platform.model.SensorReading;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 传感器读数写入 TDengine。
 *
 * <p>超级表 sensordata 只保留 ts / sensorvalue 两列，其余业务维度作为 TAGS，
 * 子表按 sensordata_{sensorcode} 命名。启动时不建连，首次写入时幂等建库、建超级表，
 * 每个子表首次出现时执行 CREATE TABLE IF NOT EXISTS ... USING ... TAGS (...)。
 *
 * <p>所有来自设备的字符串都经过白名单清洗或转义；写入用占位符。
 * 任何异常都在此处消化并做限频告警，绝不影响上行消息确认。
 */
@Component
public class TdEngineWriter implements SensorReadingWriter {

    /** 标签名集中定义：原始需求中的 gatewaycode 在此统一为 stationcode。 */
    private static final String TAG_SENSORTYPE = "sensortype";
    private static final String TAG_STATIONCODE = "stationcode";
    private static final String TAG_NODECODE = "nodecode";
    private static final String TAG_SENSORCODE = "sensorcode";
    private static final String TAG_UNIT = "unit";

    private static final Logger log = LoggerFactory.getLogger(TdEngineWriter.class);
    private static final long WARN_INTERVAL_MS = 60000L;

    private final JdbcTemplate jdbcTemplate;
    private final TdEngineProperties properties;
    private final ConcurrentMap<String, Boolean> createdSubTables =
            new ConcurrentHashMap<String, Boolean>();
    private final AtomicLong totalWritten = new AtomicLong();
    private final AtomicLong totalFailed = new AtomicLong();
    private final AtomicLong lastWarnAt = new AtomicLong();
    private final Object schemaLock = new Object();

    private volatile boolean schemaReady;

    @Autowired
    public TdEngineWriter(JdbcTemplate jdbcTemplate, TdEngineProperties properties) {
        this.jdbcTemplate = jdbcTemplate;
        this.properties = properties;
    }

    @Override
    public int write(List<SensorReading> readings) {
        if (readings == null || readings.isEmpty()) {
            return 0;
        }
        if (!properties.isEnabled()) {
            log.debug("TDengine disabled, skip {} sensor readings", readings.size());
            return 0;
        }
        try {
            ensureSchema();
        } catch (Exception e) {
            totalFailed.addAndGet(readings.size());
            warnThrottled("ensure TDengine schema failed: " + e.getMessage(), readings.size());
            return 0;
        }

        Map<String, List<SensorReading>> grouped = groupBySubTable(readings);
        int written = 0;
        for (Map.Entry<String, List<SensorReading>> entry : grouped.entrySet()) {
            String subTable = entry.getKey();
            List<SensorReading> rows = entry.getValue();
            try {
                ensureSubTable(subTable, rows.get(0));
                // batchUpdate 未抛异常即视为该批全部写入；不同驱动对返回数组的行数语义不一致，
                // 不以返回值推断成功条数，避免误报写入不完整。
                jdbcTemplate.batchUpdate(insertSql(subTable), new SensorBatchSetter(rows));
                int groupWritten = rows.size();
                written += groupWritten;
                totalWritten.addAndGet(groupWritten);
            } catch (Exception e) {
                totalFailed.addAndGet(rows.size());
                warnThrottled("TDengine insert into " + subTable + " failed: " + e.getMessage(),
                        rows.size());
            }
        }
        return written;
    }

    private Map<String, List<SensorReading>> groupBySubTable(List<SensorReading> readings) {
        Map<String, List<SensorReading>> grouped =
                new LinkedHashMap<String, List<SensorReading>>();
        for (SensorReading reading : readings) {
            String table = subTableName(reading.getSensorcode());
            List<SensorReading> rows = grouped.get(table);
            if (rows == null) {
                rows = new ArrayList<SensorReading>();
                grouped.put(table, rows);
            }
            rows.add(reading);
        }
        return grouped;
    }

    private void ensureSchema() {
        if (schemaReady) {
            return;
        }
        synchronized (schemaLock) {
            if (schemaReady) {
                return;
            }
            String database = databaseName();
            jdbcTemplate.execute("CREATE DATABASE IF NOT EXISTS " + database);
            jdbcTemplate.execute(stableDdl(database));
            schemaReady = true;
            log.info("TDengine schema ready: database={} stable={} tags={},{},{},{},{}",
                    database, stableName(), TAG_SENSORTYPE, TAG_STATIONCODE, TAG_NODECODE,
                    TAG_SENSORCODE, TAG_UNIT);
        }
    }

    private String stableDdl(String database) {
        int tagLength = Math.max(16, properties.getTagLength());
        return "CREATE STABLE IF NOT EXISTS " + database + "." + stableName()
                + " (ts TIMESTAMP, sensorvalue DOUBLE) TAGS ("
                + TAG_SENSORTYPE + " NCHAR(" + tagLength + "), "
                + TAG_STATIONCODE + " NCHAR(" + tagLength + "), "
                + TAG_NODECODE + " NCHAR(" + tagLength + "), "
                + TAG_SENSORCODE + " NCHAR(" + tagLength + "), "
                + TAG_UNIT + " NCHAR(" + tagLength + "))";
    }

    private void ensureSubTable(String subTable, SensorReading sample) {
        if (createdSubTables.containsKey(subTable)) {
            return;
        }
        synchronized (schemaLock) {
            if (createdSubTables.containsKey(subTable)) {
                return;
            }
            String database = databaseName();
            // DDL 的 TAGS 只能使用字面量：所有值先转义并限长，避免设备上报内容注入 SQL。
            String ddl = "CREATE TABLE IF NOT EXISTS " + database + "." + subTable
                    + " USING " + database + "." + stableName() + " TAGS ("
                    + literal(sample.getSensortype()) + ", "
                    + literal(sample.getStationcode()) + ", "
                    + literal(sample.getNodecode()) + ", "
                    + literal(sample.getSensorcode()) + ", "
                    + literal(sample.getUnit()) + ")";
            jdbcTemplate.execute(ddl);
            createdSubTables.put(subTable, Boolean.TRUE);
        }
    }

    private String insertSql(String subTable) {
        return "INSERT INTO " + databaseName() + "." + subTable
                + " (ts, sensorvalue) VALUES (?, ?)";
    }

    /** 子表名白名单清洗：sensordata_{sensorcode}，仅保留字母数字下划线并限长。 */
    String subTableName(String sensorcode) {
        String prefix = stableName() + "_";
        String cleaned = sensorcode == null ? "" : sensorcode.replaceAll("[^A-Za-z0-9_]", "_");
        if (cleaned.isEmpty()) {
            cleaned = "unknown";
        }
        String name = prefix + cleaned;
        int max = properties.getMaxSubTableNameLength();
        if (max > prefix.length() && name.length() > max) {
            name = name.substring(0, max);
        }
        return name;
    }

    private String literal(String value) {
        int max = Math.max(16, properties.getTagLength());
        String v = value == null ? "" : value;
        v = v.replace("'", "''").replaceAll("[\\x00-\\x1f]", "");
        return "'" + (v.length() > max ? v.substring(0, max) : v) + "'";
    }

    private String databaseName() {
        return TdEngineConfig.sanitizeIdentifier(properties.getDatabase());
    }

    private String stableName() {
        String raw = properties.getStable();
        if (raw == null || raw.trim().isEmpty()) {
            return "sensordata";
        }
        return TdEngineConfig.sanitizeIdentifier(raw);
    }

    private void warnThrottled(String reason, int affected) {
        long now = System.currentTimeMillis();
        long last = lastWarnAt.get();
        if (last != 0 && now - last < WARN_INTERVAL_MS) {
            log.debug("TDengine issue (suppressed): {} affected={}", reason, affected);
            return;
        }
        if (lastWarnAt.compareAndSet(last, now)) {
            log.warn("TDengine issue: {} affected={} totalWritten={} totalFailed={}",
                    reason, affected, totalWritten.get(), totalFailed.get());
        }
    }

    public long getTotalWritten() {
        return totalWritten.get();
    }

    public long getTotalFailed() {
        return totalFailed.get();
    }

    public void resetSchemaCache() {
        schemaReady = false;
        createdSubTables.clear();
    }

    private static final class SensorBatchSetter implements BatchPreparedStatementSetter {

        private final List<SensorReading> rows;

        private SensorBatchSetter(List<SensorReading> rows) {
            this.rows = rows;
        }

        @Override
        public void setValues(PreparedStatement ps, int i) throws SQLException {
            SensorReading row = rows.get(i);
            ps.setTimestamp(1, new Timestamp(row.getTs()));
            ps.setDouble(2, row.getSensorvalue());
        }

        @Override
        public int getBatchSize() {
            return rows.size();
        }
    }
}
