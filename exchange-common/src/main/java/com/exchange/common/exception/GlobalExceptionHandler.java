package com.exchange.common.exception;

import com.exchange.common.error.CommonErrorCode;
import com.exchange.common.error.ErrorCode;
import com.exchange.common.response.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.util.stream.Collectors;

/**
 * 全局异常处理器
 * 统一处理各种异常并返回标准格式的响应
 */
@RestControllerAdvice
public class GlobalExceptionHandler {
    
    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    
    /**
     * 处理业务异常
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Object>> handleBusinessException(BusinessException e) {
        logger.warn("业务异常: {}", e.getMessage());
        
        ApiResponse<Object> response = ApiResponse.error(e.getErrorCode(), e.getData());
        response.setTraceId(getTraceId());
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * 处理参数校验异常（@Valid）
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Object>> handleValidationException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));
        
        logger.warn("参数校验失败: {}", message);
        
        ApiResponse<Object> response = ApiResponse.error(CommonErrorCode.PARAMETER_ERROR, message);
        response.setTraceId(getTraceId());
        
        return ResponseEntity.badRequest().body(response);
    }
    
    /**
     * 处理绑定异常
     */
    @ExceptionHandler(BindException.class)
    public ResponseEntity<ApiResponse<Object>> handleBindException(BindException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));
        
        logger.warn("参数绑定失败: {}", message);
        
        ApiResponse<Object> response = ApiResponse.error(CommonErrorCode.PARAMETER_ERROR, message);
        response.setTraceId(getTraceId());
        
        return ResponseEntity.badRequest().body(response);
    }
    
    /**
     * 处理约束违反异常
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Object>> handleConstraintViolationException(ConstraintViolationException e) {
        String message = e.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.joining(", "));
        
        logger.warn("约束违反: {}", message);
        
        ApiResponse<Object> response = ApiResponse.error(CommonErrorCode.PARAMETER_ERROR, message);
        response.setTraceId(getTraceId());
        
        return ResponseEntity.badRequest().body(response);
    }
    
    /**
     * 处理方法参数类型不匹配异常
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Object>> handleMethodArgumentTypeMismatchException(MethodArgumentTypeMismatchException e) {
        String message = "参数类型不匹配: " + e.getName() + " 应为 " + e.getRequiredType().getSimpleName();
        
        logger.warn("参数类型不匹配: {}", message);
        
        ApiResponse<Object> response = ApiResponse.error(CommonErrorCode.PARAMETER_ERROR, message);
        response.setTraceId(getTraceId());
        
        return ResponseEntity.badRequest().body(response);
    }
    
    /**
     * 处理数字格式异常
     */
    @ExceptionHandler(NumberFormatException.class)
    public ResponseEntity<ApiResponse<Object>> handleNumberFormatException(NumberFormatException e) {
        logger.warn("数字格式错误: {}", e.getMessage());
        
        ApiResponse<Object> response = ApiResponse.error(CommonErrorCode.PARAMETER_ERROR, "数字格式错误");
        response.setTraceId(getTraceId());
        
        return ResponseEntity.badRequest().body(response);
    }
    
    /**
     * 处理非法参数异常
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Object>> handleIllegalArgumentException(IllegalArgumentException e) {
        logger.warn("非法参数: {}", e.getMessage());
        
        ApiResponse<Object> response = ApiResponse.error(CommonErrorCode.PARAMETER_ERROR, e.getMessage());
        response.setTraceId(getTraceId());
        
        return ResponseEntity.badRequest().body(response);
    }
    
    /**
     * 处理空指针异常
     */
    @ExceptionHandler(NullPointerException.class)
    public ResponseEntity<ApiResponse<Object>> handleNullPointerException(NullPointerException e) {
        logger.error("空指针异常", e);
        
        ApiResponse<Object> response = ApiResponse.error(CommonErrorCode.SYSTEM_ERROR, "系统内部错误");
        response.setTraceId(getTraceId());
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }
    
    /**
     * 处理运行时异常
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ApiResponse<Object>> handleRuntimeException(RuntimeException e) {
        logger.error("运行时异常", e);
        
        ApiResponse<Object> response = ApiResponse.error(CommonErrorCode.SYSTEM_ERROR, "系统内部错误");
        response.setTraceId(getTraceId());
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }
    
    /**
     * 处理所有其他异常
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Object>> handleException(Exception e) {
        logger.error("未处理的异常", e);
        
        ApiResponse<Object> response = ApiResponse.error(CommonErrorCode.SYSTEM_ERROR, "系统内部错误");
        response.setTraceId(getTraceId());
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }
    
    /**
     * 获取请求追踪ID
     */
    private String getTraceId() {
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                return request.getHeader("X-Trace-Id");
            }
        } catch (Exception e) {
            logger.warn("获取追踪ID失败", e);
        }
        return null;
    }
} 