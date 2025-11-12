package com.chrono.chrono.dto.crm;

import com.chrono.chrono.entities.crm.CrmDocument;

public class CreateCrmDocumentRequest {
    private String fileName;
    private String url;

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public CrmDocument toEntity() {
        CrmDocument document = new CrmDocument();
        document.setFileName(fileName);
        document.setUrl(url);
        return document;
    }
}
