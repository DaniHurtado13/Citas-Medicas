package com.citas.medicas.service;

import com.citas.medicas.almacenamiento.AlmacenamientoService;
import com.citas.medicas.almacenamiento.ArchivoDescarga;
import com.citas.medicas.almacenamiento.ArchivoValidado;
import com.citas.medicas.dto.request.ActualizarDoctorRequest;
import com.citas.medicas.dto.request.CrearDoctorRequest;
import com.citas.medicas.entity.Doctor;
import com.citas.medicas.entity.Especialidad;
import com.citas.medicas.entity.Rol;
import com.citas.medicas.entity.TipoDocumento;
import com.citas.medicas.entity.Usuario;
import com.citas.medicas.exception.ArchivoInvalidoException;
import com.citas.medicas.exception.RecursoDuplicadoException;
import com.citas.medicas.exception.RecursoNoEncontradoException;
import com.citas.medicas.repository.DoctorRepository;
import com.citas.medicas.repository.EspecialidadRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DoctorServiceTest {

    @Mock
    private DoctorRepository doctorRepository;

    @Mock
    private EspecialidadRepository especialidadRepository;

    @Mock
    private UsuarioService usuarioService;

    @Mock
    private AlmacenamientoService almacenamientoService;

    @Mock
    private CitaService citaService;

    @InjectMocks
    private DoctorService doctorService;

    private final UUID especialidadId = UUID.randomUUID();
    private final UUID doctorId = UUID.randomUUID();

    private final MockMultipartFile archivo =
            new MockMultipartFile("certificado", "titulo.pdf", "application/pdf", "%PDF-1.7".getBytes());

    private final ArchivoValidado certificadoValidado =
            new ArchivoValidado("generado.pdf", "titulo.pdf", "application/pdf", "%PDF-1.7".getBytes());

    private final CrearDoctorRequest crearRequest = new CrearDoctorRequest(
            TipoDocumento.CC, "2000000001", "laura@clinica.com", "provisional123",
            "Laura Gómez", especialidadId, 30, "Consultorio 101", archivo);

    private Especialidad especialidad() {
        Especialidad especialidad = new Especialidad();
        especialidad.setId(especialidadId);
        especialidad.setNombre("Cardiología");
        return especialidad;
    }

    @Test
    void crearDoctorCreaSuUsuarioConRolDoctorYGuardaElCertificado() {
        Especialidad especialidad = especialidad();
        Usuario usuario = new Usuario();
        when(almacenamientoService.validar(archivo)).thenReturn(certificadoValidado);
        when(especialidadRepository.findById(especialidadId)).thenReturn(Optional.of(especialidad));
        when(usuarioService.crearUsuario(TipoDocumento.CC, "2000000001", "laura@clinica.com", "provisional123", Rol.DOCTOR))
                .thenReturn(usuario);

        Doctor doctor = doctorService.crear(crearRequest);

        assertSame(usuario, doctor.getUsuario());
        assertSame(especialidad, doctor.getEspecialidad());
        assertEquals("Laura Gómez", doctor.getNombre());
        assertEquals(30, doctor.getDuracionConsultaMin());
        assertEquals("generado.pdf", doctor.getCertificadoArchivo());
        assertEquals("titulo.pdf", doctor.getCertificadoNombreOriginal());
    }

    @Test
    void crearValidaElArchivoGuardaElDoctorYSoloDespuesEscribeEnDisco() {
        when(almacenamientoService.validar(archivo)).thenReturn(certificadoValidado);
        when(especialidadRepository.findById(especialidadId)).thenReturn(Optional.of(especialidad()));
        when(usuarioService.crearUsuario(any(), any(), any(), any(), any())).thenReturn(new Usuario());

        doctorService.crear(crearRequest);

        InOrder orden = inOrder(almacenamientoService, doctorRepository);
        orden.verify(almacenamientoService).validar(archivo);
        orden.verify(doctorRepository).saveAndFlush(any(Doctor.class));
        orden.verify(almacenamientoService).escribir(certificadoValidado);
    }

    @Test
    void crearConCertificadoInvalidoNoCreaNada() {
        when(almacenamientoService.validar(archivo))
                .thenThrow(new ArchivoInvalidoException("El certificado debe ser un archivo PDF, JPG o PNG"));

        assertThrows(ArchivoInvalidoException.class, () -> doctorService.crear(crearRequest));
        verifyNoInteractions(usuarioService, doctorRepository);
        verify(almacenamientoService, never()).escribir(any());
    }

    @Test
    void crearConEspecialidadInexistenteNoCreaElUsuario() {
        when(almacenamientoService.validar(archivo)).thenReturn(certificadoValidado);
        when(especialidadRepository.findById(especialidadId)).thenReturn(Optional.empty());

        assertThrows(RecursoNoEncontradoException.class, () -> doctorService.crear(crearRequest));
        verifyNoInteractions(usuarioService);
        verify(almacenamientoService, never()).escribir(any());
    }

    @Test
    void crearConCuentaRepetidaNoCreaElDoctorNiEscribeElArchivo() {
        when(almacenamientoService.validar(archivo)).thenReturn(certificadoValidado);
        when(especialidadRepository.findById(especialidadId)).thenReturn(Optional.of(especialidad()));
        when(usuarioService.crearUsuario(any(), any(), any(), any(), any()))
                .thenThrow(new RecursoDuplicadoException("Ya existe una cuenta con ese email"));

        assertThrows(RecursoDuplicadoException.class, () -> doctorService.crear(crearRequest));
        verify(doctorRepository, never()).saveAndFlush(any());
        verify(almacenamientoService, never()).escribir(any());
    }

    @Test
    void obtenerCertificadoDevuelveArchivoNombreOriginalYTipo() {
        Doctor doctor = new Doctor();
        doctor.setCertificadoArchivo("generado.pdf");
        doctor.setCertificadoNombreOriginal("titulo.pdf");
        Resource contenido = new ByteArrayResource("%PDF".getBytes());
        when(doctorRepository.findConEspecialidadById(doctorId)).thenReturn(Optional.of(doctor));
        when(almacenamientoService.cargar("generado.pdf")).thenReturn(contenido);
        when(almacenamientoService.tipoContenido("generado.pdf")).thenReturn("application/pdf");

        ArchivoDescarga descarga = doctorService.obtenerCertificado(doctorId);

        assertSame(contenido, descarga.contenido());
        assertEquals("titulo.pdf", descarga.nombre());
        assertEquals("application/pdf", descarga.tipoContenido());
    }

    @Test
    void obtenerCertificadoDeDoctorRegistradoAntesDeLaReglaDa404() {
        when(doctorRepository.findConEspecialidadById(doctorId)).thenReturn(Optional.of(new Doctor()));

        assertThrows(RecursoNoEncontradoException.class, () -> doctorService.obtenerCertificado(doctorId));
        verify(almacenamientoService, never()).cargar(any());
    }

    @Test
    void actualizarCambiaLosDatosDelDoctor() {
        Doctor doctor = new Doctor();
        doctor.setId(doctorId);
        Especialidad especialidad = especialidad();
        when(doctorRepository.findConEspecialidadById(doctorId)).thenReturn(Optional.of(doctor));
        when(especialidadRepository.findById(especialidadId)).thenReturn(Optional.of(especialidad));
        when(doctorRepository.save(doctor)).thenReturn(doctor);

        Doctor actualizado = doctorService.actualizar(doctorId,
                new ActualizarDoctorRequest("Laura Gómez Ruiz", especialidadId, 20, "Consultorio 202"));

        assertEquals("Laura Gómez Ruiz", actualizado.getNombre());
        assertEquals(20, actualizado.getDuracionConsultaMin());
        assertEquals("Consultorio 202", actualizado.getConsultorio());
        assertSame(especialidad, actualizado.getEspecialidad());
    }

    @Test
    void obtenerDoctorInexistenteLanzaExcepcion() {
        when(doctorRepository.findConEspecialidadById(doctorId)).thenReturn(Optional.empty());

        assertThrows(RecursoNoEncontradoException.class, () -> doctorService.obtener(doctorId));
    }

    private final Pageable pageable = PageRequest.of(0, 20);
    private final Page<Doctor> pagina = new PageImpl<>(List.of(new Doctor()), pageable, 1);

    @Test
    void listarPorDefectoDevuelveSoloActivos() {
        when(doctorRepository.findByUsuarioActivoTrue(pageable)).thenReturn(pagina);

        assertSame(pagina, doctorService.listar(null, false, pageable));
    }

    @Test
    void listarConEspecialidadDevuelveSoloSusActivos() {
        when(doctorRepository.findByEspecialidadIdAndUsuarioActivoTrue(especialidadId, pageable)).thenReturn(pagina);

        assertSame(pagina, doctorService.listar(especialidadId, false, pageable));
    }

    @Test
    void listarIncluyendoInactivosDevuelveTodos() {
        when(doctorRepository.findAllBy(pageable)).thenReturn(pagina);

        assertSame(pagina, doctorService.listar(null, true, pageable));
    }

    @Test
    void listarIncluyendoInactivosConEspecialidadFiltraPorElla() {
        when(doctorRepository.findByEspecialidadId(especialidadId, pageable)).thenReturn(pagina);

        assertSame(pagina, doctorService.listar(especialidadId, true, pageable));
    }

    // ---------- Activar y desactivar ----------

    private Doctor doctorActivo() {
        Usuario usuario = new Usuario();
        usuario.setActivo(true);
        Doctor doctor = new Doctor();
        doctor.setId(doctorId);
        doctor.setUsuario(usuario);
        return doctor;
    }

    @Test
    void desactivarCancelaSusCitasFuturasYDesactivaSuUsuario() {
        Doctor doctor = doctorActivo();
        when(doctorRepository.findConEspecialidadById(doctorId)).thenReturn(Optional.of(doctor));
        when(citaService.cancelarCitasFuturasDelDoctor(doctorId)).thenReturn(3);

        DoctorService.Desactivacion resultado = doctorService.desactivar(doctorId);

        assertFalse(doctor.getUsuario().getActivo());
        assertEquals(1, doctor.getUsuario().getVersionSesion());
        assertEquals(3, resultado.citasCanceladas());
        assertSame(doctor, resultado.doctor());
    }

    @Test
    void desactivarDoctorInexistenteNoCancelaNada() {
        when(doctorRepository.findConEspecialidadById(doctorId)).thenReturn(Optional.empty());

        assertThrows(RecursoNoEncontradoException.class, () -> doctorService.desactivar(doctorId));
        verifyNoInteractions(citaService);
    }

    @Test
    void activarVuelveAHabilitarSuUsuario() {
        Doctor doctor = doctorActivo();
        doctor.getUsuario().setActivo(false);
        when(doctorRepository.findConEspecialidadById(doctorId)).thenReturn(Optional.of(doctor));

        doctorService.activar(doctorId);

        assertTrue(doctor.getUsuario().getActivo());
        verifyNoInteractions(citaService);
    }
}
