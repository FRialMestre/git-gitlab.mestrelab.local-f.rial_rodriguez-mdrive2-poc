# MDrive y Spinpilot

PoC de MDrive con Keycloak 1 (KC1) como proveedor principal y broker OIDC
hacia un Keycloak 2 (KC2) externo, instalado junto a Spinpilot.

## Arquitectura actual

```text
Navegador
  -> Proxy TLS -> UI MDrive
  -> Proxy TLS -> KC1 (realm mdrive)

UI -> Backend MDrive (token KC1)
Backend -> KC1: token exchange legacy, requested_issuer=topspin
KC1 -> Backend: token externo de KC2 almacenado durante el login federado
Backend -> Middleware: operación + token KC2
Middleware -> Spinpilot: operación + mismo token KC2
```

La composición Docker ejecuta únicamente los servicios de MDrive:

- frontend/nginx;
- KC1;
- backend;
- middleware.

KC2 y Spinpilot están fuera de esta composición, en el servidor externo de
Bruker/Topspin:

- KC2: `https://mdrive-topspin50-win-dev.mestrelab.local:8443`;
- Spinpilot: `https://mdrive-topspin50-win-dev.mestrelab.local:8017`.

El proxy publica la UI y KC1:

- UI: <https://mdrive2-poc-ui.mestrelab.local>;
- KC1: <https://mdrive2-poc-keycloak.mestrelab.local>.

Backend y middleware sólo son accesibles dentro de la red Docker. El tráfico
interno entre estos servicios usa HTTP; las conexiones a KC1, KC2 y Spinpilot
usan HTTPS según sus endpoints configurados.

## Arranque

Requiere Docker Compose v2.

```bash
export MDRIVE_TOKEN_EXCHANGE_CLIENT_SECRET='<secret-del-cliente-app1-middleware>'
docker compose up --build -d
```

El backend recibe su configuración de token exchange desde Docker Compose:

- `MDRIVE_KC1_ISSUER_URI`;
- `MDRIVE_KC1_JWK_SET_URI`;
- `MDRIVE_KC1_TOKEN_URL`;
- `MDRIVE_TOKEN_EXCHANGE_CLIENT_ID`;
- `MDRIVE_TOKEN_EXCHANGE_CLIENT_SECRET` (obligatoria);
- `MDRIVE_TOKEN_EXCHANGE_REQUESTED_ISSUER`;
- `MDRIVE_MIDDLEWARE_BASE_URL`.

Las URLs, el client ID, el issuer solicitado y la URL del middleware tienen
valores por defecto adecuados para esta PoC. El secreto no tiene valor por
defecto y debe proporcionarse mediante el entorno o un mecanismo de secretos.

El middleware recibe desde Compose:

- `MDRIVE_KC2_ISSUER_URI`;
- `MDRIVE_KC2_JWK_SET_URI`;
- `MDRIVE_SPINPILOT_BASE_URL`.

El script [`keycloak2/configure-token-exchange.sh`](/home/jrial/Documents/POCs/mdrive-frontend+keycloak1+keycloak2/keycloak2/configure-token-exchange.sh)
pertenece al diseño anterior, en el que el exchange se ejecutaba en KC2. No es
necesario para el flujo actual y no debe ejecutarse contra el KC2 externo sin
revisarlo.

## Certificados

- El proxy proporciona el certificado TLS de la UI y KC1.
- KC1 confía en la CA de Bruker para poder comunicarse con KC2 durante el
  broker OIDC.
- El middleware confía en la CA de Bruker para KC2 y Spinpilot.
- El backend confía en [`certs/MestrelabCA.crt`](/home/jrial/Documents/POCs/mdrive-frontend+keycloak1+keycloak2/certs/MestrelabCA.crt)
  para conectarse a KC1.
- [`certs/root_ca_bruker.crt`](/home/jrial/Documents/POCs/mdrive-frontend+keycloak1+keycloak2/certs/root_ca_bruker.crt)
  se utiliza en KC1 y middleware.

Los Dockerfiles conservan las CAs Java por defecto y añaden la CA específica
del servicio. No se generan certificados TLS para la UI ni KC1.

## Login y token exchange

MDrive sólo conoce KC1. KC1 ofrece login local y el proveedor OIDC con alias
`topspin`, que redirige al usuario hacia KC2. Tras un login federado, KC1 emite
un token KC1 para el frontend y almacena el token externo de KC2.

Cuando un usuario federado solicita una operación contra Spinpilot:

1. el backend valida el token KC1;
2. comprueba `auth_source=topspin`;
3. solicita a KC1 un token exchange legacy con `requested_issuer=topspin`;
4. verifica que la respuesta contiene un `access_token`;
5. llama al middleware con el token KC2;
6. el middleware valida el token KC2 y llama a Spinpilot.

El backend realiza el exchange. El middleware no hace exchange, no administra
usuarios y no usa la Admin API de KC2. Un usuario local de KC1 puede utilizar
MDrive, pero no puede iniciar operaciones contra Spinpilot.

El token de KC2 debe tener issuer KC2 y la audience que Spinpilot requiera,
normalmente `spinpilot-api`. La configuración de esa audience pertenece a KC2.

## Endpoints de la PoC

- Estado de MDrive: `GET /api/status`;
- solvents de Spinpilot: `GET /api/middleware/solvents`;
- API externa de Spinpilot: `GET https://mdrive-topspin50-win-dev.mestrelab.local:8017/solvents`.

La UI muestra la respuesta JSON de solvents para facilitar las pruebas. No se
debe mostrar el JWT completo en una aplicación real.

## Configuración necesaria en KC1

KC1 debe tener:

- realm `mdrive`;
- proveedor OIDC hacia KC2 con alias `topspin`;
- `Store Token` activado en ese proveedor;
- cliente confidencial `app1-middleware`;
- secreto del cliente configurado en el backend;
- permiso de token exchange para `app1-middleware` sobre el proveedor `topspin`;
- soporte para el token exchange legacy usado por `requested_issuer`.

El cliente `mdrive-broker` es el cliente OIDC que KC1 utiliza contra KC2. No es
el cliente del backend para el token exchange.

## Credenciales

Las credenciales importadas son de desarrollo. Deben cambiarse antes de
cualquier uso real. No se deben incluir secretos reales, contraseñas de
administración ni tokens en el repositorio.
