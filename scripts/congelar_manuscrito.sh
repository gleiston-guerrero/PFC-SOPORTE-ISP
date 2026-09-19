#!/usr/bin/env bash
# Entregable 26 de la guia de cierre ("Separacion entre documento vivo y documento congelado").
#
# Congela el manuscrito en un commit exacto: compila docs/latex/main.tex tal como estaba en ese
# commit, con el aviso de \avisoversion reemplazado por un mensaje que cita el commit y la fecha
# de esta instantanea (en vez del mensaje generico del documento vivo), y deja el PDF resultante
# mas un README con su huella SHA-256 completa (no truncada) bajo
# docs/entregas-congeladas/entrega4/.
#
# No modifica el arbol de trabajo actual ni el historial: trabaja sobre una copia temporal de
# docs/latex/ tomada con "git show <commit>:<ruta>", y no vuelve a comprometer nada por su
# cuenta -- el llamador revisa el resultado y hace el commit.
#
# Uso: scripts/congelar_manuscrito.sh [commit]   (por defecto, HEAD)
set -euo pipefail

COMMIT="${1:-HEAD}"
COMMIT_HASH="$(git rev-parse "$COMMIT")"
COMMIT_SHORT="$(git rev-parse --short "$COMMIT")"
COMMIT_DATE="$(git show -s --format=%cd --date=format:%Y-%m-%d "$COMMIT")"
FREEZE_TIMESTAMP="$(date -u +%Y-%m-%dT%H:%M:%SZ)"

REPO_ROOT="$(git rev-parse --show-toplevel)"
OUT_DIR="$REPO_ROOT/docs/entregas-congeladas/entrega4"
TMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TMP_DIR"' EXIT

echo "Congelando docs/latex/ en el commit $COMMIT_HASH ($COMMIT_DATE)..."

# Copia el arbol docs/latex/ tal como estaba en ese commit exacto (no el arbol de trabajo
# actual), mas docs/diagrams/ y release/screenshots/ -- las figuras de aplicacion_web.tex y
# aplicacion_movil.tex las referencian con ruta relativa ../../release/screenshots/*.png, fuera
# de docs/, y sin ellas pdflatex aborta con "File ... not found: using draft setting" (el mismo
# problema que el entregable #42 documenta para la variante Docker del README que monta solo
# docs/). git archive conserva la ruta completa, asi que el fuente queda en
# "$TMP_DIR/docs/latex/", no en "$TMP_DIR/latex/".
git archive "$COMMIT_HASH" docs/latex docs/diagrams release/screenshots | tar -x -C "$TMP_DIR"
LATEX_DIR="$TMP_DIR/docs/latex"

AVISO_CONGELADO="Instantánea congelada de la Entrega 4 — commit \\\\texttt{$COMMIT_SHORT}, generada el $COMMIT_DATE. Este PDF no se vuelve a recompilar ni a sobrescribir. Para la versión viva y más reciente, vea \\\\texttt{docs/latex/main.tex} en la rama \\\\texttt{main}; para verificar esta copia, vea \\\\texttt{docs/entregas-congeladas/entrega4/README.md}."

python3 - "$LATEX_DIR/main.tex" "$AVISO_CONGELADO" <<'PYEOF'
import sys
path, aviso = sys.argv[1], sys.argv[2]
with open(path, encoding="utf-8") as f:
    content = f.read()
marker = "\\begin{document}"
before, sep, after = content.partition(marker)
if not sep:
    sys.exit("No se encontro \\begin{document} en main.tex de ese commit")
needle = "\\newcommand{\\avisoversion}{"
start = before.rfind(needle)
if start == -1:
    sys.exit("No se encontro \\avisoversion en main.tex de ese commit -- ¿el commit es anterior al Entregable 26?")
# El propio \newcommand{\avisoversion}{...} es la ULTIMA definicion antes de \begin{document}
# (asi se escribio en main.tex): todo desde su "\newcommand" hasta el final de "before" es su
# cuerpo, sin necesidad de balancear llaves anidadas a mano.
new_before = before[:start] + "\\newcommand{\\avisoversion}{" + aviso + "}\n\n"
new_content = new_before + marker + after
if new_content == content:
    sys.exit("La sustitucion no cambio nada -- revisar main.tex de ese commit")
with open(path, "w", encoding="utf-8") as f:
    f.write(new_content)
PYEOF

pushd "$LATEX_DIR" > /dev/null
# latexmk, no pdflatex/bibtex sueltos: pdflatex de MiKTeX devuelve exit 1 en la primera
# ejecucion de una maquina nueva solo por el aviso "check for MiKTeX updates" (no es un error
# de LaTeX), lo que mataria el script bajo "set -e". latexmk no tiene ese problema.
latexmk -pdf -interaction=nonstopmode -halt-on-error main.tex > latexmk.log 2>&1
popd > /dev/null

PAGES="$(pdfinfo "$LATEX_DIR/main.pdf" | grep -i '^Pages:' | awk '{print $2}')"
OUT_PDF="$OUT_DIR/manuscrito-entrega4-${COMMIT_SHORT}-${COMMIT_DATE}.pdf"
cp "$LATEX_DIR/main.pdf" "$OUT_PDF"
SHA256="$(sha256sum "$OUT_PDF" | awk '{print $1}')"
SIZE_BYTES="$(stat -c%s "$OUT_PDF" 2>/dev/null || stat -f%z "$OUT_PDF")"

cat > "$OUT_DIR/README.md" <<EOF
# Instantánea congelada — Entrega 4

Este directorio existe por el Entregable 26 de la Guía de Cierre ("Separación entre documento
vivo y documento congelado"): el PDF versionado en \`docs/latex/main.pdf\` **no** sirve como
instantánea, porque es la salida del documento vivo (\`docs/latex/main.tex\`) recompilada en
cada \`push\` a \`main\` — no lleva fecha ni commit de referencia propio, así que no hay forma de
saber a qué versión corresponde un PDF descargado en un momento dado.

## Qué es este archivo

[\`$(basename "$OUT_PDF")\`]($(basename "$OUT_PDF")) es una copia congelada del manuscrito,
generada por \`scripts/congelar_manuscrito.sh\` a partir del commit \`$COMMIT_HASH\`
($COMMIT_DATE) — el commit de cierre de esta entrega. El propio PDF lleva, en un recuadro bajo el
título, el aviso "Instantánea congelada de la Entrega 4 — commit \`$COMMIT_SHORT\`, generada el
$COMMIT_DATE", así que la referencia de versión no depende solo del nombre del archivo o de este
README.

**Esta copia no se vuelve a recompilar ni se sobrescribe.** Si el commit de cierre cambia (por
ejemplo, por una corrección de último momento antes del corte), se regenera con
\`scripts/congelar_manuscrito.sh <nuevo-commit>\`, que reemplaza este archivo y este README con
la instantánea correcta — no coexisten dos congeladas distintas a propósito.

## Cómo verificarla

\`\`\`bash
git show $COMMIT_HASH:docs/latex/main.tex > /tmp/main_${COMMIT_SHORT}.tex   # el fuente exacto de ese commit
sha256sum $(basename "$OUT_PDF")
# Huella esperada:
# $SHA256
\`\`\`

Tamaño esperado: $SIZE_BYTES bytes, $PAGES páginas.

El PDF se compiló con \`scripts/congelar_manuscrito.sh\` ($FREEZE_TIMESTAMP), que reproduce los
mismos cuatro pasos (\`pdflatex\`, \`bibtex\`, \`pdflatex\` × 2) que documenta la raíz del
\`README.md\`, sobre el árbol de \`docs/latex/\` tal como estaba en \`$COMMIT_HASH\` (no sobre el
árbol de trabajo actual).
EOF

echo "Listo: $OUT_PDF"
echo "SHA-256: $SHA256"
