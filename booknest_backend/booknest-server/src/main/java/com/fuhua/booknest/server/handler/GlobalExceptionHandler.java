package com.fuhua.booknest.server.handler;

import com.fuhua.booknest.common.constant.MessageConstant;
import com.fuhua.booknest.common.exception.BaseException;
import com.fuhua.booknest.common.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.sql.SQLIntegrityConstraintViolationException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 全局异常处理器，处理项目中抛出的业务异常
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * 捕获业务异常
     * @param ex
     * @return
     */
    @ExceptionHandler
    public Result exceptionHandler(BaseException ex) {
        log.error("异常信息：{}", ex.getMessage());
        return Result.error(ex.getMessage());
    }

    /**
     * 处理参数校验异常（@Valid 校验失败）
     * @param ex
     * @return
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result exceptionHandler(MethodArgumentNotValidException ex) {
        String msg = ex.getBindingResult().getFieldErrors().stream()
                .findFirst().map(FieldError::getDefaultMessage).orElse("参数不合法");
        log.error("参数校验异常：{}", msg);
        return Result.error(msg);
    }

    /**
     * 处理SQL异常
     * @param ex
     * @return
     */
    @ExceptionHandler
    public Result exceptionHandler(SQLIntegrityConstraintViolationException ex) {
        String message = ex.getMessage();
        log.error("SQL异常：{}", message);

        if (message.contains("Duplicate entry")) {
            // 用正则提取重复值，避免硬编码下标越界（非标准格式时安全降级）
            Pattern pattern = Pattern.compile("Duplicate entry '(.*?)'");
            Matcher matcher = pattern.matcher(message);
            if (matcher.find()) {
                String duplicateValue = matcher.group(1);
                return Result.error(duplicateValue + MessageConstant.ACCOUNT_ALREADY_EXISTS);
            }
            return Result.error("数据已存在或重复");
        } else {
            return Result.error("未知错误");
        }
    }
}
