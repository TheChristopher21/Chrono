package com.chrono.chrono.controller;

import com.chrono.chrono.entities.Payslip;
import com.chrono.chrono.dto.PayslipDTO;
import com.chrono.chrono.services.AccessControlService;
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

    @Autowired
    private AccessControlService accessControlService;

    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERADMIN', 'PAYROLL_ADMIN')")
    @PostMapping("/generate")
    public ResponseEntity<PayslipDTO> generate(
            @RequestParam Long userId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate payoutDate,
            @RequestParam(defaultValue = "false") boolean payoutOvertime,
            @RequestParam(required = false) Double overtimeHours,
            Principal principal) {
        User requester = requireRequester(principal);
        requirePayrollAccess(requester, requireTargetUser(userId));
        Payslip ps = payrollService.generatePayslip(userId, start, end, payoutDate, overtimeHours, payoutOvertime);
        return ResponseEntity.ok(new PayslipDTO(ps));
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERADMIN', 'PAYROLL_ADMIN')")
    @PostMapping("/schedule")
    public ResponseEntity<Void> schedule(
            @RequestParam Long userId,
            @RequestParam int day,
            Principal principal) {
        User requester = requireRequester(principal);
        requirePayrollAccess(requester, requireTargetUser(userId));
        payrollService.setPayslipSchedule(userId, day);
        return ResponseEntity.ok().build();
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERADMIN', 'PAYROLL_ADMIN')")
    @PostMapping("/schedule-all")
    public ResponseEntity<Void> scheduleAll(@RequestParam(defaultValue = "1") int day,
                                            Principal principal) {
        User requester = requireRequester(principal);
        payrollService.setPayslipScheduleForAll(requester, day);
        return ResponseEntity.ok().build();
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERADMIN', 'PAYROLL_ADMIN', 'USER')")
    @GetMapping("/user/{userId}")
    public ResponseEntity<List<PayslipDTO>> list(
            @PathVariable Long userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end,
            Principal principal
    ) {
        User requester = userRepository.findByUsername(principal.getName()).orElseThrow();
        boolean privileged = hasAnyRole(requester, "ROLE_ADMIN", "ROLE_SUPERADMIN", "ROLE_PAYROLL_ADMIN");
        if (!privileged && !requester.getId().equals(userId)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        if (privileged) {
            requirePayrollAccess(requester, requireTargetUser(userId));
        }
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

        boolean isAdmin = hasAnyRole(user, "ROLE_ADMIN", "ROLE_SUPERADMIN", "ROLE_PAYROLL_ADMIN");
        if (isAdmin) {
            requirePayrollAccess(user, ps.getUser());
        } else if (!ps.getUser().getId().equals(user.getId())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        byte[] bytes = payrollService.getPayslipPdf(id, lang);
        if (bytes == null) return ResponseEntity.notFound().build();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=payslip-" + id + ".pdf");
        return ResponseEntity.ok().headers(headers).body(bytes);
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERADMIN', 'PAYROLL_ADMIN')")
    @PostMapping("/approve/{id}")
    public ResponseEntity<Void> approve(@PathVariable Long id,
                                        @RequestParam(required = false) String comment,
                                        Principal principal) {
        User requester = requireRequester(principal);
        requireAccessiblePayslip(id, requester);
        payrollService.approvePayslip(id, comment);
        return ResponseEntity.ok().build();
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERADMIN', 'PAYROLL_ADMIN')")
    @PostMapping("/set-payout/{id}")
    public ResponseEntity<Void> setPayoutDate(@PathVariable Long id,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate payoutDate,
            Principal principal) {
        User requester = requireRequester(principal);
        requireAccessiblePayslip(id, requester);
        payrollService.setPayoutDate(id, payoutDate);
        return ResponseEntity.ok().build();
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERADMIN', 'PAYROLL_ADMIN')")
    @PostMapping("/approve-all")
    public ResponseEntity<Void> approveAll(@RequestParam(required = false) Long userId,
                                           @RequestParam(required = false) String comment,
                                           Principal principal) {
        User requester = requireRequester(principal);
        if (userId != null) {
            requirePayrollAccess(requester, requireTargetUser(userId));
            payrollService.approveAllForUser(userId, comment);
        } else {
            if (accessControlService.isSuperAdmin(requester) && requester.getCompany() == null) {
                payrollService.approveAll(comment);
            } else {
                for (User visibleUser : accessControlService.visibleUsersForAdmin(requester)) {
                    payrollService.approveAllForUser(visibleUser.getId(), comment);
                }
            }
        }
        return ResponseEntity.ok().build();
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERADMIN', 'PAYROLL_ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id, Principal principal) {
        User requester = requireRequester(principal);
        requireAccessiblePayslip(id, requester);
        payrollService.deletePayslip(id);
        return ResponseEntity.ok().build();
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERADMIN', 'PAYROLL_ADMIN')")
    @PostMapping("/reopen/{id}")
    public ResponseEntity<Void> reopen(@PathVariable Long id, Principal principal) {
        User requester = requireRequester(principal);
        requireAccessiblePayslip(id, requester);
        payrollService.reopenPayslip(id);
        return ResponseEntity.ok().build();
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERADMIN', 'PAYROLL_ADMIN')")
    @GetMapping("/admin/all")
    public ResponseEntity<List<PayslipDTO>> all(Principal principal) {
        User requester = requireRequester(principal);
        return ResponseEntity.ok(payrollService.getAllPayslips(requester).stream().map(PayslipDTO::new).toList());
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERADMIN', 'PAYROLL_ADMIN')")
    @GetMapping("/admin/pending")
    public ResponseEntity<List<PayslipDTO>> pending(Principal principal) {
        User admin = userRepository.findByUsername(principal.getName()).orElseThrow();
        return ResponseEntity.ok(
                payrollService.getPendingPayslips(admin).stream()
                        .map(PayslipDTO::new).toList());
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERADMIN', 'PAYROLL_ADMIN')")
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

    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERADMIN', 'PAYROLL_ADMIN')")
    @GetMapping("/admin/pdf/{id}")
    public ResponseEntity<byte[]> downloadPdf(@PathVariable Long id,
            @RequestParam(defaultValue = "de") String lang,
            Principal principal) {
        User requester = requireRequester(principal);
        requireAccessiblePayslip(id, requester);
        byte[] bytes = payrollService.getPayslipPdf(id, lang);
        if (bytes == null) return ResponseEntity.notFound().build();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=payslip-" + id + ".pdf");
        return ResponseEntity.ok().headers(headers).body(bytes);
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERADMIN', 'PAYROLL_ADMIN')")
    @GetMapping("/admin/export")
    public ResponseEntity<String> exportCsv(@RequestParam(defaultValue = "en") String lang,
                                            Principal principal) {
        User requester = requireRequester(principal);
        StringBuilder sb = new StringBuilder();
        if ("de".equalsIgnoreCase(lang)) {
            sb.append("BenutzerID,Start,Ende,Brutto,Abzuege,Netto,Waehrung\n");
        } else {
            sb.append("userId,periodStart,periodEnd,gross,deductions,net,currency\n");
        }
        payrollService.getAllPayslips(requester).forEach(ps -> {
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

    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERADMIN', 'PAYROLL_ADMIN')")
    @GetMapping("/admin/backup")
    public ResponseEntity<String> backup(Principal principal) {
        // Default-Sprache: Englisch
        return exportCsv("en", principal);
    }

    private boolean hasAnyRole(User user, String... roleNames) {
        if (user == null || roleNames == null) {
            return false;
        }
        return user.getRoles().stream()
                .anyMatch(role -> java.util.Arrays.asList(roleNames).contains(role.getRoleName()));
    }

    private User requireRequester(Principal principal) {
        return userRepository.findByUsername(principal.getName()).orElseThrow();
    }

    private User requireTargetUser(Long userId) {
        return userRepository.findById(userId).orElseThrow();
    }

    private void requirePayrollAccess(User requester, User target) {
        if (requester.getId().equals(target.getId())) {
            return;
        }
        accessControlService.requirePayrollAccess(requester, target);
    }

    private Payslip requireAccessiblePayslip(Long id, User requester) {
        Payslip payslip = payslipRepository.findById(id).orElseThrow();
        requirePayrollAccess(requester, payslip.getUser());
        return payslip;
    }
}
