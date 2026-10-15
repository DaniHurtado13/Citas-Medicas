/* ==========================================================
   DTOs: espejo exacto de los records del backend (medicas/dto).
   Es lo que viaja por la red; las vistas no deberían inventar campos.
   Formatos tal como llegan en el JSON:
     UUID          → '22222222-2222-2222-2222-222222222202'
     LocalDate     → '2026-10-14'
     LocalDateTime → '2026-10-14T09:30:00'
     LocalTime     → '08:00:00'
   ========================================================== */
import { DiaSemana, EstadoCita, Rol, TipoDocumento } from '../models';

type Uuid = string;
type FechaISO = string;
type FechaHoraISO = string;
type HoraISO = string;

/* ---------- Respuestas ---------- */

export interface LoginResponse {
  token: string;
  tipo: 'Bearer';
  expiraEnSegundos: number;
  rol: Rol;
}

/** GET /api/auth/yo — "quién soy"; lo que no aplica al rol llega en null */
export interface UsuarioActualResponse {
  id: Uuid;
  email: string;
  rol: Rol;
  nombre: string | null; // el admin no tiene nombre
  pacienteId: Uuid | null;
  doctorId: Uuid | null;
}

export interface PacienteResponse {
  id: Uuid;
  nombre: string;
  email: string;
  telefono: string;
  fechaNacimiento: FechaISO;
}

export interface EspecialidadResponse {
  id: Uuid;
  nombre: string;
  descripcion: string;
}

export interface DoctorResponse {
  id: Uuid;
  nombre: string;
  especialidadId: Uuid;
  especialidadNombre: string;
  duracionConsultaMin: number;
  consultorio: string;
  activo: boolean;
}

export interface DesactivacionDoctorResponse {
  doctor: DoctorResponse;
  citasCanceladas: number;
}

export interface HorarioResponse {
  id: Uuid;
  diaSemana: DiaSemana;
  horaInicio: HoraISO;
  horaFin: HoraISO;
}

export interface BloqueoResponse {
  id: Uuid;
  fechaInicio: FechaHoraISO;
  fechaFin: FechaHoraISO;
  motivo: string | null;
}

export interface CitaResponse {
  id: Uuid;
  pacienteId: Uuid;
  pacienteNombre: string;
  doctorId: Uuid;
  doctorNombre: string;
  citaOrigenId: Uuid | null;
  fechaHora: FechaHoraISO;
  estado: EstadoCita;
  motivoConsulta: string;
}

/** Página de Spring traducida por PaginaResponse.java */
export interface PaginaResponse<T> {
  contenido: T[];
  pagina: number;
  tamano: number;
  totalElementos: number;
  totalPaginas: number;
}

/** Errores del back (RFC 9457). `errores` solo llega en las validaciones: { campo: mensaje } */
export interface ProblemDetail {
  title?: string;
  status: number;
  detail?: string;
  instance?: string;
  errores?: Record<string, string>;
}

/* ---------- Peticiones ---------- */

export interface LoginRequest {
  email: string;
  password: string;
}

export interface DemoLoginRequest {
  rol: Rol;
}

export interface RegistroPacienteRequest {
  nombre: string;
  tipoDocumento: TipoDocumento;
  numeroDocumento: string;
  email: string;
  password: string; // 8 a 72 caracteres
  telefono: string; // máximo 10
  fechaNacimiento: FechaISO; // en el pasado
}

export interface VerificarEmailRequest { token: string; }
export interface ReenviarVerificacionRequest { email: string; }
export interface OlvidePasswordRequest { email: string; }
export interface RestablecerPasswordRequest { token: string; nuevaContrasena: string; }
export interface CambiarPasswordRequest { contrasenaActual: string; nuevaContrasena: string; }

export interface ReservarCitaRequest {
  doctorId: Uuid;
  fechaHora: FechaHoraISO; // en el futuro y dentro de la disponibilidad
  motivoConsulta: string; // máximo 255
}

/** Reprogramar conserva el doctor y el motivo: solo cambia la hora */
export interface ReprogramarCitaRequest { nuevaFechaHora: FechaHoraISO; }

export interface HorarioRequest {
  diaSemana: DiaSemana;
  horaInicio: HoraISO;
  horaFin: HoraISO;
}

export interface BloqueoRequest {
  fechaInicio: FechaHoraISO;
  fechaFin: FechaHoraISO;
  motivo?: string;
}

export interface EspecialidadRequest {
  nombre: string;
  descripcion: string; // máximo 500
}

export interface ActualizarDoctorRequest {
  nombre: string;
  especialidadId: Uuid;
  duracionConsultaMin: number;
  consultorio: string;
}

/** Se envía como multipart/form-data (FormData), no como JSON */
export interface CrearDoctorRequest extends ActualizarDoctorRequest {
  tipoDocumento: TipoDocumento;
  numeroDocumento: string;
  email: string;
  passwordProvisional: string; // 8 a 72 caracteres
  certificado: File; // PDF, JPG o PNG, máximo 5 MB
}

/** Parámetros de paginación de Spring: ?page=0&size=10&sort=fechaHora,desc */
export interface Paginacion {
  page?: number;
  size?: number;
  sort?: string;
}
