package com.chrono.chrono.services;

import com.chrono.chrono.dto.ChangelogDTO;
import com.chrono.chrono.entities.Changelog;
import com.chrono.chrono.repositories.ChangelogRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class ChangelogService {

    private final ChangelogRepository changelogRepository;

    @Autowired
    public ChangelogService(ChangelogRepository changelogRepository) {
        this.changelogRepository = changelogRepository;
    }

    @Transactional
    public ChangelogDTO createChangelog(ChangelogDTO changelogDTO) {
        Changelog changelog = toEntity(changelogDTO);
        Changelog savedChangelog = changelogRepository.save(changelog);
        return toDTO(savedChangelog);
    }

    @Transactional(readOnly = true)
    public Optional<ChangelogDTO> getLatestChangelog() {
        return changelogRepository.findTopByOrderByCreatedAtDesc().map(this::toDTO);
    }

    @Transactional(readOnly = true)
    public List<ChangelogDTO> getAllChangelogs() {
        return changelogRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    // --- Private Mapper-Methoden ---

    private ChangelogDTO toDTO(Changelog changelog) {
        return new ChangelogDTO(
                changelog.getId(),
                changelog.getVersion(),
                changelog.getTitle(),
                changelog.getContent(),
                changelog.getCreatedAt()
        );
    }

    private Changelog toEntity(ChangelogDTO dto) {
        Changelog changelog = new Changelog();
        // Die ID wird nicht vom DTO übernommen, sie wird von der DB generiert.
        changelog.setVersion(dto.getVersion());
        changelog.setTitle(dto.getTitle());
        changelog.setContent(dto.getContent());
        // Das Erstellungsdatum wird automatisch von der DB gesetzt.
        return changelog;
    }
}