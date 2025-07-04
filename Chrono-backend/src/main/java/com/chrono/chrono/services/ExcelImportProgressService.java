package com.chrono.chrono.services;

import com.chrono.chrono.dto.ImportStatusDTO;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class ExcelImportProgressService {
    private final ExecutorService executor = Executors.newFixedThreadPool(2);
    private final ConcurrentHashMap<String, ImportStatusDTO> statusMap = new ConcurrentHashMap<>();

    @Autowired
    private TimeTrackingService timeTrackingService;

    public String startImport(MultipartFile file, Long adminCompanyId) {
        String id = UUID.randomUUID().toString();
        ImportStatusDTO status = new ImportStatusDTO();
        statusMap.put(id, status);
        executor.submit(() -> {
            try (InputStream is = file.getInputStream(); Workbook workbook = WorkbookFactory.create(is)) {
                Sheet sheet = workbook.getSheetAt(0);
                int totalRows = sheet.getPhysicalNumberOfRows() - 1; // Header ignorieren
                status.setTotalRows(Math.max(totalRows, 0));
                timeTrackingService.importTimeTrackingFromExcelWithProgress(workbook, adminCompanyId, status);
                status.setCompleted(true);
                status.setMessage(status.getErrorMessages().isEmpty() ?
                        "Import erfolgreich" : "Import mit Fehlern abgeschlossen");
            } catch (Exception e) {
                status.getErrorMessages().add(e.getMessage());
                status.setCompleted(true);
                status.setMessage("Import fehlgeschlagen");
            }
        });
        return id;
    }

    public ImportStatusDTO getStatus(String id) {
        return statusMap.get(id);
    }
}
