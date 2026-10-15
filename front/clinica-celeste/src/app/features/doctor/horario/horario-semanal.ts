import { Component, computed, inject, Injectable, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { HorarioResponse } from '../../../core/api/dtos';
import { HorariosApi } from '../../../core/api/horarios.api';
import { mensajeDeError } from '../../../core/http/errores';
import { DialogService, ToastService } from '../../../core/ui.service';
import { DiaSemana } from '../../../core/models';
import { fmtHora } from '../../../core/fechas';
import { Icon } from '../../../shared/icons';

const DIAS: [DiaSemana, string][] = [
  ['MONDAY', 'Lunes'], ['TUESDAY', 'Martes'], ['WEDNESDAY', 'Miércoles'], ['THURSDAY', 'Jueves'],
  ['FRIDAY', 'Viernes'], ['SATURDAY', 'Sábado'], ['SUNDAY', 'Domingo'],
];
const NOMBRE_DIA = Object.fromEntries(DIAS) as Record<DiaSemana, string>;

/** El back manda '08:00:00'; los <input type="time"> usan '08:00' */
const hm = (h: string) => h.slice(0, 5);

/**
 * Horario semanal del doctor: bloques por día. El back rechaza (409) los que se cruzan en el mismo día.
 * La lista y el formulario son componentes aparte (van en columnas distintas) y comparten este estado.
 */
@Injectable()
export class HorarioSemanalEstado {
  private readonly api = inject(HorariosApi);
  private readonly dialog = inject(DialogService);
  private readonly toast = inject(ToastService);

  readonly horarios = signal<HorarioResponse[]>([]);
  readonly cargado = signal(false);
  readonly errorCarga = signal('');

  // Formulario
  readonly editando = signal<HorarioResponse | null>(null);
  readonly dia = signal<DiaSemana>('MONDAY');
  readonly inicio = signal('08:00');
  readonly fin = signal('12:00');
  readonly error = signal('');
  readonly campoError = signal<'rango' | null>(null);
  readonly guardando = signal(false);

  readonly resumen = computed(() => {
    const dias = new Set(this.horarios().map((h) => h.diaSemana)).size;
    return dias ? `Atiendes ${dias} ${dias === 1 ? 'día' : 'días'} a la semana` : 'Sin horario definido';
  });

  constructor() {
    this.cargar();
  }

  async cargar() {
    this.errorCarga.set('');
    try {
      this.horarios.set(await firstValueFrom(this.api.listarMios()));
    } catch (e) {
      this.errorCarga.set(mensajeDeError(e));
    } finally {
      this.cargado.set(true);
    }
  }

  delDia(dia: DiaSemana) { return this.horarios().filter((h) => h.diaSemana === dia); }
  rango(h: HorarioResponse) { return `${fmtHora(hm(h.horaInicio))} a ${fmtHora(hm(h.horaFin))}`; }

  editar(h: HorarioResponse) {
    this.editando.set(h);
    this.dia.set(h.diaSemana);
    this.inicio.set(hm(h.horaInicio));
    this.fin.set(hm(h.horaFin));
    this.error.set('');
    this.campoError.set(null);
  }

  limpiar() {
    this.editando.set(null);
    this.error.set('');
    this.campoError.set(null);
  }

  async guardar(e: Event) {
    e.preventDefault();
    if (!this.inicio() || !this.fin()) return this.falla('Escribe la hora de inicio y la de fin.');
    if (this.inicio() >= this.fin()) return this.falla('La hora de inicio debe ser anterior a la de fin.');
    this.error.set('');
    this.campoError.set(null);

    const body = { diaSemana: this.dia(), horaInicio: this.inicio(), horaFin: this.fin() };
    const editando = this.editando();
    this.guardando.set(true);
    try {
      await firstValueFrom(editando ? this.api.actualizar(editando.id, body) : this.api.crear(body));
      this.toast.show(editando ? 'Horario actualizado' : `Agregaste el ${NOMBRE_DIA[body.diaSemana].toLowerCase()} de ${fmtHora(body.horaInicio)} a ${fmtHora(body.horaFin)}`);
      this.limpiar();
      await this.cargar();
    } catch (err) {
      this.falla(mensajeDeError(err)); // 409: se cruza con otro bloque del mismo día
    } finally {
      this.guardando.set(false);
    }
  }

  async eliminar(h: HorarioResponse) {
    const ok = await this.dialog.confirm({
      title: `¿Quitar el ${NOMBRE_DIA[h.diaSemana].toLowerCase()} de ${this.rango(h)}?`,
      body: 'Los pacientes dejarán de ver turnos en ese bloque. Las citas que ya están agendadas se mantienen.',
      confirmText: 'Quitar bloque', cancelText: 'Mantener', danger: true, tone: 'cancelada', ic: 'x',
    });
    if (!ok) return;
    try {
      await firstValueFrom(this.api.eliminar(h.id));
      if (this.editando()?.id === h.id) this.limpiar();
      this.toast.show('Bloque quitado', 'x');
      await this.cargar();
    } catch (err) {
      this.toast.show(mensajeDeError(err), 'alert', true);
    }
  }

  private falla(msg: string) {
    this.error.set(msg);
    this.campoError.set('rango');
  }
}

@Component({
  selector: 'app-horario-lista',
  imports: [Icon],
  template: `
    <section class="panel">
      <header class="panel-head"><h2>Horario semanal</h2><span class="muted small">{{ e.resumen() }}</span></header>
      @if (!e.cargado() || e.errorCarga()) {
        <div class="empty" [attr.role]="e.errorCarga() ? 'alert' : 'status'">
          @if (e.errorCarga()) {
            <svg appIcon="alert"></svg><p>{{ e.errorCarga() }}</p>
            <button class="btn btn-ghost btn-sm" (click)="e.cargar()"><svg appIcon="refresh"></svg> Reintentar</button>
          } @else {
            <svg appIcon="clock"></svg><p>Cargando tu horario…</p>
          }
        </div>
      } @else {
        <div class="pref-list">
          @for (d of dias; track d[0]) {
            @let bloques = e.delDia(d[0]);
            <div class="pref">
              <div><strong>{{ d[1] }}</strong>@if (!bloques.length) {<span>No atiendes</span>}</div>
              @if (bloques.length) {
                <div class="bloques">
                  @for (b of bloques; track b.id) {
                    <span class="bloque" [class.is-editing]="e.editando()?.id === b.id">
                      <button type="button" class="bloque-txt" (click)="e.editar(b)" [attr.aria-label]="'Editar ' + d[1] + ' de ' + e.rango(b)">{{ e.rango(b) }}</button>
                      <button type="button" class="bloque-x" (click)="e.eliminar(b)" [attr.aria-label]="'Quitar ' + d[1] + ' de ' + e.rango(b)"><svg appIcon="x"></svg></button>
                    </span>
                  }
                </div>
              }
            </div>
          }
        </div>
      }
    </section>`,
})
export class HorarioSemanalLista {
  protected readonly e = inject(HorarioSemanalEstado);
  protected readonly dias = DIAS;
}

@Component({
  selector: 'app-horario-form',
  template: `
    <form class="panel" novalidate (submit)="e.guardar($event)">
      <header class="panel-head"><h2>{{ e.editando() ? 'Editar bloque' : 'Agregar bloque' }}</h2></header>
      <div class="form-grid">
        <div class="field span-2">
          <label for="h-dia">Día</label>
          <select id="h-dia" (change)="e.dia.set($any($event.target).value)">
            @for (d of dias; track d[0]) { <option [value]="d[0]" [selected]="e.dia() === d[0]">{{ d[1] }}</option> }
          </select>
        </div>
        <div class="field" [class.has-error]="e.campoError() === 'rango'">
          <label for="h-inicio">Desde</label>
          <input id="h-inicio" type="time" step="900" [value]="e.inicio()" (input)="e.inicio.set($any($event.target).value)">
        </div>
        <div class="field" [class.has-error]="e.campoError() === 'rango'">
          <label for="h-fin">Hasta</label>
          <input id="h-fin" type="time" step="900" [value]="e.fin()" (input)="e.fin.set($any($event.target).value)">
        </div>
      </div>
      <p class="form-error" role="alert">{{ e.error() }}</p>
      <div class="form-actions">
        @if (e.editando()) { <button type="button" class="btn btn-ghost" (click)="e.limpiar()">Cancelar edición</button> }
        <button type="submit" class="btn btn-primary" [class.is-loading]="e.guardando()" [disabled]="e.guardando()">
          <span class="btn-label">{{ e.editando() ? 'Guardar cambios' : 'Agregar bloque' }}</span><span class="spinner"></span>
        </button>
      </div>
      <p class="muted small" style="margin-top:14px">Los pacientes ven los turnos libres dentro de estos bloques. Las citas ya agendadas no cambian.</p>
    </form>`,
})
export class HorarioSemanalForm {
  protected readonly e = inject(HorarioSemanalEstado);
  protected readonly dias = DIAS;
}
