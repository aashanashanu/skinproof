export type AuthSession = {
  accessToken: string
  idToken: string
  refreshToken: string
  expiresAt: number
  subject: string
  email: string
}

type TokenResponse = {
  access_token: string
  id_token: string
  refresh_token?: string
  expires_in: number
  token_type: string
}

type UserInfo = {
  sub: string
  email?: string
}

const SESSION_KEY = 'skinproof-cognito-session'
const OAUTH_STATE_KEY = 'skinproof-oauth-state'
const PKCE_VERIFIER_KEY = 'skinproof-pkce-verifier'
const configured = Boolean(
  import.meta.env.VITE_COGNITO_HOSTED_UI_DOMAIN
  && import.meta.env.VITE_COGNITO_CLIENT_ID
  && import.meta.env.VITE_COGNITO_ISSUER_URL,
)

function authDomain() {
  return String(import.meta.env.VITE_COGNITO_HOSTED_UI_DOMAIN).replace(/\/$/, '')
}

function clientId() {
  return String(import.meta.env.VITE_COGNITO_CLIENT_ID)
}

function redirectUri() {
  return window.location.origin + '/'
}

function encodeBase64Url(bytes: Uint8Array) {
  let binary = ''
  bytes.forEach((byte) => { binary += String.fromCharCode(byte) })
  return btoa(binary).replaceAll('+', '-').replaceAll('/', '_').replace(/=+$/, '')
}

function randomString(byteLength = 32) {
  return encodeBase64Url(crypto.getRandomValues(new Uint8Array(byteLength)))
}

async function createCodeChallenge(verifier: string) {
  const bytes = new TextEncoder().encode(verifier)
  return encodeBase64Url(new Uint8Array(await crypto.subtle.digest('SHA-256', bytes)))
}

function readSession(): AuthSession | null {
  try {
    const raw = sessionStorage.getItem(SESSION_KEY)
    return raw ? JSON.parse(raw) as AuthSession : null
  } catch {
    return null
  }
}

function saveSession(session: AuthSession) {
  sessionStorage.setItem(SESSION_KEY, JSON.stringify(session))
}

function clearSession() {
  sessionStorage.removeItem(SESSION_KEY)
}

async function exchangeToken(body: URLSearchParams): Promise<TokenResponse> {
  const response = await fetch(`${authDomain()}/oauth2/token`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body,
  })
  if (!response.ok) throw new Error('Sign-in could not be completed. Please try again.')
  return await response.json() as TokenResponse
}

async function buildSession(tokens: TokenResponse, previous?: AuthSession): Promise<AuthSession> {
  const userInfoResponse = await fetch(`${authDomain()}/oauth2/userInfo`, {
    headers: { Authorization: `Bearer ${tokens.access_token}` },
  })
  if (!userInfoResponse.ok) throw new Error('Signed-in profile could not be loaded.')
  const user = await userInfoResponse.json() as UserInfo
  return {
    accessToken: tokens.access_token,
    idToken: tokens.id_token,
    refreshToken: tokens.refresh_token ?? previous?.refreshToken ?? '',
    expiresAt: Date.now() + tokens.expires_in * 1000,
    subject: user.sub,
    email: user.email ?? '',
  }
}

export function identityIsConfigured() {
  return configured
}

export async function beginSignIn() {
  if (!configured) throw new Error('Account sign-in is not configured for this deployment.')
  const state = randomString()
  const verifier = randomString(48)
  const challenge = await createCodeChallenge(verifier)
  sessionStorage.setItem(OAUTH_STATE_KEY, state)
  sessionStorage.setItem(PKCE_VERIFIER_KEY, verifier)
  const url = new URL(`${authDomain()}/oauth2/authorize`)
  url.search = new URLSearchParams({
    client_id: clientId(),
    response_type: 'code',
    scope: 'openid email profile',
    redirect_uri: redirectUri(),
    state,
    code_challenge: challenge,
    code_challenge_method: 'S256',
  }).toString()
  window.location.assign(url.toString())
}

export async function completeSignInRedirect() {
  if (!configured) return null
  const currentUrl = new URL(window.location.href)
  const code = currentUrl.searchParams.get('code')
  const returnedState = currentUrl.searchParams.get('state')
  if (!code) return readSession()

  const expectedState = sessionStorage.getItem(OAUTH_STATE_KEY)
  const verifier = sessionStorage.getItem(PKCE_VERIFIER_KEY)
  sessionStorage.removeItem(OAUTH_STATE_KEY)
  sessionStorage.removeItem(PKCE_VERIFIER_KEY)
  currentUrl.searchParams.delete('code')
  currentUrl.searchParams.delete('state')
  currentUrl.searchParams.delete('scope')
  currentUrl.searchParams.delete('session_state')
  window.history.replaceState({}, document.title, currentUrl.pathname + currentUrl.search + currentUrl.hash)
  if (!returnedState || !expectedState || returnedState !== expectedState || !verifier) {
    throw new Error('Sign-in state verification failed. Start sign-in again.')
  }

  const tokens = await exchangeToken(new URLSearchParams({
    grant_type: 'authorization_code',
    client_id: clientId(),
    code,
    redirect_uri: redirectUri(),
    code_verifier: verifier,
  }))
  const session = await buildSession(tokens)
  saveSession(session)
  return session
}

export async function currentSession() {
  if (!configured) return null
  const session = readSession()
  if (!session) return null
  if (session.expiresAt > Date.now() + 60_000) return session
  if (!session.refreshToken) {
    throw new Error('Your sign-in expired. Sign in again to continue.')
  }
  try {
    const tokens = await exchangeToken(new URLSearchParams({
      grant_type: 'refresh_token',
      client_id: clientId(),
      refresh_token: session.refreshToken,
    }))
    const refreshed = await buildSession(tokens, session)
    saveSession(refreshed)
    return refreshed
  } catch {
    throw new Error('Your sign-in expired. Sign in again to continue.')
  }
}

export async function apiHeaders(
  demoWorkspaceId: string,
  extraHeaders: Record<string, string> = {},
  claimLegacyWorkspace = false,
) {
  const session = await currentSession()
  if (session) {
    return {
      ...extraHeaders,
      Authorization: `Bearer ${session.accessToken}`,
      ...(claimLegacyWorkspace ? { 'X-Demo-Workspace': demoWorkspaceId } : {}),
    }
  }
  return { ...extraHeaders, 'X-Demo-Workspace': demoWorkspaceId }
}

export async function signOut() {
  clearSession()
  if (!configured) return
  const url = new URL(`${authDomain()}/logout`)
  url.search = new URLSearchParams({ client_id: clientId(), logout_uri: redirectUri() }).toString()
  window.location.assign(url.toString())
}
