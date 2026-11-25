package com.chrono.chrono.dto.pms;
import com.chrono.chrono.entities.pms.FolioItemType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public final class PmsEventOrderDto {
    private PmsEventOrderDto() {}
    public record Line(@NotBlank @Size(max=240) String description,@NotNull @DecimalMin("0.01") @Digits(integer=8,fraction=2) BigDecimal quantity,
                       @NotNull @DecimalMin("0") BigDecimal netUnitPrice,@NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal taxRate,@NotNull FolioItemType type) {}
    public record Save(@Min(0) @Max(1440) int setupMinutes,@Min(0) @Max(1440) int teardownMinutes,
                       @Size(max=8000) String agenda,@Size(max=4000) String setupInstructions,@Size(max=4000) String cateringNotes,
                       @NotEmpty @Size(max=500) List<@Valid Line> lines) {}
    public record Post(Long folioId) {}
    public record LineView(Long id,String description,BigDecimal quantity,BigDecimal netUnitPrice,BigDecimal taxRate,FolioItemType type,
                           BigDecimal netAmount,BigDecimal taxAmount,BigDecimal grossAmount) {}
    public record View(Long resourceBookingId,String title,String resourceName,String organizerName,LocalDateTime startAt,LocalDateTime endAt,
                       LocalDateTime occupiedFrom,LocalDateTime occupiedUntil,int attendees,String currencyCode,Long groupBookingId,
                       String status,Long postedFolioId,LocalDateTime postedAt,int setupMinutes,int teardownMinutes,String agenda,
                       String setupInstructions,String cateringNotes,BigDecimal netAmount,BigDecimal taxAmount,BigDecimal grossAmount,List<LineView> lines) {}
}
