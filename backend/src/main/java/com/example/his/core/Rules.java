package com.example.his.core;
import java.math.BigDecimal;
import java.util.Set;
public final class Rules {
  private Rules() {}
  public static final Set<String> ROLES=Set.of("ADMIN","REGISTRAR","DOCTOR","EXAM","LAB","PROCEDURE","PHARMACIST");
  public static final Set<String> TYPES=Set.of("CHECK","LAB","PROCEDURE");
  public static final Set<String> PERIODS=Set.of("上午","下午");
  public static String tableFor(String type){return switch(type){case "CHECK"->"check_request";case "LAB"->"inspection_request";case "PROCEDURE"->"disposal_request";default->throw new IllegalArgumentException("非法项目类型");};}
  public static String prefixFor(String type){return switch(type){case "CHECK"->"check";case "LAB"->"inspection";case "PROCEDURE"->"disposal";default->throw new IllegalArgumentException("非法项目类型");};}
  public static BigDecimal registrationTotal(BigDecimal fee,boolean book){if(fee==null||fee.signum()<0)throw new IllegalArgumentException("挂号费非法");return fee.add(book?new BigDecimal("1.00"):BigDecimal.ZERO).setScale(2);}
  public static BigDecimal drugTotal(BigDecimal unit,int quantity){if(unit==null||unit.signum()<0||quantity<=0)throw new IllegalArgumentException("价格或数量非法");return unit.multiply(BigDecimal.valueOf(quantity)).setScale(2);}
  public static boolean canRefund(String category,String serviceStatus,String drugStatus){return switch(category){case "REGISTRATION","BOOKLET"->"WAITING".equals(serviceStatus);case "SERVICE"->"CREATED".equals(serviceStatus);case "DRUG"->"UNPAID".equals(drugStatus)||"PAID".equals(drugStatus)||"RETURNED".equals(drugStatus);default->false;};}
}
