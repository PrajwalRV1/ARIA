import { RenderMode, ServerRoute } from '@angular/ssr';

export const serverRoutes: ServerRoute[] = [
  {
    path: 'interview',
    renderMode: RenderMode.Server,
  },
  {
    path: 'interview/:sessionId',
    renderMode: RenderMode.Server,
  },
  {
    path: 'interview-room',
    renderMode: RenderMode.Server,
  },
  {
    path: 'interview-room/:sessionId',
    renderMode: RenderMode.Server,
  },
  {
    path: '**',
    renderMode: RenderMode.Prerender,
  },
];
