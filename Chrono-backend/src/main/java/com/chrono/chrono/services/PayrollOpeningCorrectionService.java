package com.chrono.chrono.services;

import com.chrono.chrono.entities.*;
import com.chrono.chrono.repositories.*;
import com.chrono.chrono.dto.PayslipDTO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

/** Stages an opening and its complete correction chain in one transaction. Activation waits for approval. */
@Service
public class PayrollOpeningCorrectionService {
    private final PayrollOpeningService openings;
    private final PayrollCorrectionService corrections;
    private final UserRepository users;
    private final PayslipRepository slips;
    public PayrollOpeningCorrectionService(PayrollOpeningService openings,PayrollCorrectionService corrections,UserRepository users,PayslipRepository slips) {
        this.openings=openings;this.corrections=corrections;this.users=users;this.slips=slips;
    }
    public record Input(PayrollOpeningService.Input opening,PayrollCorrectionService.Request correction) {}
    public record Result(PayrollOpeningVersion opening,List<PayslipDTO> corrections) {}
    @Transactional
    public Result stage(Long companyId,Long actorId,Input input,String actor) {
        if(input==null||input.correction()==null)throw new IllegalArgumentException("Eröffnungsdaten und Korrekturgrund/-datum fehlen.");
        var version=openings.stageCorrection(companyId,actorId,input.opening());
        User user=users.findByIdForUpdate(version.getUserId()).orElseThrow();
        var first=slips.findApprovedForUpdate(user).stream().filter(p->!p.isCorrection()&&p.getCalculationSnapshot()!=null&&!p.getPeriodStart().isBefore(version.getPayrollStart()))
                .filter(p->p.getPeriodStart().getYear()==version.getPayrollStart().getYear()).min(Comparator.comparing(Payslip::getPeriodStart).thenComparing(Payslip::getId)).orElseThrow();
        var request=input.correction();
        var documents=corrections.generate(first.getId(),new PayrollCorrectionService.Request(request.reason(),request.payoutDate(),null,request.yearAssignment(),version.getId()),actor);
        openings.bindCorrectionBatch(version,documents.get(0).getCorrectionBatch());
        return new Result(version,documents.stream().map(PayslipDTO::new).toList());
    }
}
