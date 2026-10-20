package com.chrono.chrono.services.pms;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.Locale;

/** Hotel currency determines precision. Guest nationality never changes monetary arithmetic. */
public final class PmsMoney {
    private PmsMoney() {}
    public static int digits(String currencyCode) {
        try {
            int digits = Currency.getInstance(currencyCode.toUpperCase(Locale.ROOT)).getDefaultFractionDigits();
            if (digits < 0 || digits > 4) throw new IllegalArgumentException();
            return digits;
        } catch (RuntimeException error) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nicht unterstützte Hotelwährung."); }
    }
    public static BigDecimal require(BigDecimal amount, String currencyCode) {
        if (amount == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ein Geldbetrag ist erforderlich.");
        try { return amount.setScale(digits(currencyCode), RoundingMode.UNNECESSARY); }
        catch (ArithmeticException error) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Der Betrag enthält Bruchteile der kleinsten Einheit für " + currencyCode + "."); }
    }
    public static BigDecimal round(BigDecimal amount, String currencyCode) {
        return (amount == null ? BigDecimal.ZERO : amount).setScale(digits(currencyCode), RoundingMode.HALF_UP);
    }
}
