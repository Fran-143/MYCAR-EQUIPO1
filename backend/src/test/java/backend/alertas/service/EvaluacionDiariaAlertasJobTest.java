package backend.alertas.service;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import backend.alertas.service.EvaluacionProgramadaAlertasService.Modo;
import org.junit.jupiter.api.Test;

class EvaluacionDiariaAlertasJobTest {

    @Test
    void delegaEnServicioCompleto() {
        EvaluacionProgramadaAlertasService servicio = mock(EvaluacionProgramadaAlertasService.class);
        when(servicio.evaluar(Modo.COMPLETO))
                .thenReturn(new EvaluacionProgramadaAlertasService.Resultado(false, "COMPLETO", 1, 0, 0));
        new EvaluacionDiariaAlertasJob(servicio).evaluarDiario();
        verify(servicio).evaluar(Modo.COMPLETO);
    }
}
