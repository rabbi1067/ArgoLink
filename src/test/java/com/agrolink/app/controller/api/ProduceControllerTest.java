package com.agrolink.app.controller.api;

import com.agrolink.app.dto.ProduceListingDTO;
import com.agrolink.app.dto.ProduceListingRequest;
import com.agrolink.app.dto.ProduceListingResponse;
import com.agrolink.app.exception.GlobalExceptionHandler;
import com.agrolink.app.model.Role;
import com.agrolink.app.model.User;
import com.agrolink.app.service.ProduceService;
import com.agrolink.app.service.PublicCatalogService;
import com.agrolink.app.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProduceController.class)
@Import({GlobalExceptionHandler.class, ProduceController.class, ProduceControllerTest.MethodSecurityConfig.class})
class ProduceControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProduceService produceService;
    @MockitoBean
    private PublicCatalogService catalogService;
    @MockitoBean
    private UserService userService;

    @Configuration
    @EnableWebSecurity
    @EnableMethodSecurity
    static class MethodSecurityConfig {

        @Bean
        public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
            http.csrf(csrf -> csrf.disable())
                    .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
            return http.build();
        }

        @Bean
        public MongoMappingContext mongoMappingContext() {
            return new MongoMappingContext();
        }
    }

    @Test
    void search_shouldBePubliclyAccessible() throws Exception {
        ProduceListingDTO listing = dto("Wheat");
        when(produceService.searchActive(null, null)).thenReturn(List.of(listing));

        mockMvc.perform(get("/api/v1/produce/listings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].cropName").value("Wheat"));
    }

    @Test
    @WithMockUser(username = "farmer@agrolink.com", roles = "FARMER")
    void create_shouldAllowFarmer() throws Exception {
        User farmer = User.builder().id("farmer-1").email("farmer@agrolink.com")
                .role(Role.FARMER).name("Amar Patel").build();
        when(userService.getUserByEmail("farmer@agrolink.com")).thenReturn(farmer);
        when(produceService.create(eq("farmer-1"), any(ProduceListingRequest.class)))
                .thenReturn(response("Wheat"));

        mockMvc.perform(post("/api/v1/produce")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "cropName": "Wheat",
                                  "category": "Grains",
                                  "availableQuantity": 1000.00,
                                  "unit": "kg",
                                  "pricePerUnit": 22.50,
                                  "district": "Punjab",
                                  "location": "Farm gate, Punjab"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.cropName").value("Wheat"));
    }

    @Test
    @WithMockUser(username = "buyer@agrolink.com", roles = "BUYER")
    void create_shouldDenyBuyer() throws Exception {
        mockMvc.perform(post("/api/v1/produce")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "cropName": "Wheat",
                                  "category": "Grains",
                                  "availableQuantity": 1000.00,
                                  "unit": "kg",
                                  "pricePerUnit": 22.50,
                                  "district": "Punjab",
                                  "location": "Farm gate, Punjab"
                                }
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void create_shouldDenyAnonymous() throws Exception {
        mockMvc.perform(post("/api/v1/produce")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "cropName": "Wheat",
                                  "category": "Grains",
                                  "availableQuantity": 1000.00,
                                  "unit": "kg",
                                  "pricePerUnit": 22.50,
                                  "district": "Punjab",
                                  "location": "Farm gate, Punjab"
                                }
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "farmer@agrolink.com", roles = "FARMER")
    void create_shouldRejectInvalidBody() throws Exception {
        mockMvc.perform(post("/api/v1/produce")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "category": "Grains",
                                  "availableQuantity": 1000.00,
                                  "unit": "kg",
                                  "pricePerUnit": 22.50,
                                  "district": "Punjab",
                                  "location": "Farm gate, Punjab"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    private ProduceListingDTO dto(String cropName) {
        return new ProduceListingDTO(
                "listing-1", "farmer-1", cropName, "Grains",
                new BigDecimal("1000"), new BigDecimal("0"),
                "kg", new BigDecimal("22.50"), "Punjab",
                null, null, null, null, com.agrolink.app.model.ListingStatus.ACTIVE, 0L, null);
    }

    private ProduceListingResponse response(String cropName) {
        return new ProduceListingResponse(
                "listing-1", "farmer-1", "Amar Patel", cropName, "Grains", null,
                new BigDecimal("1000"), new BigDecimal("0"), new BigDecimal("1000"),
                "kg", new BigDecimal("22.50"), null, "Punjab", null, null, null,
                com.agrolink.app.model.ListingStatus.ACTIVE, null);
    }
}