import { Component, computed, inject, signal } from '@angular/core';
import { EspecialidadResponse } from '../../core/api/dtos';
import { erroresDeCampos, mensajeDeError } from '../../core/http/errores';
import { DialogService, ToastService } from '../../core/ui.service';
import { Icon } from '../../shared/icons';
import { Carga } from '../../shared/ui';
import { AdminStore } from './admin.store';

/** Especialidades de la clínica. El back no deja repetir nombres (409) ni borrar una que tenga doctores (409) */
@Component({
  selector: 'app-admin-especialidades',
  imports: [Icon, Carga],
  template: `
    <section class="page-head">
      <div>
        <h1 class="page-title">Especialidades</h1>
        <p>Los pacientes eligen una especialidad antes de escoger al doctor. Solo se puede eliminar una especialidad sin doctores.</p>
      </div>
    </section>

    @if (!store.cargado() || store.error()) {
      <app-carga texto="Cargando especialidades…" [error]="store.error()" (reintentar)="store.cargar()" />
    } @else {
      <div class="grid-2">
        <section class="panel">
          <header class="panel-head"><h2>Especialidades</h2><span class="muted small">{{ store.especialidades().length }} en total</span></header>
          @if (store.especialidades().length) {
            <div class="pref-list">
              @for (e of store.especialidades(); track e.id) {
                @let n = doctoresDe(e.id);
                <div class="pref" [class.is-editing]="editando()?.id === e.id">
                  <div><strong>{{ e.nombre }}</strong><span>{{ e.descripcion }}</span></div>
                  <div class="row-actions">
                    <span class="muted small" style="white-space:nowrap">{{ n }} {{ n === 1 ? 'doctor' : 'doctores' }}</span>
                    <button type="button" class="btn btn-ghost btn-sm" (click)="editar(e)">Editar</button>
                    <button type="button" class="btn btn-soft-danger btn-sm" [disabled]="n > 0"
                            [title]="n > 0 ? 'Tiene doctores asignados: cámbialos de especialidad para poder eliminarla' : ''" (click)="eliminar(e)">Eliminar</button>
                  </div>
                </div>
              }
            </div>
          } @else {
            <div class="empty"><svg appIcon="steth"></svg><p>Aún no hay especialidades. Crea la primera con el formulario.</p></div>
          }
        </section>

        <form class="panel" novalidate (submit)="guardar($event)">
          <header class="panel-head"><h2>{{ editando() ? 'Editar especialidad' : 'Nueva especialidad' }}</h2></header>
          <div class="form-grid">
            <div class="field span-2" [class.has-error]="errores()['nombre']">
              <label for="e-nombre">Nombre</label>
              <input id="e-nombre" maxlength="255" placeholder="Por ejemplo: Dermatología" [value]="nombre()" (input)="nombre.set($any($event.target).value)">
              <small class="field-msg">{{ errores()['nombre'] }}</small>
            </div>
            <div class="field span-2" [class.has-error]="errores()['descripcion']">
              <label for="e-desc">Descripción</label>
              <textarea id="e-desc" maxlength="500" placeholder="Qué atiende, en palabras que entienda un paciente" [value]="descripcion()" (input)="descripcion.set($any($event.target).value)"></textarea>
              <small class="field-msg">{{ errores()['descripcion'] || descripcion().length + ' de 500 caracteres' }}</small>
            </div>
          </div>
          <p class="form-error" role="alert">{{ error() }}</p>
          <div class="form-actions">
            @if (editando()) { <button type="button" class="btn btn-ghost" (click)="limpiar()">Cancelar edición</button> }
            <button type="submit" class="btn btn-primary" [class.is-loading]="guardando()" [disabled]="guardando()">
              <span class="btn-label">{{ editando() ? 'Guardar cambios' : 'Crear especialidad' }}</span><span class="spinner"></span>
            </button>
          </div>
        </form>
      </div>
    }`,
})
export class AdminEspecialidades {
  protected readonly store = inject(AdminStore);
  private readonly dialog = inject(DialogService);
  private readonly toast = inject(ToastService);

  protected readonly editando = signal<EspecialidadResponse | null>(null);
  protected readonly nombre = signal('');
  protected readonly descripcion = signal('');
  protected readonly errores = signal<Record<string, string>>({});
  protected readonly error = signal('');
  protected readonly guardando = signal(false);

  /** Cuenta también a los desactivados: el back tampoco deja borrar si los tiene */
  private readonly conteo = computed(() => {
    const m = new Map<string, number>();
    this.store.doctores().forEach((d) => m.set(d.especialidadId, (m.get(d.especialidadId) ?? 0) + 1));
    return m;
  });

  constructor() {
    this.store.cargar();
  }

  protected doctoresDe(id: string) { return this.conteo().get(id) ?? 0; }

  protected editar(e: EspecialidadResponse) {
    this.editando.set(e);
    this.nombre.set(e.nombre);
    this.descripcion.set(e.descripcion);
    this.errores.set({});
    this.error.set('');
  }

  protected limpiar() {
    this.editando.set(null);
    this.nombre.set('');
    this.descripcion.set('');
    this.errores.set({});
    this.error.set('');
  }

  protected async guardar(ev: Event) {
    ev.preventDefault();
    const body = { nombre: this.nombre().trim(), descripcion: this.descripcion().trim() };
    const errores: Record<string, string> = {};
    if (!body.nombre) errores['nombre'] = 'Escribe el nombre.';
    if (!body.descripcion) errores['descripcion'] = 'Escribe una descripción corta.';
    this.errores.set(errores);
    if (Object.keys(errores).length) {
      this.error.set('');
      return;
    }

    const editando = this.editando();
    this.guardando.set(true);
    try {
      if (editando) await this.store.actualizarEspecialidad(editando.id, body);
      else await this.store.crearEspecialidad(body);
      this.toast.show(editando ? 'Especialidad actualizada' : `Creaste ${body.nombre}`);
      this.limpiar();
    } catch (e) {
      this.errores.set(erroresDeCampos(e));
      this.error.set(mensajeDeError(e)); // 409: ya existe una especialidad con ese nombre
    } finally {
      this.guardando.set(false);
    }
  }

  protected async eliminar(e: EspecialidadResponse) {
    const ok = await this.dialog.confirm({
      title: `¿Eliminar ${e.nombre}?`,
      body: 'Dejará de aparecer para los pacientes. Esta acción no se puede deshacer.',
      confirmText: 'Eliminar', cancelText: 'Mantener', danger: true, tone: 'cancelada', ic: 'x',
    });
    if (!ok) return;
    try {
      await this.store.eliminarEspecialidad(e.id);
      if (this.editando()?.id === e.id) this.limpiar();
      this.toast.show(`Eliminaste ${e.nombre}`, 'x');
    } catch (err) {
      this.toast.show(mensajeDeError(err), 'alert', true); // 409: tiene doctores
    }
  }
}
