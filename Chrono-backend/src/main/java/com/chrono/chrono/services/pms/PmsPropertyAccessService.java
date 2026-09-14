package com.chrono.chrono.services.pms;

import com.chrono.chrono.dto.pms.PmsAccessDtos;
import com.chrono.chrono.entities.User;
import com.chrono.chrono.entities.pms.HotelProperty;
import com.chrono.chrono.entities.pms.PmsPropertyGrant;
import com.chrono.chrono.repositories.UserRepository;
import com.chrono.chrono.repositories.pms.HotelPropertyRepository;
import com.chrono.chrono.repositories.pms.PmsPropertyGrantRepository;
import com.chrono.chrono.services.UserPermissionService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;

@Service
public class PmsPropertyAccessService {
    public static final List<String> PERMISSIONS = List.of("FRONT_DESK", "GUESTS", "HOUSEKEEPING", "FINANCE", "REFUNDS", "RATES", "REPORTS", "INTEGRATIONS");
    private final UserRepository users;
    private final HotelPropertyRepository properties;
    private final PmsPropertyGrantRepository grants;
    private final UserPermissionService permissions;
    private final PmsAuditWriter audit;
    private com.chrono.chrono.repositories.pms.PmsUserDirectoryRepository directory;
    @org.springframework.beans.factory.annotation.Autowired
    public void setDirectory(com.chrono.chrono.repositories.pms.PmsUserDirectoryRepository directory){this.directory=directory;}

    public PmsPropertyAccessService(UserRepository users, HotelPropertyRepository properties,
                                   PmsPropertyGrantRepository grants, UserPermissionService permissions, PmsAuditWriter audit) {
        this.users = users; this.properties = properties; this.grants = grants; this.permissions = permissions; this.audit = audit;
    }

    @Transactional(readOnly = true)
    public Access access(String username) {
        User actor = requireUser(username);
        return access(actor);
    }

    private Access access(User actor) {
        Map<Long, Map<String, String>> allowed = new LinkedHashMap<>();
        boolean manage = permissions.hasPageAccess(actor, "pms", "MANAGE");
        boolean master = manage && permissions.hasPageAccess(actor, "pmsSettings", "MANAGE");
        if (!master) for (PmsPropertyGrant grant : grants.findByUser_IdAndProperty_Company_Id(actor.getId(), actor.getCompany().getId())) {
            Map<String, String> values = new LinkedHashMap<>();
            grant.getPermissions().forEach((key, value) -> {
                if (PERMISSIONS.contains(key) && Set.of("VIEW", "MANAGE").contains(value)) {
                    values.put(key, manage ? value : "VIEW");
                }
            });
            if (!values.isEmpty()) allowed.put(grant.getProperty().getId(), Map.copyOf(values));
        }
        return new Access(actor.getId(), actor.getCompany().getId(), master, Map.copyOf(allowed));
    }

    @Transactional(readOnly = true)
    public PmsAccessDtos.Self self(String username) {
        Access access = access(username);
        return new PmsAccessDtos.Self(access.userId(), access.master(), PERMISSIONS,
                properties.findAllByCompany_IdOrderByNameAsc(access.companyId()).stream()
                        .filter(property -> access.any(property.getId()))
                        .map(property -> new PmsAccessDtos.PropertyAccess(property.getId(), property.getName(), access.permissions(property.getId())))
                        .toList());
    }

    @Transactional(readOnly = true)
    public PmsAccessDtos.Administration administration(String username) {
        return administration(username,0,50,"");
    }

    @Transactional(readOnly=true)
    public PmsAccessDtos.Administration administration(String username,int page,int size,String query) {
        Access actor = requireMaster(username);
        if(page<0 || page>10000 || size<1 || size>100)throw invalid("Seite muss 0–10000 und Seitengröße 1–100 sein.");
        String search=query==null?"":query.trim().toLowerCase(Locale.ROOT);
        if(search.length()>120)throw invalid("Der Suchtext darf höchstens 120 Zeichen enthalten.");
        String pattern="%"+search.replace("!","!!").replace("%","!%").replace("_","!_")+"%";
        var ids=directory.searchIds(actor.companyId(),pattern,org.springframework.data.domain.PageRequest.of(page,size));
        Map<Long,User> people=new HashMap<>();
        Map<Long,List<PmsAccessDtos.Grant>> assigned=new HashMap<>();
        if(!ids.isEmpty()) {
            directory.loadPermissionContext(actor.companyId(),ids.getContent()).forEach(user->people.put(user.getId(),user));
            grants.findByUser_IdInAndProperty_Company_Id(ids.getContent(),actor.companyId()).forEach(grant->assigned.computeIfAbsent(grant.getUser().getId(),ignored->new ArrayList<>())
                    .add(new PmsAccessDtos.Grant(grant.getProperty().getId(),Map.copyOf(grant.getPermissions()))));
        }
        List<PmsAccessDtos.UserAccess> pageUsers=ids.getContent().stream().map(people::get).filter(Objects::nonNull)
                .map(user->new PmsAccessDtos.UserAccess(user.getId(),user.getUsername(),
                        String.join(" ",Objects.toString(user.getFirstName(),""),Objects.toString(user.getLastName(),"")).trim(),
                        isMaster(user),assigned.getOrDefault(user.getId(),List.of()))).toList();
        return new PmsAccessDtos.Administration(PERMISSIONS,
                properties.findAllByCompany_IdOrderByNameAsc(actor.companyId()).stream()
                        .map(property -> new PmsAccessDtos.Property(property.getId(), property.getName())).toList(),
                pageUsers,page,size,ids.getTotalElements(),ids.hasNext());
    }

    @Transactional
    public PmsAccessDtos.UserAccess update(String username, Long userId, PmsAccessDtos.Update request) {
        Access actor = requireMaster(username);
        User target = users.findById(userId).filter(user -> !user.isDeleted() && user.getCompany() != null
                && Objects.equals(user.getCompany().getId(), actor.companyId())).orElseThrow(() -> denied("Mitarbeiter nicht verfügbar."));
        if (isMaster(target)) throw denied("Masterkonten werden über die Masterberechtigung verwaltet.");
        if (request == null || request.grants() == null || request.grants().size() > 1000) throw invalid("Hotelzuweisungen fehlen oder sind zu umfangreich.");
        List<PmsPropertyGrant> replacements = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        for (PmsAccessDtos.Grant input : request.grants()) {
            if (input == null || input.propertyId() == null || !seen.add(input.propertyId())) throw invalid("Hotelzuweisungen müssen eindeutig sein.");
            HotelProperty property = properties.findByIdAndCompany_Id(input.propertyId(), actor.companyId())
                    .orElseThrow(() -> denied("Hotel nicht verfügbar."));
            if (input.permissions() == null || input.permissions().size() > PERMISSIONS.size()) throw invalid("Berechtigungen sind ungültig.");
            Map<String, String> values = new LinkedHashMap<>();
            input.permissions().forEach((key, value) -> {
                if (!PERMISSIONS.contains(key) || value == null || !Set.of("NONE", "VIEW", "MANAGE").contains(value)) throw invalid("Unbekannte PMS-Berechtigung oder Zugriffsstufe.");
                if (!"NONE".equals(value)) values.put(key, value);
            });
            if (!values.isEmpty()) {
                PmsPropertyGrant grant = new PmsPropertyGrant();
                grant.setUser(target); grant.setProperty(property); grant.setPermissions(values); replacements.add(grant);
            }
        }
        Set<Long> changedProperties = new TreeSet<>();
        grants.findByUser_IdAndProperty_Company_Id(userId, actor.companyId()).forEach(grant -> changedProperties.add(grant.getProperty().getId()));
        replacements.forEach(grant -> changedProperties.add(grant.getProperty().getId()));
        grants.deleteByUser_Id(userId);
        grants.flush();
        for (Long propertyId : changedProperties) {
            HotelProperty property = properties.findByIdAndCompany_Id(propertyId, actor.companyId()).orElseThrow();
            String details = "{\"userId\":" + userId + ",\"permissions\":{"
                    + replacements.stream().filter(grant -> grant.getProperty().getId().equals(propertyId)).findFirst()
                    .map(grant -> grant.getPermissions().entrySet().stream().sorted(Map.Entry.comparingByKey())
                            .map(entry -> "\"" + entry.getKey() + "\":\"" + entry.getValue() + "\"")
                            .collect(java.util.stream.Collectors.joining(","))).orElse("") + "}}";
            audit.append(property, "pms_access.changed", "user", userId.toString(), details);
        }
        grants.saveAll(replacements);
        grants.flush();
        return userView(target);
    }

    public void require(Access access, Long propertyId, String permission, boolean write) {
        if (propertyId == null || !access.allows(propertyId, permission, write)) throw denied("Keine Berechtigung für diesen Hotelbereich.");
        if (properties.findByIdAndCompany_Id(propertyId, access.companyId()).isEmpty()) throw denied("Hotel nicht verfügbar.");
    }

    private User requireUser(String username) {
        if (username == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentifizierung erforderlich.");
        User user = users.findByUsernameWithPermissionContext(username).filter(actor -> !actor.isDeleted())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Benutzer nicht verfügbar."));
        permissions.assertPageAccess(user, "pms", "VIEW", "PMS-Zugriff erforderlich.");
        if (user.getCompany() == null) throw denied("Firmenzuordnung erforderlich.");
        return user;
    }

    private boolean isMaster(User user) {
        return permissions.hasPageAccess(user, "pms", "MANAGE") && permissions.hasPageAccess(user, "pmsSettings", "MANAGE");
    }
    public Access requireMaster(String username) {
        Access actor = access(username);
        if (!actor.master()) throw denied("Nur ein PMS-Master darf Hotelrechte verwalten.");
        return actor;
    }
    private PmsAccessDtos.UserAccess userView(User user) {
        return new PmsAccessDtos.UserAccess(user.getId(), user.getUsername(),
                String.join(" ", Objects.toString(user.getFirstName(), ""), Objects.toString(user.getLastName(), "")).trim(),
                isMaster(user), grants.findByUser_IdAndProperty_Company_Id(user.getId(), user.getCompany().getId()).stream()
                        .map(grant -> new PmsAccessDtos.Grant(grant.getProperty().getId(), Map.copyOf(grant.getPermissions()))).toList());
    }

    public record Access(Long userId, Long companyId, boolean master, Map<Long, Map<String, String>> grants) {
        public boolean any(Long propertyId) { return master || !grants.getOrDefault(propertyId, Map.of()).isEmpty(); }
        public boolean any() { return master || !grants.isEmpty(); }
        public boolean allows(Long propertyId, String permission, boolean write) {
            if (master) return true;
            if (permission == null) return !write && any(propertyId);
            String level = grants.getOrDefault(propertyId, Map.of()).get(permission);
            return "MANAGE".equals(level) || !write && "VIEW".equals(level);
        }
        public Map<String, String> permissions(Long propertyId) {
            if (!master) return grants.getOrDefault(propertyId, Map.of());
            Map<String, String> result = new LinkedHashMap<>(); PERMISSIONS.forEach(key -> result.put(key, "MANAGE")); return Map.copyOf(result);
        }
    }
    private static ResponseStatusException denied(String message) { return new ResponseStatusException(HttpStatus.FORBIDDEN, message); }
    private static ResponseStatusException invalid(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
}
