# Inventario bloque 1 — Plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Dar de alta insumos y registrar sus movimientos manuales (entradas, salidas, mermas, ajustes por conteo) con alerta de mínimo, en backend y frontend.

**Architecture:** Paquete nuevo `com.callejon9.inventory` con la estructura de `catalog` (`domain`, `repository`, `service`, `web`, `web.dto`). Dos servicios: `InventoryItemService` para el catálogo e `InventoryMovementService` para el ledger. La aritmética del stock vive en la entidad `InventoryItem`; el signo de cada tipo vive en el enum `InventoryMovementType`. En frontend, sección propia `/inventory` con dos pestañas.

**Tech Stack:** Java 21, Spring Boot, JPA/Hibernate, Flyway, PostgreSQL con RLS, JUnit 5 + MockMvc + AssertJ. Next.js App Router, TanStack Query, shadcn/ui, Tailwind.

## Global Constraints

- **Código en inglés, documentación y textos de UI en español.** Los mensajes de error en Java se escriben **sin acentos** (`"El insumo no existe."`), igual que el resto del proyecto.
- **Ningún repositorio filtra por `tenant_id`.** Las políticas RLS de PostgreSQL delimitan las filas. `TenantScopedEntity` asigna el tenant en su `@PrePersist`.
- **`spring.jpa.hibernate.ddl-auto: validate`** en el perfil de test: cualquier desajuste entre entidad y esquema tumba el contexto de Spring completo. La migración va antes que la entidad.
- **Las llaves foráneas viajan como `UUID` planos**, sin `@ManyToOne`, igual que `Product.categoryId` y `Sale.cashierId`.
- **El `userId` de un movimiento nunca viene en el cuerpo:** sale de `authentication.getPrincipal()`, como en `CheckoutController`.
- **Comparaciones de `BigDecimal` en pruebas con `isEqualByComparingTo`**, nunca `isEqualTo`: las columnas son `numeric(12,3)` y `8` no es `8.000` para `equals`.
- **Prelude de entorno para las pruebas** (PowerShell, una vez por sesión, desde la raíz del repo):

```powershell
$env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot'
$env:DB_APP_PASSWORD = 'app_dev_pwd'
$env:DB_OWNER_PASSWORD = 'owner_dev_pwd'
$env:JWT_SECRET = 'dev-only-secret-change-me-min-32-bytes-long!!'
```

Las pruebas necesitan PostgreSQL escuchando en el puerto 5433 con la base `callejon9_test`. Todos los comandos `mvnw` se ejecutan desde `backend/`, todos los `npm` desde `frontend/`.

---

## Mapa de archivos

**Backend — se crean:**

| Archivo | Responsabilidad |
|---|---|
| `src/main/resources/db/migration/V7__inventory_item_active.sql` | La única columna nueva |
| `inventory/domain/InventoryMovementType.java` | Los cuatro tipos y el signo de cada uno |
| `inventory/domain/StockLevel.java` | `OK`, `LOW`, `NEGATIVE` |
| `inventory/domain/InventoryItem.java` | Entidad; aritmética del stock y nivel derivado |
| `inventory/domain/InventoryMovement.java` | Entidad del ledger |
| `inventory/repository/InventoryItemRepository.java` | Finders del catálogo y el lock pesimista |
| `inventory/repository/InventoryMovementRepository.java` | Proyección del listado y existencia por insumo |
| `inventory/service/InventoryItemService.java` | Reglas del catálogo |
| `inventory/service/InventoryMovementService.java` | Reglas del ledger |
| `inventory/web/InventoryItemController.java` | HTTP del catálogo |
| `inventory/web/InventoryMovementController.java` | HTTP del ledger |
| `inventory/web/dto/*.java` | 4 peticiones, 2 respuestas, 1 anotación, 1 validador |

**Backend — se modifican:** ninguno. El módulo no toca código existente.

**Frontend — se crean:** `app/(authenticated)/inventory/{page,inventory-view,create-item-dialog,edit-item-dialog,register-movement-dialog}.tsx`

**Frontend — se modifican:** `lib/types.ts`, `lib/endpoints.ts`, `lib/query-keys.ts`, `components/layout/app-sidebar.tsx`, `components/shared/status-badge.tsx`

**Docs — se modifican:** `README.md:70` (conteo de pruebas y rutas), `docs/glosario-es-en.md` (tabla de entidades)

---

## Orden de las tareas y por qué

Las tareas 2 y 3 se implementan sin las dos dependencias cruzadas entre servicios; la tarea 4 las cierra. Eso evita un orden circular: `InventoryItemService.createItem` necesita `InventoryMovementService`, que necesita insumos ya creables.

La tarea 3 usa `findById`. **La tarea 5 introduce el lock pesimista con una prueba que falla sin él** — es la única forma de que esa prueba demuestre algo. Es el mismo camino que siguió `RestaurantTableRepository.findByIdForUpdate` en el commit `30b7631`.

| Tarea | Entregable independiente |
|---|---|
| 1 | Migración + dominio + aritmética probada |
| 2 | Catálogo de insumos operable por HTTP |
| 3 | Movimientos registrables, con las cuatro semánticas |
| 4 | Stock inicial y unidad bloqueada (los dos cruces entre servicios) |
| 5 | El lock pesimista, con su prueba de carrera |
| 6 | Andamiaje de frontend: tipos, rutas, claves, navegación, insignias |
| 7 | Pestaña *Insumos* con alta y edición |
| 8 | Pestaña *Movimientos* y diálogo de registro |
| 9 | Documentación y verificación final |

---

### Task 1: Migración y dominio

**Files:**
- Create: `backend/src/main/resources/db/migration/V7__inventory_item_active.sql`
- Create: `backend/src/main/java/com/callejon9/inventory/domain/InventoryMovementType.java`
- Create: `backend/src/main/java/com/callejon9/inventory/domain/StockLevel.java`
- Create: `backend/src/main/java/com/callejon9/inventory/domain/InventoryItem.java`
- Create: `backend/src/main/java/com/callejon9/inventory/domain/InventoryMovement.java`
- Test: `backend/src/test/java/com/callejon9/inventory/InventoryItemTest.java`

**Interfaces:**
- Consumes: `TenantScopedEntity` (asigna `tenantId` en `@PrePersist`), `BaseEntity` (id, createdAt, updatedAt).
- Produces: `InventoryMovementType.signedEffect(BigDecimal): BigDecimal`; `InventoryItem.apply(InventoryMovementType, BigDecimal): void`; `InventoryItem.level(): StockLevel`; builders Lombok `InventoryItem.builder()` e `InventoryMovement.builder()`.

- [ ] **Step 1: Escribir la prueba unitaria que falla**

Crear `backend/src/test/java/com/callejon9/inventory/InventoryItemTest.java`:

```java
package com.callejon9.inventory;

import com.callejon9.inventory.domain.InventoryItem;
import com.callejon9.inventory.domain.InventoryMovementType;
import com.callejon9.inventory.domain.StockLevel;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Aritmetica del stock y nivel derivado: comportamiento inherente de la
 * entidad, sin Spring ni base de datos.
 */
@DisplayName("InventoryItem")
class InventoryItemTest {

    private InventoryItem itemWith(String stock, String minStock) {
        return InventoryItem.builder()
                .name("Cebolla")
                .unit("kg")
                .stock(new BigDecimal(stock))
                .minStock(new BigDecimal(minStock))
                .unitCost(BigDecimal.ZERO)
                .active(true)
                .build();
    }

    @Test
    @DisplayName("una entrada suma al stock")
    void anEntryAddsToStock() {
        InventoryItem item = itemWith("10.000", "0");

        item.apply(InventoryMovementType.IN, new BigDecimal("5.500"));

        assertThat(item.getStock()).isEqualByComparingTo("15.500");
    }

    @Test
    @DisplayName("una salida y una merma restan del stock")
    void anExitAndAWasteSubtractFromStock() {
        InventoryItem item = itemWith("10.000", "0");

        item.apply(InventoryMovementType.OUT, new BigDecimal("2.000"));
        item.apply(InventoryMovementType.WASTE, new BigDecimal("1.500"));

        assertThat(item.getStock()).isEqualByComparingTo("6.500");
    }

    @Test
    @DisplayName("un ajuste suma la diferencia con signo, positiva o negativa")
    void anAdjustmentAddsTheSignedDelta() {
        InventoryItem item = itemWith("11.000", "0");

        item.apply(InventoryMovementType.ADJUSTMENT, new BigDecimal("-3.000"));
        assertThat(item.getStock()).isEqualByComparingTo("8.000");

        item.apply(InventoryMovementType.ADJUSTMENT, new BigDecimal("2.000"));
        assertThat(item.getStock()).isEqualByComparingTo("10.000");
    }

    @Test
    @DisplayName("el stock puede quedar negativo: apply no lanza ni topa en cero")
    void stockIsAllowedToGoNegative() {
        InventoryItem item = itemWith("1.000", "0");

        item.apply(InventoryMovementType.OUT, new BigDecimal("4.000"));

        assertThat(item.getStock()).isEqualByComparingTo("-3.000");
    }

    @Test
    @DisplayName("stock negativo es NEGATIVE incluso con minimo en cero")
    void negativeStockIsNegativeEvenWithoutAConfiguredMinimum() {
        assertThat(itemWith("-0.500", "0").level()).isEqualTo(StockLevel.NEGATIVE);
    }

    @Test
    @DisplayName("stock igual al minimo ya es LOW")
    void stockAtTheMinimumIsAlreadyLow() {
        assertThat(itemWith("5.000", "5.000").level()).isEqualTo(StockLevel.LOW);
    }

    @Test
    @DisplayName("stock bajo el minimo es LOW")
    void stockBelowTheMinimumIsLow() {
        assertThat(itemWith("4.000", "5.000").level()).isEqualTo(StockLevel.LOW);
    }

    @Test
    @DisplayName("minimo en cero no genera alerta: significa 'no configure minimo'")
    void aZeroMinimumNeverRaisesAnAlert() {
        assertThat(itemWith("0.000", "0").level()).isEqualTo(StockLevel.OK);
        assertThat(itemWith("50.000", "0").level()).isEqualTo(StockLevel.OK);
    }

    @Test
    @DisplayName("stock sobre el minimo es OK")
    void stockAboveTheMinimumIsOk() {
        assertThat(itemWith("6.000", "5.000").level()).isEqualTo(StockLevel.OK);
    }
}
```

- [ ] **Step 2: Correr la prueba y verificar que falla**

```powershell
.\mvnw.cmd -B test -Dtest=InventoryItemTest
```

Esperado: **BUILD FAILURE** con errores de compilación — `package com.callejon9.inventory.domain does not exist`. En Java el rojo de TDD es el error de compilación; es correcto y esperado.

- [ ] **Step 3: Crear la migración**

`backend/src/main/resources/db/migration/V7__inventory_item_active.sql`:

```sql
-- ============================================================
-- Baja logica de insumos.
--
-- Un insumo esta referenciado por los movimientos que lo tocaron
-- (inventory_movements.inventory_item_id), asi que borrarlo perderia
-- el historico. Mismo patron que products, restaurant_tables y users:
-- active = false, nunca DELETE.
--
-- No hace falta tocar la politica RLS ni los permisos: la politica
-- tenant_isolation aplica a la tabla completa y los GRANT de V4 se
-- otorgaron a nivel de tabla, no de columna.
-- ============================================================

ALTER TABLE inventory_items
    ADD COLUMN active boolean NOT NULL DEFAULT true;
```

- [ ] **Step 4: Crear el enum de tipos con su signo**

`backend/src/main/java/com/callejon9/inventory/domain/InventoryMovementType.java`:

```java
package com.callejon9.inventory.domain;

import java.math.BigDecimal;

/**
 * Los cuatro tipos que el CHECK de inventory_movements admite.
 *
 * El signo vive aqui porque es lo unico que sabe que significa cada tipo:
 * ponerlo en el servicio repartiria el mismo switch por varios metodos. En
 * IN, OUT y WASTE la cantidad siempre llega positiva y el tipo decide la
 * direccion; ADJUSTMENT es el unico que llega con signo, porque es una
 * diferencia contra el conteo fisico y puede ir en cualquier sentido.
 */
public enum InventoryMovementType {
    IN,
    OUT,
    ADJUSTMENT,
    WASTE;

    public BigDecimal signedEffect(BigDecimal quantity) {
        return switch (this) {
            case IN -> quantity;
            case OUT, WASTE -> quantity.negate();
            case ADJUSTMENT -> quantity;
        };
    }
}
```

- [ ] **Step 5: Crear el enum de nivel**

`backend/src/main/java/com/callejon9/inventory/domain/StockLevel.java`:

```java
package com.callejon9.inventory.domain;

/**
 * Nivel derivado del stock, calculado por la entidad y enviado en la
 * respuesta para que la interfaz no recalcule umbrales de negocio.
 *
 * NEGATIVE es un estado propio, no un caso de LOW: un stock negativo no es
 * "se esta acabando", es la senal de que el conteo fisico esta mal y hay que
 * corregirlo.
 */
public enum StockLevel {
    OK,
    LOW,
    NEGATIVE
}
```

- [ ] **Step 6: Crear la entidad `InventoryItem`**

`backend/src/main/java/com/callejon9/inventory/domain/InventoryItem.java`:

```java
package com.callejon9.inventory.domain;

import com.callejon9.shared.domain.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "inventory_items")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryItem extends TenantScopedEntity {

    @Column(nullable = false, length = 160)
    private String name;

    @Column(nullable = false, length = 20)
    private String unit;

    @Column(nullable = false)
    private BigDecimal stock;

    @Column(name = "min_stock", nullable = false)
    private BigDecimal minStock;

    @Column(name = "unit_cost", nullable = false)
    private BigDecimal unitCost;

    @Column(nullable = false)
    private boolean active;

    /**
     * Aplica el efecto de un movimiento sobre el stock. Nunca lanza y nunca
     * topa en cero: negarse a registrar el movimiento no produce la cebolla
     * que el cocinero tiene en la mano, produce que dejen de usar el sistema.
     * Un stock negativo se permite y se senala (ver {@link #level()}).
     */
    public void apply(InventoryMovementType type, BigDecimal quantity) {
        this.stock = this.stock.add(type.signedEffect(quantity));
    }

    /**
     * La alerta de minimo exige {@code minStock > 0}. La columna tiene
     * DEFAULT 0, asi que sin esa condicion todo insumo recien creado con
     * stock 0 y minimo 0 apareceria en alerta, y la lista de alertas -- que
     * es la mitad del valor del modulo -- naceria llena de ruido.
     * {@code minStock = 0} significa "no configure minimo".
     */
    public StockLevel level() {
        if (stock.signum() < 0) {
            return StockLevel.NEGATIVE;
        }
        if (minStock.signum() > 0 && stock.compareTo(minStock) <= 0) {
            return StockLevel.LOW;
        }
        return StockLevel.OK;
    }
}
```

- [ ] **Step 7: Crear la entidad `InventoryMovement`**

`backend/src/main/java/com/callejon9/inventory/domain/InventoryMovement.java`:

```java
package com.callejon9.inventory.domain;

import com.callejon9.shared.domain.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Una fila del ledger. La suma de las cantidades de todos los movimientos de
 * un insumo cuadra siempre con su columna stock: no existe ningun camino que
 * cambie el stock sin dejar una fila aqui.
 */
@Entity
@Table(name = "inventory_movements")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryMovement extends TenantScopedEntity {

    @Column(name = "inventory_item_id", nullable = false)
    private UUID inventoryItemId;

    @Enumerated(EnumType.STRING)
    @Column(name = "movement_type", nullable = false, length = 20)
    private InventoryMovementType movementType;

    /** Con signo unicamente en ADJUSTMENT; en los otros tres siempre positiva. */
    @Column(nullable = false)
    private BigDecimal quantity;

    @Column(length = 200)
    private String reason;

    @Column(name = "user_id")
    private UUID userId;
}
```

- [ ] **Step 8: Correr la prueba unitaria y verificar que pasa**

```powershell
.\mvnw.cmd -B test -Dtest=InventoryItemTest
```

Esperado: **BUILD SUCCESS**, 9 pruebas verdes.

- [ ] **Step 9: Correr la suite completa**

```powershell
.\mvnw.cmd -B verify
```

Esperado: **BUILD SUCCESS**. Este paso no es ceremonia: con `ddl-auto: validate`, si el mapeo de las entidades nuevas no coincidiera con el esquema real, *todos* los tests con `@SpringBootTest` fallarían al levantar el contexto. Que la suite siga verde es la prueba de que la migración y las entidades concuerdan.

- [ ] **Step 10: Commit**

```bash
git add backend/src/main/resources/db/migration/V7__inventory_item_active.sql backend/src/main/java/com/callejon9/inventory backend/src/test/java/com/callejon9/inventory
git commit -m "feat(backend): add inventory domain with stock arithmetic in the entity"
```

---

### Task 2: Catálogo de insumos por HTTP

Sin `initialStock` y sin la regla de unidad bloqueada: las dos necesitan movimientos, y llegan en la tarea 4.

**Files:**
- Create: `backend/src/main/java/com/callejon9/inventory/repository/InventoryItemRepository.java`
- Create: `backend/src/main/java/com/callejon9/inventory/service/InventoryItemService.java`
- Create: `backend/src/main/java/com/callejon9/inventory/web/InventoryItemController.java`
- Create: `backend/src/main/java/com/callejon9/inventory/web/dto/CreateInventoryItemRequest.java`
- Create: `backend/src/main/java/com/callejon9/inventory/web/dto/UpdateInventoryItemRequest.java`
- Create: `backend/src/main/java/com/callejon9/inventory/web/dto/UpdateInventoryItemStatusRequest.java`
- Create: `backend/src/main/java/com/callejon9/inventory/web/dto/InventoryItemResponse.java`
- Test: `backend/src/test/java/com/callejon9/inventory/InventoryItemControllerTest.java`

**Interfaces:**
- Consumes: de la tarea 1, `InventoryItem`, `StockLevel`. Del proyecto, `ResourceNotFoundException` (→404), `BusinessRuleException` (→409), `TenantOnboardingService.onboard(...)`, `JwtService.generateAccessToken(User)`.
- Produces: `InventoryItemRepository` con `findByActiveTrueOrderByName()`, `findAllByOrderByName()`, `existsByName(String)`, `existsByNameAndIdNot(String, UUID)`; `InventoryItemService.listItems(boolean)`, `.createItem(String, String, BigDecimal, BigDecimal)`, `.updateItem(UUID, String, String, BigDecimal, BigDecimal)`, `.setActive(UUID, boolean)`; `InventoryItemResponse.from(InventoryItem)`; endpoints bajo `/api/v1/inventory/items`.

- [ ] **Step 1: Escribir la prueba de controlador que falla**

Crear `backend/src/test/java/com/callejon9/inventory/InventoryItemControllerTest.java`:

```java
package com.callejon9.inventory;

import com.callejon9.auth.service.JwtService;
import com.callejon9.platform.tenant.domain.Tenant;
import com.callejon9.platform.tenant.service.TenantOnboardingService;
import com.callejon9.tenancy.TenantContext;
import com.callejon9.user.domain.User;
import com.callejon9.user.domain.UserRole;
import jakarta.servlet.http.Cookie;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Insumos")
class InventoryItemControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private TenantOnboardingService onboardingService;
    @Autowired private JwtService jwtService;
    @Autowired private JdbcTemplate jdbcTemplate;

    private Tenant tenant;
    private User admin;

    @BeforeEach
    void seed() {
        tenant = onboardingService.onboard("Insumos Test", "insumos-test",
                "admin@insumos.com", "Admin", "Secreto123!", "FREE");
        admin = fakeUser(UserRole.ADMIN);
    }

    @AfterEach
    void cleanUp() {
        TenantContext.clear();
        jdbcTemplate.update("DELETE FROM tenants WHERE slug = 'insumos-test'");
    }

    private Cookie cookieFor(User user) {
        return new Cookie("access_token", jwtService.generateAccessToken(user));
    }

    private User fakeUser(UserRole role) {
        User user = User.builder()
                .email(role.name().toLowerCase() + "@insumos.com").passwordHash("x")
                .fullName(role.name()).role(role).active(true).build();
        user.setId(UUID.randomUUID());
        user.setTenantId(tenant.getId());
        return user;
    }

    private UUID createItem(String name, String unit) throws Exception {
        String body = mockMvc.perform(post("/api/v1/inventory/items")
                        .cookie(cookieFor(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\",\"unit\":\"" + unit + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(body.replaceAll(".*\"id\":\"([0-9a-fA-F-]+)\".*", "$1"));
    }

    @Test
    @DisplayName("ADMIN crea un insumo y nace con stock en cero y nivel OK")
    void adminCreatesAnItemThatStartsAtZero() throws Exception {
        mockMvc.perform(post("/api/v1/inventory/items")
                        .cookie(cookieFor(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Cebolla","unit":"kg","minStock":5.000,"unitCost":32.50}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Cebolla"))
                .andExpect(jsonPath("$.unit").value("kg"))
                .andExpect(jsonPath("$.stock").value(0))
                .andExpect(jsonPath("$.minStock").value(5.000))
                .andExpect(jsonPath("$.unitCost").value(32.50))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.level").value("OK"));
    }

    @Test
    @DisplayName("minStock y unitCost son opcionales y entran en cero")
    void minStockAndUnitCostDefaultToZero() throws Exception {
        mockMvc.perform(post("/api/v1/inventory/items")
                        .cookie(cookieFor(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Sal\",\"unit\":\"kg\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.minStock").value(0))
                .andExpect(jsonPath("$.unitCost").value(0));
    }

    @Test
    @DisplayName("crear un insumo con un nombre ya usado da 409")
    void duplicateNameIsRejected() throws Exception {
        createItem("Cebolla", "kg");

        mockMvc.perform(post("/api/v1/inventory/items")
                        .cookie(cookieFor(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Cebolla\",\"unit\":\"pieza\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("nombre vacio da 400")
    void blankNameIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/inventory/items")
                        .cookie(cookieFor(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"  \",\"unit\":\"kg\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.name").exists());
    }

    @Test
    @DisplayName("un WAITER puede consultar insumos pero no crearlos ni editarlos")
    void waiterCanReadButNotWrite() throws Exception {
        UUID itemId = createItem("Cebolla", "kg");
        User waiter = fakeUser(UserRole.WAITER);

        mockMvc.perform(get("/api/v1/inventory/items").cookie(cookieFor(waiter)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        mockMvc.perform(post("/api/v1/inventory/items")
                        .cookie(cookieFor(waiter))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Tomate\",\"unit\":\"kg\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/v1/inventory/items/" + itemId)
                        .cookie(cookieFor(waiter))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Cebolla morada\",\"unit\":\"kg\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch("/api/v1/inventory/items/" + itemId)
                        .cookie(cookieFor(waiter))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("ADMIN corrige nombre, minimo y costo, y el stock queda intacto")
    void adminUpdatesTheItemWithoutTouchingStock() throws Exception {
        UUID itemId = createItem("Cebolla", "kg");

        mockMvc.perform(put("/api/v1/inventory/items/" + itemId)
                        .cookie(cookieFor(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Cebolla morada","unit":"kg","minStock":8.000,"unitCost":40.00}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Cebolla morada"))
                .andExpect(jsonPath("$.minStock").value(8.000))
                .andExpect(jsonPath("$.unitCost").value(40.00))
                .andExpect(jsonPath("$.stock").value(0));
    }

    @Test
    @DisplayName("renombrar un insumo sobre el nombre de otro da 409")
    void renamingOntoAnExistingNameIsRejected() throws Exception {
        createItem("Cebolla", "kg");
        UUID tomatoId = createItem("Tomate", "kg");

        mockMvc.perform(put("/api/v1/inventory/items/" + tomatoId)
                        .cookie(cookieFor(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Cebolla\",\"unit\":\"kg\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("editar un insumo que no existe da 404")
    void updatingANonexistentItemGives404() throws Exception {
        mockMvc.perform(put("/api/v1/inventory/items/" + UUID.randomUUID())
                        .cookie(cookieFor(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Cebolla\",\"unit\":\"kg\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("PATCH da de baja un insumo y desaparece del listado por defecto")
    void patchDeactivatesAndHidesTheItem() throws Exception {
        UUID itemId = createItem("Cebolla", "kg");

        mockMvc.perform(patch("/api/v1/inventory/items/" + itemId)
                        .cookie(cookieFor(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));

        mockMvc.perform(get("/api/v1/inventory/items").cookie(cookieFor(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        mockMvc.perform(get("/api/v1/inventory/items").param("includeInactive", "true")
                        .cookie(cookieFor(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].active").value(false));
    }

    @Test
    @DisplayName("reactivar un insumo lo hace reaparecer en el listado por defecto")
    void reactivatingMakesTheItemReappear() throws Exception {
        UUID itemId = createItem("Cebolla", "kg");

        mockMvc.perform(patch("/api/v1/inventory/items/" + itemId)
                        .cookie(cookieFor(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false}"))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/v1/inventory/items/" + itemId)
                        .cookie(cookieFor(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));

        mockMvc.perform(get("/api/v1/inventory/items").cookie(cookieFor(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    @DisplayName("el listado sale ordenado por nombre")
    void listIsOrderedByName() throws Exception {
        createItem("Tomate", "kg");
        createItem("Aceite", "litro");
        createItem("Cebolla", "kg");

        mockMvc.perform(get("/api/v1/inventory/items").cookie(cookieFor(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Aceite"))
                .andExpect(jsonPath("$[1].name").value("Cebolla"))
                .andExpect(jsonPath("$[2].name").value("Tomate"));
    }
}
```

- [ ] **Step 2: Correr y verificar que falla**

```powershell
.\mvnw.cmd -B test -Dtest=InventoryItemControllerTest
```

Esperado: **BUILD FAILURE**, error de compilación por los DTOs y el controlador inexistentes.

- [ ] **Step 3: Crear el repositorio**

`backend/src/main/java/com/callejon9/inventory/repository/InventoryItemRepository.java`:

```java
package com.callejon9.inventory.repository;

import com.callejon9.inventory.domain.InventoryItem;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * No filtra por tenant en las consultas: las politicas RLS de PostgreSQL ya
 * limitan las filas visibles al tenant activo.
 */
public interface InventoryItemRepository extends JpaRepository<InventoryItem, UUID> {

    boolean existsByName(String name);

    /** Para renombrar un insumo existente sin chocar contra si mismo. */
    boolean existsByNameAndIdNot(String name, UUID id);

    List<InventoryItem> findByActiveTrueOrderByName();

    /** Para includeInactive=true: todos los insumos, sin filtrar por estado. */
    List<InventoryItem> findAllByOrderByName();
}
```

- [ ] **Step 4: Crear los DTOs**

`CreateInventoryItemRequest.java`:

```java
package com.callejon9.inventory.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * {@code minStock} y {@code unitCost} son opcionales y su ausencia equivale a
 * cero, que es el DEFAULT de las columnas. Un insumo sin minimo configurado no
 * genera alertas, que es lo que se espera de no haberlo configurado.
 */
public record CreateInventoryItemRequest(
        @NotBlank @Size(max = 160) String name,
        @NotBlank @Size(max = 20) String unit,
        @PositiveOrZero BigDecimal minStock,
        @PositiveOrZero BigDecimal unitCost) {
}
```

`UpdateInventoryItemRequest.java`:

```java
package com.callejon9.inventory.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * No incluye {@code stock} a proposito: el stock solo cambia a traves de un
 * movimiento, para que el ledger explique siempre el numero que se ve.
 */
public record UpdateInventoryItemRequest(
        @NotBlank @Size(max = 160) String name,
        @NotBlank @Size(max = 20) String unit,
        @PositiveOrZero BigDecimal minStock,
        @PositiveOrZero BigDecimal unitCost) {
}
```

`UpdateInventoryItemStatusRequest.java`:

```java
package com.callejon9.inventory.web.dto;

import jakarta.validation.constraints.NotNull;

public record UpdateInventoryItemStatusRequest(@NotNull Boolean active) {
}
```

`InventoryItemResponse.java`:

```java
package com.callejon9.inventory.web.dto;

import com.callejon9.inventory.domain.InventoryItem;
import com.callejon9.inventory.domain.StockLevel;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * {@code level} viaja ya calculado por la entidad: la interfaz pinta una
 * insignia sin recalcular umbrales de negocio en el cliente.
 */
public record InventoryItemResponse(
        UUID id,
        String name,
        String unit,
        BigDecimal stock,
        BigDecimal minStock,
        BigDecimal unitCost,
        boolean active,
        StockLevel level) {

    public static InventoryItemResponse from(InventoryItem item) {
        return new InventoryItemResponse(
                item.getId(), item.getName(), item.getUnit(),
                item.getStock(), item.getMinStock(), item.getUnitCost(),
                item.isActive(), item.level());
    }
}
```

- [ ] **Step 5: Crear el servicio**

`backend/src/main/java/com/callejon9/inventory/service/InventoryItemService.java`:

```java
package com.callejon9.inventory.service;

import com.callejon9.inventory.domain.InventoryItem;
import com.callejon9.inventory.repository.InventoryItemRepository;
import com.callejon9.shared.error.BusinessRuleException;
import com.callejon9.shared.error.ResourceNotFoundException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Catalogo de insumos. El stock NUNCA se toca desde aqui: ver InventoryMovementService. */
@Service
public class InventoryItemService {

    private final InventoryItemRepository itemRepository;

    public InventoryItemService(InventoryItemRepository itemRepository) {
        this.itemRepository = itemRepository;
    }

    /**
     * Por defecto solo insumos activos. La pantalla de inventario pasa
     * {@code includeInactive = true} para poder ver (y reactivar) los dados de
     * baja, que de otro modo quedarian atrapados sin forma de deshacerse.
     */
    @Transactional(readOnly = true)
    public List<InventoryItem> listItems(boolean includeInactive) {
        return includeInactive
                ? itemRepository.findAllByOrderByName()
                : itemRepository.findByActiveTrueOrderByName();
    }

    @Transactional
    public InventoryItem createItem(String name, String unit,
                                    BigDecimal minStock, BigDecimal unitCost) {
        if (itemRepository.existsByName(name)) {
            throw new BusinessRuleException("Ya existe un insumo llamado '" + name + "'.");
        }

        return itemRepository.save(InventoryItem.builder()
                .name(name)
                .unit(unit)
                .stock(BigDecimal.ZERO)
                .minStock(Objects.requireNonNullElse(minStock, BigDecimal.ZERO))
                .unitCost(Objects.requireNonNullElse(unitCost, BigDecimal.ZERO))
                .active(true)
                .build());
    }

    /**
     * Corrige nombre, unidad, minimo y costo. No toca el stock: cambiarlo aqui
     * dejaria un salto en el historial que ninguna fila explicaria.
     */
    @Transactional
    public InventoryItem updateItem(UUID itemId, String name, String unit,
                                    BigDecimal minStock, BigDecimal unitCost) {
        InventoryItem item = itemRepository.findById(itemId)
                .orElseThrow(() -> new ResourceNotFoundException("El insumo no existe."));

        if (itemRepository.existsByNameAndIdNot(name, itemId)) {
            throw new BusinessRuleException("Ya existe un insumo llamado '" + name + "'.");
        }

        item.setName(name);
        item.setUnit(unit);
        item.setMinStock(Objects.requireNonNullElse(minStock, BigDecimal.ZERO));
        item.setUnitCost(Objects.requireNonNullElse(unitCost, BigDecimal.ZERO));
        return itemRepository.save(item);
    }

    /**
     * Da de alta o de baja un insumo. La baja es siempre logica: el insumo
     * queda referenciado por los movimientos que lo tocaron, y borrarlo
     * perderia ese historico.
     */
    @Transactional
    public InventoryItem setActive(UUID itemId, boolean active) {
        InventoryItem item = itemRepository.findById(itemId)
                .orElseThrow(() -> new ResourceNotFoundException("El insumo no existe."));

        item.setActive(active);
        return itemRepository.save(item);
    }
}
```

- [ ] **Step 6: Crear el controlador**

`backend/src/main/java/com/callejon9/inventory/web/InventoryItemController.java`:

```java
package com.callejon9.inventory.web;

import com.callejon9.inventory.service.InventoryItemService;
import com.callejon9.inventory.web.dto.CreateInventoryItemRequest;
import com.callejon9.inventory.web.dto.InventoryItemResponse;
import com.callejon9.inventory.web.dto.UpdateInventoryItemRequest;
import com.callejon9.inventory.web.dto.UpdateInventoryItemStatusRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/inventory/items")
public class InventoryItemController {

    private final InventoryItemService itemService;

    public InventoryItemController(InventoryItemService itemService) {
        this.itemService = itemService;
    }

    @GetMapping
    public List<InventoryItemResponse> list(
            @RequestParam(required = false, defaultValue = "false") boolean includeInactive) {
        return itemService.listItems(includeInactive).stream()
                .map(InventoryItemResponse::from)
                .toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public InventoryItemResponse create(@Valid @RequestBody CreateInventoryItemRequest request) {
        return InventoryItemResponse.from(itemService.createItem(
                request.name(), request.unit(), request.minStock(), request.unitCost()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public InventoryItemResponse update(@PathVariable UUID id,
                                        @Valid @RequestBody UpdateInventoryItemRequest request) {
        return InventoryItemResponse.from(itemService.updateItem(
                id, request.name(), request.unit(), request.minStock(), request.unitCost()));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public InventoryItemResponse patch(@PathVariable UUID id,
                                       @Valid @RequestBody UpdateInventoryItemStatusRequest request) {
        return InventoryItemResponse.from(itemService.setActive(id, request.active()));
    }
}
```

- [ ] **Step 7: Correr la prueba y verificar que pasa**

```powershell
.\mvnw.cmd -B test -Dtest=InventoryItemControllerTest
```

Esperado: **BUILD SUCCESS**, 11 pruebas verdes.

- [ ] **Step 8: Commit**

```bash
git add backend/src/main/java/com/callejon9/inventory backend/src/test/java/com/callejon9/inventory
git commit -m "feat(backend): add inventory item catalog endpoints"
```

---

### Task 3: Movimientos manuales

**Files:**
- Create: `backend/src/main/java/com/callejon9/inventory/repository/InventoryMovementRepository.java`
- Create: `backend/src/main/java/com/callejon9/inventory/service/InventoryMovementService.java`
- Create: `backend/src/main/java/com/callejon9/inventory/web/InventoryMovementController.java`
- Create: `backend/src/main/java/com/callejon9/inventory/web/dto/RegisterMovementRequest.java`
- Create: `backend/src/main/java/com/callejon9/inventory/web/dto/ValidMovementRequest.java`
- Create: `backend/src/main/java/com/callejon9/inventory/web/dto/MovementRequestValidator.java`
- Create: `backend/src/main/java/com/callejon9/inventory/web/dto/InventoryMovementRow.java`
- Test: `backend/src/test/java/com/callejon9/inventory/InventoryMovementControllerTest.java`

**Interfaces:**
- Consumes: de la tarea 1, `InventoryMovementType`, `InventoryItem`, `InventoryMovement`. De la tarea 2, `InventoryItemRepository` y el endpoint `POST /api/v1/inventory/items`. Del proyecto, `BusinessCalendar.today()` y `.toInstantRange(LocalDate, LocalDate)`, `InstantRange.start()/.endExclusive()`.
- Produces: `InventoryMovementRepository.findHistory(Instant, Instant, UUID)`, `.existsByInventoryItemId(UUID)`, `.sumQuantityByInventoryItemId(UUID)`; `InventoryMovementService.register(UUID, InventoryMovementType, BigDecimal, BigDecimal, String, UUID)` y `.listMovements(LocalDate, LocalDate, UUID)`; endpoints bajo `/api/v1/inventory/movements`.

**Nota deliberada:** este paso usa `findById`. El lock pesimista llega en la tarea 5, con la prueba que lo justifica.

- [ ] **Step 1: Escribir la prueba de controlador que falla**

Crear `backend/src/test/java/com/callejon9/inventory/InventoryMovementControllerTest.java`. Reutiliza el mismo andamiaje de siembra que la tarea 2 (`slug = 'movimientos-test'`):

```java
package com.callejon9.inventory;

import com.callejon9.auth.service.JwtService;
import com.callejon9.platform.tenant.domain.Tenant;
import com.callejon9.platform.tenant.service.TenantOnboardingService;
import com.callejon9.tenancy.TenantContext;
import com.callejon9.user.domain.User;
import com.callejon9.user.domain.UserRole;
import jakarta.servlet.http.Cookie;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Movimientos de inventario")
class InventoryMovementControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private TenantOnboardingService onboardingService;
    @Autowired private JwtService jwtService;
    @Autowired private JdbcTemplate jdbcTemplate;

    private Tenant tenant;
    private User admin;

    @BeforeEach
    void seed() {
        tenant = onboardingService.onboard("Movimientos Test", "movimientos-test",
                "admin@movimientos.com", "Admin", "Secreto123!", "FREE");
        admin = fakeUser(UserRole.ADMIN);
    }

    @AfterEach
    void cleanUp() {
        TenantContext.clear();
        jdbcTemplate.update("DELETE FROM tenants WHERE slug = 'movimientos-test'");
    }

    private Cookie cookieFor(User user) {
        return new Cookie("access_token", jwtService.generateAccessToken(user));
    }

    private User fakeUser(UserRole role) {
        User user = User.builder()
                .email(role.name().toLowerCase() + "@movimientos.com").passwordHash("x")
                .fullName(role.name()).role(role).active(true).build();
        user.setId(UUID.randomUUID());
        user.setTenantId(tenant.getId());
        return user;
    }

    private UUID createItem(String name) throws Exception {
        String body = mockMvc.perform(post("/api/v1/inventory/items")
                        .cookie(cookieFor(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\",\"unit\":\"kg\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(body.replaceAll(".*\"id\":\"([0-9a-fA-F-]+)\".*", "$1"));
    }

    private void register(UUID itemId, String json) throws Exception {
        mockMvc.perform(post("/api/v1/inventory/movements")
                        .cookie(cookieFor(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"inventoryItemId\":\"" + itemId + "\"," + json))
                .andExpect(status().isCreated());
    }

    private void expectStock(UUID itemId, double expected) throws Exception {
        mockMvc.perform(get("/api/v1/inventory/items").cookie(cookieFor(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id=='" + itemId + "')].stock").value(expected));
    }

    @Test
    @DisplayName("una entrada suma al stock del insumo")
    void anEntryAddsToStock() throws Exception {
        UUID itemId = createItem("Cebolla");

        register(itemId, "\"movementType\":\"IN\",\"quantity\":20.000}");

        expectStock(itemId, 20.000);
    }

    @Test
    @DisplayName("una salida resta del stock")
    void anExitSubtractsFromStock() throws Exception {
        UUID itemId = createItem("Cebolla");
        register(itemId, "\"movementType\":\"IN\",\"quantity\":20.000}");

        register(itemId, "\"movementType\":\"OUT\",\"quantity\":5.000}");

        expectStock(itemId, 15.000);
    }

    @Test
    @DisplayName("una merma con motivo resta del stock")
    void aWasteWithReasonSubtractsFromStock() throws Exception {
        UUID itemId = createItem("Cebolla");
        register(itemId, "\"movementType\":\"IN\",\"quantity\":20.000}");

        register(itemId, "\"movementType\":\"WASTE\",\"quantity\":2.000,\"reason\":\"Se echo a perder\"}");

        expectStock(itemId, 18.000);
    }

    @Test
    @DisplayName("el stock puede quedar negativo y se reporta como NEGATIVE")
    void stockCanGoNegativeAndIsReported() throws Exception {
        UUID itemId = createItem("Cebolla");

        register(itemId, "\"movementType\":\"OUT\",\"quantity\":3.000}");

        mockMvc.perform(get("/api/v1/inventory/items").cookie(cookieFor(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].stock").value(-3.000))
                .andExpect(jsonPath("$[0].level").value("NEGATIVE"));
    }

    @Test
    @DisplayName("un ajuste guarda la diferencia con signo y el conteo en el motivo")
    void anAdjustmentStoresTheSignedDeltaAndKeepsTheCount() throws Exception {
        UUID itemId = createItem("Cebolla");
        register(itemId, "\"movementType\":\"IN\",\"quantity\":11.000}");

        mockMvc.perform(post("/api/v1/inventory/movements")
                        .cookie(cookieFor(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"inventoryItemId\":\"" + itemId
                                + "\",\"movementType\":\"ADJUSTMENT\",\"countedStock\":8.000}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.quantity").value(-3.000))
                .andExpect(jsonPath("$.reason").value("Conteo fisico: 8"));

        expectStock(itemId, 8.000);
    }

    @Test
    @DisplayName("un ajuste concatena el motivo del usuario despues del conteo")
    void anAdjustmentAppendsTheUserReason() throws Exception {
        UUID itemId = createItem("Cebolla");
        register(itemId, "\"movementType\":\"IN\",\"quantity\":10.000}");

        mockMvc.perform(post("/api/v1/inventory/movements")
                        .cookie(cookieFor(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"inventoryItemId\":\"" + itemId + "\",\"movementType\":\"ADJUSTMENT\","
                                + "\"countedStock\":12.000,\"reason\":\"Habia una caja sin registrar\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.quantity").value(2.000))
                .andExpect(jsonPath("$.reason")
                        .value("Conteo fisico: 12 - Habia una caja sin registrar"));
    }

    @Test
    @DisplayName("un ajuste cuyo conteo coincide con el stock da 409")
    void anAdjustmentThatChangesNothingIsRejected() throws Exception {
        UUID itemId = createItem("Cebolla");
        register(itemId, "\"movementType\":\"IN\",\"quantity\":10.000}");

        mockMvc.perform(post("/api/v1/inventory/movements")
                        .cookie(cookieFor(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"inventoryItemId\":\"" + itemId
                                + "\",\"movementType\":\"ADJUSTMENT\",\"countedStock\":10.000}"))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("mandar quantity junto con ADJUSTMENT da 400 y senala el campo")
    void quantityWithAnAdjustmentIsRejected() throws Exception {
        UUID itemId = createItem("Cebolla");

        mockMvc.perform(post("/api/v1/inventory/movements")
                        .cookie(cookieFor(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"inventoryItemId\":\"" + itemId + "\",\"movementType\":\"ADJUSTMENT\","
                                + "\"countedStock\":8.000,\"quantity\":3.000}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.quantity").exists());
    }

    @Test
    @DisplayName("un ajuste sin countedStock da 400 y senala el campo")
    void anAdjustmentWithoutTheCountIsRejected() throws Exception {
        UUID itemId = createItem("Cebolla");

        mockMvc.perform(post("/api/v1/inventory/movements")
                        .cookie(cookieFor(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"inventoryItemId\":\"" + itemId
                                + "\",\"movementType\":\"ADJUSTMENT\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.countedStock").exists());
    }

    @Test
    @DisplayName("mandar countedStock en una entrada da 400")
    void countedStockOnAnEntryIsRejected() throws Exception {
        UUID itemId = createItem("Cebolla");

        mockMvc.perform(post("/api/v1/inventory/movements")
                        .cookie(cookieFor(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"inventoryItemId\":\"" + itemId
                                + "\",\"movementType\":\"IN\",\"countedStock\":8.000}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.countedStock").exists());
    }

    @Test
    @DisplayName("una merma sin motivo da 400: sin motivo no sirve para nada")
    void aWasteWithoutReasonIsRejected() throws Exception {
        UUID itemId = createItem("Cebolla");

        mockMvc.perform(post("/api/v1/inventory/movements")
                        .cookie(cookieFor(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"inventoryItemId\":\"" + itemId
                                + "\",\"movementType\":\"WASTE\",\"quantity\":2.000}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.reason").exists());
    }

    @Test
    @DisplayName("cantidad cero o negativa en una entrada da 400")
    void nonPositiveQuantityIsRejected() throws Exception {
        UUID itemId = createItem("Cebolla");

        mockMvc.perform(post("/api/v1/inventory/movements")
                        .cookie(cookieFor(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"inventoryItemId\":\"" + itemId
                                + "\",\"movementType\":\"IN\",\"quantity\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.quantity").exists());
    }

    @Test
    @DisplayName("un movimiento sobre un insumo dado de baja da 409")
    void aMovementOnAnInactiveItemIsRejected() throws Exception {
        UUID itemId = createItem("Cebolla");
        mockMvc.perform(patch("/api/v1/inventory/items/" + itemId)
                        .cookie(cookieFor(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/inventory/movements")
                        .cookie(cookieFor(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"inventoryItemId\":\"" + itemId
                                + "\",\"movementType\":\"IN\",\"quantity\":5.000}"))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("un movimiento sobre un insumo inexistente da 404")
    void aMovementOnANonexistentItemGives404() throws Exception {
        mockMvc.perform(post("/api/v1/inventory/movements")
                        .cookie(cookieFor(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"inventoryItemId\":\"" + UUID.randomUUID()
                                + "\",\"movementType\":\"IN\",\"quantity\":5.000}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("KITCHEN puede registrar movimientos; WAITER no")
    void kitchenCanRegisterAndWaiterCannot() throws Exception {
        UUID itemId = createItem("Cebolla");

        mockMvc.perform(post("/api/v1/inventory/movements")
                        .cookie(cookieFor(fakeUser(UserRole.KITCHEN)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"inventoryItemId\":\"" + itemId
                                + "\",\"movementType\":\"WASTE\",\"quantity\":1.000,\"reason\":\"Quemado\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/inventory/movements")
                        .cookie(cookieFor(fakeUser(UserRole.WAITER)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"inventoryItemId\":\"" + itemId
                                + "\",\"movementType\":\"IN\",\"quantity\":1.000}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("el movimiento registra quien lo hizo y trae el nombre del insumo")
    void theListingCarriesTheItemNameAndTheAuthor() throws Exception {
        UUID itemId = createItem("Cebolla");
        register(itemId, "\"movementType\":\"IN\",\"quantity\":4.000}");

        mockMvc.perform(get("/api/v1/inventory/movements").cookie(cookieFor(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].itemName").value("Cebolla"))
                .andExpect(jsonPath("$[0].unit").value("kg"))
                .andExpect(jsonPath("$[0].movementType").value("IN"))
                .andExpect(jsonPath("$[0].userName").value("ADMIN"));
    }

    @Test
    @DisplayName("el listado filtra por itemId")
    void theListingFiltersByItem() throws Exception {
        UUID onionId = createItem("Cebolla");
        UUID tomatoId = createItem("Tomate");
        register(onionId, "\"movementType\":\"IN\",\"quantity\":4.000}");
        register(tomatoId, "\"movementType\":\"IN\",\"quantity\":7.000}");

        mockMvc.perform(get("/api/v1/inventory/movements")
                        .param("itemId", onionId.toString())
                        .cookie(cookieFor(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].itemName").value("Cebolla"));
    }

    @Test
    @DisplayName("un movimiento de hoy aparece en el dia local del negocio, no en el de UTC")
    void aMovementRegisteredTodayAppearsInTheBusinessDay() throws Exception {
        UUID itemId = createItem("Cebolla");
        register(itemId, "\"movementType\":\"IN\",\"quantity\":4.000}");

        // Sin parametros el rango es "hoy" en la zona del negocio. Si el rango
        // se resolviera en UTC, despues de las 18:00 locales este listado
        // saldria vacio -- justo a media cena.
        mockMvc.perform(get("/api/v1/inventory/movements").cookie(cookieFor(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    @DisplayName("un rango que no incluye hoy sale vacio")
    void aRangeThatExcludesTodayIsEmpty() throws Exception {
        UUID itemId = createItem("Cebolla");
        register(itemId, "\"movementType\":\"IN\",\"quantity\":4.000}");

        mockMvc.perform(get("/api/v1/inventory/movements")
                        .param("from", "2020-01-01").param("to", "2020-01-31")
                        .cookie(cookieFor(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }
}
```

- [ ] **Step 2: Correr y verificar que falla**

```powershell
.\mvnw.cmd -B test -Dtest=InventoryMovementControllerTest
```

Esperado: **BUILD FAILURE** por compilación.

- [ ] **Step 3: Crear la proyección del listado**

`backend/src/main/java/com/callejon9/inventory/web/dto/InventoryMovementRow.java`:

```java
package com.callejon9.inventory.web.dto;

import com.callejon9.inventory.domain.InventoryMovementType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Una fila del ledger tal como la lee una persona. {@code userName} puede ser
 * nulo -- la llave es ON DELETE SET NULL y el usuario puede estar dado de
 * baja -- asi que viaja en su tipo de referencia.
 */
public record InventoryMovementRow(
        UUID id,
        UUID inventoryItemId,
        String itemName,
        String unit,
        InventoryMovementType movementType,
        BigDecimal quantity,
        String reason,
        String userName,
        Instant createdAt) {
}
```

- [ ] **Step 4: Crear el repositorio de movimientos**

`backend/src/main/java/com/callejon9/inventory/repository/InventoryMovementRepository.java`:

```java
package com.callejon9.inventory.repository;

import com.callejon9.inventory.domain.InventoryMovement;
import com.callejon9.inventory.web.dto.InventoryMovementRow;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * No filtra por tenant en las consultas: las politicas RLS de PostgreSQL ya
 * limitan las filas visibles al tenant activo.
 */
public interface InventoryMovementRepository extends JpaRepository<InventoryMovement, UUID> {

    /** Para bloquear el cambio de unidad en cuanto el insumo tiene historia. */
    boolean existsByInventoryItemId(UUID inventoryItemId);

    /**
     * Ledger del rango [from, to), mas reciente primero, opcionalmente de un
     * solo insumo. El join a InventoryItem es interno porque la llave es NOT
     * NULL con ON DELETE CASCADE: un movimiento sin insumo no existe. El de
     * User es left join porque su llave es ON DELETE SET NULL y el usuario
     * puede estar dado de baja. Ninguna de las dos asociaciones esta mapeada
     * en JPA (solo comparten el id como columna simple), asi que el join se
     * declara con {@code on}.
     */
    @Query("""
            select new com.callejon9.inventory.web.dto.InventoryMovementRow(
                m.id, m.inventoryItemId, i.name, i.unit,
                m.movementType, m.quantity, m.reason, u.fullName, m.createdAt)
            from InventoryMovement m
            join com.callejon9.inventory.domain.InventoryItem i on i.id = m.inventoryItemId
            left join com.callejon9.user.domain.User u on u.id = m.userId
            where m.createdAt >= :from and m.createdAt < :to
              and (:itemId is null or m.inventoryItemId = :itemId)
            order by m.createdAt desc
            """)
    List<InventoryMovementRow> findHistory(@Param("from") Instant from,
                                           @Param("to") Instant to,
                                           @Param("itemId") UUID itemId);

    /**
     * Suma de las cantidades de un insumo. La invariante del modulo es que
     * esta suma cuadre siempre con inventory_items.stock; la prueba de
     * concurrencia se apoya en ella.
     */
    @Query("""
            select coalesce(sum(m.quantity), 0)
            from InventoryMovement m
            where m.inventoryItemId = :itemId
            """)
    BigDecimal sumQuantityByInventoryItemId(@Param("itemId") UUID itemId);
}
```

- [ ] **Step 5: Crear la anotación de validación**

`backend/src/main/java/com/callejon9/inventory/web/dto/ValidMovementRequest.java`:

```java
package com.callejon9.inventory.web.dto;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Regla cruzada entre {@code movementType} y los campos de cantidad: no cabe
 * en una anotacion por campo porque depende del valor de otro campo.
 *
 * Va en la capa de validacion, no en el servicio: mandar el campo equivocado
 * es una solicitud mal formada (400), no un conflicto de negocio (409).
 */
@Documented
@Constraint(validatedBy = MovementRequestValidator.class)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidMovementRequest {

    String message() default "La combinacion de tipo y campos del movimiento es invalida.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
```

- [ ] **Step 6: Crear el validador**

`backend/src/main/java/com/callejon9/inventory/web/dto/MovementRequestValidator.java`:

```java
package com.callejon9.inventory.web.dto;

import com.callejon9.inventory.domain.InventoryMovementType;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Cada campo del cuerpo significa una sola cosa, y este validador es lo que lo
 * sostiene: ADJUSTMENT lleva countedStock y nunca quantity; los otros tres
 * llevan quantity y nunca countedStock; WASTE exige motivo.
 *
 * Los mensajes se cuelgan del campo con addPropertyNode para que salgan en el
 * mapa "errors" del ProblemDetail, que es donde el frontend ya los busca.
 */
public class MovementRequestValidator
        implements ConstraintValidator<ValidMovementRequest, RegisterMovementRequest> {

    @Override
    public boolean isValid(RegisterMovementRequest request, ConstraintValidatorContext context) {
        if (request == null || request.movementType() == null) {
            // @NotNull sobre movementType ya reporta ese caso; no se duplica.
            return true;
        }

        context.disableDefaultConstraintViolation();
        boolean valid = true;

        if (request.movementType() == InventoryMovementType.ADJUSTMENT) {
            if (request.countedStock() == null) {
                reject(context, "countedStock", "Un ajuste requiere el conteo fisico.");
                valid = false;
            }
            if (request.quantity() != null) {
                reject(context, "quantity",
                        "Un ajuste no lleva cantidad: el sistema calcula la diferencia.");
                valid = false;
            }
            return valid;
        }

        if (request.quantity() == null) {
            reject(context, "quantity", "La cantidad es obligatoria.");
            valid = false;
        }
        if (request.countedStock() != null) {
            reject(context, "countedStock", "El conteo fisico solo aplica a un ajuste.");
            valid = false;
        }
        if (request.movementType() == InventoryMovementType.WASTE && isBlank(request.reason())) {
            reject(context, "reason", "Una merma requiere motivo.");
            valid = false;
        }
        return valid;
    }

    private void reject(ConstraintValidatorContext context, String field, String message) {
        context.buildConstraintViolationWithTemplate(message)
                .addPropertyNode(field)
                .addConstraintViolation();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
```

- [ ] **Step 7: Crear la petición**

`backend/src/main/java/com/callejon9/inventory/web/dto/RegisterMovementRequest.java`:

```java
package com.callejon9.inventory.web.dto;

import com.callejon9.inventory.domain.InventoryMovementType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Dos campos de cantidad, cada uno con un solo significado:
 *
 * <pre>
 * IN / OUT / WASTE    { movementType, quantity: &gt; 0, reason }
 * ADJUSTMENT          { movementType, countedStock: &gt;= 0, reason }
 * </pre>
 *
 * El delta de un ajuste lo calcula el servidor, no el cliente: un cliente
 * leeria un stock que quizas ya cambio y dejaria el inventario en un numero
 * que nadie conto.
 *
 * {@code reason} se limita a 150 caracteres para que el prefijo "Conteo
 * fisico: ..." quepa en el varchar(200) de la columna.
 */
@ValidMovementRequest
public record RegisterMovementRequest(
        @NotNull UUID inventoryItemId,
        @NotNull InventoryMovementType movementType,
        @Positive BigDecimal quantity,
        @PositiveOrZero BigDecimal countedStock,
        @Size(max = 150) String reason) {
}
```

- [ ] **Step 8: Crear el servicio**

`backend/src/main/java/com/callejon9/inventory/service/InventoryMovementService.java`:

```java
package com.callejon9.inventory.service;

import com.callejon9.inventory.domain.InventoryItem;
import com.callejon9.inventory.domain.InventoryMovement;
import com.callejon9.inventory.domain.InventoryMovementType;
import com.callejon9.inventory.repository.InventoryItemRepository;
import com.callejon9.inventory.repository.InventoryMovementRepository;
import com.callejon9.inventory.web.dto.InventoryMovementRow;
import com.callejon9.shared.error.BusinessRuleException;
import com.callejon9.shared.error.ResourceNotFoundException;
import com.callejon9.shared.time.BusinessCalendar;
import com.callejon9.shared.time.InstantRange;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * El ledger. Todo cambio de stock pasa por aqui: no existe ningun otro camino
 * que toque inventory_items.stock, y por eso la suma de los movimientos de un
 * insumo cuadra siempre con su columna.
 */
@Service
public class InventoryMovementService {

    private final InventoryItemRepository itemRepository;
    private final InventoryMovementRepository movementRepository;
    private final BusinessCalendar businessCalendar;

    public InventoryMovementService(InventoryItemRepository itemRepository,
                                    InventoryMovementRepository movementRepository,
                                    BusinessCalendar businessCalendar) {
        this.itemRepository = itemRepository;
        this.movementRepository = movementRepository;
        this.businessCalendar = businessCalendar;
    }

    /**
     * Registra un movimiento y aplica su efecto sobre el stock, en una sola
     * transaccion.
     *
     * En un ADJUSTMENT el delta se calcula aqui, contra el stock que acaba de
     * leerse: {@code countedStock - stock}. Es la unica forma de que el
     * numero guardado corresponda al conteo que la persona hizo.
     */
    @Transactional
    public InventoryMovement register(UUID itemId, InventoryMovementType type,
                                     BigDecimal quantity, BigDecimal countedStock,
                                     String reason, UUID userId) {
        InventoryItem item = itemRepository.findById(itemId)
                .orElseThrow(() -> new ResourceNotFoundException("El insumo no existe."));

        if (!item.isActive()) {
            throw new BusinessRuleException("El insumo '" + item.getName()
                    + "' esta dado de baja. Reactivalo para poder moverlo.");
        }

        BigDecimal effectiveQuantity = type == InventoryMovementType.ADJUSTMENT
                ? countedStock.subtract(item.getStock())
                : quantity;

        if (type == InventoryMovementType.ADJUSTMENT && effectiveQuantity.signum() == 0) {
            throw new BusinessRuleException(
                    "El conteo coincide con el stock registrado: no hay nada que ajustar.");
        }

        item.apply(type, effectiveQuantity);
        itemRepository.save(item);

        return movementRepository.save(InventoryMovement.builder()
                .inventoryItemId(item.getId())
                .movementType(type)
                .quantity(effectiveQuantity)
                .reason(type == InventoryMovementType.ADJUSTMENT
                        ? adjustmentReason(countedStock, reason)
                        : reason)
                .userId(userId)
                .build());
    }

    /**
     * El motivo de un ajuste conserva el numero que se conto, que el delta por
     * si solo pierde: guardar solo "-3" no dice si se contaron 8 sobre 11 o 5
     * sobre 8.
     */
    private String adjustmentReason(BigDecimal countedStock, String reason) {
        String prefix = "Conteo fisico: " + countedStock.stripTrailingZeros().toPlainString();
        return reason == null || reason.isBlank() ? prefix : prefix + " - " + reason.trim();
    }

    /**
     * El rango se resuelve en la zona horaria del negocio, no en UTC (ver
     * {@link BusinessCalendar}): un movimiento registrado a las 19:00 en
     * Mexico City cae en el dia siguiente en UTC, y el listado se vaciaria a
     * media cena. Sin parametros, hoy; con uno solo, ese mismo dia.
     */
    @Transactional(readOnly = true)
    public List<InventoryMovementRow> listMovements(LocalDate from, LocalDate to, UUID itemId) {
        LocalDate today = businessCalendar.today();
        LocalDate effectiveFrom = from != null ? from : (to != null ? to : today);
        LocalDate effectiveTo = to != null ? to : effectiveFrom;

        InstantRange range = businessCalendar.toInstantRange(effectiveFrom, effectiveTo);
        return movementRepository.findHistory(range.start(), range.endExclusive(), itemId);
    }
}
```

- [ ] **Step 9: Crear el controlador**

`backend/src/main/java/com/callejon9/inventory/web/InventoryMovementController.java`:

```java
package com.callejon9.inventory.web;

import com.callejon9.inventory.domain.InventoryMovement;
import com.callejon9.inventory.service.InventoryMovementService;
import com.callejon9.inventory.web.dto.InventoryMovementRow;
import com.callejon9.inventory.web.dto.RegisterMovementRequest;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** El principal autenticado es el UUID del usuario (ver TenantFilter); nunca se confia en el body. */
@RestController
@RequestMapping("/api/v1/inventory/movements")
public class InventoryMovementController {

    private final InventoryMovementService movementService;

    public InventoryMovementController(InventoryMovementService movementService) {
        this.movementService = movementService;
    }

    @GetMapping
    public List<InventoryMovementRow> list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) UUID itemId) {
        return movementService.listMovements(from, to, itemId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','KITCHEN')")
    public RegisteredMovementResponse register(@Valid @RequestBody RegisterMovementRequest request,
                                              Authentication authentication) {
        InventoryMovement movement = movementService.register(
                request.inventoryItemId(), request.movementType(),
                request.quantity(), request.countedStock(), request.reason(),
                userIdOf(authentication));
        return RegisteredMovementResponse.from(movement);
    }

    private UUID userIdOf(Authentication authentication) {
        return (UUID) authentication.getPrincipal();
    }
}
```

Añadir el import `com.callejon9.inventory.web.dto.RegisteredMovementResponse` junto a los otros.

- [ ] **Step 9b: Crear la respuesta del registro**

`backend/src/main/java/com/callejon9/inventory/web/dto/RegisteredMovementResponse.java`:

```java
package com.callejon9.inventory.web.dto;

import com.callejon9.inventory.domain.InventoryMovement;
import com.callejon9.inventory.domain.InventoryMovementType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * El movimiento tal como quedo guardado. Importa devolverlo y no un 204: en un
 * ajuste, el delta y el motivo compuesto los produjo el servidor, y quien lo
 * registro necesita ver que quedo.
 */
public record RegisteredMovementResponse(
        UUID id,
        UUID inventoryItemId,
        InventoryMovementType movementType,
        BigDecimal quantity,
        String reason,
        Instant createdAt) {

    public static RegisteredMovementResponse from(InventoryMovement movement) {
        return new RegisteredMovementResponse(
                movement.getId(), movement.getInventoryItemId(),
                movement.getMovementType(), movement.getQuantity(),
                movement.getReason(), movement.getCreatedAt());
    }
}
```

- [ ] **Step 10: Correr la prueba y verificar que pasa**

```powershell
.\mvnw.cmd -B test -Dtest=InventoryMovementControllerTest
```

Esperado: **BUILD SUCCESS**, 19 pruebas verdes.

- [ ] **Step 11: Commit**

```bash
git add backend/src/main/java/com/callejon9/inventory backend/src/test/java/com/callejon9/inventory
git commit -m "feat(backend): register manual inventory movements with physical count adjustments"
```

---

### Task 4: Los dos cruces entre servicios

Stock inicial en el alta y unidad bloqueada. Ambos necesitan que exista el ledger, por eso llegan ahora.

**Files:**
- Modify: `backend/src/main/java/com/callejon9/inventory/service/InventoryItemService.java`
- Modify: `backend/src/main/java/com/callejon9/inventory/web/dto/CreateInventoryItemRequest.java`
- Modify: `backend/src/main/java/com/callejon9/inventory/web/InventoryItemController.java`
- Modify: `backend/src/test/java/com/callejon9/inventory/InventoryItemControllerTest.java`

**Interfaces:**
- Consumes: `InventoryMovementService.register(...)` e `InventoryMovementRepository.existsByInventoryItemId(UUID)` de la tarea 3.
- Produces: `InventoryItemService.createItem(String, String, BigDecimal, BigDecimal, BigDecimal, UUID)` — **la firma cambia**: gana `initialStock` y `userId` al final.

- [ ] **Step 1: Añadir las cuatro pruebas que fallan**

Agregar al final de `InventoryItemControllerTest`, antes de la llave de cierre:

```java
    @Test
    @DisplayName("crear un insumo con stock inicial deja el stock y su movimiento IN")
    void initialStockLeavesAnEntryInTheLedger() throws Exception {
        String body = mockMvc.perform(post("/api/v1/inventory/items")
                        .cookie(cookieFor(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Cebolla","unit":"kg","initialStock":20.000}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.stock").value(20.000))
                .andReturn().getResponse().getContentAsString();
        UUID itemId = UUID.fromString(body.replaceAll(".*\"id\":\"([0-9a-fA-F-]+)\".*", "$1"));

        // El stock no aparecio de la nada: hay una fila que lo explica.
        mockMvc.perform(get("/api/v1/inventory/movements")
                        .param("itemId", itemId.toString())
                        .cookie(cookieFor(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].movementType").value("IN"))
                .andExpect(jsonPath("$[0].quantity").value(20.000))
                .andExpect(jsonPath("$[0].reason").value("Stock inicial"));
    }

    @Test
    @DisplayName("crear un insumo sin stock inicial no genera ningun movimiento")
    void withoutInitialStockThereIsNoMovement() throws Exception {
        UUID itemId = createItem("Cebolla", "kg");

        mockMvc.perform(get("/api/v1/inventory/movements")
                        .param("itemId", itemId.toString())
                        .cookie(cookieFor(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("se puede cambiar la unidad mientras el insumo no tenga movimientos")
    void theUnitCanChangeWhileThereIsNoHistory() throws Exception {
        UUID itemId = createItem("Cebolla", "kg");

        mockMvc.perform(put("/api/v1/inventory/items/" + itemId)
                        .cookie(cookieFor(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Cebolla\",\"unit\":\"gramo\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unit").value("gramo"));
    }

    @Test
    @DisplayName("cambiar la unidad de un insumo con movimientos da 409, pero el resto si se corrige")
    void theUnitIsLockedOnceThereIsHistory() throws Exception {
        UUID itemId = createItem("Cebolla", "kg");
        mockMvc.perform(post("/api/v1/inventory/movements")
                        .cookie(cookieFor(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"inventoryItemId\":\"" + itemId
                                + "\",\"movementType\":\"IN\",\"quantity\":20.000}"))
                .andExpect(status().isCreated());

        mockMvc.perform(put("/api/v1/inventory/items/" + itemId)
                        .cookie(cookieFor(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Cebolla\",\"unit\":\"gramo\"}"))
                .andExpect(status().isConflict());

        // Mandar la MISMA unidad no es un cambio y no debe estorbar.
        mockMvc.perform(put("/api/v1/inventory/items/" + itemId)
                        .cookie(cookieFor(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Cebolla blanca\",\"unit\":\"kg\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Cebolla blanca"));
    }
```

- [ ] **Step 2: Correr y verificar que fallan**

```powershell
.\mvnw.cmd -B test -Dtest=InventoryItemControllerTest
```

Esperado: **4 pruebas en rojo**. `initialStockLeavesAnEntryInTheLedger` falla porque `stock` sale 0 (el campo se ignora), y `theUnitIsLockedOnceThereIsHistory` porque el `PUT` devuelve 200 en lugar de 409. Las otras dos ya pasan — quedan como red de seguridad de que el cambio no rompe el caso sin historia.

- [ ] **Step 3: Añadir `initialStock` a la petición**

En `CreateInventoryItemRequest`, agregar el campo al final del record:

```java
public record CreateInventoryItemRequest(
        @NotBlank @Size(max = 160) String name,
        @NotBlank @Size(max = 20) String unit,
        @PositiveOrZero BigDecimal minStock,
        @PositiveOrZero BigDecimal unitCost,
        @PositiveOrZero BigDecimal initialStock) {
}
```

Y ampliar su javadoc con una línea: `initialStock` es opcional y su ausencia equivale a cero — dar de alta un insumo que todavía no llega es el caso normal, no la excepción.

- [ ] **Step 4: Cerrar los dos cruces en el servicio**

En `InventoryItemService`, inyectar las dos dependencias nuevas y reescribir `createItem` y `updateItem`:

```java
    private final InventoryItemRepository itemRepository;
    private final InventoryMovementRepository movementRepository;
    private final InventoryMovementService movementService;

    public InventoryItemService(InventoryItemRepository itemRepository,
                               InventoryMovementRepository movementRepository,
                               InventoryMovementService movementService) {
        this.itemRepository = itemRepository;
        this.movementRepository = movementRepository;
        this.movementService = movementService;
    }

    /**
     * El stock inicial no se escribe en la columna: se registra como un
     * movimiento IN, y el movimiento es el que mueve el stock. Asi no existe
     * ningun camino por el que la columna cambie sin una fila que lo explique,
     * ni siquiera el alta.
     *
     * Es una sola transaccion: si el movimiento falla, el insumo no queda
     * creado con un stock que nada justifica.
     */
    @Transactional
    public InventoryItem createItem(String name, String unit, BigDecimal minStock,
                                    BigDecimal unitCost, BigDecimal initialStock, UUID userId) {
        if (itemRepository.existsByName(name)) {
            throw new BusinessRuleException("Ya existe un insumo llamado '" + name + "'.");
        }

        InventoryItem item = itemRepository.save(InventoryItem.builder()
                .name(name)
                .unit(unit)
                .stock(BigDecimal.ZERO)
                .minStock(Objects.requireNonNullElse(minStock, BigDecimal.ZERO))
                .unitCost(Objects.requireNonNullElse(unitCost, BigDecimal.ZERO))
                .active(true)
                .build());

        if (initialStock != null && initialStock.signum() > 0) {
            movementService.register(item.getId(), InventoryMovementType.IN,
                    initialStock, null, "Stock inicial", userId);
        }
        return item;
    }

    /**
     * Corrige nombre, unidad, minimo y costo. No toca el stock: cambiarlo aqui
     * dejaria un salto en el historial que ninguna fila explicaria.
     *
     * La unidad queda fija en cuanto el insumo tiene movimientos. Sin esta
     * regla, un 20 registrado en kilos se convierte en gramos y nada en el
     * historial dice en que unidad se capturo cada fila. Mandar la misma
     * unidad no cuenta como cambio.
     */
    @Transactional
    public InventoryItem updateItem(UUID itemId, String name, String unit,
                                    BigDecimal minStock, BigDecimal unitCost) {
        InventoryItem item = itemRepository.findById(itemId)
                .orElseThrow(() -> new ResourceNotFoundException("El insumo no existe."));

        if (itemRepository.existsByNameAndIdNot(name, itemId)) {
            throw new BusinessRuleException("Ya existe un insumo llamado '" + name + "'.");
        }

        if (!item.getUnit().equals(unit) && movementRepository.existsByInventoryItemId(itemId)) {
            throw new BusinessRuleException("El insumo '" + item.getName() + "' ya tiene movimientos en '"
                    + item.getUnit() + "'. Cambiar la unidad haria ilegible su historial; "
                    + "crea otro insumo con la unidad correcta.");
        }

        item.setName(name);
        item.setUnit(unit);
        item.setMinStock(Objects.requireNonNullElse(minStock, BigDecimal.ZERO));
        item.setUnitCost(Objects.requireNonNullElse(unitCost, BigDecimal.ZERO));
        return itemRepository.save(item);
    }
```

Imports nuevos: `com.callejon9.inventory.domain.InventoryMovementType`, `com.callejon9.inventory.repository.InventoryMovementRepository`.

**Nota sobre el `stock` devuelto:** `movementService.register` modifica la misma instancia de `InventoryItem` dentro de la transacción (es la misma entidad gestionada por el `EntityManager`), así que el `item` que se devuelve ya trae el stock actualizado y `InventoryItemResponse.from` reporta 20.000, no 0. Si el test dijera 0, la causa sería que el servicio recargó la entidad; no hay que "arreglarlo" añadiendo un `findById` extra.

- [ ] **Step 5: Pasar el usuario desde el controlador**

En `InventoryItemController`, el `POST` gana el `Authentication`:

```java
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public InventoryItemResponse create(@Valid @RequestBody CreateInventoryItemRequest request,
                                        Authentication authentication) {
        return InventoryItemResponse.from(itemService.createItem(
                request.name(), request.unit(), request.minStock(), request.unitCost(),
                request.initialStock(), (UUID) authentication.getPrincipal()));
    }
```

Import nuevo: `org.springframework.security.core.Authentication`.

- [ ] **Step 6: Correr las dos clases y verificar que pasan**

```powershell
.\mvnw.cmd -B test -Dtest="InventoryItemControllerTest+InventoryMovementControllerTest"
```

Esperado: **BUILD SUCCESS**, 15 + 19 pruebas verdes.

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/com/callejon9/inventory backend/src/test/java/com/callejon9/inventory
git commit -m "feat(backend): back initial stock with a movement and lock the unit once history exists"
```

---

### Task 5: El lock pesimista

**Files:**
- Modify: `backend/src/main/java/com/callejon9/inventory/repository/InventoryItemRepository.java`
- Modify: `backend/src/main/java/com/callejon9/inventory/service/InventoryMovementService.java:register` (una línea)
- Test: `backend/src/test/java/com/callejon9/inventory/InventoryMovementConcurrencyTest.java`

**Interfaces:**
- Produces: `InventoryItemRepository.findByIdForUpdate(UUID): Optional<InventoryItem>`.

- [ ] **Step 1: Escribir la prueba de carrera que falla**

Crear `backend/src/test/java/com/callejon9/inventory/InventoryMovementConcurrencyTest.java`:

```java
package com.callejon9.inventory;

import com.callejon9.inventory.domain.InventoryItem;
import com.callejon9.inventory.domain.InventoryMovementType;
import com.callejon9.inventory.repository.InventoryItemRepository;
import com.callejon9.inventory.repository.InventoryMovementRepository;
import com.callejon9.inventory.service.InventoryItemService;
import com.callejon9.inventory.service.InventoryMovementService;
import com.callejon9.platform.tenant.domain.Tenant;
import com.callejon9.platform.tenant.service.TenantOnboardingService;
import com.callejon9.tenancy.TenantContext;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Bajo READ COMMITTED, dos salidas simultaneas sobre el MISMO insumo pueden
 * leer ambas el mismo stock antes de que cualquiera escriba: el ledger acaba
 * con dos movimientos y la columna stock refleja solo uno. No hay error, no hay
 * excepcion; simplemente el inventario deja de cuadrar.
 *
 * Ese lost update rompe la invariante que sostiene el modulo -- que la suma de
 * los movimientos cuadre con el stock -- asi que la asercion central no es
 * "el stock vale 5", es "el stock es igual a la suma del ledger".
 *
 * El CountDownLatch es lo que hace valer la prueba: llamar al metodo dos veces
 * en secuencia no reproduce la carrera y pasaria incluso sin el lock.
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Concurrencia al mover el mismo insumo")
class InventoryMovementConcurrencyTest {

    @Autowired private InventoryItemService itemService;
    @Autowired private InventoryMovementService movementService;
    @Autowired private InventoryItemRepository itemRepository;
    @Autowired private InventoryMovementRepository movementRepository;
    @Autowired private TenantOnboardingService onboardingService;
    @Autowired private JdbcTemplate jdbcTemplate;

    private UUID tenantId;
    private UUID itemId;

    @BeforeEach
    void seed() {
        Tenant tenant = onboardingService.onboard("Inventario Concurrencia", "inv-concurrencia",
                "admin@inv.com", "Admin", "Secreto123!", "FREE");
        tenantId = tenant.getId();

        TenantContext.set(tenantId);
        try {
            // userId nulo a proposito: la columna es nullable y este test no
            // necesita autoria, solo la carrera.
            InventoryItem item = itemService.createItem(
                    "Cebolla", "kg", BigDecimal.ZERO, BigDecimal.ZERO,
                    new BigDecimal("20.000"), null);
            itemId = item.getId();
        } finally {
            TenantContext.clear();
        }
    }

    @AfterEach
    void cleanUp() {
        TenantContext.clear();
        jdbcTemplate.update("DELETE FROM tenants WHERE slug = 'inv-concurrencia'");
    }

    @Test
    @DisplayName("dos salidas a la vez: el stock refleja las dos y cuadra con el ledger")
    void concurrentExitsBothLandAndTheLedgerReconciles() throws InterruptedException {
        int threadCount = 2;
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threadCount);
        List<Throwable> failures = new CopyOnWriteArrayList<>();

        Runnable exitTask = () -> {
            TenantContext.set(tenantId);
            try {
                ready.countDown();
                start.await();
                movementService.register(itemId, InventoryMovementType.OUT,
                        new BigDecimal("5.000"), null, null, null);
            } catch (Throwable ex) {
                failures.add(ex);
            } finally {
                TenantContext.clear();
                done.countDown();
            }
        };

        Thread cookOne = new Thread(exitTask, "cook-1");
        Thread cookTwo = new Thread(exitTask, "cook-2");
        cookOne.start();
        cookTwo.start();

        assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
        start.countDown();
        assertThat(done.await(10, TimeUnit.SECONDS)).isTrue();

        assertThat(failures).as("ninguna salida debe fallar: las dos son validas").isEmpty();

        TenantContext.set(tenantId);
        try {
            BigDecimal stock = itemRepository.findById(itemId).orElseThrow().getStock();
            BigDecimal ledger = movementRepository.sumQuantityByInventoryItemId(itemId);

            assertThat(stock)
                    .as("20 de entrada menos dos salidas de 5 deben dejar 10")
                    .isEqualByComparingTo("10.000");
            assertThat(stock)
                    .as("la suma del ledger debe cuadrar con la columna stock")
                    .isEqualByComparingTo(ledger);
        } finally {
            TenantContext.clear();
        }
    }
}
```

- [ ] **Step 2: Correr y verificar que falla por la carrera, no por compilación**

```powershell
.\mvnw.cmd -B test -Dtest=InventoryMovementConcurrencyTest
```

Esperado: **FAIL** en la aserción del stock, con un valor de `15.000` en lugar de `10.000` (una de las dos salidas se perdió). La prueba puede pasar por casualidad si los hilos se serializan solos; si pasa, córrela dos o tres veces más antes de continuar. Que falle al menos una vez es lo que demuestra que la carrera existe.

- [ ] **Step 3: Añadir el finder con lock**

Al final de `InventoryItemRepository`, con los imports `jakarta.persistence.LockModeType`, `java.util.Optional`, `org.springframework.data.jpa.repository.Lock`, `org.springframework.data.jpa.repository.Query` y `org.springframework.data.repository.query.Param`:

```java
    /**
     * Igual que {@code findById}, pero con {@code SELECT ... FOR UPDATE}:
     * bloquea la fila hasta que la transaccion actual termine.
     *
     * Sin este lock, dos movimientos simultaneos sobre el mismo insumo bajo
     * READ COMMITTED leen el mismo stock, calculan sobre el mismo valor y una
     * escritura sobrescribe a la otra: el ledger guarda dos filas y la columna
     * stock refleja una. Con el lock, la segunda transaccion espera a que la
     * primera confirme y relee el stock ya actualizado.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from InventoryItem i where i.id = :id")
    Optional<InventoryItem> findByIdForUpdate(@Param("id") UUID id);
```

- [ ] **Step 4: Usar el lock en el servicio**

En `InventoryMovementService.register`, cambiar la primera línea del cuerpo:

```java
        InventoryItem item = itemRepository.findByIdForUpdate(itemId)
                .orElseThrow(() -> new ResourceNotFoundException("El insumo no existe."));
```

Y añadir al javadoc del método: el lock va **antes** de leer el stock, porque el delta de un ajuste se calcula contra ese valor y leerlo sin bloquear es justo la ventana de la carrera.

`InventoryItemService.updateItem` y `setActive` siguen con `findById`: no calculan nada a partir del stock, así que no hay carrera que serializar y bloquear ahí solo agregaría contención.

- [ ] **Step 5: Correr la prueba de concurrencia varias veces**

```powershell
.\mvnw.cmd -B test -Dtest=InventoryMovementConcurrencyTest
.\mvnw.cmd -B test -Dtest=InventoryMovementConcurrencyTest
.\mvnw.cmd -B test -Dtest=InventoryMovementConcurrencyTest
```

Esperado: **BUILD SUCCESS** las tres veces. Una carrera que se arregla no se comprueba con una sola corrida.

- [ ] **Step 6: Correr la suite completa**

```powershell
.\mvnw.cmd -B verify
```

Esperado: **BUILD SUCCESS**. Debe haber 128 + 44 = **172 pruebas**.

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/com/callejon9/inventory backend/src/test/java/com/callejon9/inventory
git commit -m "fix(backend): serialize concurrent movements on the same inventory item"
```

---

## Nota sobre las tareas de frontend

El proyecto no tiene pruebas automatizadas de frontend y este plan no introduce un arnés nuevo: el spec lo dice explícitamente. La verificación de cada tarea es `npm run lint`, `npm run build` y comprobaciones concretas en el navegador, enumeradas paso por paso.

Para verificar en navegador hacen falta backend y frontend arriba:

```powershell
# Terminal 1, desde la raiz
.\scripts\run-dev.ps1
# Terminal 2, desde frontend/
npm run dev
```

---

### Task 6: Tipos, rutas, claves, navegación e insignias

**Files:**
- Modify: `frontend/src/lib/types.ts`
- Modify: `frontend/src/lib/endpoints.ts`
- Modify: `frontend/src/lib/query-keys.ts`
- Modify: `frontend/src/components/layout/app-sidebar.tsx`
- Modify: `frontend/src/components/shared/status-badge.tsx`

**Interfaces:**
- Produces: tipos `InventoryMovementType`, `StockLevel`, `InventoryItemResponse`, `InventoryMovementRow`, `CreateInventoryItemRequest`, `UpdateInventoryItemRequest`, `UpdateInventoryItemStatusRequest`, `RegisterMovementRequest`, `RegisteredMovementResponse`; constantes `MOVEMENT_TYPE_LABELS`, `STOCK_LEVEL_LABELS`; `endpoints.inventory.*`; `queryKeys.inventory.*`; `StatusBadge` con `kind: "stock"` y `kind: "movement"`.

- [ ] **Step 1: Añadir los tipos**

En `frontend/src/lib/types.ts`, junto a los demás tipos de dominio (después de `PaymentMethod`, línea ~80):

```ts
export type InventoryMovementType = "IN" | "OUT" | "ADJUSTMENT" | "WASTE";

export type StockLevel = "OK" | "LOW" | "NEGATIVE";
```

Junto a las demás respuestas:

```ts
export interface InventoryItemResponse {
  id: string;
  name: string;
  unit: string;
  stock: number;
  minStock: number;
  unitCost: number;
  active: boolean;
  level: StockLevel;
}

/** Una fila del ledger. `reason` y `userName` pueden venir nulos: el motivo es
 * opcional salvo en mermas, y el usuario pudo darse de baja. */
export interface InventoryMovementRow {
  id: string;
  inventoryItemId: string;
  itemName: string;
  unit: string;
  movementType: InventoryMovementType;
  quantity: number;
  reason: string | null;
  userName: string | null;
  createdAt: string;
}

export interface RegisteredMovementResponse {
  id: string;
  inventoryItemId: string;
  movementType: InventoryMovementType;
  quantity: number;
  reason: string | null;
  createdAt: string;
}
```

Junto a las demás peticiones:

```ts
export interface CreateInventoryItemRequest {
  name: string;
  unit: string;
  minStock?: number;
  unitCost?: number;
  /** Opcional. Si viene, el backend registra su movimiento IN "Stock inicial". */
  initialStock?: number;
}

export interface UpdateInventoryItemRequest {
  name: string;
  unit: string;
  minStock?: number;
  unitCost?: number;
}

export interface UpdateInventoryItemStatusRequest {
  active: boolean;
}

/**
 * `quantity` en entradas, salidas y mermas; `countedStock` solo en ajustes, y
 * nunca los dos: el backend rechaza la combinacion con 400. El delta de un
 * ajuste lo calcula el servidor, no este cliente.
 */
export interface RegisterMovementRequest {
  inventoryItemId: string;
  movementType: InventoryMovementType;
  quantity?: number;
  countedStock?: number;
  reason?: string;
}
```

Y al final, con los demás mapas de etiquetas:

```ts
export const MOVEMENT_TYPE_LABELS: Record<InventoryMovementType, string> = {
  IN: "Entrada",
  OUT: "Salida",
  ADJUSTMENT: "Ajuste",
  WASTE: "Merma",
};

export const STOCK_LEVEL_LABELS: Record<StockLevel, string> = {
  OK: "Suficiente",
  LOW: "Bajo minimo",
  NEGATIVE: "Negativo",
};
```

- [ ] **Step 2: Añadir las rutas**

En `frontend/src/lib/endpoints.ts`, después de `analytics`:

```ts
  inventory: {
    items: () => "/api/v1/inventory/items",
    createItem: () => "/api/v1/inventory/items",
    /** PUT: corrige nombre/unidad/minimo/costo. PATCH: alta o baja. Mismo path. */
    updateItem: (itemId: string) => `/api/v1/inventory/items/${itemId}`,
    updateItemStatus: (itemId: string) => `/api/v1/inventory/items/${itemId}`,
    /** GET; acepta `from`/`to` (fechas ISO) e `itemId`. Default: hoy. */
    movements: () => "/api/v1/inventory/movements",
    registerMovement: () => "/api/v1/inventory/movements",
  },
```

- [ ] **Step 3: Añadir las claves de consulta**

En `frontend/src/lib/query-keys.ts`, después de `analytics`:

```ts
  inventory: {
    /** Mismo criterio que `products.all`: la pantalla pide includeInactive
     * para poder reactivar insumos dados de baja. */
    items: (includeInactive?: boolean) =>
      includeInactive
        ? (["inventory", "items", { includeInactive: true }] as const)
        : (["inventory", "items"] as const),
    /** El rango y el filtro de insumo son parte de la clave. */
    movements: (from: string, to: string, itemId?: string) =>
      ["inventory", "movements", { from, to, itemId: itemId ?? null }] as const,
  },
```

- [ ] **Step 4: Añadir la sección al sidebar**

En `frontend/src/components/layout/app-sidebar.tsx`, añadir la constante y las dos entradas:

```ts
const NAV_INVENTORY = { href: "/inventory", label: "Inventario" };
```

```ts
  ADMIN: [NAV_ADMIN, NAV_WAITER, NAV_KITCHEN, NAV_CASHIER, NAV_INVENTORY, NAV_HISTORY, NAV_ANALYTICS],
  WAITER: [NAV_WAITER],
  KITCHEN: [NAV_KITCHEN, NAV_INVENTORY],
  CASHIER: [NAV_CASHIER, NAV_HISTORY],
```

Y ampliar el comentario del bloque con un párrafo:

```
 * KITCHEN ve Inventario porque POST /api/v1/inventory/movements es
 * hasAnyRole('ADMIN','KITCHEN'): la cocina es quien ve la merma y quien saca
 * los insumos del estante. Como el alta y la edicion de insumos si son solo
 * de ADMIN, la pantalla oculta esos botones por rol -- la barra concede la
 * seccion, no cada operacion dentro de ella.
```

- [ ] **Step 5: Extender la insignia de estado**

Reescribir `frontend/src/components/shared/status-badge.tsx`:

```tsx
import { Badge } from "@/components/ui/badge";
import {
  KITCHEN_STATUS_LABELS,
  MOVEMENT_TYPE_LABELS,
  ORDER_STATUS_LABELS,
  PAYMENT_METHOD_LABELS,
  STOCK_LEVEL_LABELS,
  TABLE_STATUS_LABELS,
  type InventoryMovementType,
  type KitchenItemStatus,
  type OrderStatus,
  type PaymentMethod,
  type StockLevel,
  type TableStatus,
} from "@/lib/types";

type StatusBadgeProps =
  | { kind: "order"; status: OrderStatus }
  | { kind: "table"; status: TableStatus }
  | { kind: "kitchen"; status: KitchenItemStatus }
  | { kind: "payment"; status: PaymentMethod }
  | { kind: "stock"; status: StockLevel }
  | { kind: "movement"; status: InventoryMovementType };

/**
 * Badge con la etiqueta en espanol de cualquiera de las seis familias de
 * estado del dominio. `src/lib/types.ts` es la unica fuente de esas
 * etiquetas; este componente solo elige el mapa correcto segun `kind`.
 *
 * El nivel de stock es la unica familia que cambia de color: un stock negativo
 * no es un estado mas, es la senal de que el conteo fisico esta mal, y en una
 * lista de treinta insumos una insignia gris se pierde.
 */
export function StatusBadge(props: StatusBadgeProps) {
  return <Badge variant={variantFor(props)}>{labelFor(props)}</Badge>;
}

function variantFor(props: StatusBadgeProps): "secondary" | "destructive" | "outline" {
  if (props.kind === "stock") {
    if (props.status === "NEGATIVE") {
      return "destructive";
    }
    if (props.status === "LOW") {
      return "outline";
    }
  }
  return "secondary";
}

function labelFor(props: StatusBadgeProps): string {
  switch (props.kind) {
    case "order":
      return ORDER_STATUS_LABELS[props.status];
    case "table":
      return TABLE_STATUS_LABELS[props.status];
    case "kitchen":
      return KITCHEN_STATUS_LABELS[props.status];
    case "payment":
      return PAYMENT_METHOD_LABELS[props.status];
    case "stock":
      return STOCK_LEVEL_LABELS[props.status];
    case "movement":
      return MOVEMENT_TYPE_LABELS[props.status];
  }
}
```

- [ ] **Step 6: Verificar que compila y no rompe nada**

```powershell
npm run lint
npm run build
```

Esperado: sin errores. El `build` es la verificación importante: `StatusBadge` se usa en cinco pantallas y el `switch` exhaustivo sobre `kind` hace que TypeScript falle si una familia quedó sin rama.

- [ ] **Step 7: Verificar la navegación en navegador**

1. Entrar como `ADMIN`: la barra muestra **Inventario** entre *Caja* e *Historial*. El enlace da 404 todavía — la pantalla llega en la tarea 7.
2. Crear un usuario `KITCHEN` desde *Administracion → Usuarios* si no existe, entrar con él: la barra muestra **Cocina** e **Inventario**, y nada más.
3. Entrar como `WAITER`: la barra muestra solo *Mesas*, sin *Inventario*.

- [ ] **Step 8: Commit**

```bash
git add frontend/src/lib frontend/src/components
git commit -m "feat(frontend): add inventory types, routes, query keys and navigation"
```

---

### Task 7: Pestaña Insumos

**Files:**
- Create: `frontend/src/app/(authenticated)/inventory/page.tsx`
- Create: `frontend/src/app/(authenticated)/inventory/inventory-view.tsx`
- Create: `frontend/src/app/(authenticated)/inventory/create-item-dialog.tsx`
- Create: `frontend/src/app/(authenticated)/inventory/edit-item-dialog.tsx`

**Interfaces:**
- Consumes: todo lo de la tarea 6, más `QueryState`, `Money`, `FieldError`, `useSession`, `api`, `ApiError`.
- Produces: `InventoryView` (default export de la pantalla), `CreateItemDialog`, `EditItemDialog` con props `{ item: InventoryItemResponse | null; onOpenChange: (open: boolean) => void }`.

- [ ] **Step 1: Crear la página**

`frontend/src/app/(authenticated)/inventory/page.tsx`. Copiar la forma exacta de `app/(authenticated)/history/page.tsx` (leerlo primero) cambiando el componente por `InventoryView`:

```tsx
import { InventoryView } from "./inventory-view";

export default function InventoryPage() {
  return <InventoryView />;
}
```

- [ ] **Step 2: Crear el diálogo de alta**

`frontend/src/app/(authenticated)/inventory/create-item-dialog.tsx`:

```tsx
"use client";

import { useState } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";

import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { FieldError } from "@/components/shared/field-error";
import { ApiError, api } from "@/lib/api";
import { endpoints } from "@/lib/endpoints";
import { queryKeys } from "@/lib/query-keys";
import type { CreateInventoryItemRequest, InventoryItemResponse } from "@/lib/types";

/**
 * Alta de insumo. El stock inicial es opcional: si se captura, el backend
 * registra su movimiento IN "Stock inicial", de modo que ni el alta cambia el
 * stock sin dejar una fila que lo explique.
 */
export function CreateItemDialog() {
  const [open, setOpen] = useState(false);
  const queryClient = useQueryClient();

  const createMutation = useMutation({
    mutationFn: (payload: CreateInventoryItemRequest) =>
      api.post<InventoryItemResponse>(endpoints.inventory.createItem(), payload),
    onSuccess: (item) => {
      queryClient.invalidateQueries({ queryKey: queryKeys.inventory.items(true) });
      queryClient.invalidateQueries({ queryKey: ["inventory", "movements"] });
      toast.success(`Insumo "${item.name}" creado.`);
      setOpen(false);
    },
  });

  function handleSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const formData = new FormData(event.currentTarget);

    createMutation.mutate({
      name: String(formData.get("name") ?? ""),
      unit: String(formData.get("unit") ?? ""),
      minStock: optionalNumber(formData.get("minStock")),
      unitCost: optionalNumber(formData.get("unitCost")),
      initialStock: optionalNumber(formData.get("initialStock")),
    });
  }

  const apiError = createMutation.error instanceof ApiError ? createMutation.error : null;
  const hasFieldErrors = Boolean(apiError?.errors && Object.keys(apiError.errors).length > 0);

  return (
    <Dialog
      open={open}
      onOpenChange={(nextOpen) => {
        setOpen(nextOpen);
        if (!nextOpen) {
          createMutation.reset();
        }
      }}
    >
      <DialogTrigger asChild>
        <Button>Nuevo insumo</Button>
      </DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Nuevo insumo</DialogTitle>
          <DialogDescription>
            La unidad queda fija en cuanto el insumo tenga movimientos, asi que conviene elegirla
            con cuidado.
          </DialogDescription>
        </DialogHeader>
        <form onSubmit={handleSubmit} className="flex flex-col gap-4">
          {apiError && !hasFieldErrors && (
            <Alert variant="destructive">
              <AlertTitle>No se pudo crear el insumo</AlertTitle>
              <AlertDescription>{apiError.message}</AlertDescription>
            </Alert>
          )}

          <div className="flex flex-col gap-1.5">
            <Label htmlFor="name">Nombre</Label>
            <Input id="name" name="name" required maxLength={160} disabled={createMutation.isPending} />
            <FieldError error={apiError} field="name" />
          </div>

          <div className="flex flex-col gap-1.5">
            <Label htmlFor="unit">Unidad</Label>
            <Input
              id="unit"
              name="unit"
              required
              maxLength={20}
              placeholder="kg, litro, pieza"
              disabled={createMutation.isPending}
            />
            <FieldError error={apiError} field="unit" />
          </div>

          <div className="grid grid-cols-2 gap-4">
            <div className="flex flex-col gap-1.5">
              <Label htmlFor="minStock">Minimo (opcional)</Label>
              <Input
                id="minStock"
                name="minStock"
                type="number"
                min={0}
                step="0.001"
                disabled={createMutation.isPending}
              />
              <FieldError error={apiError} field="minStock" />
            </div>

            <div className="flex flex-col gap-1.5">
              <Label htmlFor="unitCost">Costo unitario (opcional)</Label>
              <Input
                id="unitCost"
                name="unitCost"
                type="number"
                min={0}
                step="0.01"
                disabled={createMutation.isPending}
              />
              <FieldError error={apiError} field="unitCost" />
            </div>
          </div>

          <div className="flex flex-col gap-1.5">
            <Label htmlFor="initialStock">Stock inicial (opcional)</Label>
            <Input
              id="initialStock"
              name="initialStock"
              type="number"
              min={0}
              step="0.001"
              disabled={createMutation.isPending}
            />
            <p className="text-xs text-muted-foreground">
              Si lo capturas, queda registrado como una entrada en el historial.
            </p>
            <FieldError error={apiError} field="initialStock" />
          </div>

          <DialogFooter>
            <Button type="submit" disabled={createMutation.isPending}>
              {createMutation.isPending ? "Guardando..." : "Crear insumo"}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}

/** Un campo numerico vacio debe viajar como undefined, no como 0 ni NaN. */
function optionalNumber(value: FormDataEntryValue | null): number | undefined {
  const text = String(value ?? "").trim();
  return text === "" ? undefined : Number(text);
}
```

- [ ] **Step 3: Crear el diálogo de edición**

`frontend/src/app/(authenticated)/inventory/edit-item-dialog.tsx`. Misma estructura, controlado desde fuera por la prop `item` (el patrón de `edit-product-dialog.tsx`, leerlo primero para copiar la forma del `Dialog` controlado):

```tsx
"use client";

import { useMutation, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";

import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { FieldError } from "@/components/shared/field-error";
import { ApiError, api } from "@/lib/api";
import { endpoints } from "@/lib/endpoints";
import { queryKeys } from "@/lib/query-keys";
import type { InventoryItemResponse, UpdateInventoryItemRequest } from "@/lib/types";

interface EditItemDialogProps {
  item: InventoryItemResponse | null;
  onOpenChange: (open: boolean) => void;
}

/**
 * Correccion de un insumo. No incluye el stock a proposito: el stock solo se
 * mueve con un movimiento. Si el insumo ya tiene historial, el backend rechaza
 * el cambio de unidad con 409 y el mensaje se muestra tal cual.
 */
export function EditItemDialog({ item, onOpenChange }: EditItemDialogProps) {
  const queryClient = useQueryClient();

  const updateMutation = useMutation({
    mutationFn: (payload: UpdateInventoryItemRequest) =>
      api.put<InventoryItemResponse>(endpoints.inventory.updateItem(item!.id), payload),
    onSuccess: (updated) => {
      queryClient.invalidateQueries({ queryKey: queryKeys.inventory.items(true) });
      queryClient.invalidateQueries({ queryKey: ["inventory", "movements"] });
      toast.success(`Insumo "${updated.name}" actualizado.`);
      onOpenChange(false);
    },
  });

  function handleSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const formData = new FormData(event.currentTarget);

    updateMutation.mutate({
      name: String(formData.get("name") ?? ""),
      unit: String(formData.get("unit") ?? ""),
      minStock: optionalNumber(formData.get("minStock")),
      unitCost: optionalNumber(formData.get("unitCost")),
    });
  }

  const apiError = updateMutation.error instanceof ApiError ? updateMutation.error : null;
  const hasFieldErrors = Boolean(apiError?.errors && Object.keys(apiError.errors).length > 0);

  return (
    <Dialog
      open={item !== null}
      onOpenChange={(nextOpen) => {
        if (!nextOpen) {
          updateMutation.reset();
        }
        onOpenChange(nextOpen);
      }}
    >
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Editar insumo</DialogTitle>
          <DialogDescription>
            El stock no se edita aqui: se cambia registrando un movimiento.
          </DialogDescription>
        </DialogHeader>
        {item && (
          <form onSubmit={handleSubmit} className="flex flex-col gap-4">
            {apiError && !hasFieldErrors && (
              <Alert variant="destructive">
                <AlertTitle>No se pudo actualizar el insumo</AlertTitle>
                <AlertDescription>{apiError.message}</AlertDescription>
              </Alert>
            )}

            <div className="flex flex-col gap-1.5">
              <Label htmlFor="edit-name">Nombre</Label>
              <Input
                id="edit-name"
                name="name"
                required
                maxLength={160}
                defaultValue={item.name}
                disabled={updateMutation.isPending}
              />
              <FieldError error={apiError} field="name" />
            </div>

            <div className="flex flex-col gap-1.5">
              <Label htmlFor="edit-unit">Unidad</Label>
              <Input
                id="edit-unit"
                name="unit"
                required
                maxLength={20}
                defaultValue={item.unit}
                disabled={updateMutation.isPending}
              />
              <FieldError error={apiError} field="unit" />
            </div>

            <div className="grid grid-cols-2 gap-4">
              <div className="flex flex-col gap-1.5">
                <Label htmlFor="edit-minStock">Minimo</Label>
                <Input
                  id="edit-minStock"
                  name="minStock"
                  type="number"
                  min={0}
                  step="0.001"
                  defaultValue={item.minStock}
                  disabled={updateMutation.isPending}
                />
                <FieldError error={apiError} field="minStock" />
              </div>

              <div className="flex flex-col gap-1.5">
                <Label htmlFor="edit-unitCost">Costo unitario</Label>
                <Input
                  id="edit-unitCost"
                  name="unitCost"
                  type="number"
                  min={0}
                  step="0.01"
                  defaultValue={item.unitCost}
                  disabled={updateMutation.isPending}
                />
                <FieldError error={apiError} field="unitCost" />
              </div>
            </div>

            <DialogFooter>
              <Button type="submit" disabled={updateMutation.isPending}>
                {updateMutation.isPending ? "Guardando..." : "Guardar cambios"}
              </Button>
            </DialogFooter>
          </form>
        )}
      </DialogContent>
    </Dialog>
  );
}

/** Un campo numerico vacio debe viajar como undefined, no como 0 ni NaN. */
function optionalNumber(value: FormDataEntryValue | null): number | undefined {
  const text = String(value ?? "").trim();
  return text === "" ? undefined : Number(text);
}
```

- [ ] **Step 4: Crear la pantalla con la pestaña Insumos**

`frontend/src/app/(authenticated)/inventory/inventory-view.tsx`. La pestaña *Movimientos* llega en la tarea 8. Aquí el `Tabs` lleva **una sola** pestaña: no se deja ningún marcador de "pendiente" en pantalla, porque una pantalla a medias no se distingue de una pantalla rota. La tarea 8 añade el `TabsTrigger` y su contenido de una vez.

```tsx
"use client";

import { useMemo, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";

import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { Money } from "@/components/shared/money";
import { QueryState } from "@/components/shared/query-state";
import { StatusBadge } from "@/components/shared/status-badge";
import { useSession } from "@/hooks/use-session";
import { ApiError, api } from "@/lib/api";
import { endpoints } from "@/lib/endpoints";
import { queryKeys } from "@/lib/query-keys";
import { cn } from "@/lib/utils";
import type { InventoryItemResponse, UpdateInventoryItemStatusRequest } from "@/lib/types";
import { CreateItemDialog } from "./create-item-dialog";
import { EditItemDialog } from "./edit-item-dialog";

/**
 * Pantalla de inventario. ADMIN administra el catalogo y registra movimientos;
 * KITCHEN solo registra movimientos, porque el alta y la edicion de insumos son
 * endpoints de ADMIN. La interfaz oculta lo que el servidor rechazaria en vez
 * de ofrecer botones que devuelven 403.
 */
export function InventoryView() {
  const { user } = useSession();
  const queryClient = useQueryClient();
  const [editingItem, setEditingItem] = useState<InventoryItemResponse | null>(null);

  const canManageCatalog = user?.role === "ADMIN";

  const itemsQuery = useQuery({
    queryKey: queryKeys.inventory.items(canManageCatalog),
    queryFn: () =>
      api.get<InventoryItemResponse[]>(endpoints.inventory.items(), {
        includeInactive: canManageCatalog,
      }),
  });

  const toggleActiveMutation = useMutation({
    mutationFn: ({ itemId, active }: { itemId: string; active: boolean }) =>
      api.patch<InventoryItemResponse>(endpoints.inventory.updateItemStatus(itemId), {
        active,
      } satisfies UpdateInventoryItemStatusRequest),
    onSuccess: (item) => {
      queryClient.invalidateQueries({ queryKey: queryKeys.inventory.items(true) });
      toast.success(
        item.active ? `Insumo "${item.name}" activado.` : `Insumo "${item.name}" dado de baja.`,
      );
    },
    onError: (error) => {
      toast.error(
        error instanceof ApiError ? error.message : "No se pudo actualizar el insumo.",
      );
    },
  });

  const items = itemsQuery.data ?? [];

  const alertCount = useMemo(
    () => items.filter((item) => item.level !== "OK").length,
    [items],
  );

  /** Valor del inventario: es lo que le da sentido a capturar el costo unitario. */
  const inventoryValue = useMemo(
    () => items.reduce((sum, item) => sum + item.stock * item.unitCost, 0),
    [items],
  );

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-xl font-semibold">Inventario</h1>
        <p className="text-sm text-muted-foreground">
          Insumos, entradas, salidas, mermas y ajustes por conteo fisico.
        </p>
      </div>

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
        <Card>
          <CardHeader>
            <CardDescription>Insumos</CardDescription>
            <CardTitle className="text-2xl">
              {itemsQuery.isLoading ? "…" : items.length}
            </CardTitle>
          </CardHeader>
        </Card>
        <Card>
          <CardHeader>
            <CardDescription>En alerta</CardDescription>
            <CardTitle className="text-2xl">
              {itemsQuery.isLoading ? "…" : alertCount}
            </CardTitle>
          </CardHeader>
        </Card>
        <Card>
          <CardHeader>
            <CardDescription>Valor del inventario</CardDescription>
            <CardTitle className="text-2xl">
              {itemsQuery.isLoading ? "…" : <Money amount={inventoryValue} />}
            </CardTitle>
          </CardHeader>
        </Card>
      </div>

      <Tabs defaultValue="items">
        <TabsList>
          <TabsTrigger value="items">Insumos</TabsTrigger>
        </TabsList>

        <TabsContent value="items" className="flex flex-col gap-4">
          {canManageCatalog && (
            <div className="flex justify-end">
              <CreateItemDialog />
            </div>
          )}
          <Card>
            <CardContent>
              <QueryState
                isLoading={itemsQuery.isLoading}
                error={itemsQuery.error}
                isEmpty={items.length === 0}
                emptyMessage="Todavia no hay insumos. Crea el primero con el boton de arriba."
              >
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead>Nombre</TableHead>
                      <TableHead>Stock</TableHead>
                      <TableHead>Nivel</TableHead>
                      <TableHead>Minimo</TableHead>
                      <TableHead>Costo</TableHead>
                      {canManageCatalog && <TableHead>Alta</TableHead>}
                      <TableHead />
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {items.map((item) => {
                      const isTogglingThisRow =
                        toggleActiveMutation.isPending &&
                        toggleActiveMutation.variables?.itemId === item.id;
                      return (
                        <TableRow
                          key={item.id}
                          className={cn(item.level === "NEGATIVE" && "bg-destructive/10")}
                        >
                          <TableCell>{item.name}</TableCell>
                          <TableCell>
                            {item.stock} {item.unit}
                          </TableCell>
                          <TableCell>
                            <StatusBadge kind="stock" status={item.level} />
                          </TableCell>
                          <TableCell>
                            {item.minStock > 0 ? `${item.minStock} ${item.unit}` : "—"}
                          </TableCell>
                          <TableCell>
                            <Money amount={item.unitCost} />
                          </TableCell>
                          {canManageCatalog && (
                            <TableCell>
                              <Badge variant={item.active ? "secondary" : "outline"}>
                                {item.active ? "Activo" : "Inactivo"}
                              </Badge>
                            </TableCell>
                          )}
                          <TableCell className="flex justify-end gap-2 text-right">
                            {canManageCatalog && (
                              <>
                                <Button
                                  variant="outline"
                                  size="sm"
                                  onClick={() => setEditingItem(item)}
                                >
                                  Editar
                                </Button>
                                <Button
                                  variant={item.active ? "destructive" : "outline"}
                                  size="sm"
                                  disabled={isTogglingThisRow}
                                  onClick={() =>
                                    toggleActiveMutation.mutate({
                                      itemId: item.id,
                                      active: !item.active,
                                    })
                                  }
                                >
                                  {isTogglingThisRow
                                    ? "Guardando..."
                                    : item.active
                                      ? "Dar de baja"
                                      : "Reactivar"}
                                </Button>
                              </>
                            )}
                          </TableCell>
                        </TableRow>
                      );
                    })}
                  </TableBody>
                </Table>
              </QueryState>
            </CardContent>
          </Card>
        </TabsContent>

      </Tabs>

      <EditItemDialog
        key={editingItem?.id ?? "none"}
        item={editingItem}
        onOpenChange={(open) => {
          if (!open) {
            setEditingItem(null);
          }
        }}
      />
    </div>
  );
}
```

**Detalle a no perder:** `key={editingItem?.id ?? "none"}` fuerza el remontaje del diálogo al cambiar de insumo. Sin él, los `defaultValue` conservan los del insumo anterior — es la misma razón por la que `admin-view.tsx:509` lo hace con `EditProductDialog`.

- [ ] **Step 5: Verificar que compila**

```powershell
npm run lint
npm run build
```

- [ ] **Step 6: Verificar en navegador como ADMIN**

1. Entrar a **Inventario**: tres tarjetas en cero y el estado vacío de la tabla.
2. *Nuevo insumo* → nombre `Cebolla`, unidad `kg`, mínimo `5`, costo `32.50`, stock inicial `20`. La fila aparece con `20 kg`, nivel **Suficiente** y el valor del inventario en `$650.00`.
3. Crear `Tomate` / `kg` / mínimo `10` sin stock inicial: nivel **Bajo minimo**, porque `0 <= 10` con un mínimo configurado. La tarjeta *En alerta* marca **1**.
4. Crear `Sal` / `kg` **sin mínimo** ni stock: nivel **Suficiente**, y *En alerta* sigue en **1**. Mismo stock 0 que `Tomate` y distinto nivel: es la regla `minStock > 0` funcionando, la que evita que la lista de alertas nazca llena de ruido.
5. *Editar* en `Cebolla`: cambiar la unidad a `gramo` → debe fallar con el mensaje de unidad bloqueada (ya tiene el movimiento del stock inicial). Cambiar solo el nombre a `Cebolla morada` → guarda bien.
6. *Editar* en `Sal`: cambiar la unidad a `gramo` → guarda bien, porque no tiene movimientos.
7. *Dar de baja* en `Sal`: la fila queda marcada **Inactivo** y sigue visible (la pantalla pide `includeInactive`). *Reactivar* la devuelve a **Activo**.

- [ ] **Step 7: Verificar en navegador como KITCHEN**

Entrar con el usuario `KITCHEN` a **Inventario**: se ven las tarjetas y la tabla, **sin** *Nuevo insumo*, *Editar*, *Dar de baja* ni la columna *Alta*, y los insumos dados de baja no aparecen.

- [ ] **Step 8: Commit**

```bash
git add frontend/src/app/\(authenticated\)/inventory
git commit -m "feat(frontend): add inventory screen with item catalog and stock alerts"
```

---

### Task 8: Registro de movimientos y su historial

**Files:**
- Create: `frontend/src/app/(authenticated)/inventory/register-movement-dialog.tsx`
- Modify: `frontend/src/app/(authenticated)/inventory/inventory-view.tsx`

**Interfaces:**
- Consumes: `RegisterMovementRequest`, `RegisteredMovementResponse`, `InventoryMovementRow`, `MOVEMENT_TYPE_LABELS`, `todayIsoDate`, `formatShortDate`, `formatShortTime`.
- Produces: `RegisterMovementDialog` con props `{ item: InventoryItemResponse | null; onOpenChange: (open: boolean) => void }`.

- [ ] **Step 1: Crear el diálogo de registro**

`frontend/src/app/(authenticated)/inventory/register-movement-dialog.tsx`:

```tsx
"use client";

import { useState } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";

import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Textarea } from "@/components/ui/textarea";
import { FieldError } from "@/components/shared/field-error";
import { ApiError, api } from "@/lib/api";
import { endpoints } from "@/lib/endpoints";
import { queryKeys } from "@/lib/query-keys";
import {
  MOVEMENT_TYPE_LABELS,
  type InventoryItemResponse,
  type InventoryMovementType,
  type RegisterMovementRequest,
  type RegisteredMovementResponse,
} from "@/lib/types";

interface RegisterMovementDialogProps {
  item: InventoryItemResponse | null;
  onOpenChange: (open: boolean) => void;
}

const MOVEMENT_TYPES: InventoryMovementType[] = ["IN", "OUT", "WASTE", "ADJUSTMENT"];

/**
 * Registro de un movimiento. El tipo decide que campo se pide, porque el
 * backend acepta `quantity` en entradas, salidas y mermas, y `countedStock`
 * solo en ajustes -- nunca los dos.
 *
 * En un ajuste se muestra la diferencia estimada contra el stock actual, pero
 * lo que viaja es el conteo: el delta lo calcula el servidor con la fila
 * bloqueada. Si este cliente mandara el delta, lo habria calculado sobre un
 * stock que ya pudo cambiar.
 */
export function RegisterMovementDialog({ item, onOpenChange }: RegisterMovementDialogProps) {
  const queryClient = useQueryClient();
  const [movementType, setMovementType] = useState<InventoryMovementType>("IN");
  const [countedStock, setCountedStock] = useState("");

  const isAdjustment = movementType === "ADJUSTMENT";
  const requiresReason = movementType === "WASTE";

  const registerMutation = useMutation({
    mutationFn: (payload: RegisterMovementRequest) =>
      api.post<RegisteredMovementResponse>(endpoints.inventory.registerMovement(), payload),
    onSuccess: (movement) => {
      queryClient.invalidateQueries({ queryKey: queryKeys.inventory.items(true) });
      queryClient.invalidateQueries({ queryKey: queryKeys.inventory.items() });
      queryClient.invalidateQueries({ queryKey: ["inventory", "movements"] });
      toast.success(
        `${MOVEMENT_TYPE_LABELS[movement.movementType]} registrada: ${movement.quantity} ${item?.unit ?? ""}.`,
      );
      close();
    },
  });

  function close() {
    registerMutation.reset();
    setMovementType("IN");
    setCountedStock("");
    onOpenChange(false);
  }

  function handleSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!item) {
      return;
    }
    const formData = new FormData(event.currentTarget);
    const reason = String(formData.get("reason") ?? "").trim();

    registerMutation.mutate({
      inventoryItemId: item.id,
      movementType,
      quantity: isAdjustment ? undefined : Number(formData.get("quantity")),
      countedStock: isAdjustment ? Number(countedStock) : undefined,
      reason: reason === "" ? undefined : reason,
    });
  }

  const apiError = registerMutation.error instanceof ApiError ? registerMutation.error : null;
  const hasFieldErrors = Boolean(apiError?.errors && Object.keys(apiError.errors).length > 0);
  const estimatedDelta =
    item && isAdjustment && countedStock.trim() !== ""
      ? Number(countedStock) - item.stock
      : null;

  return (
    <Dialog
      open={item !== null}
      onOpenChange={(nextOpen) => {
        if (!nextOpen) {
          close();
        }
      }}
    >
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Registrar movimiento</DialogTitle>
          <DialogDescription>
            {item ? `${item.name} — stock actual: ${item.stock} ${item.unit}` : ""}
          </DialogDescription>
        </DialogHeader>
        {item && (
          <form onSubmit={handleSubmit} className="flex flex-col gap-4">
            {apiError && !hasFieldErrors && (
              <Alert variant="destructive">
                <AlertTitle>No se pudo registrar el movimiento</AlertTitle>
                <AlertDescription>{apiError.message}</AlertDescription>
              </Alert>
            )}

            <div className="flex flex-col gap-1.5">
              <Label htmlFor="movementType">Tipo</Label>
              <Select
                value={movementType}
                onValueChange={(value) => setMovementType(value as InventoryMovementType)}
                disabled={registerMutation.isPending}
              >
                <SelectTrigger id="movementType" className="w-full">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {MOVEMENT_TYPES.map((type) => (
                    <SelectItem key={type} value={type}>
                      {MOVEMENT_TYPE_LABELS[type]}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>

            {isAdjustment ? (
              <div className="flex flex-col gap-1.5">
                <Label htmlFor="countedStock">Conteo fisico</Label>
                <Input
                  id="countedStock"
                  name="countedStock"
                  type="number"
                  min={0}
                  step="0.001"
                  required
                  value={countedStock}
                  onChange={(event) => setCountedStock(event.target.value)}
                  disabled={registerMutation.isPending}
                />
                <p className="text-xs text-muted-foreground">
                  {estimatedDelta === null
                    ? "Captura cuanto hay en realidad; el sistema calcula la diferencia."
                    : `Diferencia estimada: ${estimatedDelta > 0 ? "+" : ""}${estimatedDelta.toFixed(3)} ${item.unit}. El valor definitivo lo calcula el servidor.`}
                </p>
                <FieldError error={apiError} field="countedStock" />
              </div>
            ) : (
              <div className="flex flex-col gap-1.5">
                <Label htmlFor="quantity">Cantidad ({item.unit})</Label>
                <Input
                  id="quantity"
                  name="quantity"
                  type="number"
                  min={0}
                  step="0.001"
                  required
                  disabled={registerMutation.isPending}
                />
                <FieldError error={apiError} field="quantity" />
              </div>
            )}

            <div className="flex flex-col gap-1.5">
              <Label htmlFor="reason">
                {requiresReason ? "Motivo" : "Motivo (opcional)"}
              </Label>
              <Textarea
                id="reason"
                name="reason"
                maxLength={150}
                required={requiresReason}
                placeholder={requiresReason ? "Se echo a perder, se quemo, se cayo..." : ""}
                disabled={registerMutation.isPending}
              />
              <FieldError error={apiError} field="reason" />
            </div>

            <DialogFooter>
              <Button type="submit" disabled={registerMutation.isPending}>
                {registerMutation.isPending ? "Guardando..." : "Registrar"}
              </Button>
            </DialogFooter>
          </form>
        )}
      </DialogContent>
    </Dialog>
  );
}
```

- [ ] **Step 2: Añadir el botón de registro a la tabla de insumos**

En `inventory-view.tsx`: importar `RegisterMovementDialog`, añadir el estado y el botón, que **sí** se pinta para `KITCHEN`.

Estado nuevo, junto a `editingItem`:

```tsx
  const [movingItem, setMovingItem] = useState<InventoryItemResponse | null>(null);
```

Botón como primer elemento de la celda de acciones, **fuera** del `canManageCatalog`:

```tsx
                          <TableCell className="flex justify-end gap-2 text-right">
                            <Button
                              size="sm"
                              disabled={!item.active}
                              onClick={() => setMovingItem(item)}
                            >
                              Movimiento
                            </Button>
                            {canManageCatalog && (
```

Y el diálogo junto a `EditItemDialog`:

```tsx
      <RegisterMovementDialog
        key={movingItem?.id ?? "none"}
        item={movingItem}
        onOpenChange={(open) => {
          if (!open) {
            setMovingItem(null);
          }
        }}
      />
```

`disabled={!item.active}` refleja el `409` del backend por adelantado: un insumo dado de baja no se mueve.

- [ ] **Step 3: Añadir la pestaña Movimientos**

En `inventory-view.tsx`, agregar el `TabsTrigger` junto al de *Insumos*:

```tsx
        <TabsList>
          <TabsTrigger value="items">Insumos</TabsTrigger>
          <TabsTrigger value="movements">Movimientos</TabsTrigger>
        </TabsList>
```

Y su contenido, después del `TabsContent value="items"` y antes del cierre de `</Tabs>`:

```tsx
        <TabsContent value="movements" className="flex flex-col gap-4">
          <MovementsPanel items={items} />
        </TabsContent>
```

- [ ] **Step 4: Añadir el panel de movimientos al final del archivo**

Al final de `inventory-view.tsx`, después de `InventoryView`:

```tsx
interface MovementsPanelProps {
  items: InventoryItemResponse[];
}

/** Valor centinela para "todos los insumos": Radix Select no admite value="". */
const ALL_ITEMS = "all";

/**
 * Historial del ledger. El rango se resuelve en la zona del negocio del lado
 * del servidor; aqui solo se mandan las fechas. Mismo formulario no controlado
 * que el historial de ventas.
 */
function MovementsPanel({ items }: MovementsPanelProps) {
  const today = todayIsoDate();
  const [range, setRange] = useState({ from: today, to: today });
  const [itemId, setItemId] = useState<string>(ALL_ITEMS);

  const movementsQuery = useQuery({
    queryKey: queryKeys.inventory.movements(
      range.from,
      range.to,
      itemId === ALL_ITEMS ? undefined : itemId,
    ),
    queryFn: () =>
      api.get<InventoryMovementRow[]>(endpoints.inventory.movements(), {
        from: range.from,
        to: range.to,
        itemId: itemId === ALL_ITEMS ? undefined : itemId,
      }),
  });

  function handleRangeSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const formData = new FormData(event.currentTarget);
    setRange({
      from: String(formData.get("from") || today),
      to: String(formData.get("to") || today),
    });
  }

  const movements = movementsQuery.data ?? [];

  return (
    <>
      <Card>
        <CardHeader>
          <CardTitle>Filtros</CardTitle>
        </CardHeader>
        <CardContent>
          <form onSubmit={handleRangeSubmit} className="flex flex-wrap items-end gap-3">
            <div className="flex flex-col gap-1.5">
              <Label htmlFor="movements-from">Desde</Label>
              <Input id="movements-from" name="from" type="date" defaultValue={today} required />
            </div>
            <div className="flex flex-col gap-1.5">
              <Label htmlFor="movements-to">Hasta</Label>
              <Input id="movements-to" name="to" type="date" defaultValue={today} required />
            </div>
            <div className="flex flex-col gap-1.5">
              <Label htmlFor="movements-item">Insumo</Label>
              <Select value={itemId} onValueChange={setItemId}>
                <SelectTrigger id="movements-item" className="w-56">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value={ALL_ITEMS}>Todos</SelectItem>
                  {items.map((item) => (
                    <SelectItem key={item.id} value={item.id}>
                      {item.name}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
            <Button type="submit">Buscar</Button>
          </form>
        </CardContent>
      </Card>

      <Card>
        <CardContent>
          <QueryState
            isLoading={movementsQuery.isLoading}
            error={movementsQuery.error}
            isEmpty={movements.length === 0}
            emptyMessage="No hay movimientos en el rango seleccionado."
          >
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Fecha</TableHead>
                  <TableHead>Insumo</TableHead>
                  <TableHead>Tipo</TableHead>
                  <TableHead>Cantidad</TableHead>
                  <TableHead>Motivo</TableHead>
                  <TableHead>Registro</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {movements.map((movement) => (
                  <TableRow key={movement.id}>
                    <TableCell>
                      {formatShortDate(movement.createdAt)} {formatShortTime(movement.createdAt)}
                    </TableCell>
                    <TableCell>{movement.itemName}</TableCell>
                    <TableCell>
                      <StatusBadge kind="movement" status={movement.movementType} />
                    </TableCell>
                    <TableCell
                      className={cn(movement.quantity < 0 ? "text-destructive" : "text-foreground")}
                    >
                      {movement.quantity > 0 ? "+" : ""}
                      {movement.quantity} {movement.unit}
                    </TableCell>
                    <TableCell>{movement.reason ?? "—"}</TableCell>
                    <TableCell>{movement.userName ?? "—"}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </QueryState>
        </CardContent>
      </Card>
    </>
  );
}
```

Imports que hay que añadir arriba del archivo:

```tsx
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { formatShortDate, formatShortTime, todayIsoDate } from "@/lib/format";
import type { InventoryMovementRow } from "@/lib/types";
import { RegisterMovementDialog } from "./register-movement-dialog";
```

- [ ] **Step 5: Verificar que compila**

```powershell
npm run lint
npm run build
```

- [ ] **Step 6: Verificar el recorrido completo en navegador como ADMIN**

Partiendo de `Cebolla` con `20 kg`:

1. *Movimiento* en `Cebolla` → **Entrada** `5` → el stock queda en `25 kg`, la pestaña *Movimientos* muestra la fila `+5 kg` de tipo **Entrada**, y el valor del inventario sube. La tabla de insumos se actualizó sin recargar la página.
2. **Salida** `3` → stock `22 kg`, fila `-3 kg`.
3. **Merma** sin motivo → el botón no envía (el `Textarea` es `required`). Con motivo `Se echo a perder` → stock `20 kg` y el motivo aparece en el historial.
4. **Ajuste**: escribir `18` → el texto de ayuda dice *Diferencia estimada: -2.000 kg*. Registrar → la fila queda como `-2 kg` con motivo `Conteo fisico: 18` y el stock en `18 kg`.
5. **Ajuste** con el conteo igual al stock (`18`) → error visible con el mensaje de que no hay nada que ajustar.
6. **Salida** `100` → stock `-82 kg`, nivel **Negativo** en rojo y la fila de la tabla con fondo tenue. La operación **no** se rechaza: es el comportamiento decidido.
7. Filtrar *Movimientos* por insumo `Tomate` → la lista queda vacía. Volver a *Todos* → reaparecen las filas de `Cebolla`.
8. Poner el rango en un mes pasado → lista vacía. Volver a hoy → reaparecen.
9. Dar de baja `Cebolla` desde la pestaña *Insumos*: el botón *Movimiento* de esa fila queda deshabilitado.

- [ ] **Step 7: Verificar como KITCHEN**

Entrar con el usuario `KITCHEN`: registrar una **Merma** con motivo sobre un insumo activo funciona, y la pestaña *Movimientos* muestra la fila con su nombre en la columna *Registro*. Siguen sin aparecer *Nuevo insumo*, *Editar* ni *Dar de baja*.

- [ ] **Step 8: Commit**

```bash
git add frontend/src/app/\(authenticated\)/inventory
git commit -m "feat(frontend): register inventory movements and browse the ledger"
```

---

### Task 9: Documentación y verificación final

**Files:**
- Modify: `README.md:70`
- Modify: `docs/glosario-es-en.md`

- [ ] **Step 1: Correr la suite completa y contar**

```powershell
.\mvnw.cmd -B verify
```

Anotar el número exacto de pruebas que reporta Maven (`Tests run: N`). El plan espera **172**; si difiere, usar el número real en el paso siguiente.

- [ ] **Step 2: Actualizar el README**

`README.md:70` dice hoy:

```
backend/    Spring Boot · Maven Wrapper · 120 tests · 25 rutas de API
```

El conteo de pruebas ya estaba desfasado antes de este trabajo. Reemplazar por el número real del paso 1 y **31** rutas (25 + 6 de inventario):

```
backend/    Spring Boot · Maven Wrapper · 172 tests · 31 rutas de API
```

- [ ] **Step 3: Añadir las entidades al glosario**

En `docs/glosario-es-en.md`, en la tabla *Entidades del dominio*, después de la fila de `Ticket`:

```markdown
| Insumo | `InventoryItem` | `inventory_items` |
| Movimiento de inventario | `InventoryMovement` | `inventory_movements` |
```

Y un párrafo después de la tabla:

```markdown
`InventoryMovement` no se llama `StockMovement` porque la tabla del esquema original ya se llamaba `inventory_movements`, y renombrar el concepto en el código para que dejara de coincidir con la tabla habría creado exactamente la fricción que este glosario existe para evitar.
```

- [ ] **Step 4: Verificar el frontend una última vez**

```powershell
npm run lint
npm run build
```

- [ ] **Step 5: Confirmar que no quedó nada sin commitear**

```bash
git status --short
```

Esperado: sin cambios pendientes salvo los de este paso.

- [ ] **Step 6: Commit**

```bash
git add README.md docs/glosario-es-en.md
git commit -m "docs: bring the README counts and the glossary up to the inventory module"
```

---

## Autorevisión del plan

**Cobertura del spec, sección por sección:**

| Sección del spec | Tarea |
|---|---|
| §2 Migración `V7` | 1 |
| §3 Dominio: entidades, enum del signo, `apply`, `level()` | 1 |
| §3 Concurrencia: `findByIdForUpdate` | 5 |
| §4 `InventoryItemService`: listar, crear, corregir, baja | 2 (base) + 4 (stock inicial, unidad fija) |
| §4 `InventoryMovementService`: registrar, ajuste, motivos, listar | 3 |
| §5 Seis rutas con sus roles | 2 (cuatro) + 3 (dos) |
| §5 `@ValidMovementRequest` | 3 |
| §5 Respuestas y proyección con joins | 2, 3 |
| §6 Sección `/inventory`, dos pestañas, tres diálogos | 6, 7, 8 |
| §6 Tarjetas, fila negativa, roles en la UI | 7 |
| §6 `status-badge` con dos familias nuevas | 6 |
| §6 Navegación `ADMIN` + `KITCHEN` | 6 |
| §7 Cuatro clases de prueba | 1, 2, 3, 5 |
| §7 Sin prueba de aislamiento nueva | — (decisión respetada) |

Sin huecos.

**Consistencia de firmas:** `createItem` cambia de firma en la tarea 4 (gana `initialStock` y `userId`); está declarado en su bloque *Interfaces* y el llamador del controlador se actualiza en el mismo paso. `InventoryMovementService.register` mantiene su firma de seis parámetros en las tareas 3, 4 y 5; la tarea 5 solo cambia una línea de su cuerpo. `MovementResponse` se renombró a `RegisteredMovementResponse` y vive en `web/dto`, como el resto de los DTOs.

**Un riesgo que vale nombrar:** la prueba de la tarea 5 puede pasar por casualidad si los dos hilos se serializan solos, por eso el paso 2 pide correrla varias veces antes de dar el problema por reproducido. Una prueba de concurrencia que nunca se vio fallar no prueba nada.
