# -*- coding: utf-8 -*-
"""
aporte_manuscrito.py -- Calcula el aporte por integrante sobre una ruta del repositorio,
excluyendo dependencias y artefactos generados, que es el metodo que declara el epigrafe de
la Tabla "Aporte por area" del manuscrito.

El problema que resuelve: el epigrafe promete excluir dependencias y artefactos generados,
pero la tabla se calculo a mano sin excluirlos. Sobre docs/latex/, los archivos que produce
BibTeX (main.bbl, main.blg) aportan 318 de 6.008 lineas y desplazan los porcentajes
publicados. Este guion aplica el metodo declarado, de modo que el metodo declarado y el
metodo aplicado son el mismo y cualquiera puede reproducir la tabla.

Uso:
    python scripts/aporte_manuscrito.py docs/latex/
    python scripts/aporte_manuscrito.py experimentos/ spark/
    python scripts/aporte_manuscrito.py docs/latex/ --commit 964fa6e
"""
import argparse
import collections
import re
import subprocess
import sys

# Un mismo integrante aparece en el historial con varios nombres de autor; se mapea por correo.
INTEGRANTES = {
    "cristhianpachecoc03@gmail.com": "C. Pacheco",
    "carlospatroner@gmail.com": "C. Carpio",
    "rcandom@uteq.edu.ec": "R. Cando",
    "163648365+robimson@users.noreply.github.com": "R. Cando",
    "jalvarezp3@uteq.edu.ec": "J. Alvarez",
    "144397723+dejere@users.noreply.github.com": "J. Alvarez",
}

# Dependencias y artefactos generados: no los escribe una persona, los produce una herramienta.
EXCLUIR = re.compile(
    r"(^|/)(node_modules|build|dist|\.gradle|target)/"
    r"|\.(bbl|blg|aux|toc|out|lof|lot|lock|log)$"
    r"|(^|/)(package-lock\.json|yarn\.lock|gradlew|gradlew\.bat)$"
    r"|\.(jar|png|jpg|jpeg|pdf|apk|exec)$"
)


def lineas_por_integrante(rutas, commit):
    salida = subprocess.run(
        ["git", "log", "--numstat", "--pretty=format:@%ae", commit, "--", *rutas],
        capture_output=True, text=True, check=True,
    ).stdout
    total = collections.Counter()
    excluidas = collections.Counter()
    correo = None
    for linea in salida.splitlines():
        if linea.startswith("@"):
            correo = linea[1:].strip().lower()
            continue
        campos = linea.split("\t")
        if len(campos) != 3 or campos[0] == "-":
            continue
        insertadas, archivo = int(campos[0]), campos[2]
        persona = INTEGRANTES.get(correo, correo)
        if EXCLUIR.search(archivo):
            excluidas[persona] += insertadas
        else:
            total[persona] += insertadas
    return total, excluidas


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("rutas", nargs="+", help="una o mas rutas a medir, p.ej. docs/latex/")
    parser.add_argument("--commit", default="HEAD", help="commit sobre el que se mide")
    args = parser.parse_args()

    incluidas, excluidas = lineas_por_integrante(args.rutas, args.commit)
    base = sum(incluidas.values())
    if base == 0:
        print("Sin lineas insertadas en esa ruta.", file=sys.stderr)
        return 1

    descartadas = sum(excluidas.values())
    print(f"Rutas: {' '.join(args.rutas)}   commit: {args.commit}")
    print(f"Lineas contadas: {base}   descartadas por ser generadas o dependencias: {descartadas}")
    print()
    print(f"{'Integrante':<14}{'Lineas':>9}{'Aporte':>10}")
    for persona, n in sorted(incluidas.items(), key=lambda x: -x[1]):
        print(f"{persona:<14}{n:>9}{100 * n / base:>9.2f}%")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
