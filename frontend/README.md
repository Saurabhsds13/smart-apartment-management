# Societix Frontend

Vite + React + TypeScript (strict) single-page app. Served **same-origin** with the
backend so it uses the server-side session cookie with CSRF protection (design ADR-3).

## Stack

- Vite + React 18
- TypeScript strict (`strict: true`, `noUncheckedIndexedAccess`)
- React Router — routing
- TanStack Query — server state
- React Hook Form + Zod — forms and validation

## Structure (`src/`)

| Folder         | Purpose                                                        |
| -------------- | ------------------------------------------------------------- |
| `app/`         | application shell, providers, error boundaries                |
| `routes/`      | route definitions, role-guarded routes                        |
| `features/`    | one folder per domain feature (billing, residents, visitors…) |
| `components/`  | reusable accessible UI library                                |
| `hooks/`       | cross-cutting hooks (useAuth, useTenant, usePermissions)      |
| `lib/`         | API client, query client, CSRF, formatting, Zod helpers       |
| `styles/`      | Tailwind config, design tokens                                |

## API client & tenancy

`src/lib/api-client.ts` is a same-origin fetch wrapper. It:

- sends the session cookie on every request (`credentials: 'include'`);
- attaches the CSRF token (`XSRF-TOKEN` cookie → `X-XSRF-TOKEN` header) on unsafe
  methods (POST/PUT/PATCH/DELETE);
- **never sends a society id** — tenant scope is derived from the server session
  (Req 1.2, BR1);
- surfaces RFC 7807 problem+json errors as `ApiError`.

## Scripts

```bash
npm install          # install dependencies
npm run dev          # start dev server (proxies /api to the backend)
npm run build        # typecheck (tsc --noEmit) then production build
npm run typecheck

npm test             # run unit + component tests once (Vitest)
npm run test:watch   # watch mode
npm run test:coverage # unit/component tests with coverage
npm run test:e2e     # Playwright E2E (boots the dev server; needs browsers)
```

The dev server proxies `/api` to `VITE_BACKEND_ORIGIN` (default `http://localhost:8080`)
to preserve the same-origin contract for cookies and CSRF.

## Testing

Two complementary layers wire the automated accessibility checks required by
WCAG 2.2 AA conformance (Req 19.1):

- **Unit + component (Vitest + React Testing Library)** — `vitest.config.ts`
  runs in a `jsdom` environment with jest-dom matchers (`src/test/setup.ts`).
  Component tests run **axe-core** against the rendered subtree via the helper
  in `src/test/axe.ts`. Colour-contrast is disabled here (jsdom has no layout);
  it is covered by the browser-based E2E scan.
- **E2E (Playwright + @axe-core/playwright)** — `playwright.config.ts` boots the
  Vite dev server (`webServer`) and runs specs under `e2e/`: a smoke test for
  the app's title and landmark structure, and a full-page **axe** scan against
  the live shell in a real browser.

Running the E2E suite requires the Playwright browsers:

```bash
npx playwright install --with-deps   # one-time (done automatically in CI)
npm run test:e2e
```

In CI the browsers are installed, so the E2E + axe scan runs there. The suite
exercises the signed-out shell when no backend is reachable (the session fetch
fails gracefully and the shell still renders).
