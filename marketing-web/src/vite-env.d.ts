/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** Caminho base do módulo. Padrão: /modulos/marketing/ */
  readonly VITE_BASE_PATH?: string;
  /** Endereço da API. Padrão: /api/marketing (caminho relativo, pelo gateway ou pelo proxy) */
  readonly VITE_API_URL?: string;
  /** Token de um usuário de teste, só para o modo sozinho */
  readonly VITE_TOKEN_DEV?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
