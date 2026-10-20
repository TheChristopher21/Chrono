package com.chrono.chrono.controller.pms;

import com.chrono.chrono.config.PmsAccessPolicy;
import com.chrono.chrono.config.PmsAccessRequestAdvice;
import com.chrono.chrono.services.pms.PmsPropertyAccessService;
import com.chrono.chrono.services.pms.PmsOperationsService;
import com.chrono.chrono.services.UserPermissionService;
import com.chrono.chrono.repositories.UserRepository;
import com.chrono.chrono.entities.Company;
import com.chrono.chrono.entities.User;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.HandlerInterceptor;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PmsHotelAuthorizationMvcTest {
    @RestController static class Endpoint {
        AtomicInteger writes = new AtomicInteger();
        record Booking(Long propertyId) {}
        @PostMapping("/api/pms/reservations") Map<String, Integer> create(@RequestBody Booking input) {
            return Map.of("writes", writes.incrementAndGet());
        }
    }
    @Test void typedBodyAuthorizationRunsBeforeMutationAndRejectsUnassignedHotel() throws Exception {
        var access = new PmsPropertyAccessService.Access(7L, 10L, false, Map.of(5L, Map.of("FRONT_DESK", "MANAGE")));
        var service = mock(PmsPropertyAccessService.class);
        when(service.access("staff")).thenReturn(access);
        doAnswer(call -> {
            if (!access.allows(call.getArgument(1), call.getArgument(2), call.getArgument(3))) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
            return null;
        }).when(service).require(eq(access), anyLong(), anyString(), anyBoolean());
        var policy = new PmsAccessPolicy(service, mock(EntityManager.class), new ObjectMapper());
        var endpoint = new Endpoint();
        var mvc = MockMvcBuilders.standaloneSetup(endpoint)
                .setControllerAdvice(new PmsAccessRequestAdvice(policy))
                .addInterceptors(new HandlerInterceptor() {
                    @Override public boolean preHandle(jakarta.servlet.http.HttpServletRequest request, jakarta.servlet.http.HttpServletResponse response, Object handler) {
                        policy.authorize(request, null); return true;
                    }
                }).build();
        mvc.perform(post("/api/pms/reservations").principal(() -> "staff").contentType("application/json").content("{\"propertyId\":6}"))
                .andExpect(status().isForbidden());
        assertThat(endpoint.writes).hasValue(0);
        mvc.perform(post("/api/pms/reservations").principal(() -> "staff").contentType("application/json").content("{\"propertyId\":5}"))
                .andExpect(status().isOk());
        assertThat(endpoint.writes).hasValue(1);
    }
    @Test void actualRateControllerAllowsAssignedRateManagerAndRejectsReadOnlyOrOtherHotel() throws Exception {
        var actor = new AtomicReference<>(new PmsPropertyAccessService.Access(7L, 10L, false, Map.of(5L, Map.of("RATES", "MANAGE"))));
        var grants = mock(PmsPropertyAccessService.class);
        when(grants.access("staff")).thenAnswer(call -> actor.get());
        doAnswer(call -> {
            PmsPropertyAccessService.Access current = call.getArgument(0);
            if (!current.allows(call.getArgument(1), call.getArgument(2), call.getArgument(3))) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
            return null;
        }).when(grants).require(any(), anyLong(), anyString(), anyBoolean());
        var permissions = mock(UserPermissionService.class);
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "No master access"))
                .when(permissions).assertPageAccess(any(), eq(UserPermissionService.PAGE_PMS_SETTINGS), anyString(), anyString());
        var company = new Company(); company.setId(10L);
        var user = new User(); user.setId(7L); user.setUsername("staff"); user.setCompany(company);
        var users = mock(UserRepository.class); when(users.findByUsernameWithPermissionContext("staff")).thenReturn(Optional.of(user));
        var operations = mock(PmsOperationsService.class);
        var policy = new PmsAccessPolicy(grants, mock(EntityManager.class), new ObjectMapper().findAndRegisterModules());
        var mvc = MockMvcBuilders.standaloneSetup(new PmsOperationsController(operations, users, permissions))
                .setControllerAdvice(new PmsAccessRequestAdvice(policy))
                .addInterceptors(new HandlerInterceptor() {
                    @Override public boolean preHandle(jakarta.servlet.http.HttpServletRequest request, jakarta.servlet.http.HttpServletResponse response, Object handler) {
                        policy.authorize(request, null); return true;
                    }
                }).build();
        String rate = "{\"roomTypeId\":2,\"code\":\"BAR\",\"name\":\"Standard\",\"nightlyRate\":100,\"minStay\":1}";
        String override = "{\"stayDate\":\"2030-01-01\",\"price\":120,\"minStay\":1}";
        mvc.perform(post("/api/pms/properties/5/rate-plans").principal(() -> "staff").contentType("application/json").content(rate)).andExpect(status().isCreated());
        mvc.perform(put("/api/pms/properties/5/rate-plans/3").principal(() -> "staff").contentType("application/json").content(rate)).andExpect(status().isOk());
        mvc.perform(put("/api/pms/properties/5/rate-plans/3/override").principal(() -> "staff").contentType("application/json").content(override)).andExpect(status().isOk());
        actor.set(new PmsPropertyAccessService.Access(7L, 10L, false, Map.of(5L, Map.of("RATES", "VIEW"))));
        mvc.perform(post("/api/pms/properties/5/rate-plans").principal(() -> "staff").contentType("application/json").content(rate)).andExpect(status().isForbidden());
        mvc.perform(put("/api/pms/properties/5/rate-plans/3").principal(() -> "staff").contentType("application/json").content(rate)).andExpect(status().isForbidden());
        mvc.perform(put("/api/pms/properties/5/rate-plans/3/override").principal(() -> "staff").contentType("application/json").content(override)).andExpect(status().isForbidden());
        actor.set(new PmsPropertyAccessService.Access(7L, 10L, false, Map.of(6L, Map.of("RATES", "MANAGE"))));
        mvc.perform(post("/api/pms/properties/5/rate-plans").principal(() -> "staff").contentType("application/json").content(rate)).andExpect(status().isForbidden());
        mvc.perform(put("/api/pms/properties/5/rate-plans/3").principal(() -> "staff").contentType("application/json").content(rate)).andExpect(status().isForbidden());
        mvc.perform(put("/api/pms/properties/5/rate-plans/3/override").principal(() -> "staff").contentType("application/json").content(override)).andExpect(status().isForbidden());
        verify(operations).createRatePlan(eq(company), eq(5L), any(), isNull());
        verify(operations).updateRatePlan(eq(company), eq(5L), eq(3L), any(), isNull());
        verify(operations).upsertRateOverride(eq(company), eq(5L), eq(3L), any(), isNull());
        verifyNoMoreInteractions(operations);
        verify(permissions, never()).assertPageAccess(any(), eq(UserPermissionService.PAGE_PMS_SETTINGS), anyString(), anyString());
    }
}
