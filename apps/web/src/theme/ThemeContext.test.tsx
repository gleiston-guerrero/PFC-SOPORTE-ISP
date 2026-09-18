import { describe, it, expect, beforeEach } from 'vitest'
import { act, renderHook } from '@testing-library/react'
import { ThemeProvider, useTheme } from './ThemeContext'

describe('ThemeContext', () => {
  beforeEach(() => {
    window.localStorage.clear()
    document.documentElement.removeAttribute('data-theme')
  })

  it('toggle alterna entre claro y oscuro y lo refleja en <html data-theme>', () => {
    const { result } = renderHook(() => useTheme(), { wrapper: ThemeProvider })

    const initial = result.current.mode
    act(() => result.current.toggle())

    expect(result.current.mode).not.toBe(initial)
    expect(document.documentElement.getAttribute('data-theme')).toBe(result.current.mode)
  })

  it('useTheme fuera de ThemeProvider lanza un error claro', () => {
    // Se prueba el hook aislado (sin wrapper) para confirmar la validacion explicita.
    expect(() => renderHook(() => useTheme())).toThrowError(/useTheme debe usarse dentro/)
  })

  it('lee el tema ya guardado en localStorage al iniciar, sin caer a la preferencia del sistema', () => {
    // Todas las pruebas de arriba montan sin nada guardado, asi que siempre ejercitan el
    // fallback de matchMedia (mockeado a "sin preferencia oscura" en test/setup.ts) -- la
    // rama real de "usuario que vuelve" (tema ya elegido antes) nunca se probaba.
    window.localStorage.setItem('soporte-web-theme', 'dark')

    const { result } = renderHook(() => useTheme(), { wrapper: ThemeProvider })

    expect(result.current.mode).toBe('dark')
  })

  it('toggle persiste el nuevo valor en localStorage, no solo en el atributo del DOM', () => {
    // La prueba original de toggle solo verificaba data-theme; el proposito real de
    // guardarlo en localStorage (que un usuario que vuelve recupere su eleccion) nunca se
    // confirmaba de punta a punta.
    const { result } = renderHook(() => useTheme(), { wrapper: ThemeProvider })
    const initial = result.current.mode

    act(() => result.current.toggle())

    expect(window.localStorage.getItem('soporte-web-theme')).toBe(result.current.mode)
    expect(window.localStorage.getItem('soporte-web-theme')).not.toBe(initial)
  })
})
