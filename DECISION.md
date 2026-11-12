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

## Configuración requerida

En KC1:

- IdP OIDC hacia KC2 con alias `topspin`;
- `Store Token` activado;
- cliente confidencial `backend-token-exchange`;
- permiso restringido de token exchange sobre `topspin`;
- token exchange legacy compatible con `requested_issuer`.

En KC2:

- cliente OIDC `mdrive-broker` para el broker de KC1;
- redirect URI exacta del endpoint del IdP `topspin` en KC1;
- emisión de tokens con la audience que Spinpilot acepta;
- configuración de issuer, scopes y roles propia de Spinpilot.

El cliente `mdrive-broker` no es el cliente usado por el backend para el
exchange. El secreto del backend debe corresponder al cliente
`backend-token-exchange` de KC1.

## Decisiones descartadas

### Token técnico compartido

Aunque sería mucho más facil de implementar, se descarta porque no conserva
la identidad individual necesaria para la trazabilidad de las operaciones en Spinpilot