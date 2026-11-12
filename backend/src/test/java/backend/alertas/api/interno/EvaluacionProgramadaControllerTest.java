package backend.alertas.api.interno;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import backend.alertas.seguridad.CronSecretAuthenticator;
import backend.alertas.seguridad.CronSecretFilter;
import backend.alertas.seguridad.CronSecretProperties;
import backend.alertas.service.EvaluacionProgramadaAlertasService;
import backend.alertas.service.EvaluacionProgramadaAlertasService.Modo;
import backend.alertas.service.EvaluacionProgramadaAlertasService.Resultado;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class EvaluacionProgramadaControllerTest {
    MockMvc mvc;
    EvaluacionProgramadaAlertasService servicio;
    CronSecretProperties props;

    @BeforeEach
    void preparar() {
        servicio = mock(EvaluacionProgramadaAlertasService.class);
        props = new CronSecretProperties();
        props.setSecret("esperado");
        mvc = MockMvcBuilders.standaloneSetup(new EvaluacionProgramadaController(servicio))
                .addFilters(new CronSecretFilter(new CronSecretAuthenticator(props)))
                .build();
    }

    @Test
    void sinSecretoRechaza401() throws Exception {
        mvc.perform(post("/api/v1/interno/alertas/evaluar-programadas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"modo\":\"SIMULAR\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void secretoInvalidoRechaza401() throws Exception {
        mvc.perform(post("/api/v1/interno/alertas/evaluar-programadas")
                        .header("X-MyCar-Cron-Secret", "malo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"modo\":\"SIMULAR\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void secretoValidoEjecutaSimular() throws Exception {
        when(servicio.evaluar(Modo.SIMULAR)).thenReturn(new Resultado(false, "SIMULAR", 4, 0, 0));
        mvc.perform(post("/api/v1/interno/alertas/evaluar-programadas")
                        .header("X-MyCar-Cron-Secret", "esperado")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"modo\":\"SIMULAR\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.modo").value("SIMULAR"))
                .andExpect(jsonPath("$.vehiculos").value(4))
                .andExpect(jsonPath("$.omitidoEnCurso").value(false));
    }

    @Test
    void omitidoEnCursoResponde200() throws Exception {
        when(servicio.evaluar(any())).thenReturn(new Resultado(true, "COMPLETO", 0, 0, 0));
        mvc.perform(post("/api/v1/interno/alertas/evaluar-programadas")
                        .header("X-MyCar-Cron-Secret", "esperado")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.omitidoEnCurso").value(true));
    }
}
