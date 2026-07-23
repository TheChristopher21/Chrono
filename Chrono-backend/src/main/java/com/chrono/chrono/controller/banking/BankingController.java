package com.chrono.chrono.controller.banking;

import com.chrono.chrono.dto.banking.BankAccountDTO;
import com.chrono.chrono.dto.banking.CreateBankAccountRequest;
import com.chrono.chrono.dto.banking.CreatePaymentBatchRequest;
import com.chrono.chrono.dto.banking.CreateSecureMessageRequest;
import com.chrono.chrono.dto.banking.CreateSignatureRequest;
import com.chrono.chrono.dto.banking.DigitalSignatureRequestDTO;
import com.chrono.chrono.dto.banking.PaymentBatchDTO;
import com.chrono.chrono.dto.banking.PaymentInstructionRequest;
import com.chrono.chrono.dto.banking.SecureMessageDTO;
import com.chrono.chrono.dto.banking.TransmitBatchRequest;
import com.chrono.chrono.entities.Company;
import com.chrono.chrono.entities.User;
import com.chrono.chrono.entities.accounting.CustomerInvoice;
import com.chrono.chrono.entities.accounting.VendorInvoice;
import com.chrono.chrono.entities.banking.BankAccount;
import com.chrono.chrono.entities.banking.PaymentBatch;
import com.chrono.chrono.entities.banking.PaymentInstruction;
import com.chrono.chrono.entities.banking.PaymentStatus;
import com.chrono.chrono.entities.banking.SecureMessage;
import com.chrono.chrono.services.UserService;
import com.chrono.chrono.services.banking.BankingService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.Principal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

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

    @PutMapping("/accounts/{id}")
    public ResponseEntity<BankAccountDTO> updateAccount(@PathVariable Long id,
                                                        @RequestBody CreateBankAccountRequest request,
                                                        Principal principal) {
        Company company = getCompany(principal);
        if (company == null) {
            return ResponseEntity.status(401).build();
        }
        return handleBankingRequest(() -> ResponseEntity.ok(
                BankAccountDTO.from(bankingService.updateBankAccount(company, id, request.toEntity(company)))));
    }

    @DeleteMapping("/accounts/{id}")
    public ResponseEntity<Void> deleteAccount(@PathVariable Long id, Principal principal) {
        Company company = getCompany(principal);
        if (company == null) {
            return ResponseEntity.status(401).build();
        }
        return handleBankingRequest(() -> {
            bankingService.deleteBankAccount(company, id);
            return ResponseEntity.noContent().build();
        });
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
        return handleBankingRequest(() -> {
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
                    VendorInvoice invoice = bankingService.getVendorInvoice(company, payload.getVendorInvoiceId());
                    instruction.setVendorInvoice(invoice);
                }
                if (payload.getCustomerInvoiceId() != null) {
                    CustomerInvoice invoice = bankingService.getCustomerInvoice(company, payload.getCustomerInvoiceId());
                    instruction.setCustomerInvoice(invoice);
                }
                instructions.add(instruction);
            }
            PaymentBatch batch = bankingService.createBatch(company, request.getBankAccountId(), instructions);
            return ResponseEntity.created(URI.create("/api/banking/batches/" + batch.getId()))
                    .body(PaymentBatchDTO.from(batch));
        });
    }

    @GetMapping("/batches")
    public ResponseEntity<List<PaymentBatchDTO>> listBatches(@RequestParam(required = false) PaymentStatus status,
                                                             @RequestParam(required = false) Long bankAccountId,
                                                             Principal principal) {
        Company company = getCompany(principal);
        if (company == null) {
            return ResponseEntity.status(401).build();
        }
        List<PaymentBatchDTO> batches = bankingService.listBatches(company).stream()
                .filter(batch -> status == null || batch.getStatus() == status)
                .filter(batch -> bankAccountId == null || (batch.getBankAccount() != null && bankAccountId.equals(batch.getBankAccount().getId())))
                .map(PaymentBatchDTO::from)
                .toList();
        return ResponseEntity.ok(batches);
    }

    @GetMapping("/batches/{id}")
    public ResponseEntity<PaymentBatchDTO> getBatch(@PathVariable Long id, Principal principal) {
        Company company = getCompany(principal);
        if (company == null) {
            return ResponseEntity.status(401).build();
        }
        return handleBankingRequest(() -> ResponseEntity.ok(PaymentBatchDTO.from(bankingService.getBatch(company, id))));
    }

    @PostMapping("/batches/{id}/approve")
    @PreAuthorize("hasAnyRole('ADMIN','SUPERADMIN')")
    public ResponseEntity<PaymentBatchDTO> approveBatch(@PathVariable Long id, Principal principal) {
        Company company = getCompany(principal);
        if (company == null) {
            return ResponseEntity.status(401).build();
        }
        return handleBankingRequest(() ->
                ResponseEntity.ok(PaymentBatchDTO.from(bankingService.approveBatch(company, id, principal.getName()))));
    }

    @PostMapping("/batches/{id}/transmit")
    @PreAuthorize("hasAnyRole('ADMIN','SUPERADMIN')")
    public ResponseEntity<PaymentBatchDTO> transmitBatch(@PathVariable Long id,
                                                         @RequestBody TransmitBatchRequest request,
                                                         @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
                                                         Principal principal) {
        Company company = getCompany(principal);
        if (company == null) {
            return ResponseEntity.status(401).build();
        }
        String reference = request.getReference() != null && !request.getReference().isBlank()
                ? request.getReference()
                : idempotencyKey;
        if (reference == null || reference.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        return handleBankingRequest(() ->
                ResponseEntity.ok(PaymentBatchDTO.from(bankingService.markBatchTransmitted(company, id, reference, principal.getName()))));
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
    public ResponseEntity<DigitalSignatureRequestDTO> requestSignature(@RequestBody CreateSignatureRequest body,
                                                                       Principal principal) {
        Company company = getCompany(principal);
        if (company == null) {
            return ResponseEntity.status(401).build();
        }
        return handleBankingRequest(() -> {
            var saved = bankingService.requestSignature(company, body.getDocumentType(), body.getDocumentPath(), body.getEmail());
            return ResponseEntity.created(URI.create("/api/banking/signatures/" + saved.getId()))
                    .body(DigitalSignatureRequestDTO.from(saved));
        });
    }

    @GetMapping("/signatures")
    public ResponseEntity<List<DigitalSignatureRequestDTO>> listSignatures(Principal principal) {
        Company company = getCompany(principal);
        if (company == null) {
            return ResponseEntity.status(401).build();
        }
        return ResponseEntity.ok(bankingService.listSignatureRequests(company).stream()
                .map(DigitalSignatureRequestDTO::from)
                .toList());
    }

    @PostMapping("/signatures/{id}/refresh")
    public ResponseEntity<DigitalSignatureRequestDTO> refreshSignature(@PathVariable Long id, Principal principal) {
        Company company = getCompany(principal);
        if (company == null) {
            return ResponseEntity.status(401).build();
        }
        return handleBankingRequest(() ->
                ResponseEntity.ok(DigitalSignatureRequestDTO.from(bankingService.refreshSignatureStatus(company, id))));
    }

    @PostMapping("/signatures/{id}/complete")
    public ResponseEntity<DigitalSignatureRequestDTO> completeSignature(@PathVariable Long id, Principal principal) {
        Company company = getCompany(principal);
        if (company == null) {
            return ResponseEntity.status(401).build();
        }
        return handleBankingRequest(() ->
                ResponseEntity.ok(DigitalSignatureRequestDTO.from(bankingService.markSignatureCompleted(company, id))));
    }

    @PostMapping(value = "/signatures/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> uploadSignatureDocument(@RequestParam("file") MultipartFile file,
                                                                       Principal principal) {
        Company company = getCompany(principal);
        if (company == null) {
            return ResponseEntity.status(401).build();
        }
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        try {
            Path directory = Path.of("signature-documents").resolve("company-" + company.getId());
            Files.createDirectories(directory);
            String originalName = sanitizeFileName(file.getOriginalFilename());
            String targetName = UUID.randomUUID() + "-" + originalName;
            Path target = directory.resolve(targetName);
            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return ResponseEntity.ok(Map.of(
                    "documentPath", target.toAbsolutePath().toString(),
                    "fileName", originalName,
                    "size", file.getSize()
            ));
        } catch (IOException ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping("/messages")
    public ResponseEntity<SecureMessageDTO> sendSecureMessage(@RequestBody CreateSecureMessageRequest body, Principal principal) {
        Company company = getCompany(principal);
        if (company == null) {
            return ResponseEntity.status(401).build();
        }
        return handleBankingRequest(() -> {
            SecureMessage saved = bankingService.logSecureMessage(company, body.getRecipient(), body.getSubject(), body.getBody(), body.getTransport());
            SecureMessageDTO dto = SecureMessageDTO.from(saved);
            if (!saved.isDelivered()) {
                return ResponseEntity.status(HttpStatus.ACCEPTED).body(dto);
            }
            return ResponseEntity.created(URI.create("/api/banking/messages/" + saved.getId()))
                    .body(dto);
        });
    }

    @GetMapping("/messages")
    public ResponseEntity<List<SecureMessageDTO>> listMessages(Principal principal) {
        Company company = getCompany(principal);
        if (company == null) {
            return ResponseEntity.status(401).build();
        }
        return ResponseEntity.ok(bankingService.listSecureMessages(company).stream()
                .map(SecureMessageDTO::from)
                .toList());
    }

    @GetMapping("/pain001/{id}")
    public ResponseEntity<String> getPain001(@PathVariable Long id, Principal principal) {
        Company company = getCompany(principal);
        if (company == null) {
            return ResponseEntity.status(401).build();
        }
        return handleBankingRequest(() -> {
            PaymentBatch batch = bankingService.getBatch(company, id);
            return ResponseEntity.ok(bankingService.generatePain001Xml(batch));
        });
    }

    private String sanitizeFileName(String originalName) {
        if (originalName == null || originalName.isBlank()) {
            return "signature-document.bin";
        }
        return originalName.replace("\\", "_").replace("/", "_");
    }

    private <T> ResponseEntity<T> handleBankingRequest(BankingResponseSupplier<T> supplier) {
        try {
            return supplier.get();
        } catch (NoSuchElementException ex) {
            return ResponseEntity.notFound().build();
        } catch (IllegalArgumentException | IllegalStateException ex) {
            return ResponseEntity.badRequest().build();
        }
    }

    @FunctionalInterface
    private interface BankingResponseSupplier<T> {
        ResponseEntity<T> get();
    }
}
