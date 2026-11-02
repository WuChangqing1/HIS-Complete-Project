package com.example.his.auth;
import com.example.his.core.ApiError;
import javax.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;
@Service
public class AuthService implements ApplicationRunner {
 private final JdbcTemplate db;
 private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(11);
 private final SecureRandom random = new SecureRandom();
 @Value("${his.bootstrap-admin:admin}") private String adminName;
 @Value("${his.bootstrap-password:}") private String adminPassword;
 @Value("${his.demo-mode:false}") private boolean demo;
 @Value("${his.demo-password:}") private String demoPassword;
 public AuthService(JdbcTemplate db){this.db=db;}
 private String hash(String text){try{byte[] b=MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));return HexFormat.of().formatHex(b);}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
 public Map<String,Object> login(String username,String password){
   var users=db.queryForList("SELECT id,username,realname,role,password FROM employee WHERE username=? AND delmark=1",username);
   if(users.isEmpty()||!encoder.matches(password,String.valueOf(users.get(0).get("password"))))throw new ApiError(401,"用户名或密码错误");
   var u=users.get(0);byte[] b=new byte[32];random.nextBytes(b);String token=HexFormat.of().formatHex(b);
   db.update("INSERT INTO auth_session(employee_id,token_hash,expires_at) VALUES(?,?,?)",u.get("id"),hash(token),java.sql.Timestamp.valueOf(LocalDateTime.now().plusHours(12)));
   log(((Number)u.get("id")).longValue(),"LOGIN","employee:"+u.get("id"),"");
   return Map.of("token",token,"user",publicUser(u));
 }
 private Map<String,Object> publicUser(Map<String,Object> user){return Map.of("id",user.get("id"),"username",user.get("username"),"name",user.get("realname"),"role",user.get("role"));}
 public Map<String,Object> current(HttpServletRequest req){String h=req.getHeader("Authorization");if(h==null||!h.startsWith("Bearer "))throw new ApiError(401,"请先登录");
   var results=db.queryForList("SELECT e.id,e.username,e.realname,e.role FROM auth_session s JOIN employee e ON e.id=s.employee_id WHERE s.token_hash=? AND s.expires_at>CURRENT_TIMESTAMP AND e.delmark=1",hash(h.substring(7)));
   if(results.isEmpty())throw new ApiError(401,"登录已过期，请重新登录");return results.get(0);
 }
 public Map<String,Object> me(HttpServletRequest req){return publicUser(current(req));}
 public long require(HttpServletRequest req,String... roles){var u=current(req);String role=String.valueOf(u.get("role"));if(!role.equals("ADMIN")&&!Arrays.asList(roles).contains(role))throw ApiError.forbidden();return ((Number)u.get("id")).longValue();}
 public void logout(HttpServletRequest req){var u=current(req);String h=req.getHeader("Authorization");db.update("DELETE FROM auth_session WHERE token_hash=?",hash(h.substring(7)));log(((Number)u.get("id")).longValue(),"LOGOUT","session","");}
 public void log(long actor,String action,String target,String detail){db.update("INSERT INTO audit_log(actor_id,action,target,detail) VALUES(?,?,?,?)",actor,action,target,detail);}
 private void addUser(String name,String real,String role,long dept,long level,String password){
    if(db.queryForObject("SELECT COUNT(*) FROM employee WHERE username=?",Integer.class,name)>0)return;
    db.update("INSERT INTO employee(deptment_id,regist_level_id,realname,username,password,role) VALUES(?,?,?,?,?,?)",dept==0?null:dept,level==0?null:level,real,name,encoder.encode(password),role);
 }
 private long getId(String table,String key,String value){return db.queryForObject("SELECT id FROM "+table+" WHERE "+key+"=?",Long.class,value);}
 @Override public void run(ApplicationArguments args){
    if(adminPassword!=null&&!adminPassword.isBlank()) addUser(adminName,"系统管理员","ADMIN",0,0,adminPassword);
    if(!demo)return;
    if(demoPassword==null||demoPassword.isBlank())throw new IllegalStateException("DEMO_MODE=true requires DEMO_PASSWORD");
    seed("INSERT IGNORE INTO department(dept_code,dept_name) VALUES('IM','内科'),('SUR','外科'),('EX','检查科'),('LAB','检验科'),('PRO','处置科'),('PH','药房')");
    seed("INSERT IGNORE INTO regist_level(regist_code,regist_name,regist_fee,regist_quota) VALUES('NORMAL','普通号',10,40),('EXPERT','专家号',30,15)");
    seed("INSERT IGNORE INTO settle_category(settle_code,settle_name) VALUES('SELF','自费'),('INS','医保')");
    long dept=getId("department","dept_code","IM"),level=getId("regist_level","regist_code","NORMAL");
    addUser("registrar","挂号收费员","REGISTRAR",0,0,demoPassword);addUser("doctor","门诊医生","DOCTOR",dept,level,demoPassword);
    addUser("exam","检查医生","EXAM",getId("department","dept_code","EX"),0,demoPassword);
    addUser("lab","检验医生","LAB",getId("department","dept_code","LAB"),0,demoPassword);
    addUser("procedure","处置医生","PROCEDURE",getId("department","dept_code","PRO"),0,demoPassword);
    addUser("pharmacy","药房管理员","PHARMACIST",getId("department","dept_code","PH"),0,demoPassword);
    long doctor=getId("employee","username","doctor");
    for(int day=1;day<=7;day++)for(String period:List.of("上午","下午"))db.update("INSERT IGNORE INTO scheduling(employee_id,week_rule,noon,quota) VALUES(?,?,?,?)",doctor,day,period,40);
    seed("INSERT IGNORE INTO medical_technology(project_code,project_name,project_type,unit_price) VALUES('CHECK001','胸部X光','CHECK',85.00),('CHECK002','腹部超声','CHECK',120.00),('LAB001','血常规','LAB',25.00),('LAB002','血糖','LAB',12.00),('PRO001','伤口换药','PROCEDURE',35.00),('PRO002','清创处理','PROCEDURE',60.00)");
    seed("INSERT IGNORE INTO drug_info(drug_code,drug_name,unit,producer,drug_type,unit_price,stock) VALUES('DRUG001','示例药品A','盒','教学样例','口服',18.00,100),('DRUG002','示例药品B','盒','教学样例','外用',12.50,120)");
 }
 private void seed(String sql){db.update(sql);}
 @Scheduled(cron="0 0 3 * * *") public void purge(){db.update("DELETE FROM auth_session WHERE expires_at<=CURRENT_TIMESTAMP");}
}
