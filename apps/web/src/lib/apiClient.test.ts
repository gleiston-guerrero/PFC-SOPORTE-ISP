import { describe, it, expect, vi, beforeEach } from 'vitest'
import { AxiosHeaders } from 'axios'
import { authClient, ticketsClient, reportsClient } from './apiClient'
import { getAccessToken } from '../features/auth/session'

// attachBearerToken() (el interceptor que adjunta "Authorization: Bearer <token>") no tenia
// ninguna prueba directa: authApi.test.ts mockea axios.post entero, sin pasar nunca por la
// cadena real de interceptores. Se invoca el interceptor de peticion registrado, no un
// servidor real ni un adaptador de mocks (ninguno esta instalado en este proyecto) -- tecnica
// estandar para probar interceptores de axios en aislamiento.
vi.mock('../features/auth/session', () => ({
  getAccessToken: vi.fn(),
}))

function interceptorDePeticion(client: typeof authClient) {
  // eslint-disable-next-line @typescript-eslint/no-explicit-any -- InterceptorManager no expone `handlers` en sus tipos publicos.
  const handlers = (client.interceptors.request as any).handlers
  return handlers[0].fulfilled
}

describe('apiClient - interceptor Authorization Bearer', () => {
  beforeEach(() => {
    vi.mocked(getAccessToken).mockReset()
  })

  it('adjunta el token cuando hay uno guardado', () => {
    vi.mocked(getAccessToken).mockReturnValue('token-real-123')
    const config = { headers: new AxiosHeaders() }

    const resultado = interceptorDePeticion(authClient)(config)

    expect(resultado.headers.get('Authorization')).toBe('Bearer token-real-123')
  })

  it('no adjunta ningun encabezado cuando no hay sesion', () => {
    vi.mocked(getAccessToken).mockReturnValue(null)
    const config = { headers: new AxiosHeaders() }

    const resultado = interceptorDePeticion(authClient)(config)

    expect(resultado.headers.has('Authorization')).toBe(false)
  })

  it('el mismo comportamiento se aplica a ticketsClient y reportsClient', () => {
    vi.mocked(getAccessToken).mockReturnValue('token-compartido')

    const resultadoTickets = interceptorDePeticion(ticketsClient)({ headers: new AxiosHeaders() })
    const resultadoReports = interceptorDePeticion(reportsClient)({ headers: new AxiosHeaders() })

    expect(resultadoTickets.headers.get('Authorization')).toBe('Bearer token-compartido')
    expect(resultadoReports.headers.get('Authorization')).toBe('Bearer token-compartido')
  })
})
