package com.chrono.chrono.exceptions;

import com.chrono.chrono.dto.ErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@ControllerAdvice
public class GlobalExceptionHandler {

    // Verwende einen Logger, um Fehler sicher zu protokollieren
    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final Map<String, String> VALIDATION_FIELD_LABELS = Map.ofEntries(
            Map.entry("propertyId", "Hotelbetrieb"),
            Map.entry("guestId", "Gast"),
            Map.entry("roomTypeId", "Zimmertyp"),
            Map.entry("roomId", "Zimmer"),
            Map.entry("ratePlanId", "Ratenplan"),
            Map.entry("reservationId", "Reservierung"),
            Map.entry("folioId", "Gastkonto"),
            Map.entry("targetFolioId", "Zielkonto"),
            Map.entry("resourceId", "Hotelressource"),
            Map.entry("groupBookingId", "Gruppenreservierung"),
            Map.entry("templateId", "Kommunikationsvorlage"),
            Map.entry("organizationId", "Geschäftspartner"),
            Map.entry("contactGuestId", "Hauptansprechperson"),
            Map.entry("providerCode", "Anbietercode"),
            Map.entry("displayName", "Bezeichnung"),
            Map.entry("environment", "Betriebsart"),
            Map.entry("secretReference", "Zugangsdaten-Referenz"),
            Map.entry("mappings", "Schnittstellen-Zuordnungen"),
            Map.entry("externalRoomCode", "Externer Zimmercode"),
            Map.entry("externalRateCode", "Externer Ratencode"),
            Map.entry("number", "Zimmernummer"),
            Map.entry("baseOccupancy", "Standardbelegung"),
            Map.entry("maxOccupancy", "Maximale Belegung"),
            Map.entry("bedCount", "Bettenanzahl"),
            Map.entry("bedType", "Bettenart"),
            Map.entry("sortOrder", "Sortierung"),
            Map.entry("countryCode", "Ländercode"),
            Map.entry("nationalityCode", "Nationalität"),
            Map.entry("currencyCode", "Währungscode"),
            Map.entry("languageCode", "Sprachcode"),
            Map.entry("timezone", "Zeitzone"),
            Map.entry("checkInTime", "Check-in-Zeit"),
            Map.entry("checkOutTime", "Check-out-Zeit"),
            Map.entry("firstName", "Vorname"),
            Map.entry("lastName", "Nachname"),
            Map.entry("arrivalDate", "Anreisedatum"),
            Map.entry("departureDate", "Abreisedatum"),
            Map.entry("adults", "Erwachsene"),
            Map.entry("children", "Kinder"),
            Map.entry("nightlyRate", "Preis pro Nacht"),
            Map.entry("minStay", "Mindestaufenthalt"),
            Map.entry("stayDate", "Aufenthaltsdatum"),
            Map.entry("openingFloat", "Anfangsbestand"),
            Map.entry("actualCash", "Ist-Bargeld"),
            Map.entry("amount", "Betrag"),
            Map.entry("method", "Zahlungsart"),
            Map.entry("serviceDate", "Leistungsdatum"),
            Map.entry("quantity", "Menge"),
            Map.entry("unitPrice", "Einzelpreis"),
            Map.entry("priority", "Priorität"),
            Map.entry("estimatedMinutes", "Zeitaufwand"),
            Map.entry("assignedTo", "Zuständige Person"),
            Map.entry("resolutionNotes", "Abschlussnotiz"),
            Map.entry("paymentTermsDays", "Zahlungsziel"),
            Map.entry("vatRate", "MWST-Satz"),
            Map.entry("dueDate", "Fälligkeitsdatum"),
            Map.entry("recipientName", "Rechnungsempfänger"),
            Map.entry("recipientAddress", "Rechnungsadresse"),
            Map.entry("recipientPostalCode", "PLZ des Rechnungsempfängers"),
            Map.entry("recipientCity", "Ort des Rechnungsempfängers"),
            Map.entry("recipientCountryCode", "Ländercode des Rechnungsempfängers"),
            Map.entry("creditorIban", "IBAN"),
            Map.entry("qrReference", "QR-Referenz"),
            Map.entry("privacyConsent", "Bestätigung"),
            Map.entry("documentNumber", "Ausweis- oder Passnummer"),
            Map.entry("signatureName", "Vollständiger Name"),
            Map.entry("vehiclePlate", "Kennzeichen"),
            Map.entry("addressLine", "Adresse"),
            Map.entry("addressLine1", "Adresse"),
            Map.entry("postalCode", "PLZ"),
            Map.entry("city", "Ort"),
            Map.entry("email", "E-Mail-Adresse"),
            Map.entry("billingEmail", "Rechnungs-E-Mail-Adresse"),
            Map.entry("phone", "Telefonnummer"),
            Map.entry("title", "Titel"),
            Map.entry("organizerName", "Veranstalter"),
            Map.entry("startAt", "Beginn"),
            Map.entry("endAt", "Ende"),
            Map.entry("attendees", "Teilnehmende"),
            Map.entry("totalAmount", "Gesamtbetrag"),
            Map.entry("externalThreadId", "Externe Vorgangs-ID"),
            Map.entry("subject", "Betreff"),
            Map.entry("body", "Nachricht"),
            Map.entry("sender", "Absender"),
            Map.entry("recipient", "Empfänger"),
            Map.entry("groupCode", "Gruppencode"),
            Map.entry("rooms", "Zimmerliste"),
            Map.entry("itemIds", "Positionen"),
            Map.entry("reason", "Begründung"),
            Map.entry("reference", "Referenz"),
            Map.entry("description", "Beschreibung"),
            Map.entry("notes", "Notiz"),
            Map.entry("type", "Art"),
            Map.entry("status", "Status"),
            Map.entry("code", "Code"),
            Map.entry("name", "Name"),
            Map.entry("label", "Bezeichnung")
    );

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleUserNotFound(UserNotFoundException ex) {
        // Logge die Exception intern – ohne sensible Details an den Client zu geben
        logger.error("User not found: {}", ex.getMessage());
        // Gib dem Client eine generische Fehlermeldung zurück
        return new ResponseEntity<>(new ErrorResponse("Benutzer wurde nicht gefunden."), HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleInvalidCredentials(InvalidCredentialsException ex) {
        logger.warn("Invalid login attempt: {}", ex.getMessage());
        return new ResponseEntity<>(new ErrorResponse(ex.getMessage()), HttpStatus.UNAUTHORIZED);
    }

    @ExceptionHandler(TooManyLoginAttemptsException.class)
    public ResponseEntity<ErrorResponse> handleTooManyLoginAttempts(TooManyLoginAttemptsException ex) {
        logger.warn("Login temporarily blocked: {}", ex.getMessage());
        return new ResponseEntity<>(new ErrorResponse(ex.getMessage()), HttpStatus.TOO_MANY_REQUESTS);
    }

    // Spezieller Handler für zu große Datei-Uploads
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleMaxSize(MaxUploadSizeExceededException ex) {
        logger.error("Dateiupload zu groß", ex);
        return new ResponseEntity<>(new ErrorResponse("Datei ist zu gross."), HttpStatus.PAYLOAD_TOO_LARGE);
    }

    @ExceptionHandler(BankingIntegrationException.class)
    public ResponseEntity<ErrorResponse> handleBankingIntegration(BankingIntegrationException ex) {
        logger.warn("Banking integration error: {}", ex.getMessage());
        return new ResponseEntity<>(new ErrorResponse(ex.getMessage()), HttpStatus.BAD_GATEWAY);
    }

    // Weitere spezifische Exception-Handler können hier hinzugefügt werden

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex) {
        logger.warn("Validation error: {}", ex.getMessage());
        return new ResponseEntity<>(new ErrorResponse(ex.getMessage()), HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> handleIllegalState(IllegalStateException ex) {
        logger.warn("Business rule conflict: {}", ex.getMessage());
        return new ResponseEntity<>(new ErrorResponse(ex.getMessage()), HttpStatus.CONFLICT);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> validationMessage(error.getField(), error.getCode()))
                .orElse("Die Eingabe ist ungültig.");
        return new ResponseEntity<>(new ErrorResponse(message), HttpStatus.BAD_REQUEST);
    }

    private String validationMessage(String field, String validationCode) {
        String fieldName = field == null ? "" : field.replaceAll("\\[[0-9]+]", "");
        int separator = fieldName.lastIndexOf('.');
        if (separator >= 0) {
            fieldName = fieldName.substring(separator + 1);
        }
        String label = VALIDATION_FIELD_LABELS.getOrDefault(fieldName, "Eingabe");
        String detail = switch (validationCode == null ? "" : validationCode) {
            case "NotBlank", "NotEmpty", "NotNull" -> "Dieses Feld ist erforderlich.";
            case "AssertTrue" -> "Bitte bestätigen.";
            case "Email" -> "Bitte eine gültige E-Mail-Adresse eingeben.";
            case "Pattern" -> "Das Format ist ungültig.";
            case "Size" -> "Die Länge oder Anzahl liegt ausserhalb des zulässigen Bereichs.";
            case "Min", "DecimalMin", "Positive", "PositiveOrZero" -> "Der Wert ist zu klein.";
            case "Max", "DecimalMax", "Negative", "NegativeOrZero" -> "Der Wert ist zu gross.";
            case "Future", "FutureOrPresent" -> "Das Datum muss in der Zukunft liegen.";
            case "Past", "PastOrPresent" -> "Das Datum darf nicht in der Zukunft liegen.";
            default -> "Die Eingabe ist ungültig.";
        };
        return label + ": " + detail;
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        logger.warn("Data integrity violation", ex);
        return new ResponseEntity<>(new ErrorResponse("Die Daten verletzen eine Eindeutigkeits- oder Referenzregel."), HttpStatus.CONFLICT);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex) {
        logger.warn("Access denied: {}", ex.getMessage());
        return new ResponseEntity<>(new ErrorResponse("Keine Berechtigung für diese Aktion."), HttpStatus.FORBIDDEN);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatus(ResponseStatusException ex) {
        String message = ex.getReason() != null ? ex.getReason() : ex.getStatusCode().toString();
        logger.warn("Request rejected with status {}: {}", ex.getStatusCode().value(), message);
        return ResponseEntity.status(ex.getStatusCode()).body(new ErrorResponse(message));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex) {
        // Logge den Fehler inklusive Stacktrace intern, aber gebe nur eine generische Nachricht an den Client weiter
        logger.error("Ein unerwarteter Fehler ist aufgetreten.", ex);
        return new ResponseEntity<>(new ErrorResponse("Ein unerwarteter Fehler ist aufgetreten."), HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
