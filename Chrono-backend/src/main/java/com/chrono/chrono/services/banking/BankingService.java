package com.chrono.chrono.services.banking;

import com.chrono.chrono.entities.Company;
import com.chrono.chrono.entities.accounting.CustomerInvoice;
import com.chrono.chrono.entities.accounting.InvoiceStatus;
import com.chrono.chrono.entities.accounting.VendorInvoice;
import com.chrono.chrono.entities.banking.*;
import com.chrono.chrono.exceptions.BankingIntegrationException;
import com.chrono.chrono.repositories.accounting.CustomerInvoiceRepository;
import com.chrono.chrono.repositories.accounting.VendorInvoiceRepository;
import com.chrono.chrono.repositories.banking.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class BankingService {

    private final BankAccountRepository bankAccountRepository;
    private final PaymentBatchRepository paymentBatchRepository;
    private final PaymentInstructionRepository paymentInstructionRepository;
    private final DigitalSignatureRequestRepository digitalSignatureRequestRepository;
    private final SecureMessageRepository secureMessageRepository;
    private final VendorInvoiceRepository vendorInvoiceRepository;
    private final CustomerInvoiceRepository customerInvoiceRepository;
    private final PaymentGatewayClient paymentGatewayClient;
    private final DigitalSignatureProviderClient digitalSignatureProviderClient;
    private final SecureMessagingClient secureMessagingClient;

    public BankingService(BankAccountRepository bankAccountRepository,
                          PaymentBatchRepository paymentBatchRepository,
                          PaymentInstructionRepository paymentInstructionRepository,
                          DigitalSignatureRequestRepository digitalSignatureRequestRepository,
                          SecureMessageRepository secureMessageRepository,
                          VendorInvoiceRepository vendorInvoiceRepository,
                          CustomerInvoiceRepository customerInvoiceRepository,
                          PaymentGatewayClient paymentGatewayClient,
                          DigitalSignatureProviderClient digitalSignatureProviderClient,
                          SecureMessagingClient secureMessagingClient) {
        this.bankAccountRepository = bankAccountRepository;
        this.paymentBatchRepository = paymentBatchRepository;
        this.paymentInstructionRepository = paymentInstructionRepository;
        this.digitalSignatureRequestRepository = digitalSignatureRequestRepository;
        this.secureMessageRepository = secureMessageRepository;
        this.vendorInvoiceRepository = vendorInvoiceRepository;
        this.customerInvoiceRepository = customerInvoiceRepository;
        this.paymentGatewayClient = paymentGatewayClient;
        this.digitalSignatureProviderClient = digitalSignatureProviderClient;
        this.secureMessagingClient = secureMessagingClient;
    }

    @Transactional
    public BankAccount saveBankAccount(BankAccount account) {
        return bankAccountRepository.save(account);
    }

    @Transactional(readOnly = true)
    public List<BankAccount> listAccounts(Company company) {
        return bankAccountRepository.findByCompany(company);
    }

    @Transactional(readOnly = true)
    public BankAccount getAccount(Long id) {
        return bankAccountRepository.findById(id).orElseThrow();
    }

    @Transactional(readOnly = true)
    public VendorInvoice getVendorInvoice(Long id) {
        return vendorInvoiceRepository.findById(id).orElseThrow();
    }

    @Transactional(readOnly = true)
    public CustomerInvoice getCustomerInvoice(Long id) {
        return customerInvoiceRepository.findById(id).orElseThrow();
    }

    @Transactional
    public PaymentBatch createBatch(Company company, Long bankAccountId, List<PaymentInstruction> instructions) {
        BankAccount bankAccount = bankAccountRepository.findById(bankAccountId).orElseThrow();
        if (!Objects.equals(bankAccount.getCompany().getId(), company.getId())) {
            throw new IllegalArgumentException("Bank account does not belong to company");
        }
        PaymentBatch batch = new PaymentBatch();
        batch.setCompany(company);
        batch.setBankAccount(bankAccount);
        batch.setStatus(PaymentStatus.PENDING_APPROVAL);
        PaymentBatch saved = paymentBatchRepository.save(batch);
        List<PaymentInstruction> persistedInstructions = new ArrayList<>();
        for (PaymentInstruction instruction : instructions) {
            if (instruction.getAmount() == null || instruction.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("Payment instruction amount must be positive");
            }
            instruction.setBatch(saved);
            instruction.setCurrency(instruction.getCurrency() != null ? instruction.getCurrency() : "CHF");
            persistedInstructions.add(paymentInstructionRepository.save(instruction));
        }
        saved.setInstructions(persistedInstructions);
        return paymentBatchRepository.save(saved);
    }

    @Transactional
    public PaymentBatch approveBatch(Long batchId, String approver) {
        PaymentBatch batch = paymentBatchRepository.findById(batchId).orElseThrow();
        if (batch.getStatus() != PaymentStatus.PENDING_APPROVAL && batch.getStatus() != PaymentStatus.DRAFT) {
            throw new IllegalStateException("Only draft or pending batches can be approved");
        }
        batch.setStatus(PaymentStatus.APPROVED);
        batch.setApprovalBy(approver);
        batch.setApprovedAt(java.time.LocalDateTime.now());
        return paymentBatchRepository.save(batch);
    }

    @Transactional
    public PaymentBatch markBatchTransmitted(Long batchId, String transmissionReference) {
        PaymentBatch batch = paymentBatchRepository.findById(batchId).orElseThrow();
        if (batch.getStatus() == PaymentStatus.SENT) {
            if (Objects.equals(batch.getTransmissionReference(), transmissionReference)) {
                return batch;
            }
            throw new IllegalStateException("Batch already transmitted with a different reference");
        }
        if (batch.getStatus() != PaymentStatus.APPROVED) {
            throw new IllegalStateException("Only approved batches can be transmitted");
        }
        String pain001Xml = generatePain001Xml(batch);
        PaymentGatewayClient.PaymentSubmissionResult submission =
                paymentGatewayClient.transmit(batch, pain001Xml, transmissionReference);

        batch.setStatus(PaymentStatus.SENT);
        batch.setTransmittedAt(java.time.LocalDateTime.now());
        batch.setTransmissionReference(submission.reference());
        batch.setProviderStatus(submission.providerStatus());
        batch.setProviderMessage(submission.providerMessage());
        batch.getInstructions().forEach(instruction -> {
            if (instruction.getVendorInvoice() != null) {
                VendorInvoice invoice = instruction.getVendorInvoice();
                if (invoice.getStatus() != InvoiceStatus.OPEN) {
                    throw new IllegalStateException("Vendor invoice not open: " + invoice.getInvoiceNumber());
                }
                invoice.setStatus(InvoiceStatus.PAID);
                vendorInvoiceRepository.save(invoice);
            }
            if (instruction.getCustomerInvoice() != null) {
                CustomerInvoice invoice = instruction.getCustomerInvoice();
                if (invoice.getStatus() != InvoiceStatus.OPEN) {
                    throw new IllegalStateException("Customer invoice not open: " + invoice.getInvoiceNumber());
                }
                invoice.setStatus(InvoiceStatus.PAID);
                customerInvoiceRepository.save(invoice);
            }
        });
        return paymentBatchRepository.save(batch);
    }

    @Transactional(readOnly = true)
    public Page<PaymentBatch> findOpenBatches(Company company, Pageable pageable) {
        return paymentBatchRepository.findByCompanyAndStatusIn(company,
                List.of(PaymentStatus.DRAFT, PaymentStatus.PENDING_APPROVAL, PaymentStatus.APPROVED), pageable);
    }

    @Transactional(readOnly = true)
    public PaymentBatch getBatch(Long id) {
        return paymentBatchRepository.findById(id).orElseThrow();
    }

    @Transactional
    public DigitalSignatureRequest requestSignature(String documentType, String path, String email) {
        DigitalSignatureProviderClient.SignatureCreationResult result =
                digitalSignatureProviderClient.createSignatureRequest(documentType, path, email);
        DigitalSignatureRequest request = new DigitalSignatureRequest();
        request.setDocumentType(documentType);
        request.setDocumentPath(path);
        request.setSignerEmail(email);
        request.setStatus(result.status());
        request.setProviderReference(result.providerReference());
        request.setSigningUrl(result.signingUrl());
        request.setProviderStatusMessage(result.providerMessage());
        request.setLastStatusCheck(java.time.LocalDateTime.now());
        if (result.status() == SignatureStatus.COMPLETED) {
            request.setCompletedAt(java.time.LocalDateTime.now());
        }
        return digitalSignatureRequestRepository.save(request);
    }

    @Transactional
    public DigitalSignatureRequest markSignatureCompleted(Long id) {
        DigitalSignatureRequest request = digitalSignatureRequestRepository.findById(id).orElseThrow();
        if (request.getProviderReference() == null) {
            request.setStatus(SignatureStatus.COMPLETED);
            request.setCompletedAt(java.time.LocalDateTime.now());
            request.setProviderStatusMessage("Completed locally without provider reference");
            request.setLastStatusCheck(java.time.LocalDateTime.now());
            return digitalSignatureRequestRepository.save(request);
        }
        DigitalSignatureProviderClient.SignatureStatusUpdate statusUpdate =
                digitalSignatureProviderClient.finalizeSignature(request.getProviderReference());
        request.setStatus(statusUpdate.status());
        request.setProviderStatusMessage(statusUpdate.providerMessage());
        if (statusUpdate.signingUrl() != null) {
            request.setSigningUrl(statusUpdate.signingUrl());
        }
        request.setLastStatusCheck(java.time.LocalDateTime.now());
        if (statusUpdate.status() == SignatureStatus.COMPLETED || statusUpdate.status() == SignatureStatus.FAILED) {
            request.setCompletedAt(java.time.LocalDateTime.now());
        }
        return digitalSignatureRequestRepository.save(request);
    }

    @Transactional(noRollbackFor = BankingIntegrationException.class)
    public SecureMessage logSecureMessage(Company company, String recipient, String subject, String body, String transport) {
        SecureMessage message = new SecureMessage();
        message.setCompany(company);
        message.setRecipient(recipient);
        message.setSubject(subject);
        message.setBody(body);
        message.setTransport(transport);
        SecureMessagingClient.SecureMessageResult result =
                secureMessagingClient.sendSecureMessage(company, recipient, subject, body, transport);
        message.setDelivered(result.delivered());
        message.setProviderReference(result.providerReference());
        message.setProviderStatus(result.providerStatus());
        message.setProviderMessage(result.providerMessage());
        return secureMessageRepository.save(message);
    }

    @Transactional(readOnly = true)
    public String generatePain001Xml(PaymentBatch batch) {
        if (batch.getBankAccount() == null || batch.getBankAccount().getIban() == null) {
            throw new IllegalStateException("Batch requires debtor IBAN");
        }
        DateTimeFormatter dateFormatter = DateTimeFormatter.ISO_DATE;
        String messageId = "CHRONO-" + batch.getId();
        LocalDate executionDate = LocalDate.now();
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        xml.append("<Document xmlns=\"urn:iso:std:iso:20022:tech:xsd:pain.001.001.03\">");
        xml.append("<CstmrCdtTrfInitn>");
        xml.append("<GrpHdr>");
        xml.append("<MsgId>").append(messageId).append("</MsgId>");
        xml.append("<CreDtTm>").append(OffsetDateTime.now()).append("</CreDtTm>");
        xml.append("<NbOfTxs>").append(batch.getInstructions().size()).append("</NbOfTxs>");
        xml.append("</GrpHdr>");
        xml.append("<PmtInf>");
        xml.append("<PmtInfId>").append(messageId).append("</PmtInfId>");
        xml.append("<PmtMtd>TRF</PmtMtd>");
        xml.append("<BtchBookg>true</BtchBookg>");
        xml.append("<ReqdExctnDt>").append(dateFormatter.format(executionDate)).append("</ReqdExctnDt>");
        xml.append("<Dbtr><Nm>").append(batch.getCompany().getName()).append("</Nm></Dbtr>");
        xml.append("<DbtrAcct><Id><IBAN>").append(batch.getBankAccount().getIban()).append("</IBAN></Id></DbtrAcct>");
        if (batch.getBankAccount().getBic() != null && !batch.getBankAccount().getBic().isBlank()) {
            xml.append("<DbtrAgt><FinInstnId><BIC>").append(batch.getBankAccount().getBic()).append("</BIC></FinInstnId></DbtrAgt>");
        } else {
            xml.append("<DbtrAgt><FinInstnId><Othr><Id>NOTPROVIDED</Id></Othr></FinInstnId></DbtrAgt>");
        }
        for (PaymentInstruction instruction : batch.getInstructions()) {
            if (instruction.getCreditorIban() == null) {
                throw new IllegalStateException("Instruction missing creditor IBAN");
            }
            xml.append("<CdtTrfTxInf>");
            xml.append("<PmtId><EndToEndId>")
                    .append(instruction.getReference() != null ? instruction.getReference() : UUID.randomUUID())
                    .append("</EndToEndId></PmtId>");
            xml.append("<Amt><InstdAmt Ccy=\"").append(instruction.getCurrency()).append("\">")
                    .append(instruction.getAmount().setScale(2, RoundingMode.HALF_UP))
                    .append("</InstdAmt></Amt>");
            xml.append("<CdtrAgt><FinInstnId>");
            if (instruction.getCreditorBic() != null && !instruction.getCreditorBic().isBlank()) {
                xml.append("<BIC>").append(instruction.getCreditorBic()).append("</BIC>");
            } else {
                xml.append("<Othr><Id>NOTPROVIDED</Id></Othr>");
            }
            xml.append("</FinInstnId></CdtrAgt>");
            xml.append("<Cdtr><Nm>").append(instruction.getCreditorName()).append("</Nm></Cdtr>");
            xml.append("<CdtrAcct><Id><IBAN>").append(instruction.getCreditorIban()).append("</IBAN></Id></CdtrAcct>");
            if (instruction.getReference() != null) {
                xml.append("<RmtInf><Ustrd>").append(instruction.getReference()).append("</Ustrd></RmtInf>");
            }
            xml.append("</CdtTrfTxInf>");
        }
        xml.append("</PmtInf>");
        xml.append("</CstmrCdtTrfInitn>");
        xml.append("</Document>");
        return xml.toString();
    }
}
