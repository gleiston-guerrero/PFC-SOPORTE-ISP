# Instantánea congelada — Entrega 4

Este directorio existe por el Entregable 26 de la Guía de Cierre ("Separación entre documento
vivo y documento congelado"): el PDF versionado en `docs/latex/main.pdf` **no** sirve como
instantánea, porque es la salida del documento vivo (`docs/latex/main.tex`) recompilada en
cada `push` a `main` — no lleva fecha ni commit de referencia propio, así que no hay forma de
saber a qué versión corresponde un PDF descargado en un momento dado.

## Qué es este archivo

[`manuscrito-entrega4-267d2fd-2026-09-18.pdf`](manuscrito-entrega4-267d2fd-2026-09-18.pdf) es una copia congelada del manuscrito,
generada por `scripts/congelar_manuscrito.sh` a partir del commit `267d2fd2ba425ae0acd437ee2e45a1e03c09467c`
(2026-09-18) — el commit de cierre de esta entrega. El propio PDF lleva, en un recuadro bajo el
título, el aviso "Instantánea congelada de la Entrega 4 — commit `267d2fd`, generada el
2026-09-18", así que la referencia de versión no depende solo del nombre del archivo o de este
README.

**Esta copia no se vuelve a recompilar ni se sobrescribe.** Si el commit de cierre cambia (por
ejemplo, por una corrección de último momento antes del corte), se regenera con
`scripts/congelar_manuscrito.sh <nuevo-commit>`, que reemplaza este archivo y este README con
la instantánea correcta — no coexisten dos congeladas distintas a propósito.

## Cómo verificarla

```bash
git show 267d2fd2ba425ae0acd437ee2e45a1e03c09467c:docs/latex/main.tex > /tmp/main_267d2fd.tex   # el fuente exacto de ese commit
sha256sum manuscrito-entrega4-267d2fd-2026-09-18.pdf
# Huella esperada:
# cf26721928e1ef4033caf907cb9ab72baeb86637b54709e586d9ab6ca9a89e85
```

Tamaño esperado: 2498077 bytes, 67 páginas.

El PDF se compiló con `scripts/congelar_manuscrito.sh` (2026-09-19T04:03:25Z), que reproduce los
mismos cuatro pasos (`pdflatex`, `bibtex`, `pdflatex` × 2) que documenta la raíz del
`README.md`, sobre el árbol de `docs/latex/` tal como estaba en `267d2fd2ba425ae0acd437ee2e45a1e03c09467c` (no sobre el
árbol de trabajo actual).
