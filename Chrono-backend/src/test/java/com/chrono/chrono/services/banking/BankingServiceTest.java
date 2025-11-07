package com.chrono.chrono.services.banking;

import com.chrono.chrono.entities.Company;
import com.chrono.chrono.entities.banking.BankAccount;
import com.chrono.chrono.entities.banking.DigitalSignatureRequest;
import com.chrono.chrono.entities.banking.PaymentBatch;
import com.chrono.chrono.entities.banking.PaymentStatus;
import com.chrono.chrono.entities.banking.SecureMessage;
import com.chrono.chrono.entities.banking.SignatureStatus;
import com.chrono.chrono.repositories.accounting.CustomerInvoiceRepository;
import com.chrono.chrono.repositories.accounting.VendorInvoiceRepository;
import com.chrono.chrono.repositories.banking.BankAccountRepository;
import com.chrono.chrono.repositories.banking.DigitalSignatureRequestRepository;
import com.chrono.chrono.repositories.banking.PaymentBatchRepository;
import com.chrono.chrono.repositories.banking.PaymentInstructionRepository;
import com.chrono.chrono.repositories.banking.SecureMessageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BankingServiceTest {

    private BankAccountRepository bankAccountRepository;
    private PaymentBatchRepository paymentBatchRepository;
    private PaymentInstructionRepository paymentInstructionRepository;
    private DigitalSignatureRequestRepository digitalSignatureRequestRepository;
    private SecureMessageRepository secureMessageRepository;
    private VendorInvoiceRepository vendorInvoiceRepository;
    private CustomerInvoiceRepository customerInvoiceRepository;
    private PaymentGatewayClient paymentGatewayClient;
    private DigitalSignatureProviderClient digitalSignatureProviderClient;
    private SecureMessagingClient secureMessagingClient;
    private BankingService bankingService;

    @BeforeEach
    void setUp() {
        bankAccountRepository = mock(BankAccountRepository.class);
        paymentBatchRepository = mock(PaymentBatchRepository.class);
        paymentInstructionRepository = mock(PaymentInstructionRepository.class);
        digitalSignatureRequestRepository = mock(DigitalSignatureRequestRepository.class);
        secureMessageRepository = mock(SecureMessageRepository.class);
        vendorInvoiceRepository = mock(VendorInvoiceRepository.class);
        customerInvoiceRepository = mock(CustomerInvoiceRepository.class);
        paymentGatewayClient = mock(PaymentGatewayClient.class);
        digitalSignatureProviderClient = mock(DigitalSignatureProviderClient.class);
        secureMessagingClient = mock(SecureMessagingClient.class);

        bankingService = new BankingService(
                bankAccountRepository,
                paymentBatchRepository,
                paymentInstructionRepository,
                digitalSignatureRequestRepository,
                secureMessageRepository,
                vendorInvoiceRepository,
                customerInvoiceRepository,
                paymentGatewayClient,
                digitalSignatureProviderClient,
                secureMessagingClient);
    }

    @Test
    void markBatchTransmittedCallsGatewayAndSetsReference() {
        Company company = new Company();
        company.setId(10L);
        company.setName("Chrono AG");

        BankAccount account = new BankAccount();
        account.setCompany(company);
        account.setIban("CH9300762011623852957");
        account.setBic("POFICHBEXXX");

        PaymentBatch batch = new PaymentBatch();
        batch.setId(7L);
        batch.setCompany(company);
        batch.setBankAccount(account);
        batch.setStatus(PaymentStatus.APPROVED);
        batch.setInstructions(Collections.emptyList());

        when(paymentBatchRepository.findById(7L)).thenReturn(Optional.of(batch));
        when(paymentBatchRepository.save(any(PaymentBatch.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(paymentGatewayClient.transmit(any(PaymentBatch.class), any(String.class), any(String.class)))
                .thenReturn(new PaymentGatewayClient.PaymentSubmissionResult("REF-123", "SENT", "ok"));

        PaymentBatch updated = bankingService.markBatchTransmitted(7L, "IDEMPOTENT-REF");

        assertThat(updated.getStatus()).isEqualTo(PaymentStatus.SENT);
        assertThat(updated.getTransmissionReference()).isEqualTo("REF-123");
        assertThat(updated.getProviderStatus()).isEqualTo("SENT");
        verify(paymentGatewayClient).transmit(any(PaymentBatch.class), any(String.class), any(String.class));
    }

    @Test
    void requestSignaturePersistsProviderFields() {
        when(digitalSignatureProviderClient.createSignatureRequest("PAYROLL", "/tmp/doc.pdf", "user@example.com"))
                .thenReturn(new DigitalSignatureProviderClient.SignatureCreationResult(SignatureStatus.IN_PROGRESS,
                        "SIG-42", "https://sign.example.com/42", "created"));
        when(digitalSignatureRequestRepository.save(any(DigitalSignatureRequest.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        DigitalSignatureRequest request = bankingService.requestSignature("PAYROLL", "/tmp/doc.pdf", "user@example.com");

        assertThat(request.getProviderReference()).isEqualTo("SIG-42");
        assertThat(request.getSigningUrl()).isEqualTo("https://sign.example.com/42");
        assertThat(request.getStatus()).isEqualTo(SignatureStatus.IN_PROGRESS);
    }

    @Test
    void logSecureMessageStoresProviderInformation() {
        Company company = new Company();
        company.setName("Chrono AG");
        when(secureMessagingClient.sendSecureMessage(any(), any(), any(), any(), any()))
                .thenReturn(new SecureMessagingClient.SecureMessageResult(true, "MSG-1", "DELIVERED", "ok"));
        when(secureMessageRepository.save(any(SecureMessage.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        SecureMessage message = bankingService.logSecureMessage(company, "bank@example.com", "Subject", "Body", "EBICS");

        assertThat(message.isDelivered()).isTrue();
        assertThat(message.getProviderReference()).isEqualTo("MSG-1");
        assertThat(message.getProviderStatus()).isEqualTo("DELIVERED");
    }
}
