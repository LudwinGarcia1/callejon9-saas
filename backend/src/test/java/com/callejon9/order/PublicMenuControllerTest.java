package com.callejon9.order;

import com.callejon9.catalog.domain.Category;
import com.callejon9.catalog.domain.Product;
import com.callejon9.catalog.repository.CategoryRepository;
import com.callejon9.catalog.repository.ProductRepository;
import com.callejon9.order.domain.Order;
import com.callejon9.order.domain.OrderStatus;
import com.callejon9.order.event.OrderSentToKitchenEvent;
import com.callejon9.order.event.WaiterCalledEvent;
import com.callejon9.order.repository.OrderItemRepository;
import com.callejon9.order.repository.OrderRepository;
import com.callejon9.order.service.FolioGenerator;
import com.callejon9.order.service.WaiterCallThrottle;
import com.callejon9.order.web.PublicMenuController;
import com.callejon9.order.web.dto.AddOrderItemRequest;
import com.callejon9.order.web.dto.AddOrderItemsRequest;
import com.callejon9.order.web.dto.OrderResponse;
import com.callejon9.order.web.dto.PublicCatalogResponse;
import com.callejon9.platform.tenant.domain.Tenant;
import com.callejon9.platform.tenant.repository.TenantRepository;
import com.callejon9.shared.error.BusinessRuleException;
import com.callejon9.shared.error.ResourceNotFoundException;
import com.callejon9.table.domain.RestaurantTable;
import com.callejon9.table.domain.TableStatus;
import com.callejon9.table.repository.RestaurantTableRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PublicMenuControllerTest {

    @Mock
    private TenantRepository tenantRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private RestaurantTableRepository tableRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderItemRepository orderItemRepository;

    @Mock
    private FolioGenerator folioGenerator;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private PlatformTransactionManager transactionManager;

    private PublicMenuController controller;

    private final UUID tenantId = UUID.randomUUID();
    private final String slug = "los-taquitos";

    @BeforeEach
    void setUp() {
        controller = new PublicMenuController(
                tenantRepository,
                categoryRepository,
                productRepository,
                tableRepository,
                orderRepository,
                orderItemRepository,
                folioGenerator,
                eventPublisher,
                new TransactionTemplate(transactionManager),
                new WaiterCallThrottle()
        );
    }

    @Test
    @DisplayName("getCatalog retorna categorías y productos activos para un slug válido")
    void getCatalog_Success() {
        Tenant tenant = Tenant.builder().slug(slug).name("Los Taquitos").build();
        tenant.setId(tenantId);
        when(tenantRepository.findBySlug(slug)).thenReturn(Optional.of(tenant));

        Category cat = Category.builder().name("Tacos").sortOrder(1).build();
        cat.setId(UUID.randomUUID());
        cat.setTenantId(tenantId);
        when(categoryRepository.findAllByOrderBySortOrderAscNameAsc()).thenReturn(List.of(cat));

        Product prod = Product.builder().name("Taco Pastor").price(new BigDecimal("25.00")).active(true).build();
        prod.setId(UUID.randomUUID());
        prod.setTenantId(tenantId);
        when(productRepository.findByActiveTrueOrderByName()).thenReturn(List.of(prod));

        PublicCatalogResponse response = controller.getCatalog(slug);

        assertThat(response.restaurantName()).isEqualTo("Los Taquitos");
        assertThat(response.slug()).isEqualTo(slug);
        assertThat(response.categories()).hasSize(1);
        assertThat(response.products()).hasSize(1);
        assertThat(response.products().get(0).name()).isEqualTo("Taco Pastor");
    }

    @Test
    @DisplayName("getCatalog lanza ResourceNotFoundException cuando el slug no existe")
    void getCatalog_NotFound() {
        when(tenantRepository.findBySlug("no-existe")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller.getCatalog("no-existe"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("no existe");
    }

    @Test
    @DisplayName("createTableOrder crea la orden, ocupa la mesa y notifica a cocina")
    void createTableOrder_Success() {
        Tenant tenant = Tenant.builder().slug(slug).name("Los Taquitos").build();
        tenant.setId(tenantId);
        when(tenantRepository.findBySlug(slug)).thenReturn(Optional.of(tenant));

        UUID tableId = UUID.randomUUID();
        RestaurantTable table = RestaurantTable.builder()
                .number(4)
                .capacity(4)
                .status(TableStatus.FREE)
                .active(true)
                .build();
        table.setId(tableId);
        table.setTenantId(tenantId);
        when(tableRepository.findByNumber(4)).thenReturn(Optional.of(table));

        when(orderRepository.findFirstByTableIdAndStatusInOrderByOpenedAtDesc(any(), any()))
                .thenReturn(Optional.empty());

        when(folioGenerator.next()).thenReturn("FOL-202610-004");

        UUID orderId = UUID.randomUUID();
        Order newOrder = Order.builder()
                .folio("FOL-202610-004")
                .tableId(tableId)
                .status(OrderStatus.NEW)
                .total(BigDecimal.ZERO)
                .openedAt(Instant.now())
                .build();
        newOrder.setId(orderId);
        newOrder.setTenantId(tenantId);
        when(orderRepository.save(any(Order.class))).thenReturn(newOrder);

        UUID prodId = UUID.randomUUID();
        Product product = Product.builder()
                .name("Guacamole")
                .price(new BigDecimal("95.00"))
                .active(true)
                .build();
        product.setId(prodId);
        product.setTenantId(tenantId);
        when(productRepository.findById(prodId)).thenReturn(Optional.of(product));

        AddOrderItemsRequest request = new AddOrderItemsRequest(List.of(
                new AddOrderItemRequest(prodId, 2, "Sin cebolla")
        ));

        OrderResponse response = controller.createTableOrder(slug, 4, request);

        assertThat(response).isNotNull();
        assertThat(response.folio()).isEqualTo("FOL-202610-004");
        assertThat(table.getStatus()).isEqualTo(TableStatus.OCCUPIED);

        ArgumentCaptor<OrderSentToKitchenEvent> eventCaptor = ArgumentCaptor.forClass(OrderSentToKitchenEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().tenantId()).isEqualTo(tenantId);
    }

    @Test
    @DisplayName("callWaiter responde confirmación y publica el aviso de la mesa al personal")
    void callWaiter_Success() {
        RestaurantTable table = stubTable(5, true);

        Map<String, Object> result = controller.callWaiter(slug, 5);

        assertThat(result.get("success")).isEqualTo(true);
        assertThat(result.get("tableNumber")).isEqualTo(5);

        ArgumentCaptor<WaiterCalledEvent> eventCaptor = ArgumentCaptor.forClass(WaiterCalledEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().tenantId()).isEqualTo(tenantId);
        assertThat(eventCaptor.getValue().call().tableId()).isEqualTo(table.getId());
        assertThat(eventCaptor.getValue().call().tableNumber()).isEqualTo(5);
        assertThat(eventCaptor.getValue().call().calledAt()).isNotNull();
    }

    @Test
    @DisplayName("callWaiter repetido de la misma mesa responde igual pero avisa una sola vez")
    void callWaiter_RepeatedWithinWindowNotifiesOnce() {
        stubTable(5, true);

        controller.callWaiter(slug, 5);
        Map<String, Object> second = controller.callWaiter(slug, 5);

        assertThat(second.get("success")).isEqualTo(true);
        verify(eventPublisher, times(1)).publishEvent(any(WaiterCalledEvent.class));
    }

    @Test
    @DisplayName("callWaiter de una mesa inactiva se rechaza sin avisar")
    void callWaiter_InactiveTableIsRejected() {
        stubTable(5, false);

        assertThatThrownBy(() -> controller.callWaiter(slug, 5))
                .isInstanceOf(BusinessRuleException.class);
        verify(eventPublisher, never()).publishEvent(any());
    }

    private RestaurantTable stubTable(int number, boolean active) {
        Tenant tenant = Tenant.builder().slug(slug).name("Los Taquitos").build();
        tenant.setId(tenantId);
        when(tenantRepository.findBySlug(slug)).thenReturn(Optional.of(tenant));

        RestaurantTable table = RestaurantTable.builder().number(number).active(active).build();
        table.setId(UUID.randomUUID());
        when(tableRepository.findByNumber(number)).thenReturn(Optional.of(table));
        return table;
    }
}
