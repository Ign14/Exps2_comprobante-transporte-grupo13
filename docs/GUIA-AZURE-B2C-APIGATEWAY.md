# Guía paso a paso — Azure AD B2C (IDaaS) + AWS API Gateway (S6, Grupo 13)

Configuración de la seguridad de la **Experiencia 2 – Semana 6**, siguiendo lo enseñado en
las guías S4/S5/S6 y lo permitido en **AWS Academy Learner Lab**. Al terminar tendrás:

- Un **IDaaS** (Azure AD B2C) que emite tokens **JWT** con un **rol** en un *custom claim*.
- Un **API Gateway (HTTP API)** que registra y **securitiza** los endpoints con un **autorizador JWT**.
- El **backend Spring Boot** validando el mismo JWT y aplicando **autorización por rol**
  (`DESCARGA` solo descarga; `GESTOR` el resto).

> Reemplaza siempre los marcadores `TU_...`, `<tenant>`, `<tenant-id>`, `<client-id>`, `TU_IP_EC2`.

---

## 0. Arquitectura de la solución

```
Postman ──(JWT Bearer)──▶ AWS API Gateway (HTTP API)
                              │  autorizador JWT (valida firma, issuer, audience)
                              ▼
                          EC2 (Spring Boot)  ── Spring Security (Resource Server)
                              │  valida el MISMO JWT + autoriza por rol (extension_Role)
                              ▼
                          EFS (temporal) ─▶ Amazon S3 (final)
```

Doble capa de seguridad: **API Gateway** autentica el token (capa de borde) y el **backend**
re-valida y aplica el control de acceso por rol (defensa en profundidad). Ambos confían en el
mismo emisor de Azure AD B2C.

---

## PARTE A — Azure AD B2C (IDaaS)

> Necesitas una cuenta de **Azure** (es independiente de AWS Academy). El tenant B2C es gratuito
> en su nivel básico.

### A1. Crear el tenant B2C
1. Inicia sesión en `https://portal.azure.com`.
2. Barra de búsqueda → **Subscriptions** → selecciona tu suscripción activa.
3. Menú → **Create a resource** → busca **B2C** → **Azure Active Directory B2C** → **Create**.
4. Elige **Create a new Azure AD B2C Tenant**.
5. Completa: *Organization name* (ej. `DuocGrupo13`), *Initial domain name* (ej. `duocgrupo13`,
   queda como `duocgrupo13.onmicrosoft.com`), país Chile. **Review + create** → **Create**.
6. Espera unos minutos hasta el mensaje de creación satisfactoria.

### A2. Cambiar al tenant B2C
1. Ícono de **engranaje** (arriba a la derecha) → **Directories + subscriptions**.
2. Ubica tu nuevo tenant B2C → botón **Switch**.
3. Vuelve al inicio, busca **B2C** → **Azure AD B2C** (ya en el tenant nuevo).

### A3. Registrar la aplicación (App registration)
1. En **Azure AD B2C** → **App registrations** → **New registration**.
2. Nombre: `transportes-guias-grupo13`. *Supported account types*: deja la opción de cuentas en
   cualquier proveedor de identidades/organizational (la que indica la guía para B2C).
3. **Register**. Anota el **Application (client) ID** y el **Directory (tenant) ID** (los usarás
   en el autorizador, en Postman y en el backend).
4. **Authentication** → marca las dos casillas de *tokens* (**Access tokens** e **ID tokens**) →
   **Save**. (Si vas a usar el flujo interactivo, agrega como *Redirect URI* tipo Web
   `https://jwt.ms`.)
5. **API permissions** → **Add a permission** → **Microsoft Graph** → agrega los permisos que
   indica la guía → **Grant admin consent**.

### A4. Custom claim de ROL (extension_Role)  ← clave para los 2 roles
1. En **Azure AD B2C** → **User attributes** → **Add**.
2. Nombre: `Role`, tipo **String**, descripción "Rol de acceso (DESCARGA / GESTOR)" → **Create**.
   (Azure lo expone en el token como **`extension_Role`**, que es justo el claim que lee el backend.)

### A5. User flow (registro e inicio de sesión)
1. **Azure AD B2C** → **User flows** → **New user flow**.
2. Tipo **Sign up and sign in** → versión **Recommended** → nombre `B2C_1_signupsignin`.
3. *Identity providers*: **Email signup**.
4. *User attributes and token claims*: en **Application claims** marca tu atributo **Role**
   (y los que necesites: Display Name, Email). **Create**.
5. Abre el user flow → **Run user flow** para registrar un usuario de prueba; al ejecutarlo,
   en `https://jwt.ms` verás el token y el claim **`extension_Role`** (asigna el valor del rol
   al usuario, p. ej. `GESTOR` o `DESCARGA`, desde **Users** → el usuario → **Edit** del atributo).

### A6. Exponer un scope y crear el secreto (para pedir token en Postman)
1. App registration → **Expose an API** → **Add a scope** (acepta el Application ID URI por
   defecto). Crea el scope `azure_aws` (Admins) → **Add scope**.
2. **Certificates & secrets** → **New client secret** → descripción `secreto` → **Add**.
   **Copia el Value ahora** (no se vuelve a mostrar). Es tu **Client Secret**.

### A7. Datos que obtienes de Azure (anótalos)
| Dato | Dónde | Ejemplo |
|---|---|---|
| Tenant (dominio) | A1 | `duocgrupo13.onmicrosoft.com` |
| Directory (tenant) ID | A3 | `8f3c...-...-...` |
| Application (client) ID | A3 | `0193f8b2-bd81-40ec-...` |
| Client Secret (Value) | A6 | `-BS8Q~...` |
| Issuer (iss) | construido | `https://<tenant>.b2clogin.com/<tenant-id>/v2.0/` |
| JWKS | construido | `https://<tenant>.b2clogin.com/<tenant>.onmicrosoft.com/B2C_1_signupsignin/discovery/v2.0/keys` |

---

## PARTE B — AWS API Gateway (API Manager) en AWS Academy

> Primero ten tu backend desplegado en EC2 (S3/S6) y accesible en `http://TU_IP_EC2:8080`
> (puerto 8080 abierto en el security group), tal como en la entrega de S3.

### B1. Crear la HTTP API
1. AWS Academy → **API Gateway** → **Create API** → **HTTP API** → **Build**.
2. Nombre: `api-transportes-guias-grupo13`. **Next** dejando lo demás por defecto → **Create**.

### B2. Integración hacia el backend (EC2)
1. En la API → **Routes** → **Create**.
2. Crea las rutas de tu backend (método + ruta), por ejemplo:
   - `POST /api/guias`
   - `POST /api/guias/{id}/subir-s3`
   - `GET /api/guias/{id}/descargar`
   - `PUT /api/guias/{id}`
   - `DELETE /api/guias/{id}`
   - `GET /api/guias`
   - `GET /api/guias/{id}`
   *(Atajo válido: una sola ruta catch-all `ANY /api/{proxy+}` que cubre todos los endpoints.)*
3. Selecciona una ruta → **Attach integration** → **Create and attach an integration**.
4. Tipo **HTTP URI**, método correspondiente, **URL del endpoint** = `http://TU_IP_EC2:8080/api/guias`
   (o `http://TU_IP_EC2:8080/api/{proxy}` si usaste catch-all). **Create**.
5. Repite/asocia la integración para cada ruta.

### B3. Crear el autorizador JWT y asociarlo
1. En la API → **Authorization** → pestaña **Manage authorizers** → **Create**.
2. Tipo **JWT**. Nombre `b2c-jwt`.
3. **Issuer URL**: `https://<tenant>.b2clogin.com/<tenant-id>/v2.0/`  (el `iss` del token del user flow; verifícalo en jwt.ms)
4. **Audience**: tu **Application (client) ID**.
5. **Create**.
6. Pestaña **Attach authorizers**: asocia `b2c-jwt` a **cada ruta** que debe quedar protegida
   (todas las de `/api/guias`). *(Opcional: en `authorizationScopes` puedes exigir el scope.)*

### B4. Desplegar (Stage)
1. **Deploy** → **Stages** → **Create** → nombre `prod` → **Create**.
2. **Deploy** → selecciona `prod` → **Deploy**.
3. Copia la **Invoke URL** (algo como `https://abc123.execute-api.us-east-1.amazonaws.com`).
   Tus endpoints quedan en `https://abc123.execute-api.us-east-1.amazonaws.com/api/guias...`.

### B5. Verificación rápida
- Sin token → `GET {InvokeURL}/api/guias` debe responder **401 Unauthorized** (lo bloquea el autorizador).

---

## PARTE C — Configurar el backend Spring Boot con los datos de B2C

En la EC2 (o en los GitHub Secrets / `docker run`), define estas variables de entorno con tus
valores de Azure (Parte A7):

```bash
AZURE_B2C_ISSUER=https://<tenant>.b2clogin.com/<tenant-id>/v2.0/
AZURE_B2C_JWK_SET_URI=https://<tenant>.b2clogin.com/<tenant>.onmicrosoft.com/B2C_1_signupsignin/discovery/v2.0/keys
AZURE_B2C_ROLES_CLAIM=extension_Role
```

Ejemplo de `docker run` en EC2 (añadiendo estas 3 al comando que ya usas):

```bash
docker run -d --name transportes-guias-api --restart unless-stopped \
  -v /mnt/efs:/app/efs -p 8080:8080 \
  -e AWS_REGION=us-east-1 \
  -e AWS_S3_BUCKET=transportes-guias-grupo13 \
  -e AZURE_B2C_ISSUER="https://<tenant>.b2clogin.com/<tenant-id>/v2.0/" \
  -e AZURE_B2C_JWK_SET_URI="https://<tenant>.b2clogin.com/<tenant>.onmicrosoft.com/B2C_1_signupsignin/discovery/v2.0/keys" \
  -e AZURE_B2C_ROLES_CLAIM="extension_Role" \
  TU_USUARIO_DOCKERHUB/transportes-guias-api:latest
```

El backend (`SecurityConfig` + `RolesClaimConverter`) ya está preparado: valida el JWT contra ese
issuer/JWKS y mapea el claim `extension_Role` a `ROLE_DESCARGA` / `ROLE_GESTOR`.

---

## PARTE D — Obtener el token en Postman y probar

### D1. Token de usuario con rol (recomendado para los 2 roles)
Como los roles viven en un **claim de usuario** (`extension_Role`), el token debe obtenerse con un
**flujo de usuario** (no client-credentials, que no lleva claims de usuario):

1. En Postman, request → pestaña **Authorization** → **OAuth 2.0**.
2. **Grant Type**: *Authorization Code* (with PKCE).
3. **Auth URL**: `https://<tenant>.b2clogin.com/<tenant>.onmicrosoft.com/B2C_1_signupsignin/oauth2/v2.0/authorize`
4. **Access Token URL**: `https://<tenant>.b2clogin.com/<tenant>.onmicrosoft.com/B2C_1_signupsignin/oauth2/v2.0/token`
5. **Client ID**: Application (client) ID. **Scope**: `openid <Application ID URI>/azure_aws`.
6. **Callback URL**: `https://jwt.ms` (o el registrado). **Get New Access Token** → inicia sesión
   con el usuario de prueba (al que asignaste el rol) → **Use Token**.
7. Decodifica el token en `https://jwt.ms` y confirma que trae **`extension_Role`** con el valor del rol.

> Para la demo usa **dos usuarios** (o cambia el valor del atributo Role): uno con `GESTOR` y otro
> con `DESCARGA`, y así evidencias los dos roles.

### D2. Probar los endpoints (vía API Gateway)
Usa la **Invoke URL** del API Gateway como `baseUrl` y agrega el header `Authorization: Bearer <token>`:

| Acción | Método / Ruta | Rol que debe funcionar | Esperado |
|---|---|---|---|
| Crear | `POST /api/guias` | GESTOR | 201 |
| Subir a S3 | `POST /api/guias/{id}/subir-s3` | GESTOR | 200 |
| Historial | `GET /api/guias` | GESTOR | 200 |
| Detalle | `GET /api/guias/{id}` | GESTOR | 200 |
| Actualizar | `PUT /api/guias/{id}` | GESTOR | 200 |
| Eliminar | `DELETE /api/guias/{id}` | GESTOR | 200 |
| Descargar | `GET /api/guias/{id}/descargar` | DESCARGA | 200 |
| Evidencia de rol | `GET .../descargar` con token GESTOR | — | **403** |
| Evidencia de rol | `POST /api/guias` con token DESCARGA | — | **403** |
| Sin token | cualquiera | — | **401** |

Estas tres últimas filas (401 / 403 cruzados) son la **evidencia clave** de que los 2 roles y la
seguridad funcionan.

---

## Notas / consideraciones AWS Academy y Azure

- En **AWS Academy** la **HTTP API** y el **autorizador JWT** están permitidos (no requieren Lambda).
- Si la **IP de la EC2 cambia** (reinicio del lab), actualiza la **URL de integración** del API
  Gateway y vuelve a **Deploy**. Considera una **Elastic IP** para fijarla.
- El backend re-valida el JWT (no confía solo en el API Gateway): cumple "securitizar el backend
  con Spring Security todos los endpoints".
- Azure AD B2C es independiente del lab AWS; su token no caduca por reiniciar el lab (sí expira el
  access token por su `exp` normal: pide uno nuevo cuando haga falta).
- Alternativa más simple si no necesitas login interactivo: definir **App Roles** y pedir token por
  *Client Credentials* contra `https://login.microsoftonline.com/<tenant-id>/oauth2/v2.0/token`
  (el rol llega en el claim `roles`). En ese caso usa el emisor de Azure AD:
  `AZURE_B2C_ISSUER=https://login.microsoftonline.com/<tenant-id>/v2.0`,
  `AZURE_B2C_JWK_SET_URI=https://login.microsoftonline.com/<tenant-id>/discovery/v2.0/keys`,
  `AZURE_B2C_ROLES_CLAIM=roles`, y en el autorizador del API Gateway pon ese mismo issuer.
  **Importante:** el issuer del autorizador (Parte B3) y el del backend (Parte C) deben ser
  EXACTAMENTE el `iss` que muestre tu token en jwt.ms.

---

## Valores REALES confirmados — Grupo 13 (verificados en jwt.ms)

| Parámetro | Valor |
|---|---|
| Tenant | `duocgrupo13.onmicrosoft.com` |
| Tenant ID | `3f8624f7-de01-47f3-9fd0-f834f87dc061` |
| Application (client) ID / **audience** | `259dff0d-8d49-41ef-8f85-18bebb472ec0` |
| User flow | `B2C_1_signupsignin` |
| **Issuer (iss)** | `https://duocgrupo13.b2clogin.com/3f8624f7-de01-47f3-9fd0-f834f87dc061/v2.0/` |
| **JWKS** | `https://duocgrupo13.b2clogin.com/duocgrupo13.onmicrosoft.com/B2C_1_signupsignin/discovery/v2.0/keys` |
| **Claim de rol** | `extension_Role` (valores `GESTOR` / `DESCARGA`) |
| Authorize URL | `https://duocgrupo13.b2clogin.com/duocgrupo13.onmicrosoft.com/B2C_1_signupsignin/oauth2/v2.0/authorize` |
| Token URL | `https://duocgrupo13.b2clogin.com/duocgrupo13.onmicrosoft.com/B2C_1_signupsignin/oauth2/v2.0/token` |
| Scope (access token con aud=app) | `openid 259dff0d-8d49-41ef-8f85-18bebb472ec0 offline_access` |

### Variables de entorno del backend (Grupo 13)
```bash
AZURE_B2C_ISSUER=https://duocgrupo13.b2clogin.com/3f8624f7-de01-47f3-9fd0-f834f87dc061/v2.0/
AZURE_B2C_JWK_SET_URI=https://duocgrupo13.b2clogin.com/duocgrupo13.onmicrosoft.com/B2C_1_signupsignin/discovery/v2.0/keys
AZURE_B2C_ROLES_CLAIM=extension_Role
```

### Autorizador JWT del API Gateway (Grupo 13)
- Issuer URL: `https://duocgrupo13.b2clogin.com/3f8624f7-de01-47f3-9fd0-f834f87dc061/v2.0/`
- Audience: `259dff0d-8d49-41ef-8f85-18bebb472ec0`
