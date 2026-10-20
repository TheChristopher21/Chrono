package com.chrono.chrono.services.pms;
import com.chrono.chrono.dto.pms.PmsAdvancedResponse.*;
import com.chrono.chrono.dto.pms.PmsDirectoryDtos.Page;
import com.chrono.chrono.repositories.pms.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.util.*;
@Service @Transactional(readOnly=true)
public class PmsDirectoryService {
 private final PmsPropertyAccessService access; private final PmsOrganizationRepository organizations; private final GroupBookingRepository groups;private final PmsAdvancedService advanced;
 public PmsDirectoryService(PmsPropertyAccessService access,PmsOrganizationRepository organizations,GroupBookingRepository groups,PmsAdvancedService advanced){this.access=access;this.organizations=organizations;this.groups=groups;this.advanced=advanced;}
 public Page<OrganizationView> organizations(String user,Long propertyId,int page,int size,String query,boolean activeOnly,boolean masterOnly){
  var actor=allowed(user,propertyId,true);var result=organizations.searchDirectory(actor.companyId(),search(query),activeOnly,masterOnly,paging(page,size));
  return new Page<>(result.getContent().stream().map(advanced::organizationView).toList(),page,size,result.getTotalElements(),result.hasNext());
 }
 public OrganizationView organization(String user,Long propertyId,Long id){var actor=allowed(user,propertyId,true);return advanced.organizationView(organizations.findByIdAndCompany_Id(id,actor.companyId()).orElseThrow(()->missing()));}
 public Page<GroupBookingView> groups(String user,Long propertyId,int page,int size,String query){allowed(user,propertyId,false);var result=groups.searchDirectory(propertyId,search(query),paging(page,size));return new Page<>(result.getContent().stream().map(advanced::groupView).toList(),page,size,result.getTotalElements(),result.hasNext());}
 public GroupBookingView group(String user,Long propertyId,Long id){var actor=allowed(user,propertyId,false);return advanced.groupView(groups.findByIdAndProperty_Company_Id(id,actor.companyId()).filter(g->g.getProperty().getId().equals(propertyId)).orElseThrow(()->missing()));}
 private PmsPropertyAccessService.Access allowed(String user,Long id,boolean organization){var actor=access.access(user);for(String role:organization?List.of("GUESTS","FRONT_DESK","FINANCE","RATES"):List.of("FRONT_DESK","FINANCE")){if(actor.allows(id,role,false)){access.require(actor,id,role,false);return actor;}}throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Keine Berechtigung für dieses Hotelverzeichnis.");}
 private PageRequest paging(int page,int size){if(page<0||page>10000||size<1||size>100)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Ungültige Verzeichnisseite.");return PageRequest.of(page,size);}
 private String search(String value){String s=value==null?"":value.trim().toLowerCase(Locale.ROOT);if(s.length()>120)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Suchtext zu lang.");return s.isEmpty()?"":"%"+s.replace("!","!!").replace("%","!%").replace("_","!_")+"%";}
 private ResponseStatusException missing(){return new ResponseStatusException(HttpStatus.NOT_FOUND,"Verzeichniseintrag nicht gefunden.");}
}
