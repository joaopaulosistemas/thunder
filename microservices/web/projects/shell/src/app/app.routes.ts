import { loadRemoteModule } from '@angular-architects/native-federation';
import { Route, Routes } from '@angular/router';
import { RemoteUnavailable } from './remote-unavailable';

/**
 * Carrega as rotas de um microfrontend em tempo de execucao (Module Federation).
 * O endereco de cada remoto vem de federation.manifest.json, sem recompilar o shell.
 * Se o remoto estiver fora do ar, mostra um aviso em vez de quebrar a aplicacao.
 */
function remote(name: string): () => Promise<Routes> {
  return () =>
    loadRemoteModule<{ routes: Routes }>(name, './routes')
      .then((m) => m.routes)
      .catch((error) => {
        console.error(`Falha ao carregar o microfrontend ${name}`, error);
        const fallback: Route = { path: '', component: RemoteUnavailable };
        return [fallback];
      });
}

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'catalog' },
  { path: 'catalog', loadChildren: remote('mfe-catalog') },
  { path: 'orders', loadChildren: remote('mfe-orders') },
  { path: 'notifications', loadChildren: remote('mfe-notifications') },
  { path: '**', redirectTo: 'catalog' },
];
