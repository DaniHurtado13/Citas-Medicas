import { inject, Injectable, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { DoctoresApi } from '../../core/api/doctores.api';
import { DoctorResponse, EspecialidadResponse } from '../../core/api/dtos';
import { EspecialidadesApi } from '../../core/api/especialidades.api';
import { todasLasPaginas } from '../../core/api/paginas';

/**
 * Especialidades y doctores que ve el paciente. No dependen de quién inició sesión,
 * así que se piden una sola vez y se completan bajo demanda.
 */
@Injectable({ providedIn: 'root' })
export class CatalogoStore {
  private readonly especialidadesApi = inject(EspecialidadesApi);
  private readonly doctoresApi = inject(DoctoresApi);

  readonly especialidades = signal<EspecialidadResponse[]>([]);
  /** Doctores activos: los únicos que aceptan reservas */
  readonly activos = signal<DoctorResponse[]>([]);
  readonly cargado = signal(false);

  /** Todos los doctores conocidos, también los desactivados que aparecen en el historial del paciente */
  private readonly conocidos = signal(new Map<string, DoctorResponse>());
  private carga: Promise<void> | null = null;

  cargar(): Promise<void> {
    this.carga ??= this.pedir().catch((e) => {
      this.carga = null; // permite reintentar
      throw e;
    });
    return this.carga;
  }

  doctor(id: string): DoctorResponse | undefined {
    return this.conocidos().get(id);
  }

  /** Pide los doctores que falten, uno por uno (p. ej. uno ya desactivado que atendió al paciente) */
  async completar(ids: string[]): Promise<void> {
    const faltan = [...new Set(ids)].filter((id) => !this.conocidos().has(id));
    if (!faltan.length) return;
    const docs = await Promise.all(faltan.map((id) => firstValueFrom(this.doctoresApi.obtener(id)).catch(() => null)));
    this.recordar(docs.filter((d): d is DoctorResponse => d !== null));
  }

  private async pedir(): Promise<void> {
    const [especialidades, doctores] = await Promise.all([
      firstValueFrom(this.especialidadesApi.listar()),
      todasLasPaginas((p) => this.doctoresApi.listar(p)),
    ]);
    this.especialidades.set(especialidades);
    this.activos.set(doctores);
    this.recordar(doctores);
    this.cargado.set(true);
  }

  private recordar(doctores: DoctorResponse[]) {
    this.conocidos.update((m) => {
      const nuevo = new Map(m);
      doctores.forEach((d) => nuevo.set(d.id, d));
      return nuevo;
    });
  }
}
