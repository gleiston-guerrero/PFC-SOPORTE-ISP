import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import { AssignedChip, PriorityBadge, SlaBadge, StatusBadge, UnassignedChip } from './Badges'
import type { Priority, TicketStatus } from '../types/ticket'

describe('Badges', () => {
  it('StatusBadge muestra la etiqueta traducida del estado', () => {
    render(<StatusBadge status="ESCALADO" />)
    expect(screen.getByText('Escalado')).toBeInTheDocument()
  })

  it('StatusBadge: ningun par de estados comparte el mismo color de fondo', () => {
    // Solo se probaba la etiqueta traducida, nunca el color -- STATUS_VAR es un mapa
    // manual (no un enum exhaustivo verificado por el compilador como en mobile), asi que
    // un color copiado por error de un estado a otro pasaria el build sin que nada lo note.
    const estados: TicketStatus[] = ['NUEVO', 'ASIGNADO', 'EN_PROGRESO', 'ESCALADO', 'RESUELTO', 'CERRADO']
    const colores = estados.map((status) => {
      const { container, unmount } = render(<StatusBadge status={status} />)
      const color = container.querySelector('span')!.style.background
      unmount()
      return color
    })

    expect(new Set(colores).size).toBe(estados.length)
  })

  it('PriorityBadge muestra la etiqueta traducida de la prioridad', () => {
    render(<PriorityBadge priority="CRITICO" />)
    expect(screen.getByText('Crítico')).toBeInTheDocument()
  })

  it('PriorityBadge: CRITICO reutiliza a proposito el color de ESCALADO, no uno propio', () => {
    // Documentado en el codigo (mismo criterio que colorForPriority en mobile, Ronda 15):
    // prioridad no tiene paleta propia, reusa los tonos de estado -- nunca se verificaba.
    render(<PriorityBadge priority="CRITICO" />)
    const span = screen.getByText('Crítico')

    expect(span.style.color).toBe('var(--status-escalado)')
  })

  it('PriorityBadge: las cuatro prioridades tienen colores distintos entre si', () => {
    const prioridades: Priority[] = ['CRITICO', 'ALTO', 'MEDIO', 'BAJO']
    const colores = prioridades.map((priority) => {
      const { container, unmount } = render(<PriorityBadge priority={priority} />)
      const color = container.querySelector('span')!.style.color
      unmount()
      return color
    })

    expect(new Set(colores).size).toBe(prioridades.length)
  })

  it('AssignedChip muestra la etiqueta recibida', () => {
    // Ninguna prueba existente lo ejercitaba: reemplaza el UUID crudo del tecnico para
    // CLIENTE (ver comentario del componente), simetrico a UnassignedChip que si se probaba.
    render(<AssignedChip label="Asignado" />)
    expect(screen.getByText('Asignado')).toBeInTheDocument()
  })

  it('SlaBadge muestra OK cuando no esta vencido', () => {
    render(<SlaBadge breached={false} />)
    expect(screen.getByText('OK')).toBeInTheDocument()
  })

  it('SlaBadge muestra el aviso cuando esta vencido', () => {
    render(<SlaBadge breached={true} />)
    expect(screen.getByText(/Vencido/)).toBeInTheDocument()
  })

  it('UnassignedChip muestra la etiqueta recibida', () => {
    render(<UnassignedChip label="Sin asignar" />)
    expect(screen.getByText('Sin asignar')).toBeInTheDocument()
  })
})
