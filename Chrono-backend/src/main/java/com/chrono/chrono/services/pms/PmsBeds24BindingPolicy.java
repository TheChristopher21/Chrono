package com.chrono.chrono.services.pms;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import java.util.HashSet;
import java.util.Set;

/** Operator-owned capabilities. Tenant-editable settings and token reachability grant no authority. */
@Component
public class PmsBeds24BindingPolicy {
    private record Binding(Long companyId, Long propertyId, String reference, Long externalPropertyId) {}
    private final Set<Binding> bindings;
    private final boolean valid;

    public PmsBeds24BindingPolicy(@Value("${app.pms.beds24.bindings:}") String configuration) {
        Set<Binding> parsed = new HashSet<>();
        boolean accepted = true;
        try {
            if (configuration != null && !configuration.isBlank()) for (String entry : configuration.split(";", -1)) {
                String[] fields = entry.trim().split("\\|", -1);
                if (fields.length != 4 || !fields[2].matches("env:[A-Z][A-Z0-9_]{2,100}")) throw new IllegalArgumentException();
                parsed.add(new Binding(positive(fields[0]), positive(fields[1]), fields[2], positive(fields[3])));
            }
        } catch (IllegalArgumentException exception) { accepted = false; parsed.clear(); }
        this.bindings = Set.copyOf(parsed); this.valid = accepted;
    }

    private static Long positive(String value) {
        if (!value.matches("[1-9][0-9]*")) throw new IllegalArgumentException();
        return Long.valueOf(value);
    }

    public void require(Long companyId, Long propertyId, String reference, Long externalPropertyId) {
        if (!valid || !bindings.contains(new Binding(companyId, propertyId, reference, externalPropertyId))) throw new BindingDenied();
    }

    public static class BindingDenied extends ResponseStatusException {
        BindingDenied() { super(HttpStatus.CONFLICT, "Beds24-Serverfreigabe fehlt oder stimmt nicht überein. Betreiber muss APP_PMS_BEDS24_BINDINGS für Firma, Hotel, Tokenreferenz und Anbieterhotel prüfen."); }
    }
}
