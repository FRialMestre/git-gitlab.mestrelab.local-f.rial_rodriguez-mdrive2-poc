# MDrive y Spinpilot: dos emisores OIDC

Demo Docker con dos proveedores de identidad independientes. MDrive puede iniciar sesión directamente contra Keycloak 1 o Keycloak 2. Spinpilot y su middleware aceptan únicamente tokens emitidos por Keycloak 2.

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

La pantalla inicial de MDrive ofrece dos clientes públicos:

- `frontend` en Keycloak 1.
- `mdrive-frontend-kc2` en Keycloak 2.

El proveedor elegido determina el issuer real del token. No se usa el broker para convertir un token de KC1 en uno de KC2.

```text
MDrive + token KC1 -> MDrive Backend: permitido
MDrive + token KC1 -> Middleware: rechazado

MDrive + token KC2 -> MDrive Backend: permitido
MDrive + token KC2 -> Middleware -> Spinpilot: permitido
```

Los imports son solo para desarrollo. Cambia secretos, contraseñas y configura HTTPS antes de usar este patrón en producción.

## API protegida

Después del login, el frontend envía el access token al endpoint `GET http://localhost:8083/api/status`.
El backend Spring Boot valida la firma mediante el JWKS interno del emisor correspondiente y acepta únicamente estos dos issuers:

```text
http://localhost:8081/realms/mdrive
http://localhost:8082/realms/keycloak2
```

La respuesta muestra el estado, el usuario validado, el subject, el issuer y la hora de comprobación.

## Middleware

La tarjeta `Backend -> Middleware` del frontend llama a `GET http://localhost:8083/api/middleware/status`.
El backend reenvía el bearer token al middleware por la red interna de Docker. El middleware valida exclusivamente tokens de Keycloak 2 y reenvía el mismo token a Spinpilot.
El endpoint interno del middleware es `GET http://middleware:8080/api/middleware/status` y su puerto publicado para comprobaciones es `http://localhost:8085`.
Los tokens de Keycloak 1 reciben `401` en el middleware; los tokens de Keycloak 2 reciben `200` y llegan a Spinpilot.

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