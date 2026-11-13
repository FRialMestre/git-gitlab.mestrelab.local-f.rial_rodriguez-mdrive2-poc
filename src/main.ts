import Keycloak from 'keycloak-js'
import './style.css'

const keycloak = new Keycloak({
  url: 'https://mdrive2-poc-keycloak.mestrelab.local',
  realm: 'mdrive',
  clientId: 'frontend',
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
    return { header: 'Unable to decode', payload: 'Unable to decode' }
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

const checkBackend = async () => {
  const statusElement = document.querySelector<HTMLDivElement>('#backend-status')
  if (!statusElement) return

  try {
    await keycloak.updateToken(30)
    const rawTokenElement = document.querySelector<HTMLTextAreaElement>('#raw-token')
    if (rawTokenElement) rawTokenElement.value = keycloak.token ?? ''
    updateDecodedToken()
    const response = await fetch('/api/status', {
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
    statusElement.querySelector('small')!.textContent = `Validated user: ${successBody.user}`
  } catch (error) {
    statusElement.classList.add('is-error')
    statusElement.querySelector('strong')!.textContent = 'The backend rejected the token'
    statusElement.querySelector('small')!.textContent = error instanceof Error ? error.message : String(error)
  }
}

const checkMiddleware = async () => {
  const statusElement = document.querySelector<HTMLDivElement>('#middleware-status')
  const responseElement = document.querySelector<HTMLPreElement>('#spinpilot-response')
  if (!statusElement) return

  try {
    await keycloak.updateToken(30)
    const response = await fetch('/api/middleware/solvents', {
      headers: { Authorization: `Bearer ${keycloak.token}` },
    })

    const body = await response.json() as unknown
    if (!response.ok) {
      const message = typeof body === 'object' && body !== null && 'message' in body
        ? String(body.message)
        : `HTTP ${response.status}`
      throw new Error(message)
    }

    statusElement.classList.add('is-ok')
    statusElement.querySelector('strong')!.textContent = 'Response received'
    statusElement.querySelector('small')!.textContent = 'Middleware -> Connector -> Spinpilot'
    if (responseElement) responseElement.textContent = JSON.stringify(body, null, 2)
  } catch (error) {
    statusElement.classList.add('is-error')
    statusElement.querySelector('strong')!.textContent = error instanceof Error ? error.message : String(error)
    statusElement.querySelector('small')!.textContent = ''
    if (responseElement) responseElement.textContent = 'No response was received from SpinPilot'
  }
}

const render = () => {
  const token = keycloak.tokenParsed
  const username = String(token?.preferred_username ?? 'user')
  const issuer = String(token?.iss ?? '')
  const source = 'Keycloak 1'
  const tokenClaims = [
    ['User', username],
    ['Subject', String(token?.sub ?? '-')],
    ['Issuer', issuer],
    ['Scope', String(token?.scope ?? '-')],
    ['Expires', token?.exp ? new Date(token.exp * 1000).toLocaleString() : '-'],
    ['Session ID', String(token?.sid ?? '-')],
  ]

  app.innerHTML = `
    <div class="shell">
      <header class="topbar">
        <span class="mark">MD</span>
        <span class="brand">MDrive Access</span>
        <button id="logout" class="button button-quiet">Log out</button>
      </header>
      <section class="content">
        <p class="eyebrow">Active session</p>
        <h1>Hello, ${escapeHtml(username)}</h1>
        <p class="lede">Your identity has been validated by ${escapeHtml(source)}.</p>
        <div class="details">
          <div><span>User</span><strong>${escapeHtml(username)}</strong></div>
          <div><span>Issuer</span><strong>${escapeHtml(source)}</strong></div>
          <div><span>Authentication provider</span><strong>${escapeHtml(source)}</strong></div>
        </div>
        <div class="api-statuses">
          <div id="backend-status" class="backend-status">
            <span>Backend request</span>
            <strong>Checking...</strong>
            <small>Sending the bearer token</small>
          </div>
          <div id="middleware-status" class="backend-status">
            <span>SpinPilot request</span>
            <strong>Checking...</strong>
            <small>GET /solvents · forwarding the bearer token</small>
          </div>
        </div>
        <section class="spinpilot-response">
          <div class="response-heading">
            <p class="eyebrow">SpinPilot response</p>
            <span>GET /solvents · via /api/middleware/solvents</span>
          </div>
          <pre id="spinpilot-response">Waiting for a response...</pre>
        </section>
        <section class="token-panel">
          <div class="token-heading">
            <p class="eyebrow">Access token</p>
            <span>Claims read in the browser</span>
          </div>
          <div class="claims">
            ${tokenClaims.map(([label, value]) => `<div><span>${label}</span><strong>${escapeHtml(value)}</strong></div>`).join('')}
          </div>
          <div class="raw-token-heading">
            <span>Complete JWT for debugging</span>
            <button id="copy-token" class="button button-small" type="button">Copy</button>
          </div>
          <textarea id="raw-token" class="raw-token" readonly spellcheck="false">${escapeHtml(keycloak.token ?? '')}</textarea>
          <div class="decoded-heading">
            <span>Decoded JWT</span>
            <small>The content is encoded, not encrypted</small>
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
    ;(event.currentTarget as HTMLButtonElement).textContent = 'Copied'
  })

  updateDecodedToken()
  void checkBackend()
  void checkMiddleware()
}

const renderError = (error: unknown) => {
  app.innerHTML = `
    <main class="error-state">
    <p class="eyebrow">Unable to start</p>
    <h1>Keycloak is unavailable</h1>
    <p>Check that the Docker services are running and try again.</p>
      <code>${error instanceof Error ? error.message : String(error)}</code>
    </main>
  `
}

void keycloak
  .init({ onLoad: 'login-required', checkLoginIframe: false })
  .then(() => render())
  .catch(renderError)