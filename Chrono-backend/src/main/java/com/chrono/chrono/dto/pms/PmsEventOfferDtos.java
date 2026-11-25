package com.chrono.chrono.dto.pms;
import java.time.LocalDateTime;
public final class PmsEventOfferDtos {
 private PmsEventOfferDtos() {}
 public record Create(LocalDateTime validUntil,String terms) {}
 public record View(Long id,int version,String status,String terms,LocalDateTime validUntil,LocalDateTime createdAt,String createdBy,String contentHash,LocalDateTime decidedAt,String signatureName,String decisionNote,String acceptanceHash,LocalDateTime appliedAt,PmsEventOrderDto.View order) {}
 public record Link(Long offerId,String token,LocalDateTime validUntil) {}
 public record Decision(String decision,String signatureName,String note,boolean consent) {}
}
