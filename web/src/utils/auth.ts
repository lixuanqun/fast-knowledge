const TOKEN_KEY = 'kb_token'
const USER_KEY = 'kb_user'

/** localStorage 持久化层；运行时状态以 Pinia useAuthStore 为准 */
export function getToken(): string | null {
  return localStorage.getItem(TOKEN_KEY)
}

export function setToken(token: string) {
  localStorage.setItem(TOKEN_KEY, token)
}

export function clearAuth() {
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(USER_KEY)
}

export function removeToken() {
  localStorage.removeItem(TOKEN_KEY)
}

export function removeUser() {
  localStorage.removeItem(USER_KEY)
}

export function setUser(user: object) {
  localStorage.setItem(USER_KEY, JSON.stringify(user))
}

export function getUser<T>(): T | null {
  const raw = localStorage.getItem(USER_KEY)
  return raw ? JSON.parse(raw) : null
}
