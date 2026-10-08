package com.callejon9.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

/** Contrato versionado independiente del descubrimiento y de las anotaciones. */
final class AuthorizationContractMatrix {
    record Key(String method, String path, List<String> params, List<String> headers,
               List<String> consumes, List<String> produces) { }

    record Rule(String method, String path, List<String> params, List<String> headers,
                List<String> consumes, List<String> produces, Set<String> roles, String reason) {
        Key key() {
            return new Key(method, path, sorted(params), sorted(headers), sorted(consumes), sorted(produces));
        }
    }

    static List<Rule> load() throws IOException {
        try (var input = AuthorizationContractMatrix.class.getResourceAsStream("/security/authorization-contract.json")) {
            if (input == null) { throw new IOException("Falta la matriz de autorizacion"); }
            return new ObjectMapper().readValue(input, new TypeReference<List<Rule>>() { });
        }
    }

    static Key key(AuthorizationContractVerifier.Endpoint endpoint) {
        var mapping = endpoint.mapping();
        return new Key(endpoint.route().method(), endpoint.route().path(),
                sorted(mapping.getParamsCondition().getExpressions()),
                sorted(mapping.getHeadersCondition().getExpressions()),
                sorted(mapping.getConsumesCondition().getExpressions()),
                sorted(mapping.getProducesCondition().getExpressions()));
    }

    static void requireMatches(List<AuthorizationContractVerifier.Endpoint> endpoints, List<Rule> rules,
                               AuthorizationContractVerifier verifier) {
        var remaining = new HashMap<Key, Rule>();
        var violations = new ArrayList<String>();
        for (var rule : rules) {
            if (rule.reason() == null || rule.reason().isBlank() || rule.roles().isEmpty()) {
                violations.add("Decision sin roles o justificacion: " + rule.key());
            }
            if (remaining.put(rule.key(), rule) != null) {
                violations.add("Decision duplicada: " + rule.key());
            }
        }
        for (var endpoint : endpoints) {
            var rule = remaining.remove(key(endpoint));
            if (rule == null) {
                violations.add("Endpoint sin decision en matriz: " + endpoint.description());
                continue;
            }
            var finding = verifier.inspect(endpoint);
            if (endpoint.route().path().startsWith("/api/v1/") && !finding.valid()) {
                violations.add(finding.describe());
            }
            if (endpoint.mapping().getCustomCondition() != null) {
                violations.add("Condicion personalizada no modelada: " + endpoint.description());
            }
            var actual = endpoint.route().path().startsWith("/api/v1/")
                    ? finding.allowed()
                    : verifier.webDecisions(endpoint).entrySet().stream().filter(java.util.Map.Entry::getValue)
                            .map(java.util.Map.Entry::getKey).collect(java.util.stream.Collectors.toSet());
            if (!actual.equals(rule.roles())) {
                violations.add(endpoint.description() + "; esperados=" + rule.roles() + "; reales=" + actual);
            }
        }
        remaining.values().forEach(rule -> violations.add("Decision sin endpoint descubierto: " + rule.key()));
        if (!violations.isEmpty()) {
            throw new AssertionError("Matriz de autorizacion incumplida:\n" + String.join("\n", violations));
        }
    }

    private static List<String> sorted(Collection<?> values) {
        return values.stream().map(Object::toString).sorted().toList();
    }
}
