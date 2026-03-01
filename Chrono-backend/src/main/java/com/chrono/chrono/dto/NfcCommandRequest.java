package com.chrono.chrono.dto;

public class NfcCommandRequest {
    private String type;  // z. B. "PROGRAM"
    private String data;  // Hex-Datensatz (32 Zeichen)

    // Getter & Setter
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getData() { return data; }
    public void setData(String data) { this.data = data; }
}
