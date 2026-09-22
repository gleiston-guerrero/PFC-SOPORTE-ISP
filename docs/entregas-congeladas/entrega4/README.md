# Instantánea congelada — Entrega 4

Este directorio existe por el Entregable 26 de la Guía de Cierre ("Separación entre documento
vivo y documento congelado"): el PDF versionado en `docs/latex/main.pdf` **no** sirve como
instantánea, porque es la salida del documento vivo (`docs/latex/main.tex`) recompilada en
cada `push` a `main` — no lleva fecha ni commit de referencia propio, así que no hay forma de
saber a qué versión corresponde un PDF descargado en un momento dado.

## Qué es este archivo

[`manuscrito-entrega4-fd62294-2026-09-22.pdf`](manuscrito-entrega4-fd62294-2026-09-22.pdf) es una copia congelada del manuscrito,
generada por `scripts/congelar_manuscrito.sh` a partir del commit `fd622946afb7a98b59a9d910b4bd6b972f7a1ec2`
(2026-09-22) — el commit de cierre de esta entrega. El propio PDF lleva, en un recuadro bajo el
título, el aviso "Instantánea congelada de la Entrega 4 — commit `fd62294`, generada el
2026-09-22", así que la referencia de versión no depende solo del nombre del archivo o de este
README.

**Esta copia no se vuelve a recompilar ni se sobrescribe.** Si el commit de cierre cambia (por
ejemplo, por una corrección de último momento antes del corte), se regenera con
`scripts/congelar_manuscrito.sh <nuevo-commit>`, que reemplaza este archivo y este README con
la instantánea correcta — no coexisten dos congeladas distintas a propósito.

## Cómo verificarla

```bash
git show fd622946afb7a98b59a9d910b4bd6b972f7a1ec2:docs/latex/main.tex > /tmp/main_fd62294.tex   # el fuente exacto de ese commit
sha256sum manuscrito-entrega4-fd62294-2026-09-22.pdf
# Huella esperada:
# e9c9222280a512b7d135e75bf304f3d2ecfa479722d8a53294a6fad557719029
```

Tamaño esperado: 2869224 bytes, 70 páginas.

El PDF se compiló con `scripts/congelar_manuscrito.sh` (2026-09-22T22:19:46Z), que corre
`latexmk -pdf -interaction=nonstopmode -halt-on-error main.tex` (no los cuatro pasos sueltos
de `pdflatex`/`bibtex`/`pdflatex` × 2 que documenta la raíz del `README.md`: `latexmk`
decide cuántas pasadas hacen falta y evita que un `pdflatex` de MiKTeX recién instalado aborte
el guion con `set -e` solo por el aviso de "check for updates"), sobre el árbol de
`docs/latex/` tal como estaba en `fd622946afb7a98b59a9d910b4bd6b972f7a1ec2` (no sobre el árbol de trabajo actual).
