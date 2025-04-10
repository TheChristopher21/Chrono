package com.chrono.chrono.entities;

import jakarta.persistence.*;


import java.time.LocalDateTime;

@Entity
@Table(name = "nfc_commands")
public class NfcCommand {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Operationstyp, z. B. "PROGRAM"
    private String type;

    // Der zu schreibende Datensatz (Hex-String, z. B. 32 Zeichen)
    private String data;

    // Status: "pending" oder "done"
    private String status;

    private LocalDateTime createdAt;
    private LocalDateTime processedAt;

    public NfcCommand() {
        this.createdAt = LocalDateTime.now();
        this.status = "pending";
    }

    // Getter & Setter
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getData() { return data; }
    public void setData(String data) { this.data = data; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getProcessedAt() { return processedAt; }
    public void setProcessedAt(LocalDateTime processedAt) { this.processedAt = processedAt; }
}
