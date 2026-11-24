package com.callejon9.config;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.fail;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "springdoc.writer-with-default-pretty-printer=true",
        "springdoc.writer-with-order-by-keys=true"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OpenApiConfigTest {
    @Autowired private MockMvc mockMvc;

    @Test
    void serverStaysRelativeBehindTlsTerminatingProxy() throws Exception {
        // App Service termina el TLS y reenvia por http al puerto 80.
        mockMvc.perform(get("http://app-callejon9-api.azurewebsites.net/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.servers", hasSize(1)))
                .andExpect(jsonPath("$.servers[0].url").value("/"));
    }

    @Test
    void generatedContractMatchesVersionedDocument() throws Exception {
        String document = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths").isNotEmpty())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        Path generated = Path.of("target", "openapi.json");
        Path versioned = Path.of("..", "docs", "api", "openapi.json");
        Files.createDirectories(generated.getParent());
        Files.writeString(generated, document.replace("\r\n", "\n").stripTrailing() + "\n");

        if (Boolean.getBoolean("openapi.update")) {
            Files.copy(generated, versioned, StandardCopyOption.REPLACE_EXISTING);
            return;
        }

        String[] expected = Files.readAllLines(versioned).toArray(String[]::new);
        String[] actual = Files.readAllLines(generated).toArray(String[]::new);
        int difference = Arrays.mismatch(expected, actual);
        if (difference >= 0) {
            fail("El contrato OpenAPI difiere en la línea " + (difference + 1)
                    + "\nVersionado: " + lineAt(expected, difference)
                    + "\nGenerado:   " + lineAt(actual, difference)
                    + "\nComparar " + versioned + " con " + generated
                    + ". Para actualizarlo, consulta docs/api/README.md.");
        }
    }

    private static String lineAt(String[] lines, int index) {
        return index < lines.length ? lines[index] : "(fin del archivo)";
    }
}
