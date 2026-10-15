import { Component, computed, inject, input, OnInit, signal } from '@angular/core';
import { erroresDeCampos, mensajeDeError } from '../../core/http/errores';
import { DialogService, ToastService } from '../../core/ui.service';
import { TipoDocumento, TIPOS_DOCUMENTO } from '../../core/models';
import { Icon } from '../../shared/icons';
import { AdminStore } from './admin.store';
import { EMAIL_RE } from '../../core/validacion';

const TIPOS_CERTIFICADO = ['application/pdf', 'image/jpeg', 'image/png'];
const MAX_CERTIFICADO = 5 * 1024 * 1024;

type Campo = 'nombre' | 'especialidadId' | 'duracionConsultaMin' | 'consultorio'
  | 'tipoDocumento' | 'numeroDocumento' | 'email' | 'passwordProvisional' | 'certificado';

/**
 * Crear o editar un doctor en el cajón lateral.
 * Crear también abre su cuenta (correo + contraseña provisional) y exige el certificado profesional.
 * Editar solo cambia lo que acepta PUT /api/doctores/{id}: nombre, especialidad, duración y consultorio.
 */
@Component({
  selector: 'app-doctor-form',
  imports: [Icon],
  template: `
    <div class="drawer-head">
      <div style="flex:1">
        <h3>{{ editando() ? 'Editar doctor' : 'Nuevo doctor' }}</h3>
        <p class="muted">{{ editando() ? 'Cambia sus datos de atención.' : 'Se creará su cuenta con una contraseña provisional.' }}</p>
      </div>
      <button class="icon-btn" (click)="dialog.close()" aria-label="Cerrar"><svg appIcon="x"></svg></button>
    </div>

    <form class="form-grid" novalidate (submit)="guardar($event)">
      <div class="field span-2" [class.has-error]="err('nombre')">
        <label for="d-nombre">Nombre completo</label>
        <input id="d-nombre" maxlength="255" [value]="nombre()" (input)="nombre.set($any($event.target).value); limpiar('nombre')">
        <small class="field-msg">{{ err('nombre') }}</small>
      </div>
      <div class="field span-2" [class.has-error]="err('especialidadId')">
        <label for="d-esp">Especialidad</label>
        <select id="d-esp" (change)="especialidadId.set($any($event.target).value); limpiar('especialidadId')">
          <option value="" [selected]="!especialidadId()">Elige una especialidad</option>
          @for (e of store.especialidades(); track e.id) { <option [value]="e.id" [selected]="especialidadId() === e.id">{{ e.nombre }}</option> }
        </select>
        <small class="field-msg">{{ err('especialidadId') }}</small>
      </div>
      <div class="field" [class.has-error]="err('consultorio')">
        <label for="d-cons">Consultorio</label>
        <input id="d-cons" maxlength="255" placeholder="Consultorio 204" [value]="consultorio()" (input)="consultorio.set($any($event.target).value); limpiar('consultorio')">
        <small class="field-msg">{{ err('consultorio') }}</small>
      </div>
      <div class="field" [class.has-error]="err('duracionConsultaMin')">
        <label for="d-dur">Minutos por cita</label>
        <input id="d-dur" type="number" min="5" step="5" [value]="duracion()" (input)="duracion.set($any($event.target).value); limpiar('duracionConsultaMin')">
        <small class="field-msg">{{ err('duracionConsultaMin') }}</small>
      </div>

      @if (!editando()) {
        <div class="field" [class.has-error]="err('tipoDocumento')">
          <label for="d-tipo">Tipo de documento</label>
          <select id="d-tipo" (change)="tipoDocumento.set($any($event.target).value); limpiar('tipoDocumento')">
            @for (t of tipos; track t) { <option [value]="t" [selected]="tipoDocumento() === t">{{ t }}</option> }
          </select>
        </div>
        <div class="field" [class.has-error]="err('numeroDocumento')">
          <label for="d-doc">Número de documento</label>
          <input id="d-doc" maxlength="255" inputmode="numeric" [value]="numeroDocumento()" (input)="numeroDocumento.set($any($event.target).value); limpiar('numeroDocumento')">
          <small class="field-msg">{{ err('numeroDocumento') }}</small>
        </div>
        <div class="field span-2" [class.has-error]="err('email')">
          <label for="d-email">Correo para iniciar sesión</label>
          <input id="d-email" type="email" maxlength="255" autocomplete="off" [value]="email()" (input)="email.set($any($event.target).value); limpiar('email')">
          <small class="field-msg">{{ err('email') }}</small>
        </div>
        <div class="field span-2" [class.has-error]="err('passwordProvisional')">
          <label for="d-pass">Contraseña provisional</label>
          <input id="d-pass" type="text" minlength="8" maxlength="72" autocomplete="new-password" placeholder="Mínimo 8 caracteres; el doctor la cambiará"
                 [value]="password()" (input)="password.set($any($event.target).value); limpiar('passwordProvisional')">
          <small class="field-msg">{{ err('passwordProvisional') }}</small>
        </div>
        <div class="field span-2" [class.has-error]="err('certificado')">
          <label for="d-cert">Título o certificado profesional</label>
          <input id="d-cert" type="file" accept=".pdf,.jpg,.jpeg,.png,application/pdf,image/jpeg,image/png" (change)="elegirArchivo($any($event.target).files)">
          <small class="field-msg">{{ err('certificado') || 'PDF, JPG o PNG de máximo 5 MB.' }}</small>
        </div>
      }

      <p class="form-error span-2" role="alert">{{ error() }}</p>
      <div class="form-actions span-2" style="margin-top:0">
        <button type="button" class="btn btn-ghost" (click)="dialog.close()">Cancelar</button>
        <button type="submit" class="btn btn-primary" [class.is-loading]="guardando()" [disabled]="guardando()">
          <span class="btn-label">{{ editando() ? 'Guardar cambios' : 'Crear doctor' }}</span><span class="spinner"></span>
        </button>
      </div>
    </form>`,
  host: { style: 'display: contents' },
})
export class DoctorForm implements OnInit {
  /** Sin id: crear. Con id: editar */
  readonly doctorId = input<string>();
  protected readonly store = inject(AdminStore);
  protected readonly dialog = inject(DialogService);
  private readonly toast = inject(ToastService);

  protected readonly tipos = TIPOS_DOCUMENTO;
  protected readonly editando = computed(() => !!this.doctorId());

  protected readonly nombre = signal('');
  protected readonly especialidadId = signal('');
  protected readonly consultorio = signal('');
  protected readonly duracion = signal('30');
  protected readonly tipoDocumento = signal<TipoDocumento>('CC');
  protected readonly numeroDocumento = signal('');
  protected readonly email = signal('');
  protected readonly password = signal('');
  protected readonly certificado = signal<File | null>(null);

  protected readonly errores = signal<Partial<Record<Campo, string>>>({});
  protected readonly error = signal('');
  protected readonly guardando = signal(false);

  ngOnInit() {
    const d = this.doctorId() ? this.store.doctor(this.doctorId()!) : null;
    if (!d) return;
    this.nombre.set(d.nombre);
    this.especialidadId.set(d.especialidadId);
    this.consultorio.set(d.consultorio);
    this.duracion.set(String(d.duracionConsultaMin));
  }

  protected err(c: Campo) { return this.errores()[c] ?? ''; }

  /** Al escribir en un campo se quita su error: ya no aplica */
  protected limpiar(c: Campo) {
    if (this.errores()[c]) this.errores.update(({ [c]: _, ...resto }) => resto);
  }

  protected elegirArchivo(files: FileList | null) {
    this.certificado.set(files?.[0] ?? null);
    this.errores.update(({ certificado, ...resto }) => resto);
  }

  /** Mismas reglas que los @NotBlank/@Size/@Email del back, para avisar antes de enviar */
  private validar(): Partial<Record<Campo, string>> {
    const e: Partial<Record<Campo, string>> = {};
    if (!this.nombre().trim()) e.nombre = 'Escribe el nombre.';
    if (!this.especialidadId()) e.especialidadId = 'Elige la especialidad.';
    if (!this.consultorio().trim()) e.consultorio = 'Escribe el consultorio.';
    const min = Number(this.duracion());
    if (!Number.isInteger(min) || min <= 0) e.duracionConsultaMin = 'Debe ser un número entero de minutos.';
    if (this.editando()) return e;
    if (!this.numeroDocumento().trim()) e.numeroDocumento = 'Escribe el número de documento.';
    if (!EMAIL_RE.test(this.email().trim())) e.email = 'Escribe un correo válido.';
    const p = this.password();
    if (p.length < 8 || p.length > 72) e.passwordProvisional = 'Debe tener entre 8 y 72 caracteres.';
    const f = this.certificado();
    if (!f) e.certificado = 'Adjunta el certificado.';
    else if (!TIPOS_CERTIFICADO.includes(f.type)) e.certificado = 'Debe ser un PDF, JPG o PNG.';
    else if (f.size > MAX_CERTIFICADO) e.certificado = 'Pesa más de 5 MB.';
    return e;
  }

  protected async guardar(ev: Event) {
    ev.preventDefault();
    const errores = this.validar();
    this.errores.set(errores);
    if (Object.keys(errores).length) {
      this.error.set('Revisa los campos marcados.');
      return;
    }
    this.error.set('');
    const base = {
      nombre: this.nombre().trim(),
      especialidadId: this.especialidadId(),
      duracionConsultaMin: Number(this.duracion()),
      consultorio: this.consultorio().trim(),
    };

    this.guardando.set(true);
    try {
      if (this.editando()) {
        await this.store.actualizarDoctor(this.doctorId()!, base);
        this.toast.show('Datos del doctor actualizados');
      } else {
        await this.store.crearDoctor({
          ...base,
          tipoDocumento: this.tipoDocumento(),
          numeroDocumento: this.numeroDocumento().trim(),
          email: this.email().trim(),
          passwordProvisional: this.password(),
          certificado: this.certificado()!,
        });
        this.toast.show(`${base.nombre} ya puede iniciar sesión con su contraseña provisional`);
      }
      this.dialog.close();
    } catch (e) {
      // 400 con errores por campo, 409 (correo o documento repetido), 413 (archivo grande)
      this.errores.set(erroresDeCampos(e));
      this.error.set(mensajeDeError(e, { 413: 'El certificado pesa más de lo permitido (5 MB).' }));
    } finally {
      this.guardando.set(false);
    }
  }
}
