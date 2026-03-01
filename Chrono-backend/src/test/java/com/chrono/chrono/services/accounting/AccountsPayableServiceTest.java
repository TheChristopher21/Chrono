package com.chrono.chrono.services.accounting;

import com.chrono.chrono.entities.accounting.InvoiceStatus;
import com.chrono.chrono.entities.accounting.JournalEntry;
import com.chrono.chrono.entities.accounting.VendorInvoice;
import com.chrono.chrono.repositories.accounting.VendorInvoiceRepository;
import com.chrono.chrono.services.accounting.AccountingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@Import({AccountingService.class, AccountsPayableService.class})
@ActiveProfiles("test")
class AccountsPayableServiceTest {

    @Autowired
    private AccountsPayableService accountsPayableService;

    @Autowired
    private VendorInvoiceRepository vendorInvoiceRepository;

    @Test
    void recordVendorInvoiceAssignsDefaultsAndPostsJournalEntry() {
        VendorInvoice invoice = new VendorInvoice();
        invoice.setVendorName("Supply AG");
        invoice.setAmount(new BigDecimal("1500.00"));
        invoice.setInvoiceNumber(null);
        invoice.setInvoiceDate(null);
        invoice.setDueDate(null);
        invoice.setStatus(InvoiceStatus.OPEN);

        VendorInvoice saved = accountsPayableService.recordVendorInvoice(invoice);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getInvoiceNumber()).isNotBlank();
        assertThat(saved.getInvoiceDate()).isEqualTo(LocalDate.now());
        assertThat(saved.getDueDate()).isEqualTo(LocalDate.now().plusDays(30));
        assertThat(saved.getJournalEntry()).isNotNull();

        JournalEntry entry = saved.getJournalEntry();
        assertThat(entry.getLines()).hasSize(2);
        assertThat(entry.getLines().get(0).getDebit()).isEqualByComparingTo(new BigDecimal("1500.00"));
        assertThat(entry.getLines().get(1).getCredit()).isEqualByComparingTo(new BigDecimal("1500.00"));

        VendorInvoice reloaded = vendorInvoiceRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getJournalEntry()).isNotNull();
    }
}
