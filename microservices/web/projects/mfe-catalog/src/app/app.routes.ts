import { Routes } from '@angular/router';
import { CategoriesPage } from './categories.page';
import { ProductsPage } from './products.page';

/** Exposto pelo Module Federation como "./routes" e montado pelo shell em /catalog. */
export const routes: Routes = [
  { path: '', component: ProductsPage },
  { path: 'categories', component: CategoriesPage },
];
