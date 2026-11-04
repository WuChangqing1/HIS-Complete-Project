package com.example.his;

import com.example.his.api.ApiController;
import com.example.his.api.HisService;
import com.example.his.auth.AuthService;
import com.example.his.catalog.CatalogMapper;
import com.example.his.core.ApiAdvice;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ApiHttpErrorsTest {
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new ApiController(
            mock(AuthService.class), mock(HisService.class), mock(CatalogMapper.class)))
            .setControllerAdvice(new ApiAdvice()).build();

    @Test void invalidRecordIdReturnsBadRequest() throws Exception {
        mvc.perform(get("/api/records/abc")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").isString());
    }

    @Test void unsupportedMethodReturnsAllowHeader() throws Exception {
        mvc.perform(delete("/api/patients")).andExpect(status().isMethodNotAllowed())
                .andExpect(header().exists("Allow")).andExpect(jsonPath("$.error").isString());
    }
}
