package ec.edu.uteq.soporte.mobile.ui.theme

import ec.edu.uteq.soporte.mobile.data.remote.dto.Category
import ec.edu.uteq.soporte.mobile.data.remote.dto.Priority
import ec.edu.uteq.soporte.mobile.data.remote.dto.TicketStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

/**
 * colorForStatus/labelForStatus/colorForPriority/labelForCategory no tenian ninguna prueba: son
 * mapeos exhaustivos por `when` (el compilador exige cubrir todos los casos, asi que nunca falta
 * una rama), pero el compilador no verifica que el VALOR de cada rama sea el correcto -- un
 * copy-paste que le asigne a dos estados distintos el mismo color, o un acento mal escrito en una
 * etiqueta, pasaria el build sin que nada lo note.
 */
class StatusColorsTest {

    @Test
    fun `cada estado de ticket tiene un color distinto de los demas`() {
        val colores = TicketStatus.entries.map { colorForStatus(it) }

        assertEquals(colores.size, colores.distinct().size)
    }

    @Test
    fun `escalado se pinta de rojo, el color de mayor alarma`() {
        assertEquals(StatusEscalado, colorForStatus(TicketStatus.ESCALADO))
    }

    @Test
    fun `resuelto y cerrado no comparten color aunque ambos sean estados finales`() {
        assertNotEquals(colorForStatus(TicketStatus.RESUELTO), colorForStatus(TicketStatus.CERRADO))
    }

    @Test
    fun `las etiquetas de estado llevan tildes y capitalizacion correctas`() {
        assertEquals("En progreso", labelForStatus(TicketStatus.EN_PROGRESO))
        assertEquals("Nuevo", labelForStatus(TicketStatus.NUEVO))
    }

    @Test
    fun `critico usa el mismo color de alarma que escalado, a proposito`() {
        // Segun el comentario de la clase: prioridad reusa el mismo tono de acento que estado,
        // distinguiendose por forma (contorno) en vez de por un color nuevo -- si esto se
        // rompiera silenciosamente, criticos y no-escalados podrian verse identicos.
        assertEquals(StatusEscalado, colorForPriority(Priority.CRITICO))
    }

    @Test
    fun `cada prioridad tiene un color distinto de las demas`() {
        val colores = Priority.entries.map { colorForPriority(it) }

        assertEquals(colores.size, colores.distinct().size)
    }

    @Test
    fun `las etiquetas de prioridad llevan tilde donde corresponde`() {
        assertEquals("Crítico", labelForPriority(Priority.CRITICO))
    }

    @Test
    fun `cada categoria tiene una etiqueta en espanol distinta de las demas`() {
        val etiquetas = Category.entries.map { labelForCategory(it) }

        assertEquals(etiquetas.size, etiquetas.distinct().size)
        assertEquals("Configuración", labelForCategory(Category.CONFIGURACION))
    }
}
