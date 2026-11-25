package com.chrono.chrono.dto.inventory;

import java.util.ArrayList;
import java.util.List;

public class ReceivingPreviewRequest {
    private String scanValue;
    private String documentText;
    private String fileName;
    private List<String> detectedCodes = new ArrayList<>();

    public String getScanValue() {
        return scanValue;
    }

    public void setScanValue(String scanValue) {
        this.scanValue = scanValue;
    }

    public String getDocumentText() {
        return documentText;
    }

    public void setDocumentText(String documentText) {
        this.documentText = documentText;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public List<String> getDetectedCodes() {
        return detectedCodes;
    }

    public void setDetectedCodes(List<String> detectedCodes) {
        this.detectedCodes = detectedCodes == null ? new ArrayList<>() : new ArrayList<>(detectedCodes);
    }
}
