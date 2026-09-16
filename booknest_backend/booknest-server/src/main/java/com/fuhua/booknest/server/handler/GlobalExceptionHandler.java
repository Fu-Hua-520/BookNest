package com.fuhua.booknest.server.handler;

import com.fuhua.booknest.common.exception.BaseException;
import com.fuhua.booknest.common.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.sql.SQLIntegrityConstraintViolationException;

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
     * 处理 @RequestParam 参数缺失。
     * 典型场景：前端把参数放 JSON body、后端却用 @RequestParam 接收（契约不一致）。
     * 不处理的话 Spring 返回 {timestamp,status,error,path}，前端取不到 msg 只能提示
     * 「网络异常，请稍后重试」，真实原因被完全掩盖，排查成本极高。
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public Result exceptionHandler(MissingServletRequestParameterException ex) {
        String msg = "缺少请求参数：" + ex.getParameterName();
        log.error("参数缺失：{}", msg);
        return Result.error(msg);
    }

    /**
     * 处理参数类型转换失败（如把非数字字符串塞给 Integer）
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public Result exceptionHandler(MethodArgumentTypeMismatchException ex) {
        String msg = "参数类型不正确：" + ex.getName();
        log.error("参数类型错误：{}", msg);
        return Result.error(msg);
    }

    /**
     * 处理请求体解析失败（JSON 格式错误 / 字段类型对不上）
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public Result exceptionHandler(HttpMessageNotReadableException ex) {
        log.error("请求体解析失败：{}", ex.getMessage());
        return Result.error("请求体格式错误，无法解析");
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
            // 唯一键冲突提示中性化，不区分具体业务字段
            return Result.error("该值已存在或重复");
        } else {
            return Result.error("未知错误");
        }
    }
}
