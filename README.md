# MDrive: Keycloak broker demo

Demo Docker de un frontend que autentica contra `keycloak1`. El realm `mdrive` de `keycloak1` publica `keycloak2` como proveedor OIDC, por lo que el usuario puede entrar con una cuenta local de Keycloak 1 o elegir el proveedor `TopSpin` para autenticarse con Keycloak 2.

## Arranque

Requiere Docker Compose v2.

```bash
docker compose up --build
```

Abre <http://localhost:5173>. Los servicios quedan disponibles en:

- Frontend: `http://localhost:5173`
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

1. La app redirige al cliente `frontend` de `keycloak1`.
2. Keycloak 1 muestra su login local y el botón **TopSpin**.
3. Al elegirlo, Keycloak 1 redirige al realm `keycloak2`.
4. Keycloak 2 devuelve la identidad al endpoint broker de Keycloak 1.
5. La app recibe un token emitido por Keycloak 1 en ambos casos.

El usuario de Keycloak 2 se autentica originalmente en Keycloak 2, pero al usar el broker el token final de la aplicación tiene como issuer a Keycloak 1.

Los imports son solo para desarrollo. Cambia secretos, contraseñas y configura HTTPS antes de usar este patrón en producción.

## API protegida

Después del login, el frontend envía el access token al endpoint `GET http://localhost:8083/api/status`.
El backend Spring Boot valida la firma mediante el JWKS interno de Keycloak 1 y comprueba el issuer público `http://localhost:8081/realms/mdrive`.
La respuesta muestra el estado, el usuario validado, el subject, el issuer y la hora de comprobación.

## Middleware

La tarjeta `Backend -> Middleware` del frontend llama a `GET http://localhost:8083/api/middleware/status`.
El backend valida el JWT recibido, reenvía el mismo bearer token al middleware por la red interna de Docker y el middleware lo reenvía finalmente a Spinpilot.
Spinpilot valida el token firmado por Keycloak 1 y exige el claim `auth_source=keycloak2`, que Keycloak 1 añade solo cuando el login se ha realizado mediante el broker TopSpin.
El endpoint interno del middleware es `GET http://middleware:8080/api/middleware/status` y su puerto publicado para comprobaciones es `http://localhost:8085`.
Los usuarios locales de Keycloak 1 reciben `403` en Spinpilot; los usuarios federados desde Keycloak 2 reciben `200`.

El frontend también muestra el header, el payload decodificado y el JWT completo para depuración de esta PoC.

## Spinpilot

`spinpilot` es un segundo resource server Java independiente. Valida tokens cuyo issuer sea el realm de Keycloak 1:

```text
http://localhost:8081/realms/mdrive
```

Su endpoint protegido es `GET http://localhost:8084/api/spinpilot/status` y el healthcheck público está en `http://localhost:8084/actuator/health`.
Además, exige `auth_source=keycloak2`: un usuario local de Keycloak 1 recibe `403`, mientras que un usuario autenticado mediante TopSpin es aceptado.

El token sigue siendo emitido por Keycloak 1 porque la aplicación usa el broker; la distinción de origen se conserva mediante la claim `auth_source`.