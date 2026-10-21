import { HttpErrorResponse } from '@angular/common/http';

/** Texto amigável para o erro do backend (ProblemDetail do Spring traz "detail"). */
export function messageFor(error: unknown, fallback: string): string {
  if (error instanceof HttpErrorResponse) {
    if (error.status === 409) {
      return (error.error?.detail as string | undefined) ?? 'Conflito: o registro foi alterado. Recarregue e tente de novo.';
    }
    if (error.status === 400) {
      return 'Dados inválidos. Confira os campos e tente de novo.';
    }
    if (error.status === 403) {
      return 'Você não tem permissão para esta ação.';
    }
  }
  return fallback;
}
