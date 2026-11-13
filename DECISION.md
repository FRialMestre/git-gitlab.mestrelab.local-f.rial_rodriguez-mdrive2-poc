# Decisión de arquitectura

## Flujo elegido

MDrive utiliza **KC1 como issuer del frontend**, **KC2 como Identity Provider
OIDC de KC1 para el exchange**, y **token exchange ejecutado por el backend
contra KC2**.

```text
Usuario -> KC1
Frontend -> Backend con token KC1
Backend -> KC2 Admin API: provisioning/link por username y sub
Backend -> KC2 con subject_issuer=mdrive
KC2 -> Backend con token KC2
Backend -> Middleware -> Spinpilot con token KC2
```

## Motivos

- El frontend sólo necesita conocer KC1.
- KC1 centraliza el login local.
- El backend es el único componente que decide si una operación contra
  Spinpilot está autorizada.
- Spinpilot recibe un token emitido por su propio KC2 y puede mantener la
  trazabilidad individual del usuario.
- El middleware no necesita credenciales administrativas ni lógica de
  identidad.

## Configuración requerida

En KC1:

- cliente confidencial `kc2-to-kc1-idp`;
- audience mapper para `kc2-to-kc1-idp` en el token de `frontend`.

En KC2:

- Identity Provider OIDC `mdrive` apuntando a KC1;
- cliente `mdrive-backend-token-exchange` autorizado para token exchange;
- cliente separado `mdrive-backend-provisioner` con permisos mínimos de Admin
  API;
- cliente destino `TopSpinAutomation`, con permisos de token exchange para
  `mdrive-backend-token-exchange`;
- audience solicitada: `TopSpinAutomation`;
- mapping único por username y enlace federado por `sub`;
- emisión de tokens con la audience que Spinpilot acepta.

El backend no reutiliza el cliente provisioner para el token exchange. El
provisioning debe ser idempotente y rechazar enlaces ambiguos o inconsistentes.

## Decisiones descartadas

### Token técnico compartido

Aunque sería mucho más facil de implementar, se descarta porque no conserva
la identidad individual necesaria para la trazabilidad de las operaciones en Spinpilot