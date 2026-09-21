# Instantánea congelada — Entrega 4

Este directorio existe por el Entregable 26 de la Guía de Cierre ("Separación entre documento
vivo y documento congelado"): el PDF versionado en `docs/latex/main.pdf` **no** sirve como
instantánea, porque es la salida del documento vivo (`docs/latex/main.tex`) recompilada en
cada `push` a `main` — no lleva fecha ni commit de referencia propio, así que no hay forma de
saber a qué versión corresponde un PDF descargado en un momento dado.

## Qué es este archivo

[`manuscrito-entrega4-abf7e7c-2026-09-21.pdf`](manuscrito-entrega4-abf7e7c-2026-09-21.pdf) es una copia congelada del manuscrito,
generada por `scripts/congelar_manuscrito.sh` a partir del commit `abf7e7c339f5d61afd87d75bdc68764b486b812a`
(2026-09-21) — el commit de cierre de esta entrega. El propio PDF lleva, en un recuadro bajo el
título, el aviso "Instantánea congelada de la Entrega 4 — commit `abf7e7c`, generada el
2026-09-21", así que la referencia de versión no depende solo del nombre del archivo o de este
README.

**Esta copia no se vuelve a recompilar ni se sobrescribe.** Si el commit de cierre cambia (por
ejemplo, por una corrección de último momento antes del corte), se regenera con
`scripts/congelar_manuscrito.sh <nuevo-commit>`, que reemplaza este archivo y este README con
la instantánea correcta — no coexisten dos congeladas distintas a propósito.

## Cómo verificarla

```bash
git show abf7e7c339f5d61afd87d75bdc68764b486b812a:docs/latex/main.tex > /tmp/main_abf7e7c.tex   # el fuente exacto de ese commit
sha256sum manuscrito-entrega4-abf7e7c-2026-09-21.pdf
# Huella esperada:
# c91b9882db2cd7323efef90ef3c0bd20aa2f013cb65a1b5205bc12559a595f4b
```

Tamaño esperado: 2498228 bytes, 67 páginas.

El PDF se compiló con `scripts/congelar_manuscrito.sh` (2026-09-21T21:21:42Z), que reproduce los
mismos cuatro pasos (`pdflatex`, `bibtex`, `pdflatex` × 2) que documenta la raíz del
`README.md`, sobre el árbol de `docs/latex/` tal como estaba en `abf7e7c339f5d61afd87d75bdc68764b486b812a` (no sobre el
árbol de trabajo actual).
