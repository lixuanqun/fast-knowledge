import request from '@/utils/request'

export interface LoginResult {
  token: string
  userId: number
  username: string
  displayName: string
  role: string
  mustChangePassword?: boolean
}

export function login(username: string, password: string) {
  return request.post<any, { data: LoginResult }>('/auth/login', { username, password })
}

export function logout() {
  return request.post('/auth/logout')
}

export function ldapLogin(username: string, password: string) {
  return request.post<any, { data: LoginResult }>('/auth/ldap/login', { username, password })
}

export function getOidcAuthorizeUrl() {
  return request.get<any, { data: { authorizationUrl: string } }>('/auth/oidc/authorize')
}

export function completeSetup(instanceName: string, newPassword: string) {
  return request.post('/auth/setup', { instanceName, newPassword })
}

export function changePassword(oldPassword: string, newPassword: string) {
  return request.post('/users/change-password', { oldPassword, newPassword })
}

export function getCurrentUser() {
  return request.get('/users/me')
}
