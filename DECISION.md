# Decisión de arquitectura

## Flujo elegido

MDrive utiliza **Identity Brokering en KC1** y **token exchange ejecutado por el
backend contra KC1**.

```text
Usuario -> KC1 -> KC2 durante el login federado
Frontend -> Backend con token KC1
Backend -> KC1 con requested_issuer=topspin
KC1 -> Backend con token externo KC2
Backend -> Middleware -> Spinpilot con token KC2
```

## Motivos

- El frontend sólo necesita conocer KC1.
- KC1 centraliza el login local y federado.
- El backend es el único componente que decide si una operación contra
  Spinpilot está autorizada.
- Spinpilot recibe un token emitido por su propio KC2 y puede mantener la
  trazabilidad individual del usuario.
- El middleware no necesita credenciales administrativas ni lógica de
  identidad.
- Se elimina la búsqueda de usuarios por username/email en KC2.
- Se elimina la creación de federated identities desde una llamada de negocio.

## Configuración requerida

En KC1:

- IdP OIDC hacia KC2 con alias `topspin`;
- `Store Token` activado;
- cliente confidencial `app1-middleware`;
- permiso restringido de token exchange sobre `topspin`;
- token exchange legacy compatible con `requested_issuer`.

En KC2:

- cliente OIDC `mdrive-broker` para el broker de KC1;
- redirect URI exacta del endpoint del IdP `topspin` en KC1;
- emisión de tokens con la audience que Spinpilot acepta;
- configuración de issuer, scopes y roles propia de Spinpilot.

El cliente `mdrive-broker` no es el cliente usado por el backend para el
exchange. El secreto del backend debe corresponder al cliente
`app1-middleware` de KC1.

## Decisiones descartadas

### Exchange ejecutado en KC2

Se descarta porque el flujo actual no depende de la Admin API ni de un token
exchange cross-server iniciado contra KC2. KC1 conserva el token externo y el
backend solicita su devolución mediante el exchange del propio KC1.

### Token técnico compartido

Se descarta porque no conserva la identidad individual necesaria para la
trazabilidad de las operaciones en Spinpilot.

### Gestión manual de usuarios en KC2

Se descarta la búsqueda por username/email y la creación de enlaces desde el
backend durante una operación de negocio. La relación brokered la gestiona KC1
como parte del login.

## Consecuencias

- El usuario federado debe haber completado correctamente el login en KC1 para
  que KC1 disponga del token externo.
- La caducidad o ausencia del token externo puede requerir repetir el login.
- El token exchange legacy debe considerarse una superficie sensible y
  mantenerse con permisos mínimos.
- La configuración del audience final corresponde a KC2/Spinpilot, no al
  middleware.
