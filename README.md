# MDrive y Spinpilot: Keycloak 2 como proveedor principal

Demo Docker con un login unificado en Keycloak 2. Keycloak 1 está configurado como proveedor de identidad externo de Keycloak 2. Tanto los usuarios locales de KC2 como los usuarios de KC1 federados por KC2 reciben un token emitido por KC2.

## Arranque

Requiere Docker Compose v2.

```bash
docker compose up --build
```

Abre <http://localhost:4200>. Los servicios quedan disponibles en:

- Frontend: `http://localhost:4200`
- Keycloak 1: `http://localhost:8081`
- Keycloak 2: `http://localhost:8082`
- Backend Java: `http://localhost:8083`
- Middleware Java: `http://localhost:8085`
- Spinpilot: `http://localhost:8084`

Credenciales de prueba:

- Usuario local de Keycloak 1: `usuario-kc1` / `kc1pass`
- Usuario de Keycloak 2: `usuario-kc2` / `kc2pass`
- Administración de ambos Keycloak: `admin` / `admin`

Para gestionar usuarios, entra al panel de administración correspondiente, selecciona el realm y usa **Users**.

## Flujo

MDrive usa únicamente el cliente público `mdrive-frontend-kc2` de Keycloak 2. La pantalla de login de KC2 ofrece el login local y el botón **Keycloak 1**.

```text
Usuario local KC2 -> Login KC2 -> token KC2
Usuario KC1 -> Login KC2 -> Identity Provider KC1 -> token KC2

Ambos -> MDrive Backend -> Middleware -> Spinpilot: permitido
```

Los imports son solo para desarrollo. Cambia secretos, contraseñas y configura HTTPS antes de usar este patrón en producción.

## API protegida

Después del login, el frontend envía el access token al endpoint `GET http://localhost:8083/api/status`.
El backend Spring Boot valida la firma mediante el JWKS interno de Keycloak 2 y acepta este issuer:

```text
http://localhost:8082/realms/keycloak2
```

La respuesta muestra el estado, el usuario validado, el subject, el issuer y la hora de comprobación.

## Middleware

La tarjeta `Backend -> Middleware` del frontend llama a `GET http://localhost:8083/api/middleware/status`.
El backend reenvía el bearer token al middleware por la red interna de Docker. El middleware valida exclusivamente tokens de Keycloak 2 y reenvía el mismo token a Spinpilot.
El endpoint interno del middleware es `GET http://middleware:8080/api/middleware/status` y su puerto publicado para comprobaciones es `http://localhost:8085`.
Los tokens que llegan a MDrive siempre son de Keycloak 2, por lo que ambos tipos de usuario pueden alcanzar Spinpilot. El filtrado de usuarios puede añadirse posteriormente en el backend mediante roles o claims.

El frontend también muestra el header, el payload decodificado y el JWT completo para depuración de esta PoC.

## Spinpilot

`spinpilot` es un segundo resource server Java independiente. Valida únicamente tokens cuyo issuer sea el realm de Keycloak 2:

```text
http://localhost:8082/realms/keycloak2
```

Su endpoint protegido es `GET http://localhost:8084/api/spinpilot/status` y el healthcheck público está en `http://localhost:8084/actuator/health`.
No necesita la claim `auth_source`: la autorización se basa en la firma y el issuer de KC2.

Para observar sus peticiones:

```bash
docker compose logs -f spinpilot
```