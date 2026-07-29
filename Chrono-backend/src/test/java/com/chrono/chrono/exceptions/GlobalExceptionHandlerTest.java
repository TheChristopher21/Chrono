package com.chrono.chrono.exceptions;

import com.chrono.chrono.dto.ErrorResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GlobalExceptionHandlerTest {

    @Test
    void handleResponseStatus_preservesStatusAndReason() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();

        ResponseEntity<ErrorResponse> response = handler.handleResponseStatus(
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Demo login is disabled")
        );

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals("Demo login is disabled", response.getBody().getMessage());
    }

    @Test
    void handleValidation_usesReadableGermanPmsFieldLabel() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "request");
        bindingResult.addError(new FieldError(
                "request",
                "rooms[0].roomTypeId",
                null,
                false,
                new String[]{"NotNull"},
                null,
                "must not be null"
        ));
        MethodArgumentNotValidException exception = mock(MethodArgumentNotValidException.class);
        when(exception.getBindingResult()).thenReturn(bindingResult);

        ResponseEntity<ErrorResponse> response = handler.handleValidation(exception);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Zimmertyp: Dieses Feld ist erforderlich.", response.getBody().getMessage());
    }

    @Test
    void handleValidation_doesNotExposeLocaleDependentValidatorText() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "request");
        bindingResult.addError(new FieldError(
                "request",
                "countryCode",
                "CHE",
                false,
                new String[]{"Pattern"},
                null,
                "must match \"(?i)[A-Z]{2}\""
        ));
        MethodArgumentNotValidException exception = mock(MethodArgumentNotValidException.class);
        when(exception.getBindingResult()).thenReturn(bindingResult);

        ResponseEntity<ErrorResponse> response = handler.handleValidation(exception);

        assertEquals("Ländercode: Das Format ist ungültig.", response.getBody().getMessage());
    }

    @Test
    void handleAccessDenied_usesReadableSwissGermanMessage() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();

        ResponseEntity<ErrorResponse> response =
                handler.handleAccessDenied(new AccessDeniedException("technical detail"));

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertEquals("Keine Berechtigung für diese Aktion.", response.getBody().getMessage());
    }
}
