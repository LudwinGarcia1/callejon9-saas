package com.callejon9;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Se excluye UserDetailsServiceAutoConfiguration a proposito.
 *
 * Al no existir un bean UserDetailsService, Spring Boot creaba uno en memoria
 * con una contrasena aleatoria y la imprimia en el arranque junto a una
 * advertencia de seguridad. No era explotable -- la cadena de filtros no
 * declara formLogin ni httpBasic, asi que ese usuario no tenia por donde
 * autenticarse -- pero era un usuario que nadie pidio y un mensaje enganoso
 * en el log.
 *
 * La autenticacion de esta aplicacion la resuelve TenantFilter a partir del
 * JWT de la cookie, y no pasa por el AuthenticationManager de Spring, de modo
 * que la autoconfiguracion no aporta nada.
 */
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@ConfigurationPropertiesScan
public class Callejon9Application {

	public static void main(String[] args) {
		SpringApplication.run(Callejon9Application.class, args);
	}

}
