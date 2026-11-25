package com.chrono.chrono.services.pms;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import java.sql.DriverManager;
import static org.assertj.core.api.Assertions.*;

class PmsPaymentIdentityMigrationTest {
    @Test void legacyProviderKeysSurviveAndOnlyActualOwnedDepositRowsOccupyAutomationIdentity() throws Exception {
        try(var connection=DriverManager.getConnection("jdbc:h2:mem:pms_payment_identity;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
            var sql=connection.createStatement()) {
            sql.execute("CREATE TABLE pms_reservations(id BIGINT PRIMARY KEY)");
            sql.execute("CREATE TABLE pms_folios(id BIGINT PRIMARY KEY,reservation_id BIGINT NOT NULL)");
            sql.execute("CREATE TABLE pms_payment_requests(id BIGINT PRIMARY KEY,folio_id BIGINT NOT NULL,request_key VARCHAR(80) NOT NULL UNIQUE,created_by VARCHAR(120),kind VARCHAR(20),provider_session_id VARCHAR(160))");
            sql.execute("INSERT INTO pms_reservations VALUES(100),(200),(300),(400)");
            sql.execute("INSERT INTO pms_folios VALUES(10,100),(20,200),(30,300),(40,400)");
            sql.execute("INSERT INTO pms_payment_requests VALUES(1,10,'deposit-reservation-100','deposit-automation','PAYMENT','cs_legacy'),(2,20,'deposit-reservation-300','Alice','PAYMENT','cs_poison'),(3,20,'guarantee-old','Bob','AUTHORIZATION',NULL),(4,40,'deposit-reservation-400','Deposit-Automation','PAYMENT',NULL)");
            ScriptUtils.executeSqlScript(connection,new ClassPathResource("db/migration/V58__pms_payment_request_identity_scope.sql"));
            try(var rows=sql.executeQuery("SELECT * FROM pms_payment_requests ORDER BY id")) {
                assertThat(rows.next()).isTrue(); assertThat(rows.getString("request_key")).isEqualTo("deposit-reservation-100");
                assertThat(rows.getString("client_request_key")).isEqualTo("deposit-reservation-100"); assertThat(rows.getString("request_source")).isEqualTo("AUTOMATIC_DEPOSIT");
                assertThat(rows.getLong("automatic_reservation_id")).isEqualTo(100); assertThat(rows.getString("provider_session_id")).isEqualTo("cs_legacy");
                assertThat(rows.next()).isTrue(); assertThat(rows.getString("request_key")).isEqualTo("deposit-reservation-300");
                assertThat(rows.getString("request_source")).isEqualTo("CLIENT"); assertThat(rows.getObject("automatic_reservation_id")).isNull();
                assertThat(rows.getString("provider_session_id")).isEqualTo("cs_poison");
                assertThat(rows.next()).isTrue(); assertThat(rows.getString("request_key")).isEqualTo("guarantee-old"); assertThat(rows.getString("client_request_key")).isEqualTo("guarantee-old");
                assertThat(rows.next()).isTrue(); assertThat(rows.getObject("automatic_reservation_id")).isNull();
            }
            // The real reservation remains free despite a foreign folio having preempted its old text key.
            sql.execute("INSERT INTO pms_payment_requests(id,folio_id,request_key,client_request_key,request_source,automatic_reservation_id) VALUES(5,30,'chrono-pms-request-new','deposit-reservation-300','AUTOMATIC_DEPOSIT',300)");
            sql.execute("INSERT INTO pms_payment_requests(id,folio_id,request_key,client_request_key,request_source) VALUES(6,10,'chrono-pms-request-a','same-client-key','CLIENT'),(7,20,'chrono-pms-request-b','same-client-key','CLIENT')");
            assertThatThrownBy(() -> sql.execute("INSERT INTO pms_payment_requests(id,folio_id,request_key,client_request_key,request_source) VALUES(8,10,'chrono-pms-request-c','same-client-key','CLIENT')")).isInstanceOf(java.sql.SQLException.class);
        }
    }
}
