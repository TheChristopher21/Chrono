package com.chrono.chrono.services.pms;
import com.chrono.chrono.repositories.pms.PmsStripeEventRepository;
import com.chrono.chrono.entities.pms.PmsStripeEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.assertj.core.api.Assertions.*;
class PmsStripeWebhookServiceTest {
    final String secret="whsec_isolated_webhook_test_only";
    final String payload="{\"id\":\"evt_example\",\"type\":\"checkout.session.completed\",\"account\":\"acct_hotel\",\"data\":{\"object\":{\"id\":\"cs_example\",\"metadata\":{\"chronoRequestId\":\"payment-one\"}}}}";
    String sign(String body,long timestamp)throws Exception{var mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8),"HmacSHA256"));return "t="+timestamp+",v1="+HexFormat.of().formatHex(mac.doFinal((timestamp+"."+body).getBytes(StandardCharsets.UTF_8)));}
    @Test void verifiesRawSignatureAndRetainsOneDurableInboxRecordForDuplicateDelivery()throws Exception{
        var repository=mock(PmsStripeEventRepository.class);var transactions=mock(PlatformTransactionManager.class);when(transactions.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        Map<String,PmsStripeEvent> rows=new HashMap<>();when(repository.existsByEventId(anyString())).thenAnswer(i -> rows.containsKey(i.getArgument(0)));when(repository.saveAndFlush(any())).thenAnswer(i -> {PmsStripeEvent r=i.getArgument(0);rows.put(r.getEventId(),r);return r;});
        var service=new PmsStripeWebhookService(repository,new ObjectMapper(),secret,transactions);String signature=sign(payload,Instant.now().getEpochSecond());
        service.accept(payload,signature);service.accept(payload,signature);
        assertThat(rows).hasSize(1);assertThat(rows.get("evt_example").getAccountId()).isEqualTo("acct_hotel");assertThat(rows.get("evt_example").getRequestKey()).isEqualTo("payment-one");
        verify(repository,times(1)).saveAndFlush(any());
        assertThatThrownBy(() -> service.accept(payload.replace("acct_hotel","acct_other"),signature)).hasMessageContaining("Signatur");
        String stale=sign(payload,Instant.now().minusSeconds(600).getEpochSecond());assertThatThrownBy(() -> service.accept(payload,stale)).hasMessageContaining("Signatur");
    }
    @Test void ownershipRequiresExactConnectedAccountOrAnExplicitPlatformSnapshot(){
        assertThat(PmsStripeWebhookService.owns("CONNECT:acct_one","acct_one")).isTrue();
        assertThat(PmsStripeWebhookService.owns("CONNECT:acct_one","acct_two")).isFalse();
        assertThat(PmsStripeWebhookService.owns("CONNECT:acct_one",null)).isFalse();
        assertThat(PmsStripeWebhookService.owns("PLATFORM:acct_platform",null)).isTrue();
        assertThat(PmsStripeWebhookService.owns(null,null)).isFalse();
        assertThat(PmsStripeWebhookService.owns("PLATFORM:acct_platform","acct_one")).isFalse();
    }
}
