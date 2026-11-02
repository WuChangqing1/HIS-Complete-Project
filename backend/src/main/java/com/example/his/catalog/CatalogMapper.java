package com.example.his.catalog;
import org.apache.ibatis.annotations.*;
import java.util.*;
@Mapper
public interface CatalogMapper {
 @Select("SELECT id,dept_code,dept_name FROM department WHERE delmark=1 ORDER BY id") List<Map<String,Object>> departments();
 @Select("SELECT id,regist_code,regist_name,regist_fee,regist_quota FROM regist_level WHERE delmark=1 ORDER BY sequence_no,id") List<Map<String,Object>> levels();
 @Select("SELECT id,settle_code,settle_name FROM settle_category WHERE delmark=1 ORDER BY sequence_no,id") List<Map<String,Object>> settlement();
 @Select("SELECT id,project_code,project_name,project_type,unit_price,specification FROM medical_technology WHERE enabled=1 AND (#{type}='' OR project_type=#{type}) ORDER BY id") List<Map<String,Object>> projects(@Param("type") String type);
 @Select("SELECT id,drug_code,drug_name,unit,producer,drug_type,unit_price,stock FROM drug_info WHERE enabled=1 AND (#{q}='' OR drug_name LIKE CONCAT('%',#{q},'%') OR drug_code LIKE CONCAT('%',#{q},'%')) ORDER BY id LIMIT 200") List<Map<String,Object>> drugs(@Param("q") String q);
}
