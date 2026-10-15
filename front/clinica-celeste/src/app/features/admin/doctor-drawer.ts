import { Component, computed, inject, input, signal } from '@angular/core';
import { mensajeDeError } from '../../core/http/errores';
import { DialogService, ToastService } from '../../core/ui.service';
import { Icon } from '../../shared/icons';
import { Avatar } from '../../shared/ui';
import { AdminStore } from './admin.store';
import { DoctorForm } from './doctor-form';

/** Ficha del doctor en el cajón lateral: datos, certificado y acceso a editar */
@Component({
  selector: 'app-doctor-drawer',
  imports: [Icon, Avatar],
  template: `
    @if (doctor(); as d) {
      <div class="drawer-head">
        <app-avatar class="avatar-lg" [name]="d.nombre" />
        <div>
          <h3>{{ d.nombre }}</h3>
          <p class="muted">{{ d.especialidadNombre }}</p>
          <div style="margin-top:8px"><span class="badge" [attr.data-s]="d.activo ? 'activo' : 'inactivo'">{{ d.activo ? 'Habilitado' : 'Deshabilitado' }}</span></div>
        </div>
        <button class="icon-btn" (click)="dialog.close()" aria-label="Cerrar"><svg appIcon="x"></svg></button>
      </div>
      <div class="side-info">
        <dl>
          <div><dt>Consultorio</dt><dd>{{ d.consultorio }}</dd></div>
          <div><dt>Duración de cada cita</dt><dd>{{ d.duracionConsultaMin }} minutos</dd></div>
          @if (especialidad(); as e) {
            <div><dt>Especialidad</dt><dd>{{ e.nombre }}<span class="muted" style="display:block;font-weight:500">{{ e.descripcion }}</span></dd></div>
          }
        </dl>
      </div>
      <div class="appt-actions">
        <button class="btn btn-ghost" [class.is-loading]="abriendo()" [disabled]="abriendo()" (click)="verCertificado()">
          <span class="btn-label"><svg appIcon="eye"></svg> Ver certificado</span><span class="spinner"></span>
        </button>
        <button class="btn btn-primary" (click)="editar()">Editar datos</button>
      </div>
    }`,
  host: { style: 'display: contents' },
})
export class DoctorDrawer {
  readonly doctorId = input.required<string>();
  private readonly store = inject(AdminStore);
  private readonly toast = inject(ToastService);
  protected readonly dialog = inject(DialogService);

  protected readonly doctor = computed(() => this.store.doctor(this.doctorId()));
  protected readonly especialidad = computed(() => this.store.especialidades().find((e) => e.id === this.doctor()?.especialidadId));
  protected readonly abriendo = signal(false);

  /** Se pide como blob (con el token) y se abre con una URL temporal que se libera al minuto */
  protected async verCertificado() {
    this.abriendo.set(true);
    try {
      const archivo = await this.store.certificado(this.doctorId());
      const url = URL.createObjectURL(archivo);
      if (!window.open(url, '_blank')) {
        // Si el navegador bloquea la pestaña nueva, se descarga
        const a = Object.assign(document.createElement('a'), { href: url, download: `certificado-${this.doctor()?.nombre ?? 'doctor'}` });
        a.click();
      }
      setTimeout(() => URL.revokeObjectURL(url), 60_000);
    } catch (e) {
      this.toast.show(mensajeDeError(e, { 404: 'Este doctor no tiene certificado registrado.' }), 'alert', true);
    } finally {
      this.abriendo.set(false);
    }
  }

  protected editar() {
    this.dialog.openDrawer(DoctorForm, { doctorId: this.doctorId() });
  }
}
