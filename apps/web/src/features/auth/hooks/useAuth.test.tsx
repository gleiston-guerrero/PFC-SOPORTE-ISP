import { describe, it, expect, beforeEach, vi } from 'vitest'
import { renderHook, act } from '@testing-library/react'
import type { ReactNode } from 'react'
import { AuthProvider, useAuth } from './useAuth'
import { isLoggedIn } from '../session'
import { login as loginRequest } from '../api/authApi'

// login() no tenia ninguna prueba directa: ProtectedRoute.test.tsx usa <AuthProvider> de
// paso, pero nunca ejercita login()/logout() ni los estados loading/error que expone. Se
// mockea authApi.login (no axios/MSW) porque es la unica dependencia externa real de este
// hook -- session.ts es codigo propio y se deja correr de verdad contra sessionStorage.
vi.mock('../api/authApi', () => ({
  login: vi.fn(),
}))

function tokenConRol(role: 'CLIENTE' | 'TECNICO' | 'ADMIN'): string {
  const payload = { sub: 'user-1', email: 'u@test.com', role, permissions: [], exp: 9999999999 }
  const base64url = btoa(JSON.stringify(payload)).replace(/\+/g, '-').replace(/\//g, '_')
  return `header.${base64url}.signature`
}

function wrapper({ children }: { children: ReactNode }) {
  return <AuthProvider>{children}</AuthProvider>
}

describe('useAuth', () => {
  beforeEach(() => {
    window.sessionStorage.clear()
    vi.mocked(loginRequest).mockReset()
  })

  it('lanza si se usa fuera de AuthProvider', () => {
    expect(() => renderHook(() => useAuth())).toThrow('useAuth debe usarse dentro de <AuthProvider>')
  })

  it('arranca sin autenticar cuando no hay sesion guardada', () => {
    const { result } = renderHook(() => useAuth(), { wrapper })

    expect(result.current.authenticated).toBe(false)
    expect(result.current.role).toBeNull()
    expect(result.current.loading).toBe(false)
    expect(result.current.error).toBeNull()
  })

  it('login exitoso guarda la sesion y expone el rol decodificado del token', async () => {
    vi.mocked(loginRequest).mockResolvedValue({
      accessToken: tokenConRol('TECNICO'),
      refreshToken: 'refresh-1',
      accessTokenExpiresAt: null,
    })
    const { result } = renderHook(() => useAuth(), { wrapper })

    let exito = false
    await act(async () => {
      exito = await result.current.login('tecnico@test.com', 'Clave123!')
    })

    expect(exito).toBe(true)
    expect(result.current.authenticated).toBe(true)
    expect(result.current.role).toBe('TECNICO')
    expect(result.current.loading).toBe(false)
    expect(result.current.error).toBeNull()
    expect(isLoggedIn()).toBe(true)
  })

  it('login fallido con Error expone el mensaje real y no autentica', async () => {
    vi.mocked(loginRequest).mockRejectedValue(new Error('Credenciales invalidas'))
    const { result } = renderHook(() => useAuth(), { wrapper })

    let exito = true
    await act(async () => {
      exito = await result.current.login('malo@test.com', 'clave-mala')
    })

    expect(exito).toBe(false)
    expect(result.current.authenticated).toBe(false)
    expect(result.current.error).toBe('Credenciales invalidas')
  })

  it('login fallido con un valor no-Error cae al mensaje generico', async () => {
    // authApi.login solo puede rechazar con lo que axios/fetch le pasen; un rechazo que no
    // sea un Error (p.ej. una cadena) no debe tumbar el hook, debe caer al mensaje por
    // defecto -- justo la rama "err instanceof Error ? ... : 'No se pudo iniciar sesion'".
    vi.mocked(loginRequest).mockRejectedValue('fallo de red crudo')
    const { result } = renderHook(() => useAuth(), { wrapper })

    await act(async () => {
      await result.current.login('x@test.com', 'y')
    })

    expect(result.current.error).toBe('No se pudo iniciar sesión')
  })

  it('logout limpia la sesion y el rol', async () => {
    vi.mocked(loginRequest).mockResolvedValue({
      accessToken: tokenConRol('ADMIN'),
      refreshToken: 'refresh-2',
      accessTokenExpiresAt: null,
    })
    const { result } = renderHook(() => useAuth(), { wrapper })
    await act(async () => {
      await result.current.login('admin@test.com', 'Clave123!')
    })
    expect(result.current.authenticated).toBe(true)

    act(() => {
      result.current.logout()
    })

    expect(result.current.authenticated).toBe(false)
    expect(result.current.role).toBeNull()
    expect(isLoggedIn()).toBe(false)
  })

  it('mientras login esta en curso, loading es true', async () => {
    let resolverLogin: (value: { accessToken: string; refreshToken: string; accessTokenExpiresAt: string | null }) => void
    vi.mocked(loginRequest).mockReturnValue(
      new Promise((resolve) => {
        resolverLogin = resolve
      }),
    )
    const { result } = renderHook(() => useAuth(), { wrapper })

    let promesaLogin!: Promise<boolean>
    act(() => {
      promesaLogin = result.current.login('x@test.com', 'y')
    })
    expect(result.current.loading).toBe(true)

    await act(async () => {
      resolverLogin({ accessToken: tokenConRol('CLIENTE'), refreshToken: 'r', accessTokenExpiresAt: null })
      await promesaLogin
    })
    expect(result.current.loading).toBe(false)
  })
})
