"""Analisis de las 5 repeticiones de cada escenario de carga (Modulo D / evaluacion ISO 25010,
Entrega 4, escala reducida por restriccion de tiempo -- ver
docs/experimentos/evaluacion_iso25010.md).

Metodologia: se descartan la primera y la ultima repeticion (calentamiento/enfriamiento) y se
calculan media +/- intervalo de confianza del 95% (distribucion t, n=3, df=2) sobre las 3
repeticiones intermedias, para cada metrica: p50, p95, p99, throughput (req/s) y tasa de error.
No se inventa ningun numero: todo sale de los CSV que genera Locust en resultados/locust/.
"""
import csv
import statistics as st

T_95_DF2 = 4.303  # valor critico t de Student para 95% de confianza con 2 grados de libertad


def leer_agregado(path):
    with open(path, newline="", encoding="utf-8") as f:
        for row in csv.DictReader(f):
            if row["Name"] == "Aggregated":
                return {
                    "requests": int(row["Request Count"]),
                    "failures": int(row["Failure Count"]),
                    "p50": float(row["50%"]),
                    "p95": float(row["95%"]),
                    "p99": float(row["99%"]),
                    "rps": float(row["Requests/s"]),
                }
    raise ValueError(f"No se encontro la fila Aggregated en {path}")


def resumen(valores):
    media = st.mean(valores)
    if len(valores) > 1:
        margen = T_95_DF2 * (st.stdev(valores) / (len(valores) ** 0.5))
    else:
        margen = 0.0
    return media, margen


def analizar_escenario(nombre, n_runs=5):
    corridas = [leer_agregado(f"resultados/locust/{nombre}_run{i}_stats.csv") for i in range(1, n_runs + 1)]
    # Se descartan la 1a y la ultima (calentamiento/enfriamiento), se usan las 3 intermedias
    intermedias = corridas[1:-1]

    print(f"\n=== {nombre} ({n_runs} repeticiones, se usan las intermedias {len(intermedias)}) ===")
    print(f"{'metrica':<12}{'run1':>8}{'run2':>8}{'run3':>8}{'run4':>8}{'run5':>8}   media(IC95%)")
    for metrica, etiqueta in [("p50", "p50 (ms)"), ("p95", "p95 (ms)"), ("p99", "p99 (ms)"), ("rps", "throughput")]:
        valores_todas = [c[metrica] for c in corridas]
        valores_medias = [c[metrica] for c in intermedias]
        media, margen = resumen(valores_medias)
        fila = "".join(f"{v:>8.1f}" for v in valores_todas)
        print(f"{etiqueta:<12}{fila}   {media:.1f} +/- {margen:.1f}")

    tasas_error = [100 * c["failures"] / c["requests"] if c["requests"] else 0 for c in corridas]
    tasas_error_medias = tasas_error[1:-1]
    media_err, margen_err = resumen(tasas_error_medias)
    fila = "".join(f"{v:>8.1f}" for v in tasas_error)
    print(f"{'error %':<12}{fila}   {media_err:.1f} +/- {margen_err:.1f}")

    total_reqs = sum(c["requests"] for c in corridas)
    total_fail = sum(c["failures"] for c in corridas)
    print(f"Total combinado (5 repeticiones): {total_reqs} peticiones, {total_fail} fallidas "
          f"({100 * total_fail / total_reqs:.1f}%)")


if __name__ == "__main__":
    analizar_escenario("escenarioA")
    analizar_escenario("escenarioB")

    # Ultimo paso, a proposito: las sumas se regeneran despues de analizar, para que nunca
    # queden desfasadas respecto a los CSV que Locust acaba de producir.
    import sys
    from pathlib import Path
    sys.path.insert(0, str(Path(__file__).parent.parent.parent / "experimentos"))
    import generar_checksums
    generar_checksums.generar_para(Path(__file__).parent)
