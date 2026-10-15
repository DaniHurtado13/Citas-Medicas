import { Component, computed, inject, signal } from '@angular/core';
import { DoctorResponse } from '../../core/api/dtos';
import { mensajeDeError } from '../../core/http/errores';
import { DialogService, ToastService } from '../../core/ui.service';
import { VistaStateService } from '../../core/vista-state.service';
import { Icon } from '../../shared/icons';
import { Avatar, Carga } from '../../shared/ui';
import { AdminStore } from './admin.store';
import { DoctorDrawer } from './doctor-drawer';
import { DoctorForm } from './doctor-form';

const ESTADOS_FILTRO = [['todos', 'Todos'], ['activos', 'Activos'], ['inactivos', 'Deshabilitados']] as const;

@Component({
  selector: 'app-admin-doctores',
  imports: [Icon, Avatar, Carga],
  template: `
    <section class="page-head">
      <div>
        <h1 class="page-title">Doctores</h1>
        <p>Consulta sus datos y decide quién puede recibir nuevas citas. Deshabilitar a un doctor no borra su historial.</p>
      </div>
      <button class="btn btn-primary" (click)="nuevo()"><svg appIcon="plus"></svg> Nuevo doctor</button>
    </section>

    @if (!store.cargado() || store.error()) {
      <app-carga texto="Cargando doctores…" [error]="store.error()" (reintentar)="store.cargar()" />
    } @else {
      <div class="toolbar">
        <label class="search">
          <svg appIcon="search"></svg>
          <input class="input" type="search" placeholder="Buscar por nombre o consultorio" [value]="vista.docSearch()"
                 (input)="vista.docSearch.set($any($event.target).value)" aria-label="Buscar doctor">
        </label>
        <select class="input" aria-label="Filtrar por especialidad" (change)="vista.docEsp.set($any($event.target).value)">
          <option value="">Todas las especialidades</option>
          @for (e of store.especialidades(); track e.id) {
            <option [value]="e.id" [selected]="vista.docEsp() === e.id">{{ e.nombre }}</option>
          }
        </select>
        <div class="seg" role="group" aria-label="Filtrar por estado">
          @for (f of filtros; track f[0]) {
            <button [class.is-active]="vista.docEstado() === f[0]" (click)="vista.docEstado.set(f[0])" [attr.aria-pressed]="vista.docEstado() === f[0]">{{ f[1] }}</button>
          }
        </div>
        <span class="count">{{ lista().length }} de {{ store.doctores().length }} doctores</span>
      </div>

      <section class="panel p0">
        <div class="table-wrap">
          <table>
            <thead><tr><th>Doctor</th><th>Especialidad</th><th>Consultorio</th><th>Consulta</th><th>Estado</th><th><span class="sr-only">Acciones</span></th></tr></thead>
            <tbody>
              @for (d of lista(); track d.id) {
                <tr [class.is-off]="!d.activo">
                  <td><div class="who"><app-avatar [name]="d.nombre" /><div><strong>{{ d.nombre }}</strong></div></div></td>
                  <td>{{ d.especialidadNombre }}</td>
                  <td>{{ d.consultorio }}</td>
                  <td class="num">{{ d.duracionConsultaMin }} min</td>
                  <td>
                    <div class="state-cell" [attr.data-s]="d.activo ? 'activo' : 'inactivo'">
                      <label class="switch" [title]="d.activo ? 'Deshabilitar' : 'Habilitar'">
                        <input type="checkbox" [checked]="d.activo" [disabled]="ocupado() === d.id" (change)="toggle(d, $any($event.target))"
                               [attr.aria-label]="(d.activo ? 'Deshabilitar' : 'Habilitar') + ' a ' + d.nombre">
                        <span></span>
                      </label>
                      <b>{{ d.activo ? 'Habilitado' : 'Deshabilitado' }}</b>
                    </div>
                  </td>
                  <td><div class="row-actions"><button class="btn btn-ghost btn-sm" (click)="ver(d)"><svg appIcon="eye"></svg> Ver</button></div></td>
                </tr>
              } @empty {
                <tr><td colspan="6"><div class="empty"><svg appIcon="search"></svg><p>Ningún doctor coincide con la búsqueda. Prueba con otro nombre o quita los filtros.</p></div></td></tr>
              }
            </tbody>
          </table>
        </div>
      </section>
    }`,
})
export class AdminDoctores {
  protected readonly store = inject(AdminStore);
  protected readonly vista = inject(VistaStateService);
  private readonly dialog = inject(DialogService);
  private readonly toast = inject(ToastService);

  protected readonly filtros = ESTADOS_FILTRO;
  /** id del doctor cuyo cambio de estado está en camino */
  protected readonly ocupado = signal<string | null>(null);

  protected readonly lista = computed(() => {
    const q = this.vista.docSearch().toLowerCase().trim();
    const esp = this.vista.docEsp();
    const estado = this.vista.docEstado();
    return this.store.doctores().filter((d) =>
      (!q || d.nombre.toLowerCase().includes(q) || d.consultorio.toLowerCase().includes(q))
      && (!esp || d.especialidadId === esp)
      && (estado === 'todos' || (estado === 'activos' ? d.activo : !d.activo)));
  });

  constructor() {
    this.store.cargar();
  }

  protected nuevo() {
    this.dialog.openDrawer(DoctorForm, {});
  }

  protected ver(d: DoctorResponse) {
    this.dialog.openDrawer(DoctorDrawer, { doctorId: d.id });
  }

  /** En el back, deshabilitar cancela sus citas futuras y avisa a cada paciente por correo */
  protected async toggle(d: DoctorResponse, input: HTMLInputElement) {
    input.checked = d.activo; // el switch solo cambia cuando el back confirma
    if (d.activo) {
      const ok = await this.dialog.confirm({
        title: `¿Deshabilitar a ${d.nombre}?`,
        body: 'No podrá iniciar sesión ni recibir reservas. Sus citas futuras se cancelarán y cada paciente recibirá un correo. Las citas pasadas se conservan.',
        confirmText: 'Deshabilitar', tone: 'cancelada', ic: 'ban', danger: true,
      });
      if (!ok) return;
    }
    this.ocupado.set(d.id);
    try {
      if (d.activo) {
        const n = await this.store.desactivar(d.id);
        this.toast.show(`${d.nombre} quedó deshabilitado${n ? `. Se cancelaron ${n} ${n === 1 ? 'cita' : 'citas'}` : ''}`, 'ban');
      } else {
        await this.store.activar(d.id);
        this.toast.show(`${d.nombre} está habilitado de nuevo`);
      }
    } catch (e) {
      this.toast.show(mensajeDeError(e), 'alert', true);
    } finally {
      this.ocupado.set(null);
    }
  }
}
