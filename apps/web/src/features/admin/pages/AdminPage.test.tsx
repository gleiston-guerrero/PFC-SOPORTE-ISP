import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { AdminPage } from './AdminPage'
import * as adminApi from '../api/adminApi'
import type { UserResponse } from '../types/user'

const existingUser: UserResponse = {
  id: 'u1',
  email: 'tecorte@test.com',
  fullName: 'Tecnico Norte',
  role: 'TECNICO',
  zone: 'QUEVEDO_NORTE',
  active: true,
  createdAt: '2026-01-01T00:00:00Z',
}

describe('AdminPage', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
  })

  it('carga y muestra la lista de cuentas registradas', async () => {
    vi.spyOn(adminApi, 'listUsers').mockResolvedValue([existingUser])
    render(<AdminPage />)

    expect(await screen.findByText('tecorte@test.com')).toBeInTheDocument()
  })

  it('muestra un error si la lista falla', async () => {
    vi.spyOn(adminApi, 'listUsers').mockRejectedValue(new Error('network'))
    render(<AdminPage />)

    expect(await screen.findByText('No se pudieron cargar las cuentas')).toBeInTheDocument()
  })

  it('el campo de zona solo aparece cuando el rol elegido es TECNICO', async () => {
    vi.spyOn(adminApi, 'listUsers').mockResolvedValue([])
    render(<AdminPage />)
    await waitFor(() => expect(adminApi.listUsers).toHaveBeenCalled())

    expect(screen.queryByText('Zona (técnico)')).not.toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Técnico' }))
    expect(screen.getByText('Zona (técnico)')).toBeInTheDocument()
  })

  it('crea una cuenta y refresca la lista', async () => {
    vi.spyOn(adminApi, 'listUsers').mockResolvedValue([])
    const createSpy = vi.spyOn(adminApi, 'createUser').mockResolvedValue({ ...existingUser, email: 'nuevo@test.com' })
    render(<AdminPage />)
    await waitFor(() => expect(adminApi.listUsers).toHaveBeenCalled())

    fireEvent.change(screen.getByLabelText('Correo'), { target: { value: 'nuevo@test.com' } })
    fireEvent.change(screen.getByLabelText('Contraseña'), { target: { value: 'Passw0rd!' } })
    fireEvent.change(screen.getByLabelText('Nombre completo'), { target: { value: 'Cliente Nuevo' } })

    fireEvent.click(screen.getByRole('button', { name: '+ Crear cuenta' }))

    await waitFor(() => expect(createSpy).toHaveBeenCalledWith({
      email: 'nuevo@test.com',
      password: 'Passw0rd!',
      fullName: 'Cliente Nuevo',
      role: 'CLIENTE',
      zone: null,
    }))
    expect(await screen.findByText('Cuenta creada: nuevo@test.com')).toBeInTheDocument()
  })

  it('crea una cuenta TECNICO enviando la zona elegida, no null', async () => {
    // La unica prueba existente de creacion exitosa dejaba el rol por defecto (CLIENTE, zone
    // null); el camino real donde SI se envia una zona -- exigido por AuthService.validateZoneForRole
    // en el backend -- nunca se ejercitaba.
    vi.spyOn(adminApi, 'listUsers').mockResolvedValue([])
    const createSpy = vi.spyOn(adminApi, 'createUser').mockResolvedValue({ ...existingUser, email: 'tec@test.com' })
    render(<AdminPage />)
    await waitFor(() => expect(adminApi.listUsers).toHaveBeenCalled())

    fireEvent.click(screen.getByRole('button', { name: 'Técnico' }))
    fireEvent.change(screen.getByLabelText('Correo'), { target: { value: 'tec@test.com' } })
    fireEvent.change(screen.getByLabelText('Contraseña'), { target: { value: 'Passw0rd!' } })
    fireEvent.change(screen.getByLabelText('Nombre completo'), { target: { value: 'Tecnico Nuevo' } })
    fireEvent.change(screen.getByLabelText('Zona (técnico)'), { target: { value: 'QUEVEDO_SUR' } })
    fireEvent.click(screen.getByRole('button', { name: '+ Crear cuenta' }))

    await waitFor(() =>
      expect(createSpy).toHaveBeenCalledWith(expect.objectContaining({ role: 'TECNICO', zone: 'QUEVEDO_SUR' })),
    )
  })

  it('muestra un error generico si la creacion falla en el servidor', async () => {
    // La unica prueba de error del formulario existente era la validacion local (campos
    // vacios); el fallo real de la API -- correo duplicado, por ejemplo -- nunca se probaba.
    vi.spyOn(adminApi, 'listUsers').mockResolvedValue([])
    vi.spyOn(adminApi, 'createUser').mockRejectedValue(new Error('409'))
    render(<AdminPage />)
    await waitFor(() => expect(adminApi.listUsers).toHaveBeenCalled())

    fireEvent.change(screen.getByLabelText('Correo'), { target: { value: 'ya@existe.com' } })
    fireEvent.change(screen.getByLabelText('Contraseña'), { target: { value: 'Passw0rd!' } })
    fireEvent.change(screen.getByLabelText('Nombre completo'), { target: { value: 'Alguien' } })
    fireEvent.click(screen.getByRole('button', { name: '+ Crear cuenta' }))

    expect(await screen.findByText('No se pudo crear la cuenta')).toBeInTheDocument()
  })

  it('limpia correo, contraseña y nombre despues de crear la cuenta con exito', async () => {
    // Solo se confirmaba el mensaje de exito, nunca que el formulario realmente se
    // limpiara para poder cargar la siguiente cuenta sin arrastrar datos de la anterior.
    vi.spyOn(adminApi, 'listUsers').mockResolvedValue([])
    vi.spyOn(adminApi, 'createUser').mockResolvedValue({ ...existingUser, email: 'nuevo2@test.com' })
    render(<AdminPage />)
    await waitFor(() => expect(adminApi.listUsers).toHaveBeenCalled())

    fireEvent.change(screen.getByLabelText('Correo'), { target: { value: 'nuevo2@test.com' } })
    fireEvent.change(screen.getByLabelText('Contraseña'), { target: { value: 'Passw0rd!' } })
    fireEvent.change(screen.getByLabelText('Nombre completo'), { target: { value: 'Cliente Nuevo 2' } })
    fireEvent.click(screen.getByRole('button', { name: '+ Crear cuenta' }))

    await screen.findByText('Cuenta creada: nuevo2@test.com')
    expect(screen.getByLabelText('Correo')).toHaveValue('')
    expect(screen.getByLabelText('Contraseña')).toHaveValue('')
    expect(screen.getByLabelText('Nombre completo')).toHaveValue('')
  })

  it('campos de puro espacio en blanco cuentan como vacios', async () => {
    vi.spyOn(adminApi, 'listUsers').mockResolvedValue([])
    const createSpy = vi.spyOn(adminApi, 'createUser')
    render(<AdminPage />)
    await waitFor(() => expect(adminApi.listUsers).toHaveBeenCalled())

    fireEvent.change(screen.getByLabelText('Correo'), { target: { value: '   ' } })
    fireEvent.change(screen.getByLabelText('Contraseña'), { target: { value: '   ' } })
    fireEvent.change(screen.getByLabelText('Nombre completo'), { target: { value: '   ' } })
    fireEvent.click(screen.getByRole('button', { name: '+ Crear cuenta' }))

    expect(await screen.findByText('Completa correo, contraseña y nombre')).toBeInTheDocument()
    expect(createSpy).not.toHaveBeenCalled()
  })

  it('muestra un error si faltan campos obligatorios', async () => {
    vi.spyOn(adminApi, 'listUsers').mockResolvedValue([])
    render(<AdminPage />)
    await waitFor(() => expect(adminApi.listUsers).toHaveBeenCalled())

    fireEvent.click(screen.getByRole('button', { name: '+ Crear cuenta' }))

    expect(await screen.findByText('Completa correo, contraseña y nombre')).toBeInTheDocument()
  })
})
