package com.chrono.chrono.exceptions;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@ControllerAdvice
public class GlobalExceptionHandler {

    // Verwende einen Logger, um Fehler sicher zu protokollieren
    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<String> handleUserNotFound(UserNotFoundException ex) {
        // Logge die Exception intern – ohne sensible Details an den Client zu geben
        logger.error("User not found: {}", ex.getMessage());
        // Gib dem Client eine generische Fehlermeldung zurück
        return new ResponseEntity<>("Benutzer wurde nicht gefunden.", HttpStatus.NOT_FOUND);
    }

    // Spezieller Handler für zu große Datei-Uploads
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<String> handleMaxSize(MaxUploadSizeExceededException ex) {
        logger.error("Dateiupload zu groß", ex);
        return new ResponseEntity<>("Datei ist zu groß.", HttpStatus.PAYLOAD_TOO_LARGE);
    }

    // Weitere spezifische Exception-Handler können hier hinzugefügt werden

    @ExceptionHandler(Exception.class)
    public ResponseEntity<String> handleGenericException(Exception ex) {
        // Logge den Fehler inklusive Stacktrace intern, aber gebe nur eine generische Nachricht an den Client weiter
        logger.error("Ein unerwarteter Fehler ist aufgetreten.", ex);
        return new ResponseEntity<>("Ein unerwarteter Fehler ist aufgetreten.", HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
