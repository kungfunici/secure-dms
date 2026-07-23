const API_BASE = 'http://localhost:8080'

let accessToken: string | null = null

export function setToken(token: string | null) {
  accessToken = token
}

export function getToken() {
  return accessToken
}

async function request(path: string, options: RequestInit = {}) {
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    ...(options.headers as Record<string, string>),
  }
  if (accessToken) {
    headers['Authorization'] = `Bearer ${accessToken}`
  }
  const res = await fetch(`${API_BASE}${path}`, { ...options, headers })
  if (!res.ok) {
    const body = await res.json().catch(() => ({ error: res.statusText }))
    throw new Error(body.error || 'Request failed')
  }
  const contentType = res.headers.get('content-type')
  if (contentType?.includes('application/json')) {
    return res.json()
  }
  return res
}

export interface AuthResponse {
  id: number
  token: string
  refreshToken?: string
  tokenType: string
  username: string
  role: string
}

export interface DocumentResponse {
  id: number
  originalFilename: string
  contentType: string
  fileSize: number
  description: string
  documentType?: string
  ownerUsername: string
  permission?: string
  folderId?: number
  folderName?: string
  deletedAt?: string
  uploadedAt: string
  currentVersion?: number
  versionCount?: number
  favorite?: boolean
  tags?: string[]
  retentionAt?: string
  legalHold?: boolean
}

export interface VersionResponse {
  id: number
  versionNumber: number
  fileSize: number
  contentType: string
  uploadedByUsername: string
  createdAt: string
}

export interface FolderResponse {
  id: number
  name: string
  documentCount: number
  createdAt: string
}

export interface PermissionResponse {
  id: number
  userId: number
  username: string
  permissionType: string
  grantedAt: string
}

export interface TagResponse {
  id: number
  name: string
  color: string
}

export interface NotificationResponse {
  id: number
  type: string
  title: string
  message?: string
  documentId?: number
  read: boolean
  createdAt: string
}

export interface AdminUserResponse {
  id: number
  username: string
  email: string
  profilePicture: string | null
  role: string
  enabled: boolean
  versionRetentionDays: number
  createdAt: string
}

export interface AdminStatsResponse {
  userCount: number
  documentCount: number
  auditLogCount24h: number
}

export interface AuditLogResponse {
  id: number
  action: string
  username: string | null
  documentId: number | null
  details: string | null
  ipAddress: string | null
  timestamp: string
}

export interface RetentionPolicyResponse {
  id: number
  name: string
  documentType?: string
  folderId?: number
  folderName?: string
  retentionDays: number
  action: string
  enabled: boolean
  createdAt: string
}

export interface LegalHoldResponse {
  id: number
  documentId: number
  documentName: string
  reason: string
  createdByUsername: string
  createdAt: string
  releasedAt?: string
}

export interface WebhookResponse {
  id: number
  url: string
  events: string[]
  secret?: string
  enabled: boolean
  createdByUsername: string
  createdAt: string
}

export interface UserResponse {
  id: number
  username: string
  email: string
  profilePicture: string | null
  versionRetentionDays: number
  createdAt: string
}

export interface PageResponse<T> {
  content: T[]
  totalElements: number
  totalPages: number
  number: number
  size: number
}

export const auth = {
  register: (username: string, email: string, password: string) =>
    request('/api/auth/register', {
      method: 'POST',
      body: JSON.stringify({ username, email, password }),
    }) as Promise<AuthResponse>,

  login: (username: string, password: string) =>
    request('/api/auth/login', {
      method: 'POST',
      body: JSON.stringify({ username, password }),
    }) as Promise<AuthResponse>,

  refresh: (refreshToken: string) =>
    request('/api/auth/refresh', {
      method: 'POST',
      body: JSON.stringify({ refreshToken }),
    }) as Promise<AuthResponse>,

  forgotPassword: (email: string) =>
    request('/api/auth/forgot-password', {
      method: 'POST',
      body: JSON.stringify({ email }),
    }) as Promise<{ message: string }>,

  resetPassword: (token: string, newPassword: string) =>
    request('/api/auth/reset-password', {
      method: 'POST',
      body: JSON.stringify({ token, newPassword }),
    }) as Promise<{ message: string }>,

  changePassword: (currentPassword: string, newPassword: string) =>
    request('/api/auth/change-password', {
      method: 'POST',
      body: JSON.stringify({ currentPassword, newPassword }),
    }) as Promise<{ message: string }>,
}

export const users = {
  getProfile: (id: number) =>
    request(`/api/users/${id}`) as Promise<UserResponse>,

  updateProfile: (id: number, avatar?: File | null, versionRetentionDays?: number) => {
    const formData = new FormData()
    if (avatar) formData.append('avatar', avatar)
    if (versionRetentionDays !== undefined) formData.append('versionRetentionDays', String(versionRetentionDays))
    const headers: Record<string, string> = {}
    if (accessToken) headers['Authorization'] = `Bearer ${accessToken}`
    return fetch(`${API_BASE}/api/users/${id}`, {
      method: 'PUT',
      headers,
      body: formData,
    }).then(async (res) => {
      if (!res.ok) {
        const body = await res.json().catch(() => ({ error: res.statusText }))
        throw new Error(body.error || 'Update failed')
      }
      return res.json() as Promise<UserResponse>
    })
  },

  deleteAccount: (id: number) =>
    request(`/api/users/${id}`, { method: 'DELETE' }) as Promise<void>,

  search: (q: string) =>
    request(`/api/users/search?q=${encodeURIComponent(q)}`) as Promise<UserResponse[]>,

  avatarUrl: (id: number) => `${API_BASE}/api/users/${id}/avatar`,
}

export const folders = {
  list: () =>
    request('/api/folders') as Promise<FolderResponse[]>,

  create: (name: string) =>
    request('/api/folders', {
      method: 'POST',
      body: JSON.stringify({ name }),
    }) as Promise<FolderResponse>,

  rename: (id: number, name: string) =>
    request(`/api/folders/${id}`, {
      method: 'PUT',
      body: JSON.stringify({ name }),
    }) as Promise<FolderResponse>,

  delete: (id: number) =>
    request(`/api/folders/${id}`, { method: 'DELETE' }) as Promise<void>,
}

export const documents = {
  list: (page = 0, size = 50, sort?: string) =>
    request(`/api/documents?page=${page}&size=${size}${sort ? `&sort=${sort}` : ''}`) as Promise<PageResponse<DocumentResponse>>,

  search: (q: string, page = 0, size = 50) =>
    request(`/api/documents/search?q=${encodeURIComponent(q)}&page=${page}&size=${size}`) as Promise<PageResponse<DocumentResponse>>,

  sharedWithMe: (page = 0, size = 50, q?: string) =>
    request(`/api/documents/shared-with-me?page=${page}&size=${size}${q ? `&q=${encodeURIComponent(q)}` : ''}`) as Promise<PageResponse<DocumentResponse>>,

  sharedByMe: (page = 0, size = 50, q?: string) =>
    request(`/api/documents/shared-by-me?page=${page}&size=${size}${q ? `&q=${encodeURIComponent(q)}` : ''}`) as Promise<PageResponse<DocumentResponse>>,

  moveToFolder: (id: number, folderId: number | null) => {
    let path = `/api/documents/${id}/move`
    if (folderId !== null) path += `?folderId=${folderId}`
    return request(path, { method: 'PATCH' }) as Promise<DocumentResponse>
  },

  upload: (file: File, description: string, documentType?: string, folderId?: number) => {
    const formData = new FormData()
    formData.append('file', file)
    formData.append('description', description)
    if (documentType) formData.append('documentType', documentType)
    if (folderId !== undefined) formData.append('folderId', String(folderId))
    const headers: Record<string, string> = {}
    if (accessToken) headers['Authorization'] = `Bearer ${accessToken}`
    return fetch(`${API_BASE}/api/documents/upload`, {
      method: 'POST',
      headers,
      body: formData,
    }).then(async (res) => {
      if (!res.ok) {
        const body = await res.json().catch(() => ({ error: res.statusText }))
        throw new Error(body.error || 'Upload failed')
      }
      return res.json() as Promise<DocumentResponse>
    })
  },

  download: async (id: number) => {
    const headers: Record<string, string> = {}
    if (accessToken) headers['Authorization'] = `Bearer ${accessToken}`
    const res = await fetch(`${API_BASE}/api/documents/${id}/download`, { headers })
    if (!res.ok) {
      const body = await res.json().catch(() => ({ error: res.statusText }))
      throw new Error(body.error || 'Download failed')
    }
    const blob = await res.blob()
    const disposition = res.headers.get('content-disposition')
    const match = disposition?.match(/filename="?(.+?)"?$/)
    const filename = match?.[1] || `document-${id}`
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = filename
    a.click()
    URL.revokeObjectURL(url)
  },

  delete: (id: number) =>
    request(`/api/documents/${id}`, { method: 'DELETE' }) as Promise<void>,

  restore: (id: number) =>
    request(`/api/documents/${id}/restore`, { method: 'PATCH' }) as Promise<DocumentResponse>,

  trash: (page = 0, size = 50, q?: string) =>
    request(`/api/documents/trash?page=${page}&size=${size}${q ? `&q=${encodeURIComponent(q)}` : ''}`) as Promise<PageResponse<DocumentResponse>>,

  recentlyViewed: (page = 0, size = 20) =>
    request(`/api/documents/recently-viewed?page=${page}&size=${size}`) as Promise<PageResponse<DocumentResponse>>,

  update: (id: number, file: File, description: string) => {
    const formData = new FormData()
    formData.append('file', file)
    formData.append('description', description)
    const headers: Record<string, string> = {}
    if (accessToken) headers['Authorization'] = `Bearer ${accessToken}`
    return fetch(`${API_BASE}/api/documents/${id}`, {
      method: 'PUT',
      headers,
      body: formData,
    }).then(async (res) => {
      if (!res.ok) {
        const body = await res.json().catch(() => ({ error: res.statusText }))
        throw new Error(body.error || 'Update failed')
      }
      return res.json() as Promise<DocumentResponse>
    })
  },

  previewUrl: (id: number) => `${API_BASE}/api/documents/${id}/preview`,

  getPreviewBlobUrl: async (id: number, mimeType?: string) => {
    const headers: Record<string, string> = {}
    if (accessToken) headers['Authorization'] = `Bearer ${accessToken}`
    const res = await fetch(`${API_BASE}/api/documents/${id}/preview`, { headers })
    if (!res.ok) throw new Error('Preview failed')
    const blob = await res.blob()
    const correctedBlob = mimeType ? new Blob([blob], { type: mimeType }) : blob
    return URL.createObjectURL(correctedBlob)
  },



  getContent: async (id: number) => {
    const headers: Record<string, string> = {}
    if (accessToken) headers['Authorization'] = `Bearer ${accessToken}`
    const res = await fetch(`${API_BASE}/api/documents/${id}/content`, { headers })
    if (!res.ok) { const body = await res.json().catch(() => ({ error: res.statusText })); throw new Error(body.error || 'Failed to load content') }
    return res.text()
  },

  updateContent: async (id: number, content: string) => {
    const headers: Record<string, string> = { 'Content-Type': 'application/json' }
    if (accessToken) headers['Authorization'] = `Bearer ${accessToken}`
    const res = await fetch(`${API_BASE}/api/documents/${id}/content`, {
      method: 'PUT',
      headers,
      body: JSON.stringify({ content }),
    })
    if (!res.ok) { const body = await res.json().catch(() => ({ error: res.statusText })); throw new Error(body.error || 'Failed to save content') }
    return res.json() as Promise<DocumentResponse>
  },

  getRender: async (id: number) => {
    const headers: Record<string, string> = {}
    if (accessToken) headers['Authorization'] = `Bearer ${accessToken}`
    const res = await fetch(`${API_BASE}/api/documents/${id}/render`, { headers })
    if (!res.ok) { const body = await res.json().catch(() => ({ error: res.statusText })); throw new Error(body.error || 'Failed to render document') }
    return res.text()
  },

  getById: (id: number) =>
    request(`/api/documents/${id}`) as Promise<DocumentResponse>,

  saveRendered: async (id: number, html: string) => {
    const headers: Record<string, string> = { 'Content-Type': 'application/json' }
    if (accessToken) headers['Authorization'] = `Bearer ${accessToken}`
    const res = await fetch(`${API_BASE}/api/documents/${id}/save-rendered`, {
      method: 'PUT',
      headers,
      body: JSON.stringify({ html }),
    })
    if (!res.ok) { const body = await res.json().catch(() => ({ error: res.statusText })); throw new Error(body.error || 'Failed to save rendered document') }
    return res.json() as Promise<DocumentResponse>
  },

  getVersions: (id: number) =>
    request(`/api/documents/${id}/versions`) as Promise<VersionResponse[]>,

  restoreVersion: (id: number, versionId: number) =>
    request(`/api/documents/${id}/versions/${versionId}/restore`, { method: 'POST' }) as Promise<DocumentResponse>,

  batchDelete: async (ids: number[]) => {
    const headers: Record<string, string> = { 'Content-Type': 'application/json' }
    if (accessToken) headers['Authorization'] = `Bearer ${accessToken}`
    const res = await fetch(`${API_BASE}/api/documents/batch/delete`, {
      method: 'POST', headers, body: JSON.stringify(ids),
    })
    if (!res.ok) { const body = await res.json().catch(() => ({ error: res.statusText })); throw new Error(body.error || 'Batch delete failed') }
  },

  emptyTrash: async () => {
    const headers: Record<string, string> = { 'Content-Type': 'application/json' }
    if (accessToken) headers['Authorization'] = `Bearer ${accessToken}`
    const res = await fetch(`${API_BASE}/api/documents/trash/empty`, {
      method: 'POST', headers,
    })
    if (!res.ok) { const body = await res.json().catch(() => ({ error: res.statusText })); throw new Error(body.error || 'Empty trash failed') }
  },

  batchMove: async (ids: number[], folderId: number | null) => {
    const params = folderId !== null ? `?folderId=${folderId}` : ''
    const headers: Record<string, string> = { 'Content-Type': 'application/json' }
    if (accessToken) headers['Authorization'] = `Bearer ${accessToken}`
    const res = await fetch(`${API_BASE}/api/documents/batch/move${params}`, {
      method: 'POST', headers, body: JSON.stringify(ids),
    })
    if (!res.ok) { const body = await res.json().catch(() => ({ error: res.statusText })); throw new Error(body.error || 'Batch move failed') }
  },

  batchDownload: async (ids: number[]) => {
    const headers: Record<string, string> = { 'Content-Type': 'application/json' }
    if (accessToken) headers['Authorization'] = `Bearer ${accessToken}`
    const res = await fetch(`${API_BASE}/api/documents/batch/download`, {
      method: 'POST', headers, body: JSON.stringify(ids),
    })
    if (!res.ok) { const body = await res.json().catch(() => ({ error: res.statusText })); throw new Error(body.error || 'Batch download failed') }
    const blob = await res.blob()
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = 'documents.zip'
    a.click()
    URL.revokeObjectURL(url)
  },

  toggleFavorite: (id: number) =>
    request(`/api/documents/${id}/favorite`, { method: 'POST' }) as Promise<{ favorite: boolean }>,

  listFavorites: () =>
    request('/api/documents/favorites') as Promise<DocumentResponse[]>,

  duplicate: (id: number) =>
    request(`/api/documents/${id}/duplicate`, { method: 'POST' }) as Promise<DocumentResponse>,

  addTag: (id: number, tagId: number) =>
    request(`/api/documents/${id}/tags/${tagId}`, { method: 'POST' }) as Promise<DocumentResponse>,

  removeTag: (id: number, tagId: number) =>
    request(`/api/documents/${id}/tags/${tagId}`, { method: 'DELETE' }) as Promise<DocumentResponse>,

  permissions: {
    list: (docId: number) =>
      request(`/api/documents/${docId}/permissions`) as Promise<PermissionResponse[]>,

    grant: (docId: number, userId: number, permissionType: string) =>
      request(`/api/documents/${docId}/permissions`, {
        method: 'POST',
        body: JSON.stringify({ userId, permissionType }),
      }) as Promise<PermissionResponse>,

    revoke: (docId: number, userId: number) =>
      request(`/api/documents/${docId}/permissions/${userId}`, { method: 'DELETE' }) as Promise<void>,
  },
}

export const tags = {
  list: () =>
    request('/api/tags') as Promise<TagResponse[]>,

  create: (name: string, color?: string) =>
    request('/api/tags', {
      method: 'POST',
      body: JSON.stringify({ name, color }),
    }) as Promise<TagResponse>,

  delete: (id: number) =>
    request(`/api/tags/${id}`, { method: 'DELETE' }) as Promise<void>,
}

export const admin = {
  getStats: () =>
    request('/api/admin/stats') as Promise<AdminStatsResponse>,

  getUsers: (page = 0, size = 50) =>
    request(`/api/admin/users?page=${page}&size=${size}`) as Promise<PageResponse<AdminUserResponse>>,

  updateUserRole: (userId: number, role: string) =>
    request(`/api/admin/users/${userId}/role`, {
      method: 'PATCH',
      body: JSON.stringify({ role }),
    }) as Promise<AdminUserResponse>,

  setUserEnabled: (userId: number, enabled: boolean) =>
    request(`/api/admin/users/${userId}/enabled`, {
      method: 'PATCH',
      body: JSON.stringify({ enabled }),
    }) as Promise<AdminUserResponse>,

  getAuditLogs: (params?: { action?: string; username?: string; page?: number; size?: number }) => {
    const p = new URLSearchParams()
    if (params?.action) p.set('action', params.action)
    if (params?.username) p.set('username', params.username)
    p.set('page', String(params?.page ?? 0))
    p.set('size', String(params?.size ?? 50))
    return request(`/api/admin/audit-logs?${p}`) as Promise<PageResponse<AuditLogResponse>>
  },

  // Retention Policies
  getRetentionPolicies: (page = 0, size = 50) =>
    request(`/api/admin/retention-policies?page=${page}&size=${size}`) as Promise<PageResponse<RetentionPolicyResponse>>,

  createRetentionPolicy: (data: { name: string; documentType?: string; folderId?: number; retentionDays: number; action?: string; enabled?: boolean }) =>
    request('/api/admin/retention-policies', { method: 'POST', body: JSON.stringify(data) }) as Promise<RetentionPolicyResponse>,

  updateRetentionPolicy: (id: number, data: { name: string; documentType?: string; folderId?: number | null; retentionDays: number; action?: string; enabled?: boolean }) =>
    request(`/api/admin/retention-policies/${id}`, { method: 'PUT', body: JSON.stringify(data) }) as Promise<RetentionPolicyResponse>,

  deleteRetentionPolicy: (id: number) =>
    request(`/api/admin/retention-policies/${id}`, { method: 'DELETE' }) as Promise<void>,

  // Legal Holds
  getLegalHolds: (page = 0, size = 50) =>
    request(`/api/admin/legal-holds?page=${page}&size=${size}`) as Promise<PageResponse<LegalHoldResponse>>,

  createLegalHold: (documentId: number, reason: string) =>
    request('/api/admin/legal-holds', { method: 'POST', body: JSON.stringify({ documentId, reason }) }) as Promise<LegalHoldResponse>,

  releaseLegalHold: (id: number) =>
    request(`/api/admin/legal-holds/${id}/release`, { method: 'POST' }) as Promise<LegalHoldResponse>,

  // System Config
  getSystemConfig: () =>
    request('/api/admin/system/config') as Promise<Record<string, string>>,

  updateSystemConfig: (config: Record<string, string>) =>
    request('/api/admin/system/config', { method: 'PUT', body: JSON.stringify(config) }) as Promise<Record<string, string>>,
}

export const webhooksApi = {
  list: (page = 0, size = 50) =>
    request(`/api/webhooks?page=${page}&size=${size}`) as Promise<PageResponse<WebhookResponse>>,

  create: (data: { url: string; events: string[]; secret?: string; enabled?: boolean }) =>
    request('/api/webhooks', { method: 'POST', body: JSON.stringify(data) }) as Promise<WebhookResponse>,

  update: (id: number, data: { url: string; events: string[]; secret?: string; enabled?: boolean }) =>
    request(`/api/webhooks/${id}`, { method: 'PUT', body: JSON.stringify(data) }) as Promise<WebhookResponse>,

  delete: (id: number) =>
    request(`/api/webhooks/${id}`, { method: 'DELETE' }) as Promise<void>,
}

export const notifications = {
  list: () =>
    request('/api/notifications') as Promise<NotificationResponse[]>,

  unreadCount: () =>
    request('/api/notifications/unread-count') as Promise<{ count: number }>,

  markRead: (id: number) =>
    request(`/api/notifications/${id}/read`, { method: 'PUT' }) as Promise<void>,

  markAllRead: () =>
    request('/api/notifications/read-all', { method: 'PUT' }) as Promise<void>,

  delete: (id: number) =>
    request(`/api/notifications/${id}`, { method: 'DELETE' }) as Promise<void>,

  clearAll: () =>
    request('/api/notifications/clear-all', { method: 'DELETE' }) as Promise<void>,
}
