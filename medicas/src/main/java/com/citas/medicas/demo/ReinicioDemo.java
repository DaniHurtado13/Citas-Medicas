package com.citas.medicas.demo;

import com.citas.medicas.almacenamiento.AlmacenamientoService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;

@Slf4j
@Component
@ConditionalOnProperty(name = "citas.demo.habilitada", havingValue = "true")
public class ReinicioDemo implements ApplicationRunner {

    private static final Resource SCRIPT = new ClassPathResource("demo/clinica-demo.sql");

    private final DataSource dataSource;
    private final TransactionTemplate transaccion;
    private final AlmacenamientoService almacenamiento;

    public ReinicioDemo(DataSource dataSource, TransactionTemplate transaccion, AlmacenamientoService almacenamiento) {
        this.dataSource = dataSource;
        this.transaccion = transaccion;
        this.almacenamiento = almacenamiento;
    }

    @Override
    public void run(ApplicationArguments args) {
        reiniciar();
    }

    @Scheduled(cron = "${citas.demo.reinicio.cron}", zone = "${citas.zona-horaria}")
    public void reiniciar() {
        // Todo el script en una transacción: si algo falla, la base queda como estaba
        transaccion.executeWithoutResult(estado ->
                new ResourceDatabasePopulator(false, false, "UTF-8", SCRIPT).execute(dataSource));
        // Después del commit: ningún doctor de la clínica de demo tiene certificado
        int certificados = almacenamiento.vaciar();
        log.info("Demo reiniciada: clínica de ejemplo recreada y {} certificados borrados", certificados);
    }
}
