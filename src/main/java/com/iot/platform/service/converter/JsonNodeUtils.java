package com.iot.platform.service.converter;

import com.fasterxml.jackson.databind.JsonNode;

/** 结构化业务字段的读取辅助：统一处理 null、数字、字符串与固定长度 string 的 \\0 填充。 */
public final class JsonNodeUtils {

    private JsonNodeUtils() {
    }

    public static String text(JsonNode node) {
        if (node == null || node.isNull()) {
            return "";
        }
        return clean(node.isTextual() ? node.asText() : node.toString());
    }

    public static String text(String value) {
        return value == null ? "" : clean(value);
    }

    private static String clean(String value) {
        return value.trim().replaceAll("\u0000+$", "");
    }
}
