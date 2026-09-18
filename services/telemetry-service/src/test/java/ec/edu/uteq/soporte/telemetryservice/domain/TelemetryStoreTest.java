package ec.edu.uteq.soporte.telemetryservice.domain;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TelemetryStore no tenia ninguna prueba: es el buffer del que lee ZonaVentanaTelemetriaStrategy
 * (svc-principal, ver ZonaVentanaTelemetriaStrategyTest, Ronda 12) para decidir si hay evidencia
 * real de averia antes de agrupar tickets -- si este buffer devolviera eventos fuera de ventana,
 * en el orden equivocado, o filtrara por la zona equivocada, la correlacion c2 fallaria en
 * silencio, sin que ninguna prueba existente lo notara (las pruebas de c2 mockean
 * TelemetryQueryPort por completo, nunca ejercitan este buffer real).
 */
class TelemetryStoreTest {

    private final TelemetryStore store = new TelemetryStore();

    @Test
    void consultarSinEventosDevuelveListaVacia() {
        assertThat(store.consultar("QUEVEDO_NORTE", 900)).isEmpty();
    }

    @Test
    void consultarSoloDevuelveEventosDeLaZonaPedidaNoDeOtras() {
        store.registrar(evento("QUEVEDO_NORTE", 1L, ahora()));
        store.registrar(evento("QUEVEDO_SUR", 2L, ahora()));

        List<TelemetryEvent> resultado = store.consultar("QUEVEDO_NORTE", 900);

        assertThat(resultado).hasSize(1);
        assertThat(resultado.get(0).zone()).isEqualTo("QUEVEDO_NORTE");
    }

    @Test
    void consultarConVentanaPositivaExcluyeEventosMasViejosQueLaVentana() {
        long ahora = ahora();
        store.registrar(evento("QUEVEDO_NORTE", 1L, ahora - 20 * 60 * 1000)); // 20 min, fuera
        store.registrar(evento("QUEVEDO_NORTE", 2L, ahora - 5 * 60 * 1000));  // 5 min, dentro

        List<TelemetryEvent> resultado = store.consultar("QUEVEDO_NORTE", 900); // ventana 15 min

        assertThat(resultado).extracting(TelemetryEvent::lamportTimestamp).containsExactly(2L);
    }

    @Test
    void consultarConVentanaCeroOMenorDevuelveTodoLoQueElBufferConserva() {
        store.registrar(evento("QUEVEDO_NORTE", 1L, ahora() - 60 * 60 * 1000)); // 1h, "vieja"

        List<TelemetryEvent> resultado = store.consultar("QUEVEDO_NORTE", 0);

        assertThat(resultado).hasSize(1);
    }

    @Test
    void consultarOrdenaPorTimestampDeLamportNoPorOrdenDeLlegada() {
        // La garantia central que el comentario de la clase remarca: orden CAUSAL, no orden de
        // llegada. Se registran fuera de orden de Lamport a proposito para que una
        // implementacion que solo devolviera la lista tal cual (o la ordenara por
        // recibidoEnEpochMs) fallara esta prueba.
        long ahora = ahora();
        store.registrar(evento("QUEVEDO_NORTE", 30L, ahora));
        store.registrar(evento("QUEVEDO_NORTE", 10L, ahora));
        store.registrar(evento("QUEVEDO_NORTE", 20L, ahora));

        List<TelemetryEvent> resultado = store.consultar("QUEVEDO_NORTE", 900);

        assertThat(resultado).extracting(TelemetryEvent::lamportTimestamp).containsExactly(10L, 20L, 30L);
    }

    @Test
    void purgarEliminaEventosMasViejosQueLaRetencionEnTodasLasZonas() {
        long ahora = ahora();
        store.registrar(evento("QUEVEDO_NORTE", 1L, ahora - 20 * 60 * 1000)); // 20 min, vencido
        store.registrar(evento("QUEVEDO_SUR", 2L, ahora - 5 * 60 * 1000));    // 5 min, vigente

        store.purgar();

        assertThat(store.consultar("QUEVEDO_NORTE", 0)).isEmpty();
        assertThat(store.consultar("QUEVEDO_SUR", 0)).hasSize(1);
    }

    @Test
    void purgarNoEliminaEventosDentroDeLaRetencion() {
        store.registrar(evento("QUEVEDO_NORTE", 1L, ahora() - 60 * 1000)); // 1 min

        store.purgar();

        assertThat(store.consultar("QUEVEDO_NORTE", 0)).hasSize(1);
    }

    private long ahora() {
        return System.currentTimeMillis();
    }

    private TelemetryEvent evento(String zone, long lamportTimestamp, long recibidoEnEpochMs) {
        return new TelemetryEvent(
                TelemetryEvent.TipoOrigen.EQUIPO,
                "origen-" + lamportTimestamp,
                zone,
                lamportTimestamp,
                recibidoEnEpochMs,
                "{}"
        );
    }
}
