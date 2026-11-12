package com.iot.platform.timeseries;

import com.iot.platform.config.TdEngineProperties;
import com.iot.platform.model.SensorReading;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

public class TdEngineWriterTest {

    private JdbcTemplate jdbc;
    private TdEngineProperties properties;
    private TdEngineWriter writer;

    @Before
    public void setUp() {
        jdbc = mock(JdbcTemplate.class);
        properties = new TdEngineProperties();
        properties.setEnabled(true);
        writer = new TdEngineWriter(jdbc, properties);
    }

    @Test
    public void createsSchemaAndSubTableOnceAndBatchesInsert() {
        List<SensorReading> rows = Arrays.asList(
                reading("TEMP01", 235.0), reading("TEMP01", 236.0));

        assertEquals(2, writer.write(rows));

        ArgumentCaptor<String> ddl = ArgumentCaptor.forClass(String.class);
        verify(jdbc, atLeast(3)).execute(ddl.capture());
        List<String> sqls = ddl.getAllValues();
        assertTrue(sqls.get(0).contains("CREATE DATABASE IF NOT EXISTS iot"));
        assertTrue(contains(sqls, "CREATE STABLE IF NOT EXISTS iot.sensordata"));
        assertTrue(contains(sqls, "CREATE TABLE IF NOT EXISTS iot.sensordata_TEMP01"));
        assertTrue(contains(sqls, "(ts TIMESTAMP, sensorvalue DOUBLE)"));
        assertTrue(contains(sqls, "stationcode NCHAR("));
        assertTrue(contains(sqls, "'GW1'"));

        ArgumentCaptor<String> insert = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<BatchPreparedStatementSetter> setter =
                ArgumentCaptor.forClass(BatchPreparedStatementSetter.class);
        verify(jdbc, times(1)).batchUpdate(insert.capture(), setter.capture());
        assertTrue(insert.getValue().contains("INSERT INTO iot.sensordata_TEMP01"));
        assertTrue(insert.getValue().contains("VALUES (?, ?)"));
        assertEquals(2, setter.getValue().getBatchSize());

        // 第二次写入复用已建 schema / 子表，不再执行 DDL
        reset(jdbc);
        assertEquals(2, writer.write(rows));
        verify(jdbc, never()).execute(anyString());
        verify(jdbc, times(1)).batchUpdate(anyString(), any(BatchPreparedStatementSetter.class));
    }

    @Test
    public void groupsReadingsBySubTable() {
        List<SensorReading> rows = Arrays.asList(
                reading("TEMP01", 1.0), reading("HUM01", 2.0), reading("TEMP01", 3.0));

        assertEquals(3, writer.write(rows));

        verify(jdbc, times(2)).batchUpdate(anyString(), any(BatchPreparedStatementSetter.class));
        ArgumentCaptor<String> ddl = ArgumentCaptor.forClass(String.class);
        verify(jdbc, atLeast(4)).execute(ddl.capture());
        assertTrue(contains(ddl.getAllValues(), "iot.sensordata_TEMP01"));
        assertTrue(contains(ddl.getAllValues(), "iot.sensordata_HUM01"));
    }

    @Test
    public void sanitizesSubTableName() {
        assertEquals("sensordata_TEMP01", writer.subTableName("TEMP01"));
        assertEquals("sensordata_TEMP_01_DROP_TABLE", writer.subTableName("TEMP-01;DROP TABLE"));
        assertEquals("sensordata_unknown", writer.subTableName(null));
        StringBuilder longCode = new StringBuilder();
        for (int i = 0; i < 200; i++) {
            longCode.append('A');
        }
        assertTrue(writer.subTableName(longCode.toString()).length()
                <= properties.getMaxSubTableNameLength());
    }

    @Test
    public void disabledWriterSkipsAllJdbcCalls() {
        properties.setEnabled(false);
        assertEquals(0, writer.write(Arrays.asList(reading("TEMP01", 1.0))));
        verifyZeroInteractions(jdbc);
    }

    @Test
    public void schemaFailureIsIsolated() {
        doThrow(new RuntimeException("tdengine unavailable"))
                .when(jdbc).execute(anyString());
        assertEquals(0, writer.write(Arrays.asList(reading("TEMP01", 1.0))));
    }

    @Test
    public void insertFailureIsIsolated() {
        when(jdbc.batchUpdate(anyString(), any(BatchPreparedStatementSetter.class)))
                .thenThrow(new RuntimeException("insert failed"));
        assertEquals(0, writer.write(Arrays.asList(reading("TEMP01", 1.0))));
        assertEquals(1L, writer.getTotalFailed());
    }

    @Test
    public void escapeTagLiterals() {
        SensorReading row = reading("TEMP01", 1.0);
        row.setStationcode("GW'1");
        writer.write(Arrays.asList(row));

        ArgumentCaptor<String> ddl = ArgumentCaptor.forClass(String.class);
        verify(jdbc, atLeast(3)).execute(ddl.capture());
        assertTrue(contains(ddl.getAllValues(), "'GW''1'"));
    }

    private static SensorReading reading(String sensorcode, double value) {
        return new SensorReading(1700000000000L, "1", "GW1", "NODE1",
                sensorcode, "C", value);
    }

    private static boolean contains(List<String> sqls, String fragment) {
        for (String sql : sqls) {
            if (sql.contains(fragment)) {
                return true;
            }
        }
        return false;
    }
}
