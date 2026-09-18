package ec.edu.uteq.soporte.mobile.ui.tickets.list

import app.cash.turbine.test
import ec.edu.uteq.soporte.mobile.data.remote.dto.TicketResponse
import ec.edu.uteq.soporte.mobile.data.remote.dto.TicketStatus
import ec.edu.uteq.soporte.mobile.data.remote.dto.Zone
import ec.edu.uteq.soporte.mobile.data.repository.TicketRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * TicketListViewModel no tenia ninguna prueba. Es el unico ViewModel que combina dos filtros
 * independientes sobre el mismo flujo (busqueda por texto + estado), y tiene una distincion
 * sutil que una prueba puede proteger y una lectura del codigo no: totalCount y
 * slaBreachedCount se calculan sobre TODOS los tickets, no sobre los ya filtrados -- si alguien
 * cambiara "tickets.size" por "filtered.size" por error, el resumen mentiria en cuanto se
 * aplicara cualquier filtro, y ninguna prueba existente lo hubiera detectado.
 *
 * uiState se expone via combine(...).stateIn(scope, WhileSubscribed(5_000), inicial) -- a
 * diferencia de TicketDetailViewModel (un MutableStateFlow simple), este necesita un
 * colector activo para que el combine realmente corra, por eso cada prueba se suscribe con
 * Turbine en vez de leer uiState.value directamente.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TicketListViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var ticketRepository: TicketRepository

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        ticketRepository = mockk()
        coEvery { ticketRepository.refreshTickets() } returns Result.success(Unit)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `sin filtros muestra todos los tickets`() = runTest {
        coEvery { ticketRepository.observeTickets() } returns flowOf(
            listOf(
                ticket(descripcion = "Sin senal", status = TicketStatus.NUEVO),
                ticket(descripcion = "Router lento", status = TicketStatus.RESUELTO),
            )
        )
        val viewModel = TicketListViewModel(ticketRepository)

        viewModel.uiState.test {
            awaitItem() // estado inicial por defecto, antes de que corra el combine
            dispatcher.scheduler.advanceUntilIdle()

            assertEquals(2, expectMostRecentItem().tickets.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `el conteo total y de incumplimientos de SLA se calculan sobre todos los tickets, no sobre los filtrados`() = runTest {
        coEvery { ticketRepository.observeTickets() } returns flowOf(
            listOf(
                ticket(descripcion = "Sin senal", status = TicketStatus.NUEVO, slaBreached = true),
                ticket(descripcion = "Router lento", status = TicketStatus.RESUELTO, slaBreached = false),
                ticket(descripcion = "Camara caida", status = TicketStatus.RESUELTO, slaBreached = true),
            )
        )
        val viewModel = TicketListViewModel(ticketRepository)

        viewModel.uiState.test {
            awaitItem()
            dispatcher.scheduler.advanceUntilIdle()

            viewModel.onStatusFilterSelected(TicketStatus.RESUELTO)
            dispatcher.scheduler.advanceUntilIdle()

            val estado = expectMostRecentItem()
            assertEquals(2, estado.tickets.size, "el listado si debe reflejar el filtro")
            assertEquals(3, estado.totalCount, "el total es sobre todos los tickets")
            assertEquals(2, estado.slaBreachedCount, "el conteo de SLA es sobre todos los tickets")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `la busqueda de texto no distingue mayusculas de minusculas`() = runTest {
        coEvery { ticketRepository.observeTickets() } returns flowOf(
            listOf(
                ticket(descripcion = "Router SIN SENAL de internet", status = TicketStatus.NUEVO),
                ticket(descripcion = "Camara con imagen borrosa", status = TicketStatus.NUEVO),
            )
        )
        val viewModel = TicketListViewModel(ticketRepository)

        viewModel.uiState.test {
            awaitItem()
            dispatcher.scheduler.advanceUntilIdle()

            viewModel.onSearchQueryChanged("sin senal")
            dispatcher.scheduler.advanceUntilIdle()

            assertEquals(1, expectMostRecentItem().tickets.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `la busqueda y el filtro de estado se combinan con AND`() = runTest {
        coEvery { ticketRepository.observeTickets() } returns flowOf(
            listOf(
                ticket(descripcion = "Sin senal de internet", status = TicketStatus.NUEVO),
                ticket(descripcion = "Sin senal de internet", status = TicketStatus.RESUELTO),
                ticket(descripcion = "Router lento", status = TicketStatus.NUEVO),
            )
        )
        val viewModel = TicketListViewModel(ticketRepository)

        viewModel.uiState.test {
            awaitItem()
            dispatcher.scheduler.advanceUntilIdle()

            viewModel.onSearchQueryChanged("senal")
            viewModel.onStatusFilterSelected(TicketStatus.NUEVO)
            dispatcher.scheduler.advanceUntilIdle()

            assertEquals(1, expectMostRecentItem().tickets.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `tocar el mismo filtro de estado otra vez lo quita`() = runTest {
        coEvery { ticketRepository.observeTickets() } returns flowOf(
            listOf(
                ticket(descripcion = "Sin senal", status = TicketStatus.NUEVO),
                ticket(descripcion = "Router lento", status = TicketStatus.RESUELTO),
            )
        )
        val viewModel = TicketListViewModel(ticketRepository)

        viewModel.uiState.test {
            awaitItem()
            dispatcher.scheduler.advanceUntilIdle()

            viewModel.onStatusFilterSelected(TicketStatus.NUEVO)
            dispatcher.scheduler.advanceUntilIdle()
            assertEquals(TicketStatus.NUEVO, expectMostRecentItem().statusFilter)

            viewModel.onStatusFilterSelected(TicketStatus.NUEVO)
            dispatcher.scheduler.advanceUntilIdle()

            val estado = expectMostRecentItem()
            assertNull(estado.statusFilter)
            assertEquals(2, estado.tickets.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `si refreshTickets falla por falta de red conserva los tickets de la cache y muestra un mensaje`() = runTest {
        coEvery { ticketRepository.observeTickets() } returns flowOf(
            listOf(ticket(descripcion = "Sin senal", status = TicketStatus.NUEVO))
        )
        coEvery { ticketRepository.refreshTickets() } returns Result.failure(java.io.IOException("sin conexion"))
        val viewModel = TicketListViewModel(ticketRepository)

        viewModel.uiState.test {
            awaitItem()
            dispatcher.scheduler.advanceUntilIdle()

            val estado = expectMostRecentItem()
            assertEquals(1, estado.tickets.size)
            assertEquals("Sin conexión — mostrando datos guardados", estado.errorMessage)
            assertEquals(false, estado.isRefreshing)
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun ticket(
        descripcion: String,
        status: TicketStatus,
        slaBreached: Boolean = false,
    ) = TicketResponse(
        zone = Zone.QUEVEDO_NORTE,
        ticketId = java.util.UUID.randomUUID().toString(),
        clientId = java.util.UUID.randomUUID().toString(),
        technicianId = null,
        category = null,
        priority = null,
        status = status,
        description = descripcion,
        createdAt = "2026-09-17T00:00:00Z",
        slaDeadline = null,
        slaBreached = slaBreached,
    )
}
