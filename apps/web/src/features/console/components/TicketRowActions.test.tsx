import { describe, it, expect, vi } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { TicketRowActions } from './TicketRowActions'
import * as ticketsApi from '../api/ticketsApi'
import type { TicketResponse } from '../types/ticket'
import type { UserResponse } from '../../admin/types/user'

const baseTicket: TicketResponse = {
  zone: 'QUEVEDO_NORTE',
  ticketId: 't1',
  clientId: 'c1',
  technicianId: null,
  category: null,
  priority: null,
  status: 'NUEVO',
  description: 'x',
  createdAt: '2026-01-01T00:00:00Z',
  slaDeadline: null,
  resolvedAt: null,
  slaBreached: false,
}

const technicians: UserResponse[] = [
  { id: 'tec-norte', email: 'norte@test.com', fullName: 'Tecnico Norte', role: 'TECNICO', zone: 'QUEVEDO_NORTE', active: true, createdAt: '2026-01-01T00:00:00Z' },
  { id: 'tec-sur', email: 'sur@test.com', fullName: 'Tecnico Sur', role: 'TECNICO', zone: 'QUEVEDO_SUR', active: true, createdAt: '2026-01-01T00:00:00Z' },
]

describe('TicketRowActions', () => {
  it('cambia el estado y llama a onChanged', async () => {
    const spy = vi.spyOn(ticketsApi, 'updateTicketStatus').mockResolvedValue({ ...baseTicket, status: 'EN_PROGRESO' })
    const onChanged = vi.fn()
    render(<TicketRowActions ticket={baseTicket} currentUserId="u1" role="TECNICO" technicians={[]} onChanged={onChanged} />)

    fireEvent.change(screen.getByRole('combobox'), { target: { value: 'EN_PROGRESO' } })

    await waitFor(() => expect(spy).toHaveBeenCalledWith('t1', 'EN_PROGRESO'))
    expect(onChanged).toHaveBeenCalled()
  })

  it('TECNICO ve el boton "Asignarme" (icono con nombre accesible) solo si el ticket no tiene tecnico', () => {
    const { rerender } = render(
      <TicketRowActions ticket={baseTicket} currentUserId="u1" role="TECNICO" technicians={[]} onChanged={vi.fn()} />,
    )
    expect(screen.getByRole('button', { name: 'Asignarme' })).toBeInTheDocument()

    rerender(
      <TicketRowActions
        ticket={{ ...baseTicket, technicianId: 'tec-1' }}
        currentUserId="u1"
        role="TECNICO"
        technicians={[]}
        onChanged={vi.fn()}
      />,
    )
    expect(screen.queryByRole('button', { name: 'Asignarme' })).not.toBeInTheDocument()
  })

  it('al hacer click en Asignarme, el TECNICO se autoasigna el ticket', async () => {
    const spy = vi.spyOn(ticketsApi, 'assignTechnician').mockResolvedValue({ ...baseTicket, technicianId: 'u1' })
    const onChanged = vi.fn()
    render(<TicketRowActions ticket={baseTicket} currentUserId="u1" role="TECNICO" technicians={[]} onChanged={onChanged} />)

    fireEvent.click(screen.getByRole('button', { name: 'Asignarme' }))

    await waitFor(() => expect(spy).toHaveBeenCalledWith('t1', 'u1'))
    expect(onChanged).toHaveBeenCalled()
  })

  it('ADMIN ve un selector con los tecnicos de la zona del ticket, no el boton Asignarme', () => {
    render(<TicketRowActions ticket={baseTicket} currentUserId="admin1" role="ADMIN" technicians={technicians} onChanged={vi.fn()} />)

    expect(screen.queryByRole('button', { name: 'Asignarme' })).not.toBeInTheDocument()
    const picker = screen.getByRole('combobox', { name: 'Asignar a técnico…' })
    expect(picker).toBeInTheDocument()
    // baseTicket.zone es QUEVEDO_NORTE -- solo el tecnico de esa zona debe listarse.
    expect(screen.getByText('Tecnico Norte')).toBeInTheDocument()
    expect(screen.queryByText('Tecnico Sur')).not.toBeInTheDocument()
  })

  it('al elegir un tecnico en el selector, ADMIN le asigna ese ticket (no a si mismo)', async () => {
    const spy = vi.spyOn(ticketsApi, 'assignTechnician').mockResolvedValue({ ...baseTicket, technicianId: 'tec-norte' })
    const onChanged = vi.fn()
    render(
      <TicketRowActions ticket={baseTicket} currentUserId="admin1" role="ADMIN" technicians={technicians} onChanged={onChanged} />,
    )

    fireEvent.change(screen.getByRole('combobox', { name: 'Asignar a técnico…' }), { target: { value: 'tec-norte' } })

    await waitFor(() => expect(spy).toHaveBeenCalledWith('t1', 'tec-norte'))
    expect(onChanged).toHaveBeenCalled()
  })

  it('seleccionar el mismo estado que ya tiene el ticket no llama a la API', () => {
    // El guard "if (status === ticket.status) return" nunca se ejercitaba -- sin el,
    // reseleccionar el mismo valor en el select dispararia un PATCH redundante cada vez.
    const spy = vi.spyOn(ticketsApi, 'updateTicketStatus')
    render(<TicketRowActions ticket={baseTicket} currentUserId="u1" role="TECNICO" technicians={[]} onChanged={vi.fn()} />)

    fireEvent.change(screen.getByRole('combobox'), { target: { value: baseTicket.status } })

    expect(spy).not.toHaveBeenCalled()
  })

  it('con rol CLIENTE (o sin rol) no se muestra ningun control de asignacion', () => {
    // Defensa en profundidad: ConsolePage ya oculta este componente para CLIENTE, pero el
    // componente en si nunca probaba que, si de todos modos se renderizara, no ofreciera
    // ni "Asignarme" ni el selector de ADMIN -- las siete pruebas de arriba solo cubren
    // TECNICO y ADMIN.
    render(<TicketRowActions ticket={baseTicket} currentUserId="c1" role="CLIENTE" technicians={technicians} onChanged={vi.fn()} />)

    expect(screen.queryByRole('button', { name: 'Asignarme' })).not.toBeInTheDocument()
    expect(screen.queryByRole('combobox', { name: 'Asignar a técnico…' })).not.toBeInTheDocument()
  })

  it('TECNICO no ve RESUELTO como opcion del selector de estado (exige evidencia que esta consola no captura)', () => {
    render(<TicketRowActions ticket={baseTicket} currentUserId="u1" role="TECNICO" technicians={[]} onChanged={vi.fn()} />)

    const opciones = screen.getAllByRole('option').map((o) => (o as HTMLOptionElement).value)
    expect(opciones).not.toContain('RESUELTO')
  })

  it('ADMIN si ve RESUELTO como opcion del selector de estado (puede resolver sin evidencia)', () => {
    render(<TicketRowActions ticket={baseTicket} currentUserId="admin1" role="ADMIN" technicians={[]} onChanged={vi.fn()} />)

    const opciones = screen.getAllByRole('option').map((o) => (o as HTMLOptionElement).value)
    expect(opciones).toContain('RESUELTO')
  })

  it('un ticket ya RESUELTO sigue mostrando ese estado en el selector aunque lo vea un TECNICO', () => {
    render(
      <TicketRowActions
        ticket={{ ...baseTicket, status: 'RESUELTO' }}
        currentUserId="u1"
        role="TECNICO"
        technicians={[]}
        onChanged={vi.fn()}
      />,
    )

    expect(screen.getByRole('combobox')).toHaveValue('RESUELTO')
  })

  it('ADMIN sin tecnicos en la zona del ticket ve un aviso en vez de un selector vacio', () => {
    render(
      <TicketRowActions
        ticket={{ ...baseTicket, zone: 'QUEVEDO_CENTRO' }}
        currentUserId="admin1"
        role="ADMIN"
        technicians={technicians}
        onChanged={vi.fn()}
      />,
    )

    expect(screen.queryByRole('combobox', { name: 'Asignar a técnico…' })).not.toBeInTheDocument()
    expect(screen.getByText('Sin técnicos en esta zona')).toBeInTheDocument()
  })
})
