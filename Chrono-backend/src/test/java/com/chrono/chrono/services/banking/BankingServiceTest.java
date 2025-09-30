package com.chrono.chrono.services.banking;

import com.chrono.chrono.entities.Company;
import com.chrono.chrono.entities.accounting.InvoiceStatus;
import com.chrono.chrono.entities.accounting.VendorInvoice;
import com.chrono.chrono.entities.banking.BankAccount;
import com.chrono.chrono.entities.banking.PaymentBatch;
import com.chrono.chrono.entities.banking.PaymentInstruction;
import com.chrono.chrono.entities.banking.PaymentStatus;
import com.chrono.chrono.repositories.CompanyRepository;
import com.chrono.chrono.repositories.accounting.VendorInvoiceRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@Import(BankingService.class)
@ActiveProfiles("test")
class BankingServiceTest {

    @Autowired
    private BankingService bankingService;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private VendorInvoiceRepository vendorInvoiceRepository;

    @Test
    void createApproveAndTransmitBatchUpdatesInvoicesAndProducesPain001() {
        Company company = companyRepository.save(new Company("Chrono Test"));

        BankAccount account = new BankAccount();
        account.setCompany(company);
        account.setName("Main");
        account.setIban("CH9300762011623852957");
        account.setBic("POFICHBEXXX");
        account = bankingService.saveBankAccount(account);

        VendorInvoice vendorInvoice = new VendorInvoice();
        vendorInvoice.setVendorName("Hardware GmbH");
        vendorInvoice.setInvoiceNumber("SUP-1001");
        vendorInvoice.setInvoiceDate(LocalDate.now());
        vendorInvoice.setDueDate(LocalDate.now().plusDays(15));
        vendorInvoice.setAmount(new BigDecimal("500.00"));
        vendorInvoice.setStatus(InvoiceStatus.OPEN);
        vendorInvoice = vendorInvoiceRepository.save(vendorInvoice);

        PaymentInstruction instruction = new PaymentInstruction();
        instruction.setVendorInvoice(vendorInvoice);
        instruction.setCreditorName("Hardware GmbH");
        instruction.setCreditorIban("CH6500762011623852957");
        instruction.setCreditorBic("POFICHBEXXX");
        instruction.setAmount(new BigDecimal("500.00"));
        instruction.setCurrency("CHF");
        instruction.setReference("SUP-1001");

        PaymentBatch batch = bankingService.createBatch(company, account.getId(), List.of(instruction));
        assertThat(batch.getInstructions()).hasSize(1);
        assertThat(batch.getStatus()).isEqualTo(PaymentStatus.PENDING_APPROVAL);

        PaymentBatch approved = bankingService.approveBatch(batch.getId(), "approver@chrono");
        assertThat(approved.getStatus()).isEqualTo(PaymentStatus.APPROVED);
        assertThat(approved.getApprovalBy()).isEqualTo("approver@chrono");

        PaymentBatch transmitted = bankingService.markBatchTransmitted(batch.getId(), "BANK-REF-1");
        assertThat(transmitted.getStatus()).isEqualTo(PaymentStatus.SENT);
        assertThat(transmitted.getTransmissionReference()).isEqualTo("BANK-REF-1");

        VendorInvoice refreshed = vendorInvoiceRepository.findById(vendorInvoice.getId()).orElseThrow();
        assertThat(refreshed.getStatus()).isEqualTo(InvoiceStatus.PAID);

        PaymentBatch idempotent = bankingService.markBatchTransmitted(batch.getId(), "BANK-REF-1");
        assertThat(idempotent.getStatus()).isEqualTo(PaymentStatus.SENT);

        String xml = bankingService.generatePain001Xml(transmitted);
        assertThat(xml).contains("<MsgId>CHRONO-" + transmitted.getId());
        assertThat(xml).contains("<Cdtr><Nm>Hardware GmbH</Nm></Cdtr>");
        assertThat(xml).contains("<InstdAmt Ccy=\"CHF\">500.00</InstdAmt>");
    }
}
