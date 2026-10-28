package com.callejon9.order.web;

import com.callejon9.catalog.domain.Product;
import com.callejon9.catalog.repository.CategoryRepository;
import com.callejon9.catalog.repository.ProductRepository;
import com.callejon9.catalog.web.dto.CategoryResponse;
import com.callejon9.catalog.web.dto.ProductResponse;
import com.callejon9.order.domain.KitchenItemStatus;
import com.callejon9.order.domain.Order;
import com.callejon9.order.domain.OrderItem;
import com.callejon9.order.domain.OrderStatus;
import com.callejon9.order.event.OrderSentToKitchenEvent;
import com.callejon9.order.repository.OrderItemRepository;
import com.callejon9.order.repository.OrderRepository;
import com.callejon9.order.service.FolioGenerator;
import com.callejon9.order.service.OrderWithItems;
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
import com.callejon9.tenancy.TenantContext;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints publicos para autoservicio del comensal mediante codigo QR en mesa.
 * No requiere sesion ni credenciales: el slug identifica al tenant y
 * {@link TenantContext#callAs} encapsula las consultas dentro del tenant_id correspondiente.
 *
 * <p>Las transacciones se abren con {@link TransactionTemplate} DENTRO de
 * {@code callAs}, igual que en {@code AuthService}: el tenant se conoce a mitad
 * del metodo, y {@code TenantAwareTransactionManager} lo publica a PostgreSQL
 * solo al abrir la transaccion. Con {@code @Transactional} en el metodo, la
 * transaccion se abriria sin tenant y RLS no devolveria ninguna fila.
 */
@RestController
@RequestMapping("/api/v1/public")
public class PublicMenuController {

    private static final Logger log = LoggerFactory.getLogger(PublicMenuController.class);

    private final TenantRepository tenantRepository;
    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final RestaurantTableRepository tableRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final FolioGenerator folioGenerator;
    private final ApplicationEventPublisher eventPublisher;
    private final TransactionTemplate transactionTemplate;

    public PublicMenuController(
            TenantRepository tenantRepository,
            CategoryRepository categoryRepository,
            ProductRepository productRepository,
            RestaurantTableRepository tableRepository,
            OrderRepository orderRepository,
            OrderItemRepository orderItemRepository,
            FolioGenerator folioGenerator,
            ApplicationEventPublisher eventPublisher,
            TransactionTemplate transactionTemplate) {
        this.tenantRepository = tenantRepository;
        this.categoryRepository = categoryRepository;
        this.productRepository = productRepository;
        this.tableRepository = tableRepository;
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.folioGenerator = folioGenerator;
        this.eventPublisher = eventPublisher;
        this.transactionTemplate = transactionTemplate;
    }

    @GetMapping("/{slug}/catalog")
    public PublicCatalogResponse getCatalog(@PathVariable String slug) {
        Tenant tenant = tenantRepository.findBySlug(slug)
                .orElseThrow(() -> new ResourceNotFoundException("El restaurante '" + slug + "' no existe."));

        return TenantContext.callAs(tenant.getId(), () -> transactionTemplate.execute(status -> {
            List<CategoryResponse> categories = categoryRepository.findAllByOrderBySortOrderAscNameAsc().stream()
                    .map(CategoryResponse::from)
                    .toList();
            List<ProductResponse> products = productRepository.findByActiveTrueOrderByName().stream()
                    .map(ProductResponse::from)
                    .toList();

            return new PublicCatalogResponse(tenant.getName(), tenant.getSlug(), categories, products);
        }));
    }

    @PostMapping("/{slug}/tables/{tableNumber}/orders")
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse createTableOrder(
            @PathVariable String slug,
            @PathVariable int tableNumber,
            @Valid @RequestBody AddOrderItemsRequest request) {
        Tenant tenant = tenantRepository.findBySlug(slug)
                .orElseThrow(() -> new ResourceNotFoundException("El restaurante '" + slug + "' no existe."));

        return TenantContext.callAs(tenant.getId(), () -> transactionTemplate.execute(status -> {
            RestaurantTable table = tableRepository.findByNumber(tableNumber)
                    .orElseThrow(() -> new ResourceNotFoundException("La mesa " + tableNumber + " no existe."));

            if (!table.isActive()) {
                throw new BusinessRuleException("La mesa " + tableNumber + " esta inactiva.");
            }

            List<OrderStatus> openStatuses = List.of(OrderStatus.NEW, OrderStatus.SENT, OrderStatus.READY);
            Order order = orderRepository.findFirstByTableIdAndStatusInOrderByOpenedAtDesc(table.getId(), openStatuses)
                    .orElseGet(() -> {
                        if (table.getStatus() == TableStatus.FREE) {
                            table.setStatus(TableStatus.OCCUPIED);
                            tableRepository.save(table);
                        }
                        Instant now = Instant.now();
                        Order newOrder = Order.builder()
                                .folio(folioGenerator.next())
                                .tableId(table.getId())
                                .guestCount(1)
                                .status(OrderStatus.NEW)
                                .total(BigDecimal.ZERO)
                                .openedAt(now)
                                .build();
                        return orderRepository.save(newOrder);
                    });

            Instant now = Instant.now();
            List<OrderItem> itemsToSave = request.items().stream()
                    .map(itemReq -> {
                        Product product = productRepository.findById(itemReq.productId())
                                .orElseThrow(() -> new ResourceNotFoundException("El producto no existe."));
                        if (!product.isActive()) {
                            throw new BusinessRuleException("El producto " + product.getName() + " no esta disponible.");
                        }
                        return OrderItem.builder()
                                .orderId(order.getId())
                                .productId(product.getId())
                                .productName(product.getName())
                                .unitPrice(product.getPrice())
                                .quantity(itemReq.quantity())
                                .kitchenStatus(KitchenItemStatus.PENDING)
                                .notes(itemReq.notes())
                                .orderedAt(now)
                                .build();
                    })
                    .toList();

            orderItemRepository.saveAll(itemsToSave);

            List<OrderItem> allItems = orderItemRepository.findByOrderIdOrderByOrderedAt(order.getId());
            BigDecimal total = allItems.stream()
                    .map(it -> it.getUnitPrice().multiply(BigDecimal.valueOf(it.getQuantity())))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            order.setTotal(total);
            order.setStatus(OrderStatus.SENT);
            if (order.getSentToKitchenAt() == null) {
                order.setSentToKitchenAt(now);
            }
            orderRepository.save(order);

            OrderWithItems orderWithItems = new OrderWithItems(order, allItems);
            eventPublisher.publishEvent(new OrderSentToKitchenEvent(tenant.getId(), OrderResponse.from(orderWithItems)));

            log.info("Comanda auto-pedido creada por QR: folio={} tenant={} mesa={} total={}",
                    order.getFolio(), slug, tableNumber, total);

            return OrderResponse.from(orderWithItems);
        }));
    }

    @PostMapping("/{slug}/tables/{tableNumber}/call-waiter")
    public Map<String, Object> callWaiter(
            @PathVariable String slug,
            @PathVariable int tableNumber) {
        Tenant tenant = tenantRepository.findBySlug(slug)
                .orElseThrow(() -> new ResourceNotFoundException("El restaurante '" + slug + "' no existe."));

        return TenantContext.callAs(tenant.getId(), () -> transactionTemplate.execute(status -> {
            RestaurantTable table = tableRepository.findByNumber(tableNumber)
                    .orElseThrow(() -> new ResourceNotFoundException("La mesa " + tableNumber + " no existe."));
            log.info("Notificacion de llamada a mesero: tenant={} mesa={}", slug, table.getNumber());
            return Map.<String, Object>of(
                    "success", true,
                    "message", "Mesero notificado para la Mesa " + tableNumber,
                    "tableNumber", tableNumber
            );
        }));
    }
}
