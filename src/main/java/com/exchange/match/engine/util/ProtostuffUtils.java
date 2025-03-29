package com.exchange.match.engine.util;

import io.protostuff.LinkedBuffer;
import io.protostuff.ProtostuffIOUtil;
import io.protostuff.Schema;
import io.protostuff.runtime.RuntimeSchema;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Protostuff序列化工具类
 */
public class ProtostuffUtils {
    
    private static final Map<Class<?>, Schema<?>> SCHEMA_CACHE = new ConcurrentHashMap<>();
    
    /**
     * 获取类的Schema
     *
     * @param clazz 类
     * @param <T>   类型
     * @return Schema
     */
    @SuppressWarnings("unchecked")
    private static <T> Schema<T> getSchema(Class<T> clazz) {
        Schema<T> schema = (Schema<T>) SCHEMA_CACHE.get(clazz);
        if (schema == null) {
            schema = RuntimeSchema.getSchema(clazz);
            SCHEMA_CACHE.put(clazz, schema);
        }
        return schema;
    }
    
    /**
     * 序列化对象
     *
     * @param obj 对象
     * @param <T> 类型
     * @return 序列化后的字节数组
     */
    @SuppressWarnings("unchecked")
    public static <T> byte[] serialize(T obj) {
        Class<T> clazz = (Class<T>) obj.getClass();
        LinkedBuffer buffer = LinkedBuffer.allocate(LinkedBuffer.DEFAULT_BUFFER_SIZE);
        try {
            Schema<T> schema = getSchema(clazz);
            return ProtostuffIOUtil.toByteArray(obj, schema, buffer);
        } finally {
            buffer.clear();
        }
    }
    
    /**
     * 反序列化对象
     *
     * @param data  字节数组
     * @param clazz 类
     * @param <T>   类型
     * @return 反序列化后的对象
     */
    public static <T> T deserialize(byte[] data, Class<T> clazz) {
        try {
            T obj = clazz.getDeclaredConstructor().newInstance();
            Schema<T> schema = getSchema(clazz);
            ProtostuffIOUtil.mergeFrom(data, obj, schema);
            return obj;
        } catch (Exception e) {
            throw new RuntimeException("反序列化失败", e);
        }
    }
} 