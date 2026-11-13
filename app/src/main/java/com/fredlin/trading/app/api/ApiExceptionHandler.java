package com.fredlin.trading.app.api;

import com.fredlin.trading.app.engine.EngineService;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Dtos.Error> badArgument(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(new Dtos.Error(ex.getMessage()));
    }
    @ExceptionHandler({HttpMessageNotReadableException.class, MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class})
    public ResponseEntity<Dtos.Error> badFormat() {
        return ResponseEntity.badRequest().body(new Dtos.Error("请求格式或参数类型不合法"));
    }
    @ExceptionHandler(EngineService.Unavailable.class)
    public ResponseEntity<Dtos.Error> unavailable(EngineService.Unavailable ex) {
        return ResponseEntity.status(OrderController.status(ex.outcome())).body(new Dtos.Error(ex.getMessage()));
    }
}
