package com.callejon9.order.web.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Topes de CAL-112 en el borde exacto: el último valor aceptado y el primero rechazado. */
@DisplayName("UT · topes de las peticiones de órdenes (CAL-112)")
class OrderRequestLimitsTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void createValidator() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        factory.close();
    }

    private static Set<String> invalidFields(Object request) {
        return validator.validate(request).stream()
                .map(ConstraintViolation::getPropertyPath)
                .map(Object::toString)
                .collect(Collectors.toSet());
    }

    private static AddOrderItemRequest line(int quantity, String notes) {
        return new AddOrderItemRequest(UUID.randomUUID(), quantity, notes);
    }

    @Test
    @DisplayName("cantidad: 1 y 99 se aceptan; 0 y 100 se rechazan")
    void quantityLimits() {
        assertThat(invalidFields(line(1, null))).isEmpty();
        assertThat(invalidFields(line(99, null))).isEmpty();
        assertThat(invalidFields(line(0, null))).containsExactly("quantity");
        assertThat(invalidFields(line(100, null))).containsExactly("quantity");
    }

    @Test
    @DisplayName("notas: 500 caracteres se aceptan; 501 se rechazan; sin nota es válido")
    void notesLimits() {
        assertThat(invalidFields(line(1, "a".repeat(500)))).isEmpty();
        assertThat(invalidFields(line(1, "a".repeat(501)))).containsExactly("notes");
        assertThat(invalidFields(line(1, null))).isEmpty();
    }

    @Test
    @DisplayName("lote: 1 y 50 líneas se aceptan; 0 y 51 se rechazan")
    void batchSizeLimits() {
        assertThat(invalidFields(new AddOrderItemsRequest(lines(1)))).isEmpty();
        assertThat(invalidFields(new AddOrderItemsRequest(lines(50)))).isEmpty();
        assertThat(invalidFields(new AddOrderItemsRequest(Collections.emptyList()))).containsExactly("items");
        assertThat(invalidFields(new AddOrderItemsRequest(lines(51)))).containsExactly("items");
    }

    @Test
    @DisplayName("el lote valida cada línea: una cantidad de 100 dentro del lote se rechaza")
    void batchValidatesEachLine() {
        List<AddOrderItemRequest> items = List.of(line(2, null), line(100, null));
        assertThat(invalidFields(new AddOrderItemsRequest(items))).containsExactly("items[1].quantity");
    }

    @Test
    @DisplayName("comensales: 1 y 50 se aceptan; 0 y 51 se rechazan")
    void guestCountLimits() {
        UUID table = UUID.randomUUID();
        assertThat(invalidFields(new OpenOrderRequest(table, 1))).isEmpty();
        assertThat(invalidFields(new OpenOrderRequest(table, 50))).isEmpty();
        assertThat(invalidFields(new OpenOrderRequest(table, 0))).containsExactly("guestCount");
        assertThat(invalidFields(new OpenOrderRequest(table, 51))).containsExactly("guestCount");
    }

    private static List<AddOrderItemRequest> lines(int count) {
        return IntStream.range(0, count).mapToObj(i -> line(1, null)).toList();
    }
}
