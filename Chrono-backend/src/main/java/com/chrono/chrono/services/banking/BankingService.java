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
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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
        normalizeAndValidateAccount(account);
        return bankAccountRepository.save(account);
    }

    @Transactional(readOnly = true)
    public List<BankAccount> listAccounts(Company company) {
        return bankAccountRepository.findByCompany(company);
    }

    @Transactional
    public BankAccount updateBankAccount(Company company, Long id, BankAccount updatedAccount) {
        BankAccount existing = getAccount(company, id);
        normalizeAndValidateAccount(updatedAccount);
        existing.setName(updatedAccount.getName());
        existing.setIban(updatedAccount.getIban());
        existing.setBic(updatedAccount.getBic());
        existing.setClearingNumber(updatedAccount.getClearingNumber());
        return bankAccountRepository.save(existing);
    }

    @Transactional
    public void deleteBankAccount(Company company, Long id) {
        BankAccount account = getAccount(company, id);
        if (paymentBatchRepository.countByBankAccount(account) > 0) {
            throw new IllegalStateException("Bank account is used by payment batches and cannot be deleted");
        }
        bankAccountRepository.delete(account);
    }

    @Transactional(readOnly = true)
    public BankAccount getAccount(Company company, Long id) {
        return bankAccountRepository.findByIdAndCompany(id, company).orElseThrow();
    }

    @Transactional(readOnly = true)
    public VendorInvoice getVendorInvoice(Long id) {
        return vendorInvoiceRepository.findById(id).orElseThrow();
    }

    @Transactional(readOnly = true)
    public VendorInvoice getVendorInvoice(Company company, Long id) {
        return vendorInvoiceRepository.findByIdAndCompany(id, company).orElseThrow();
    }

    @Transactional(readOnly = true)
    public CustomerInvoice getCustomerInvoice(Long id) {
        return customerInvoiceRepository.findById(id).orElseThrow();
    }

    @Transactional(readOnly = true)
    public CustomerInvoice getCustomerInvoice(Company company, Long id) {
        return customerInvoiceRepository.findByIdAndCompany(id, company).orElseThrow();
    }

    @Transactional
    public PaymentBatch createBatch(Company company, Long bankAccountId, List<PaymentInstruction> instructions) {
        BankAccount bankAccount = getAccount(company, bankAccountId);
        PaymentBatch batch = new PaymentBatch();
        batch.setCompany(company);
        batch.setBankAccount(bankAccount);
        batch.setStatus(PaymentStatus.PENDING_APPROVAL);
        PaymentBatch saved = paymentBatchRepository.save(batch);
        List<PaymentInstruction> persistedInstructions = new ArrayList<>();
        for (PaymentInstruction instruction : instructions) {
            validateLinkedInvoices(company, instruction);
            enrichInstructionFromLinkedInvoices(instruction);
            normalizeAndValidateInstruction(instruction);
            instruction.setBatch(saved);
            persistedInstructions.add(paymentInstructionRepository.save(instruction));
        }
        saved.setInstructions(persistedInstructions);
        return paymentBatchRepository.save(saved);
    }

    @Transactional(readOnly = true)
    public List<PaymentBatch> listBatches(Company company) {
        return paymentBatchRepository.findByCompanyOrderByCreatedAtDesc(company);
    }

    @Transactional
    public PaymentBatch approveBatch(Company company, Long batchId, String approver) {
        PaymentBatch batch = getBatch(company, batchId);
        if (batch.getStatus() != PaymentStatus.PENDING_APPROVAL && batch.getStatus() != PaymentStatus.DRAFT) {
            throw new IllegalStateException("Only draft or pending batches can be approved");
        }
        batch.setStatus(PaymentStatus.APPROVED);
        batch.setApprovalBy(approver);
        batch.setApprovedAt(LocalDateTime.now());
        return paymentBatchRepository.save(batch);
    }

    @Transactional
    public PaymentBatch markBatchTransmitted(Company company, Long batchId, String transmissionReference) {
        return markBatchTransmitted(company, batchId, transmissionReference, null);
    }

    @Transactional
    public PaymentBatch markBatchTransmitted(Company company, Long batchId, String transmissionReference, String transmittedBy) {
        PaymentBatch batch = getBatch(company, batchId);
        if (batch.getStatus() == PaymentStatus.SENT) {
            if (Objects.equals(batch.getTransmissionReference(), transmissionReference)) {
                return batch;
            }
            throw new IllegalStateException("Batch already transmitted with a different reference");
        }
        if (batch.getStatus() != PaymentStatus.APPROVED) {
            throw new IllegalStateException("Only approved batches can be transmitted");
        }
        if (transmittedBy != null && transmittedBy.equals(batch.getApprovalBy())) {
            throw new IllegalStateException("Payment batches must be transmitted by a different user than the approver");
        }
        String pain001Xml = generatePain001Xml(batch);
        PaymentGatewayClient.PaymentSubmissionResult submission =
                paymentGatewayClient.transmit(batch, pain001Xml, transmissionReference);

        batch.setStatus(PaymentStatus.SENT);
        batch.setTransmittedAt(LocalDateTime.now());
        batch.setTransmissionReference(submission.reference());
        batch.setProviderStatus(submission.providerStatus());
        batch.setProviderMessage(submission.providerMessage());
        batch.setDeliveryChannel(submission.deliveryChannel());
        batch.setProviderArtifactPath(submission.providerArtifactPath());
        batch.setProviderArtifactName(submission.providerArtifactName());
        batch.getInstructions().forEach(instruction -> {
            if (instruction.getVendorInvoice() != null) {
                VendorInvoice invoice = instruction.getVendorInvoice();
                if (!sameCompany(invoice.getCompany(), company)) {
                    throw new IllegalStateException("Vendor invoice belongs to another company");
                }
                if (invoice.getStatus() != InvoiceStatus.OPEN) {
                    throw new IllegalStateException("Vendor invoice not open: " + invoice.getInvoiceNumber());
                }
                invoice.setStatus(InvoiceStatus.PAID);
                vendorInvoiceRepository.save(invoice);
            }
            if (instruction.getCustomerInvoice() != null) {
                CustomerInvoice invoice = instruction.getCustomerInvoice();
                if (!sameCompany(invoice.getCompany(), company)) {
                    throw new IllegalStateException("Customer invoice belongs to another company");
                }
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
    public PaymentBatch getBatch(Company company, Long id) {
        return paymentBatchRepository.findByIdAndCompany(id, company).orElseThrow();
    }

    @Transactional
    public DigitalSignatureRequest requestSignature(Company company, String documentType, String path, String email) {
        DigitalSignatureProviderClient.SignatureCreationResult result =
                digitalSignatureProviderClient.createSignatureRequest(documentType, path, email);
        DigitalSignatureRequest request = new DigitalSignatureRequest();
        request.setCompany(company);
        request.setDocumentType(documentType);
        request.setDocumentPath(path);
        request.setSignerEmail(email);
        request.setStatus(result.status());
        request.setProviderReference(result.providerReference());
        request.setSigningUrl(result.signingUrl());
        request.setProviderStatusMessage(result.providerMessage());
        request.setLastStatusCheck(LocalDateTime.now());
        if (result.status() == SignatureStatus.COMPLETED) {
            request.setCompletedAt(LocalDateTime.now());
        }
        return digitalSignatureRequestRepository.save(request);
    }

    @Transactional
    public DigitalSignatureRequest refreshSignatureStatus(Company company, Long id) {
        DigitalSignatureRequest request = getSignatureRequest(company, id);
        if (request.getProviderReference() == null) {
            request.setLastStatusCheck(LocalDateTime.now());
            if (request.getStatus() == SignatureStatus.PENDING) {
                request.setStatus(SignatureStatus.IN_PROGRESS);
            }
            request.setProviderStatusMessage("Status refreshed without provider reference");
            return digitalSignatureRequestRepository.save(request);
        }
        DigitalSignatureProviderClient.SignatureStatusUpdate statusUpdate =
                digitalSignatureProviderClient.fetchStatus(request.getProviderReference());
        return applySignatureStatusUpdate(request, statusUpdate, false);
    }

    @Transactional(readOnly = true)
    public List<DigitalSignatureRequest> listSignatureRequests(Company company) {
        return digitalSignatureRequestRepository.findByCompanyOrderByRequestedAtDesc(company);
    }

    @Transactional
    public DigitalSignatureRequest markSignatureCompleted(Company company, Long id) {
        DigitalSignatureRequest request = getSignatureRequest(company, id);
        if (request.getProviderReference() == null) {
            request.setStatus(SignatureStatus.COMPLETED);
            request.setCompletedAt(LocalDateTime.now());
            request.setProviderStatusMessage("Completed locally without provider reference");
            request.setLastStatusCheck(LocalDateTime.now());
            return digitalSignatureRequestRepository.save(request);
        }
        DigitalSignatureProviderClient.SignatureStatusUpdate statusUpdate =
                digitalSignatureProviderClient.finalizeSignature(request.getProviderReference());
        return applySignatureStatusUpdate(request, statusUpdate, true);
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
    public List<SecureMessage> listSecureMessages(Company company) {
        return secureMessageRepository.findByCompanyOrderBySentAtDesc(company);
    }

    @Transactional(readOnly = true)
    public String generatePain001Xml(PaymentBatch batch) {
        if (batch.getCompany() == null || !StringUtils.hasText(batch.getCompany().getName())) {
            throw new IllegalStateException("Batch requires company name");
        }
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
        xml.append("<Dbtr><Nm>").append(escapeXml(batch.getCompany().getName())).append("</Nm></Dbtr>");
        xml.append("<DbtrAcct><Id><IBAN>").append(escapeXml(batch.getBankAccount().getIban())).append("</IBAN></Id></DbtrAcct>");
        if (batch.getBankAccount().getBic() != null && !batch.getBankAccount().getBic().isBlank()) {
            xml.append("<DbtrAgt><FinInstnId><BIC>").append(escapeXml(batch.getBankAccount().getBic())).append("</BIC></FinInstnId></DbtrAgt>");
        } else {
            xml.append("<DbtrAgt><FinInstnId><Othr><Id>NOTPROVIDED</Id></Othr></FinInstnId></DbtrAgt>");
        }
        for (PaymentInstruction instruction : batch.getInstructions()) {
            if (instruction.getCreditorIban() == null) {
                throw new IllegalStateException("Instruction missing creditor IBAN");
            }
            xml.append("<CdtTrfTxInf>");
            xml.append("<PmtId><EndToEndId>")
                    .append(escapeXml(instruction.getReference() != null ? instruction.getReference() : UUID.randomUUID().toString()))
                    .append("</EndToEndId></PmtId>");
            xml.append("<Amt><InstdAmt Ccy=\"").append(instruction.getCurrency()).append("\">")
                    .append(instruction.getAmount().setScale(2, RoundingMode.HALF_UP))
                    .append("</InstdAmt></Amt>");
            xml.append("<CdtrAgt><FinInstnId>");
            if (instruction.getCreditorBic() != null && !instruction.getCreditorBic().isBlank()) {
                xml.append("<BIC>").append(escapeXml(instruction.getCreditorBic())).append("</BIC>");
            } else {
                xml.append("<Othr><Id>NOTPROVIDED</Id></Othr>");
            }
            xml.append("</FinInstnId></CdtrAgt>");
            xml.append("<Cdtr><Nm>").append(escapeXml(instruction.getCreditorName())).append("</Nm></Cdtr>");
            xml.append("<CdtrAcct><Id><IBAN>").append(escapeXml(instruction.getCreditorIban())).append("</IBAN></Id></CdtrAcct>");
            if (instruction.getReference() != null) {
                xml.append("<RmtInf><Ustrd>").append(escapeXml(instruction.getReference())).append("</Ustrd></RmtInf>");
            }
            xml.append("</CdtTrfTxInf>");
        }
        xml.append("</PmtInf>");
        xml.append("</CstmrCdtTrfInitn>");
        xml.append("</Document>");
        return xml.toString();
    }

    private void enrichInstructionFromLinkedInvoices(PaymentInstruction instruction) {
        if (instruction.getVendorInvoice() != null) {
            VendorInvoice invoice = instruction.getVendorInvoice();
            instruction.setCreditorName(invoice.getVendorName());
            instruction.setAmount(invoice.getOpenAmount());
            instruction.setCurrency(invoice.getCurrency());
            if (instruction.getReference() == null || instruction.getReference().isBlank()) {
                instruction.setReference(invoice.getInvoiceNumber());
            }
        }
        if (instruction.getCustomerInvoice() != null) {
            CustomerInvoice invoice = instruction.getCustomerInvoice();
            instruction.setCreditorName(invoice.getCustomerName());
            instruction.setAmount(invoice.getOpenAmount());
            instruction.setCurrency(invoice.getCurrency());
            if (instruction.getReference() == null || instruction.getReference().isBlank()) {
                instruction.setReference(invoice.getInvoiceNumber());
            }
        }
    }

    private void validateLinkedInvoices(Company company, PaymentInstruction instruction) {
        if (instruction.getVendorInvoice() != null
                && !sameCompany(instruction.getVendorInvoice().getCompany(), company)) {
            throw new IllegalArgumentException("Vendor invoice belongs to another company");
        }
        if (instruction.getCustomerInvoice() != null
                && !sameCompany(instruction.getCustomerInvoice().getCompany(), company)) {
            throw new IllegalArgumentException("Customer invoice belongs to another company");
        }
    }

    private boolean sameCompany(Company left, Company right) {
        return left != null && right != null && Objects.equals(left.getId(), right.getId());
    }

    private void normalizeAndValidateAccount(BankAccount account) {
        if (account == null) {
            throw new IllegalArgumentException("Bank account is required");
        }
        account.setName(normalizeRequiredText(account.getName(), "Bank account name"));
        account.setIban(normalizeAndValidateIban(account.getIban(), "Debtor IBAN"));
        account.setBic(normalizeOptionalBankCode(account.getBic()));
        account.setClearingNumber(normalizeOptionalText(account.getClearingNumber()));
    }

    private void normalizeAndValidateInstruction(PaymentInstruction instruction) {
        if (instruction == null) {
            throw new IllegalArgumentException("Payment instruction is required");
        }
        instruction.setCreditorName(normalizeRequiredText(instruction.getCreditorName(), "Creditor name"));
        instruction.setCreditorIban(normalizeAndValidateIban(instruction.getCreditorIban(), "Creditor IBAN"));
        instruction.setCreditorBic(normalizeOptionalBankCode(instruction.getCreditorBic()));
        instruction.setCurrency(normalizeCurrency(instruction.getCurrency()));
        instruction.setReference(normalizeOptionalText(instruction.getReference()));
        if (instruction.getAmount() == null || instruction.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Payment instruction amount must be positive");
        }
    }

    private String normalizeAndValidateIban(String iban, String label) {
        String normalized = normalizeOptionalText(iban);
        if (!StringUtils.hasText(normalized)) {
            throw new IllegalArgumentException(label + " is required");
        }
        String compact = normalized.replace(" ", "").toUpperCase(Locale.ROOT);
        if (compact.length() < 15 || compact.length() > 34
                || !Character.isLetter(compact.charAt(0))
                || !Character.isLetter(compact.charAt(1))) {
            throw new IllegalArgumentException(label + " is invalid");
        }
        return compact;
    }

    private String normalizeCurrency(String currency) {
        String normalized = normalizeOptionalText(currency);
        return StringUtils.hasText(normalized) ? normalized.toUpperCase(Locale.ROOT) : "CHF";
    }

    private String normalizeOptionalBankCode(String value) {
        String normalized = normalizeOptionalText(value);
        return StringUtils.hasText(normalized) ? normalized.toUpperCase(Locale.ROOT) : null;
    }

    private String normalizeRequiredText(String value, String fieldLabel) {
        String normalized = normalizeOptionalText(value);
        if (!StringUtils.hasText(normalized)) {
            throw new IllegalArgumentException(fieldLabel + " is required");
        }
        return normalized;
    }

    private String normalizeOptionalText(String value) {
        return value == null ? null : value.trim();
    }

    private String escapeXml(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    private DigitalSignatureRequest getSignatureRequest(Company company, Long id) {
        return digitalSignatureRequestRepository.findByIdAndCompany(id, company).orElseThrow();
    }

    private DigitalSignatureRequest applySignatureStatusUpdate(DigitalSignatureRequest request,
                                                               DigitalSignatureProviderClient.SignatureStatusUpdate statusUpdate,
                                                               boolean closeRequestWhenTerminal) {
        request.setStatus(statusUpdate.status());
        request.setProviderStatusMessage(statusUpdate.providerMessage());
        if (statusUpdate.signingUrl() != null) {
            request.setSigningUrl(statusUpdate.signingUrl());
        }
        request.setLastStatusCheck(LocalDateTime.now());
        if (statusUpdate.status() == SignatureStatus.COMPLETED ||
                statusUpdate.status() == SignatureStatus.FAILED) {
            request.setCompletedAt(LocalDateTime.now());
        } else if (closeRequestWhenTerminal) {
            request.setCompletedAt(null);
        }
        return digitalSignatureRequestRepository.save(request);
    }
}
