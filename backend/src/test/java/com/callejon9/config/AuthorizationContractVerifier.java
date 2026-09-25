package com.callejon9.config;

import com.callejon9.user.domain.UserRole;
import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.method.PreAuthorizeAuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.test.util.AopTestUtils;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.RequestMappingInfoHandlerMapping;

/** Utilidad exclusiva de tests. No invoca negocio ni cambia permisos. */
final class AuthorizationContractVerifier {
    record Route(String method, String path) { }

    record Endpoint(Route route, RequestMappingInfo mapping, HandlerMethod handler) {
        String description() {
            return handler.getBeanType().getSimpleName() + "#" + handler.getMethod().getName()
                    + " — " + route.method() + " " + route.path() + " " + mapping;
        }
    }

    record Finding(Endpoint endpoint, String decision, Set<String> allowed, String violation) {
        boolean valid() { return violation == null; }

        String describe() {
            return endpoint.description() + " => " + decision + "; filtros/anotaciones=" + allowed
                    + (valid() ? "" : "; ERROR: " + violation);
        }
    }

    private static final String ANONYMOUS = "ANONYMOUS";
    private static final UUID SAMPLE_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final Set<String> ROLES = Arrays.stream(UserRole.values())
            .map(Enum::name).collect(java.util.stream.Collectors.toUnmodifiableSet());
    private static final Map<Route, String> PUBLIC_ROUTES = Map.of(
            new Route("POST", "/api/v1/auth/login"), "Iniciar sesión sin cookie previa",
            new Route("POST", "/api/v1/signup"), "Registrar un restaurante sin sesión previa",
            new Route("GET", "/actuator/health"), "Comprobar disponibilidad",
            new Route("GET", "/v3/api-docs"), "Consultar OpenAPI JSON",
            new Route("GET", "/v3/api-docs.yaml"), "Consultar OpenAPI YAML si está expuesto",
            new Route("GET", "/v3/api-docs/swagger-config"), "Configurar Swagger UI",
            new Route("GET", "/swagger-ui.html"), "Abrir Swagger UI");
    private static final Set<Route> SESSION_ROUTES = Set.of(
            new Route("GET", "/api/v1/auth/me"), new Route("POST", "/api/v1/auth/logout"));

    private final WebApplicationContext context;
    private final FilterChainProxy filters;
    private final PreAuthorizeAuthorizationManager methodAuthorization = new PreAuthorizeAuthorizationManager();

    AuthorizationContractVerifier(WebApplicationContext context, FilterChainProxy filters) {
        this.context = context;
        this.filters = filters;
        methodAuthorization.setApplicationContext(context);
    }

    List<Endpoint> discover() {
        List<Endpoint> endpoints = new ArrayList<>();
        // Incluye también los mappings de Actuator; no se filtra por paquete.
        context.getBeansOfType(RequestMappingInfoHandlerMapping.class).values().forEach(mapping ->
                mapping.getHandlerMethods().forEach((info, handler) -> {
                    Set<RequestMethod> methods = info.getMethodsCondition().getMethods();
                    if (methods.isEmpty()) {
                        methods = Set.of(RequestMethod.values());
                    }
                    for (RequestMethod method : methods) {
                        for (String path : info.getPatternValues()) {
                            endpoints.add(new Endpoint(new Route(method.name(), path), info, handler));
                        }
                    }
                }));
        return endpoints.stream().sorted(java.util.Comparator.comparing(Endpoint::description)).toList();
    }

    Finding inspect(Endpoint endpoint) {
        try {
            Map<String, Boolean> web = webDecisions(endpoint.route());
            Map<String, Boolean> method = methodDecisions(endpoint.handler());
            Set<String> allowed = new TreeSet<>();
            web.forEach((subject, granted) -> {
                if (granted && (method.isEmpty() || method.get(subject))) {
                    allowed.add(subject);
                }
            });
            String publicReason = publicReason(endpoint.route());
            if (publicReason != null) {
                if (!allowed.contains(ANONYMOUS)) {
                    return failure(endpoint, allowed, "Ruta pública documentada rechazada por la seguridad real");
                }
                return new Finding(endpoint, "Pública: " + publicReason, allowed, null);
            }
            if ((Boolean.TRUE.equals(web.get(ANONYMOUS)) && method.isEmpty())
                    || Boolean.TRUE.equals(method.get(ANONYMOUS))) {
                return failure(endpoint, allowed, "Permiso público sin entrada exacta en la lista pública");
            }
            if (!method.isEmpty()) {
                return new Finding(endpoint, "@PreAuthorize de método, clase o heredada", allowed, null);
            }
            if (SESSION_ROUTES.contains(endpoint.route())) {
                return allowed.equals(ROLES)
                        ? new Finding(endpoint, "Sesión explícita documentada", allowed, null)
                        : failure(endpoint, allowed, "La ruta de sesión no exige autenticación para todos los roles");
            }
            if (endpoint.route().path().startsWith("/api/v1/platform/")) {
                return allowed.equals(Set.of("SUPER_ADMIN"))
                        ? new Finding(endpoint, "Matcher de plataforma verificado en SecurityFilterChain", allowed, null)
                        : failure(endpoint, allowed, "El matcher real de plataforma no limita a SUPER_ADMIN");
            }
            return failure(endpoint, allowed,
                    "Falta una decisión explícita; authenticated() general no constituye contrato");
        } catch (RuntimeException ex) {
            return failure(endpoint, Set.of(), "No se pudo verificar: " + ex.getClass().getSimpleName()
                    + ": " + ex.getMessage());
        }
    }

    void requireValid(List<Endpoint> endpoints) {
        List<String> violations = endpoints.stream().map(this::inspect).filter(f -> !f.valid())
                .map(Finding::describe).toList();
        if (!violations.isEmpty()) {
            throw new AssertionError("Contrato de autorización incumplido:\n" + String.join("\n", violations));
        }
    }

    Map<String, Boolean> webDecisions(Route route) {
        Map<String, Boolean> decisions = new LinkedHashMap<>();
        subjects().forEach((subject, authentication) -> {
            MockHttpServletRequest request = new MockHttpServletRequest(context.getServletContext(),
                    route.method(), route.path().replaceAll("\\{[^}]+}", SAMPLE_ID.toString()));
            request.setServletPath(request.getRequestURI());
            var chain = filters.getFilterChains().stream().filter(candidate -> candidate.matches(request))
                    .findFirst().orElseThrow(() -> new IllegalStateException("Sin SecurityFilterChain"));
            var authorization = chain.getFilters().stream().filter(AuthorizationFilter.class::isInstance)
                    .map(AuthorizationFilter.class::cast).findFirst()
                    .orElseThrow(() -> new IllegalStateException("Sin AuthorizationFilter"));
            AuthorizationDecision decision = authorization.getAuthorizationManager()
                    .check(() -> authentication, (HttpServletRequest) request);
            if (decision == null) {
                throw new IllegalStateException("El filtro no decidió autorización");
            }
            decisions.put(subject, decision.isGranted());
        });
        return decisions;
    }

    private Map<String, Boolean> methodDecisions(HandlerMethod handler) {
        Object bean = handler.getBean() instanceof String name ? context.getBean(name) : handler.getBean();
        Object target = AopTestUtils.getUltimateTargetObject(bean);
        Method method = handler.getMethod();
        MethodInvocation invocation = new MethodInvocation() {
            public Method getMethod() { return method; }
            public Object[] getArguments() { return new Object[method.getParameterCount()]; }
            public Object proceed() { throw new UnsupportedOperationException("No ejecutar negocio"); }
            public Object getThis() { return target; }
            public AccessibleObject getStaticPart() { return method; }
        };
        Map<String, Boolean> decisions = new LinkedHashMap<>();
        subjects().forEach((subject, authentication) -> {
            AuthorizationDecision decision = methodAuthorization.check(() -> authentication, invocation);
            if (decision != null) {
                decisions.put(subject, decision.isGranted());
            }
        });
        return decisions;
    }

    private static Map<String, Authentication> subjects() {
        Map<String, Authentication> subjects = new LinkedHashMap<>();
        subjects.put(ANONYMOUS, new AnonymousAuthenticationToken("contract-test", "anonymousUser",
                List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))));
        for (UserRole role : UserRole.values()) {
            subjects.put(role.name(), UsernamePasswordAuthenticationToken.authenticated(SAMPLE_ID, null,
                    List.of(new SimpleGrantedAuthority("ROLE_" + role.name()))));
        }
        return subjects;
    }

    private static String publicReason(Route route) {
        Route normalized = route.method().equals("HEAD") ? new Route("GET", route.path()) : route;
        return PUBLIC_ROUTES.get(normalized);
    }

    private static Finding failure(Endpoint endpoint, Set<String> allowed, String message) {
        return new Finding(endpoint, "Sin aprobar", allowed, message);
    }
}
