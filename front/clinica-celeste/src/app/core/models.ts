/* ==========================================================
   Tipos del dominio: los enums del backend (Citas/medicas/entity) y sus etiquetas en pantalla.
   Las formas de los datos que viajan por la red están en api/dtos.ts.
   ========================================================== */

export type Rol = 'ADMIN' | 'DOCTOR' | 'PACIENTE';

export type EstadoCita = 'PENDIENTE' | 'CONFIRMADA' | 'COMPLETADA' | 'CANCELADA' | 'NO_ASISTIO';

/** java.time.DayOfWeek */
export type DiaSemana = 'MONDAY' | 'TUESDAY' | 'WEDNESDAY' | 'THURSDAY' | 'FRIDAY' | 'SATURDAY' | 'SUNDAY';

export type TipoDocumento = 'CC' | 'CE' | 'TI' | 'PASAPORTE';

export const TIPOS_DOCUMENTO: TipoDocumento[] = ['CC', 'CE', 'TI', 'PASAPORTE'];

export const ESTADOS: Record<EstadoCita, string> = {
  CONFIRMADA: 'Confirmada',
  PENDIENTE: 'Por confirmar',
  COMPLETADA: 'Atendida',
  CANCELADA: 'Cancelada',
  NO_ASISTIO: 'No asistió',
};

/** Valor del atributo data-s que usa el CSS para colorear un estado */
export const estadoKey = (e: EstadoCita | string) => e.toLowerCase();
