package callejon9.contractfixtures;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Fuera del component scan de com.callejon9; solo se registran mediante @Import del test. */
public final class AuthorizationFixtures {
    private AuthorizationFixtures() { }

    @Target({ElementType.TYPE, ElementType.METHOD})
    @Retention(RetentionPolicy.RUNTIME)
    @PreAuthorize("hasRole('ADMIN')")
    public @interface AdminOnly { }

    public interface InheritedPolicy {
        @PreAuthorize("hasRole('CASHIER')")
        String inherited();
    }

    @RestController
    @RequestMapping("/api/v1/contract-fixtures")
    public static class MethodRules implements InheritedPolicy {
        @GetMapping("/unprotected")
        public String unprotected() { return "fixture"; }

        @GetMapping("/method")
        @PreAuthorize("hasRole('ADMIN')")
        public String method() { return "fixture"; }

        @GetMapping("/composed")
        @AdminOnly
        public String composed() { return "fixture"; }

        @Override
        @GetMapping("/inherited")
        public String inherited() { return "fixture"; }

        @GetMapping("/public")
        @PreAuthorize("permitAll()")
        public String publicWithoutJustification() { return "fixture"; }
    }

    @RestController
    @RequestMapping("/api/v1/contract-class")
    @PreAuthorize("hasRole('KITCHEN')")
    public static class ClassRules {
        @GetMapping
        public String list() { return "fixture"; }

        @GetMapping("/override")
        @PreAuthorize("hasRole('ADMIN')")
        public String override() { return "fixture"; }
    }

    @RestController
    public static class MatcherRules {
        @GetMapping("/api/v1/platform/contract-fixture")
        public String platform() { return "fixture"; }

        @GetMapping("/api/v1/auth/contract-fixture")
        public String accidentallyPublic() { return "fixture"; }

        @GetMapping("/api/v1/platform-other/contract-fixture")
        public String similarPrefix() { return "fixture"; }
    }
}
