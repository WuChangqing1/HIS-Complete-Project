package com.example.his.api;
import com.example.his.auth.AuthService;
import com.example.his.core.*;
import com.example.his.catalog.CatalogMapper;
import javax.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController @RequestMapping("/api")
public class ApiController {
 private final AuthService auth; private final HisService service; private final CatalogMapper catalog;
 public ApiController(AuthService a,HisService s,CatalogMapper c){auth=a;service=s;catalog=c;}
 @PostMapping("/auth/login") public Object login(@RequestBody Map<String,Object> p){return auth.login(Values.required(p,"username",64),Values.required(p,"password",256));}
 @GetMapping("/auth/guest") public Object guestStatus(){return Map.of("enabled",auth.guestEnabled());}
 @PostMapping("/auth/guest") public Object guest(javax.servlet.http.HttpServletResponse response){response.setHeader("Cache-Control","no-store");return auth.enterGuest();}
 @GetMapping("/auth/me") public Object me(HttpServletRequest r){return auth.me(r);}
 @PostMapping("/auth/logout") public Object logout(HttpServletRequest r){auth.logout(r);return Map.of("ok",true);}
 @GetMapping("/meta") public Object meta(HttpServletRequest r){auth.current(r);return Map.of("departments",catalog.departments(),"levels",catalog.levels(),"settlements",catalog.settlement(),"doctors",service.doctors());}
 @GetMapping("/schedules") public Object schedules(HttpServletRequest r,@RequestParam String date,@RequestParam(defaultValue="上午")String period){auth.require(r,"REGISTRAR","DOCTOR");return service.schedules(date,period);}
 @GetMapping("/patients") public Object patients(HttpServletRequest r,@RequestParam(defaultValue="")String q){auth.current(r);return service.patients(q);}
 @PostMapping("/patients") public Object patient(HttpServletRequest r,@RequestBody Map<String,Object> p){long actor=auth.require(r,"REGISTRAR");return service.createPatient(p,actor);}
 @PutMapping("/patients/{id}") public Object updatePatient(HttpServletRequest r,@PathVariable long id,@RequestBody Map<String,Object> p){return service.updatePatient(id,p,auth.require(r,"REGISTRAR"));}
 @GetMapping("/registrations") public Object registrations(HttpServletRequest r,@RequestParam(defaultValue="")String q){auth.require(r,"DOCTOR","REGISTRAR");return service.registrations(q,auth.current(r));}
 @PostMapping("/registrations") public Object register(HttpServletRequest r,@RequestBody Map<String,Object> p){long actor=auth.require(r,"REGISTRAR");return service.register(p,actor);}
 @PostMapping("/registrations/{id}/cancel") public Object cancel(HttpServletRequest r,@PathVariable long id){return service.cancelRegistration(id,auth.require(r,"REGISTRAR"));}
 @GetMapping("/records/{registrationId}") public Object record(HttpServletRequest r,@PathVariable long registrationId){auth.current(r);return service.record(registrationId,auth.current(r));}
 @PutMapping("/records/{registrationId}") public Object saveRecord(HttpServletRequest r,@PathVariable long registrationId,@RequestBody Map<String,Object> p){return service.saveRecord(registrationId,p,auth.require(r,"DOCTOR"),auth.current(r));}
 @PostMapping("/records/{registrationId}/finalize") public Object finalizeRecord(HttpServletRequest r,@PathVariable long registrationId,@RequestBody Map<String,Object> p){return service.finalizeRecord(registrationId,p,auth.require(r,"DOCTOR"),auth.current(r));}
 @GetMapping("/catalog") public Object catalog(HttpServletRequest r,@RequestParam(defaultValue="")String type){auth.current(r);if(!type.isEmpty()&&!Rules.TYPES.contains(type))throw ApiError.bad("类型错误");return catalog.projects(type);}
 @GetMapping("/services") public Object services(HttpServletRequest r,@RequestParam String type,@RequestParam(defaultValue="")String q){auth.current(r);return service.services(type,q,auth.current(r));}
 @PostMapping("/services") public Object addService(HttpServletRequest r,@RequestBody Map<String,Object> p){return service.addService(p,auth.require(r,"DOCTOR"),auth.current(r));}
 @PostMapping("/services/{type}/{id}/start") public Object start(HttpServletRequest r,@PathVariable String type,@PathVariable long id){return service.startService(type,id,auth.current(r));}
 @PostMapping("/services/{type}/{id}/complete") public Object complete(HttpServletRequest r,@PathVariable String type,@PathVariable long id,@RequestBody Map<String,Object> p){return service.completeService(type,id,p,auth.current(r));}
 @PostMapping("/services/{type}/{id}/cancel") public Object cancelService(HttpServletRequest r,@PathVariable String type,@PathVariable long id){return service.cancelService(type,id,auth.current(r));}
 @PostMapping("/drugs") public Object addDrug(HttpServletRequest r,@RequestBody Map<String,Object> p){return service.addDrug(p,auth.require(r,"PHARMACIST"));}
 @PutMapping("/drugs/{id}") public Object updateDrug(HttpServletRequest r,@PathVariable long id,@RequestBody Map<String,Object> p){return service.updateDrug(id,p,auth.require(r,"PHARMACIST"));}
 @GetMapping("/drugs") public Object drugs(HttpServletRequest r,@RequestParam(defaultValue="")String q){auth.current(r);return catalog.drugs(q);}
 @PostMapping("/prescriptions") public Object prescription(HttpServletRequest r,@RequestBody Map<String,Object> p){return service.addPrescription(p,auth.require(r,"DOCTOR"),auth.current(r));}
 @GetMapping("/prescriptions") public Object prescriptions(HttpServletRequest r,@RequestParam(defaultValue="")String q){auth.require(r,"DOCTOR","REGISTRAR","PHARMACIST");return service.prescriptions(q,auth.current(r));}
 @PostMapping("/prescriptions/{itemId}/dispense") public Object dispense(HttpServletRequest r,@PathVariable long itemId){return service.dispense(itemId,auth.require(r,"PHARMACIST"));}
 @PostMapping("/prescriptions/{itemId}/return") public Object returnDrug(HttpServletRequest r,@PathVariable long itemId){return service.returnDrug(itemId,auth.require(r,"PHARMACIST"));}
 @PostMapping("/drugs/{id}/stock") public Object stock(HttpServletRequest r,@PathVariable long id,@RequestBody Map<String,Object> p){return service.adjustStock(id,p,auth.require(r,"PHARMACIST"));}
 @GetMapping("/charges") public Object charges(HttpServletRequest r,@RequestParam(defaultValue="")String q){auth.require(r,"DOCTOR","REGISTRAR","PHARMACIST");return service.charges(q,auth.current(r));}
 @PostMapping("/charges/{id}/pay") public Object pay(HttpServletRequest r,@PathVariable long id,@RequestBody Map<String,Object> p){return service.pay(id,p,auth.require(r,"REGISTRAR"));}
 @PostMapping("/charges/{id}/refund") public Object refund(HttpServletRequest r,@PathVariable long id,@RequestBody Map<String,Object> p){return service.refund(id,p,auth.require(r,"REGISTRAR"));}
 @GetMapping("/dashboard") public Object dashboard(HttpServletRequest r){auth.current(r);return service.dashboard();}
 @GetMapping("/audit") public Object audit(HttpServletRequest r){auth.require(r,"ADMIN");return service.audit();}
}
