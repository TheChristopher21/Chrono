package com.chrono.chrono.services.pms;

import com.chrono.chrono.dto.pms.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;

public final class PmsProfileData {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private PmsProfileData() { }
    public static String encode(Object value) {
        if (value == null) return null;
        try { return MAPPER.writeValueAsString(value); }
        catch (Exception ex) { throw new IllegalArgumentException("Ungültige Profildaten", ex); }
    }
    private static <T> T decode(String value, TypeReference<T> type, T fallback) {
        if (value == null || value.isBlank()) return fallback;
        try { return MAPPER.readValue(value, type); }
        catch (Exception ex) { throw new IllegalStateException("Gespeicherte Profildaten sind beschädigt", ex); }
    }
    public static List<String> emails(String value) { return decode(value, new TypeReference<>() {}, List.of()); }
    public static List<OrganizationContact> contacts(String value) { return decode(value, new TypeReference<>() {}, List.of()); }
    public static BillingProfile billing(String value) { return decode(value, new TypeReference<>() {}, null); }
    public static List<String> normalizeEmails(List<String> values) {
        return values == null ? List.of() : values.stream().filter(Objects::nonNull).map(String::trim)
                .filter(v -> !v.isEmpty()).map(v -> v.toLowerCase(Locale.ROOT)).distinct().toList();
    }
    public static String recipientBlock(BillingProfile profile) {
        if (profile == null) return "";
        List<String> lines = new ArrayList<>();
        if ("PERSON_FIRST".equals(profile.recipientOrder())) { lines.add(profile.attention()); lines.add(profile.legalName()); }
        else { lines.add(profile.legalName()); lines.add(profile.attention()); }
        lines.add(profile.addressLine1()); lines.add(profile.addressLine2());
        if ("CITY_REGION_POSTAL".equals(profile.addressFormat())) lines.add(join(profile.city(), profile.region(), profile.postalCode()));
        else { lines.add(join(profile.postalCode(), profile.city())); lines.add(profile.region()); }
        lines.add(profile.countryCode());
        return String.join("\n", lines.stream().filter(v -> v != null && !v.isBlank()).toList());
    }
    private static String join(String... values) { return String.join(" ", Arrays.stream(values).filter(v -> v != null && !v.isBlank()).toList()); }
}
