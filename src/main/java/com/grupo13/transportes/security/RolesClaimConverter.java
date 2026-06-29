package com.grupo13.transportes.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Extrae el/los rol(es) desde un custom claim del JWT de Azure AD B2C
 * (por defecto "extension_Role") y los transforma en authorities con prefijo
 * ROLE_ para que Spring Security los evalue con hasRole(...).
 *
 * Soporta el claim como texto ("GESTOR") o como lista (["GESTOR","DESCARGA"]).
 * El nombre del claim es configurable con AZURE_B2C_ROLES_CLAIM.
 */
@Component
public class RolesClaimConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

    @Value("${azure.b2c.roles-claim:extension_Role}")
    private String rolesClaim;

    @Override
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        List<GrantedAuthority> authorities = new ArrayList<>();
        Object raw = jwt.getClaim(rolesClaim);
        if (raw == null) {
            return authorities;
        }
        if (raw instanceof String s) {
            for (String r : s.split("[ ,]+")) {
                add(authorities, r);
            }
        } else if (raw instanceof Collection<?> col) {
            for (Object o : col) {
                if (o != null) add(authorities, o.toString());
            }
        }
        return authorities;
    }

    private void add(List<GrantedAuthority> list, String role) {
        if (role != null && !role.isBlank()) {
            list.add(new SimpleGrantedAuthority("ROLE_" + role.trim().toUpperCase()));
        }
    }
}
