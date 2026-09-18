import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { LoginPage } from './LoginPage'
import { AuthProvider } from '../hooks/useAuth'
import { login as loginRequest } from '../api/authApi'

// mockea authApi.login (mismo criterio que useAuth.test.tsx): es la unica dependencia
// externa real de este flujo, y AuthProvider real (no mockeado) es lo que hace que
// navigate('/main') y el mensaje de authError sean observables de punta a punta.
vi.mock('../api/authApi', () => ({
  login: vi.fn(),
}))

function renderLoginPage() {
  return render(
    <AuthProvider>
      <MemoryRouter initialEntries={['/']}>
        <Routes>
          <Route path="/" element={<LoginPage />} />
          <Route path="/main" element={<p>Consola principal</p>} />
        </Routes>
      </MemoryRouter>
    </AuthProvider>,
  )
}

function tokenConRol(role: 'CLIENTE' | 'TECNICO' | 'ADMIN'): string {
  const payload = { sub: 'user-1', email: 'u@test.com', role, permissions: [], exp: 9999999999 }
  const base64url = btoa(JSON.stringify(payload)).replace(/\+/g, '-').replace(/\//g, '_')
  return `header.${base64url}.signature`
}

describe('LoginPage', () => {
  beforeEach(() => {
    window.sessionStorage.clear()
    vi.mocked(loginRequest).mockReset()
  })

  it('muestra un error de validacion si se envia el formulario vacio', () => {
    renderLoginPage()

    fireEvent.click(screen.getByRole('button', { name: /ingresar/i }))

    expect(screen.getByText('Ingresa correo y contraseña')).toBeInTheDocument()
  })

  it('limpia el error de validacion al escribir en los campos', () => {
    renderLoginPage()
    fireEvent.click(screen.getByRole('button', { name: /ingresar/i }))
    expect(screen.getByText('Ingresa correo y contraseña')).toBeInTheDocument()

    fireEvent.change(screen.getByPlaceholderText('Correo'), { target: { value: 'a@b.com' } })
    fireEvent.change(screen.getByPlaceholderText('Contraseña'), { target: { value: 'x' } })

    // El mensaje de validacion local se limpia; el submit real fallara por red (no hay
    // backend en el entorno de pruebas), lo cual es un caso aparte y no se afirma aqui.
    expect(screen.queryByText('Ingresa correo y contraseña')).not.toBeInTheDocument()
  })

  it('un login exitoso navega a /main', async () => {
    // Ninguna prueba existente llegaba a ejercitar el submit real -- authApi.login estaba
    // sin mockear, asi que cualquier intento de submit fallaba por red antes de poder
    // observar la navegacion.
    vi.mocked(loginRequest).mockResolvedValue({
      accessToken: tokenConRol('CLIENTE'),
      refreshToken: 'refresh-1',
      accessTokenExpiresAt: null,
    })
    renderLoginPage()

    fireEvent.change(screen.getByPlaceholderText('Correo'), { target: { value: 'cliente@test.com' } })
    fireEvent.change(screen.getByPlaceholderText('Contraseña'), { target: { value: 'Passw0rd!' } })
    fireEvent.click(screen.getByRole('button', { name: /ingresar/i }))

    expect(await screen.findByText('Consola principal')).toBeInTheDocument()
  })

  it('recorta el correo antes de enviarlo, pero no la contraseña', async () => {
    vi.mocked(loginRequest).mockResolvedValue({
      accessToken: tokenConRol('CLIENTE'),
      refreshToken: 'refresh-1',
      accessTokenExpiresAt: null,
    })
    renderLoginPage()

    fireEvent.change(screen.getByPlaceholderText('Correo'), { target: { value: '  cliente@test.com  ' } })
    fireEvent.change(screen.getByPlaceholderText('Contraseña'), { target: { value: 'Passw0rd!' } })
    fireEvent.click(screen.getByRole('button', { name: /ingresar/i }))

    await waitFor(() => expect(loginRequest).toHaveBeenCalledWith('cliente@test.com', 'Passw0rd!'))
  })

  it('un login fallido muestra el mensaje de error del backend, no se queda mudo', async () => {
    vi.mocked(loginRequest).mockRejectedValue(new Error('Credenciales inválidas'))
    renderLoginPage()

    fireEvent.change(screen.getByPlaceholderText('Correo'), { target: { value: 'cliente@test.com' } })
    fireEvent.change(screen.getByPlaceholderText('Contraseña'), { target: { value: 'mala-clave' } })
    fireEvent.click(screen.getByRole('button', { name: /ingresar/i }))

    expect(await screen.findByText('Credenciales inválidas')).toBeInTheDocument()
    expect(screen.queryByText('Consola principal')).not.toBeInTheDocument()
  })

  it('el boton del ojo alterna el campo de contraseña entre oculto y visible', () => {
    renderLoginPage()
    const passwordInput = screen.getByPlaceholderText('Contraseña')
    expect(passwordInput).toHaveAttribute('type', 'password')

    fireEvent.click(screen.getByRole('button', { name: 'Mostrar contraseña' }))

    expect(passwordInput).toHaveAttribute('type', 'text')
    expect(screen.getByRole('button', { name: 'Ocultar contraseña' })).toBeInTheDocument()
  })
})
