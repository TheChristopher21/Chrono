package com.chrono.chrono.services.pms;

import com.chrono.chrono.entities.Company;
import com.chrono.chrono.entities.pms.*;
import com.chrono.chrono.repositories.pms.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDateTime;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

@Service
public class PmsProfileDocumentService {
    public static final long MAX_FILE_BYTES = 4L * 1024 * 1024;
    private final PmsProfileDocumentRepository documents;
    private final PmsOrganizationRepository organizations;
    private final RatePlanRepository rates;
    private final EntityManager entityManager;
    public PmsProfileDocumentService(PmsProfileDocumentRepository documents, PmsOrganizationRepository organizations,
                                     RatePlanRepository rates, EntityManager entityManager) {
        this.documents = documents; this.organizations = organizations; this.rates = rates; this.entityManager = entityManager;
    }
    public record DocumentView(Long id, Long organizationId, Long ratePlanId, String fileName, String contentType,
                               long sizeBytes, String sha256, String uploadedBy, LocalDateTime uploadedAt) { }
    private DocumentView view(PmsProfileDocument d) {
        return new DocumentView(d.getId(), d.getOrganization().getId(), d.getRatePlan() == null ? null : d.getRatePlan().getId(),
                d.getFileName(), d.getContentType(), d.getSizeBytes(), d.getSha256(), d.getUploadedBy(), d.getUploadedAt());
    }
    @Transactional(readOnly = true)
    public List<DocumentView> list(Company company, Long organizationId) {
        requireOrganization(company, organizationId);
        return documents.findMetadata(organizationId, company.getId()).stream().map(d -> new DocumentView(d.getId(),d.getOrganizationId(),
                d.getRatePlanId(),d.getFileName(),d.getContentType(),d.getSizeBytes(),d.getSha256(),d.getUploadedBy(),d.getUploadedAt())).toList();
    }
    @Transactional
    public DocumentView upload(Company company, Long organizationId, Long ratePlanId, MultipartFile file, String username) {
        PmsOrganization organization = requireOrganization(company, organizationId);
        // Serialize tenant quota checks so concurrent uploads cannot bypass the bound.
        entityManager.find(Company.class, company.getId(), LockModeType.PESSIMISTIC_WRITE);
        if (file.isEmpty() || file.getSize() > MAX_FILE_BYTES) throw error(HttpStatus.BAD_REQUEST, "Datei muss zwischen 1 Byte und 4 MB groß sein.");
        if (documents.countByOrganization_Id(organizationId) >= 50 || documents.storedBytes(company.getId()) + file.getSize() > 250L * 1024 * 1024)
            throw error(HttpStatus.CONFLICT, "Dokumentenlimit erreicht (50 je Firma / 250 MB je Mandant).");
        RatePlan rate = ratePlanId == null ? null : rates.findByIdAndProperty_Company_Id(ratePlanId, company.getId())
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Rate nicht gefunden."));
        if (rate != null && rate.getOrganization() != null && !rate.getOrganization().getId().equals(organizationId))
            throw error(HttpStatus.BAD_REQUEST, "Die Vertragsrate gehört zu einer anderen Firma.");
        String name = Objects.toString(file.getOriginalFilename(), "Dokument").replaceAll("[\\\\/\\r\\n\\p{Cntrl}]", "_");
        if (name.length() > 180) name = name.substring(name.length() - 180);
        try {
            byte[] content = file.getBytes();
            String type = detectType(content, name);
            PmsProfileDocument document = new PmsProfileDocument();
            document.setCompany(company); document.setOrganization(organization); document.setRatePlan(rate);
            document.setFileName(name); document.setContentType(type); document.setSizeBytes(content.length);
            document.setSha256(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content)));
            document.setContent(content); document.setUploadedBy(username); document.setUploadedAt(LocalDateTime.now());
            return view(documents.save(document));
        } catch (java.io.IOException | java.security.NoSuchAlgorithmException ex) {
            throw error(HttpStatus.BAD_REQUEST, "Datei konnte nicht verarbeitet werden.");
        }
    }
    @Transactional(readOnly = true)
    public PmsProfileDocument download(Company company, Long id) {
        PmsProfileDocument doc = documents.findByIdAndCompany_Id(id, company.getId())
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Dokument nicht gefunden."));
        doc.getContent(); // Resolve the private payload while the transaction is open.
        return doc;
    }
    @Transactional
    public void delete(Company company, Long id) { documents.delete(download(company, id)); }
    private PmsOrganization requireOrganization(Company company, Long id) {
        return organizations.findByIdAndCompany_Id(id, company.getId())
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Firma nicht gefunden."));
    }
    static String detectType(byte[] bytes, String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".pdf") && bytes.length > 5 && new String(bytes,0,5, StandardCharsets.US_ASCII).equals("%PDF-")) return "application/pdf";
        if (lower.endsWith(".png") && bytes.length > 8 && Arrays.equals(Arrays.copyOf(bytes,8),new byte[]{(byte)137,80,78,71,13,10,26,10})) return "image/png";
        if ((lower.endsWith(".jpg") || lower.endsWith(".jpeg")) && bytes.length > 3 && bytes[0] == (byte)0xff && bytes[1] == (byte)0xd8 && bytes[2] == (byte)0xff) return "image/jpeg";
        throw error(HttpStatus.BAD_REQUEST, "Erlaubt sind PDF, PNG und JPEG mit passendem Dateiinhalt.");
    }
    private static ResponseStatusException error(HttpStatus status, String message) { return new ResponseStatusException(status, message); }
}
