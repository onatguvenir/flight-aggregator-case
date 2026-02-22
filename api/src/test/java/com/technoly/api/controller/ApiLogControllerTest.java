package com.technoly.api.controller;

import com.technoly.application.service.ApiLogService;
import com.technoly.infrastructure.persistence.entity.ApiLogEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ApiLogController.class)
@TestPropertySource(properties = {
        "security.enabled=false",
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=",
        "rate-limit.capacity=60",
        "rate-limit.refill-per-minute=60"
})
class ApiLogControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ApiLogService apiLogService;

    @Test
    @WithMockUser
    void getLogs_ShouldReturnPagedLogs_WhenNoEndpointFilterProvided() throws Exception {
        // Arrange
        ApiLogEntity log1 = ApiLogEntity.builder().endpoint("/api/v1/flights/search").statusCode(200).build();
        ApiLogEntity log2 = ApiLogEntity.builder().endpoint("/api/v1/flights/search/cheapest").statusCode(200).build();
        Page<ApiLogEntity> page = new PageImpl<>(List.of(log1, log2), PageRequest.of(0, 20), 2);

        when(apiLogService.getAllLogs(any(Pageable.class))).thenReturn(page);

        // Act & Assert
        mockMvc.perform(get("/api/v1/logs")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].endpoint").value("/api/v1/flights/search"))
                .andExpect(jsonPath("$.totalElements").value(2));

        verify(apiLogService).getAllLogs(any(Pageable.class));
        verify(apiLogService, never()).getLogsByEndpoint(anyString(), any(Pageable.class));
    }

    @Test
    @WithMockUser
    void getLogs_ShouldReturnFilteredLogs_WhenEndpointFilterProvided() throws Exception {
        // Arrange
        String endpoint = "/api/v1/flights/search";
        ApiLogEntity log1 = ApiLogEntity.builder().endpoint(endpoint).statusCode(200).build();
        Page<ApiLogEntity> page = new PageImpl<>(List.of(log1), PageRequest.of(0, 20), 1);

        when(apiLogService.getLogsByEndpoint(eq(endpoint), any(Pageable.class))).thenReturn(page);

        // Act & Assert
        mockMvc.perform(get("/api/v1/logs")
                .param("endpoint", endpoint)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].endpoint").value(endpoint));

        verify(apiLogService).getLogsByEndpoint(eq(endpoint), any(Pageable.class));
        verify(apiLogService, never()).getAllLogs(any(Pageable.class));
    }
}
