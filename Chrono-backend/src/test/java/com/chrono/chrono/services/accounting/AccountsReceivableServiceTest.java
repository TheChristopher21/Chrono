package com.chrono.chrono.services.accounting;

import com.chrono.chrono.dto.InvoiceSummaryDTO;
import com.chrono.chrono.entities.Project;
import com.chrono.chrono.entities.accounting.CustomerInvoice;
import com.chrono.chrono.repositories.accounting.CustomerInvoiceRepository;
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
@Import({AccountingService.class, AccountsReceivableService.class})
@ActiveProfiles("test")
class AccountsReceivableServiceTest {

    @Autowired
    private AccountsReceivableService accountsReceivableService;

    @Autowired
    private CustomerInvoiceRepository customerInvoiceRepository;

    @Test
    void recordProjectInvoiceCreatesReceivableAndJournalEntry() {
        Project project = new Project();
        project.setId(99L);

        InvoiceSummaryDTO summary = new InvoiceSummaryDTO();
        summary.setCustomerName("Client GmbH");
        summary.setProjectId(99L);
        summary.setEndDate(LocalDate.of(2024, 6, 30));
        summary.setTotalAmount(new BigDecimal("2750.00"));
        summary.setCurrency("CHF");

        CustomerInvoice saved = accountsReceivableService.recordProjectInvoice(project, summary);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getInvoiceNumber()).contains("PRJ99-");
        assertThat(saved.getDueDate()).isEqualTo(summary.getEndDate().plusDays(30));
        assertThat(saved.getJournalEntry()).isNotNull();
        assertThat(saved.getJournalEntry().getLines()).hasSize(2);

        CustomerInvoice reloaded = customerInvoiceRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getJournalEntry()).isNotNull();
    }
}
