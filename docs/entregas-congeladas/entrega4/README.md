# Instantánea congelada — Entrega 4

Este directorio existe por el Entregable 26 de la Guía de Cierre ("Separación entre documento
vivo y documento congelado"): el PDF versionado en `docs/latex/main.pdf` **no** sirve como
instantánea, porque es la salida del documento vivo (`docs/latex/main.tex`) recompilada en
cada `push` a `main` — no lleva fecha ni commit de referencia propio, así que no hay forma de
saber a qué versión corresponde un PDF descargado en un momento dado.

## Qué es este archivo

[`manuscrito-entrega4-fe00891-2026-09-22.pdf`](manuscrito-entrega4-fe00891-2026-09-22.pdf) es una copia congelada del manuscrito,
generada por `scripts/congelar_manuscrito.sh` a partir del commit `fe00891625f9f31e0ebd1bde97d70ab709dcf5e6`
(2026-09-22) — el commit de cierre de esta entrega. El propio PDF lleva, en un recuadro bajo el
título, el aviso "Instantánea congelada de la Entrega 4 — commit `fe00891`, generada el
2026-09-22", así que la referencia de versión no depende solo del nombre del archivo o de este
README.

**Esta copia no se vuelve a recompilar ni se sobrescribe.** Si el commit de cierre cambia (por
ejemplo, por una corrección de último momento antes del corte), se regenera con
`scripts/congelar_manuscrito.sh <nuevo-commit>`, que reemplaza este archivo y este README con
la instantánea correcta — no coexisten dos congeladas distintas a propósito.

## Cómo verificarla

```bash
git show fe00891625f9f31e0ebd1bde97d70ab709dcf5e6:docs/latex/main.tex > /tmp/main_fe00891.tex   # el fuente exacto de ese commit
sha256sum manuscrito-entrega4-fe00891-2026-09-22.pdf
# Huella esperada:
# c51d4ca7f66a0ab602400d67e2f11ee2c8e8419103519310159decc4535a26dd
```

Tamaño esperado: 2515611 bytes, 69 páginas.

El PDF se compiló con `scripts/congelar_manuscrito.sh` (2026-09-22T17:38:10Z), que reproduce los
mismos cuatro pasos (`pdflatex`, `bibtex`, `pdflatex` × 2) que documenta la raíz del
`README.md`, sobre el árbol de `docs/latex/` tal como estaba en `fe00891625f9f31e0ebd1bde97d70ab709dcf5e6` (no sobre el
árbol de trabajo actual).
