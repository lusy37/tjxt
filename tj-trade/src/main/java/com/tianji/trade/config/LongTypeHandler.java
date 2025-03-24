package com.tianji.trade.config;

import com.baomidou.mybatisplus.extension.handlers.AbstractJsonTypeHandler;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.List;

public class LongTypeHandler extends AbstractJsonTypeHandler<List<Long>> {
    private static final ObjectMapper objectMapper = new ObjectMapper();

    // 解析 JSON 字符串为 List<Long>
    @Override
    protected List<Long> parse(String json) {
        if (json == null || json.isEmpty()) {
            return null;  // 返回 null 如果 JSON 字符串为空
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<Long>>() {});  // 使用 Jackson 反序列化为 List<Long>
        } catch (IOException e) {
            throw new IllegalArgumentException("Cannot parse JSON to List<Long>: " + json, e);
        }
    }

    // 将 List<Long> 转换为 JSON 字符串
    @Override
    protected String toJson(List<Long> obj) {
        if (obj == null || obj.isEmpty()) {
            return "[]";  // 如果 List 是 null 或空，返回空的 JSON 数组
        }
        try {
            return objectMapper.writeValueAsString(obj);  // 使用 Jackson 将 List<Long> 转换为 JSON 字符串
        } catch (IOException e) {
            throw new IllegalArgumentException("Cannot convert List<Long> to JSON", e);
        }
    }
}

