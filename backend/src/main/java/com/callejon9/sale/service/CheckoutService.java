package com.callejon9.sale.service;

import com.callejon9.order.domain.Order;
import com.callejon9.order.domain.OrderItem;
import com.callejon9.order.domain.OrderStatus;
import com.callejon9.order.repository.OrderItemRepository;
import com.callejon9.order.repository.OrderRepository;
import com.callejon9.order.service.FolioGenerator;
import com.callejon9.sale.domain.PaymentMethod;
import com.callejon9.sale.domain.Sale;
import com.callejon9.sale.domain.SaleStatus;
import com.callejon9.sale.repository.SaleRepository;
import com.callejon9.shared.error.BusinessRuleException;
import com.callejon9.shared.error.ResourceNotFoundException;
import com.callejon9.table.service.TableService;
import com.callejon9.ticket.domain.Ticket;
import com.callejon9.ticket.domain.TicketItemSnapshot;
import com.callejon9.ticket.repository.TicketRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cierra la cuenta de una orden: crea la venta y su ticket inmutable, marca
 * la orden como PAID y libera la mesa. Todo en una sola transaccion, tal
 * como lo exige la regla de negocio (si algo falla a la mitad, nada de esto
 * debe quedar a medias).
 *
 * El TenantContext ya esta fijado por TenantFilter antes de que la peticion
 * llegue aqui, asi que @Transactional declarativo alcanza (mismo patron que
 * OrderService).
 */
@Service
public class CheckoutService {

    private static final String TICKET_FOLIO_PREFIX = "TCK-";

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final SaleRepository saleRepository;
    private final TicketRepository ticketRepository;
    private final TableService tableService;
    private final FolioGenerator folioGenerator;

    public CheckoutService(
            OrderRepository orderRepository,
            OrderItemRepository orderItemRepository,
            SaleRepository saleRepository,
            TicketRepository ticketRepository,
            TableService tableService,
            FolioGenerator folioGenerator) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.saleRepository = saleRepository;
        this.ticketRepository = ticketRepository;
        this.tableService = tableService;
        this.folioGenerator = folioGenerator;
    }

    @Transactional
    public Ticket checkout(UUID orderId, PaymentMethod paymentMethod, BigDecimal tipPercent, UUID cashierId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("La orden " + orderId + " no existe."));

        if (order.getStatus() == OrderStatus.PAID || order.getStatus() == OrderStatus.CANCELED) {
            throw new BusinessRuleException(
                    "La orden " + order.getFolio() + " ya esta " + order.getStatus()
                            + " y no admite cobro.");
        }

        List<OrderItem> items = orderItemRepository.findByOrderIdOrderByOrderedAt(orderId);
        if (items.isEmpty()) {
            throw new BusinessRuleException(
                    "La orden " + order.getFolio() + " no tiene productos que cobrar.");
        }

        List<TicketItemSnapshot> snapshot = items.stream().map(this::toSnapshot).toList();

        BigDecimal subtotal = snapshot.stream()
                .map(TicketItemSnapshot::subtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal tip = subtotal.multiply(tipPercent)
                .divide(BigDecimal.valueOf(100), 10, RoundingMode.HALF_UP)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal total = subtotal.add(tip).setScale(2, RoundingMode.HALF_UP);

        Instant now = Instant.now();

        Sale sale = saleRepository.save(Sale.builder()
                .orderId(order.getId())
                .tableId(order.getTableId())
                .cashierId(cashierId)
                .status(SaleStatus.COMPLETED)
                .paymentMethod(paymentMethod)
                .subtotal(subtotal)
                .tip(tip)
                .total(total)
                .build());

        Ticket ticket = ticketRepository.save(Ticket.builder()
                .saleId(sale.getId())
                .orderId(order.getId())
                .folio(folioGenerator.next(TICKET_FOLIO_PREFIX))
                .itemsSnapshot(snapshot)
                .subtotal(subtotal)
                .tip(tip)
                .tipPercent(tipPercent.setScale(2, RoundingMode.HALF_UP))
                .total(total)
                .paymentMethod(paymentMethod)
                .closedAt(now)
                .build());

        order.setStatus(OrderStatus.PAID);
        order.setClosedAt(now);
        orderRepository.save(order);

        if (order.getTableId() != null) {
            tableService.free(order.getTableId());
        }

        return ticket;
    }

    private TicketItemSnapshot toSnapshot(OrderItem item) {
        BigDecimal lineSubtotal = item.getUnitPrice()
                .multiply(BigDecimal.valueOf(item.getQuantity()))
                .setScale(2, RoundingMode.HALF_UP);
        return new TicketItemSnapshot(item.getProductId(), item.getProductName(),
                item.getUnitPrice(), item.getQuantity(), lineSubtotal);
    }
}
