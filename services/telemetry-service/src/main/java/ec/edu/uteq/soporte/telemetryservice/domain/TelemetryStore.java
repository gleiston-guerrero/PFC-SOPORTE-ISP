package ec.edu.uteq.soporte.telemetryservice.domain;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Buffer en memoria de eventos de telemetria, acotado por zona con ventana deslizante (PE-U1,
 * equipo ACC). No es persistencia de negocio -- es un canal: igual que CORREL describe sus
 * modos c1/c2 como ventana deslizante y no como historial completo, este buffer solo conserva
 * lo reciente y descarta lo viejo.
 *
 * Java puro, sin @Component (Entregable 1 de la guia de cierre): Spring la registra como bean
 * en infrastructure/config/DomainBeansConfig.java, el unico punto de infrastructure/ que la
 * conoce por su tipo concreto, igual que svc-principal hizo con sus 9 clases de domain/.
 */
public class TelemetryStore {

    /** Cuanto se conserva un evento antes de poder ser descartado por {@link #purgar}. */
    private static final long RETENCION_MS = 15 * 60 * 1000; // 15 minutos

    /** Tope duro por zona, por si una zona recibe trafico desproporcionado. */
    private static final int MAX_EVENTOS_POR_ZONA = 5_000;

    private final Map<String, List<TelemetryEvent>> porZona = new ConcurrentHashMap<>();

    public void registrar(TelemetryEvent evento) {
        List<TelemetryEvent> lista = porZona.computeIfAbsent(evento.zone(), z -> new CopyOnWriteArrayList<>());
        lista.add(evento);
        if (lista.size() > MAX_EVENTOS_POR_ZONA) {
            lista.remove(0);
        }
    }

    /**
     * Eventos de una zona dentro de la ventana pedida, ordenados por timestamp de Lamport
     * ascendente (orden causal, no orden de llegada). {@code ventanaSegundos <= 0} significa
     * "todo lo que el buffer todavia conserve para esa zona".
     */
    public List<TelemetryEvent> consultar(String zone, long ventanaSegundos) {
        List<TelemetryEvent> lista = porZona.getOrDefault(zone, List.of());
        long limiteEpochMs = ventanaSegundos > 0
                ? System.currentTimeMillis() - (ventanaSegundos * 1000)
                : Long.MIN_VALUE;

        List<TelemetryEvent> resultado = new ArrayList<>();
        for (TelemetryEvent evento : lista) {
            if (evento.recibidoEnEpochMs() >= limiteEpochMs) {
                resultado.add(evento);
            }
        }
        resultado.sort(Comparator.comparingLong(TelemetryEvent::lamportTimestamp));
        return resultado;
    }

    /** Purga eventos mas viejos que {@link #RETENCION_MS} de todas las zonas. */
    public void purgar() {
        long limite = System.currentTimeMillis() - RETENCION_MS;
        for (List<TelemetryEvent> lista : porZona.values()) {
            lista.removeIf(e -> e.recibidoEnEpochMs() < limite);
        }
    }
}
