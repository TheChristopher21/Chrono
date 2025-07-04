package com.chrono.chrono.services;

import com.chrono.chrono.dto.ImportStatus;
import com.chrono.chrono.entities.User;
import com.chrono.chrono.repositories.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.security.Principal;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ImportProgressService {

    private final Map<String, ImportStatus> statusMap = new ConcurrentHashMap<>();

    @Autowired
    private TimeTrackingService timeTrackingService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private ThreadPoolTaskExecutor taskExecutor;

    public String startImport(MultipartFile file, Principal principal) throws Exception {
        String importId = UUID.randomUUID().toString();
        ImportStatus status = new ImportStatus();
        statusMap.put(importId, status);

        User currentUser = userRepository.findByUsername(principal.getName())
                .orElseThrow();
        Long companyId = currentUser.getCompany() != null ? currentUser.getCompany().getId() : null;

        taskExecutor.execute(() -> {
            try (InputStream is = file.getInputStream()) {
                timeTrackingService.importTimeTrackingFromExcelWithProgress(is, companyId, status);
            } catch (Exception e) {
                status.getErrorMessages().add("Fehler: " + e.getMessage());
            } finally {
                status.setCompleted(true);
            }
        });
        return importId;
    }

    public ImportStatus getStatus(String importId) {
        return statusMap.get(importId);
    }
}
