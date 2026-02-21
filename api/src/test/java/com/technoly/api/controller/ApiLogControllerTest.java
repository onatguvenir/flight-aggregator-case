package com.technoly.api.controller;

import com.technoly.application.service.ApiLogService;
import com.technoly.infrastructure.persistence.entity.ApiLogEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ApiLogController.class)
class ApiLogControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ApiLogService apiLogService;

    @Test
    void getLogs_ShouldReturnAllLogs_WhenNoEndpointFilterProvided() throws Exception {
        // Arrange
        ApiLogEntity log1 = ApiLogEntity.builder().endpoint("/api/v1/flights/search").statusCode(200).build();
        ApiLogEntity log2 = ApiLogEntity.builder().endpoint("/api/v1/flights/search/cheapest").statusCode(200).build();

        when(apiLogService.getAllLogs()).thenReturn(List.of(log1, log2));

        // Act & Assert
        mockMvc.perform(get("/api/v1/logs")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size()").value(2))
                .andExpect(jsonPath("$[0].endpoint").value("/api/v1/flights/search"))
                .andExpect(jsonPath("$[1].endpoint").value("/api/v1/flights/search/cheapest"));

        verify(apiLogService).getAllLogs();
        verify(apiLogService, never()).getLogsByEndpoint(anyString());
    }

    @Test
    void getLogs_ShouldReturnFilteredLogs_WhenEndpointFilterProvided() throws Exception {
        // Arrange
        String endpoint = "/api/v1/flights/search";
        ApiLogEntity log1 = ApiLogEntity.builder().endpoint(endpoint).statusCode(200).build();

        when(apiLogService.getLogsByEndpoint(endpoint)).thenReturn(List.of(log1));

        // Act & Assert
        mockMvc.perform(get("/api/v1/logs")
                .param("endpoint", endpoint)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size()").value(1))
                .andExpect(jsonPath("$[0].endpoint").value(endpoint));

        verify(apiLogService).getLogsByEndpoint(endpoint);
        verify(apiLogService, never()).getAllLogs();
    }
}
