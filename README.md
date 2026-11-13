# MDrive y Spinpilot

PoC de MDrive con Keycloak 1 (KC1) como proveedor principal. Keycloak 2 (KC2),
instalado junto a Spinpilot, utiliza KC1 como Identity Provider OIDC.

La composición contiene tres aplicaciones:

- `frontend`: UI de MDrive servida por nginx;
- `backend`: API que valida KC1, provisiona KC2 y realiza el token exchange;
- `middleware`: API interna que valida el token KC2 y llama a Spinpilot.

## Arquitectura actual

```text
Navegador
  -> Proxy TLS -> UI MDrive
  -> Proxy TLS -> KC1 (realm mdrive)

UI -> Backend MDrive (token KC1)
Backend -> KC2: token exchange con subject_issuer=mdrive
Backend -> KC2: provisioning/link federado idempotente por username
KC2 -> Backend: token KC2
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
export MDRIVE_TOKEN_EXCHANGE_CLIENT_ID='mdrive-backend-token-exchange'
export MDRIVE_TOKEN_EXCHANGE_CLIENT_SECRET='<secret-del-cliente-mdrive-backend-token-exchange>'
export MDRIVE_KC2_PROVISIONER_CLIENT_ID='mdrive-backend-provisioner'
export MDRIVE_KC2_PROVISIONER_CLIENT_SECRET='<secret-del-cliente-mdrive-backend-provisioner>'
docker compose up --build -d
```

El backend recibe su configuración de token exchange desde Docker Compose:

- `MDRIVE_KC1_ISSUER_URI`;
- `MDRIVE_KC1_JWK_SET_URI`;
- `MDRIVE_KC2_TOKEN_URL`;
- `MDRIVE_TOKEN_EXCHANGE_CLIENT_ID`;
- `MDRIVE_TOKEN_EXCHANGE_CLIENT_SECRET` (obligatoria);
- `MDRIVE_TOKEN_EXCHANGE_SUBJECT_ISSUER`;
- `MDRIVE_TOKEN_EXCHANGE_AUDIENCE`;
- `MDRIVE_KC2_ADMIN_BASE_URL`;
- `MDRIVE_KC2_PROVISIONING_TOKEN_URL`;
- `MDRIVE_KC2_PROVISIONER_CLIENT_ID`;
- `MDRIVE_KC2_PROVISIONER_CLIENT_SECRET` (obligatoria);
- `MDRIVE_KC2_IDP_ALIAS`;
- `MDRIVE_MIDDLEWARE_BASE_URL`.

Valores funcionales relevantes de esta PoC:

```text
MDRIVE_KC1_ISSUER_URI=https://mdrive2-poc-keycloak.mestrelab.local/realms/mdrive
MDRIVE_KC2_TOKEN_URL=https://mdrive-topspin50-win-dev.mestrelab.local:8443/auth/realms/Bruker/protocol/openid-connect/token
MDRIVE_TOKEN_EXCHANGE_CLIENT_ID=mdrive-backend-token-exchange
MDRIVE_TOKEN_EXCHANGE_SUBJECT_ISSUER=mdrive
MDRIVE_TOKEN_EXCHANGE_AUDIENCE=TopSpinAutomation
MDRIVE_KC2_PROVISIONER_CLIENT_ID=mdrive-backend-provisioner
MDRIVE_KC2_IDP_ALIAS=mdrive
```

Los secretos deben proporcionarse mediante el entorno o un mecanismo de
secretos. No deben copiarse a la documentación, al frontend ni al repositorio.

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
  para conectarse a KC1 y en la CA de Bruker para KC2.
- [`certs/root_ca_bruker.crt`](/home/jrial/Documents/POCs/mdrive-frontend+keycloak1+keycloak2/certs/root_ca_bruker.crt)
  se utiliza en KC1 y middleware.

Los Dockerfiles conservan las CAs Java por defecto y añaden la CA específica
del servicio. No se generan certificados TLS para la UI ni KC1.

La JVM de KC2 también debe confiar en la CA que firma el certificado de KC1.
En el servidor Windows de KC2 se configura mediante `truststore-paths` o un
truststore JKS utilizado por el servicio NSSM de Keycloak. Copiar el
certificado a una carpeta no es suficiente.

## Login, provisioning y token exchange

MDrive sólo conoce KC1. KC1 ofrece login local y emite el token KC1 para el
frontend. KC2 mantiene el Identity Provider OIDC `mdrive` hacia KC1.

Cuando un usuario solicita una operación contra Spinpilot:

1. el backend valida el token KC1;
2. extrae `preferred_username` y `sub`;
3. busca el mismo username en KC2;
4. crea o verifica de forma idempotente el enlace federado `mdrive` entre el
   usuario KC2 y el `sub` de KC1;
5. solicita a KC2 un token exchange usando `subject_issuer=mdrive` y
   `audience=TopSpinAutomation`;
6. llama al middleware con el token KC2;
7. el middleware valida el token KC2 y llama a Spinpilot.

El backend realiza el provisioning y el exchange. El middleware no hace
exchange, no administra usuarios y no usa la Admin API de KC2 para operaciones
de negocio.

El token de KC2 debe tener como audience `TopSpinAutomation`, que es el
client ID utilizado por la API Spinpilot en el realm `Bruker`. El cliente
`TopSpinAutomation-rights` aparece como `azp` en un token válido y no es la
audience que se debe solicitar.

## Endpoints de la PoC

- Estado de MDrive: `GET /api/status`;
- solvents de Spinpilot: `GET /api/middleware/solvents`;
- API externa de Spinpilot: `GET https://mdrive-topspin50-win-dev.mestrelab.local:8017/solvents`.

La UI muestra la respuesta JSON de solvents para facilitar las pruebas. No se
debe mostrar el JWT completo en una aplicación real.

## Configuración necesaria en KC1 y KC2

KC1 debe tener:

- realm `mdrive`;
- cliente confidencial `kc2-to-kc1-idp`;
- audience mapper en el cliente `frontend` para incluir
  `aud=kc2-to-kc1-idp`;
- redirect URI del broker de KC2;

KC2 debe tener:

- Identity Provider OIDC `mdrive` apuntando a KC1;
- cliente confidencial `mdrive-backend-token-exchange` para token exchange;
- cliente confidencial separado `mdrive-backend-provisioner` con service account;
- cliente destino `TopSpinAutomation`, utilizado como audience;
- permisos de token exchange en `TopSpinAutomation` para permitir al cliente
  `mdrive-backend-token-exchange`;
- permisos mínimos para consultar usuarios y crear enlaces federados;
- alias `mdrive` aceptado como `subject_issuer`;
- mapping único y exacto `username KC1 == username KC2`;
- emisión de tokens con audience `TopSpinAutomation`.

El cliente `kc2-to-kc1-idp` permite a KC2 utilizar KC1 como Identity Provider.
El cliente `mdrive-backend-token-exchange` es el que utiliza el backend para
solicitar tokens a KC2. El cliente `mdrive-backend-provisioner` se usa únicamente
para el provisioning idempotente de enlaces federados y nunca debe compartir
credenciales con el cliente de token exchange.

## Provisioning automático en KC2

Antes del token exchange, el backend debe garantizar el enlace federado:

```text
KC1 preferred_username == KC2 username
KC1 sub -> KC2 federated identity provider mdrive
```

La operación debe ser idempotente. Si el enlace ya existe y coincide, se
continúa; si existe otro enlace o el username no es único, la operación se
rechaza. El cliente provisioner debe usar un secret separado, no debe poder
administrar contraseñas o roles y sus credenciales no deben llegar al
frontend.

## Credenciales

Las credenciales importadas son de desarrollo. Deben cambiarse antes de
cualquier uso real. No se deben incluir secretos reales, contraseñas de
administración ni tokens en el repositorio.

### Configuración del Identity Provider `mdrive`

En KC2, realm `Bruker`, el proveedor debe utilizar:

```text
Alias: mdrive
Issuer:
  https://mdrive2-poc-keycloak.mestrelab.local/realms/mdrive
JWKS:
  https://mdrive2-poc-keycloak.mestrelab.local/realms/mdrive/protocol/openid-connect/certs
Validate signatures: activado
Use JWKS URL: activado
Client ID: kc2-to-kc1-idp
```

El issuer y la URL JWKS de KC1 no llevan `/auth`.

### Permisos de token exchange en KC2

En `Clients -> TopSpinAutomation -> Permissions`, se deben activar los
permisos y autorizar mediante una `Client Policy` al cliente:

```text
mdrive-backend-token-exchange
```

La relación es:

```text
Solicitante: mdrive-backend-token-exchange
Destino/audience: TopSpinAutomation
```

## Validación y diagnóstico

```bash
docker compose config
mvn -q -f backend/pom.xml test
mvn -q -f middleware/pom.xml test
docker compose build backend middleware
git diff --check
```

Errores habituales de integración:

- `PKIX path building failed`: la JVM de KC2 no confía en la CA de KC1;
- `Audience not found`: el audience solicitado no existe como cliente en KC2;
- `Client not allowed to exchange`: falta autorizar
  `mdrive-backend-token-exchange` en los permisos de token exchange de
  `TopSpinAutomation`.

El endpoint JWKS de KC1 puede probarse desde Windows con:

```powershell
curl.exe --cacert "C:\ruta\mestrelab-ca.crt" --ssl-no-revoke `
  "https://mdrive2-poc-keycloak.mestrelab.local/realms/mdrive/protocol/openid-connect/certs"
```

`--ssl-no-revoke` sólo sirve para diagnosticar una CRL/OCSP no disponible y no
debe utilizarse como solución permanente.
