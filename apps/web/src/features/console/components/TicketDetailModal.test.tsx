import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { TicketDetailModal } from './TicketDetailModal'
import { getTicket } from '../api/ticketsApi'
import type { TicketResponse } from '../types/ticket'

// TicketDetailModal no tenia ninguna prueba: pide GET /tickets/{id} de nuevo en vez de reusar
// el objeto de la fila (para no mostrar un estado desactualizado si el EscalationScheduler
// cambio el ticket entre que se cargo la lista y el clic), maneja un estado de error real, y
// tiene una guardia de cierre (clic en el fondo cierra, clic dentro del modal no) que es facil
// de romper sin que ninguna prueba lo note.
vi.mock('../api/ticketsApi', () => ({
  getTicket: vi.fn(),
}))

function ticket(overrides: Partial<TicketResponse> = {}): TicketResponse {
  return {
    zone: 'QUEVEDO_NORTE',
    ticketId: '0fb2c5be-3e80-46aa-af79-f2aa597f8e4a',
    clientId: 'cliente-1',
    technicianId: null,
    category: 'HARDWARE',
    priority: 'MEDIO',
    status: 'ASIGNADO',
    description: 'Router sin luz de enlace',
    createdAt: '2026-09-16T05:18:52.933Z',
    slaDeadline: '2026-09-17T05:18:52.933Z',
    resolvedAt: null,
    slaBreached: false,
    ...overrides,
  }
}

describe('TicketDetailModal', () => {
  beforeEach(() => {
    vi.mocked(getTicket).mockReset()
  })

  it('muestra el esqueleto de carga antes de que responda getTicket', () => {
    vi.mocked(getTicket).mockReturnValue(new Promise(() => {})) // nunca resuelve durante la prueba
    render(<TicketDetailModal ticketId="t1" technicianName={null} onClose={vi.fn()} />)

    expect(screen.getByRole('dialog')).toBeInTheDocument()
    expect(screen.queryByText('Router sin luz de enlace')).not.toBeInTheDocument()
  })

  it('muestra los datos del ticket una vez que getTicket responde', async () => {
    vi.mocked(getTicket).mockResolvedValue(ticket())
    render(<TicketDetailModal ticketId="0fb2c5be-3e80-46aa-af79-f2aa597f8e4a" technicianName={null} onClose={vi.fn()} />)

    await waitFor(() => {
      expect(screen.getByText('Router sin luz de enlace')).toBeInTheDocument()
    })
    expect(getTicket).toHaveBeenCalledWith('0fb2c5be-3e80-46aa-af79-f2aa597f8e4a')
  })

  it('si getTicket falla, muestra el mensaje de error y no el esqueleto', async () => {
    vi.mocked(getTicket).mockRejectedValue(new Error('500'))
    render(<TicketDetailModal ticketId="t1" technicianName={null} onClose={vi.fn()} />)

    // exact:false porque el texto viene partido en dos nodos hermanos ("📡 " literal +
    // {error}), asi que ningun nodo por si solo iguala exactamente el mensaje completo.
    await waitFor(() => {
      expect(screen.getByText('No se pudo cargar el detalle del ticket', { exact: false })).toBeInTheDocument()
    })
  })

  it('sin tecnico asignado muestra el chip de "sin asignar" en vez del id crudo', async () => {
    vi.mocked(getTicket).mockResolvedValue(ticket({ technicianId: null }))
    render(<TicketDetailModal ticketId="t1" technicianName={null} onClose={vi.fn()} />)

    await waitFor(() => expect(screen.getByText('Router sin luz de enlace')).toBeInTheDocument())
    expect(screen.getByText('Sin asignar')).toBeInTheDocument()
  })

  it('con tecnico asignado muestra el nombre en vez de su id', async () => {
    vi.mocked(getTicket).mockResolvedValue(ticket({ technicianId: 'tec-1' }))
    render(<TicketDetailModal ticketId="t1" technicianName="Ana Torres" onClose={vi.fn()} />)

    await waitFor(() => expect(screen.getByText('Ana Torres')).toBeInTheDocument())
  })

  it('la tecla Escape cierra el modal', async () => {
    vi.mocked(getTicket).mockResolvedValue(ticket())
    const onClose = vi.fn()
    render(<TicketDetailModal ticketId="t1" technicianName={null} onClose={onClose} />)
    await waitFor(() => expect(screen.getByText('Router sin luz de enlace')).toBeInTheDocument())

    await userEvent.keyboard('{Escape}')

    expect(onClose).toHaveBeenCalledTimes(1)
  })

  it('clic en el fondo (fuera del contenido) cierra el modal', async () => {
    vi.mocked(getTicket).mockResolvedValue(ticket())
    const onClose = vi.fn()
    render(<TicketDetailModal ticketId="t1" technicianName={null} onClose={onClose} />)
    await waitFor(() => expect(screen.getByText('Router sin luz de enlace')).toBeInTheDocument())

    await userEvent.click(screen.getByRole('dialog').parentElement!)

    expect(onClose).toHaveBeenCalledTimes(1)
  })

  it('clic dentro del contenido del modal NO lo cierra', async () => {
    // Guarda contra la regresion mas facil de esta guardia: si alguien quita la
    // comparacion "e.target === e.currentTarget", cualquier clic dentro del modal
    // (incluida la descripcion del ticket) lo cerraria por error.
    vi.mocked(getTicket).mockResolvedValue(ticket())
    const onClose = vi.fn()
    render(<TicketDetailModal ticketId="t1" technicianName={null} onClose={onClose} />)
    await waitFor(() => expect(screen.getByText('Router sin luz de enlace')).toBeInTheDocument())

    await userEvent.click(screen.getByText('Router sin luz de enlace'))

    expect(onClose).not.toHaveBeenCalled()
  })

  it('el boton de cerrar llama a onClose', async () => {
    // Hay dos controles con el mismo nombre accesible "Cerrar" (la X del encabezado y el
    // boton del pie); se verifican los dos, no solo el primero que encuentre la consulta.
    vi.mocked(getTicket).mockResolvedValue(ticket())
    const onClose = vi.fn()
    render(<TicketDetailModal ticketId="t1" technicianName={null} onClose={onClose} />)
    await waitFor(() => expect(screen.getByText('Router sin luz de enlace')).toBeInTheDocument())

    const botonesCerrar = screen.getAllByRole('button', { name: 'Cerrar' })
    expect(botonesCerrar).toHaveLength(2)
    await userEvent.click(botonesCerrar[0])
    await userEvent.click(botonesCerrar[1])

    expect(onClose).toHaveBeenCalledTimes(2)
  })
})
