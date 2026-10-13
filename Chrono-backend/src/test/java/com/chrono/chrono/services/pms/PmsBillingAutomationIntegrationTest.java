package com.chrono.chrono.services.pms;
import com.chrono.chrono.dto.pms.*;
import com.chrono.chrono.dto.pms.PmsDeliveryDtos.*;
import com.chrono.chrono.dto.pms.PmsBillingAutomationDtos.*;
import com.chrono.chrono.entities.Company;
import com.chrono.chrono.entities.pms.*;
import com.chrono.chrono.repositories.CompanyRepository;
import com.chrono.chrono.repositories.pms.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.*;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.web.server.ResponseStatusException;
import jakarta.mail.*;
import jakarta.mail.internet.MimeMessage;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.nio.charset.StandardCharsets;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@DataJpaTest(properties={"spring.jpa.hibernate.ddl-auto=create-drop","app.pms.billing.scheduler.enabled=false","app.pms.delivery.worker.enabled=true","app.pms.delivery.worker.interval-ms=3600000"})
@Import({PmsBillingSettingsService.class,PmsDeliveryService.class,PmsDeliveryWorker.class,PmsDunningService.class,
 PmsBankReconciliationService.class,PmsReceivablesService.class,PmsFinancialPeriodService.class,PmsAuditWriter.class})
@ActiveProfiles("test") @Transactional(propagation=Propagation.NOT_SUPPORTED)
class PmsBillingAutomationIntegrationTest {
 @Autowired PlatformTransactionManager manager;
 @Autowired CompanyRepository companies;@Autowired HotelPropertyRepository properties;@Autowired GuestProfileRepository guests;
 @Autowired RoomTypeRepository roomTypes;@Autowired RatePlanRepository rates;@Autowired ReservationRepository reservations;
 @Autowired FolioRepository folios;@Autowired PmsOrganizationRepository organizations;@Autowired PmsInvoiceRepository invoices;
 @Autowired PmsReceivableRepository receivables;@Autowired PmsReceivableSettlementRepository settlements;
 @Autowired PmsBillingSettingsRepository configs;@Autowired PmsDeliveryJobRepository jobs;
 @Autowired PmsDeliveryService delivery;@Autowired PmsDeliveryWorker worker;@Autowired PmsBillingSettingsService settings;
 @Autowired PmsBankReconciliationService bank;@Autowired PmsDunningService dunning;
 @Autowired PmsAuditWriter audit;
 @MockBean PmsAdvancedService advanced;@MockBean PmsExtensionsService extensions;@MockBean JavaMailSender smtp;
 TransactionTemplate tx;Long cid,pid,rid,iid,arid;LocalDate today=LocalDate.now(ZoneId.of("Europe/Zurich"));
 @BeforeEach void seed(){
  tx=new TransactionTemplate(manager);tx.executeWithoutResult(status->{
   Company c=companies.save(new Company("Billing "+UUID.randomUUID()));cid=c.getId();HotelProperty p=new HotelProperty();p.setCompany(c);p.setCode("BILL");p.setName("Billing Test Hotel");p.setCurrencyCode("CHF");p.setTimezone("Europe/Zurich");properties.saveAndFlush(p);pid=p.getId();
   GuestProfile g=new GuestProfile();g.setCompany(c);g.setFirstName("Ada");g.setLastName("Test");g.setEmail("guest@example.test");guests.save(g);
   RoomType type=new RoomType();type.setProperty(p);type.setCode("DBL");type.setName("Double");type.setMaxOccupancy(2);roomTypes.save(type);
   RatePlan rate=new RatePlan();rate.setProperty(p);rate.setRoomType(type);rate.setCode("BAR");rate.setName("Flexible");rate.setCurrencyCode("CHF");rate.setNightlyRate(new BigDecimal("100"));rates.save(rate);
   Reservation r=new Reservation();r.setProperty(p);r.setGuest(g);r.setRoomType(type);r.setRatePlan(rate);r.setConfirmationCode("TEST-1");r.setArrivalDate(today.minusDays(12));r.setDepartureDate(today.minusDays(10));r.setCurrencyCode("CHF");r.setCreatedBy("test");reservations.save(r);rid=r.getId();
   PmsOrganization org=new PmsOrganization();org.setCompany(c);org.setType(OrganizationType.COMPANY);org.setName("ACME");organizations.save(org);
   Folio f=new Folio();f.setReservation(r);f.setOrganization(org);f.setCurrencyCode("CHF");folios.save(f);
   PmsInvoice i=new PmsInvoice();i.setProperty(p);i.setFolio(f);i.setInvoiceNumber("INV-TEST");i.setIssueDate(today.minusDays(12));i.setDueDate(today.minusDays(10));i.setRecipientName("ACME");i.setRecipientSnapshot(PmsProfileData.encode(new BillingProfile("ACME",null,null,null,null,null,null,"CH",null,"billing@example.test",null,null,null,null,null)));i.setCurrencyCode("CHF");i.setNetAmount(new BigDecimal("100"));i.setVatAmount(BigDecimal.ZERO);i.setGrossAmount(new BigDecimal("100"));i.setVatRate(BigDecimal.ZERO);invoices.save(i);iid=i.getId();
   PmsReceivable a=new PmsReceivable();a.setProperty(p);a.setInvoice(i);a.setOrganization(org);a.setAmount(new BigDecimal("100"));a.setDueDate(today.minusDays(10));a.setCreatedAt(LocalDateTime.now());a.setCreatedBy("test");receivables.save(a);arid=a.getId();
   PmsBillingSettings s=new PmsBillingSettings();s.setProperty(p);s.setMailEnabled(true);s.setSenderEmail("hotel@example.test");s.setSenderName("Test Hotel");s.setSendReminders(true);configs.saveAndFlush(s);
  });
  when(advanced.generateInvoicePdf(any(),anyLong())).thenReturn("%PDF-test-content".getBytes(StandardCharsets.UTF_8));
  when(smtp.createMimeMessage()).thenAnswer(invocation->new MimeMessage(Session.getInstance(new Properties())));
 }
 @Test void queueIsIdempotentAndRealMimeContainsFrozenAttachment()throws Exception{
  var first=delivery.queueInvoice(cid,pid,iid,new InvoiceSend("send-1",null),"test");
  assertThat(delivery.queueInvoice(cid,pid,iid,new InvoiceSend("send-1",null),"test").id()).isEqualTo(first.id());
  assertThatThrownBy(()->delivery.queueInvoice(cid,pid,iid,new InvoiceSend("send-1","other@example.test"),"test")).hasMessageContaining("anderen Rechnung");
  assertThat(worker.process(first.id())).isTrue();assertThat(worker.process(first.id())).isFalse();
  var capture=org.mockito.ArgumentCaptor.forClass(MimeMessage.class);verify(smtp,times(1)).send(capture.capture());
  MimeMessage message=capture.getValue();assertThat(message.getHeader("Message-ID",null)).isEqualTo(first.messageId());assertThat(message.getAllRecipients()[0].toString()).isEqualTo("billing@example.test");
  java.io.ByteArrayOutputStream bytes=new java.io.ByteArrayOutputStream();message.writeTo(bytes);assertThat(bytes.toString(StandardCharsets.UTF_8)).contains("INV-TEST.pdf","application/pdf");
  assertThat((String)tx.execute(status->jobs.findById(first.id()).orElseThrow().getStatus())).isEqualTo("SENT");
 }
 @Test void ambiguousSmtpFailureCannotBeBlindlyRetried(){
  doThrow(new MailSendException("timeout after DATA",new java.net.SocketTimeoutException("unknown acceptance"))).when(smtp).send(any(MimeMessage.class));
  var job=delivery.queueInvoice(cid,pid,iid,new InvoiceSend("uncertain",null),"test");worker.process(job.id());
  assertThat((String)tx.execute(status->jobs.findById(job.id()).orElseThrow().getStatus())).isEqualTo("UNKNOWN");
  assertThatThrownBy(()->delivery.retry(cid,pid,job.id(),new Retry(false),"test")).hasMessageContaining("Doppelzustellung");
  assertThat(delivery.retry(cid,pid,job.id(),new Retry(true),"test").status()).isEqualTo("QUEUED");
 }
 @Test void aSecondWorkerCannotResendAnAlreadyClaimedJob()throws Exception{
  var entered=new java.util.concurrent.CountDownLatch(1);var release=new java.util.concurrent.CountDownLatch(1);
  doAnswer(invocation->{entered.countDown();if(!release.await(10,java.util.concurrent.TimeUnit.SECONDS))throw new IllegalStateException("test timeout");return null;}).when(smtp).send(any(MimeMessage.class));
  var queued=delivery.queueInvoice(cid,pid,iid,new InvoiceSend("concurrent",null),"test");var executor=java.util.concurrent.Executors.newSingleThreadExecutor();
  try{var first=executor.submit(()->worker.process(queued.id()));assertThat(entered.await(10,java.util.concurrent.TimeUnit.SECONDS)).isTrue();assertThat(worker.process(queued.id())).isFalse();release.countDown();assertThat(first.get(10,java.util.concurrent.TimeUnit.SECONDS)).isTrue();verify(smtp,times(1)).send(any(MimeMessage.class));}
  finally{release.countDown();executor.shutdownNow();}
 }
 @Test void durableWorkerDeliversThroughIsolatedLoopbackSmtp()throws Exception{
  try(java.net.ServerSocket server=new java.net.ServerSocket(0,1,java.net.InetAddress.getByName("127.0.0.1"))){
   server.setSoTimeout(10000);var executor=java.util.concurrent.Executors.newSingleThreadExecutor();
   try{
    var received=executor.submit(()->{
     try(var socket=server.accept();var reader=new java.io.BufferedReader(new java.io.InputStreamReader(socket.getInputStream(),StandardCharsets.UTF_8));var writer=new java.io.PrintWriter(socket.getOutputStream(),true,StandardCharsets.UTF_8)){
      socket.setSoTimeout(10000);writer.print("220 localhost test SMTP\r\n");writer.flush();StringBuilder payload=new StringBuilder();boolean data=false;String line;
      while((line=reader.readLine())!=null){
       if(data){if(line.equals(".")){data=false;writer.print("250 accepted\r\n");writer.flush();}else payload.append(line).append('\n');continue;}
       if(line.startsWith("DATA")){data=true;writer.print("354 send data\r\n");}
       else if(line.startsWith("QUIT")){writer.print("221 bye\r\n");writer.flush();break;}
       else writer.print("250 OK\r\n");writer.flush();
      }return payload.toString();
     }
    });
    var mail=new org.springframework.mail.javamail.JavaMailSenderImpl();mail.setHost("127.0.0.1");mail.setPort(server.getLocalPort());
    mail.getJavaMailProperties().setProperty("mail.smtp.connectiontimeout","5000");mail.getJavaMailProperties().setProperty("mail.smtp.timeout","5000");mail.getJavaMailProperties().setProperty("mail.smtp.writetimeout","5000");
    var isolatedWorker=new PmsDeliveryWorker(jobs,mail,manager,audit,configs,properties);
    var job=delivery.queueInvoice(cid,pid,iid,new InvoiceSend("loopback",null),"test");assertThat(isolatedWorker.process(job.id())).isTrue();
    assertThat(received.get(10,java.util.concurrent.TimeUnit.SECONDS)).contains(job.messageId(),"INV-TEST.pdf","billing@example.test");
    assertThat((String)tx.execute(status->jobs.findById(job.id()).orElseThrow().getStatus())).isEqualTo("SENT");
   }finally{executor.shutdownNow();}
  }
 }
 @Test void rejectedConnectionRetriesAndHotelDisablePausesWithoutSending(){
  doThrow(new MailSendException("connect",new java.net.ConnectException("refused"))).when(smtp).send(any(MimeMessage.class));
  var job=delivery.queueInvoice(cid,pid,iid,new InvoiceSend("retry",null),"test");worker.process(job.id());
  assertThat((String)tx.execute(status->jobs.findById(job.id()).orElseThrow().getStatus())).isEqualTo("RETRY");
  tx.executeWithoutResult(status->{var c=configs.findById(pid).orElseThrow();c.setMailEnabled(false);var j=jobs.findById(job.id()).orElseThrow();j.setNextAttemptAt(LocalDateTime.now().minusMinutes(1));});
  assertThat(worker.process(job.id())).isFalse();assertThat((String)tx.execute(status->jobs.findById(job.id()).orElseThrow().getStatus())).isEqualTo("PAUSED");verify(smtp,times(1)).send(any(MimeMessage.class));
 }
 @Test void remindersRespectDueDateIntervalAndChangedBalanceBeforeSending(){
  var first=dunning.run(cid,pid,"test");assertThat(first.created()).isEqualTo(1);assertThat(dunning.run(cid,pid,"test").created()).isZero();
  tx.executeWithoutResult(status->{var r=receivables.findById(arid).orElseThrow();r.setSettledAmount(new BigDecimal("50"));});
  Long jobId=first.notices().get(0).deliveryId();assertThat(worker.process(jobId)).isFalse();verify(smtp,never()).send(any(MimeMessage.class));
  assertThat((String)tx.execute(status->jobs.findById(jobId).orElseThrow().getStatus())).isEqualTo("CANCELLED");
 }
 @Test void futureDueReceivableIsNeverDunned(){tx.executeWithoutResult(status->receivables.findById(arid).orElseThrow().setDueDate(today.plusDays(2)));assertThat(dunning.run(cid,pid,"test").created()).isZero();}
 @Test void bankPreviewSuggestsWithoutPostingAndConfirmationIsIdempotent(){
  var batch=bank.preview(cid,pid,"statement.csv",csv("bank-1","CHF","40.00","INV-TEST"),"test");
  assertThat(batch.rows()).singleElement().satisfies(row->assertThat(row.suggestions()).singleElement().satisfies(s->assertThat(s.receivableId()).isEqualTo(arid)));
  assertThat((BigDecimal)tx.execute(status->receivables.findById(arid).orElseThrow().balance())).isEqualByComparingTo("100");
  var request=new BankConfirm(List.of(new BankMatch(batch.rows().get(0).id(),arid)));bank.confirm(cid,pid,batch.id(),request,"test");bank.confirm(cid,pid,batch.id(),request,"test");
  assertThat((BigDecimal)tx.execute(status->receivables.findById(arid).orElseThrow().balance())).isEqualByComparingTo("60");
  assertThat(bank.preview(cid,pid,"again.csv",csv("bank-1","CHF","40.00","INV-TEST"),"test").id()).isEqualTo(batch.id());
  assertThatThrownBy(()->bank.preview(cid,pid,"changed.csv",csv("bank-1","CHF","41.00","INV-TEST"),"test")).hasMessageContaining("anderen Daten");
 }
 @Test void bankRejectsCurrencyMismatchAndOverpaymentWithoutLedgerMutation(){
  var batch=bank.preview(cid,pid,"eur.csv",csv("bank-eur","EUR","40.00","INV-TEST"),"test");
  assertThat(batch.rows().get(0).suggestions()).isEmpty();assertThatThrownBy(()->bank.confirm(cid,pid,batch.id(),new BankConfirm(List.of(new BankMatch(batch.rows().get(0).id(),arid))),"test")).hasMessageContaining("dieselbe Währung");
  var over=bank.preview(cid,pid,"over.csv",csv("bank-over","CHF","101","INV-TEST"),"test");assertThatThrownBy(()->bank.confirm(cid,pid,over.id(),new BankConfirm(List.of(new BankMatch(over.rows().get(0).id(),arid))),"test")).hasMessageContaining("übersteigt");
  assertThat((BigDecimal)tx.execute(status->receivables.findById(arid).orElseThrow().balance())).isEqualByComparingTo("100");
 }
 @Test void accountingRunFreezesBytesAndAcknowledgement(){
  byte[] original="immutable;accounting\n".getBytes(StandardCharsets.UTF_8);when(extensions.accountingExport(any(),eq(pid),any(),any())).thenReturn(original);
  var request=new ExportCreate("export-1",today.minusDays(1),today);var run=bank.createExport(cid,pid,request,"test");
  when(extensions.accountingExport(any(),eq(pid),any(),any())).thenReturn("changed".getBytes(StandardCharsets.UTF_8));assertThat(bank.createExport(cid,pid,request,"test").id()).isEqualTo(run.id());assertThat(bank.download(cid,pid,run.id())).isEqualTo(original);
  assertThat(bank.acknowledge(cid,pid,run.id(),new ExportAck("BOOK-42"),"test").status()).isEqualTo("ACKNOWLEDGED");
  assertThatThrownBy(()->bank.acknowledge(cid,pid,run.id(),new ExportAck("different"),"test")).hasMessageContaining("anderer Referenz");
 }
 @Test void quotedCsvAndHeaderInjectionAreControlled(){
  assertThat(PmsBankReconciliationService.parseCsv("a;b\r\n\"quoted;value\";\"double\"\"quote\"\n").get(1)).containsExactly("quoted;value","double\"quote");
  assertThatThrownBy(()->PmsBankReconciliationService.parseCsv("a;\"unfinished")).isInstanceOf(ResponseStatusException.class);
  assertThatThrownBy(()->delivery.queueInvoice(cid,pid,iid,new InvoiceSend("inject","ok@example.test\r\nBcc: extra@example.test"),"test")).isInstanceOf(ResponseStatusException.class);
 }
 private byte[] csv(String reference,String currency,String amount,String invoice){return ("bankAccount;transactionId;bookingDate;currency;amount;reference;debtorName\nMAIN;"+reference+";"+today+";"+currency+";"+amount+";"+invoice+";ACME\n").getBytes(StandardCharsets.UTF_8);}
}
