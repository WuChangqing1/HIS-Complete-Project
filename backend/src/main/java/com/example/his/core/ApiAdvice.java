package com.example.his.core;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.dao.DataIntegrityViolationException;
import java.util.*;
@RestControllerAdvice
public class ApiAdvice {
 @ExceptionHandler(ApiError.class) public ResponseEntity<?> business(ApiError e){return ResponseEntity.status(e.status()).body(Map.of("error",e.getMessage()));}
 @ExceptionHandler({IllegalArgumentException.class,DataIntegrityViolationException.class}) public ResponseEntity<?> input(Exception e){return ResponseEntity.badRequest().body(Map.of("error","数据不符合约束，请检查输入或是否重复提交"));}
 @ExceptionHandler(Exception.class) public ResponseEntity<?> internal(Exception e){return ResponseEntity.internalServerError().body(Map.of("error","系统暂时无法完成请求，请稍后重试"));}
}
