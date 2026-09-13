package com.chrono.chrono.dto.pms;
import java.util.List;
public final class PmsDirectoryDtos {
 private PmsDirectoryDtos(){}
 public record Page<T>(List<T> items,int page,int size,long totalElements,boolean hasNext){}
}
