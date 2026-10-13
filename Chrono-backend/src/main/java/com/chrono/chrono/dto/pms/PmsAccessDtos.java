package com.chrono.chrono.dto.pms;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;

public final class PmsAccessDtos {
    private PmsAccessDtos() {}
    public record Grant(@NotNull @Positive Long propertyId, @NotNull @Size(max = 8) Map<String, String> permissions) {}
    public record Update(@NotNull @Size(max = 1000) List<@Valid Grant> grants) {}
    public record Property(Long propertyId, String propertyName) {}
    public record PropertyAccess(Long propertyId, String propertyName, Map<String, String> permissions) {}
    public record Self(Long userId, boolean master, List<String> permissionKeys, List<PropertyAccess> properties) {}
    public record UserAccess(Long userId, String username, String displayName, boolean master, List<Grant> grants) {}
    public record Administration(List<String> permissionKeys, List<Property> properties, List<UserAccess> users,
                                 int page,int size,long totalElements,boolean hasNext) {
        public Administration(List<String> permissionKeys,List<Property> properties,List<UserAccess> users){this(permissionKeys,properties,users,0,users.size(),users.size(),false);}
    }
}
