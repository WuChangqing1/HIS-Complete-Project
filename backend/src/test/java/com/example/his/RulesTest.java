package com.example.his;
import com.example.his.core.Rules;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;
class RulesTest {
 @Test void registrationBookFee(){assertEquals(new BigDecimal("11.00"),Rules.registrationTotal(new BigDecimal("10.00"),true));}
 @Test void registrationWithoutBook(){assertEquals(new BigDecimal("10.00"),Rules.registrationTotal(new BigDecimal("10.00"),false));}
 @Test void noNegativeFee(){assertThrows(IllegalArgumentException.class,()->Rules.registrationTotal(new BigDecimal("-0.01"),true));}
 @Test void drugAmountExact(){assertEquals(new BigDecimal("37.50"),Rules.drugTotal(new BigDecimal("12.50"),3));}
 @Test void zeroQuantityRejected(){assertThrows(IllegalArgumentException.class,()->Rules.drugTotal(new BigDecimal("2.00"),0));}
 @Test void negativePriceRejected(){assertThrows(IllegalArgumentException.class,()->Rules.drugTotal(new BigDecimal("-1"),1));}
 @Test void validRequestTypes(){assertEquals("check_request",Rules.tableFor("CHECK"));assertEquals("inspection_request",Rules.tableFor("LAB"));assertEquals("disposal_request",Rules.tableFor("PROCEDURE"));}
 @Test void invalidTypeRejected(){assertThrows(IllegalArgumentException.class,()->Rules.tableFor("random; drop table"));}
 @Test void fieldPrefixes(){assertEquals("check",Rules.prefixFor("CHECK"));assertEquals("inspection",Rules.prefixFor("LAB"));assertEquals("disposal",Rules.prefixFor("PROCEDURE"));}
 @Test void refundRuleService(){assertTrue(Rules.canRefund("SERVICE","CREATED",null));assertFalse(Rules.canRefund("SERVICE","REGISTERED",null));assertFalse(Rules.canRefund("SERVICE","COMPLETED",null));}
 @Test void refundRuleDrug(){assertTrue(Rules.canRefund("DRUG",null,"RETURNED"));assertFalse(Rules.canRefund("DRUG",null,"DISPENSED"));}
 @Test void refundRuleRegistration(){assertTrue(Rules.canRefund("REGISTRATION","WAITING",null));assertFalse(Rules.canRefund("REGISTRATION","IN_PROGRESS",null));}
}
