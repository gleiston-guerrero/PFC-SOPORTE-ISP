import { describe, it, expect, vi, beforeEach } from 'vitest'
import { act, renderHook, waitFor } from '@testing-library/react'
import { useTickets } from './useTickets'
import * as ticketsApi from '../api/ticketsApi'
import type { TicketResponse } from '../types/ticket'

const mockTickets: TicketResponse[] = [
  {
    zone: 'QUEVEDO_NORTE',
    ticketId: '1',
    clientId: 'c1',
    technicianId: null,
    category: null,
    priority: 'ALTO',
    status: 'NUEVO',
    description: 'Sin acceso a Internet',
    createdAt: '2026-01-01T00:00:00Z',
    slaDeadline: null,
    resolvedAt: null,
    slaBreached: false,
  },
  {
    zone: 'QUEVEDO_SUR',
    ticketId: '2',
    clientId: 'c2',
    technicianId: null,
    category: null,
    priority: 'BAJO',
    status: 'RESUELTO',
    description: 'Router lento',
    createdAt: '2026-01-02T00:00:00Z',
    slaDeadline: null,
    resolvedAt: null,
    slaBreached: true,
  },
]

describe('useTickets', () => {
  beforeEach(() => {
    vi.spyOn(ticketsApi, 'listTickets').mockResolvedValue(mockTickets)
  })

  it('carga los tickets y calcula los conteos totales', async () => {
    const { result } = renderHook(() => useTickets())
    await waitFor(() => expect(result.current.loading).toBe(false))
    expect(result.current.allCount).toBe(2)
    expect(result.current.breachedCount).toBe(1)
    expect(result.current.escaladoCount).toBe(0)
    expect(result.current.tickets).toHaveLength(2)
  })

  it('filtra por texto de busqueda en la descripcion', async () => {
    const { result } = renderHook(() => useTickets())
    await waitFor(() => expect(result.current.loading).toBe(false))

    act(() => result.current.setSearch('router'))

    expect(result.current.tickets).toHaveLength(1)
    expect(result.current.tickets[0].ticketId).toBe('2')
    // el conteo del resumen sigue siendo sobre TODOS los tickets, no solo los filtrados
    expect(result.current.allCount).toBe(2)
  })

  it('filtra por zona', async () => {
    const { result } = renderHook(() => useTickets())
    await waitFor(() => expect(result.current.loading).toBe(false))

    act(() => result.current.setZoneFilter('QUEVEDO_SUR'))

    expect(result.current.tickets).toHaveLength(1)
    expect(result.current.tickets[0].zone).toBe('QUEVEDO_SUR')
  })

  it('filtra por estado', async () => {
    const { result } = renderHook(() => useTickets())
    await waitFor(() => expect(result.current.loading).toBe(false))

    act(() => result.current.setStatusFilter('RESUELTO'))

    expect(result.current.tickets).toHaveLength(1)
    expect(result.current.tickets[0].status).toBe('RESUELTO')
  })

  it('filtra tambien por ticketId, no solo por descripcion', async () => {
    // Ver el comentario del propio hook: sirve para verificar en la consola que una accion
    // hecha desde otro cliente (ej. la app movil) se propago al mismo backend -- se copia el
    // id mostrado alla y se pega aqui. Ninguna prueba existente lo ejercitaba, solo
    // descripcion.
    const { result } = renderHook(() => useTickets())
    await waitFor(() => expect(result.current.loading).toBe(false))

    act(() => result.current.setSearch('2'))

    expect(result.current.tickets).toHaveLength(1)
    expect(result.current.tickets[0].ticketId).toBe('2')
  })

  it('busqueda de solo espacios se trata como vacia (no filtra nada)', async () => {
    const { result } = renderHook(() => useTickets())
    await waitFor(() => expect(result.current.loading).toBe(false))

    act(() => result.current.setSearch('   '))

    expect(result.current.tickets).toHaveLength(2)
  })

  it('busqueda, zona y estado se combinan con AND, no con OR', async () => {
    // Las cuatro pruebas de filtro existentes probaban cada filtro por separado -- nunca la
    // combinacion, que es donde un OR por error en vez de AND pasaria desapercibido.
    const { result } = renderHook(() => useTickets())
    await waitFor(() => expect(result.current.loading).toBe(false))

    act(() => {
      result.current.setSearch('router')
      result.current.setZoneFilter('QUEVEDO_NORTE') // el ticket "router" es de QUEVEDO_SUR
    })

    expect(result.current.tickets).toHaveLength(0)
  })

  it('refresh vuelve a pedir los tickets al backend', async () => {
    const spy = vi.spyOn(ticketsApi, 'listTickets').mockResolvedValue(mockTickets)
    const { result } = renderHook(() => useTickets())
    await waitFor(() => expect(result.current.loading).toBe(false))
    expect(spy).toHaveBeenCalledTimes(1)

    act(() => result.current.refresh())

    await waitFor(() => expect(spy).toHaveBeenCalledTimes(2))
  })

  it('reporta error si la carga falla, sin romper el hook', async () => {
    vi.spyOn(ticketsApi, 'listTickets').mockRejectedValueOnce(new Error('network'))
    const { result } = renderHook(() => useTickets())
    await waitFor(() => expect(result.current.loading).toBe(false))
    expect(result.current.error).not.toBeNull()
  })
})
