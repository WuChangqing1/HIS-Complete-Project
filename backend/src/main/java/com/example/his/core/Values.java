package com.example.his.core;
import java.math.*;
import java.time.*;
import java.util.*;
public final class Values {
 private Values(){}
 public static String str(Map<String,Object> m,String k){Object v=m.get(k);return v==null?"":v.toString().trim();}
 public static String required(Map<String,Object> m,String k,int max){String s=str(m,k);if(s.isEmpty()||s.length()>max)throw ApiError.bad(k+"不能为空或超过长度限制");return s;}
 public static String optional(Map<String,Object> m,String k,int max){String s=str(m,k);if(s.length()>max)throw ApiError.bad(k+"长度超限");return s;}
 public static long id(Map<String,Object> m,String k){try{long x=Long.parseLong(str(m,k));if(x<=0)throw new NumberFormatException();return x;}catch(Exception e){throw ApiError.bad(k+"不是有效编号");}}
 public static int positive(Map<String,Object> m,String k){try{int x=Integer.parseInt(str(m,k));if(x<=0||x>10000)throw new NumberFormatException();return x;}catch(Exception e){throw ApiError.bad(k+"数量非法");}}
 public static LocalDate date(Map<String,Object> m,String k){try{return LocalDate.parse(required(m,k,10));}catch(Exception e){throw ApiError.bad(k+"日期格式需要 YYYY-MM-DD");}}
 public static BigDecimal amount(Object v){try{BigDecimal a=new BigDecimal(v.toString());if(a.signum()<0||a.scale()>2)throw new Exception();return a.setScale(2);}catch(Exception e){throw ApiError.bad("金额格式非法");}}
}
