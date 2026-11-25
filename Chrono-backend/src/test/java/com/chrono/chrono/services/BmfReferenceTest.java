package com.chrono.chrono.services;

import com.chrono.chrono.services.payroll.BmfLohnsteuer2026;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;

class BmfReferenceTest {
    @Test void matchesEightIndependentDailyAndOneOffBmfCases() throws Exception {
        try (var stream=getClass().getResourceAsStream("/payroll/bmf-2026-extended-reference.json")) {
            var cases=new ObjectMapper().readTree(stream).get("cases"); assertEquals(8,cases.size());
            for (var reference : cases) {
                var pap=new BmfLohnsteuer2026(); var inputs=reference.get("inputs").fields();
                while(inputs.hasNext()) {
                    var entry=inputs.next(); var field=BmfLohnsteuer2026.class.getField(entry.getKey()); String value=entry.getValue().asText();
                    field.set(pap,field.getType()==int.class?Integer.parseInt(value):field.getType()==double.class?Double.parseDouble(value):new BigDecimal(value));
                }
                pap.calculate();
                for(String output:new String[]{"LSTLZZ","SOLZLZZ","BK","STS","SOLZS","BKS"}) assertEquals(0,
                        new BigDecimal(reference.get("outputs").get(output).asText()).compareTo((BigDecimal)BmfLohnsteuer2026.class.getField(output).get(pap)),reference.get("inputs")+" "+output);
            }
        }
    }
    @Test void matchesIndependentOfficialBmfResultsAcrossClassesCeilingsChildrenAndFactor() throws Exception {
        try (var stream=getClass().getResourceAsStream("/payroll/bmf-2026-reference.json")) {
            assertNotNull(stream,"Independent BMF reference results must be present");
            var cases=new ObjectMapper().readTree(stream).get("cases"); assertEquals(15,cases.size());
            for (var reference : cases) {
                var pap=new BmfLohnsteuer2026(); var inputs=reference.get("inputs").fields();
                while(inputs.hasNext()) {
                    var entry=inputs.next(); var field=BmfLohnsteuer2026.class.getField(entry.getKey()); String value=entry.getValue().asText();
                    field.set(pap,field.getType()==int.class?Integer.parseInt(value):field.getType()==double.class?Double.parseDouble(value):new BigDecimal(value));
                }
                pap.calculate();
                for(String output:new String[]{"LSTLZZ","SOLZLZZ","BK"}) assertEquals(0,
                        new BigDecimal(reference.get("outputs").get(output).asText()).compareTo((BigDecimal)BmfLohnsteuer2026.class.getField(output).get(pap)),reference.get("inputs")+" "+output);
            }
        }
    }
}
