package ec.edu.uteq.soporte.mobile.data.repository

import ec.edu.uteq.soporte.mobile.data.remote.AuthApi
import ec.edu.uteq.soporte.mobile.data.remote.dto.ApiResponse
import ec.edu.uteq.soporte.mobile.data.remote.dto.AuthResponse
import ec.edu.uteq.soporte.mobile.data.remote.dto.LoginRequest
import ec.edu.uteq.soporte.mobile.data.session.SessionManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * AuthRepository no tenia ninguna prueba: es el unico punto que decide si un login exitoso en
 * el servidor (HTTP 200 con ApiResponse.data) realmente cuenta como sesion iniciada en el
 * cliente -- envuelve la llamada en runCatching y exige `data` no nulo con requireNotNull antes
 * de guardar los tokens, una guardia que ninguna prueba verificaba.
 */
class AuthRepositoryTest {

    private val authApi = mockk<AuthApi>()
    private val sessionManager = mockk<SessionManager>(relaxUnitFun = true)
    private val repository = AuthRepository(authApi, sessionManager)

    @Test
    fun `login exitoso guarda el access y refresh token`() = runTest {
        coEvery { authApi.login(LoginRequest("cliente@test.com", "Passw0rd!")) } returns
            ApiResponse(
                data = AuthResponse("access-123", "refresh-456", "2026-09-17T12:00:00Z"),
                message = "OK",
                timestamp = "2026-09-17T11:00:00Z",
            )

        val resultado = repository.login("cliente@test.com", "Passw0rd!")

        assertTrue(resultado.isSuccess)
        coVerify(exactly = 1) { sessionManager.saveTokens("access-123", "refresh-456") }
    }

    @Test
    fun `login con data nula en la respuesta falla en vez de guardar tokens vacios`() = runTest {
        // Un ApiResponse con data=null pero HTTP 200 no deberia contarse como login exitoso --
        // sin la guardia requireNotNull, sessionManager.saveTokens se llamaria con valores
        // nulos y el repositorio reportaria una sesion iniciada que en realidad no existe.
        coEvery { authApi.login(any()) } returns ApiResponse(data = null, message = "OK", timestamp = null)

        val resultado = repository.login("cliente@test.com", "malo")

        assertTrue(resultado.isFailure)
        coVerify(exactly = 0) { sessionManager.saveTokens(any(), any()) }
    }

    @Test
    fun `login cuando el servidor responde con error propaga el fallo sin lanzar`() = runTest {
        val fallo = RuntimeException("401 Unauthorized")
        coEvery { authApi.login(any()) } throws fallo

        val resultado = repository.login("cliente@test.com", "incorrecta")

        assertTrue(resultado.isFailure)
        assertTrue(resultado.exceptionOrNull() === fallo)
    }

    @Test
    fun `isLoggedIn delega directamente en SessionManager`() {
        every { sessionManager.isLoggedIn() } returns true

        assertTrue(repository.isLoggedIn())

        every { sessionManager.isLoggedIn() } returns false

        assertFalse(repository.isLoggedIn())
    }

    @Test
    fun `logout limpia la sesion guardada`() {
        repository.logout()

        verify(exactly = 1) { sessionManager.clear() }
    }
}
