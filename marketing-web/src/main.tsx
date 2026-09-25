import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { RouterProvider } from 'react-router';
import { plataforma } from '@/plataforma';
import { roteador } from '@/rotas';
import './index.css';

// Antes de renderizar: no modo embutido, registra o ouvinte e avisa a casca (modulo:pronto)
plataforma.iniciar();

createRoot(document.getElementById('raiz')!).render(
  <StrictMode>
    <RouterProvider router={roteador} />
  </StrictMode>,
);
