package com.chrono.chrono.dto;

import com.chrono.chrono.entities.WorkdaySwap;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record WorkdaySwapDTO(
        Long id,
        String username,
        LocalDate originalWorkDate,
        LocalDate replacementWorkDate,
        Integer transferredMinutes,
        String note,
        String createdBy,
        LocalDateTime createdAt
) {
    public static WorkdaySwapDTO fromEntity(WorkdaySwap swap) {
        return new WorkdaySwapDTO(
                swap.getId(),
                swap.getUser().getUsername(),
                swap.getOriginalWorkDate(),
                swap.getReplacementWorkDate(),
                swap.getTransferredMinutes(),
                swap.getNote(),
                swap.getCreatedBy(),
                swap.getCreatedAt()
        );
    }
}
