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
6. Confirmar en KC1 el cliente `kc2-to-kc1-idp` y su audience mapper.
7. Confirmar en KC2 el Identity Provider `mdrive` apuntando a KC1.
8. Confirmar los clientes `mdrive-backend-token-exchange` y
   `mdrive-backend-provisioner`, con permisos separados.
9. Confirmar en KC1 que el cliente `kc2-to-kc1-idp` tiene la redirect URI:

   ```text
   https://mdrive-topspin50-win-dev.mestrelab.local:8443/auth/realms/Bruker/broker/mdrive/endpoint
   ```

   El path debe coincidir con el alias real del IdP (`mdrive`).
10. Confirmar en KC2 que `TopSpinAutomation` existe como cliente y que sus
    permisos de token exchange autorizan a `mdrive-backend-token-exchange`.
11. Confirmar que el Identity Provider `mdrive` usa el issuer y JWKS de KC1
    sin el prefijo `/auth`, con la validación de firma activada.

## Prueba de login y provisioning

1. Abrir la UI.
2. Iniciar sesión con un usuario local de KC1.
3. Ejecutar `GET /api/status`.
4. Confirmar que el backend valida un token cuyo issuer es KC1.
5. Ejecutar `GET /api/middleware/solvents`.
6. Confirmar que el backend busca el username en KC2.
7. Confirmar que crea el enlace `mdrive` si no existe.
8. Repetir la llamada y confirmar que el provisioning es idempotente.
9. Confirmar que el exchange solicita `audience=TopSpinAutomation`.
10. Confirmar que el exchange devuelve un token emitido por KC2 con audience
    `TopSpinAutomation`.
11. Confirmar que el middleware valida ese token y llama a Spinpilot.

El frontend no debe recibir el token KC2. El backend tampoco debe registrar el
JWT completo.

## Pruebas negativas

| Caso | Resultado esperado |
| --- | --- |
| Token KC1 enviado al middleware | Rechazo por issuer |
| Usuario KC1 no existe en KC2 | Rechazo de provisioning |
| Username duplicado | Rechazo de provisioning |
| Enlace federado inconsistente | Rechazo sin sobrescribir |
| Cliente provisioner sin permisos | Error explícito; no se llama a Spinpilot |
| Token KC2 expirado | Rechazo del middleware o Spinpilot |
| Audience inexistente | `Audience not found` |
| Audience sin permiso de exchange | `Client not allowed to exchange` |
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

El backend se empaqueta como `backend-0.1.0.jar` y el middleware como
`middleware-0.1.0.jar`.

## Diagnóstico TLS de KC2

KC2 debe poder descargar:

```text
https://mdrive2-poc-keycloak.mestrelab.local/realms/mdrive/protocol/openid-connect/certs
```

Desde el servidor Windows de KC2 se puede probar la cadena con:

```powershell
curl.exe --cacert "C:\ruta\mestrelab-ca.crt" --ssl-no-revoke `
  "https://mdrive2-poc-keycloak.mestrelab.local/realms/mdrive/protocol/openid-connect/certs"
```

Si el log de KC2 contiene `PKIX path building failed`, la CA no está
disponible para la JVM que ejecuta el servicio NSSM. `truststore-paths` debe
apuntar a una ruta efectiva para esa instalación; si es necesario, importar la
CA en un JKS y configurar la ruta absoluta. Un error TLS de KC2 se presenta
hacia el backend como `invalid_token` durante la validación de firma.

Las pruebas contra KC2 y Spinpilot requieren conectividad real, certificados
válidos y usuarios configurados en los dos Keycloak. No se debe ejecutar el
script antiguo de configuración de token exchange contra KC2 sin confirmar que
el flujo legacy anterior sigue siendo necesario.
