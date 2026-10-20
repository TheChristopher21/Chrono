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
import java.io.*;
import java.nio.file.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.*;
import org.springframework.transaction.support.*;

@Service
public class PmsProfileDocumentService {
    public static final long MAX_FILE_BYTES = 25L * 1024 * 1024;
    @Value("${app.pms.documents.max-file-bytes:26214400}") private long maxFileBytes = MAX_FILE_BYTES;
    @Value("${app.pms.documents.tenant-quota-bytes:10737418240}") private long tenantQuotaBytes = 10L * 1024 * 1024 * 1024;
    @Value("${app.pms.documents.organization-limit:5000}") private long organizationLimit = 5000;
    @Value("${app.pms.documents.storage-root:}") private String storageRoot = "";
    private final PmsProfileDocumentRepository documents;
    private final PmsOrganizationRepository organizations;
    private final RatePlanRepository rates;
    private final EntityManager entityManager;
    public PmsProfileDocumentService(PmsProfileDocumentRepository documents, PmsOrganizationRepository organizations,
                                     RatePlanRepository rates, EntityManager entityManager) {
        this.documents = documents; this.organizations = organizations; this.rates = rates; this.entityManager = entityManager;
    }
    public record DocumentView(Long id, Long organizationId, Long ratePlanId, String fileName, String contentType,
                               long sizeBytes, String sha256, String uploadedBy, LocalDateTime uploadedAt,
                               String versionGroup, int documentVersion, boolean archived) { }
    private DocumentView view(PmsProfileDocument d) {
        return new DocumentView(d.getId(), d.getOrganization().getId(), d.getRatePlan() == null ? null : d.getRatePlan().getId(),
                d.getFileName(), d.getContentType(), d.getSizeBytes(), d.getSha256(), d.getUploadedBy(), d.getUploadedAt(), d.getVersionGroup(), d.getDocumentVersion(), d.isArchived());
    }
    @Transactional(readOnly = true)
    public List<DocumentView> list(Company company, Long organizationId) {
        requireOrganization(company, organizationId);
        return documents.findMetadata(organizationId, company.getId()).stream().map(d -> new DocumentView(d.getId(),d.getOrganizationId(),
                d.getRatePlanId(),d.getFileName(),d.getContentType(),d.getSizeBytes(),d.getSha256(),d.getUploadedBy(),d.getUploadedAt(),d.getVersionGroup(),d.getDocumentVersion(),d.getArchived())).toList();
    }
    @Transactional
    public DocumentView upload(Company company, Long organizationId, Long ratePlanId, MultipartFile file, String username) {
        return upload(company, organizationId, ratePlanId, null, file, username);
    }
    @Transactional
    public DocumentView upload(Company company, Long organizationId, Long ratePlanId, Long previousDocumentId, MultipartFile file, String username) {
        PmsOrganization organization = requireOrganization(company, organizationId);
        // Serialize tenant quota checks so concurrent uploads cannot bypass the bound.
        entityManager.find(Company.class, company.getId(), LockModeType.PESSIMISTIC_WRITE);
        if (file.isEmpty() || file.getSize() > maxFileBytes) throw error(HttpStatus.BAD_REQUEST, "Datei muss zwischen 1 Byte und " + maxFileBytes / 1024 / 1024 + " MB groß sein.");
        if (documents.countByOrganization_Id(organizationId) >= organizationLimit || documents.storedBytes(company.getId()) > tenantQuotaBytes - file.getSize())
            throw error(HttpStatus.CONFLICT, "Das konfigurierte Dokumenten- oder Speicherkontingent ist erreicht.");
        RatePlan rate = ratePlanId == null ? null : rates.findByIdAndProperty_Company_Id(ratePlanId, company.getId())
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Rate nicht gefunden."));
        if (rate != null && rate.getOrganization() != null && !rate.getOrganization().getId().equals(organizationId))
            throw error(HttpStatus.BAD_REQUEST, "Die Vertragsrate gehört zu einer anderen Firma.");
        String versionGroup = UUID.randomUUID().toString(); int version = 1;
        if (previousDocumentId != null) {
            var previous = documents.findByIdAndCompany_Id(previousDocumentId, company.getId())
                    .filter(value -> value.getOrganization().getId().equals(organizationId))
                    .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Vorherige Dokumentversion nicht gefunden."));
            if (!Objects.equals(ratePlanId, previous.getRatePlan() == null ? null : previous.getRatePlan().getId()))
                throw error(HttpStatus.BAD_REQUEST, "Eine neue Version muss dieselbe Vertragsrate behalten.");
            versionGroup = previous.getVersionGroup() == null ? previous.getId().toString() : previous.getVersionGroup();
            int latest = documents.latestVersion(company.getId(), versionGroup);
            if (previous.getDocumentVersion() != latest) throw error(HttpStatus.CONFLICT, "Das Dokument hat bereits eine neuere Version. Bitte neu laden.");
            version = latest + 1;
        }
        String name = Objects.toString(file.getOriginalFilename(), "Dokument").replaceAll("[\\\\/\\r\\n\\p{Cntrl}]", "_");
        if (name.length() > 180) name = name.substring(name.length() - 180);
        try {
            String type;
            try (InputStream header = file.getInputStream()) { type = detectType(header.readNBytes(512), name); }
            PmsProfileDocument document = new PmsProfileDocument();
            document.setCompany(company); document.setOrganization(organization); document.setRatePlan(rate);
            document.setFileName(name); document.setContentType(type); document.setVersionGroup(versionGroup); document.setDocumentVersion(version);
            if (storageRoot.isBlank()) {
                byte[] content;
                try (InputStream stream = file.getInputStream()) { content = stream.readNBytes((int) Math.min(Integer.MAX_VALUE, maxFileBytes + 1)); }
                if (content.length > maxFileBytes || content.length != file.getSize()) throw error(HttpStatus.BAD_REQUEST, "Die tatsächliche Dateigröße stimmt nicht mit dem Upload überein.");
                document.setSizeBytes(content.length); document.setSha256(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content)));
                document.setContent(content);
            } else {
                Path root = Path.of(storageRoot); PmsDocumentStorage.Stored stored;
                try (InputStream stream = file.getInputStream()) { stored = PmsDocumentStorage.write(root, company.getId(), stream, maxFileBytes); }
                if (TransactionSynchronizationManager.isSynchronizationActive()) TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override public void afterCompletion(int status) { if (status != STATUS_COMMITTED) try { Files.deleteIfExists(PmsDocumentStorage.resolve(root, stored.key())); } catch (IOException ignored) { } }
                });
                if (stored.size() != file.getSize()) throw error(HttpStatus.BAD_REQUEST, "Die tatsächliche Dateigröße stimmt nicht mit dem Upload überein.");
                document.setContent(new byte[0]); document.setStorageKey(stored.key()); document.setSizeBytes(stored.size()); document.setSha256(stored.sha256());
            }
            document.setUploadedBy(username); document.setUploadedAt(LocalDateTime.now());
            return view(documents.save(document));
        } catch (java.io.IOException | java.security.NoSuchAlgorithmException ex) {
            throw error(HttpStatus.BAD_REQUEST, "Datei konnte nicht verarbeitet werden.");
        }
    }
    @Transactional(readOnly = true)
    public List<DocumentView> listForProperty(Company company, Long organizationId, Long propertyId) {
        if (propertyId == null) return list(company, organizationId);
        requireOrganization(company, organizationId);
        return documents.findMetadataForProperty(organizationId, company.getId(), propertyId).stream()
                .map(d -> new DocumentView(d.getId(), d.getOrganizationId(), d.getRatePlanId(), d.getFileName(), d.getContentType(), d.getSizeBytes(), d.getSha256(), d.getUploadedBy(), d.getUploadedAt(),d.getVersionGroup(),d.getDocumentVersion(),d.getArchived())).toList();
    }
    @Transactional(readOnly = true)
    public PmsProfileDocument download(Company company, Long id) {
        PmsProfileDocument doc = documents.findByIdAndCompany_Id(id, company.getId())
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Dokument nicht gefunden."));
        doc.getContent(); // Resolve the private payload while the transaction is open.
        return doc;
    }
    @Transactional
    public void delete(Company company, Long id) { download(company, id).setArchived(true); }
    public record Limits(long maxFileBytes, long tenantQuotaBytes, long organizationLimit, long usedBytes) {}
    @Transactional(readOnly = true)
    public Limits limits(Company company) { return new Limits(maxFileBytes, tenantQuotaBytes, organizationLimit, documents.storedBytes(company.getId())); }
    public Resource payload(PmsProfileDocument document) {
        if (document.getStorageKey() == null) return new ByteArrayResource(document.getContent());
        if (storageRoot.isBlank()) throw error(HttpStatus.SERVICE_UNAVAILABLE, "Der private Dokumentenspeicher ist nicht konfiguriert.");
        try {
            Path path = PmsDocumentStorage.resolve(Path.of(storageRoot), document.getStorageKey());
            if (!Files.isRegularFile(path) || Files.size(path) != document.getSizeBytes()) throw new IOException("Missing or truncated document");
            return new FileSystemResource(path);
        } catch (IOException failure) { throw error(HttpStatus.SERVICE_UNAVAILABLE, "Das Dokument ist im privaten Speicher nicht verfügbar."); }
    }
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
