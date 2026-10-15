import { Component, inject, Injectable, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { BloqueosApi } from '../../../core/api/bloqueos.api';
import { BloqueoResponse } from '../../../core/api/dtos';
import { mensajeDeError } from '../../../core/http/errores';
import { DialogService, ToastService } from '../../../core/ui.service';
import { ahora, fmt, fmtHora, horaDe } from '../../../core/fechas';
import { Icon } from '../../../shared/icons';

/** El back manda '2026-10-20T08:00:00'; los <input type="datetime-local"> usan '2026-10-20T08:00' */
const local = (fh: string) => fh.slice(0, 16);

/**
 * Periodos en que el doctor no atiende (vacaciones, congresos). El back no deja bloquear
 * un rango con citas pendientes o confirmadas (409): primero hay que cancelarlas.
 * La lista y el formulario son componentes aparte (van en columnas distintas) y comparten este estado.
 */
@Injectable()
export class BloqueosEstado {
  private readonly api = inject(BloqueosApi);
  private readonly dialog = inject(DialogService);
  private readonly toast = inject(ToastService);

  readonly bloqueos = signal<BloqueoResponse[]>([]);
  readonly cargado = signal(false);
  readonly errorCarga = signal('');
  readonly minimo = local(ahora());

  // Formulario
  readonly editando = signal<BloqueoResponse | null>(null);
  readonly desde = signal('');
  readonly hasta = signal('');
  readonly motivo = signal('');
  readonly error = signal('');
  readonly campoError = signal(false);
  readonly guardando = signal(false);

  constructor() {
    this.cargar();
  }

  async cargar() {
    this.errorCarga.set('');
    try {
      this.bloqueos.set(await firstValueFrom(this.api.listarVigentes()));
    } catch (e) {
      this.errorCarga.set(mensajeDeError(e));
    } finally {
      this.cargado.set(true);
    }
  }

  /** 'Del 20 de oct, 8:00 a. m. al 24 de oct, 6:00 p. m.' */
  rango(b: BloqueoResponse) {
    const parte = (fh: string) => `${fmt(fh, { day: 'numeric', month: 'short' }).replace('.', '')}, ${fmtHora(horaDe(fh))}`;
    return `Del ${parte(b.fechaInicio)} al ${parte(b.fechaFin)}`;
  }

  editar(b: BloqueoResponse) {
    this.editando.set(b);
    this.desde.set(local(b.fechaInicio));
    this.hasta.set(local(b.fechaFin));
    this.motivo.set(b.motivo ?? '');
    this.error.set('');
    this.campoError.set(false);
  }

  limpiar() {
    this.editando.set(null);
    this.desde.set('');
    this.hasta.set('');
    this.motivo.set('');
    this.error.set('');
    this.campoError.set(false);
  }

  async guardar(e: Event) {
    e.preventDefault();
    const desde = this.desde(), hasta = this.hasta();
    if (!desde || !hasta) return this.falla('Elige cuándo empieza y cuándo termina el bloqueo.');
    if (desde >= hasta) return this.falla('El bloqueo debe terminar después de empezar.');
    if (hasta <= local(ahora())) return this.falla('El bloqueo debe terminar en el futuro.');
    this.error.set('');
    this.campoError.set(false);

    const body = { fechaInicio: desde, fechaFin: hasta, motivo: this.motivo().trim() || undefined };
    const editando = this.editando();
    this.guardando.set(true);
    try {
      await firstValueFrom(editando ? this.api.actualizar(editando.id, body) : this.api.crear(body));
      this.toast.show(editando ? 'Bloqueo actualizado' : 'Periodo bloqueado');
      this.limpiar();
      await this.cargar();
    } catch (err) {
      this.falla(mensajeDeError(err)); // 409: hay citas activas en el rango
    } finally {
      this.guardando.set(false);
    }
  }

  async eliminar(b: BloqueoResponse) {
    const ok = await this.dialog.confirm({
      title: '¿Quitar este bloqueo?',
      body: `${this.rango(b)} volverá a estar disponible para que los pacientes agenden.`,
      confirmText: 'Quitar bloqueo', cancelText: 'Mantener', danger: true, tone: 'cancelada', ic: 'x',
    });
    if (!ok) return;
    try {
      await firstValueFrom(this.api.eliminar(b.id));
      if (this.editando()?.id === b.id) this.limpiar();
      this.toast.show('Bloqueo quitado', 'x');
      await this.cargar();
    } catch (err) {
      this.toast.show(mensajeDeError(err), 'alert', true);
    }
  }

  private falla(msg: string) {
    this.error.set(msg);
    this.campoError.set(true);
  }
}

@Component({
  selector: 'app-bloqueos-lista',
  imports: [Icon],
  template: `
    <section class="panel">
      <header class="panel-head"><h2>Bloqueos de agenda</h2><span class="muted small">Vacaciones, congresos o ausencias</span></header>
      @if (!e.cargado() || e.errorCarga()) {
        <div class="empty" [attr.role]="e.errorCarga() ? 'alert' : 'status'">
          @if (e.errorCarga()) {
            <svg appIcon="alert"></svg><p>{{ e.errorCarga() }}</p>
            <button class="btn btn-ghost btn-sm" (click)="e.cargar()"><svg appIcon="refresh"></svg> Reintentar</button>
          } @else {
            <svg appIcon="clock"></svg><p>Cargando tus bloqueos…</p>
          }
        </div>
      } @else if (e.bloqueos().length) {
        <div class="pref-list">
          @for (b of e.bloqueos(); track b.id) {
            <div class="pref" [class.is-editing]="e.editando()?.id === b.id">
              <div><strong>{{ e.rango(b) }}</strong><span>{{ b.motivo || 'Sin motivo' }}</span></div>
              <div class="row-actions">
                <button type="button" class="btn btn-ghost btn-sm" (click)="e.editar(b)">Editar</button>
                <button type="button" class="btn btn-soft-danger btn-sm" (click)="e.eliminar(b)">Quitar</button>
              </div>
            </div>
          }
        </div>
      } @else {
        <div class="empty"><svg appIcon="leaf"></svg><p>No tienes bloqueos próximos. Tu agenda sigue tu horario semanal.</p></div>
      }
    </section>`,
})
export class BloqueosLista {
  protected readonly e = inject(BloqueosEstado);
}

@Component({
  selector: 'app-bloqueo-form',
  template: `
    <form class="panel" novalidate (submit)="e.guardar($event)">
      <header class="panel-head"><h2>{{ e.editando() ? 'Editar bloqueo' : 'Bloquear un periodo' }}</h2></header>
      <div class="form-grid">
        <div class="field" [class.has-error]="e.campoError()">
          <label for="b-desde">Desde</label>
          <input id="b-desde" type="datetime-local" step="900" [min]="e.minimo" [value]="e.desde()" (input)="e.desde.set($any($event.target).value)">
        </div>
        <div class="field" [class.has-error]="e.campoError()">
          <label for="b-hasta">Hasta</label>
          <input id="b-hasta" type="datetime-local" step="900" [min]="e.desde() || e.minimo" [value]="e.hasta()" (input)="e.hasta.set($any($event.target).value)">
        </div>
        <div class="field span-2">
          <label for="b-motivo">Motivo <span class="muted">(opcional)</span></label>
          <input id="b-motivo" type="text" maxlength="255" placeholder="Por ejemplo: congreso de medicina interna" [value]="e.motivo()" (input)="e.motivo.set($any($event.target).value)">
        </div>
      </div>
      <p class="form-error" role="alert">{{ e.error() }}</p>
      <div class="form-actions">
        @if (e.editando()) { <button type="button" class="btn btn-ghost" (click)="e.limpiar()">Cancelar edición</button> }
        <button type="submit" class="btn btn-primary" [class.is-loading]="e.guardando()" [disabled]="e.guardando()">
          <span class="btn-label">{{ e.editando() ? 'Guardar cambios' : 'Bloquear periodo' }}</span><span class="spinner"></span>
        </button>
      </div>
      <p class="muted small" style="margin-top:14px">Mientras dure el bloqueo no aparecerán turnos libres. Si ya hay citas en ese rango, cancélalas primero desde la agenda.</p>
    </form>`,
})
export class BloqueoForm {
  protected readonly e = inject(BloqueosEstado);
}
