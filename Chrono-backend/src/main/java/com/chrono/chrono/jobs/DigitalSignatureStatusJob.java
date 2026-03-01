package com.chrono.chrono.jobs;

import com.chrono.chrono.entities.banking.DigitalSignatureRequest;
import com.chrono.chrono.entities.banking.SignatureStatus;
import com.chrono.chrono.repositories.banking.DigitalSignatureRequestRepository;
import com.chrono.chrono.services.banking.DigitalSignatureProviderClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Component
public class DigitalSignatureStatusJob {

    private static final Logger logger = LoggerFactory.getLogger(DigitalSignatureStatusJob.class);

    private final DigitalSignatureRequestRepository requestRepository;
    private final DigitalSignatureProviderClient providerClient;

    public DigitalSignatureStatusJob(DigitalSignatureRequestRepository requestRepository,
                                     DigitalSignatureProviderClient providerClient) {
        this.requestRepository = requestRepository;
        this.providerClient = providerClient;
    }

    @Scheduled(fixedDelayString = "${banking.integrations.signatures.poll-interval:PT5M}")
    @Transactional
    public void pollSignatureStatus() {
        if (!providerClient.isEnabled()) {
            return;
        }

        List<DigitalSignatureRequest> pending = new ArrayList<>();
        pending.addAll(requestRepository.findByStatus(SignatureStatus.PENDING));
        pending.addAll(requestRepository.findByStatus(SignatureStatus.IN_PROGRESS));

        if (pending.isEmpty()) {
            return;
        }

        logger.debug("Checking status for {} signature requests", pending.size());
        for (DigitalSignatureRequest request : pending) {
            if (request.getProviderReference() == null) {
                continue;
            }
            try {
                var update = providerClient.fetchStatus(request.getProviderReference());
                if (update == null) {
                    continue;
                }
                request.setProviderStatusMessage(update.providerMessage());
                if (update.signingUrl() != null) {
                    request.setSigningUrl(update.signingUrl());
                }
                request.setLastStatusCheck(LocalDateTime.now());
                if (update.status() == SignatureStatus.COMPLETED) {
                    request.setStatus(SignatureStatus.COMPLETED);
                    request.setCompletedAt(LocalDateTime.now());
                    requestRepository.save(request);
                } else if (update.status() == SignatureStatus.FAILED) {
                    request.setStatus(SignatureStatus.FAILED);
                    request.setCompletedAt(LocalDateTime.now());
                    requestRepository.save(request);
                } else if (update.status() != request.getStatus()) {
                    request.setStatus(update.status());
                    requestRepository.save(request);
                }
            } catch (Exception ex) {
                logger.warn("Failed to refresh status for signature {}: {}", request.getId(), ex.getMessage());
            }
        }
    }
}
