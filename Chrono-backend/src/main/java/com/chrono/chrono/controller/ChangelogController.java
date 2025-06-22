package com.chrono.chrono.controller;

import com.chrono.chrono.dto.ChangelogDTO;
import com.chrono.chrono.services.ChangelogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/changelog")
public class ChangelogController {

    private final ChangelogService changelogService;

    @Autowired
    public ChangelogController(ChangelogService changelogService) {
        this.changelogService = changelogService;
    }

    /**
     * Erstellt einen neuen Changelog-Eintrag. Nur für SUPER_ADMIN erlaubt.
     */
    @PostMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ChangelogDTO> createChangelog(@RequestBody ChangelogDTO changelogDTO) {
        ChangelogDTO createdChangelog = changelogService.createChangelog(changelogDTO);
        // Gibt den Status 201 Created mit dem neuen Objekt zurück.
        return ResponseEntity.created(URI.create("/api/changelog/" + createdChangelog.getId())).body(createdChangelog);
    }

    /**
     * Holt den allerneuesten Changelog-Eintrag.
     */
    @GetMapping("/latest")
    public ResponseEntity<ChangelogDTO> getLatestChangelog() {
        return changelogService.getLatestChangelog()
                .map(ResponseEntity::ok) // Wenn ein Changelog gefunden wird -> 200 OK
                .orElse(ResponseEntity.notFound().build()); // Wenn KEINER gefunden wird -> 404 Not Found
    }

    /**
     * Holt alle Changelog-Einträge, sortiert nach dem neuesten.
     */
    @GetMapping
    public ResponseEntity<List<ChangelogDTO>> getAllChangelogs() {
        List<ChangelogDTO> changelogs = changelogService.getAllChangelogs();
        return ResponseEntity.ok(changelogs);
    }
}