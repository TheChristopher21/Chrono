package com.chrono.chrono.services;
import com.chrono.chrono.services.payroll.*;
import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.Test;
import java.math.*;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class Payroll2025ReferenceTest {
    @Test void matchesIndependentOfficial2025ProgramTests() throws Exception {
        var json=new ObjectMapper();var fixtures=json.readTree(getClass().getResourceAsStream("/payroll/bmf-2025-payments-reference.json"));int count=0;
        for(var fixture:fixtures.path("cases")) {
            var pap=new BmfLohnsteuer2025();var fields=fixture.path("inputs").fields();
            while(fields.hasNext()){var pair=fields.next();var field=BmfLohnsteuer2025.class.getField(pair.getKey());if(field.getType()==BigDecimal.class)field.set(pap,new BigDecimal(pair.getValue().asText()));else if(field.getType()==double.class)field.setDouble(pap,pair.getValue().asDouble());else field.setInt(pap,pair.getValue().asInt());}
            pap.calculate();for(String key:List.of("LSTLZZ","SOLZLZZ","BK","STS","SOLZS","BKS"))assertEquals(0,new BigDecimal(fixture.path("outputs").path(key).asText()).compareTo((BigDecimal)BmfLohnsteuer2025.class.getField(key).get(pap)),key);count++;
        }
        assertEquals(5,count);
    }
    @Test void wrapperUsesThe2025PapForTheThreeWeekAssignment() {
        var t=PayrollTestTerms.german();var start=LocalDate.of(2025,12,1);var g=new PayrollGrossCalculator.Result(new BigDecimal("5500"),BigDecimal.ZERO,BigDecimal.ZERO,new BigDecimal("5500"),0);
        var tax=GermanHistoricalTax.taxes(t,g,start,start.withDayOfMonth(31),new PayrollLedgerService.History(List.of(),List.of(),true),BigDecimal.ZERO,null);
        assertEquals(933.66,tax.get("Income tax"));
    }
}
