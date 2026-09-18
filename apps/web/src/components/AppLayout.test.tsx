import { describe, it, expect, beforeEach } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { AppLayout } from './AppLayout'
import { AuthProvider } from '../features/auth/hooks/useAuth'
import { saveSession, isLoggedIn } from '../features/auth/session'
import { fakeJwt } from '../test/fakeJwt'

describe('AppLayout', () => {
  beforeEach(() => {
    window.sessionStorage.clear()
  })

  it('muestra los enlaces de navegacion y el contenido', () => {
    render(
      <AuthProvider>
        <MemoryRouter>
          <AppLayout>
            <p>contenido de la pagina</p>
          </AppLayout>
        </MemoryRouter>
      </AuthProvider>,
    )
    expect(screen.getByText('Consola')).toBeInTheDocument()
    expect(screen.getByText('Ajustes')).toBeInTheDocument()
    expect(screen.getByText('Acerca de')).toBeInTheDocument()
    expect(screen.getByText('contenido de la pagina')).toBeInTheDocument()
    expect(screen.queryByText('Administración')).not.toBeInTheDocument()
    expect(screen.queryByText('Reportes')).not.toBeInTheDocument()
  })

  it('los enlaces Administracion y Reportes solo aparecen para el rol ADMIN', () => {
    saveSession(fakeJwt({ sub: 'u1', email: 'a@test.com', role: 'ADMIN', permissions: [] }), 'refresh', null)
    render(
      <AuthProvider>
        <MemoryRouter>
          <AppLayout>
            <p>contenido</p>
          </AppLayout>
        </MemoryRouter>
      </AuthProvider>,
    )
    expect(screen.getByText('Administración')).toBeInTheDocument()
    expect(screen.getByText('Reportes')).toBeInTheDocument()
    expect(screen.getByText('Administrador')).toBeInTheDocument()
  })

  it('muestra una insignia con el rol de la cuenta activa', () => {
    saveSession(fakeJwt({ sub: 'u1', email: 'tec@test.com', role: 'TECNICO', permissions: [] }), 'refresh', null)
    render(
      <AuthProvider>
        <MemoryRouter>
          <AppLayout>
            <p>contenido</p>
          </AppLayout>
        </MemoryRouter>
      </AuthProvider>,
    )
    expect(screen.getByText('Técnico')).toBeInTheDocument()
  })

  it('el boton Salir cierra la sesion', () => {
    saveSession('token', 'refresh', null)
    render(
      <AuthProvider>
        <MemoryRouter>
          <AppLayout>
            <p>contenido</p>
          </AppLayout>
        </MemoryRouter>
      </AuthProvider>,
    )
    fireEvent.click(screen.getByText('Salir'))
    expect(isLoggedIn()).toBe(false)
  })

  it('el boton Salir tambien navega a /login, no solo cierra la sesion', () => {
    // La prueba existente solo confirmaba isLoggedIn() === false; navigate('/login',
    // { replace: true }) nunca se verificaba de punta a punta.
    saveSession(fakeJwt({ sub: 'u1', email: 'a@test.com', role: 'ADMIN', permissions: [] }), 'refresh', null)
    render(
      <AuthProvider>
        <MemoryRouter initialEntries={['/main']}>
          <Routes>
            <Route
              path="/main"
              element={
                <AppLayout>
                  <p>contenido</p>
                </AppLayout>
              }
            />
            <Route path="/login" element={<p>Pantalla de login</p>} />
          </Routes>
        </MemoryRouter>
      </AuthProvider>,
    )

    fireEvent.click(screen.getByText('Salir'))

    expect(screen.getByText('Pantalla de login')).toBeInTheDocument()
  })

  it('con rol CLIENTE, Administracion y Reportes tampoco aparecen', () => {
    // Las pruebas existentes solo cubrian "sin sesion" y "ADMIN"; CLIENTE (el rol mas
    // comun del sistema) nunca se probaba de forma explicita para esta regla de RBAC.
    saveSession(fakeJwt({ sub: 'u1', email: 'c@test.com', role: 'CLIENTE', permissions: [] }), 'refresh', null)
    render(
      <AuthProvider>
        <MemoryRouter>
          <AppLayout>
            <p>contenido</p>
          </AppLayout>
        </MemoryRouter>
      </AuthProvider>,
    )

    expect(screen.queryByText('Administración')).not.toBeInTheDocument()
    expect(screen.queryByText('Reportes')).not.toBeInTheDocument()
    expect(screen.getByText('Cliente')).toBeInTheDocument()
  })

  it('la insignia de rol usa un color distinto para cada rol', () => {
    // Mismo patron de hueco que STATUS_VAR en Badges.tsx (Ronda 23) y ReportsPage
    // (Ronda 26): ROLE_BADGE_COLOR es otro mapa manual, y las pruebas existentes solo
    // verificaban la etiqueta traducida ("Administrador", "Técnico"), nunca el color.
    const roles: Array<{ role: 'ADMIN' | 'TECNICO' | 'CLIENTE'; label: string }> = [
      { role: 'ADMIN', label: 'Administrador' },
      { role: 'TECNICO', label: 'Técnico' },
      { role: 'CLIENTE', label: 'Cliente' },
    ]
    const colores = roles.map(({ role, label }) => {
      window.sessionStorage.clear()
      saveSession(fakeJwt({ sub: 'u1', email: 'x@test.com', role, permissions: [] }), 'refresh', null)
      const { unmount } = render(
        <AuthProvider>
          <MemoryRouter>
            <AppLayout>
              <p>contenido</p>
            </AppLayout>
          </MemoryRouter>
        </AuthProvider>,
      )
      const color = screen.getByText(label).style.background
      unmount()
      return color
    })

    expect(new Set(colores).size).toBe(roles.length)
  })
})
