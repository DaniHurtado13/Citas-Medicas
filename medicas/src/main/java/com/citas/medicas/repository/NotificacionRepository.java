package com.citas.medicas.repository;

import com.citas.medicas.entity.EstadoNotificacion;
import com.citas.medicas.entity.Notificacion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface NotificacionRepository extends JpaRepository<Notificacion, UUID> {

    List<Notificacion> findByEstadoAndIntentosLessThan(EstadoNotificacion estado, int intentos);
}
