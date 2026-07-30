package com.callejon9.ticket;

import com.callejon9.auth.service.JwtService;
import com.callejon9.catalog.domain.Product;
import com.callejon9.catalog.repository.ProductRepository;
import com.callejon9.platform.tenant.domain.Tenant;
import com.callejon9.platform.tenant.service.TenantOnboardingService;
import com.callejon9.table.domain.RestaurantTable;
import com.callejon9.table.domain.TableStatus;
import com.callejon9.table.repository.RestaurantTableRepository;
import com.callejon9.tenancy.TenantContext;
import com.callejon9.user.domain.User;
import com.callejon9.user.domain.UserRole;
import com.callejon9.user.repository.UserRepository;
import jakarta.servlet.http.Cookie;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Tickets")
class TicketControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private TenantOnboardingService onboardingService;
    @Autowired private JwtService jwtService;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private TransactionTemplate transactionTemplate;
    @Autowired private UserRepository userRepository;
    @Autowired private RestaurantTableRepository tableRepository;
    @Autowired private ProductRepository productRepository;

    private Tenant tenant;
    private User waiter;
    private User cashier;
    private RestaurantTable table;
    private Product product;

    @BeforeEach
    void seed() {
        tenant = onboardingService.onboard("Tickets Test", "tickets-test",
                "admin@tickets.com", "Admin", "Secreto123!", "FREE");

        TenantContext.set(tenant.getId());
        try {
            waiter = transactionTemplate.execute(status -> userRepository.save(User.builder()
                    .email("mesero@tickets.com").passwordHash("x").fullName("Mesero")
                    .role(UserRole.WAITER).active(true).build()));
            cashier = transactionTemplate.execute(status -> userRepository.save(User.builder()
                    .email("cajero@tickets.com").passwordHash("x").fullName("Cajero")
                    .role(UserRole.CASHIER).active(true).build()));
            table = transactionTemplate.execute(status -> tableRepository.save(RestaurantTable.builder()
                    .number(1).capacity(4).status(TableStatus.FREE).active(true).build()));
            product = transactionTemplate.execute(status -> productRepository.save(Product.builder()
                    .name("Taco").description("Taco").price(new BigDecimal("25.00")).active(true).build()));
        } finally {
            TenantContext.clear();
        }
    }

    @AfterEach
    void cleanUp() {
        TenantContext.clear();
        jdbcTemplate.update("DELETE FROM tenants WHERE slug = 'tickets-test'");
    }

    private Cookie cookieFor(User user) {
        return new Cookie("access_token", jwtService.generateAccessToken(user));
    }

    private UUID checkoutAndGetTicketId() throws Exception {
        String orderBody = mockMvc.perform(post("/api/v1/orders")
                        .cookie(cookieFor(waiter))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tableId\":\"" + table.getId() + "\",\"guestCount\":2}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID orderId = UUID.fromString(orderBody.replaceAll(".*\"id\":\"([0-9a-fA-F-]+)\".*", "$1"));

        mockMvc.perform(post("/api/v1/orders/" + orderId + "/items")
                        .cookie(cookieFor(waiter))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"productId\":\"" + product.getId() + "\",\"quantity\":1}]}"))
                .andExpect(status().isOk());

        String ticketBody = mockMvc.perform(post("/api/v1/orders/" + orderId + "/checkout")
                        .cookie(cookieFor(cashier))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentMethod\":\"CASH\",\"tipPercent\":10}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(ticketBody.replaceAll(".*\"id\":\"([0-9a-fA-F-]+)\".*", "$1"));
    }

    @Test
    @DisplayName("cualquier usuario autenticado puede consultar un ticket")
    void anyAuthenticatedUserCanReadATicket() throws Exception {
        UUID ticketId = checkoutAndGetTicketId();

        mockMvc.perform(get("/api/v1/tickets/" + ticketId).cookie(cookieFor(waiter)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("un ticket inexistente da 404")
    void unknownTicketIsNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/tickets/" + UUID.randomUUID()).cookie(cookieFor(waiter)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /tickets/{id}/pdf devuelve un documento application/pdf")
    void pdfEndpointReturnsAPdfDocument() throws Exception {
        UUID ticketId = checkoutAndGetTicketId();

        var result = mockMvc.perform(get("/api/v1/tickets/" + ticketId + "/pdf").cookie(cookieFor(waiter)))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .content().contentType(MediaType.APPLICATION_PDF))
                .andReturn();

        byte[] pdf = result.getResponse().getContentAsByteArray();
        assertThat(pdf.length).isGreaterThan(0);
        // Todo PDF valido inicia con la firma %PDF-.
        assertThat(new String(pdf, 0, 5, java.nio.charset.StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
    }
}
