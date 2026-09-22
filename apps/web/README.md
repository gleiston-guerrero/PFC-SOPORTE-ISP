# apps/web — Consola web (equipo ACC)

Frontend oficial del sistema desde la Entrega 4 (reemplaza al `frontend/` vanilla de Entregas
2/3, ver `docs/diagrams/c4-nivel2-contenedores.md`). React 18 + TypeScript en modo estricto sobre
Vite, arquitectura por características (`console`, `admin`, `reports`, `auth` en
`src/features/`), empaquetado en un Dockerfile *multi-stage* (`nginx:alpine` en tiempo de
ejecución). Detalle completo — rutas, control de acceso, internacionalización, cobertura — en la
Sección "Aplicación web" del manuscrito (`docs/latex/secciones/aplicacion_web.tex`).

> Este archivo reemplaza el README genérico que deja `npm create vite` al crear el proyecto
> (mencionaba Oxlint, que este proyecto no usa — el linter real es ESLint, ver abajo).

## Cómo correrlo

```bash
npm install
npm run dev              # servidor de desarrollo (Vite)
```

Necesita `api-gateway` corriendo en `http://localhost:8000` (ver `docker-compose.yml` en la raíz
del repo) para que las llamadas a la API funcionen.

## Scripts

| Comando | Qué hace |
|---|---|
| `npm run dev` | Servidor de desarrollo con HMR |
| `npm run build` | `tsc -b && vite build` — build de producción |
| `npm run lint` | ESLint (`--max-warnings 0`, reglas de `@typescript-eslint`) |
| `npm test` | Pruebas unitarias (Vitest + Testing Library) |
| `npm run test:coverage` | Igual, con reporte de cobertura (`v8`) |
| `npm run format` | Prettier sobre `src/**/*.{ts,tsx,css}` |
| `npm run test:e2e` | Pruebas E2E (Playwright), contra el *stack* real levantado |
