package com.chrono.chrono.services.payroll;

import org.springframework.stereotype.Component;
import java.io.*;
import java.math.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.*;

/** ESTV 2026 release 04.03.2026, record format effective 01.01.2025; no network at runtime. */
@Component
public class SwissSourceTax {
    public static final String SHA256 = "619b53e364545ffcd32e01669983326f79e5dea82c520de5e158c4e9e10943d6";
    private static final Set<String> ANNUAL = Set.of("FR", "GE", "TI", "VD", "VS");
    private final Map<String, List<Band>> cache = new ConcurrentHashMap<>();
    private record Band(BigDecimal from, BigDecimal step, BigDecimal minimum, BigDecimal rate) {}
    public record Result(BigDecimal amount, BigDecimal rate, String reference) {}
    public static boolean annualModel(String canton) { return ANNUAL.contains(canton); }
    public Result annual(int year, String canton, String code, BigDecimal taxable, BigDecimal annualRateIncome) {
        if (!annualModel(canton)) throw new IllegalStateException("Der Kanton verwendet kein Jahresmodell.");
        return lookup(year, canton, code, taxable, annualRateIncome, true);
    }

    public Result monthly(int year, String canton, String code, BigDecimal taxable, BigDecimal rateIncome) {
        if (ANNUAL.contains(canton)) throw new IllegalStateException("Dieser Kanton benötigt das Jahresmodell mit Jahresausgleich.");
        return lookup(year, canton, code, taxable, rateIncome, false);
    }
    private Result lookup(int year, String canton, String code, BigDecimal taxable, BigDecimal rateIncome, boolean annual) {
        if (year != 2026) throw new IllegalStateException("Für dieses Jahr ist kein geprüfter Quellensteuertarif installiert.");
        if (canton == null || !canton.matches("[A-Z]{2}") || code == null || !code.matches("[ABCHLMNPRSTU][0-9][NY]")) throw new IllegalStateException("Kanton oder Quellensteuertarif fehlt.");
        if (taxable.signum() < 0 || rateIncome.signum() < 0) throw new IllegalArgumentException("Negative Quellensteuergrundlage.");
        var bands = cache.computeIfAbsent(canton + ":" + code, key -> load(canton, code));
        // ESTV FAQ KS45 #4: 4,000.99 remains in the band ending at 4,000.
        BigDecimal whole = rateIncome.setScale(0, RoundingMode.DOWN).max(BigDecimal.ONE);
        // Annual table bounds are e.g. 59,401–60,000, corresponding to monthly 4,951–5,000.
        // Dividing by 12 and truncating would incorrectly keep 60,001–60,011 in the lower band.
        BigDecimal factor = BigDecimal.valueOf(annual ? 12 : 1);
        Band selected = bands.stream().filter(b -> {
                    BigDecimal from = annual && "GE".equals(canton) ? b.from.multiply(factor) : b.from.subtract(BigDecimal.ONE).multiply(factor).add(BigDecimal.ONE);
                    return whole.compareTo(from) >= 0 && whole.compareTo(from.add(b.step.multiply(factor))) < 0;
                })
                .findFirst().orElseThrow(() -> new IllegalStateException("Das satzbestimmende Einkommen liegt ausserhalb der amtlichen Tarifdatei."));
        BigDecimal raw = taxable.signum() == 0 ? BigDecimal.ZERO : taxable.multiply(selected.rate).max(selected.minimum);
        // ESTV FAQ KS45 #5: round the final tax commercially to CHF 0.05.
        BigDecimal amount = raw.divide(new BigDecimal("0.05"), 0, RoundingMode.HALF_UP).multiply(new BigDecimal("0.05"));
        return new Result(amount, selected.rate, "ESTV 2026/04.03 " + canton + " " + code + "; " + SHA256);
    }
    private List<Band> load(String canton, String code) {
        try (InputStream source = getClass().getResourceAsStream("/payroll/ch/tar2026txt.zip")) {
            if (source == null) throw new IllegalStateException("Amtliche Quellensteuerdatei fehlt.");
            byte[] archive = source.readAllBytes();
            if (!HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(archive)).equals(SHA256)) throw new IllegalStateException("Die Prüfsumme der Quellensteuerdatei stimmt nicht.");
            try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(archive))) {
                ZipEntry entry;
                while ((entry = zip.getNextEntry()) != null) {
                    if (!entry.getName().equalsIgnoreCase("tar26" + canton + ".txt")) continue;
                    List<Band> result = new ArrayList<>();
                    var reader = new BufferedReader(new InputStreamReader(zip, StandardCharsets.ISO_8859_1));
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (!line.startsWith("06") || !line.substring(6, 16).trim().equals(code)) continue;
                        if (line.length() < 59 || !line.substring(2, 4).equals("01") || !line.substring(4, 6).equals(canton) || !line.substring(16, 24).equals("20260101")) throw new IllegalStateException("Unerwartetes Quellensteuer-Recordformat.");
                        result.add(new Band(decimal(line, 24, 33, 2), decimal(line, 33, 42, 2), decimal(line, 45, 54, 2), decimal(line, 54, 59, 4)));
                    }
                    if (result.isEmpty()) throw new IllegalStateException("Der persönliche Tarif ist in der amtlichen Datei nicht enthalten.");
                    result.sort(Comparator.comparing(Band::from));
                    for (int i = 1; i < result.size(); i++) {
                        var previous = result.get(i - 1);
                        BigDecimal gap = result.get(i).from.subtract(previous.from.add(previous.step));
                        // Pinned GE file: the initial zero-tax interval ends at x,449.95, next starts at x,450.
                        // No whole-franc lookup falls in that published five-cent gap.
                        boolean genevaZeroBand = "GE".equals(canton) && i == 1 && previous.from.signum() == 0 && previous.rate.signum() == 0 && gap.compareTo(new BigDecimal("0.05")) == 0;
                        if (gap.signum() != 0 && !genevaZeroBand) throw new IllegalStateException("Lücke oder Überschneidung im amtlichen Tarif.");
                    }
                    return List.copyOf(result);
                }
            }
            throw new IllegalStateException("Kein amtlicher Tarif für diesen Kanton vorhanden.");
        } catch (IOException | java.security.NoSuchAlgorithmException e) { throw new IllegalStateException("Quellensteuerdatei konnte nicht gelesen werden.", e); }
    }
    private static BigDecimal decimal(String line, int from, int to, int scale) { return new BigDecimal(line.substring(from, to)).movePointLeft(scale); }
}
