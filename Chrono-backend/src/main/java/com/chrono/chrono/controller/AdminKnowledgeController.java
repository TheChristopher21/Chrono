package com.chrono.chrono.controller;

import com.chrono.chrono.entities.Company;
import com.chrono.chrono.entities.CompanyKnowledge;
import com.chrono.chrono.entities.User;
import com.chrono.chrono.repositories.CompanyRepository;
import com.chrono.chrono.repositories.UserRepository;
import com.chrono.chrono.services.CompanyKnowledgeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/admin/knowledge")
@PreAuthorize("hasRole('ADMIN') or hasRole('SUPERADMIN')")
public class AdminKnowledgeController {

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private CompanyRepository companyRepository;
    @Autowired
    private CompanyKnowledgeService knowledgeService;

    private Company getCompany(Principal principal) {
        User admin = userRepository.findByUsername(principal.getName()).orElse(null);
        if (admin == null) return null;
        if (admin.getCompany() == null) {
            return null;
        }
        return companyRepository.findById(admin.getCompany().getId()).orElse(null);
    }

    @GetMapping
    public ResponseEntity<List<CompanyKnowledge>> list(Principal principal) {
        Company c = getCompany(principal);
        if (c == null) return ResponseEntity.badRequest().build();
        return ResponseEntity.ok(knowledgeService.findByCompany(c));
    }

    @PostMapping
    public ResponseEntity<CompanyKnowledge> create(@RequestBody CompanyKnowledge doc,
                                                   Principal principal) {
        Company c = getCompany(principal);
        if (c == null) return ResponseEntity.badRequest().build();
        doc.setCompany(c);
        return ResponseEntity.ok(knowledgeService.save(doc));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id, Principal principal) {
        Company c = getCompany(principal);
        if (c == null) return ResponseEntity.badRequest().build();
        List<CompanyKnowledge> docs = knowledgeService.findByCompany(c);
        boolean belongs = docs.stream().anyMatch(d -> d.getId().equals(id));
        if (!belongs) return ResponseEntity.status(403).build();
        knowledgeService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
