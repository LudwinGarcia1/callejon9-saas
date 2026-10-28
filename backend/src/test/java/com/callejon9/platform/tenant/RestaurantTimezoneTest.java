package com.callejon9.platform.tenant;

import com.callejon9.cashier.service.CashSessionService;
import com.callejon9.catalog.domain.Product;
import com.callejon9.catalog.repository.ProductRepository;
import com.callejon9.platform.tenant.domain.Tenant;
import com.callejon9.platform.tenant.service.TenantOnboardingService;
import com.callejon9.support.CashSessionFixtures;
import com.callejon9.support.TestSessions;
import com.callejon9.table.domain.RestaurantTable;
import com.callejon9.table.domain.TableStatus;
import com.callejon9.table.repository.RestaurantTableRepository;
import com.callejon9.tenancy.TenantContext;
import com.callejon9.user.domain.User;
import com.callejon9.user.domain.UserRole;
import com.callejon9.user.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Zona horaria por restaurante (CAL-19), contra PostgreSQL real: dos
 * restaurantes que cortan el dia en medianoches distintas, horario de
 * verano en la agregacion SQL, validacion de la zona y aislamiento de la
 * configuracion entre tenants.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Zona horaria por restaurante")
class RestaurantTimezoneTest {

    private static final ZoneId CDMX = ZoneId.of("America/Mexico_City");
    private static final ZoneId TIJUANA = ZoneId.of("America/Tijuana");

    @Autowired private MockMvc mockMvc;
    @Autowired private TenantOnboardingService onboardingService;
    @Autowired private TestSessions testSessions;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private TransactionTemplate transactionTemplate;
    @Autowired private UserRepository userRepository;
    @Autowired private RestaurantTableRepository tableRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private CashSessionService cashSessionService;

    /** Un restaurante con lo minimo para cobrar: mesero, cajero con caja abierta, ADMIN, mesa y producto. */
    private record Restaurant(Tenant tenant, User admin, User waiter, User cashier, User kitchen,
            RestaurantTable table, Product product) {
    }

    private Restaurant cdmx;
    private Restaurant cancun;

    @BeforeEach
    void seed() {
        cdmx = seedRestaurant("zona-cdmx");
        cancun = seedRestaurant("zona-cancun");
    }

    @AfterEach
    void cleanUp() {
        TenantContext.clear();
        jdbcTemplate.update("DELETE FROM tenants WHERE slug IN ('zona-cdmx','zona-cancun')");
    }

    private Restaurant seedRestaurant(String slug) {
        Tenant tenant = onboardingService.onboard("Restaurante " + slug, slug,
                "admin@" + slug + ".com", "Admin", "Secreto123!", "FREE");
        TenantContext.set(tenant.getId());
        try {
            User admin = saveUser(slug, "gerente", UserRole.ADMIN);
            User waiter = saveUser(slug, "mesero", UserRole.WAITER);
            User cashier = saveUser(slug, "cajero", UserRole.CASHIER);
            User kitchen = saveUser(slug, "cocina", UserRole.KITCHEN);
            RestaurantTable table = transactionTemplate.execute(status -> tableRepository.save(
                    RestaurantTable.builder().number(1).capacity(4).status(TableStatus.FREE).active(true).build()));
            Product product = transactionTemplate.execute(status -> productRepository.save(Product.builder()
                    .name("Taco").description("Taco").price(new BigDecimal("25.00")).active(true).build()));
            CashSessionFixtures.openFor(cashSessionService, cashier);
            return new Restaurant(tenant, admin, waiter, cashier, kitchen, table, product);
        } finally {
            TenantContext.clear();
        }
    }

    private User saveUser(String slug, String prefix, UserRole role) {
        return transactionTemplate.execute(status -> userRepository.save(User.builder()
                .email(prefix + "@" + slug + ".com").passwordHash("x").fullName(prefix)
                .role(role).active(true).build()));
    }

    private Cookie cookieFor(User user) {
        return new Cookie("access_token", testSessions.accessTokenFor(user));
    }

    /** Cobra una orden de un taco y fija el instante de la venta. */
    private UUID saleAt(Restaurant restaurant, Instant createdAt) throws Exception {
        String orderBody = mockMvc.perform(post("/api/v1/orders")
                        .cookie(cookieFor(restaurant.waiter()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tableId\":\"" + restaurant.table().getId() + "\",\"guestCount\":2}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID orderId = UUID.fromString(objectMapper.readTree(orderBody).get("id").asText());

        mockMvc.perform(post("/api/v1/orders/" + orderId + "/items")
                        .cookie(cookieFor(restaurant.waiter()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"productId\":\"" + restaurant.product().getId() + "\",\"quantity\":1}]}"))
                .andExpect(status().isOk());

        String ticketBody = mockMvc.perform(post("/api/v1/orders/" + orderId + "/checkout")
                        .cookie(cookieFor(restaurant.cashier()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentMethod\":\"CASH\",\"tipPercent\":0}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID saleId = UUID.fromString(objectMapper.readTree(ticketBody).get("saleId").asText());

        // RLS solo revela la fila con el tenant fijado en una transaccion real.
        TenantContext.set(restaurant.tenant().getId());
        try {
            transactionTemplate.executeWithoutResult(status -> jdbcTemplate.update(
                    "UPDATE sales SET created_at = ? WHERE id = ?", java.sql.Timestamp.from(createdAt), saleId));
        } finally {
            TenantContext.clear();
        }
        return saleId;
    }

    private void setTimezone(Restaurant restaurant, String timezone) throws Exception {
        mockMvc.perform(patch("/api/v1/settings")
                        .cookie(cookieFor(restaurant.admin()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"timezone\":\"" + timezone + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.timezone").value(timezone));
    }

    private String storedTimezone(Restaurant restaurant) {
        return jdbcTemplate.queryForObject("SELECT timezone FROM tenants WHERE id = ?", String.class,
                restaurant.tenant().getId());
    }

    private JsonNode salesOn(Restaurant restaurant, LocalDate day) throws Exception {
        String body = mockMvc.perform(get("/api/v1/sales")
                        .param("from", day.toString())
                        .param("to", day.toString())
                        .cookie(cookieFor(restaurant.cashier())))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("sales");
    }

    @Nested
    @DisplayName("Configuracion")
    class Settings {

        @Test
        @DisplayName("un restaurante nuevo arranca en America/Mexico_City y la sesion la expone")
        void newRestaurantDefaultsToMexicoCityAndSessionExposesIt() throws Exception {
            mockMvc.perform(get("/api/v1/settings").cookie(cookieFor(cdmx.admin())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.slug").value("zona-cdmx"))
                    .andExpect(jsonPath("$.timezone").value("America/Mexico_City"));

            mockMvc.perform(get("/api/v1/auth/me").cookie(cookieFor(cdmx.waiter())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.timezone").value("America/Mexico_City"));
        }

        @Test
        @DisplayName("el ADMIN cambia la zona y la sesion refleja el valor nuevo")
        void adminChangesTimezone() throws Exception {
            setTimezone(cancun, "America/Cancun");

            assertThat(storedTimezone(cancun)).isEqualTo("America/Cancun");
            mockMvc.perform(get("/api/v1/auth/me").cookie(cookieFor(cancun.cashier())))
                    .andExpect(jsonPath("$.timezone").value("America/Cancun"));
        }

        @ParameterizedTest
        @ValueSource(strings = {"Marte/Olympus", "-05:00", "UTC", "Etc/GMT+5", "EST", "Mexico/General", ""})
        @DisplayName("una zona invalida se rechaza con 400 y la base conserva el valor previo")
        void invalidTimezoneIsRejectedAndPreviousValueKept(String timezone) throws Exception {
            setTimezone(cdmx, "America/Cancun");

            mockMvc.perform(patch("/api/v1/settings")
                            .cookie(cookieFor(cdmx.admin()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"timezone\":\"" + timezone + "\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.timezone").exists());

            assertThat(storedTimezone(cdmx)).isEqualTo("America/Cancun");
        }

        @Test
        @DisplayName("la base rechaza una zona inexistente aunque se salte la API, y conserva la previa")
        void databaseRejectsUnknownTimezone() {
            assertThatThrownBy(() -> jdbcTemplate.update(
                    "UPDATE tenants SET timezone = 'Marte/Olympus' WHERE id = ?", cdmx.tenant().getId()))
                    .isInstanceOf(DataIntegrityViolationException.class);

            assertThat(storedTimezone(cdmx)).isEqualTo("America/Mexico_City");
        }

        @ParameterizedTest
        @EnumSource(value = UserRole.class, names = {"WAITER", "CASHIER", "KITCHEN"})
        @DisplayName("solo el ADMIN lee o cambia la configuracion")
        void nonAdminRolesAreForbidden(UserRole role) throws Exception {
            User user = switch (role) {
                case WAITER -> cdmx.waiter();
                case CASHIER -> cdmx.cashier();
                default -> cdmx.kitchen();
            };

            mockMvc.perform(get("/api/v1/settings").cookie(cookieFor(user)))
                    .andExpect(status().isForbidden());
            mockMvc.perform(patch("/api/v1/settings")
                            .cookie(cookieFor(user))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"timezone\":\"America/Cancun\"}"))
                    .andExpect(status().isForbidden());

            assertThat(storedTimezone(cdmx)).isEqualTo("America/Mexico_City");
        }

        @Test
        @DisplayName("el ADMIN de otro restaurante solo ve y cambia su propia configuracion")
        void anotherTenantAdminOnlyTouchesItsOwnSettings() throws Exception {
            setTimezone(cancun, "America/Cancun");

            mockMvc.perform(get("/api/v1/settings").cookie(cookieFor(cancun.admin())))
                    .andExpect(jsonPath("$.slug").value("zona-cancun"))
                    .andExpect(jsonPath("$.timezone").value("America/Cancun"));
            assertThat(storedTimezone(cdmx)).isEqualTo("America/Mexico_City");
        }
    }

    @Nested
    @DisplayName("Corte de dia")
    class DayCutoff {

        @Test
        @DisplayName("con una venta en la frontera, CDMX y Cancun la agrupan cada uno en su medianoche")
        void eachRestaurantGroupsByItsOwnMidnight() throws Exception {
            setTimezone(cancun, "America/Cancun");
            LocalDate day = LocalDate.now(CDMX).minusDays(10);
            // 23:30 en CDMX son las 00:30 del dia siguiente en Cancun.
            Instant boundary = day.atTime(23, 30).atZone(CDMX).toInstant();
            UUID cdmxSale = saleAt(cdmx, boundary);
            UUID cancunSale = saleAt(cancun, boundary);

            assertThat(salesOn(cdmx, day)).extracting(sale -> sale.get("id").asText())
                    .containsExactly(cdmxSale.toString());
            assertThat(salesOn(cdmx, day.plusDays(1))).isEmpty();
            assertThat(salesOn(cancun, day)).isEmpty();
            assertThat(salesOn(cancun, day.plusDays(1))).extracting(sale -> sale.get("id").asText())
                    .containsExactly(cancunSale.toString());

            // La agregacion SQL (AT TIME ZONE) usa la misma zona que el rango.
            mockMvc.perform(get("/api/v1/analytics")
                            .param("from", day.plusDays(1).toString())
                            .param("to", day.plusDays(1).toString())
                            .cookie(cookieFor(cancun.admin())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.salesByDay.length()").value(1))
                    .andExpect(jsonPath("$.salesByDay[0].day").value(day.plusDays(1).toString()))
                    .andExpect(jsonPath("$.salesByDay[0].count").value(1));
        }

        @Test
        @DisplayName("al cambiar la zona, el historial se reinterpreta sin reescribir los instantes")
        void changingTimezoneReinterpretsHistoryWithoutRewritingTimestamps() throws Exception {
            LocalDate day = LocalDate.now(CDMX).minusDays(10);
            Instant boundary = day.atTime(23, 30).atZone(CDMX).toInstant();
            UUID saleId = saleAt(cdmx, boundary);
            assertThat(salesOn(cdmx, day)).hasSize(1);

            setTimezone(cdmx, "America/Cancun");

            assertThat(salesOn(cdmx, day)).isEmpty();
            assertThat(salesOn(cdmx, day.plusDays(1))).extracting(sale -> sale.get("id").asText())
                    .containsExactly(saleId.toString());
            TenantContext.set(cdmx.tenant().getId());
            try {
                Instant stored = transactionTemplate.execute(status -> jdbcTemplate.queryForObject(
                        "SELECT created_at FROM sales WHERE id = ?", java.sql.Timestamp.class, saleId)).toInstant();
                assertThat(stored).isEqualTo(boundary);
            } finally {
                TenantContext.clear();
            }
        }

        @Test
        @DisplayName("el dia de 25 horas del cambio de horario agrupa sus dos extremos en el mismo dia")
        void daylightSavingFallBackDayKeepsBothEndsInTheSameDay() throws Exception {
            setTimezone(cdmx, "America/Tijuana");
            LocalDate fallBack = LocalDate.of(2025, 11, 2);
            // 00:30 todavia en horario de verano (-07:00); 23:30 ya en estandar (-08:00).
            saleAt(cdmx, fallBack.atTime(0, 30).atZone(TIJUANA).toInstant());
            saleAt(cdmx, fallBack.atTime(23, 30).atZone(TIJUANA).toInstant());
            saleAt(cdmx, fallBack.plusDays(1).atTime(0, 30).atZone(TIJUANA).toInstant());

            assertThat(salesOn(cdmx, fallBack)).hasSize(2);
            mockMvc.perform(get("/api/v1/analytics")
                            .param("from", fallBack.toString())
                            .param("to", fallBack.plusDays(1).toString())
                            .cookie(cookieFor(cdmx.admin())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.salesByDay[0].day").value(fallBack.toString()))
                    .andExpect(jsonPath("$.salesByDay[0].count").value(2))
                    .andExpect(jsonPath("$.salesByDay[1].day").value(fallBack.plusDays(1).toString()))
                    .andExpect(jsonPath("$.salesByDay[1].count").value(1));
        }
    }
}
