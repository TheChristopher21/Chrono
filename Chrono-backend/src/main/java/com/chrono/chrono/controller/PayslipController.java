package com.chrono.chrono.controller;

import com.chrono.chrono.entities.Payslip;
import com.chrono.chrono.dto.PayslipDTO;
import com.chrono.chrono.services.PayrollService;
import com.chrono.chrono.entities.User;
import com.chrono.chrono.repositories.UserRepository;
import com.chrono.chrono.repositories.PayslipRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.security.Principal;

@RestController
@RequestMapping("/api/payslips")
public class PayslipController {
    @Autowired
    private PayrollService payrollService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PayslipRepository payslipRepository;

    @PreAuthorize("hasRole('ADMIN') or hasRole('USER')")
    @PostMapping("/generate")
    public ResponseEntity<PayslipDTO> generate(
            @RequestParam Long userId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate payoutDate,
            @RequestParam(defaultValue = "false") boolean payoutOvertime,
            @RequestParam(required = false) Double overtimeHours) {
        Payslip ps = payrollService.generatePayslip(userId, start, end, payoutDate, overtimeHours, payoutOvertime);
        return ResponseEntity.ok(new PayslipDTO(ps));
    }

    @PreAuthorize("hasRole('ADMIN') or hasRole('USER')")
    @PostMapping("/schedule")
    public ResponseEntity<Void> schedule(
            @RequestParam Long userId,
            @RequestParam int day) {
        payrollService.setPayslipSchedule(userId, day);
        return ResponseEntity.ok().build();
    }

    @PreAuthorize("hasRole('ADMIN') or hasRole('PAYROLL_ADMIN')")
    @PostMapping("/schedule-all")
    public ResponseEntity<Void> scheduleAll(@RequestParam(defaultValue = "1") int day) {
        payrollService.setPayslipScheduleForAll(day);
        return ResponseEntity.ok().build();
    }

    @PreAuthorize("hasRole('ADMIN') or hasRole('USER')")
    @GetMapping("/user/{userId}")
    public ResponseEntity<List<PayslipDTO>> list(
            @PathVariable Long userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end
    ) {
        List<Payslip> list = payrollService.getPayslipsForUser(userId, start, end);
        return ResponseEntity.ok(list.stream().map(PayslipDTO::new).toList());
    }

    /**
     * Returns the approved payslips for the currently authenticated user.
     * This mirrors the behaviour of {@link #list(Long, LocalDate, LocalDate)}
     * but determines the user id from the {@link Principal}.
     */
    @GetMapping("/my")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<PayslipDTO>> myPayslips(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end,
            Principal principal) {
        User user = userRepository.findByUsername(principal.getName()).orElseThrow();
        List<Payslip> list = payrollService.getPayslipsForUser(user.getId(), start, end);
        return ResponseEntity.ok(list.stream().map(PayslipDTO::new).toList());
    }

    /**
     * Downloads a PDF for the given payslip if the current user is allowed to access it.
     * Regular users may only download their own payslips, while admins can access all.
     */
    @GetMapping("/pdf/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<byte[]> myPayslipPdf(@PathVariable Long id,
                                               @RequestParam(defaultValue = "de") String lang,
                                               Principal principal) {
        User user = userRepository.findByUsername(principal.getName()).orElseThrow();
        Payslip ps = payslipRepository.findById(id).orElse(null);
        if (ps == null) {
            return ResponseEntity.notFound().build();
        }

        boolean isAdmin = user.getRoles().stream().anyMatch(r ->
                r.getRoleName().equals("ROLE_ADMIN") || r.getRoleName().equals("ROLE_PAYROLL_ADMIN"));
        if (!isAdmin && !ps.getUser().getId().equals(user.getId())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        byte[] bytes = payrollService.getPayslipPdf(id, lang);
        if (bytes == null) return ResponseEntity.notFound().build();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=payslip-" + id + ".pdf");
        return ResponseEntity.ok().headers(headers).body(bytes);
    }

    @PreAuthorize("hasRole('ADMIN') or hasRole('PAYROLL_ADMIN')")
    @PostMapping("/approve/{id}")
    public ResponseEntity<Void> approve(@PathVariable Long id,
                                        @RequestParam(required = false) String comment) {
        payrollService.approvePayslip(id, comment);
        return ResponseEntity.ok().build();
    }

    @PreAuthorize("hasRole('ADMIN') or hasRole('PAYROLL_ADMIN')")
    @PostMapping("/set-payout/{id}")
    public ResponseEntity<Void> setPayoutDate(@PathVariable Long id,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate payoutDate) {
        payrollService.setPayoutDate(id, payoutDate);
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
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        payrollService.deletePayslip(id);
        return ResponseEntity.ok().build();
    }

    @PreAuthorize("hasRole('ADMIN') or hasRole('PAYROLL_ADMIN')")
    @PostMapping("/reopen/{id}")
    public ResponseEntity<Void> reopen(@PathVariable Long id) {
        payrollService.reopenPayslip(id);
        return ResponseEntity.ok().build();
    }

    @PreAuthorize("hasRole('ADMIN') or hasRole('PAYROLL_ADMIN')")
    @GetMapping("/admin/all")
    public ResponseEntity<List<PayslipDTO>> all() {
        return ResponseEntity.ok(payrollService.getAllPayslips().stream().map(PayslipDTO::new).toList());
    }

    @PreAuthorize("hasRole('ADMIN') or hasRole('PAYROLL_ADMIN')")
    @GetMapping("/admin/pending")
    public ResponseEntity<List<PayslipDTO>> pending(Principal principal) {
        User admin = userRepository.findByUsername(principal.getName()).orElseThrow();
        return ResponseEntity.ok(
                payrollService.getPendingPayslips(admin).stream()
                        .map(PayslipDTO::new).toList());
    }

    @PreAuthorize("hasRole('ADMIN') or hasRole('PAYROLL_ADMIN')")
    @GetMapping("/admin/approved")
    public ResponseEntity<List<PayslipDTO>> approved(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end,
            Principal principal) {
        User admin = userRepository.findByUsername(principal.getName()).orElseThrow();
        return ResponseEntity.ok(payrollService
                .getApprovedPayslips(admin, name, start, end));

    }

    @PreAuthorize("hasRole('ADMIN') or hasRole('PAYROLL_ADMIN')")
    @GetMapping("/admin/pdf/{id}")
    public ResponseEntity<byte[]> downloadPdf(@PathVariable Long id,
            @RequestParam(defaultValue = "de") String lang) {
        byte[] bytes = payrollService.getPayslipPdf(id, lang);
        if (bytes == null) return ResponseEntity.notFound().build();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=payslip-" + id + ".pdf");
        return ResponseEntity.ok().headers(headers).body(bytes);
    }

    @PreAuthorize("hasRole('ADMIN') or hasRole('PAYROLL_ADMIN')")
    @GetMapping("/admin/export")
    public ResponseEntity<String> exportCsv(@RequestParam(defaultValue = "en") String lang) {
        StringBuilder sb = new StringBuilder();
        if ("de".equalsIgnoreCase(lang)) {
            sb.append("BenutzerID,Start,Ende,Brutto,Abzuege,Netto,Waehrung\n");
        } else {
            sb.append("userId,periodStart,periodEnd,gross,deductions,net,currency\n");
        }
        payrollService.getAllPayslips().forEach(ps -> {
            String currency = (ps.getUser() != null && "DE".equalsIgnoreCase(ps.getUser().getCountry())) ? "EUR" : "CHF";
            sb.append(ps.getUser().getId()).append(',')
              .append(ps.getPeriodStart()).append(',')
              .append(ps.getPeriodEnd()).append(',')
              .append(ps.getGrossSalary()).append(',')
              .append(ps.getDeductions()).append(',')
              .append(ps.getNetSalary()).append(',')
              .append(currency).append('\n');
        });
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.TEXT_PLAIN);
        headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=payslips.csv");
        return ResponseEntity.ok().headers(headers).body(sb.toString());
    }

    @PreAuthorize("hasRole('ADMIN') or hasRole('PAYROLL_ADMIN')")
    @GetMapping("/admin/backup")
    public ResponseEntity<String> backup() {
        // Default-Sprache: Englisch
        return exportCsv("en");
    }
}
