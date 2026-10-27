package com.callejon9.cashier.service;

import com.callejon9.cashier.domain.CashMovement;
import com.callejon9.cashier.domain.CashMovementType;
import com.callejon9.cashier.domain.CashSession;
import com.callejon9.cashier.repository.CashMovementRepository;
import com.callejon9.cashier.repository.CashSessionRepository;
import com.callejon9.cashier.web.dto.CashMovementRequest;
import com.callejon9.cashier.web.dto.CashMovementResponse;
import com.callejon9.cashier.web.dto.CashPaymentMethodSummary;
import com.callejon9.cashier.web.dto.CashPaymentTotals;
import com.callejon9.cashier.web.dto.CashSessionListItem;
import com.callejon9.cashier.web.dto.CashSessionListResponse;
import com.callejon9.cashier.web.dto.CashSessionResponse;
import com.callejon9.cashier.web.dto.CloseCashSessionRequest;
import com.callejon9.cashier.web.dto.OpenCashSessionRequest;
import com.callejon9.sale.repository.PaymentRepository;
import com.callejon9.shared.error.BusinessRuleException;
import com.callejon9.shared.error.ResourceNotFoundException;
import com.callejon9.user.domain.User;
import com.callejon9.user.repository.UserRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CashSessionService {

    private static final BigDecimal ZERO = new BigDecimal("0.00");

    private final CashSessionRepository cashSessionRepository;
    private final CashMovementRepository cashMovementRepository;
    private final PaymentRepository paymentRepository;
    private final UserRepository userRepository;

    public CashSessionService(
            CashSessionRepository cashSessionRepository,
            CashMovementRepository cashMovementRepository,
            PaymentRepository paymentRepository,
            UserRepository userRepository) {
        this.cashSessionRepository = cashSessionRepository;
        this.cashMovementRepository = cashMovementRepository;
        this.paymentRepository = paymentRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public CashSessionResponse open(UUID cashierId, OpenCashSessionRequest request) {
        if (cashSessionRepository.findByCashierIdAndClosedAtIsNull(cashierId).isPresent()) {
            throw new BusinessRuleException("Ya tienes un turno de caja abierto.");
        }
        CashSession session = cashSessionRepository.saveAndFlush(CashSession.builder()
                .cashierId(cashierId)
                .openingFund(money(request.openingFund()))
                .openedAt(Instant.now())
                .build());
        return response(session);
    }

    @Transactional(readOnly = true)
    public CashSessionListResponse list(UUID viewerId, boolean administrator) {
        List<CashSession> sessions = administrator
                ? cashSessionRepository.findAllByOrderByOpenedAtDesc()
                : cashSessionRepository.findAllByCashierIdOrderByOpenedAtDesc(viewerId);
        List<UUID> cashierIds = sessions.stream().map(CashSession::getCashierId).distinct().toList();
        Map<UUID, User> cashiers = userRepository.findAllById(cashierIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        return new CashSessionListResponse(sessions.stream()
                .map(session -> listItem(session, cashiers.get(session.getCashierId())))
                .toList());
    }

    @Transactional(readOnly = true)
    public CashSessionResponse get(UUID sessionId, UUID viewerId, boolean administrator) {
        CashSession session = findVisibleSession(sessionId, viewerId, administrator);
        return response(session);
    }

    @Transactional
    public CashSessionResponse closeOwn(
            UUID sessionId, UUID cashierId, CloseCashSessionRequest request) {
        CashSession session = lockSession(sessionId);
        requireOwner(session, cashierId);
        requireOpen(session);
        close(session, cashierId, request.countedCash(), optionalReason(request.reason()));
        return response(cashSessionRepository.saveAndFlush(session));
    }

    @Transactional
    public CashSessionResponse forceClose(
            UUID sessionId, UUID administratorId, CloseCashSessionRequest request) {
        String reason = request.reason() == null ? "" : request.reason().trim();
        if (reason.isBlank()) {
            throw new BusinessRuleException("Indica el motivo del cierre forzado.");
        }
        CashSession session = lockSession(sessionId);
        requireOpen(session);
        close(session, administratorId, request.countedCash(), reason);
        return response(cashSessionRepository.saveAndFlush(session));
    }

    @Transactional
    public CashSessionResponse addMovement(
            UUID sessionId, UUID actorId, boolean administrator, CashMovementRequest request) {
        CashSession session = lockSession(sessionId);
        if (!administrator) {
            requireOwner(session, actorId);
        }
        requireOpen(session);

        BigDecimal amount = money(request.amount());
        if (request.type() == CashMovementType.WITHDRAWAL
                && amount.compareTo(expectedCash(session)) > 0) {
            throw new BusinessRuleException("El retiro excede el efectivo esperado en caja.");
        }
        cashMovementRepository.saveAndFlush(CashMovement.builder()
                .cashSessionId(session.getId())
                .movementType(request.type())
                .amount(amount)
                .reason(request.reason().trim())
                .recordedBy(actorId)
                .build());
        return response(session);
    }

    private void close(CashSession session, UUID closedBy, BigDecimal countedCash, String reason) {
        BigDecimal expected = expectedCash(session);
        BigDecimal counted = money(countedCash);
        session.setClosedAt(Instant.now());
        session.setClosedBy(closedBy);
        session.setExpectedCash(expected);
        session.setCountedCash(counted);
        session.setDifference(CashDrawerCalculator.difference(expected, counted));
        session.setClosureReason(reason);
    }

    private BigDecimal expectedCash(CashSession session) {
        CashPaymentTotals payments = paymentRepository.cashTotalsForSession(session.getId());
        BigDecimal received = payments == null ? ZERO : zeroIfNull(payments.receivedAmount());
        BigDecimal change = payments == null ? ZERO : zeroIfNull(payments.changeAmount());
        BigDecimal deposits = zeroIfNull(cashMovementRepository.sumAmountByCashSessionIdAndMovementType(
                session.getId(), CashMovementType.DEPOSIT));
        BigDecimal withdrawals = zeroIfNull(cashMovementRepository.sumAmountByCashSessionIdAndMovementType(
                session.getId(), CashMovementType.WITHDRAWAL));
        return CashDrawerCalculator.expectedCash(
                session.getOpeningFund(), received, change, deposits, withdrawals);
    }

    private CashSessionResponse response(CashSession session) {
        String cashierName = userRepository.findById(session.getCashierId())
                .map(User::getFullName).orElse("Usuario dado de baja");
        List<CashPaymentMethodSummary> paymentSummary =
                paymentRepository.paymentSummaryForSession(session.getId());
        List<CashMovementResponse> movements = cashMovementRepository
                .findAllByCashSessionIdOrderByCreatedAtDesc(session.getId()).stream()
                .map(movement -> new CashMovementResponse(
                        movement.getId(), movement.getMovementType(), movement.getAmount(),
                        movement.getReason(), movement.getRecordedBy(), movement.getCreatedAt()))
                .toList();
        BigDecimal expectedCash = session.isOpen() ? expectedCash(session) : session.getExpectedCash();
        return new CashSessionResponse(
                session.getId(), session.getCashierId(), cashierName, session.getOpeningFund(),
                session.getOpenedAt(), session.getClosedAt(), session.getClosedBy(),
                expectedCash, session.getCountedCash(), session.getDifference(),
                session.getClosureReason(), paymentSummary, movements);
    }

    private CashSessionListItem listItem(CashSession session, User cashier) {
        return new CashSessionListItem(
                session.getId(), session.getCashierId(),
                cashier == null ? "Usuario dado de baja" : cashier.getFullName(),
                session.getOpeningFund(), session.getOpenedAt(), session.getClosedAt(),
                session.getExpectedCash(), session.getCountedCash(), session.getDifference(),
                session.getClosureReason());
    }

    private CashSession findVisibleSession(UUID sessionId, UUID viewerId, boolean administrator) {
        CashSession session = cashSessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("El turno de caja no existe."));
        if (!administrator) {
            requireOwner(session, viewerId);
        }
        return session;
    }

    private CashSession lockSession(UUID sessionId) {
        return cashSessionRepository.findByIdForUpdate(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("El turno de caja no existe."));
    }

    private void requireOwner(CashSession session, UUID viewerId) {
        if (!session.getCashierId().equals(viewerId)) {
            throw new ResourceNotFoundException("El turno de caja no existe.");
        }
    }

    private void requireOpen(CashSession session) {
        if (!session.isOpen()) {
            throw new BusinessRuleException("El turno de caja ya está cerrado.");
        }
    }

    private static String optionalReason(String reason) {
        return reason == null || reason.isBlank() ? null : reason.trim();
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.UNNECESSARY);
    }

    private static BigDecimal zeroIfNull(BigDecimal value) {
        return value == null ? ZERO : value;
    }
}
