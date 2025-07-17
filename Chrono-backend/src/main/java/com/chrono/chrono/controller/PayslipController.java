package com.chrono.chrono.controller;

import com.chrono.chrono.entities.Payslip;
import com.chrono.chrono.dto.PayslipDTO;
import com.chrono.chrono.services.PayrollService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/payslips")
public class PayslipController {
    @Autowired
    private PayrollService payrollService;

    @PreAuthorize("hasRole('ADMIN') or hasRole('USER')")
    @PostMapping("/generate")
    public ResponseEntity<PayslipDTO> generate(
            @RequestParam Long userId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {
        Payslip ps = payrollService.generatePayslip(userId, start, end);
        return ResponseEntity.ok(new PayslipDTO(ps));
    }

    @PreAuthorize("hasRole('ADMIN') or hasRole('USER')")
    @GetMapping("/user/{userId}")
    public ResponseEntity<List<PayslipDTO>> list(@PathVariable Long userId) {
        List<Payslip> list = payrollService.getPayslipsForUser(userId);
        return ResponseEntity.ok(list.stream().map(PayslipDTO::new).toList());
    }

    @PreAuthorize("hasRole('ADMIN') or hasRole('PAYROLL_ADMIN')")
    @PostMapping("/approve/{id}")
    public ResponseEntity<Void> approve(@PathVariable Long id,
                                        @RequestParam(required = false) String comment) {
        payrollService.approvePayslip(id, comment);
        return ResponseEntity.ok().build();
    }

    @PreAuthorize("hasRole('ADMIN') or hasRole('PAYROLL_ADMIN')")
    @PostMapping("/approve-all")
    public ResponseEntity<Void> approveAll(@RequestParam(required = false) Long userId,
                                           @RequestParam(required = false) String comment) {
        if (userId != null) {
            payrollService.approveAllForUser(userId, comment);
        } else {
            payrollService.approveAll(comment);
        }
        return ResponseEntity.ok().build();
    }

    @PreAuthorize("hasRole('ADMIN') or hasRole('PAYROLL_ADMIN')")
    @GetMapping("/admin/all")
    public ResponseEntity<List<PayslipDTO>> all() {
        return ResponseEntity.ok(payrollService.getAllPayslips().stream().map(PayslipDTO::new).toList());
    }

    @PreAuthorize("hasRole('ADMIN') or hasRole('PAYROLL_ADMIN')")
    @GetMapping("/admin/pending")
    public ResponseEntity<List<PayslipDTO>> pending() {
        return ResponseEntity.ok(payrollService.getPendingPayslips().stream().map(PayslipDTO::new).toList());
    }

    @PreAuthorize("hasRole('ADMIN') or hasRole('PAYROLL_ADMIN')")
    @GetMapping("/admin/export")
    public ResponseEntity<String> exportCsv(@RequestParam(defaultValue = "en") String lang) {
        StringBuilder sb = new StringBuilder();
        if ("de".equalsIgnoreCase(lang)) {
            sb.append("BenutzerID,Start,Ende,Brutto,Abzuege,Netto\n");
        } else {
            sb.append("userId,periodStart,periodEnd,gross,deductions,net\n");
        }
        payrollService.getAllPayslips().forEach(ps -> {
            sb.append(ps.getUser().getId()).append(',')
              .append(ps.getPeriodStart()).append(',')
              .append(ps.getPeriodEnd()).append(',')
              .append(ps.getGrossSalary()).append(',')
              .append(ps.getDeductions()).append(',')
              .append(ps.getNetSalary()).append('\n');
        });
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.TEXT_PLAIN);
        headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=payslips.csv");
        return ResponseEntity.ok().headers(headers).body(sb.toString());
    }

    @PreAuthorize("hasRole('ADMIN') or hasRole('PAYROLL_ADMIN')")
    @GetMapping("/admin/backup")
    public ResponseEntity<String> backup() {
        // simple CSV backup using same export
        return exportCsv();
    }
}
