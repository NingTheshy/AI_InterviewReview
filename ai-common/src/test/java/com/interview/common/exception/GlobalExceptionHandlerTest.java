package com.interview.common.exception;

import com.interview.common.result.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("GlobalExceptionHandler 测试")
class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
    }

    @Test
    @DisplayName("handleBusinessException - 返回业务错误码和消息")
    void handleBusinessException_returnsErrorCodeAndMessage() {
        BusinessException e = new BusinessException(1001, "用户名已存在");
        ResponseEntity<Result<Void>> response = handler.handleBusinessException(e);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        Result<Void> result = response.getBody();
        assertNotNull(result);
        assertEquals(1001, result.getCode());
        assertEquals("用户名已存在", result.getMessage());
    }

    @Test
    @DisplayName("handleBusinessException - 默认错误码 500")
    void handleBusinessException_defaultCode_returns500() {
        BusinessException e = new BusinessException("服务器错误");
        ResponseEntity<Result<Void>> response = handler.handleBusinessException(e);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        Result<Void> result = response.getBody();
        assertNotNull(result);
        assertEquals(500, result.getCode());
        assertEquals("服务器错误", result.getMessage());
    }

    @Test
    @DisplayName("handleBindException - 返回参数绑定错误")
    void handleBindException_returnsBindError() {
        FieldError fieldError = new FieldError("request", "username", "用户名不能为空");
        BindingResult bindingResult = mock(BindingResult.class);
        when(bindingResult.getFieldErrors()).thenReturn(java.util.List.of(fieldError));
        when(bindingResult.getAllErrors()).thenReturn(java.util.List.of(fieldError));

        BindException bindException = new BindException(bindingResult);
        Result<Void> result = handler.handleBindException(bindException);
        assertEquals(400, result.getCode());
        assertTrue(result.getMessage().contains("用户名不能为空"));
    }

    @Test
    @DisplayName("handleConstraintViolationException - 返回约束违反错误")
    void handleConstraintViolationException_returnsConstraintError() {
        java.util.Set<jakarta.validation.ConstraintViolation<?>> violations = new java.util.HashSet<>();
        jakarta.validation.ConstraintViolationException e =
                new jakarta.validation.ConstraintViolationException("参数校验失败", violations);
        Result<Void> result = handler.handleConstraintViolationException(e);
        assertEquals(400, result.getCode());
        assertEquals("参数校验失败", result.getMessage());
    }

    @Test
    @DisplayName("handleException - 返回系统错误")
    void handleException_returnsSystemError() {
        Exception e = new Exception("系统异常");
        Result<Void> result = handler.handleException(e);
        assertEquals(500, result.getCode());
        assertEquals("服务器内部错误", result.getMessage());
    }
}
