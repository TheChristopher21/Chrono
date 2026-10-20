package com.chrono.chrono.services.pms;

import com.chrono.chrono.dto.pms.*;
import com.chrono.chrono.entities.Company;
import com.chrono.chrono.entities.pms.*;
import com.chrono.chrono.repositories.pms.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import java.util.*;

@Service
public class PmsHousekeepingService {
    private final HotelPropertyRepository properties;
    private final RoomRepository rooms;
    private final HousekeepingTaskRepository tasks;
    private final HousekeepingTaskEventRepository events;
    private final PmsAuditWriter audit;
    public PmsHousekeepingService(HotelPropertyRepository properties, RoomRepository rooms,
            HousekeepingTaskRepository tasks, HousekeepingTaskEventRepository events, PmsAuditWriter audit) {
        this.properties=properties; this.rooms=rooms; this.tasks=tasks; this.events=events; this.audit=audit;
    }
    @Transactional(readOnly=true)
    public List<PmsHousekeepingDtos.Task> list(Company company, Long propertyId, LocalDate date) {
        HotelProperty property = property(company, propertyId, false);
        return tasks.findAllByProperty_IdAndServiceDateOrderByPriorityDescRoom_NumberAsc(propertyId,
                date == null ? today(property) : date).stream().map(this::view).toList();
    }
    @Transactional
    public PmsHousekeepingDtos.Task create(Company company, Long propertyId, PmsHousekeepingDtos.Create request) {
        HotelProperty property = property(company, propertyId, true);
        Room room = rooms.findByIdAndProperty_Company_Id(request.roomId(), company.getId()).filter(value -> value.getProperty().getId().equals(propertyId)).orElseThrow(PmsHousekeepingService::missing);
        if (tasks.findByRoom_IdAndServiceDateAndWorkType(room.getId(), request.serviceDate(), request.workType()).isPresent())
            throw conflict("Für diese Arbeitsart gibt es bereits eine Aufgabe an diesem Tag.");
        HousekeepingTask task = new HousekeepingTask(); task.setProperty(property); task.setRoom(room);
        task.setServiceDate(request.serviceDate()); task.setWorkType(request.workType()); task.setType(request.type());
        task.setPriority(request.priority()); task.setEstimatedMinutes(request.estimatedMinutes());
        task.setNotes(clean(request.notes())); task.setAssignedTo(clean(request.assignedTo()));
        task.setStatus(request.workType() == HousekeepingWorkType.INSPECTION ? HousekeepingStatus.INSPECTION : HousekeepingStatus.DIRTY);
        task = tasks.saveAndFlush(task); event(task, null); syncRoom(task); return view(task);
    }
    @Transactional
    public PmsHousekeepingDtos.BatchResult createBatch(Company company, Long propertyId, PmsHousekeepingDtos.BatchCreate request) {
        property(company, propertyId, true);
        long nights = java.time.temporal.ChronoUnit.DAYS.between(request.from(), request.toExclusive());
        var ids = new TreeSet<>(request.roomIds());
        if (nights < 1 || nights > 31 || ids.size() > 100 || nights * ids.size() > 500)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ein Sammelauftrag umfasst höchstens 100 Zimmer, 31 Tage und 500 Aufgaben.");
        // Validate all rooms before creating anything; the entire batch is one hotel transaction.
        for (Long id : ids) rooms.findByIdAndProperty_Company_Id(id, company.getId()).filter(value -> value.getProperty().getId().equals(propertyId)).orElseThrow(PmsHousekeepingService::missing);
        int created = 0; int existing = 0;
        for (Long roomId : ids) for (LocalDate date=request.from();date.isBefore(request.toExclusive());date=date.plusDays(1)) {
            if (tasks.findByRoom_IdAndServiceDateAndWorkType(roomId,date,request.workType()).isPresent()) { existing++; continue; }
            create(company,propertyId,new PmsHousekeepingDtos.Create(roomId,date,request.workType(),HousekeepingTaskType.MANUAL,
                    request.priority(),request.estimatedMinutes(),request.notes(),request.assignedTo())); created++;
        }
        return new PmsHousekeepingDtos.BatchResult(created,existing);
    }
    @Transactional
    public PmsHousekeepingDtos.Task update(Company company, Long propertyId, Long taskId, PmsHousekeepingDtos.Update request) {
        property(company, propertyId, true);
        HousekeepingTask task = task(company, propertyId, taskId);
        if (request.version() == null || request.version() != task.getVersion()) throw conflict("Die Aufgabe wurde zwischenzeitlich geändert. Bitte neu laden.");
        HousekeepingWorkStatus previous = task.getWorkStatus();
        if (request.workStatus() == HousekeepingWorkStatus.DONE && task.getWorkType() == HousekeepingWorkType.INSPECTION) {
            var cleaning = tasks.findByRoom_IdAndServiceDateAndWorkType(task.getRoom().getId(), task.getServiceDate(), HousekeepingWorkType.CLEAN);
            if (cleaning.isPresent() && cleaning.get().getWorkStatus() != HousekeepingWorkStatus.DONE)
                throw conflict("Vor der Freigabe muss die Reinigung abgeschlossen sein.");
            if (cleaning.isEmpty() && task.getServiceDate().equals(today(task.getProperty()))
                    && Set.of(HousekeepingStatus.DIRTY, HousekeepingStatus.IN_PROGRESS, HousekeepingStatus.OUT_OF_SERVICE).contains(task.getRoom().getHousekeepingStatus()))
                throw conflict("Das Zimmer ist noch nicht zur Kontrolle bereit.");
        }
        if (Set.of(HousekeepingWorkStatus.DND, HousekeepingWorkStatus.DEFERRED).contains(request.workStatus()) && clean(request.notes()) == null)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Für DND oder Zurückstellung ist eine Notiz erforderlich.");
        task.setWorkStatus(request.workStatus()); task.setPriority(request.priority()); task.setEstimatedMinutes(request.estimatedMinutes());
        task.setNotes(clean(request.notes())); task.setAssignedTo(clean(request.assignedTo()));
        task.setCompletedAt(request.workStatus() == HousekeepingWorkStatus.DONE ? LocalDateTime.now().withNano(0) : null);
        task.setStatus(switch(request.workStatus()) {
            case DONE -> HousekeepingStatus.CLEAN; case IN_PROGRESS -> HousekeepingStatus.IN_PROGRESS;
            default -> task.getWorkType() == HousekeepingWorkType.INSPECTION ? HousekeepingStatus.INSPECTION : HousekeepingStatus.DIRTY;
        });
        tasks.saveAndFlush(task); event(task, previous); syncRoom(task); return view(task);
    }
    @Transactional(readOnly=true)
    public PmsHousekeepingDtos.History history(Company company, Long propertyId, Long taskId, int page, int size) {
        property(company, propertyId, false); task(company, propertyId, taskId);
        var result = events.findAllByTask_IdOrderByIdDesc(taskId, PageRequest.of(Math.max(0,page), Math.max(1,Math.min(100,size))));
        return new PmsHousekeepingDtos.History(result.getContent().stream().map(value -> new PmsHousekeepingDtos.Event(value.getId(),value.getFromStatus(),value.getToStatus(),value.getAssignedTo(),value.getNotes(),value.getActor(),value.getCreatedAt())).toList(),
                result.getNumber(),result.getSize(),result.getTotalElements(),result.hasNext());
    }
    @Transactional
    public void legacyCreate(Company company, Long propertyId, CreateHousekeepingTaskRequest request) {
        create(company,propertyId,new PmsHousekeepingDtos.Create(request.roomId(),request.serviceDate(),
                request.type()==HousekeepingTaskType.INSPECTION ? HousekeepingWorkType.INSPECTION : HousekeepingWorkType.CLEAN,
                request.type(),request.priority(),request.estimatedMinutes(),request.notes(),request.assignedTo()));
    }
    @Transactional
    public void legacyUpdate(Company company, Long propertyId, Long taskId, UpdateHousekeepingTaskRequest request) {
        property(company,propertyId,true); HousekeepingTask task=task(company,propertyId,taskId);
        HousekeepingWorkStatus status = switch(request.status()) {
            case CLEAN -> HousekeepingWorkStatus.DONE; case IN_PROGRESS -> HousekeepingWorkStatus.IN_PROGRESS; default -> HousekeepingWorkStatus.OPEN;
        };
        update(company,propertyId,taskId,new PmsHousekeepingDtos.Update(task.getVersion(),status,request.priority(),request.estimatedMinutes(),request.notes(),request.assignedTo()));
        // Changing the legacy reason before the versioned update would flush and
        // advance @Version during its property lookup, rejecting our own request.
        task.setType(request.type());
        if(request.status()==HousekeepingStatus.OUT_OF_SERVICE) {
            task.setStatus(HousekeepingStatus.OUT_OF_SERVICE);
            if(task.getWorkType()!=HousekeepingWorkType.TURNDOWN && task.getServiceDate().equals(today(task.getProperty()))) {
                task.getRoom().setHousekeepingStatus(HousekeepingStatus.OUT_OF_SERVICE); rooms.save(task.getRoom());
            }
        }
    }
    @Transactional
    public void departure(HotelProperty property, Room room, LocalDate date) {
        HousekeepingTask task=tasks.findByRoom_IdAndServiceDateAndWorkType(room.getId(),date,HousekeepingWorkType.CLEAN).orElseGet(HousekeepingTask::new);
        HousekeepingWorkStatus previous=task.getId()==null ? null : task.getWorkStatus();
        task.setProperty(property); task.setRoom(room); task.setServiceDate(date); task.setType(HousekeepingTaskType.DEPARTURE);
        task.setWorkType(HousekeepingWorkType.CLEAN); task.setWorkStatus(HousekeepingWorkStatus.OPEN);
        task.setStatus(HousekeepingStatus.DIRTY); task.setPriority(90); task.setEstimatedMinutes(35); task.setCompletedAt(null);
        tasks.saveAndFlush(task); event(task,previous);
        tasks.findByRoom_IdAndServiceDateAndWorkType(room.getId(),date,HousekeepingWorkType.INSPECTION).ifPresent(inspection -> {
            HousekeepingWorkStatus old=inspection.getWorkStatus(); inspection.setWorkStatus(HousekeepingWorkStatus.OPEN);
            inspection.setStatus(HousekeepingStatus.INSPECTION); inspection.setCompletedAt(null); tasks.saveAndFlush(inspection); event(inspection,old);
        });
    }
    private void syncRoom(HousekeepingTask task) {
        if (!task.getServiceDate().equals(today(task.getProperty())) || task.getWorkType()==HousekeepingWorkType.TURNDOWN) return;
        Room room=task.getRoom();
        if (room.getHousekeepingStatus()==HousekeepingStatus.OUT_OF_SERVICE) return;
        if (task.getWorkType()==HousekeepingWorkType.CLEAN) {
            if (task.getWorkStatus()==HousekeepingWorkStatus.DONE) {
                boolean pendingInspection=tasks.findByRoom_IdAndServiceDateAndWorkType(room.getId(),task.getServiceDate(),HousekeepingWorkType.INSPECTION)
                        .map(value -> value.getWorkStatus()!=HousekeepingWorkStatus.DONE).orElse(false);
                room.setHousekeepingStatus(pendingInspection ? HousekeepingStatus.INSPECTION : HousekeepingStatus.CLEAN);
            } else {
                room.setHousekeepingStatus(task.getWorkStatus()==HousekeepingWorkStatus.IN_PROGRESS ? HousekeepingStatus.IN_PROGRESS : HousekeepingStatus.DIRTY);
                tasks.findByRoom_IdAndServiceDateAndWorkType(room.getId(),task.getServiceDate(),HousekeepingWorkType.INSPECTION)
                        .filter(inspection -> inspection.getWorkStatus()==HousekeepingWorkStatus.DONE).ifPresent(inspection -> {
                            inspection.setWorkStatus(HousekeepingWorkStatus.OPEN); inspection.setStatus(HousekeepingStatus.INSPECTION);
                            inspection.setCompletedAt(null); tasks.saveAndFlush(inspection); event(inspection,HousekeepingWorkStatus.DONE);
                        });
            }
        } else if (task.getWorkStatus()==HousekeepingWorkStatus.DONE) room.setHousekeepingStatus(HousekeepingStatus.CLEAN);
        else if (room.getHousekeepingStatus()==HousekeepingStatus.CLEAN) room.setHousekeepingStatus(HousekeepingStatus.INSPECTION);
        rooms.save(room);
    }
    private void event(HousekeepingTask task, HousekeepingWorkStatus previous) {
        var authentication=SecurityContextHolder.getContext().getAuthentication();
        String actor=authentication==null ? "SYSTEM" : authentication.getName();
        HousekeepingTaskEvent event=new HousekeepingTaskEvent(); event.setTask(task); event.setFromStatus(previous); event.setToStatus(task.getWorkStatus());
        event.setAssignedTo(task.getAssignedTo()); event.setNotes(task.getNotes()); event.setActor(actor.substring(0,Math.min(120,actor.length()))); event.setCreatedAt(LocalDateTime.now().withNano(0)); events.save(event);
        audit.append(task.getProperty(),"housekeeping.work_updated","housekeeping",task.getId().toString(),
                "{\"workType\":\""+task.getWorkType()+"\",\"status\":\""+task.getWorkStatus()+"\"}");
    }
    private HousekeepingTask task(Company company,Long propertyId,Long taskId) { return tasks.findByIdAndProperty_Company_Id(taskId,company.getId()).filter(value -> value.getProperty().getId().equals(propertyId)).orElseThrow(PmsHousekeepingService::missing); }
    private HotelProperty property(Company company,Long propertyId,boolean write) { return (write ? properties.findByIdAndCompany_IdForUpdate(propertyId,company.getId()) : properties.findByIdAndCompany_Id(propertyId,company.getId())).orElseThrow(PmsHousekeepingService::missing); }
    private LocalDate today(HotelProperty property) { return LocalDate.now(ZoneId.of(property.getTimezone())); }
    private String clean(String value) { return value==null || value.isBlank() ? null : value.trim(); }
    private static ResponseStatusException missing() { return new ResponseStatusException(HttpStatus.NOT_FOUND,"Hotel, Zimmer oder Aufgabe nicht gefunden."); }
    private static ResponseStatusException conflict(String message) { return new ResponseStatusException(HttpStatus.CONFLICT,message); }
    private PmsHousekeepingDtos.Task view(HousekeepingTask task) { return new PmsHousekeepingDtos.Task(task.getId(),task.getVersion(),task.getRoom().getId(),task.getRoom().getNumber(),task.getServiceDate(),task.getWorkType(),task.getWorkStatus(),task.getType(),task.getPriority(),task.getEstimatedMinutes(),task.getNotes(),task.getAssignedTo(),task.getCompletedAt()); }
}
