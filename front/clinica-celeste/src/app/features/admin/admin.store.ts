import { inject, Injectable, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { DoctoresApi } from '../../core/api/doctores.api';
import { ActualizarDoctorRequest, CrearDoctorRequest, DoctorResponse, EspecialidadRequest, EspecialidadResponse } from '../../core/api/dtos';
import { EspecialidadesApi } from '../../core/api/especialidades.api';
import { todasLasPaginas } from '../../core/api/paginas';
import { mensajeDeError } from '../../core/http/errores';

const porNombre = <T extends { nombre: string }>(a: T, b: T) => a.nombre.localeCompare(b.nombre, 'es');

/**
 * Doctores (también los desactivados) y especialidades, para las vistas del admin.
 * Cada cambio se aplica primero en el back; si responde bien, se refleja aquí sin volver a pedir todo.
 */
@Injectable({ providedIn: 'root' })
export class AdminStore {
  private readonly doctoresApi = inject(DoctoresApi);
  private readonly especialidadesApi = inject(EspecialidadesApi);

  readonly doctores = signal<DoctorResponse[]>([]);
  readonly especialidades = signal<EspecialidadResponse[]>([]);
  readonly cargado = signal(false);
  readonly error = signal('');
  private carga: Promise<void> | null = null;

  cargar(): Promise<void> {
    this.carga ??= this.pedir().finally(() => (this.carga = null));
    return this.carga;
  }

  doctor(id: string) {
    return this.doctores().find((d) => d.id === id);
  }

  // ---------- Doctores ----------
  async crearDoctor(datos: CrearDoctorRequest): Promise<DoctorResponse> {
    const d = await firstValueFrom(this.doctoresApi.crear(datos));
    this.doctores.update((l) => [...l, d].sort(porNombre));
    return d;
  }

  async actualizarDoctor(id: string, body: ActualizarDoctorRequest): Promise<DoctorResponse> {
    const d = await firstValueFrom(this.doctoresApi.actualizar(id, body));
    this.reemplazarDoctor(d);
    return d;
  }

  /** Devuelve cuántas citas futuras canceló el back */
  async desactivar(id: string): Promise<number> {
    const r = await firstValueFrom(this.doctoresApi.desactivar(id));
    this.reemplazarDoctor(r.doctor);
    return r.citasCanceladas;
  }

  async activar(id: string): Promise<void> {
    this.reemplazarDoctor(await firstValueFrom(this.doctoresApi.activar(id)));
  }

  certificado(id: string): Promise<Blob> {
    return firstValueFrom(this.doctoresApi.certificado(id));
  }

  // ---------- Especialidades ----------
  async crearEspecialidad(body: EspecialidadRequest): Promise<void> {
    const e = await firstValueFrom(this.especialidadesApi.crear(body));
    this.especialidades.update((l) => [...l, e].sort(porNombre));
  }

  async actualizarEspecialidad(id: string, body: EspecialidadRequest): Promise<void> {
    const e = await firstValueFrom(this.especialidadesApi.actualizar(id, body));
    this.especialidades.update((l) => l.map((x) => (x.id === id ? e : x)).sort(porNombre));
    // Los doctores guardan el nombre de su especialidad: se actualiza también ahí
    this.doctores.update((l) => l.map((d) => (d.especialidadId === id ? { ...d, especialidadNombre: e.nombre } : d)));
  }

  async eliminarEspecialidad(id: string): Promise<void> {
    await firstValueFrom(this.especialidadesApi.eliminar(id));
    this.especialidades.update((l) => l.filter((x) => x.id !== id));
  }

  private reemplazarDoctor(d: DoctorResponse) {
    this.doctores.update((l) => l.map((x) => (x.id === d.id ? d : x)));
  }

  private async pedir(): Promise<void> {
    try {
      const [doctores, especialidades] = await Promise.all([
        todasLasPaginas((p) => this.doctoresApi.listar({ ...p, incluirInactivos: true })),
        firstValueFrom(this.especialidadesApi.listar()),
      ]);
      this.doctores.set(doctores.sort(porNombre));
      this.especialidades.set(especialidades.sort(porNombre));
      this.error.set('');
    } catch (e) {
      this.error.set(mensajeDeError(e));
    } finally {
      this.cargado.set(true);
    }
  }
}
