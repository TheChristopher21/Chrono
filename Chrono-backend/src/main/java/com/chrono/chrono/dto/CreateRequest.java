package com.chrono.chrono.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.databind.JsonNode;

/** Create bodies never choose the identity of the row being persisted. */
@JsonIgnoreProperties(ignoreUnknown = true)
public abstract class CreateRequest {
    @JsonSetter("id")
    public final void rejectId(JsonNode ignored) {
        throw new IllegalArgumentException("Bei der Erstellung darf keine ID angegeben werden.");
    }

    /** Relationships are resolved and authorized by the controller, never persisted from JSON. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Reference {
        private Long id;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
    }
}
