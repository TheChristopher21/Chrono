package com.chrono.chrono.controller.pms;

import com.chrono.chrono.entities.User;
import com.chrono.chrono.repositories.UserRepository;
import com.chrono.chrono.services.UserPermissionService;
import com.chrono.chrono.services.pms.PmsProfileDocumentService;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.List;

@RestController @RequestMapping("/api/pms")
public class PmsProfileDocumentController {
    private final PmsProfileDocumentService service;
    private final UserRepository users;
    private final UserPermissionService permissions;
    public PmsProfileDocumentController(PmsProfileDocumentService service, UserRepository users, UserPermissionService permissions) {
        this.service=service; this.users=users; this.permissions=permissions;
    }
    @GetMapping("/organizations/{id}/documents")
    public List<PmsProfileDocumentService.DocumentView> list(@PathVariable Long id, Principal principal) {
        return service.list(user(principal,false).getCompany(),id);
    }
    @PostMapping(value="/organizations/{id}/documents",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public PmsProfileDocumentService.DocumentView upload(@PathVariable Long id, @RequestParam(required=false) Long ratePlanId,
                                                         @RequestPart("file") MultipartFile file, Principal principal) {
        User user=user(principal,true); return service.upload(user.getCompany(),id,ratePlanId,file,user.getUsername());
    }
    @GetMapping("/documents/{id}/download")
    public ResponseEntity<byte[]> download(@PathVariable Long id, Principal principal) {
        var doc=service.download(user(principal,false).getCompany(),id);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(doc.getContentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment().filename(doc.getFileName(), StandardCharsets.UTF_8).build().toString())
                .header(HttpHeaders.CACHE_CONTROL,"private, no-store").header("X-Content-Type-Options","nosniff")
                .contentLength(doc.getSizeBytes()).body(doc.getContent());
    }
    @DeleteMapping("/documents/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id,Principal principal) {
        service.delete(user(principal,true).getCompany(),id); return ResponseEntity.noContent().build();
    }
    private User user(Principal principal,boolean write) {
        if(principal==null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Authentifizierung erforderlich.");
        User user=users.findByUsernameWithPermissionContext(principal.getName()).filter(value -> !value.isDeleted())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        permissions.assertPageAccess(user,UserPermissionService.PAGE_PMS,write ? UserPermissionService.ACCESS_MANAGE : UserPermissionService.ACCESS_VIEW,"PMS-Berechtigung fehlt.");
        if(write) permissions.assertPageAccess(user,UserPermissionService.PAGE_PMS_SETTINGS,UserPermissionService.ACCESS_MANAGE,"Master-Berechtigung erforderlich.");
        if(user.getCompany()==null) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        return user;
    }
}
