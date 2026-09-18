import { describe, it, expect, vi } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import { ReportsPage } from './ReportsPage'
import * as reportsApi from '../api/reportsApi'

describe('ReportsPage', () => {
  it('muestra el total y los desgloses cuando la carga tiene exito', async () => {
    vi.spyOn(reportsApi, 'getSummary').mockResolvedValue({
      totalTickets: 5,
      byStatus: { NUEVO: 3, RESUELTO: 2 },
      byZone: { QUEVEDO_NORTE: 5 },
      byCategory: { HARDWARE: 5 },
    })
    render(<ReportsPage />)

    expect(await screen.findByText('5 tickets en total')).toBeInTheDocument()
    expect(screen.getByText('Tickets por estado')).toBeInTheDocument()
    expect(screen.getByText('QUEVEDO_NORTE')).toBeInTheDocument()
  })

  it('muestra un error si la carga falla', async () => {
    vi.spyOn(reportsApi, 'getSummary').mockRejectedValue(new Error('network'))
    render(<ReportsPage />)

    await waitFor(() => expect(screen.getByText('No se pudieron cargar los reportes')).toBeInTheDocument())
  })

  it('muestra el texto de carga antes de que getSummary responda', () => {
    vi.spyOn(reportsApi, 'getSummary').mockReturnValue(new Promise(() => {})) // nunca resuelve durante la prueba
    render(<ReportsPage />)

    expect(screen.getByText('Cargando reportes…')).toBeInTheDocument()
  })

  it('un desglose vacio muestra un guion en vez de quedar en blanco', async () => {
    // BreakdownCard renderiza "-" cuando entries.length === 0 -- ningun desglose de la unica
    // prueba de exito quedaba vacio, asi que esa rama nunca se ejercitaba.
    vi.spyOn(reportsApi, 'getSummary').mockResolvedValue({
      totalTickets: 0,
      byStatus: {},
      byZone: {},
      byCategory: {},
    })
    render(<ReportsPage />)

    await screen.findByText('0 tickets en total')
    expect(screen.getAllByText('—')).toHaveLength(3)
  })

  it('con un solo valor en el desglose, la barra ocupa el 100% del ancho', async () => {
    // max = Math.max(1, ...valores) evita dividir entre cero cuando todo esta en 0, pero
    // ninguna prueba verificaba el calculo real del ancho (value/max*100%).
    vi.spyOn(reportsApi, 'getSummary').mockResolvedValue({
      totalTickets: 5,
      byStatus: { RESUELTO: 5 },
      byZone: {},
      byCategory: {},
    })
    const { container } = render(<ReportsPage />)

    await screen.findByText('5 tickets en total')
    const barra = container.querySelector('[style*="width: 100%"]')
    expect(barra).not.toBeNull()
  })

  it('un estado desconocido en byStatus cae al color de respaldo, no queda sin color', async () => {
    // STATUS_VAR aqui es una copia manual independiente de la de Badges.tsx (Ronda 23) --
    // si las dos se desincronizan, un estado nuevo o mal escrito debe seguir viendose con
    // ALGUN color (fallback a navy vía "?? 'var(--color-navy)'"), no quedar sin estilo.
    vi.spyOn(reportsApi, 'getSummary').mockResolvedValue({
      totalTickets: 1,
      byStatus: { ESTADO_INVENTADO: 1 },
      byZone: {},
      byCategory: {},
    })
    const { container } = render(<ReportsPage />)

    await screen.findByText('1 tickets en total')
    const barra = container.querySelector('[style*="width: 100%"]') as HTMLElement | null
    expect(barra?.style.background).toBe('var(--color-navy)')
  })
})
