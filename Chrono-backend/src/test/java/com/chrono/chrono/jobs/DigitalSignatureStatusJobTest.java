package com.chrono.chrono.jobs;

import com.chrono.chrono.entities.banking.DigitalSignatureRequest;
import com.chrono.chrono.entities.banking.SignatureStatus;
import com.chrono.chrono.repositories.banking.DigitalSignatureRequestRepository;
import com.chrono.chrono.services.banking.DigitalSignatureProviderClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DigitalSignatureStatusJobTest {

    private DigitalSignatureRequestRepository repository;
    private DigitalSignatureProviderClient providerClient;
    private DigitalSignatureStatusJob job;

    @BeforeEach
    void setUp() {
        repository = mock(DigitalSignatureRequestRepository.class);
        providerClient = mock(DigitalSignatureProviderClient.class);
        job = new DigitalSignatureStatusJob(repository, providerClient);
    }

    @Test
    void pollSignatureStatusUpdatesCompletedRequests() {
        DigitalSignatureRequest request = new DigitalSignatureRequest();
        request.setId(5L);
        request.setProviderReference("SIG-1");
        request.setStatus(SignatureStatus.IN_PROGRESS);

        when(providerClient.isEnabled()).thenReturn(true);
        when(repository.findByStatus(SignatureStatus.PENDING)).thenReturn(List.of());
        when(repository.findByStatus(SignatureStatus.IN_PROGRESS)).thenReturn(List.of(request));
        when(providerClient.fetchStatus("SIG-1"))
                .thenReturn(new DigitalSignatureProviderClient.SignatureStatusUpdate(SignatureStatus.COMPLETED, "done", null));

        job.pollSignatureStatus();

        ArgumentCaptor<DigitalSignatureRequest> captor = ArgumentCaptor.forClass(DigitalSignatureRequest.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(SignatureStatus.COMPLETED);
    }
}
