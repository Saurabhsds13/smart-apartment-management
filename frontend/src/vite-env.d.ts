/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** Base path for the versioned API. Same-origin in production. */
  readonly VITE_API_BASE_URL?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
