import { Component } from '@angular/core';
import { BloqueoForm, BloqueosEstado, BloqueosLista } from './bloqueos';
import { HorarioSemanalEstado, HorarioSemanalForm, HorarioSemanalLista } from './horario-semanal';

/**
 * "¿Cuándo atiendo?": el horario semanal y los periodos bloqueados, juntos en una sola página.
 * Listas a la izquierda y formularios a la derecha, en dos columnas que no dependen de la altura de la otra.
 */
@Component({
  selector: 'app-doctor-horario',
  imports: [HorarioSemanalLista, HorarioSemanalForm, BloqueosLista, BloqueoForm],
  providers: [HorarioSemanalEstado, BloqueosEstado],
  template: `
    <section class="page-head">
      <div>
        <h1 class="page-title">Mi horario</h1>
        <p>Define en qué días y horas atiendes, y bloquea los periodos en que no estarás.</p>
      </div>
    </section>

    <div class="grid-2 horario-cols">
      <div class="col">
        <app-horario-lista />
        <app-bloqueos-lista />
      </div>
      <div class="col">
        <app-horario-form />
        <app-bloqueo-form />
      </div>
    </div>`,
})
export class DoctorHorario {}
