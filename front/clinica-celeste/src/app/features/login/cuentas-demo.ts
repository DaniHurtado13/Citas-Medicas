import { Rol } from '../../core/models';
import { IconName } from '../../shared/icons';

/**
 * Botones de demo del login: entran con POST /api/auth/demo, sin contraseña.
 * El back elige la cuenta de cada rol (las marcadas como demo), así el front no guarda credenciales.
 */
export const CUENTAS_DEMO: { rol: Rol; accion: string; detalle: string; icono: IconName }[] = [
  { rol: 'PACIENTE', accion: 'Entrar como paciente', detalle: 'Reserva, reprograma y cancela citas', icono: 'calendar' },
  { rol: 'DOCTOR', accion: 'Entrar como doctor', detalle: 'Agenda, horario de atención y bloqueos', icono: 'steth' },
  { rol: 'ADMIN', accion: 'Entrar como administrador', detalle: 'Doctores y especialidades', icono: 'clipboard' },
];
