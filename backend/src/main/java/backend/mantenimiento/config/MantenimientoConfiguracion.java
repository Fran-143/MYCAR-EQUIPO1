package backend.mantenimiento.config;

import backend.mantenimiento.dao.LecturaKmMantenimientoDao;
import backend.mantenimiento.dao.RegistroMantenimientoDao;
import backend.mantenimiento.dao.VehiculoMantenimientoDao;
import backend.mantenimiento.service.LecturaKmMantenimientoService;
import backend.mantenimiento.service.RegistroMantenimientoService;
import backend.mantenimiento.service.TipoMantenimientoService;
import backend.mantenimiento.service.ValidadorRegistroMantenimiento;
import backend.mantenimiento.service.ValidadorRepuesto;
import backend.mantenimiento.service.VehiculoMantenimientoService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import java.time.Clock;
import java.time.ZoneId;

@Configuration
@EnableTransactionManagement
public class MantenimientoConfiguracion {

    @Bean
    public ValidadorRegistroMantenimiento validadorRegistroMantenimiento() {
        return new ValidadorRegistroMantenimiento(
                Clock.system(ZoneId.of("America/Lima"))
        );
    }

    @Bean
    public ValidadorRepuesto validadorRepuesto() {
        return new ValidadorRepuesto();
    }

    @Bean
    public VehiculoMantenimientoService vehiculoMantenimientoService(
            VehiculoMantenimientoDao dao
    ) {
        return new VehiculoMantenimientoService(dao);
    }

    @Bean
    public LecturaKmMantenimientoService lecturaKmMantenimientoService(
            LecturaKmMantenimientoDao dao
    ) {
        return new LecturaKmMantenimientoService(dao);
    }

    @Bean
    public RegistroMantenimientoService registroMantenimientoService(
            ValidadorRegistroMantenimiento validador,
            ValidadorRepuesto validadorRepuesto,
            VehiculoMantenimientoService vehiculos,
            TipoMantenimientoService tipos,
            LecturaKmMantenimientoService lecturas,
            RegistroMantenimientoDao dao
    ) {
        return new RegistroMantenimientoService(
                validador,
                validadorRepuesto,
                vehiculos,
                tipos,
                lecturas,
                dao
        );
    }
}