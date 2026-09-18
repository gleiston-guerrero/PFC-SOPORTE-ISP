package ec.edu.uteq.soporte.mobile.data.repository

import android.util.Base64
import ec.edu.uteq.soporte.mobile.data.local.TicketDao
import ec.edu.uteq.soporte.mobile.data.local.TicketEntity
import ec.edu.uteq.soporte.mobile.data.remote.TicketApi
import ec.edu.uteq.soporte.mobile.data.remote.dto.ApiResponse
import ec.edu.uteq.soporte.mobile.data.remote.dto.TicketResponse
import ec.edu.uteq.soporte.mobile.data.remote.dto.TicketStatus
import ec.edu.uteq.soporte.mobile.data.remote.dto.Zone
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.IOException

/**
 * TicketRepository no tenia ninguna prueba directa (solo se ejercitaba indirectamente a
 * traves de TicketDetailViewModelTest, que mockea el repositorio entero y nunca corre su
 * logica real). Es la pieza con mas logica de negocio propia de todo apps/mobile: el
 * reintento de closeOnSite (solo ante fallo de RED, nunca ante un 4xx/5xx real) y el patron
 * cache-primero de getTicket -- exactamente el tipo de codigo que se rompe en silencio si
 * alguien invierte una condicion sin que ninguna prueba lo note.
 *
 * android.util.Base64 no esta disponible en pruebas JVM puras (el stub de Android lanza
 * "not mocked" en cuanto se invoca, ver el comentario de TicketDetailViewModelTest, que evito
 * el problema mockeando TicketRepository entero). Aqui se prueba el repositorio real, asi que
 * Base64 se mockea estaticamente en su lugar.
 */
class TicketRepositoryTest {

    private val ticketApi: TicketApi = mockk()
    private val ticketDao: TicketDao = mockk()

    @BeforeEach
    fun mockearBase64() {
        mockkStatic(Base64::class)
        every { Base64.encodeToString(any(), any()) } returns "Zm90by1kZS1wcnVlYmE="
    }

    @AfterEach
    fun limpiarMockEstatico() {
        unmockkStatic(Base64::class)
    }
    private val repository = TicketRepository(ticketApi, ticketDao)

    @Test
    fun `getTicket prefiere el dato fresco del backend y actualiza la cache`() = runTest {
        val fresco = ticket(status = TicketStatus.EN_PROGRESO)
        coEvery { ticketApi.getTicket("t1") } returns ApiResponse(fresco, null, null)
        coEvery { ticketDao.upsert(any()) } returns Unit

        val resultado = repository.getTicket("t1")

        assertEquals(fresco, resultado)
        coVerify { ticketDao.upsert(match { it.ticketId == "t1" && it.status == "EN_PROGRESO" }) }
    }

    @Test
    fun `getTicket sin red cae a la cache local en vez de fallar`() = runTest {
        coEvery { ticketApi.getTicket("t1") } throws IOException("sin conexion")
        coEvery { ticketDao.findById("t1") } returns entidad(status = TicketStatus.ASIGNADO)

        val resultado = repository.getTicket("t1")

        assertEquals(TicketStatus.ASIGNADO, resultado?.status)
    }

    @Test
    fun `getTicket sin red y sin nada en cache devuelve null en vez de lanzar`() = runTest {
        coEvery { ticketApi.getTicket("t1") } throws IOException("sin conexion")
        coEvery { ticketDao.findById("t1") } returns null

        assertNull(repository.getTicket("t1"))
    }

    @Test
    fun `refreshTickets guarda todos los tickets recibidos en la cache`() = runTest {
        coEvery { ticketApi.listTickets(zone = null, status = null) } returns
            ApiResponse(listOf(ticket("t1"), ticket("t2")), null, null)
        coEvery { ticketDao.upsertAll(any()) } returns Unit

        val resultado = repository.refreshTickets()

        assertTrue(resultado.isSuccess)
        coVerify { ticketDao.upsertAll(match { it.size == 2 }) }
    }

    @Test
    fun `closeOnSite reintenta ante un fallo de red y termina en exito`() = runTest {
        val actualizado = ticket("t1", status = TicketStatus.RESUELTO)
        coEvery { ticketApi.updateStatus(eq("t1"), any()) } throws IOException("timeout") andThenThrows
            IOException("timeout otra vez") andThen ApiResponse(actualizado, null, null)
        coEvery { ticketDao.upsert(any()) } returns Unit

        val resultado = repository.closeOnSite("t1", byteArrayOf(1, 2, 3), -1.02, -79.46)

        assertTrue(resultado.isSuccess)
        assertEquals(TicketStatus.RESUELTO, resultado.getOrNull()?.status)
        coVerify(exactly = 3) { ticketApi.updateStatus(eq("t1"), any()) }
    }

    @Test
    fun `closeOnSite agota los 3 intentos y falla si la red nunca vuelve`() = runTest {
        coEvery { ticketApi.updateStatus(eq("t1"), any()) } throws IOException("sin conexion")

        val resultado = repository.closeOnSite("t1", byteArrayOf(1), -1.0, -79.0)

        assertTrue(resultado.isFailure)
        assertTrue(resultado.exceptionOrNull() is IOException)
        coVerify(exactly = 3) { ticketApi.updateStatus(eq("t1"), any()) }
    }

    @Test
    fun `closeOnSite NO reintenta ante un error HTTP real, solo ante fallo de red`() = runTest {
        // El comentario de produccion es explicito: un 4xx/5xx indica un problema real de
        // servidor/autorizacion que reintentar de inmediato no va a resolver. Si esta prueba
        // fallara, significaria que alguien amplio el catch a Exception en vez de IOException,
        // reintentando ante cualquier error -- incluido enviar dos veces un cierre invalido.
        coEvery { ticketApi.updateStatus(eq("t1"), any()) } throws
            retrofit2.HttpException(retrofit2.Response.error<Any>(403, "".toResponseBody(null)))

        val resultado = repository.closeOnSite("t1", byteArrayOf(1), -1.0, -79.0)

        assertTrue(resultado.isFailure)
        coVerify(exactly = 1) { ticketApi.updateStatus(eq("t1"), any()) }
    }

    @Test
    fun `closeOnSite codifica la foto en Base64 y manda RESUELTO con las coordenadas`() = runTest {
        coEvery {
            ticketApi.updateStatus(eq("t1"), match { req ->
                req.status == TicketStatus.RESUELTO &&
                    req.latitude == -1.02 &&
                    req.longitude == -79.46 &&
                    !req.evidencePhotoBase64.isNullOrBlank()
            })
        } returns ApiResponse(ticket("t1", status = TicketStatus.RESUELTO), null, null)
        coEvery { ticketDao.upsert(any()) } returns Unit

        val resultado = repository.closeOnSite("t1", byteArrayOf(1, 2, 3), -1.02, -79.46)

        assertTrue(resultado.isSuccess)
        coVerify(exactly = 1) { ticketApi.updateStatus(eq("t1"), any()) }
    }

    @Test
    fun `observeTickets mapea las entidades de la cache al DTO de red`() = runTest {
        coEvery { ticketDao.observeAll() } returns flowOf(listOf(entidad("t1")))

        val resultado = repository.observeTickets()

        resultado.collect { lista ->
            assertEquals(1, lista.size)
            assertEquals("t1", lista[0].ticketId)
        }
    }

    private fun ticket(id: String = "t1", status: TicketStatus = TicketStatus.NUEVO) = TicketResponse(
        zone = Zone.QUEVEDO_NORTE,
        ticketId = id,
        clientId = "cliente-1",
        technicianId = null,
        category = null,
        priority = null,
        status = status,
        description = "Sin senal",
        createdAt = "2026-09-18T00:00:00Z",
        slaDeadline = null,
        slaBreached = false,
    )

    private fun entidad(id: String = "t1", status: TicketStatus = TicketStatus.NUEVO) =
        TicketEntity.fromResponse(ticket(id, status), 0L)
}
