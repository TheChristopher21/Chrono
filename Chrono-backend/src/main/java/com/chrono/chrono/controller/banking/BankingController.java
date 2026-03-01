package com.chrono.chrono.controller.banking;

import com.chrono.chrono.dto.banking.*;
import com.chrono.chrono.entities.Company;
import com.chrono.chrono.entities.User;
import com.chrono.chrono.entities.accounting.CustomerInvoice;
import com.chrono.chrono.entities.accounting.VendorInvoice;
import com.chrono.chrono.entities.banking.BankAccount;
import com.chrono.chrono.entities.banking.PaymentBatch;
import com.chrono.chrono.entities.banking.PaymentInstruction;
import com.chrono.chrono.entities.banking.SecureMessage;
import com.chrono.chrono.services.UserService;
import com.chrono.chrono.services.banking.BankingService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.net.URI;
import java.security.Principal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@RestController
@RequestMapping("/api/banking")
public class BankingController {

    private final BankingService bankingService;
    private final UserService userService;
    public BankingController(BankingService bankingService,
                             UserService userService) {
        this.bankingService = bankingService;
        this.userService = userService;
    }

    private Company getCompany(Principal principal) {
        User user = principal != null ? userService.getUserByUsername(principal.getName()) : null;
        return user != null ? user.getCompany() : null;
    }

    @PostMapping("/accounts")
    public ResponseEntity<BankAccountDTO> addAccount(@RequestBody CreateBankAccountRequest request, Principal principal) {
        Company company = getCompany(principal);
        if (company == null) {
            return ResponseEntity.status(401).build();
        }
        BankAccount saved = bankingService.saveBankAccount(request.toEntity(company));
        return ResponseEntity.created(URI.create("/api/banking/accounts/" + saved.getId()))
                .body(BankAccountDTO.from(saved));
    }

    @GetMapping("/accounts")
    public ResponseEntity<List<BankAccountDTO>> listAccounts(Principal principal) {
        Company company = getCompany(principal);
        if (company == null) {
            return ResponseEntity.status(401).build();
        }
        List<BankAccountDTO> accounts = bankingService.listAccounts(company).stream()
                .map(BankAccountDTO::from)
                .toList();
        return ResponseEntity.ok(accounts);
    }

    @PostMapping("/batches")
    public ResponseEntity<PaymentBatchDTO> createBatch(@RequestBody CreatePaymentBatchRequest request, Principal principal) {
        Company company = getCompany(principal);
        if (company == null) {
            return ResponseEntity.status(401).build();
        }
        List<PaymentInstruction> instructions = new ArrayList<>();
        for (PaymentInstructionRequest payload : request.getInstructions()) {
            PaymentInstruction instruction = new PaymentInstruction();
            instruction.setCreditorName(payload.getCreditorName());
            instruction.setCreditorIban(payload.getCreditorIban());
            instruction.setCreditorBic(payload.getCreditorBic());
            instruction.setAmount(payload.getAmount() != null ? payload.getAmount() : BigDecimal.ZERO);
            instruction.setCurrency(payload.getCurrency());
            instruction.setReference(payload.getReference());
            if (payload.getVendorInvoiceId() != null) {
                VendorInvoice invoice = bankingService.getVendorInvoice(payload.getVendorInvoiceId());
                instruction.setVendorInvoice(invoice);
                instruction.setCreditorName(invoice.getVendorName());
                instruction.setAmount(invoice.getAmount());
                instruction.setCurrency(invoice.getCurrency());
            }
            if (payload.getCustomerInvoiceId() != null) {
                CustomerInvoice invoice = bankingService.getCustomerInvoice(payload.getCustomerInvoiceId());
                instruction.setCustomerInvoice(invoice);
                instruction.setCreditorName(invoice.getCustomerName());
                instruction.setAmount(invoice.getAmount());
                instruction.setCurrency(invoice.getCurrency());
            }
            instructions.add(instruction);
        }
        PaymentBatch batch = bankingService.createBatch(company, request.getBankAccountId(), instructions);
        return ResponseEntity.created(URI.create("/api/banking/batches/" + batch.getId()))
                .body(PaymentBatchDTO.from(batch));
    }

    @PostMapping("/batches/{id}/approve")
    public ResponseEntity<PaymentBatchDTO> approveBatch(@PathVariable Long id, Principal principal) {
        Company company = getCompany(principal);
        if (company == null) {
            return ResponseEntity.status(401).build();
        }
        PaymentBatch batch = bankingService.approveBatch(id, principal.getName());
        if (!Objects.equals(batch.getCompany().getId(), company.getId())) {
            return ResponseEntity.status(403).build();
        }
        return ResponseEntity.ok(PaymentBatchDTO.from(batch));
    }

    @PostMapping("/batches/{id}/transmit")
    public ResponseEntity<PaymentBatchDTO> transmitBatch(@PathVariable Long id,
                                                         @RequestBody TransmitBatchRequest request,
                                                         @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
                                                         Principal principal) {
        Company company = getCompany(principal);
        if (company == null) {
            return ResponseEntity.status(401).build();
        }
        String reference = request.getReference() != null && !request.getReference().isBlank() ?
                request.getReference() : idempotencyKey;
        if (reference == null || reference.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        PaymentBatch batch = bankingService.markBatchTransmitted(id, reference);
        if (!Objects.equals(batch.getCompany().getId(), company.getId())) {
            return ResponseEntity.status(403).build();
        }
        return ResponseEntity.ok(PaymentBatchDTO.from(batch));
    }

    @GetMapping("/batches/open")
    public ResponseEntity<Page<PaymentBatchDTO>> openBatches(@RequestParam(defaultValue = "0") int page,
                                                             @RequestParam(defaultValue = "20") int size,
                                                             Principal principal) {
        Company company = getCompany(principal);
        if (company == null) {
            return ResponseEntity.status(401).build();
        }
        Pageable pageable = PageRequest.of(page, Math.min(size, 100));
        Page<PaymentBatchDTO> batches = bankingService.findOpenBatches(company, pageable)
                .map(PaymentBatchDTO::from);
        return ResponseEntity.ok(batches);
    }

    @PostMapping("/signatures")
    public ResponseEntity<DigitalSignatureRequestDTO> requestSignature(@RequestBody CreateSignatureRequest body) {
        var saved = bankingService.requestSignature(body.getDocumentType(), body.getDocumentPath(), body.getEmail());
        return ResponseEntity.created(URI.create("/api/banking/signatures/" + saved.getId()))
                .body(DigitalSignatureRequestDTO.from(saved));
    }

    @PostMapping("/signatures/{id}/complete")
    public ResponseEntity<DigitalSignatureRequestDTO> completeSignature(@PathVariable Long id) {
        return ResponseEntity.ok(DigitalSignatureRequestDTO.from(bankingService.markSignatureCompleted(id)));
    }

    @PostMapping("/messages")
    public ResponseEntity<SecureMessageDTO> sendSecureMessage(@RequestBody CreateSecureMessageRequest body, Principal principal) {
        Company company = getCompany(principal);
        if (company == null) {
            return ResponseEntity.status(401).build();
        }
        SecureMessage saved = bankingService.logSecureMessage(company, body.getRecipient(), body.getSubject(), body.getBody(), body.getTransport());
        SecureMessageDTO dto = SecureMessageDTO.from(saved);
        if (!saved.isDelivered()) {
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(dto);
        }
        return ResponseEntity.created(URI.create("/api/banking/messages/" + saved.getId()))
                .body(dto);
    }

    @GetMapping("/pain001/{id}")
    public ResponseEntity<String> getPain001(@PathVariable Long id, Principal principal) {
        Company company = getCompany(principal);
        if (company == null) {
            return ResponseEntity.status(401).build();
        }
        PaymentBatch batch = bankingService.getBatch(id);
        if (!Objects.equals(batch.getCompany().getId(), company.getId())) {
            return ResponseEntity.status(403).build();
        }
        return ResponseEntity.ok(bankingService.generatePain001Xml(batch));
    }
}
