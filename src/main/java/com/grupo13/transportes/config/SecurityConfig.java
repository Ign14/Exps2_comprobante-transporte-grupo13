package com.grupo13.transportes.config;

import com.grupo13.transportes.security.RolesClaimConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Seguridad del backend (Exp2 S6).
 *
 * - Resource Server OAuth2: valida los JWT emitidos por Azure AD B2C (IDaaS).
 * - Autorizacion por ROL leido de un custom claim del token (extension_Role):
 *     ROLE_DESCARGA -> solo puede usar el endpoint de descarga de guias.
 *     ROLE_GESTOR   -> puede usar el resto de endpoints (crear, subir, actualizar,
 *                      eliminar, historial, detalle).
 * - API sin estado (STATELESS); CSRF deshabilitado por ser API REST con token.
 *
 * Configurable por variables de entorno:
 *   AZURE_B2C_JWK_SET_URI  -> endpoint JWKS del user flow de B2C.
 *   AZURE_B2C_ISSUER       -> issuer (iss) esperado del token (validacion opcional).
 */
@Configuration
public class SecurityConfig {

    public static final String ROL_DESCARGA = "DESCARGA";
    public static final String ROL_GESTOR = "GESTOR";

    @Value("${azure.b2c.jwk-set-uri:}")
    private String jwkSetUri;

    @Value("${azure.b2c.issuer:}")
    private String issuer;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   JwtAuthenticationConverter jwtAuthConverter) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // Endpoints publicos de salud (health checks del pipeline / EC2)
                .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                // Descargar guias: rol DESCARGA (o GESTOR)
                .requestMatchers(HttpMethod.GET, "/api/guias/*/descargar")
                    .hasRole(ROL_DESCARGA)
                // Resto de endpoints del negocio: rol GESTOR
                .requestMatchers("/api/guias/**").hasRole(ROL_GESTOR)
                // Cualquier otra peticion requiere autenticacion
                .anyRequest().authenticated())
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthConverter)));
        return http.build();
    }

    /** Convierte el JWT en authorities leyendo el rol del custom claim. */
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter(RolesClaimConverter rolesConverter) {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(rolesConverter);
        return converter;
    }

    /**
     * Decoder de JWT basado en el JWKS de Azure AD B2C (carga diferida: no falla
     * al arrancar si aun no se ha configurado). Si se define el issuer, se valida.
     */
    @Bean
    public JwtDecoder jwtDecoder() {
        String uri = (jwkSetUri != null && !jwkSetUri.isBlank())
                ? jwkSetUri
                : "https://login.microsoftonline.com/common/discovery/v2.0/keys";
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(uri).build();
        if (issuer != null && !issuer.isBlank()) {
            OAuth2TokenValidator<Jwt> withIssuer = JwtValidators.createDefaultWithIssuer(issuer);
            decoder.setJwtValidator(withIssuer);
        }
        return decoder;
    }
}
