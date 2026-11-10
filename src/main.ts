import Keycloak from 'keycloak-js'
import './style.css'

const keycloak = new Keycloak({
  url: 'http://localhost:8082',
  realm: 'keycloak2',
  clientId: 'mdrive-frontend-kc2',
})

const app = document.querySelector<HTMLDivElement>('#app')!

const escapeHtml = (value: string) => value.replace(/[&<>"']/g, (character) => ({
  '&': '&amp;',
  '<': '&lt;',
  '>': '&gt;',
  '"': '&quot;',
  "'": '&#039;',
})[character]!)

const decodeJwtPart = (part: string) => {
  const base64 = part.replace(/-/g, '+').replace(/_/g, '/')
  const padded = base64.padEnd(Math.ceil(base64.length / 4) * 4, '=')
  const bytes = Uint8Array.from(atob(padded), (character) => character.charCodeAt(0))
  return JSON.stringify(JSON.parse(new TextDecoder().decode(bytes)), null, 2)
}

const getDecodedToken = (rawToken: string) => {
  const parts = rawToken.split('.')
  try {
    return {
      header: decodeJwtPart(parts[0]),
      payload: decodeJwtPart(parts[1]),
    }
  } catch {
    return { header: 'No se pudo decodificar', payload: 'No se pudo decodificar' }
  }
}

const updateDecodedToken = () => {
  const decoded = getDecodedToken(keycloak.token ?? '')
  document.querySelector<HTMLElement>('#decoded-header')!.textContent = decoded.header
  document.querySelector<HTMLElement>('#decoded-payload')!.textContent = decoded.payload
}

type BackendStatus = {
  status: string
  message: string
  user: string
  issuer: string
  checkedAt: string
}

type MiddlewareStatus = BackendStatus & { application: string }

const checkBackend = async () => {
  const statusElement = document.querySelector<HTMLDivElement>('#backend-status')
  if (!statusElement) return

  try {
    await keycloak.updateToken(30)
    const rawTokenElement = document.querySelector<HTMLTextAreaElement>('#raw-token')
    if (rawTokenElement) rawTokenElement.value = keycloak.token ?? ''
    updateDecodedToken()
    const response = await fetch('http://localhost:8083/api/status', {
      headers: { Authorization: `Bearer ${keycloak.token}` },
    })
    const body = await response.json() as BackendStatus | { message?: string }

    if (!response.ok) {
      const message = 'message' in body && body.message ? body.message : `HTTP ${response.status}`
      throw new Error(message)
    }

    const successBody = body as BackendStatus
    statusElement.classList.add('is-ok')
    statusElement.querySelector('strong')!.textContent = successBody.message
    statusElement.querySelector('small')!.textContent = `Usuario validado: ${successBody.user}`
  } catch (error) {
    statusElement.classList.add('is-error')
    statusElement.querySelector('strong')!.textContent = 'El backend rechazó el token'
    statusElement.querySelector('small')!.textContent = error instanceof Error ? error.message : String(error)
  }
}

const checkMiddleware = async () => {
  const statusElement = document.querySelector<HTMLDivElement>('#middleware-status')
  if (!statusElement) return

  try {
    await keycloak.updateToken(30)
    const response = await fetch('http://localhost:8083/api/middleware/status', {
      headers: { Authorization: `Bearer ${keycloak.token}` },
    })

    const body = await response.json() as MiddlewareStatus | { message?: string }
    if (!response.ok) {
      const message = 'message' in body && body.message ? body.message : `HTTP ${response.status}`
      throw new Error(message)
    }

    const successBody = body as MiddlewareStatus
    statusElement.classList.add('is-ok')
    statusElement.querySelector('strong')!.textContent = successBody.message
    statusElement.querySelector('small')!.textContent = `Respuesta del middleware: ${successBody.user}`
  } catch (error) {
    statusElement.classList.add('is-error')
    statusElement.querySelector('strong')!.textContent = error instanceof Error ? error.message : String(error)
    statusElement.querySelector('small')!.textContent = ''
  }
}

const render = () => {
  const token = keycloak.tokenParsed
  const username = String(token?.preferred_username ?? 'usuario')
  const issuer = String(token?.iss ?? '')
  const source = 'Keycloak 2'
  const tokenClaims = [
    ['Usuario', username],
    ['Subject', String(token?.sub ?? '-')],
    ['Issuer', issuer],
    ['Scope', String(token?.scope ?? '-')],
    ['Expira', token?.exp ? new Date(token.exp * 1000).toLocaleString() : '-'],
    ['Session ID', String(token?.sid ?? '-')],
  ]

  app.innerHTML = `
    <div class="shell">
      <header class="topbar">
        <span class="mark">MD</span>
        <span class="brand">MDrive Access</span>
        <button id="logout" class="button button-quiet">Cerrar sesión</button>
      </header>
      <section class="content">
        <p class="eyebrow">Sesión activa</p>
        <h1>Hola, ${escapeHtml(username)}</h1>
        <p class="lede">Tu identidad ha sido validada por ${escapeHtml(source)}.</p>
        <div class="details">
          <div><span>Usuario</span><strong>${escapeHtml(username)}</strong></div>
          <div><span>Emisor</span><strong>${escapeHtml(source)}</strong></div>
          <div><span>Proveedor de autenticación</span><strong>${escapeHtml(source)}</strong></div>
        </div>
        <div class="api-statuses">
          <div id="backend-status" class="backend-status">
            <span>Petición al Backend</span>
            <strong>Comprobando...</strong>
            <small>Enviando el bearer token</small>
          </div>
          <div id="middleware-status" class="backend-status">
            <span>Petición al Backend -> Middleware -> Spinpilot</span>
            <strong>Comprobando...</strong>
            <small>Propagando el bearer token</small>
          </div>
        </div>
        <section class="token-panel">
          <div class="token-heading">
            <p class="eyebrow">Access token</p>
            <span>Claims leídas en el navegador</span>
          </div>
          <div class="claims">
            ${tokenClaims.map(([label, value]) => `<div><span>${label}</span><strong>${escapeHtml(value)}</strong></div>`).join('')}
          </div>
          <div class="raw-token-heading">
            <span>JWT completo para depuración</span>
            <button id="copy-token" class="button button-small" type="button">Copiar</button>
          </div>
          <textarea id="raw-token" class="raw-token" readonly spellcheck="false">${escapeHtml(keycloak.token ?? '')}</textarea>
          <div class="decoded-heading">
            <span>JWT decodificado sin cifrado</span>
            <small>El contenido está codificado, no cifrado</small>
          </div>
          <div class="decoded-token">
            <div>
              <span>Header</span>
              <pre id="decoded-header"></pre>
            </div>
            <div>
              <span>Payload / claims</span>
              <pre id="decoded-payload"></pre>
            </div>
          </div>
        </section>
      </section>
    </div>
  `

  document.querySelector<HTMLButtonElement>('#logout')?.addEventListener('click', () => {
    void keycloak.logout({ redirectUri: window.location.origin })
  })

  document.querySelector<HTMLButtonElement>('#copy-token')?.addEventListener('click', async (event) => {
    await navigator.clipboard.writeText(keycloak.token ?? '')
    ;(event.currentTarget as HTMLButtonElement).textContent = 'Copiado'
  })

  updateDecodedToken()
  void checkBackend()
  void checkMiddleware()
}

const renderError = (error: unknown) => {
  app.innerHTML = `
    <main class="error-state">
      <p class="eyebrow">No se pudo iniciar</p>
      <h1>Keycloak no está disponible</h1>
      <p>Comprueba que los servicios Docker estén levantados y vuelve a intentarlo.</p>
      <code>${error instanceof Error ? error.message : String(error)}</code>
    </main>
  `
}

void keycloak
  .init({ onLoad: 'login-required', checkLoginIframe: false })
  .then(() => render())
  .catch(renderError)