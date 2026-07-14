import { createContext, useContext, useState, useCallback, type ReactNode } from 'react'
import { auth, setToken, type AuthResponse } from '@/lib/api'

interface AuthContextType {
  user: { username: string; role: string } | null
  login: (username: string, password: string) => Promise<void>
  register: (username: string, email: string, password: string) => Promise<void>
  logout: () => void
}

const AuthContext = createContext<AuthContextType | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<{ username: string; role: string } | null>(() => {
    const stored = sessionStorage.getItem('user')
    const token = sessionStorage.getItem('token')
    if (stored && token) {
      setToken(token)
      return JSON.parse(stored)
    }
    return null
  })

  const login = useCallback(async (username: string, password: string) => {
    const res = (await auth.login(username, password)) as AuthResponse
    setToken(res.token)
    const userData = { username: res.username, role: res.role }
    sessionStorage.setItem('token', res.token)
    sessionStorage.setItem('user', JSON.stringify(userData))
    setUser(userData)
  }, [])

  const register = useCallback(async (username: string, email: string, password: string) => {
    const res = (await auth.register(username, email, password)) as AuthResponse
    setToken(res.token)
    const userData = { username: res.username, role: res.role }
    sessionStorage.setItem('token', res.token)
    sessionStorage.setItem('user', JSON.stringify(userData))
    setUser(userData)
  }, [])

  const logout = useCallback(() => {
    setToken(null)
    sessionStorage.removeItem('token')
    sessionStorage.removeItem('user')
    setUser(null)
  }, [])

  return (
    <AuthContext.Provider value={{ user, login, register, logout }}>
      {children}
    </AuthContext.Provider>
  )
}

export function useAuth() {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be used within AuthProvider')
  return ctx
}
