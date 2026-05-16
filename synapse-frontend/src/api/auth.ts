import client from './client'
import type {
  AuthPermission,
  CreateUserRequest,
  CurrentUser,
  LoginRequest,
  LoginResponse,
  RoleName,
  RoleView,
  UserAdminView
} from '@/types'

export async function login(data: LoginRequest): Promise<LoginResponse> {
  const res = await client.post('/auth/login', data)
  return res.data
}

export async function logout(): Promise<void> {
  await client.post('/auth/logout')
}

export async function me(): Promise<CurrentUser> {
  const res = await client.get('/auth/me')
  return res.data
}

export async function listUsers(): Promise<UserAdminView[]> {
  const res = await client.get('/admin/users')
  return res.data
}

export async function createUser(data: CreateUserRequest): Promise<UserAdminView> {
  const res = await client.post('/admin/users', data)
  return res.data
}

export async function assignRoles(userId: string, roles: RoleName[]): Promise<UserAdminView> {
  const res = await client.put(`/admin/users/${userId}/roles`, { roles })
  return res.data
}

export async function setEnabled(userId: string, enabled: boolean): Promise<UserAdminView> {
  const res = await client.put(`/admin/users/${userId}/enabled`, { enabled })
  return res.data
}

export async function listRoles(): Promise<RoleView[]> {
  const res = await client.get('/admin/roles')
  return res.data
}

export async function assignRolePermissions(
  roleName: RoleName,
  permissions: AuthPermission[]
): Promise<RoleView> {
  const res = await client.put(`/admin/roles/${roleName}/permissions`, { permissions })
  return res.data
}
