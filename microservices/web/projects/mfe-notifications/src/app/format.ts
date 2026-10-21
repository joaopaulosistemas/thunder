import { Pipe, PipeTransform } from '@angular/core';

// Formatação pt-BR com Intl do navegador: não depende dos dados de idioma do Angular, que o
// Native Federation não compartilha entre o shell e os remotos.
const brl = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' });
const stamp = new Intl.DateTimeFormat('pt-BR', {
  day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit', second: '2-digit',
});

@Pipe({ name: 'brl' })
export class BrlPipe implements PipeTransform {
  transform(cents: number): string {
    return brl.format(cents / 100);
  }
}

@Pipe({ name: 'stamp' })
export class StampPipe implements PipeTransform {
  transform(iso: string): string {
    return stamp.format(new Date(iso));
  }
}
