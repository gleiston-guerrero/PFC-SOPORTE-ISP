import { describe, it, expect, afterEach } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import i18n from '../../../i18n'
import { ThemeProvider } from '../../../theme/ThemeContext'
import { SettingsPage } from './SettingsPage'

function renderSettings() {
  return render(
    <ThemeProvider>
      <SettingsPage />
    </ThemeProvider>,
  )
}

describe('SettingsPage', () => {
  afterEach(async () => {
    await i18n.changeLanguage('es')
  })

  it('cambia el idioma al hacer clic en English', async () => {
    renderSettings()
    fireEvent.click(screen.getByRole('button', { name: 'English' }))
    expect(i18n.language).toBe('en')
    expect(await screen.findByText('Settings')).toBeInTheDocument()
  })

  it('cambia el tema al hacer clic en Oscuro', () => {
    renderSettings()
    fireEvent.click(screen.getByRole('button', { name: /Oscuro/ }))
    expect(document.documentElement.getAttribute('data-theme')).toBe('dark')
  })

  it('hacer clic en el tema ya activo no lo alterna al otro', () => {
    // El guard "mode !== 'light' && toggle()" nunca se ejercitaba: sin el, reclicar el
    // boton del tema ya activo llamaria a toggle() igual y cambiaria al otro tema por
    // error -- exactamente el mismo tipo de guard redundante ya protegido en
    // TicketRowActions (Ronda 22) para el estado del ticket.
    renderSettings()
    // Con matchMedia mockeado sin preferencia oscura (test/setup.ts), el tema inicial es 'light'.
    fireEvent.click(screen.getByRole('button', { name: /Claro/ }))

    expect(document.documentElement.getAttribute('data-theme')).toBe('light')
  })

  it('el boton del tema activo se ve visualmente distinto del inactivo', () => {
    // Mismo patron de hueco verificado en rondas anteriores (Badges, ReportsPage,
    // AppLayout): pillStyle(active) nunca se comparaba, solo el efecto de data-theme.
    renderSettings()
    const claro = screen.getByRole('button', { name: /Claro/ })
    const oscuro = screen.getByRole('button', { name: /Oscuro/ })

    expect(claro.style.background).not.toBe(oscuro.style.background)
  })
})
