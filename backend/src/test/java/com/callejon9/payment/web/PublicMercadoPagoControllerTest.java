package com.callejon9.payment.web;

import com.callejon9.order.domain.KitchenItemStatus;
import com.callejon9.order.domain.Order;
import com.callejon9.order.domain.OrderItem;
import com.callejon9.order.domain.OrderStatus;
import com.callejon9.order.repository.OrderItemRepository;
import com.callejon9.order.repository.OrderRepository;
import com.callejon9.order.web.dto.OrderResponse;
import com.callejon9.payment.service.MercadoPagoService;
import com.callejon9.payment.web.dto.CreatePreferenceRequest;
import com.callejon9.payment.web.dto.PaymentStatusResponse;
import com.callejon9.payment.web.dto.PreferenceResponse;
import com.callejon9.platform.tenant.domain.Tenant;
import com.callejon9.platform.tenant.repository.TenantRepository;
import com.callejon9.table.domain.RestaurantTable;
import com.callejon9.table.repository.RestaurantTableRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("UT · PublicMercadoPagoController")
class PublicMercadoPagoControllerTest {

    private static final UUID TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID ORDER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID TABLE_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final String SLUG = "callejon-9";

    @Mock
    private TenantRepository tenantRepository;

    @Mock
    private RestaurantTableRepository tableRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderItemRepository orderItemRepository;

    @Mock
    private MercadoPagoService mercadoPagoService;

    @Mock
    private TransactionTemplate transactionTemplate;

    private PublicMercadoPagoController controller;

    @BeforeEach
    void setUp() {
        lenient().when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(null);
        });

        controller = new PublicMercadoPagoController(
                tenantRepository,
                tableRepository,
                orderRepository,
                orderItemRepository,
                mercadoPagoService,
                transactionTemplate
        );
    }

    @Test
    @DisplayName("Obtiene comanda activa de la mesa para comensal")
    void getActiveTableOrderSuccess() {
        Tenant tenant = Tenant.builder().name("Callejón 9").slug(SLUG).build();
        tenant.setId(TENANT_ID);

        RestaurantTable table = RestaurantTable.builder().number(3).build();
        table.setId(TABLE_ID);

        Order order = new Order();
        order.setId(ORDER_ID);
        order.setFolio("ORD-0003");
        order.setTableId(TABLE_ID);
        order.setStatus(OrderStatus.SENT);

        OrderItem item = OrderItem.builder()
                .orderId(ORDER_ID)
                .productName("Hamburguesa")
                .unitPrice(new BigDecimal("120.00"))
                .quantity(1)
                .kitchenStatus(KitchenItemStatus.DELIVERED)
                .build();

        when(tenantRepository.findBySlug(SLUG)).thenReturn(Optional.of(tenant));
        when(tableRepository.findByNumber(3)).thenReturn(Optional.of(table));
        when(orderRepository.findFirstByTableIdAndStatusInOrderByOpenedAtDesc(eq(TABLE_ID), any()))
                .thenReturn(Optional.of(order));
        when(orderItemRepository.findByOrderIdOrderByOrderedAt(ORDER_ID)).thenReturn(List.of(item));

        OrderResponse res = controller.getActiveTableOrder(SLUG, 3);

        assertThat(res).isNotNull();
        assertThat(res.id()).isEqualTo(ORDER_ID);
        assertThat(res.folio()).isEqualTo("ORD-0003");
        assertThat(res.items()).hasSize(1);
    }

    @Test
    @DisplayName("Crea preferencia pública de cobro desde la mesa QR")
    void createTablePreferenceSuccess() {
        Tenant tenant = Tenant.builder().name("Callejón 9").slug(SLUG).build();
        tenant.setId(TENANT_ID);

        RestaurantTable table = RestaurantTable.builder().number(3).build();
        table.setId(TABLE_ID);

        Order order = new Order();
        order.setId(ORDER_ID);
        order.setTableId(TABLE_ID);
        order.setStatus(OrderStatus.SENT);

        when(tenantRepository.findBySlug(SLUG)).thenReturn(Optional.of(tenant));
        when(tableRepository.findByNumber(3)).thenReturn(Optional.of(table));
        when(orderRepository.findFirstByTableIdAndStatusInOrderByOpenedAtDesc(eq(TABLE_ID), any()))
                .thenReturn(Optional.of(order));

        PreferenceResponse mockResp = new PreferenceResponse(
                "PREF-PUB", "https://mp.com/init", "https://mp.com/sandbox", "https://mp.com/qr",
                TENANT_ID + ":" + ORDER_ID, new BigDecimal("132.00"), "ORD-0003", true
        );

        when(mercadoPagoService.createPreferenceForOrder(
                eq(TENANT_ID), eq(ORDER_ID), eq(new BigDecimal("10.0")), eq("Callejón 9"), eq(SLUG), eq(3)
        )).thenReturn(mockResp);

        CreatePreferenceRequest req = new CreatePreferenceRequest(new BigDecimal("10.0"));
        PreferenceResponse res = controller.createTablePreference(SLUG, 3, req);

        assertThat(res).isNotNull();
        assertThat(res.preferenceId()).isEqualTo("PREF-PUB");
        verify(mercadoPagoService).createPreferenceForOrder(
                eq(TENANT_ID), eq(ORDER_ID), eq(new BigDecimal("10.0")), eq("Callejón 9"), eq(SLUG), eq(3)
        );
    }

    @Test
    @DisplayName("Consulta estado público de pago de la mesa")
    void getTablePaymentStatusSuccess() {
        Tenant tenant = Tenant.builder().name("Callejón 9").slug(SLUG).build();
        tenant.setId(TENANT_ID);

        RestaurantTable table = RestaurantTable.builder().number(3).build();
        table.setId(TABLE_ID);

        Order order = new Order();
        order.setId(ORDER_ID);

        when(tenantRepository.findBySlug(SLUG)).thenReturn(Optional.of(tenant));
        when(tableRepository.findByNumber(3)).thenReturn(Optional.of(table));
        when(orderRepository.findFirstByTableIdAndStatusInOrderByOpenedAtDesc(eq(TABLE_ID), any()))
                .thenReturn(Optional.of(order));

        PaymentStatusResponse mockStatus = new PaymentStatusResponse(
                ORDER_ID, "ORD-0003", "PAID", true, "TCK-PUB", Instant.now()
        );
        when(mercadoPagoService.getPaymentStatus(TENANT_ID, ORDER_ID)).thenReturn(mockStatus);

        PaymentStatusResponse res = controller.getTablePaymentStatus(SLUG, 3);

        assertThat(res.paid()).isTrue();
        assertThat(res.ticketFolio()).isEqualTo("TCK-PUB");
    }

    @Test
    @DisplayName("Simula aprobación pública de la mesa")
    void simulateTableApprovalSuccess() {
        Tenant tenant = Tenant.builder().name("Callejón 9").slug(SLUG).build();
        tenant.setId(TENANT_ID);

        RestaurantTable table = RestaurantTable.builder().number(3).build();
        table.setId(TABLE_ID);

        Order order = new Order();
        order.setId(ORDER_ID);

        when(tenantRepository.findBySlug(SLUG)).thenReturn(Optional.of(tenant));
        when(tableRepository.findByNumber(3)).thenReturn(Optional.of(table));
        when(orderRepository.findFirstByTableIdAndStatusInOrderByOpenedAtDesc(eq(TABLE_ID), any()))
                .thenReturn(Optional.of(order));

        PaymentStatusResponse mockStatus = new PaymentStatusResponse(
                ORDER_ID, "ORD-0003", "PAID", true, "TCK-SIM", Instant.now()
        );
        when(mercadoPagoService.simulateApproval(TENANT_ID, ORDER_ID, new BigDecimal("10.0"))).thenReturn(mockStatus);

        CreatePreferenceRequest req = new CreatePreferenceRequest(new BigDecimal("10.0"));
        PaymentStatusResponse res = controller.simulateTableApproval(SLUG, 3, req);

        assertThat(res.paid()).isTrue();
        assertThat(res.ticketFolio()).isEqualTo("TCK-SIM");
    }

    @Test
    @DisplayName("syncTablePayment delega a mercadoPagoService con tenant de la mesa")
    void syncTablePaymentSuccess() {
        Tenant tenant = Tenant.builder().name("Callejón 9").slug(SLUG).build();
        tenant.setId(TENANT_ID);

        RestaurantTable table = RestaurantTable.builder().number(3).build();
        table.setId(TABLE_ID);

        Order order = new Order();
        order.setId(ORDER_ID);

        when(tenantRepository.findBySlug(SLUG)).thenReturn(Optional.of(tenant));
        when(tableRepository.findByNumber(3)).thenReturn(Optional.of(table));
        when(orderRepository.findFirstByTableIdAndStatusInOrderByOpenedAtDesc(eq(TABLE_ID), any()))
                .thenReturn(Optional.of(order));

        PaymentStatusResponse mockStatus = new PaymentStatusResponse(
                ORDER_ID, "ORD-0003", "PAID", true, "TCK-SYNC", Instant.now()
        );
        when(mercadoPagoService.syncPayment(TENANT_ID, ORDER_ID, "PAY-99", "ext-ref")).thenReturn(mockStatus);

        com.callejon9.payment.web.dto.SyncPaymentRequest req = new com.callejon9.payment.web.dto.SyncPaymentRequest("PAY-99", "ext-ref");
        PaymentStatusResponse res = controller.syncTablePayment(SLUG, 3, req);

        assertThat(res.paid()).isTrue();
        assertThat(res.ticketFolio()).isEqualTo("TCK-SYNC");
        verify(mercadoPagoService).syncPayment(TENANT_ID, ORDER_ID, "PAY-99", "ext-ref");
    }
}