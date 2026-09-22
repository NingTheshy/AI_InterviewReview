package com.interview.common.exception;

import com.interview.common.result.Result;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理器
 * <p>
 * 统一处理 Controller 层抛出的异常，返回标准 Result 格式。
 * 支持处理业务异常、参数校验异常、绑定异常等。
 * </p>
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 将业务错误码映射到适合前端处理的 HTTP 状态，同时保留原始业务码。 */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<Void>> handleBusinessException(BusinessException e) {
        log.warn("业务异常: code={}, message={}", e.getCode(), e.getMessage());
        HttpStatus status = mapCodeToStatus(e.getCode());
        return ResponseEntity.status(status).body(Result.error(e.getCode(), e.getMessage()));
    }

    /** 维护业务错误码与 HTTP 语义的单一映射点，未列出的业务错误默认视为请求错误。 */
    private HttpStatus mapCodeToStatus(int code) {
        return switch (code) {
            // 标准 HTTP 状态码
            case 400 -> HttpStatus.BAD_REQUEST;
            case 401, 1002, 1004 -> HttpStatus.UNAUTHORIZED;
            case 403, 1003, 2004, 5001, 5002, 5006 -> HttpStatus.FORBIDDEN;
            case 404, 2003, 3001, 5003 -> HttpStatus.NOT_FOUND;
            case 500 -> HttpStatus.INTERNAL_SERVER_ERROR;
            // 业务状态码
            case 2005, 4002 -> HttpStatus.CONFLICT;
            case 3002 -> HttpStatus.BAD_GATEWAY;
            case 3003 -> HttpStatus.INTERNAL_SERVER_ERROR;
            case 4003, 4004 -> HttpStatus.GONE;
            // 默认
            default -> HttpStatus.BAD_REQUEST;
        };
    }

    /** 聚合请求体字段校验错误，避免前端只能获得第一条错误。 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleValidationException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .reduce((a, b) -> a + "; " + b)
                .orElse("参数校验失败");
        return Result.error(400, message);
    }

    /** 处理查询参数和表单绑定错误。 */
    @ExceptionHandler(BindException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleBindException(BindException e) {
        String message = e.getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .reduce((a, b) -> a + "; " + b)
                .orElse("参数绑定失败");
        return Result.error(400, message);
    }

    /** 处理方法参数上的 Bean Validation 约束错误。 */
    @ExceptionHandler(ConstraintViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleConstraintViolationException(ConstraintViolationException e) {
        return Result.error(400, e.getMessage());
    }

    /** 保留底层资源访问服务设定的 HTTP 状态和响应头，例如文件 Range 错误。 */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Result<Void>> handleResponseStatusException(ResponseStatusException e) {
        int status = e.getStatusCode().value();
        String message = e.getReason() == null ? "请求无法处理" : e.getReason();
        return ResponseEntity.status(e.getStatusCode()).headers(e.getHeaders())
                .body(Result.error(status, message));
    }

    /** 最后一层异常屏障：记录完整堆栈，但不向客户端泄漏内部实现细节。 */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<Void> handleException(Exception e) {
        log.error("系统异常", e);
        return Result.error(500, "服务器内部错误");
    }
}
