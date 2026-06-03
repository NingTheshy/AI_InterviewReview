package com.interview.common.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("RedisUtil 工具类测试")
class RedisUtilTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private RedisUtil redisUtil;

    @Test
    @DisplayName("set - 设置键值对")
    void set_validInput_callsRedis() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        redisUtil.set("key1", "value1", 60, TimeUnit.SECONDS);
        verify(valueOperations).set("key1", "value1", 60, TimeUnit.SECONDS);
    }

    @Test
    @DisplayName("get - 获取键值")
    void get_existingKey_returnsValue() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("key1")).thenReturn("value1");
        String result = redisUtil.get("key1");
        assertEquals("value1", result);
    }

    @Test
    @DisplayName("get - 键不存在返回 null")
    void get_nonExistingKey_returnsNull() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("key1")).thenReturn(null);
        String result = redisUtil.get("key1");
        assertNull(result);
    }

    @Test
    @DisplayName("delete - 删除键")
    void delete_existingKey_returnsTrue() {
        when(redisTemplate.delete("key1")).thenReturn(true);
        Boolean result = redisUtil.delete("key1");
        assertTrue(result);
    }

    @Test
    @DisplayName("hasKey - 键存在返回 true")
    void hasKey_existingKey_returnsTrue() {
        when(redisTemplate.hasKey("key1")).thenReturn(true);
        Boolean result = redisUtil.hasKey("key1");
        assertTrue(result);
    }

    @Test
    @DisplayName("expire - 设置过期时间")
    void expire_validInput_returnsTrue() {
        when(redisTemplate.expire("key1", 60, TimeUnit.SECONDS)).thenReturn(true);
        Boolean result = redisUtil.expire("key1", 60, TimeUnit.SECONDS);
        assertTrue(result);
    }

    @Test
    @DisplayName("getExpire - 获取过期时间")
    void getExpire_existingKey_returnsTtl() {
        when(redisTemplate.getExpire("key1")).thenReturn(60L);
        Long result = redisUtil.getExpire("key1");
        assertEquals(60L, result);
    }
}
