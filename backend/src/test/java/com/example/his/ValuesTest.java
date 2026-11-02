package com.example.his;
import com.example.his.core.Values;
import com.example.his.core.ApiError;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import static org.junit.jupiter.api.Assertions.*;
class ValuesTest {
 @Test void normalizeText(){assertEquals("测试",Values.str(Map.of("name"," 测试 "),"name"));}
 @Test void mandatoryNotEmpty(){assertThrows(ApiError.class,()->Values.required(Map.of("x","  "),"x",20));}
 @Test void mandatoryLength(){assertThrows(ApiError.class,()->Values.required(Map.of("x","123456"),"x",5));}
 @Test void optionalMissing(){assertEquals("",Values.optional(Map.of(),"notThere",3));}
 @Test void invalidIds(){assertThrows(ApiError.class,()->Values.id(Map.of("id",-1),"id"));assertThrows(ApiError.class,()->Values.id(Map.of("id","abc"),"id"));}
 @Test void validIds(){assertEquals(29,Values.id(Map.of("id",29),"id"));}
 @Test void positiveBounds(){assertEquals(25,Values.positive(Map.of("qty",25),"qty"));assertThrows(ApiError.class,()->Values.positive(Map.of("qty",10001),"qty"));}
 @Test void parseDate(){assertEquals(LocalDate.of(2026,11,2),Values.date(Map.of("d","2026-11-02"),"d"));}
 @Test void invalidDate(){assertThrows(ApiError.class,()->Values.date(Map.of("d","2026/11/02"),"d"));}
 @Test void exactMoney(){assertEquals(new BigDecimal("12.50"),Values.amount("12.5"));}
 @Test void rejectInvalidMoney(){assertThrows(ApiError.class,()->Values.amount("3.145"));assertThrows(ApiError.class,()->Values.amount("-0.01"));}
}
