# MDrive y Spinpilot: Keycloak 1 como proveedor principal

Demo Docker con un login principal en Keycloak 1. Keycloak 2 está configurado como proveedor de identidad externo de Keycloak 1 y mantiene la emisión de tokens para la API externa Spinpilot.

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

MDrive usa el cliente público `frontend` de Keycloak 1. La pantalla de login de KC1 ofrece el login local y el botón **Keycloak 2**.

```text
Usuario local KC1 -> Login KC1 -> token KC1 -> MDrive
Usuario KC2 -> Login KC1 -> Identity Provider KC2 -> token KC1 -> MDrive

Usuario KC1 local -> Spinpilot: denegado
Usuario KC2 federado -> token exchange KC1/KC2 -> Spinpilot: permitido

KC2 usa el exchange legacy (`token-exchange:v1`) para convertir el token externo
de KC1 en un token de KC2. Tras arrancar el entorno, ejecuta una vez
`keycloak2/configure-token-exchange.sh` con `curl` y `jq` instalados para
configurar las policies y permisos estáticos de KC2. El backend se encarga
automáticamente de buscar por username y email la cuenta equivalente en KC2 y
crear el enlace federado con KC1 antes del primer exchange.
```

Los imports son solo para desarrollo. Cambia secretos, contraseñas y configura HTTPS antes de usar este patrón en producción.

## API protegida

Después del login, el frontend envía el access token al endpoint `GET http://localhost:8083/api/status`.
El backend Spring Boot valida la firma mediante el JWKS interno de Keycloak 1 y acepta este issuer:

```text
http://localhost:8081/realms/mdrive
```

La respuesta muestra el estado, el usuario validado, el subject, el issuer y la hora de comprobación.

## Middleware

La tarjeta `Backend -> Middleware` del frontend llama a `GET http://localhost:8083/api/middleware/status`.
El backend solo permite usuarios con `auth_source=keycloak2`, busca en KC2 la cuenta con el mismo username y email y crea automáticamente el enlace federado si todavía no existe. Después intercambia el token KC1 por un token KC2 con audience `spinpilot-api` y envía ese token al middleware por la red interna de Docker.
El middleware valida el token KC2 y lo reenvía a Spinpilot sin realizar otro exchange ni gestionar usuarios.
El endpoint interno del middleware es `GET http://middleware:8080/api/middleware/status` y su puerto publicado para comprobaciones es `http://localhost:8085`.
Los usuarios locales de KC1 no tienen `auth_source=keycloak2` y son rechazados antes de llamar a Spinpilot.

El frontend también muestra el header, el payload decodificado y el JWT completo para depuración de esta PoC.

## Spinpilot

`spinpilot` es un segundo resource server Java independiente. Valida únicamente tokens cuyo issuer sea el realm de Keycloak 2 y cuyo audience sea `spinpilot-api`:

```text
http://localhost:8082/realms/keycloak2
```

Su endpoint protegido es `GET http://localhost:8084/api/spinpilot/status` y el healthcheck público está en `http://localhost:8084/actuator/health`.
La claim `auth_source` se utiliza antes, en el middleware, para impedir que los usuarios locales de KC1 lleguen a esta API.

Para observar sus peticiones:

```bash
docker compose logs -f spinpilot
```