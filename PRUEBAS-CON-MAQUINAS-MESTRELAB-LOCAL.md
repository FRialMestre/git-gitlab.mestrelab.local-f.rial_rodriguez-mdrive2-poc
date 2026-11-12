# Pruebas con las máquinas de Mestrelab

Este documento describe las pruebas del entorno actual, no una composición
local de KC2. KC2 y Spinpilot están instalados fuera de Docker.

## Endpoints

Servidor de Mdrive:

- UI: <https://mdrive2-poc-ui.mestrelab.local>;
- KC1: <https://mdrive2-poc-keycloak.mestrelab.local>.

Servidor externo de Bruker/Topspin:

- KC2: <https://mdrive-topspin50-win-dev.mestrelab.local:8443>;
- Spinpilot: <https://mdrive-topspin50-win-dev.mestrelab.local:8017>.

## Preparación

1. Verificar resolución DNS desde el navegador y desde los contenedores.
2. Verificar que el proxy publica UI y KC1 con certificados válidos.
3. Verificar que KC1 confía en la CA de Bruker.
4. Verificar que el middleware confía en
   [`certs/root_ca_bruker.crt`](/home/jrial/Documents/POCs/mdrive-frontend+keycloak1+keycloak2/certs/root_ca_bruker.crt).
5. Verificar que el backend confía en
   [`certs/MestrelabCA.crt`](/home/jrial/Documents/POCs/mdrive-frontend+keycloak1+keycloak2/certs/MestrelabCA.crt).
6. Confirmar en KC1 el IdP OIDC con alias `topspin` y `Store Token` activado.
7. Confirmar el cliente confidencial `app1-middleware` y su permiso de token
   exchange sobre `topspin`.
8. Confirmar en KC2 que `mdrive-broker` tiene la redirect URI exacta del
   endpoint de KC1:

   ```text
   https://mdrive2-poc-keycloak.mestrelab.local/realms/mdrive/broker/topspin/endpoint
   ```

   El path debe coincidir con el alias real del IdP (`topspin`).

## Prueba de login local

1. Abrir la UI.
2. Iniciar sesión con un usuario local de KC1.
3. Ejecutar `GET /api/status`.
4. Confirmar que el backend valida un token cuyo issuer es KC1.
5. Ejecutar la llamada de Spinpilot.
6. Confirmar que no se contacta con KC2, middleware ni Spinpilot para el
   usuario local y que la operación se rechaza o aparece como `skipped`.

## Prueba de login federado

1. Abrir la UI y seleccionar el proveedor `topspin`.
2. Completar el login en KC2.
3. Confirmar que KC1 devuelve un token KC1 al frontend.
4. Confirmar que el backend reconoce `auth_source=topspin`.
5. Ejecutar `GET /api/middleware/solvents`.
6. Confirmar que el backend llama al endpoint de token de KC1 con
   `requested_issuer=topspin`.
7. Confirmar que la respuesta del exchange contiene un token emitido por KC2.
8. Confirmar que el middleware valida ese token y llama a Spinpilot.
9. Confirmar que la respuesta contiene el array JSON recibido desde Spinpilot.

El frontend no debe recibir el token KC2. El backend tampoco debe registrar el
JWT completo.

## Pruebas negativas

| Caso | Resultado esperado |
| --- | --- |
| Token KC1 enviado al middleware | Rechazo por issuer |
| Usuario local solicita Spinpilot | Rechazo sin llamada externa |
| `auth_source` ausente o incorrecto | Rechazo |
| Exchange sin token externo almacenado | Error explícito; no se llama a Spinpilot |
| Token KC2 expirado | Rechazo del middleware o Spinpilot |
| Audience incorrecta | Rechazo |
| KC2 no disponible | Error de integración; MDrive local continúa |
| Spinpilot no disponible | Error controlado con timeout |

## Comandos de validación local

```bash
docker compose config
docker compose build backend middleware
mvn -q -f backend/pom.xml test
mvn -q -f middleware/pom.xml test
git diff --check
```

Las pruebas contra KC2 y Spinpilot requieren conectividad real, certificados
válidos y usuarios configurados en los dos Keycloak. No se debe ejecutar el
script antiguo de configuración de token exchange contra KC2 sin confirmar que
el flujo legacy anterior sigue siendo necesario.
