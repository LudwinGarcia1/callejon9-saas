package com.callejon9.support;

import org.junit.jupiter.api.Test;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestDatabaseInitializerTest {
    @Test
    void externalModePreservesConnectionAndDoesNotRequireDocker() {
        var environment = new MockEnvironment()
                .withProperty("test.database.mode", "external")
                .withProperty("spring.datasource.url", "jdbc:postgresql://external/test");
        environment.setActiveProfiles("test");
        try (var context = new GenericApplicationContext()) {
            context.setEnvironment(environment);
            new TestDatabaseInitializer().initialize(context);
            assertThat(environment.getProperty("spring.datasource.url"))
                    .isEqualTo("jdbc:postgresql://external/test");
            assertThat(environment.getPropertySources().contains("testContainerDatabase")).isFalse();
        }
    }

    @Test
    void rejectsUnknownModeInsteadOfSilentlyUsingAnotherDatabase() {
        var environment = new MockEnvironment().withProperty("test.database.mode", "unknown");
        environment.setActiveProfiles("test");
        try (var context = new GenericApplicationContext()) {
            context.setEnvironment(environment);
            assertThatThrownBy(() -> new TestDatabaseInitializer().initialize(context))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("container o external");
        }
    }

    @Test
    void doesNotChangeNonTestContexts() {
        try (var context = new GenericApplicationContext()) {
            new TestDatabaseInitializer().initialize(context);
            assertThat(context.getEnvironment().getPropertySources().contains("testContainerDatabase")).isFalse();
        }
    }
}
