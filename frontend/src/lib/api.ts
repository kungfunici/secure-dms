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
  ownerUsername: string
  permission?: string
  folderId?: number
  folderName?: string
  uploadedAt: string
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

export interface UserResponse {
  id: number
  username: string
  email: string
  profilePicture: string | null
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
}

export const users = {
  getProfile: (id: number) =>
    request(`/api/users/${id}`) as Promise<UserResponse>,

  updateProfile: (id: number, avatar: File) => {
    const formData = new FormData()
    formData.append('avatar', avatar)
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
  list: (page = 0, size = 50) =>
    request(`/api/documents?page=${page}&size=${size}`) as Promise<PageResponse<DocumentResponse>>,

  search: (q: string, page = 0, size = 50) =>
    request(`/api/documents/search?q=${encodeURIComponent(q)}&page=${page}&size=${size}`) as Promise<PageResponse<DocumentResponse>>,

  sharedWithMe: (page = 0, size = 50) =>
    request(`/api/documents/shared-with-me?page=${page}&size=${size}`) as Promise<PageResponse<DocumentResponse>>,

  sharedByMe: (page = 0, size = 50) =>
    request(`/api/documents/shared-by-me?page=${page}&size=${size}`) as Promise<PageResponse<DocumentResponse>>,

  moveToFolder: (id: number, folderId: number | null) => {
    let path = `/api/documents/${id}/move`
    if (folderId !== null) path += `?folderId=${folderId}`
    return request(path, { method: 'PATCH' }) as Promise<DocumentResponse>
  },

  upload: (file: File, description: string, folderId?: number) => {
    const formData = new FormData()
    formData.append('file', file)
    formData.append('description', description)
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
