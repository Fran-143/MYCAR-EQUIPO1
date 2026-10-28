package backend.alertas.programacion;

import backend.alertas.programacion.dao.ProgramacionAlertaJdbcDao;
import backend.alertas.programacion.model.ProgramacionAlerta.*;
import backend.alertas.programacion.service.ProgramacionAlertaService;
import backend.autenticacion.LoginCommand;
import backend.autenticacion.SessionAuthentication;
import backend.cierresesion.CloseSession;
import backend.registro.RegisterAccount;
import backend.registro.RegistrationCommand;
import backend.vehiculos.dto.DatosRegistroVehiculo;
import backend.vehiculos.service.RegistroVehiculoService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.security.SecureRandom;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@EnabledIfSystemProperty(named="mycar.rf22.tests",matches="true")
@SpringBootTest(properties={"spring.config.import=optional:classpath:rf22-sin-env.properties",
    "mycar.rf22.api-habilitada=true","mycar.registro.privacidad.version-vigente=FIXTURE-NO-LEGAL",
    "logging.level.org.springframework.boot.autoconfigure.security=ERROR"})
@AutoConfigureMockMvc(print=MockMvcPrint.NONE)
class ProgramacionAlertaPostgresTest {
    @Autowired MockMvc mvc; @Autowired JdbcTemplate jdbc; @Autowired ObjectMapper json;
    @Autowired RegisterAccount registro; @Autowired SessionAuthentication sesiones; @Autowired CloseSession cierre;
    @Autowired RegistroVehiculoService vehiculos; @Autowired ProgramacionAlertaService servicio;
    @Autowired backend.mantenimiento.service.RegistroMantenimientoService mantenimientos;
    @Autowired PlatformTransactionManager transacciones;
    @MockitoSpyBean ProgramacionAlertaJdbcDao persistencia;
    long usuario,vehiculo,tipo; SessionAuthentication.Issued sesion;
    static final String PASSWORD="FixturePass1!";
    @DynamicPropertySource static void base(DynamicPropertyRegistry p) {
        String url=System.getenv("MYCAR_RF22_TEST_URL");
        if(url==null || !url.matches("jdbc:postgresql://127\\.0\\.0\\.1:\\d+/mycar_mvp_test_rf22_[a-f0-9]{8}"))
            throw new IllegalStateException("RF22 solo permite una base temporal aislada");
        p.add("spring.datasource.url",()->url);
        p.add("spring.datasource.username",()->System.getenv("MYCAR_RF22_TEST_USER"));
        p.add("spring.datasource.password",()->System.getenv("MYCAR_RF22_TEST_PASSWORD"));
        byte[] clave=new byte[32];new SecureRandom().nextBytes(clave);
        String secreta=Base64.getEncoder().encodeToString(clave);
        p.add("mycar.auth.jwt-secret-base64",()->secreta);p.add("mycar.auth.access-ttl",()->"PT5M");p.add("mycar.auth.session-ttl",()->"P1D");
    }
    @BeforeEach void preparar() {
        assertTrue(jdbc.queryForObject("SELECT current_database()",String.class).matches("mycar_mvp_test_rf22_[a-f0-9]{8}"));
        usuario=cuenta();sesion=sesiones.login(new LoginCommand(correo(usuario),PASSWORD,UUID.randomUUID()));
        long motor=jdbc.queryForObject("SELECT id_tipo_motor FROM mycar.tipo_motor WHERE codigo='ELECTRICO'",Long.class);
        var creado=vehiculos.registrar(usuario,new DatosRegistroVehiculo(UUID.randomUUID(),"Toyota","Corolla","PARTICULAR",
            "R2R"+String.format("%03d",usuario%1000),2020,"Azul",motor,null,50000,null,null,null,null,null,null,null));
        vehiculo=creado.idVehiculo();tipo=tipo("MANTENIMIENTO_GENERAL");
    }
    @AfterEach void limpiarEspia() {
        ProgramacionAlertaJdbcDao objetivo=AopTestUtils.getUltimateTargetObject(persistencia);
        reset(objetivo);
    }
    long cuenta() {
        String email="rf22-"+UUID.randomUUID()+"@example.test";
        return registro.register(new RegistrationCommand("Prueba","Alerta",email,LocalDate.of(1995,1,1),PASSWORD,PASSWORD,true,"FIXTURE-NO-LEGAL"));
    }
    String correo(long u) {return jdbc.queryForObject("SELECT correo FROM mycar.usuario WHERE id_usuario=?",String.class,u);}
    long tipo(String codigo) {return jdbc.queryForObject("SELECT id_tipo_mantenimiento FROM mycar.tipo_mantenimiento WHERE codigo=?",Long.class,codigo);}
    Configuracion tiempo() {return new Configuracion(vehiculo,tipo,null,Criterio.POR_TIEMPO,Unidad.MESES,2,null,null);}
    Configuracion km() {return new Configuracion(vehiculo,tipo,null,Criterio.POR_KILOMETRAJE,null,null,5000,null);}
    Solicitud solicitud(Configuracion c) {return new Solicitud(UUID.randomUUID(),servicio.revisar(usuario,c));}
    long contar(String tabla) {return jdbc.queryForObject("SELECT count(*) FROM mycar."+tabla+" WHERE id_vehiculo=?",Long.class,vehiculo);}
    String bearer() {return "Bearer "+sesion.accessToken();}
    String postAlerta(Solicitud s,int codigo) throws Exception {
        return mvc.perform(post("/api/v1/rf22/alertas").header("Authorization",bearer()).contentType("application/json").content(json.writeValueAsBytes(s)))
            .andExpect(status().is(codigo)).andReturn().getResponse().getContentAsString();
    }
    @Test void loginHttpRealYContextoAutorizadoSinEscrituras() throws Exception {
        var token=json.readTree(mvc.perform(post("/api/v1/auth/login").contentType("application/json").content(json.writeValueAsBytes(Map.of("correo",correo(usuario),"contrasena",PASSWORD,"idInstalacion",UUID.randomUUID()))))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("accessToken").asText();
        mvc.perform(get("/api/v1/rf22/contexto").header("Authorization","Bearer "+token).param("idUsuario","99999"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.vehiculos[0].idVehiculo").value(vehiculo)).andExpect(header().string("Cache-Control","no-store"));
        assertEquals(0,contar("alerta"));
    }
    @Test void cp01GuardaDesdeRf08UnaAlertaHistorialV0YRespuestaAtomicos() throws Exception {
        var s=solicitud(km());var r=json.readValue(postAlerta(s,201),Resultado.class);
        assertEquals(55000,r.kmObjetivo());assertEquals(1,contar("alerta"));assertTrue(r.eventoCreacionRegistrado());
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM mycar.historial_alerta WHERE id_alerta=? AND version_destino=0 AND estado_anterior IS NULL",Long.class,r.idAlerta()));
        assertEquals(0,jdbc.queryForObject("SELECT version_fila FROM mycar.alerta WHERE id_alerta=?",Integer.class,r.idAlerta()));
        assertEquals("CONFIRMADA",jdbc.queryForObject("SELECT estado FROM mycar.operacion_idempotente WHERE id_operacion=?",String.class,s.idOperacion()));
        assertEquals(1,contar("lectura_kilometraje"));
        assertEquals(r,json.readValue(postAlerta(s,201),Resultado.class));
        mvc.perform(get("/api/v1/rf22/operaciones/"+s.idOperacion()).header("Authorization",bearer())).andExpect(status().isOk()).andExpect(jsonPath("$.resultado.idAlerta").value(r.idAlerta()));
    }
    @Test void cp02MigracionPermiteTiempoSinLecturaPeroNoKmNiAmbos() {
        long sinLectura=jdbc.queryForObject("""
            INSERT INTO mycar.vehiculo(id_usuario,placa,marca,modelo,uso_vehiculo_codigo,anio_fabricacion,color,id_tipo_motor)
            SELECT ?,?,'Toyota','Corolla','PARTICULAR',2020,'Azul',id_tipo_motor FROM mycar.tipo_motor WHERE codigo='ELECTRICO' RETURNING id_vehiculo
            """,Long.class,usuario,"S2S"+String.format("%03d",usuario%1000));
        var c=new Configuracion(sinLectura,tipo,null,Criterio.POR_TIEMPO,Unidad.SEMANAS,1,null,null);
        var r=servicio.confirmar(usuario,solicitud(c));assertNull(r.kmObjetivo());
        assertThrows(RuntimeException.class,()->servicio.revisar(usuario,new Configuracion(sinLectura,tipo,null,Criterio.POR_KILOMETRAJE,null,null,5000,null)));
        assertThrows(RuntimeException.class,()->servicio.revisar(usuario,new Configuracion(sinLectura,tipo,null,Criterio.AMBOS,Unidad.MESES,1,5000,null)));
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM mycar.lectura_kilometraje WHERE id_vehiculo=?",Long.class,sinLectura));
    }
    @Test void ambosProduceUnaSolaAlertaConDosObjetivos() {
        var c=new Configuracion(vehiculo,tipo,null,Criterio.AMBOS,Unidad.SEMANAS,1,5000,null);
        var r=servicio.confirmar(usuario,solicitud(c));assertEquals("PENDIENTE",r.estadoInicial());assertNotNull(r.fechaObjetivo());assertEquals(55000,r.kmObjetivo());assertEquals(1,contar("alerta"));
    }
    @Test void cp03DecimalesNoSeTruncanEnApi() throws Exception {
        String configuracion=json.writeValueAsString(km()).replace("5000","1.5");
        mvc.perform(post("/api/v1/rf22/revisiones").header("Authorization",bearer()).contentType("application/json").content(configuracion)).andExpect(status().isBadRequest());
        assertEquals(0,contar("alerta"));
    }
    @Test void cp04OtroEquivalenteNormalizadoPreservaTextoYNoDuplica() {
        long otro=tipo("OTRO");
        var a=new Configuracion(vehiculo,otro,"  REVISIÓN de correa  ",Criterio.POR_TIEMPO,Unidad.MESES,2,null,null);
        var b=new Configuracion(vehiculo,otro,"revisión  de CORREA",Criterio.POR_TIEMPO,Unidad.MESES,2,null,null);
        var sa=solicitud(a);var sb=solicitud(b);var r=servicio.confirmar(usuario,sa);
        assertThrows(RuntimeException.class,()->servicio.confirmar(usuario,sb));assertEquals(1,contar("alerta"));
        assertEquals(a.servicioOtro(),jdbc.queryForObject("SELECT servicio_otro FROM mycar.alerta WHERE id_alerta=?",String.class,r.idAlerta()));
    }
    @Test void cp04DosAltasConcurrentesEquivalentesSoloUnaConHistorial() throws Exception {
        var a=solicitud(km());var b=new Solicitud(UUID.randomUUID(),a.revision());
        var pool=Executors.newFixedThreadPool(2);var inicio=new CountDownLatch(1);
        try {
            Callable<Boolean> ca=()->{inicio.await();try{servicio.confirmar(usuario,a);return true;}catch(RuntimeException e){return false;}};
            Callable<Boolean> cb=()->{inicio.await();try{servicio.confirmar(usuario,b);return true;}catch(RuntimeException e){return false;}};
            var fa=pool.submit(ca);var fb=pool.submit(cb);inicio.countDown();
            assertNotEquals(fa.get(20,TimeUnit.SECONDS),fb.get(20,TimeUnit.SECONDS));
            assertEquals(1,contar("alerta"));
            assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM mycar.historial_alerta h JOIN mycar.alerta a USING(id_alerta) WHERE a.id_vehiculo=?",Long.class,vehiculo));
        } finally {pool.shutdownNow();}
    }
    @Test void mismaOperacionConcurrenteORespuestaPerdidaRecuperaMismoResultado() throws Exception {
        var s=solicitud(km());var pool=Executors.newFixedThreadPool(2);var inicio=new CountDownLatch(1);
        try {
            Callable<Resultado> tarea=()->{inicio.await();return servicio.confirmar(usuario,s);};
            var a=pool.submit(tarea);var b=pool.submit(tarea);inicio.countDown();
            assertEquals(a.get(20,TimeUnit.SECONDS),b.get(20,TimeUnit.SECONDS));assertEquals(1,contar("alerta"));
            assertEquals(a.get(),servicio.consultar(usuario,s.idOperacion()).resultado());
        }finally{pool.shutdownNow();}
    }
    @Test void mismaClaveConOtroContenidoNoSeAcepta() {
        var s=solicitud(km());servicio.confirmar(usuario,s);
        var c=new Configuracion(vehiculo,tipo,null,Criterio.POR_KILOMETRAJE,null,null,6000,null);
        assertThrows(RuntimeException.class,()->servicio.confirmar(usuario,new Solicitud(s.idOperacion(),servicio.revisar(usuario,c))));assertEquals(1,contar("alerta"));
    }
    @Test void cp05LecturaNuevaRecalculaYExigeConfirmacion() throws Exception {
        var s=solicitud(km());
        new TransactionTemplate(transacciones).execute(status->{
            UUID op=UUID.randomUUID();
            jdbc.update("INSERT INTO mycar.operacion_idempotente(id_operacion,id_usuario,tipo_operacion,hash_solicitud) VALUES (?,?,'ACTUALIZAR_KM',?)",op,usuario,"d".repeat(64));
            jdbc.update("INSERT INTO mycar.lectura_kilometraje(id_vehiculo,valor_km,origen,id_operacion) VALUES (?,50200,'ACTUALIZACION',?)",vehiculo,op);
            jdbc.update("UPDATE mycar.operacion_idempotente SET estado='CONFIRMADA',respuesta_json='{}' WHERE id_operacion=?",op);return null;
        });
        var error=json.readTree(postAlerta(s,409));assertEquals("REVISION_CAMBIO",error.get("codigo").asText());assertEquals(55200,error.at("/revision/objetivos/kmObjetivo").asInt());assertEquals(0,contar("alerta"));
    }
    @Test void cp07OtraCuentaOVehiculoInactivoNoPuedeProgramar() throws Exception {
        long otra=cuenta();var otroToken=sesiones.login(new LoginCommand(correo(otra),PASSWORD,UUID.randomUUID()));
        mvc.perform(post("/api/v1/rf22/revisiones").header("Authorization","Bearer "+otroToken.accessToken()).contentType("application/json").content(json.writeValueAsBytes(km()))).andExpect(status().isNotFound());
        var s=solicitud(km());jdbc.update("UPDATE mycar.vehiculo SET estado_vehiculo='INACTIVO' WHERE id_vehiculo=?",vehiculo);postAlerta(s,404);assertEquals(0,contar("alerta"));
    }
    @Test void cp06ReferenciaDeRf17AnuladaSeRetiraYExigeReconfirmacion() throws Exception {
        long id=mantenimientos.registrar(usuario,new backend.mantenimiento.model.SolicitudRegistroMantenimiento(UUID.randomUUID(),vehiculo,
            new backend.mantenimiento.model.DatosRegistroMantenimiento("MANTENIMIENTO_GENERAL",LocalDate.now(ZoneId.of("America/Lima")).minusDays(2),
                20000,backend.mantenimiento.model.DatosRegistroMantenimiento.OrigenKmServicio.HISTORICO_MANUAL,null,null,null,null,null),null,List.of()));
        var c=new Configuracion(vehiculo,tipo,null,Criterio.POR_TIEMPO,Unidad.MESES,2,null,id);
        var s=solicitud(c);jdbc.update("UPDATE mycar.mantenimiento SET estado='ANULADO' WHERE id_mantenimiento=?",id);
        var error=json.readTree(postAlerta(s,409));assertEquals("REVISION_CAMBIO",error.get("codigo").asText());
        assertTrue(error.at("/revision/configuracion/idMantenimientoReferencia").isNull());assertEquals(0,contar("alerta"));
        var revisada=json.treeToValue(error.get("revision"),Revision.class);
        assertNotNull(servicio.confirmar(usuario,new Solicitud(UUID.randomUUID(),revisada)));
    }
    @Test void consultarOperacionAjenaNoRevelaRespuesta() throws Exception {
        var s=solicitud(km());servicio.confirmar(usuario,s);long otra=cuenta();
        var token=sesiones.login(new LoginCommand(correo(otra),PASSWORD,UUID.randomUUID()));
        mvc.perform(get("/api/v1/rf22/operaciones/"+s.idOperacion()).header("Authorization","Bearer "+token.accessToken()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("NO_ENCONTRADA")).andExpect(jsonPath("$.resultado").doesNotExist());
    }
    @Test void cp08ContextoSinActivosNoInventaVehiculos() throws Exception {
        jdbc.update("UPDATE mycar.vehiculo SET estado_vehiculo='INACTIVO' WHERE id_vehiculo=?",vehiculo);
        mvc.perform(get("/api/v1/rf22/contexto").header("Authorization",bearer())).andExpect(status().isOk()).andExpect(jsonPath("$.vehiculos").isEmpty());
    }
    @Test void cp10FalloHistorialRevierteAlertaYOperacion() throws Exception {
        var s=solicitud(km());ProgramacionAlertaJdbcDao objetivo=AopTestUtils.getUltimateTargetObject(persistencia);
        doThrow(new DataAccessResourceFailureException("FALLO_FIXTURE")).when(objetivo).historialInicial(anyLong(),anyString());
        postAlerta(s,503);assertEquals(0,contar("alerta"));
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM mycar.operacion_idempotente WHERE id_operacion=?",Long.class,s.idOperacion()));
        reset(objetivo);assertNotNull(servicio.confirmar(usuario,s));
    }
    @Test void cp12DesactivacionDelTipoRechazaSinEscrituras() throws Exception {
        var s=solicitud(km());jdbc.update("UPDATE mycar.tipo_mantenimiento SET activo=false WHERE id_tipo_mantenimiento=?",tipo);
        try {postAlerta(s,409);assertEquals(0,contar("alerta"));}
        finally {jdbc.update("UPDATE mycar.tipo_mantenimiento SET activo=true WHERE id_tipo_mantenimiento=?",tipo);}
    }
    @Test void replayConfirmadoNoRevalidaTipoDesactivado() throws Exception {
        var s=solicitud(km());var r=servicio.confirmar(usuario,s);jdbc.update("UPDATE mycar.tipo_mantenimiento SET activo=false WHERE id_tipo_mantenimiento=?",tipo);
        try {assertEquals(r,json.readValue(postAlerta(s,201),Resultado.class));assertEquals(1,contar("alerta"));}
        finally {jdbc.update("UPDATE mycar.tipo_mantenimiento SET activo=true WHERE id_tipo_mantenimiento=?",tipo);}
    }
    @Test void sesionesAusentesInvalidasRevocadasYSuspendidasNoAcceden() throws Exception {
        mvc.perform(get("/api/v1/rf22/contexto")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/rf22/contexto").header("Authorization","Bearer invalido")).andExpect(status().isUnauthorized());
        cierre.close(sesion.accessToken());mvc.perform(get("/api/v1/rf22/contexto").header("Authorization",bearer())).andExpect(status().isUnauthorized());
        var nueva=sesiones.login(new LoginCommand(correo(usuario),PASSWORD,UUID.randomUUID()));
        jdbc.update("UPDATE mycar.usuario SET estado_cuenta='SUSPENDIDA' WHERE id_usuario=?",usuario);
        mvc.perform(get("/api/v1/rf22/contexto").header("Authorization","Bearer "+nueva.accessToken())).andExpect(status().isUnauthorized());
    }
}
