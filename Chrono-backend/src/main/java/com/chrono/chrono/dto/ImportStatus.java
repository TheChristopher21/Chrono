package com.chrono.chrono.dto;

import java.util.ArrayList;
import java.util.List;

public class ImportStatus {
    private int totalRows;
    private int processedRows;
    private boolean completed;
    private int importedCount;
    private List<String> successMessages = new ArrayList<>();
    private List<String> errorMessages = new ArrayList<>();

    public int getTotalRows() {
        return totalRows;
    }
    public void setTotalRows(int totalRows) {
        this.totalRows = totalRows;
    }
    public int getProcessedRows() {
        return processedRows;
    }
    public void setProcessedRows(int processedRows) {
        this.processedRows = processedRows;
    }
    public boolean isCompleted() {
        return completed;
    }
    public void setCompleted(boolean completed) {
        this.completed = completed;
    }
    public int getImportedCount() { return importedCount; }
    public void setImportedCount(int importedCount) { this.importedCount = importedCount; }
    public List<String> getSuccessMessages() {
        return successMessages;
    }
    public List<String> getErrorMessages() {
        return errorMessages;
    }
}
