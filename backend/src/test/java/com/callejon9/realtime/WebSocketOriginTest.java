package com.callejon9.realtime;

import com.callejon9.auth.service.JwtService;
import com.callejon9.tenancy.TenantFilter;
import com.callejon9.user.domain.User;
import com.callejon9.user.domain.UserRole;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Lista de origenes del handshake de /ws contra un Tomcat real. El upgrade a
 * WebSocket no ocurre en MockMvc, asi que la prueba habla HTTP crudo por un
 * socket y lee solo la linea de estado: 101 si el handshake se acepto, 403 si
 * el origen se rechazo.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@DisplayName("Origenes permitidos en el WebSocket")
class WebSocketOriginTest {

    @LocalServerPort private int port;
    @Autowired private JwtService jwtService;

    private String validToken() {
        User user = User.builder()
                .email("ws-origin@demo.com").passwordHash("x").fullName("Ws Origin")
                .role(UserRole.ADMIN).active(true).build();
        user.setId(UUID.randomUUID());
        user.setTenantId(UUID.randomUUID());
        return jwtService.generateAccessToken(user);
    }

    /** Devuelve el codigo de estado de la respuesta al handshake; {@code origin} nulo omite la cabecera. */
    private int handshake(String origin) throws IOException {
        StringBuilder request = new StringBuilder()
                .append("GET /ws HTTP/1.1\r\n")
                .append("Host: localhost:").append(port).append("\r\n")
                .append("Upgrade: websocket\r\n")
                .append("Connection: Upgrade\r\n")
                .append("Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r\n")
                .append("Sec-WebSocket-Version: 13\r\n")
                .append("Cookie: ").append(TenantFilter.ACCESS_TOKEN_COOKIE).append('=')
                .append(validToken()).append("\r\n");
        if (origin != null) {
            request.append("Origin: ").append(origin).append("\r\n");
        }
        request.append("\r\n");

        try (Socket socket = new Socket("localhost", port)) {
            socket.setSoTimeout(5_000);
            OutputStream out = socket.getOutputStream();
            out.write(request.toString().getBytes(StandardCharsets.US_ASCII));
            out.flush();
            String statusLine = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII)).readLine();
            return Integer.parseInt(statusLine.split(" ")[1]);
        }
    }

    @Test
    @DisplayName("el origen configurado del frontend abre el canal")
    void allowedOriginCompletesHandshake() throws IOException {
        assertThat(handshake("http://localhost:3000")).isEqualTo(101);
    }

    @Test
    @DisplayName("otro sitio recibe 403 aunque el navegador adjunte una cookie valida")
    void foreignOriginIsRejected() throws IOException {
        assertThat(handshake("https://sitio-malicioso.example")).isEqualTo(403);
    }

    @Test
    @DisplayName("un origen parecido al permitido tambien se rechaza")
    void lookalikeOriginIsRejected() throws IOException {
        assertThat(handshake("http://localhost.sitio-malicioso.example:3000")).isEqualTo(403);
    }

    @Test
    @DisplayName("un cliente sin cabecera Origin no se ve afectado")
    void clientWithoutOriginCompletesHandshake() throws IOException {
        assertThat(handshake(null)).isEqualTo(101);
    }

    @Test
    @DisplayName("la configuracion rechaza comodines y listas vacias")
    void configurationRejectsWildcards() {
        assertThatThrownBy(() -> WebSocketConfig.requireExplicitOrigins(new String[] {"*"}))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> WebSocketConfig.requireExplicitOrigins(new String[] {"https://*.example"}))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> WebSocketConfig.requireExplicitOrigins(new String[] {}))
                .isInstanceOf(IllegalStateException.class);
        assertThat(WebSocketConfig.requireExplicitOrigins(new String[] {"https://app.callejon9.mx"}))
                .containsExactly("https://app.callejon9.mx");
    }
}
