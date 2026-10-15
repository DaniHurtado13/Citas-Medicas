import { inject, Injectable } from '@angular/core';
import { Router } from '@angular/router';
import { CitaResponse } from '../../core/api/dtos';
import { mensajeDeError } from '../../core/http/errores';
import { DialogService, ToastService } from '../../core/ui.service';
import { VistaStateService } from '../../core/vista-state.service';
import { fechaDe, fmtHora, horaDe, longDate, startOfWeek, TODAY } from '../../core/fechas';
import { CitasPacienteStore } from './citas-paciente.store';

/** Horas mínimas de anticipación para cancelar o reprogramar. Debe coincidir con citas.cancelacion.horas-minimas del back */
export const VENTANA_CAMBIOS_HORAS = 2;

/** "2026-10-19T09:00:00" sin zona se interpreta como hora local, igual que en el back (hora de la clínica) */
const faltaMasDeLaVentana = (c: CitaResponse) =>
  new Date(c.fechaHora).getTime() > Date.now() + VENTANA_CAMBIOS_HORAS * 3600000;

/** Acciones del paciente sobre una cita, compartidas por Inicio y Mis citas. Imitan las reglas de CitaService.java */
@Injectable({ providedIn: 'root' })
export class AccionesCita {
  private readonly store = inject(CitasPacienteStore);
  private readonly dialog = inject(DialogService);
  private readonly toast = inject(ToastService);
  private readonly vista = inject(VistaStateService);
  private readonly router = inject(Router);

  /** El paciente solo cancela citas confirmadas (una pendiente aún no la ha aceptado el doctor) */
  puedeCancelar(c: CitaResponse) {
    return c.estado === 'CONFIRMADA' && faltaMasDeLaVentana(c);
  }

  /** Reprogramar sí se permite con la cita pendiente o confirmada */
  puedeReprogramar(c: CitaResponse) {
    return (c.estado === 'PENDIENTE' || c.estado === 'CONFIRMADA') && faltaMasDeLaVentana(c);
  }

  motivoBloqueo(c: CitaResponse, accion: 'cancelar' | 'reprogramar'): string {
    if (accion === 'cancelar' && c.estado === 'PENDIENTE') return 'Podrás cancelarla cuando el doctor la confirme';
    return `Solo se puede cambiar con más de ${VENTANA_CAMBIOS_HORAS} horas de anticipación`;
  }

  /** Reprogramar conserva el doctor y el motivo: en Agendar solo se elige el nuevo horario */
  reprogramar(c: CitaResponse) {
    this.vista.reserva.set({ espId: '', docId: c.doctorId, weekStart: startOfWeek(TODAY), slot: null, motivo: c.motivoConsulta, reprog: c.id });
    this.router.navigateByUrl('/paciente/agendar');
  }

  async cancelar(c: CitaResponse) {
    const ok = await this.dialog.confirm({
      title: '¿Cancelar esta cita?',
      body: `Tu cita con ${c.doctorNombre} del ${longDate(fechaDe(c.fechaHora)).toLowerCase()} a las ${fmtHora(horaDe(c.fechaHora))} quedará libre para otro paciente.`,
      confirmText: 'Cancelar cita', cancelText: 'Mantener cita', danger: true, tone: 'cancelada', ic: 'x',
    });
    if (!ok) return;
    try {
      await this.store.cancelar(c.id);
      this.toast.show('Cita cancelada');
    } catch (e) {
      this.toast.show(mensajeDeError(e), 'alert', true);
    }
  }
}
