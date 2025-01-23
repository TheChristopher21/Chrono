package com.chrono.chrono.controller;

import com.chrono.chrono.services.TimeTrackingService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TimeTrackingController.class)
public class TimeTrackingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TimeTrackingService timeTrackingService;

    @Test
    public void testGetAllTimeEntries() throws Exception {
        mockMvc.perform(get("/api/timetracking/all"))
                .andExpect(status().isOk());
    }
}
