package com.exchange.framework.web.exception;

import com.exchange.common.error.CommonErrorCode;
import com.exchange.common.exception.BusinessException;
import com.exchange.common.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.stream.Collectors;

/**
 * 全局异常处理器（迁自 exchange-common，包名已更新）。
 *
 * <p>由 {@link WebAutoConfiguration} 通过 {@code @Import} 注册为 Spring bean，
 * 业务模块无需额外配置，引入 {@code framework-web} 依赖即自动生效。
 *
 * <h3>TraceId 透传</h3>
 * <p>从请求头 {@code X-Trace-Id} 读取追踪 ID 并写入响应体，
 * 便于分布式链路追踪。如项目接入了 SkyWalking / Sleuth，可在此扩展。
 *
 * <h3>扩展方式</h3>
 * <p>业务模块若需处理自定义异常，直接在业务模块内新建 {@code @RestControllerAdvice}
 * 并实现对应 {@code @ExceptionHandler}，Spring MVC 会自动合并，无需修改此类。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /* ── 业务异常 ──────────────────────────────────────────────── */

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Object>> handleBusinessException(BusinessException e) {
        log.warn("业务异常: code={}, msg={}", e.getErrorCode().getCode(), e.getMessage());
        ApiResponse<Object> response = ApiResponse.error(e.getErrorCode(), e.getData());
        response.setTraceId(resolveTraceId());
        return ResponseEntity.ok(response);
    }

    /* ── 参数校验异常 ──────────────────────────────────────────── */

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Object>> handleValidationException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));
        log.warn("参数校验失败: {}", message);
        return badRequest(message);
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<ApiResponse<Object>> handleBindException(BindException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));
        log.warn("参数绑定失败: {}", message);
        return badRequest(message);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Object>> handleConstraintViolationException(ConstraintViolationException e) {
        String message = e.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.joining(", "));
        log.warn("约束违反: {}", message);
        return badRequest(message);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Object>> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        String message = "参数类型不匹配: " + e.getName()
                + " 应为 " + (e.getRequiredType() != null ? e.getRequiredType().getSimpleName() : "unknown");
        log.warn(message);
        return badRequest(message);
    }

    @ExceptionHandler({NumberFormatException.class, IllegalArgumentException.class})
    public ResponseEntity<ApiResponse<Object>> handleIllegalArgument(RuntimeException e) {
        log.warn("非法参数: {}", e.getMessage());
        return badRequest(e.getMessage());
    }

    /* ── 系统异常（兜底） ──────────────────────────────────────── */

    @ExceptionHandler(NullPointerException.class)
    public ResponseEntity<ApiResponse<Object>> handleNpe(NullPointerException e) {
        log.error("空指针异常", e);
        return internalError();
    }

    /**
     * 兜底处理器：捕获所有未显式处理的 RuntimeException / Exception。
     * 生产建议：在此处上报告警（Sentry / 飞书机器人等）。
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Object>> handleException(Exception e) {
        log.error("未处理的异常: {}", e.getMessage(), e);
        return internalError();
    }

    /* ── 私有工具方法 ──────────────────────────────────────────── */

    private ResponseEntity<ApiResponse<Object>> badRequest(String message) {
        ApiResponse<Object> resp = ApiResponse.error(CommonErrorCode.PARAMETER_ERROR, message);
        resp.setTraceId(resolveTraceId());
        return ResponseEntity.badRequest().body(resp);
    }

    private ResponseEntity<ApiResponse<Object>> internalError() {
        ApiResponse<Object> resp = ApiResponse.error(CommonErrorCode.SYSTEM_ERROR, "系统内部错误");
        resp.setTraceId(resolveTraceId());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(resp);
    }

    /**
     * 从请求头 {@code X-Trace-Id} 读取追踪 ID。
     * 不可用（非 Web 线程）时安全返回 null。
     */
    private String resolveTraceId() {
        try {
            ServletRequestAttributes attrs =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                HttpServletRequest req = attrs.getRequest();
                // 兼容多种链路追踪头字段
                String traceId = req.getHeader("X-Trace-Id");
                if (traceId == null) traceId = req.getHeader("X-B3-TraceId");
                if (traceId == null) traceId = req.getHeader("sw8");
                return traceId;
            }
        } catch (Exception ignored) {
            log.trace("TraceId 不可用");
        }
        return null;
    }
}
