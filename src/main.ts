import Keycloak from 'keycloak-js'
import './style.css'

const keycloak = new Keycloak({
  url: 'http://localhost:8081',
  realm: 'mdrive',
  clientId: 'frontend',
})

const app = document.querySelector<HTMLDivElement>('#app')!

const render = () => {
  const username = keycloak.tokenParsed?.preferred_username ?? 'usuario'
  const issuer = keycloak.tokenParsed?.iss ?? ''
  const source = issuer.includes('keycloak2') ? 'Keycloak 2 mediante el broker' : 'Keycloak 1'

  app.innerHTML = `
    <div class="shell">
      <header class="topbar">
        <span class="mark">MD</span>
        <span class="brand">MDrive Access</span>
        <button id="logout" class="button button-quiet">Cerrar sesión</button>
      </header>
      <section class="content">
        <p class="eyebrow">Sesión activa</p>
        <h1>Hola, ${username}</h1>
        <p class="lede">Tu identidad ha sido validada por ${source}.</p>
        <div class="details">
          <div><span>Usuario</span><strong>${username}</strong></div>
          <div><span>Emisor</span><strong>Keycloak 1</strong></div>
          <div><span>Federación</span><strong>${source}</strong></div>
        </div>
      </section>
    </div>
  `

  document.querySelector<HTMLButtonElement>('#logout')?.addEventListener('click', () => {
    void keycloak.logout({ redirectUri: window.location.origin })
  })
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