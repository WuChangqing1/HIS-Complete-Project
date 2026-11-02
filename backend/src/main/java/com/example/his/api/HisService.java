package com.example.his.api;
import com.example.his.auth.AuthService;
import com.example.his.core.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.*;
import java.util.*;
@Service
public class HisService {
 private final JdbcTemplate db; private final AuthService auth; private final CryptoService crypto;
 public HisService(JdbcTemplate db,AuthService auth,CryptoService crypto){this.db=db;this.auth=auth;this.crypto=crypto;}
 private long insert(String sql,Object... args){KeyHolder kh=new GeneratedKeyHolder();db.update(c->{PreparedStatement p=c.prepareStatement(sql,Statement.RETURN_GENERATED_KEYS);for(int i=0;i<args.length;i++)p.setObject(i+1,args[i]);return p;},kh);if(kh.getKey()==null)throw new IllegalStateException("主键生成失败");return kh.getKey().longValue();}
 private Map<String,Object> one(String sql,Object... args){var rows=db.queryForList(sql,args);if(rows.isEmpty())throw new ApiError(404,"记录不存在");return rows.get(0);}
 private Map<String,Object> locked(String table,long id){return one("SELECT * FROM "+table+" WHERE id=? FOR UPDATE",id);}
 private String requestId(Map<String,Object> p){String id=Values.optional(p,"requestId",80);return id.isBlank()?UUID.randomUUID().toString():id;}
 private long num(Map<String,Object> m,String key){return ((Number)m.get(key)).longValue();}
 private String role(Map<String,Object> user){return String.valueOf(user.get("role"));}
 private void checkDoctor(long regId,Map<String,Object> user){var reg=one("SELECT employee_id,visit_state FROM register WHERE id=?",regId);if(!"ADMIN".equals(role(user))&&num(reg,"employee_id")!=num(user,"id"))throw ApiError.forbidden();if("CANCELLED".equals(reg.get("visit_state")))throw ApiError.conflict("挂号已取消");}
 private void checkRole(Map<String,Object> user,String role){if(!"ADMIN".equals(role(user))&&!role.equals(role(user)))throw ApiError.forbidden();}
 public List<Map<String,Object>> doctors(){return db.queryForList("SELECT e.id,e.realname,e.deptment_id,e.regist_level_id,d.dept_name FROM employee e LEFT JOIN department d ON e.deptment_id=d.id WHERE e.delmark=1 AND e.role='DOCTOR' ORDER BY e.id");}
 public List<Map<String,Object>> schedules(String date,String period){LocalDate d;try{d=LocalDate.parse(date);}catch(Exception e){throw ApiError.bad("日期格式错误");}if(!Rules.PERIODS.contains(period))throw ApiError.bad("午别错误");return db.queryForList("SELECT s.id,s.employee_id,e.realname,d.dept_name,s.noon,s.quota,LEAST(s.quota,l.regist_quota) AS capacity,(SELECT COUNT(*) FROM register r WHERE r.employee_id=s.employee_id AND r.visit_date=? AND r.noon=s.noon AND r.visit_state<>'CANCELLED') AS used FROM scheduling s JOIN employee e ON e.id=s.employee_id JOIN department d ON d.id=e.deptment_id JOIN regist_level l ON l.id=e.regist_level_id WHERE s.delmark=1 AND e.delmark=1 AND s.week_rule=? AND s.noon=? ORDER BY e.realname",java.sql.Date.valueOf(d),d.getDayOfWeek().getValue(),period);}
 public List<Map<String,Object>> patients(String q){return db.queryForList("SELECT id,case_number,real_name,gender,birthdate,phone,home_address,created_at FROM patient WHERE case_number LIKE ? OR real_name LIKE ? ORDER BY id DESC LIMIT 150","%"+q+"%","%"+q+"%");}
 @Transactional public Object createPatient(Map<String,Object> p,long actor){String name=Values.required(p,"name",80),gender=Values.required(p,"gender",8);if(!Set.of("男","女","未知").contains(gender))throw ApiError.bad("性别只能为男、女、未知");String requestKey=requestId(p);var existing=db.queryForList("SELECT id,case_number,real_name,gender,birthdate FROM patient WHERE client_request_id=?",requestKey);if(!existing.isEmpty())return existing.get(0);
  String identity=Values.optional(p,"identity",18),birth=Values.optional(p,"birthDate",10),phone=Values.optional(p,"phone",32),address=Values.optional(p,"address",255);
  if(!identity.isBlank()&&!identity.matches("[0-9]{17}[0-9Xx]"))throw ApiError.bad("身份证号格式非法");
  if(!identity.isBlank()&&db.queryForObject("SELECT COUNT(*) FROM patient WHERE card_hash=?",Integer.class,crypto.fingerprint(identity))>0)throw ApiError.conflict("该身份证号已存在患者档案");
  java.sql.Date bd=null;if(!birth.isBlank()){try{bd=java.sql.Date.valueOf(LocalDate.parse(birth));}catch(Exception e){throw ApiError.bad("出生日期格式错误");}}
  long id=insert("INSERT INTO patient(client_request_id,real_name,gender,card_number,card_hash,birthdate,home_address,phone) VALUES(?,?,?,?,?,?,?,?)",requestKey,name,gender,identity.isBlank()?null:crypto.encrypt(identity),crypto.fingerprint(identity),bd,address,phone);
  db.update("UPDATE patient SET case_number=CONCAT('P',LPAD(id,8,'0')) WHERE id=?",id);auth.log(actor,"PATIENT_CREATE","patient:"+id,"");return one("SELECT id,case_number,real_name,gender,birthdate FROM patient WHERE id=?",id);
 }
 @Transactional public Object updatePatient(long id,Map<String,Object> p,long actor){locked("patient",id);
  String name=Values.required(p,"name",80),gender=Values.required(p,"gender",8);if(!Set.of("男","女","未知").contains(gender))throw ApiError.bad("性别无效");
  String birth=Values.optional(p,"birthDate",10),phone=Values.optional(p,"phone",32),address=Values.optional(p,"address",255);
  java.sql.Date bd=null;if(!birth.isBlank()){try{bd=java.sql.Date.valueOf(LocalDate.parse(birth));}catch(Exception e){throw ApiError.bad("出生日期格式错误");}}
  db.update("UPDATE patient SET real_name=?,gender=?,birthdate=?,phone=?,home_address=? WHERE id=?",name,gender,bd,phone,address,id);
  auth.log(actor,"PATIENT_UPDATE","patient:"+id,"");return Map.of("ok",true);
 }
 public List<Map<String,Object>> registrations(String q,Map<String,Object> user){String sql="SELECT r.id,r.patient_id,r.employee_id,r.visit_date,r.noon,r.visit_state,r.regist_money,r.created_at,p.case_number,p.real_name,p.gender,e.realname AS doctor,d.dept_name FROM register r JOIN patient p ON p.id=r.patient_id JOIN employee e ON e.id=r.employee_id JOIN department d ON d.id=r.deptment_id WHERE (p.case_number LIKE ? OR p.real_name LIKE ?)";
  if("DOCTOR".equals(role(user)))return db.queryForList(sql+" AND r.employee_id=? ORDER BY CASE WHEN r.visit_state='WAITING' THEN 0 WHEN r.visit_state='IN_PROGRESS' THEN 1 ELSE 2 END, r.created_at ASC LIMIT 150","%"+q+"%","%"+q+"%",user.get("id"));return db.queryForList(sql+" ORDER BY r.created_at DESC LIMIT 150","%"+q+"%","%"+q+"%");
 }
 @Transactional public Object register(Map<String,Object> p,long actor){long patientId=Values.id(p,"patientId"),doctorId=Values.id(p,"doctorId"),levelId=Values.id(p,"levelId"),settleId=Values.id(p,"settleId");LocalDate date=Values.date(p,"date");String period=Values.required(p,"period",8);if(!Rules.PERIODS.contains(period)||date.isBefore(LocalDate.now()))throw ApiError.bad("就诊日期或午别非法");
  one("SELECT id FROM patient WHERE id=?",patientId);one("SELECT id FROM settle_category WHERE id=? AND delmark=1",settleId);
  var doctor=one("SELECT id,deptment_id,regist_level_id FROM employee WHERE id=? AND role='DOCTOR' AND delmark=1",doctorId);
  if(num(doctor,"regist_level_id")!=levelId)throw ApiError.bad("号别与医生不匹配");var lev=one("SELECT regist_fee,regist_quota FROM regist_level WHERE id=? AND delmark=1",levelId);
  var schedule=one("SELECT id,quota FROM scheduling WHERE employee_id=? AND week_rule=? AND noon=? AND delmark=1 FOR UPDATE",doctorId,date.getDayOfWeek().getValue(),period);
  int used=db.queryForObject("SELECT COUNT(*) FROM register WHERE employee_id=? AND visit_date=? AND noon=? AND visit_state<>'CANCELLED'",Integer.class,doctorId,java.sql.Date.valueOf(date),period);
  int quota=Math.min(((Number)schedule.get("quota")).intValue(),((Number)lev.get("regist_quota")).intValue());if(used>=quota)throw ApiError.conflict("该医生当前时段号源已满");
  String requestKey=requestId(p);var existing=db.queryForList("SELECT id,regist_money FROM register WHERE client_request_id=?",requestKey);if(!existing.isEmpty())return Map.of("id",existing.get(0).get("id"),"total",existing.get(0).get("regist_money"),"state","EXISTING");
  boolean book=Boolean.TRUE.equals(p.get("book"));BigDecimal fee=Rules.registrationTotal((BigDecimal)lev.get("regist_fee"),book);
  long id=insert("INSERT INTO register(client_request_id,patient_id,employee_id,deptment_id,regist_level_id,settle_category_id,visit_date,noon,is_book,regist_money) VALUES(?,?,?,?,?,?,?,?,?,?)",requestKey,patientId,doctorId,doctor.get("deptment_id"),levelId,settleId,java.sql.Date.valueOf(date),period,book?1:0,fee);
  long charge=insert("INSERT INTO charge_item(register_id,category,ref_id,description,amount,status,paid_at) VALUES(?,'REGISTRATION',?,'门诊挂号',?,'PAID',CURRENT_TIMESTAMP)",id,id,fee);
  db.update("INSERT INTO charge_transaction(item_id,direction,amount,method,operator_id) VALUES(?,'PAY',?,'现金',?)",charge,fee,actor);
  auth.log(actor,"REGISTER","register:"+id,"");return Map.of("id",id,"total",fee,"state","WAITING");
 }
 @Transactional public Object cancelRegistration(long id,long actor){var r=locked("register",id);if(!"WAITING".equals(r.get("visit_state")))throw ApiError.conflict("仅未看诊挂号可退号");
  db.update("UPDATE register SET visit_state='CANCELLED' WHERE id=?",id);
  var c=one("SELECT id,amount FROM charge_item WHERE category='REGISTRATION' AND ref_id=? FOR UPDATE",id);
  db.update("UPDATE charge_item SET status='REFUNDED' WHERE id=?",c.get("id"));db.update("INSERT INTO charge_transaction(item_id,direction,amount,method,operator_id) VALUES(?,'REFUND',?,'现金',?)",c.get("id"),c.get("amount"),actor);
  auth.log(actor,"REGISTER_CANCEL","register:"+id,"");return Map.of("ok",true);
 }
 public Object record(long id,Map<String,Object> user){checkDoctor(id,user);var rows=db.queryForList("SELECT * FROM medical_record WHERE register_id=?",id);if(rows.isEmpty())return Collections.emptyMap();var out=rows.get(0);for(String k:List.of("chief_complaint","present_illness","treatment_history","past_history","allergies","physical_exam","preliminary_diagnosis","suggestions","final_diagnosis","treatment_plan"))out.put(k,crypto.decrypt(out.get(k)));return out;}
 @Transactional public Object saveRecord(long id,Map<String,Object> p,long actor,Map<String,Object> user){checkDoctor(id,user);var r=locked("register",id);if("COMPLETED".equals(r.get("visit_state")))throw ApiError.conflict("已诊毕病历不能直接修改");
  if("WAITING".equals(r.get("visit_state")))db.update("UPDATE register SET visit_state='IN_PROGRESS' WHERE id=?",id);
  db.update("INSERT IGNORE INTO medical_record(register_id) VALUES(?)",id);
  db.update("UPDATE medical_record SET chief_complaint=?,present_illness=?,treatment_history=?,past_history=?,allergies=?,physical_exam=?,preliminary_diagnosis=?,suggestions=? WHERE register_id=?",
    crypto.encrypt(Values.optional(p,"complaint",5000)),crypto.encrypt(Values.optional(p,"illness",5000)),crypto.encrypt(Values.optional(p,"treatment",5000)),crypto.encrypt(Values.optional(p,"past",5000)),crypto.encrypt(Values.optional(p,"allergies",5000)),crypto.encrypt(Values.optional(p,"exam",5000)),crypto.encrypt(Values.optional(p,"preliminary",500)),crypto.encrypt(Values.optional(p,"suggestions",5000)),id);
  auth.log(actor,"RECORD_SAVE","register:"+id,"");return Map.of("ok",true);
 }
 @Transactional public Object finalizeRecord(long id,Map<String,Object> p,long actor,Map<String,Object> user){checkDoctor(id,user);var r=locked("register",id);if(!"IN_PROGRESS".equals(r.get("visit_state")))throw ApiError.conflict("需先创建病历，且未诊毕");
  String diagnosis=Values.required(p,"diagnosis",500),plan=Values.required(p,"plan",5000);
  db.update("UPDATE medical_record SET final_diagnosis=?,treatment_plan=? WHERE register_id=?",crypto.encrypt(diagnosis),crypto.encrypt(plan),id);
  db.update("UPDATE register SET visit_state='COMPLETED' WHERE id=?",id);auth.log(actor,"RECORD_FINAL","register:"+id,"");return Map.of("ok",true);
 }
 private List<Map<String,Object>> decryptResults(List<Map<String,Object>> rows){for(var row:rows)row.put("result",crypto.decrypt(row.get("result")));return rows;}
 private void serviceAccess(String type,Map<String,Object> user){Rules.tableFor(type);checkRole(user,switch(type){case "CHECK"->"EXAM";case "LAB"->"LAB";default->"PROCEDURE";});}
 public Object services(String type,String q,Map<String,Object> user){String table=Rules.tableFor(type),prefix=Rules.prefixFor(type);
  String sql="SELECT a.id,a.register_id,a."+prefix+"_state AS status,a."+prefix+"_result AS result,a."+prefix+"_position AS position,a.creation_time,p.case_number,p.real_name,t.project_name,t.unit_price,e.realname AS doctor,c.status AS payment_status FROM "+table+" a JOIN register r ON r.id=a.register_id JOIN patient p ON p.id=r.patient_id JOIN employee e ON e.id=r.employee_id JOIN medical_technology t ON t.id=a.medical_technology_id LEFT JOIN charge_item c ON c.ref_id=a.id AND c.category='"+type+"' WHERE (p.real_name LIKE ? OR p.case_number LIKE ?)";
  if("DOCTOR".equals(role(user)))return decryptResults(db.queryForList(sql+" AND r.employee_id=? ORDER BY a.id DESC LIMIT 200","%"+q+"%","%"+q+"%",user.get("id")));
  if(!"ADMIN".equals(role(user)))serviceAccess(type,user);
  return decryptResults(db.queryForList(sql+" ORDER BY a.id DESC LIMIT 200","%"+q+"%","%"+q+"%"));
 }
 @Transactional public Object addService(Map<String,Object> p,long actor,Map<String,Object> user){String type=Values.required(p,"type",16),table=Rules.tableFor(type),prefix=Rules.prefixFor(type);long regId=Values.id(p,"registrationId"),projectId=Values.id(p,"projectId");checkDoctor(regId,user);
  var r=locked("register",regId);String state=String.valueOf(r.get("visit_state"));if("WAITING".equals(state)||"CANCELLED".equals(state))throw ApiError.conflict("患者未开始就诊或已退号");
  if("PROCEDURE".equals(type)&&!"COMPLETED".equals(state))throw ApiError.conflict("处置申请需先门诊确诊");
  var project=one("SELECT * FROM medical_technology WHERE id=? AND project_type=? AND enabled=1",projectId,type);
  String requestKey=requestId(p);var existing=db.queryForList("SELECT id FROM "+table+" WHERE client_request_id=?",requestKey);if(!existing.isEmpty())return Map.of("id",existing.get(0).get("id"),"state","EXISTING");
  String purpose=Values.optional(p,"purpose",512),position=Values.optional(p,"position",255),note=Values.optional(p,"note",512);
  long id=insert("INSERT INTO "+table+"(client_request_id,register_id,medical_technology_id,"+prefix+"_info,"+prefix+"_position,"+prefix+"_remark) VALUES(?,?,?,?,?,?)",requestKey,regId,projectId,purpose,position,note);
  insert("INSERT INTO charge_item(register_id,category,ref_id,description,amount) VALUES(?,?,?,?,?)",regId,type,id,project.get("project_name"),project.get("unit_price"));
  auth.log(actor,"SERVICE_CREATE",type+":"+id,"");return Map.of("id",id,"state","CREATED");
 }
 @Transactional public Object startService(String type,long id,Map<String,Object> user){serviceAccess(type,user);String table=Rules.tableFor(type),prefix=Rules.prefixFor(type);var request=locked(table,id);String state=String.valueOf(request.get(prefix+"_state"));
  String next;if("CREATED".equals(state)){
   var charge=one("SELECT status FROM charge_item WHERE category=? AND ref_id=?",type,id);if(!"PAID".equals(charge.get("status")))throw ApiError.conflict("缴费后才能登记医技项目");next="REGISTERED";
  }else if("REGISTERED".equals(state)){next="IN_PROGRESS";}else throw ApiError.conflict("项目状态不允许重复操作");
  db.update("UPDATE "+table+" SET "+prefix+"_state=?,"+prefix+"_employee_id=? WHERE id=?",next,user.get("id"),id);
  auth.log(num(user,"id"),"SERVICE_"+next,type+":"+id,"");return Map.of("state",next);
 }
 @Transactional public Object completeService(String type,long id,Map<String,Object> p,Map<String,Object> user){serviceAccess(type,user);String table=Rules.tableFor(type),prefix=Rules.prefixFor(type);var request=locked(table,id);
  if(!"IN_PROGRESS".equals(request.get(prefix+"_state")))throw ApiError.conflict("项目未开始执行");String result=Values.required(p,"result",10000);
  db.update("UPDATE "+table+" SET "+prefix+"_state='COMPLETED',"+prefix+"_result=?,"+prefix+"_time=CURRENT_TIMESTAMP,input"+prefix+"_employee_id=? WHERE id=?",crypto.encrypt(result),user.get("id"),id);
  auth.log(num(user,"id"),"SERVICE_COMPLETE",type+":"+id,"");return Map.of("ok",true);
 }
 @Transactional public Object cancelService(String type,long id,Map<String,Object> user){checkRole(user,"DOCTOR");String table=Rules.tableFor(type),prefix=Rules.prefixFor(type);var request=locked(table,id);checkDoctor(num(request,"register_id"),user);
  if(!"CREATED".equals(request.get(prefix+"_state")))throw ApiError.conflict("仅未登记项目可撤销");var c=one("SELECT id,status FROM charge_item WHERE category=? AND ref_id=? FOR UPDATE",type,id);
  if(!Set.of("UNPAID","REFUNDED").contains(c.get("status")))throw ApiError.conflict("需先办理退费");
  db.update("UPDATE "+table+" SET "+prefix+"_state='CANCELLED' WHERE id=?",id);
  if("UNPAID".equals(c.get("status")))db.update("UPDATE charge_item SET status='CANCELLED' WHERE id=?",c.get("id"));
  auth.log(num(user,"id"),"SERVICE_CANCEL",type+":"+id,"");return Map.of("ok",true);
 }
 @Transactional public Object addPrescription(Map<String,Object> p,long actor,Map<String,Object> user){long regId=Values.id(p,"registrationId");checkDoctor(regId,user);var reg=locked("register",regId);if(!"COMPLETED".equals(reg.get("visit_state")))throw ApiError.conflict("确诊后才能开立处方");
  Object raw=p.get("items");if(!(raw instanceof List<?> items)||items.isEmpty()||items.size()>30)throw ApiError.bad("处方药品为1至30项");
  String requestKey=requestId(p);var existing=db.queryForList("SELECT id FROM prescription WHERE client_request_id=?",requestKey);if(!existing.isEmpty())return Map.of("id",existing.get(0).get("id"),"ok",true,"existing",true);
  long presId=insert("INSERT INTO prescription(client_request_id,register_id,employee_id) VALUES(?,?,?)",requestKey,regId,actor);
  for(Object o:items){if(!(o instanceof Map<?,?> src))throw ApiError.bad("药品内容格式错误");Map<String,Object> item=new HashMap<>();src.forEach((k,v)->item.put(String.valueOf(k),v));
   long drugId=Values.id(item,"drugId");int quantity=Values.positive(item,"quantity"),days=Values.positive(item,"days");String dosage=Values.required(item,"dosage",100),frequency=Values.required(item,"frequency",100),advice=Values.optional(item,"advice",255);
   var drug=one("SELECT drug_name,unit_price FROM drug_info WHERE id=? AND enabled=1",drugId);BigDecimal total=Rules.drugTotal((BigDecimal)drug.get("unit_price"),quantity);
   long itemId=insert("INSERT INTO prescription_item(prescription_id,drug_id,quantity,unit_price,dosage,frequency,use_days,advice) VALUES(?,?,?,?,?,?,?,?)",presId,drugId,quantity,drug.get("unit_price"),dosage,frequency,days,advice);
   insert("INSERT INTO charge_item(register_id,category,ref_id,description,amount) VALUES(?,'DRUG',?,?,?)",regId,itemId,drug.get("drug_name"),total);
  }
  auth.log(actor,"PRESCRIPTION_CREATE","prescription:"+presId,"");return Map.of("id",presId,"ok",true);
 }
 public Object prescriptions(String q,Map<String,Object> user){String sql="SELECT i.id AS item_id,i.quantity,i.dosage,i.frequency,i.use_days,i.advice,i.status,i.unit_price,d.drug_name,d.drug_code,d.stock,p.id AS prescription_id,p.register_id,pat.real_name,pat.case_number,c.status AS payment_status FROM prescription_item i JOIN prescription p ON p.id=i.prescription_id JOIN drug_info d ON d.id=i.drug_id JOIN register r ON r.id=p.register_id JOIN patient pat ON pat.id=r.patient_id LEFT JOIN charge_item c ON c.category='DRUG' AND c.ref_id=i.id WHERE (pat.real_name LIKE ? OR pat.case_number LIKE ?)";
  if("DOCTOR".equals(role(user)))return db.queryForList(sql+" AND r.employee_id=? ORDER BY i.id DESC LIMIT 200","%"+q+"%","%"+q+"%",user.get("id"));
  return db.queryForList(sql+" ORDER BY i.id DESC LIMIT 200","%"+q+"%","%"+q+"%");
 }
 @Transactional public Object dispense(long id,long actor){var item=locked("prescription_item",id);if(!"PAID".equals(item.get("status")))throw ApiError.conflict("仅已缴费且未发放的药品可发药");long drugId=num(item,"drug_id"),qty=num(item,"quantity");
  if(db.update("UPDATE drug_info SET stock=stock-? WHERE id=? AND stock>=?",qty,drugId,qty)!=1)throw ApiError.conflict("库存不足，无法发药");
  db.update("UPDATE prescription_item SET status='DISPENSED' WHERE id=?",id);db.update("INSERT INTO drug_stock_transaction(drug_id,delta,reason,ref_id,operator_id) VALUES(?,?,'DISPENSE',?,?)",drugId,-qty,id,actor);
  auth.log(actor,"DISPENSE","item:"+id,"");return Map.of("ok",true);
 }
 @Transactional public Object returnDrug(long id,long actor){var item=locked("prescription_item",id);if(!"DISPENSED".equals(item.get("status")))throw ApiError.conflict("仅已发放药品可退药");long drugId=num(item,"drug_id"),qty=num(item,"quantity");
  db.update("UPDATE drug_info SET stock=stock+? WHERE id=?",qty,drugId);db.update("UPDATE prescription_item SET status='RETURNED' WHERE id=?",id);
  db.update("INSERT INTO drug_stock_transaction(drug_id,delta,reason,ref_id,operator_id) VALUES(?,?,'RETURN',?,?)",drugId,qty,id,actor);
  auth.log(actor,"DRUG_RETURN","item:"+id,"");return Map.of("ok",true);
 }
 @Transactional public Object addDrug(Map<String,Object> p,long actor){String code=Values.required(p,"code",32),name=Values.required(p,"name",120),unit=Values.required(p,"unit",30),producer=Values.optional(p,"producer",120);
  BigDecimal price=Values.amount(Values.required(p,"price",30));int stock=0;
  if(p.containsKey("stock"))try{stock=Integer.parseInt(Values.str(p,"stock"));if(stock<0||stock>1000000)throw new NumberFormatException();}catch(Exception e){throw ApiError.bad("库存数量必须为非负整数");}
  long id=insert("INSERT INTO drug_info(drug_code,drug_name,unit,producer,unit_price,stock) VALUES(?,?,?,?,?,?)",code,name,unit,producer,price,stock);
  if(stock>0)db.update("INSERT INTO drug_stock_transaction(drug_id,delta,reason,operator_id) VALUES(?,?,'INITIAL',?)",id,stock,actor);
  auth.log(actor,"DRUG_CREATE","drug:"+id,"");return Map.of("id",id);
 }
 @Transactional public Object updateDrug(long id,Map<String,Object> p,long actor){locked("drug_info",id);
  String name=Values.required(p,"name",120),unit=Values.required(p,"unit",30),producer=Values.optional(p,"producer",120);BigDecimal price=Values.amount(Values.required(p,"price",30));
  db.update("UPDATE drug_info SET drug_name=?,unit=?,producer=?,unit_price=? WHERE id=?",name,unit,producer,price,id);
  auth.log(actor,"DRUG_UPDATE","drug:"+id,"");return Map.of("ok",true);
 }
 @Transactional public Object adjustStock(long drugId,Map<String,Object> p,long actor){var drug=locked("drug_info",drugId);int delta;try{delta=Integer.parseInt(Values.required(p,"delta",10));}catch(Exception e){throw ApiError.bad("库存变化数量非法");}if(delta==0||Math.abs((long)delta)>100000)throw ApiError.bad("库存变化数量非法");
  if(db.update("UPDATE drug_info SET stock=stock+? WHERE id=? AND stock+?>=0",delta,drugId,delta)!=1)throw ApiError.conflict("库存不能为负数");
  db.update("INSERT INTO drug_stock_transaction(drug_id,delta,reason,operator_id) VALUES(?,?,'ADJUST',?)",drugId,delta,actor);auth.log(actor,"STOCK_ADJUST","drug:"+drugId,"delta="+delta);return Map.of("ok",true);
 }
 public Object charges(String q,Map<String,Object> user){String sql="SELECT c.id,c.register_id,c.category,c.ref_id,c.description,c.amount,c.status,c.created_at,c.paid_at,p.real_name,p.case_number FROM charge_item c JOIN register r ON r.id=c.register_id JOIN patient p ON p.id=r.patient_id WHERE (p.real_name LIKE ? OR p.case_number LIKE ?)";
  if("DOCTOR".equals(role(user)))return db.queryForList(sql+" AND r.employee_id=? ORDER BY COALESCE(c.paid_at,c.created_at) DESC,c.id DESC LIMIT 250","%"+q+"%","%"+q+"%",user.get("id"));
  return db.queryForList(sql+" ORDER BY COALESCE(c.paid_at,c.created_at) DESC,c.id DESC LIMIT 250","%"+q+"%","%"+q+"%");
 }
 private String method(Map<String,Object> p){String method=Values.required(p,"method",30);if(!Set.of("现金","银行卡","微信","支付宝","医保").contains(method))throw ApiError.bad("支付方式无效");return method;}
 @Transactional public Object pay(long id,Map<String,Object> p,long actor){String method=method(p);var c=locked("charge_item",id);if(!"UNPAID".equals(c.get("status")))throw ApiError.conflict("此收费项目不可重复缴费");
  BigDecimal amount=(BigDecimal)c.get("amount");BigDecimal received=p.containsKey("received")?Values.amount(p.get("received")):amount;
  if(received.compareTo(amount)<0)throw ApiError.bad("实收金额不能低于应收金额");BigDecimal change=received.subtract(amount);
  db.update("UPDATE charge_item SET status='PAID',paid_at=CURRENT_TIMESTAMP WHERE id=?",id);
  if("DRUG".equals(c.get("category")))db.update("UPDATE prescription_item SET status='PAID' WHERE id=? AND status='UNPAID'",c.get("ref_id"));
  db.update("INSERT INTO charge_transaction(item_id,direction,amount,received_amount,change_amount,method,operator_id) VALUES(?,'PAY',?,?,?,?,?)",id,amount,received,change,method,actor);
  auth.log(actor,"CHARGE_PAY","charge:"+id,"");return Map.of("ok",true,"received",received,"change",change);
 }
 @Transactional public Object refund(long id,Map<String,Object> p,long actor){String method=method(p);var c=locked("charge_item",id);if(!"PAID".equals(c.get("status")))throw ApiError.conflict("仅已缴费且未退费的项目可退费");
  String cat=String.valueOf(c.get("category"));long ref=num(c,"ref_id");
  if("REGISTRATION".equals(cat)){var r=locked("register",ref);if(!"CANCELLED".equals(r.get("visit_state")))throw ApiError.conflict("请先办理退号");throw ApiError.conflict("退号费用已由系统自动退回");}
  if(Rules.TYPES.contains(cat)){String type=cat;String table=Rules.tableFor(type),prefix=Rules.prefixFor(type);var item=locked(table,ref);
   if(!"CREATED".equals(item.get(prefix+"_state")))throw ApiError.conflict("已登记或已执行项目无法退费");
  }else if("DRUG".equals(cat)){var item=locked("prescription_item",ref);if(!Set.of("PAID","RETURNED").contains(item.get("status")))throw ApiError.conflict("已发药品须先办理退药");db.update("UPDATE prescription_item SET status='REFUNDED' WHERE id=?",ref);}else throw ApiError.bad("未知费用类型");
  db.update("UPDATE charge_item SET status='REFUNDED' WHERE id=?",id);db.update("INSERT INTO charge_transaction(item_id,direction,amount,method,operator_id) VALUES(?,'REFUND',?,?,?)",id,c.get("amount"),method,actor);
  auth.log(actor,"CHARGE_REFUND","charge:"+id,"");return Map.of("ok",true);
 }
 public Object dashboard(){return Map.of("waiting",db.queryForObject("SELECT COUNT(*) FROM register WHERE visit_date=CURRENT_DATE AND visit_state='WAITING'",Integer.class),"visits",db.queryForObject("SELECT COUNT(*) FROM register WHERE visit_date=CURRENT_DATE AND visit_state<>'CANCELLED'",Integer.class),"unpaid",db.queryForObject("SELECT COUNT(*) FROM charge_item WHERE status='UNPAID'",Integer.class),"prescriptions",db.queryForObject("SELECT COUNT(*) FROM prescription_item WHERE status='PAID'",Integer.class),"completed",db.queryForObject("SELECT COUNT(*) FROM register WHERE visit_date=CURRENT_DATE AND visit_state='COMPLETED'",Integer.class));}
 public Object audit(){return db.queryForList("SELECT a.id,a.action,a.target,a.detail,a.created_at,e.realname AS operator FROM audit_log a LEFT JOIN employee e ON e.id=a.actor_id ORDER BY a.id DESC LIMIT 250");}
}
