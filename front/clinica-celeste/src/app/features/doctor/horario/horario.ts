import { Component } from '@angular/core';
import { Bloqueos } from './bloqueos';
import { HorarioSemanal } from './horario-semanal';

/** "¿Cuándo atiendo?": el horario semanal y los periodos bloqueados, juntos en una sola página */
@Component({
  selector: 'app-doctor-horario',
  imports: [HorarioSemanal, Bloqueos],
  template: `
    <section class="page-head">
      <div>
        <h1 class="page-title">Mi horario</h1>
        <p>Define en qué días y horas atiendes, y bloquea los periodos en que no estarás.</p>
      </div>
    </section>

    <div class="col">
      <app-horario-semanal />
      <app-bloqueos />
    </div>`,
})
export class DoctorHorario {}
