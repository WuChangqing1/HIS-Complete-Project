package com.example.his.core;
public class ApiError extends RuntimeException {
 private final int status;
 public ApiError(int status,String message){super(message);this.status=status;}
 public int status(){return status;}
 public static ApiError bad(String m){return new ApiError(400,m);}
 public static ApiError conflict(String m){return new ApiError(409,m);}
 public static ApiError forbidden(){return new ApiError(403,"无权执行此操作");}
}
