# Instantánea congelada — Entrega 4

Este directorio existe por el Entregable 26 de la Guía de Cierre ("Separación entre documento
vivo y documento congelado"): el PDF versionado en `docs/latex/main.pdf` **no** sirve como
instantánea, porque es la salida del documento vivo (`docs/latex/main.tex`) recompilada en
cada `push` a `main` — no lleva fecha ni commit de referencia propio, así que no hay forma de
saber a qué versión corresponde un PDF descargado en un momento dado.

## Qué es este archivo

[`manuscrito-entrega4-b875a98-2026-09-18.pdf`](manuscrito-entrega4-b875a98-2026-09-18.pdf) es una copia congelada del manuscrito,
generada por `scripts/congelar_manuscrito.sh` a partir del commit `b875a982f679f5e8cf72bde8915b4d5273889603`
(2026-09-18) — el commit de cierre de esta entrega. El propio PDF lleva, en un recuadro bajo el
título, el aviso "Instantánea congelada de la Entrega 4 — commit `b875a98`, generada el
2026-09-18", así que la referencia de versión no depende solo del nombre del archivo o de este
README.

**Esta copia no se vuelve a recompilar ni se sobrescribe.** Si el commit de cierre cambia (por
ejemplo, por una corrección de último momento antes del corte), se regenera con
`scripts/congelar_manuscrito.sh <nuevo-commit>`, que reemplaza este archivo y este README con
la instantánea correcta — no coexisten dos congeladas distintas a propósito.

## Cómo verificarla

```bash
git show b875a982f679f5e8cf72bde8915b4d5273889603:docs/latex/main.tex > /tmp/main_b875a98.tex   # el fuente exacto de ese commit
sha256sum manuscrito-entrega4-b875a98-2026-09-18.pdf
# Huella esperada:
# 3dbd593ae222ddf21c4333b1ba84f2a5172234307840972129e8dc3cd27d18bc
```

Tamaño esperado: 2498501 bytes, 67 páginas.

El PDF se compiló con `scripts/congelar_manuscrito.sh` (2026-09-19T04:17:25Z), que reproduce los
mismos cuatro pasos (`pdflatex`, `bibtex`, `pdflatex` × 2) que documenta la raíz del
`README.md`, sobre el árbol de `docs/latex/` tal como estaba en `b875a982f679f5e8cf72bde8915b4d5273889603` (no sobre el
árbol de trabajo actual).
