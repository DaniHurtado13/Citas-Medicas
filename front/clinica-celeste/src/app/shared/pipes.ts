import { Pipe, PipeTransform } from '@angular/core';
import { cap, fmt, fmtHora, horaDe, longDate } from '../core/fechas';

/** '14:30' o '2026-09-26T14:30:00' -> '2:30 p. m.' */
@Pipe({ name: 'hora' })
export class HoraPipe implements PipeTransform {
  transform(v: string): string {
    return fmtHora(v.length > 5 ? horaDe(v) : v);
  }
}

/** -> 'Sábado, 26 de septiembre' */
@Pipe({ name: 'longDate' })
export class LongDatePipe implements PipeTransform {
  transform(v: Date | string): string {
    return longDate(typeof v === 'string' ? v.slice(0, 10) : v);
  }
}

/** Formato libre con Intl en es-CO; `capitalizar` pone la primera letra en mayúscula */
@Pipe({ name: 'fecha' })
export class FechaPipe implements PipeTransform {
  transform(v: Date | string, o: Intl.DateTimeFormatOptions, capitalizar = false, sinPunto = false): string {
    let s = fmt(typeof v === 'string' ? v.slice(0, 10) : v, o);
    if (sinPunto) s = s.replace('.', '');
    return capitalizar ? cap(s) : s;
  }
}
