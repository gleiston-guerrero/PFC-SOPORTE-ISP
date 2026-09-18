import { describe, it, expect, vi } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { CreateTicketModal } from './CreateTicketModal'
import * as ticketsApi from '../api/ticketsApi'
import type { TicketResponse } from '../types/ticket'

describe('CreateTicketModal', () => {
  it('llama a onClose al hacer click en cancelar', () => {
    const onClose = vi.fn()
    render(<CreateTicketModal onClose={onClose} onCreated={vi.fn()} />)

    fireEvent.click(screen.getByText('Cancelar'))
    expect(onClose).toHaveBeenCalled()
  })

  it('muestra un error si falta el titulo o la descripcion', async () => {
    render(<CreateTicketModal onClose={vi.fn()} onCreated={vi.fn()} />)

    fireEvent.click(screen.getByText('Crear solicitud'))

    expect(await screen.findByText('Completa el título y la descripción')).toBeInTheDocument()
  })

  it('crea el ticket y llama a onCreated', async () => {
    const created = { ticketId: 't1' } as TicketResponse
    const spy = vi.spyOn(ticketsApi, 'createTicket').mockResolvedValue(created)
    const onCreated = vi.fn()
    render(<CreateTicketModal onClose={vi.fn()} onCreated={onCreated} />)

    fireEvent.change(screen.getByLabelText('Título'), { target: { value: 'Sin internet' } })
    fireEvent.change(screen.getByLabelText('Descripción del problema'), { target: { value: 'Router apagado' } })
    fireEvent.click(screen.getByText('Crear solicitud'))

    await waitFor(() =>
      expect(spy).toHaveBeenCalledWith({
        zone: 'QUEVEDO_CENTRO',
        title: 'Sin internet',
        description: 'Router apagado',
        contactPhone: undefined,
        address: undefined,
      }),
    )
    expect(onCreated).toHaveBeenCalled()
  })

  it('muestra un error generico si la creacion falla', async () => {
    vi.spyOn(ticketsApi, 'createTicket').mockRejectedValue(new Error('network'))
    render(<CreateTicketModal onClose={vi.fn()} onCreated={vi.fn()} />)

    fireEvent.change(screen.getByLabelText('Título'), { target: { value: 'Sin internet' } })
    fireEvent.change(screen.getByLabelText('Descripción del problema'), { target: { value: 'Router apagado' } })
    fireEvent.click(screen.getByText('Crear solicitud'))

    expect(await screen.findByText('No se pudo crear la solicitud')).toBeInTheDocument()
  })

  it('un titulo o descripcion de solo espacios cuenta como vacio', async () => {
    // El chequeo real es title.trim()/description.trim(), mas estricto que "!title" -- la
    // unica prueba de validacion existente dejaba los campos literalmente vacios, nunca
    // ejercito el .trim().
    const spy = vi.spyOn(ticketsApi, 'createTicket')
    render(<CreateTicketModal onClose={vi.fn()} onCreated={vi.fn()} />)

    fireEvent.change(screen.getByLabelText('Título'), { target: { value: '   ' } })
    fireEvent.change(screen.getByLabelText('Descripción del problema'), { target: { value: '   ' } })
    fireEvent.click(screen.getByText('Crear solicitud'))

    expect(await screen.findByText('Completa el título y la descripción')).toBeInTheDocument()
    expect(spy).not.toHaveBeenCalled()
  })

  it('recorta y envia telefono de contacto y direccion cuando se completan', async () => {
    // La unica prueba existente de un envio exitoso dejaba estos dos campos vacios (por eso
    // salen undefined) -- el camino real donde SI se completan, con espacios de sobra que
    // hay que recortar, nunca se ejercitaba.
    const created = { ticketId: 't1' } as TicketResponse
    const spy = vi.spyOn(ticketsApi, 'createTicket').mockResolvedValue(created)
    render(<CreateTicketModal onClose={vi.fn()} onCreated={vi.fn()} />)

    fireEvent.change(screen.getByLabelText('Título'), { target: { value: 'Sin internet' } })
    fireEvent.change(screen.getByLabelText('Descripción del problema'), { target: { value: 'Router apagado' } })
    fireEvent.change(screen.getByLabelText('Teléfono de contacto (opcional)'), { target: { value: '  0991234567  ' } })
    fireEvent.change(screen.getByLabelText('Dirección (opcional)'), { target: { value: '  Av. Siempre Viva  ' } })
    fireEvent.click(screen.getByText('Crear solicitud'))

    await waitFor(() =>
      expect(spy).toHaveBeenCalledWith(
        expect.objectContaining({ contactPhone: '0991234567', address: 'Av. Siempre Viva' }),
      ),
    )
  })

  it('cambiar la zona envia la zona seleccionada, no siempre la primera', async () => {
    const created = { ticketId: 't1' } as TicketResponse
    const spy = vi.spyOn(ticketsApi, 'createTicket').mockResolvedValue(created)
    render(<CreateTicketModal onClose={vi.fn()} onCreated={vi.fn()} />)

    fireEvent.change(screen.getByLabelText('Zona'), { target: { value: 'QUEVEDO_SUR' } })
    fireEvent.change(screen.getByLabelText('Título'), { target: { value: 'Sin internet' } })
    fireEvent.change(screen.getByLabelText('Descripción del problema'), { target: { value: 'Router apagado' } })
    fireEvent.click(screen.getByText('Crear solicitud'))

    await waitFor(() => expect(spy).toHaveBeenCalledWith(expect.objectContaining({ zone: 'QUEVEDO_SUR' })))
  })

  it('clic en el fondo (fuera del formulario) cierra el modal', () => {
    const onClose = vi.fn()
    const { container } = render(<CreateTicketModal onClose={onClose} onCreated={vi.fn()} />)

    fireEvent.click(container.firstChild as HTMLElement)

    expect(onClose).toHaveBeenCalledTimes(1)
  })

  it('clic dentro del formulario NO cierra el modal', () => {
    // Guarda contra la regresion mas facil de "e.target === e.currentTarget": si se quitara,
    // cualquier clic dentro del formulario (incluido el titulo) cerraria el modal por error.
    const onClose = vi.fn()
    render(<CreateTicketModal onClose={onClose} onCreated={vi.fn()} />)

    fireEvent.click(screen.getByText('Nueva solicitud de soporte'))

    expect(onClose).not.toHaveBeenCalled()
  })
})
