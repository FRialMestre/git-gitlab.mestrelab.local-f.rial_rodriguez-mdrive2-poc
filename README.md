# MDrive: Keycloak broker demo

Demo Docker de un frontend que autentica contra `keycloak1`. El realm `mdrive` de `keycloak1` publica `keycloak2` como proveedor OIDC, por lo que el usuario puede entrar con una cuenta local de Keycloak 1 o elegir Keycloak 2 en la pantalla de login.

## Arranque

Requiere Docker Compose v2.

```bash
docker compose up --build
```

Abre <http://localhost:5173>. Los servicios quedan disponibles en `http://localhost:8081`, `http://localhost:8082` y la API Java en `http://localhost:8083`.

Credenciales de prueba en Keycloak 2: `usuario-kc2` / `kc2pass`.

Para probar un usuario local en Keycloak 1, entra al panel de administración en `http://localhost:8081`, selecciona el realm `mdrive` y crea el usuario desde **Users**.

## Flujo

1. La app redirige al cliente `frontend` de `keycloak1`.
2. Keycloak 1 muestra su login local y el botón **Keycloak 2**.
3. Al elegirlo, Keycloak 1 redirige al realm `keycloak2`.
4. Keycloak 2 devuelve la identidad al endpoint broker de Keycloak 1.
5. La app recibe un token emitido por Keycloak 1 en ambos casos.

Los imports son solo para desarrollo. Cambia secretos, contraseñas y configura HTTPS antes de usar este patrón en producción.

## API protegida

Después del login, el frontend envía el access token al endpoint `GET http://localhost:8083/api/status`.
El backend Spring Boot valida la firma mediante el JWKS interno de Keycloak 1 y comprueba el issuer público `http://localhost:8081/realms/mdrive`.
La respuesta muestra el estado, el usuario validado, el subject, el issuer y la hora de comprobación.