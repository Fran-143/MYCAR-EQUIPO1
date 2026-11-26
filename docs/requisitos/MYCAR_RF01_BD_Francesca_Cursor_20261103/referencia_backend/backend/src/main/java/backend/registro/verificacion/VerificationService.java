package backend.registro.verificacion;

import backend.registro.*;
import java.time.*;
import java.util.*;
import static backend.registro.verificacion.VerificationRepository.*;

/** Stage 1: no component annotation, controller, origin admission or public activation. */
public final class VerificationService {
    final VerificationRepository db;
    final VerificationCrypto crypto;
    final VerificationPolicy policy;
    private final RegistrationValidator validator;
    private final RegistrationPrivacyPolicy privacy;
    private final RegistrationStore accounts;
    private final RegisterAccount register;

    public VerificationService(VerificationRepository db,VerificationCrypto crypto,RegistrationValidator validator,
            RegistrationPrivacyPolicy privacy,RegistrationStore accounts,RegisterAccount register) {
        this.db=db;this.crypto=crypto;this.policy=new VerificationPolicy(db);
        this.validator=validator;this.privacy=privacy;this.accounts=accounts;this.register=register;
    }
    public record Origin(UUID id,String secret) { @Override public String toString(){return "Origin[REDACTED]";} }
    public record Operation(UUID id,String secret) { @Override public String toString(){return "Operation[REDACTED]";} }
    public record Result(String code,UUID operation,UUID emission,long revision,Instant blockUntil) {}
    public record State(String operationState,String codeState,String deliveryState,String quotaState,long revision,
                        UUID emission,Instant expires,Instant blockUntil,String reason) {}
    public static final class Failure extends RuntimeException {
        private final String code;
        Failure(String code){super(code);this.code=code;}
        public String code(){return code;}
    }
    static Failure fail(String code){return new Failure(code);}
    private void authenticate(Row op,Operation credential) {
        if(op==null||!crypto.matches(op.bytes("secreto_hash"),crypto.credential(credential.secret())))throw fail("CREDENCIAL_OPERACION_INVALIDA");
    }
    private void open(Row op,long revision,Instant now) {
        if(!"ABIERTA".equals(op.text("estado")))throw fail("PROCESO_NO_ABIERTO");
        if(!now.isBefore(op.instant("expira_utc")))throw fail("PROCESO_EXPIRADO");
        if(op.number("revision")!=revision)throw fail("REVISION_DESACTUALIZADA");
    }
    private Result replay(Row receipt,byte[] fp) {
        if(receipt==null)return null;
        if(!crypto.matches(receipt.bytes("huella_solicitud"),fp))throw fail("IDEMPOTENCIA_INCOMPATIBLE");
        return new Result(receipt.text("resultado"),receipt.id("id_operacion"),receipt.id("id_emision"),receipt.number("revision_resultado"),receipt.instant("bloqueo_hasta_resultado_utc"));
    }
    private Result receipt(UUID op,UUID request,String action,UUID emission,byte[] fp,String result,int status,long revision,Instant block,Instant now) {
        db.update("""
            INSERT INTO mycar.intento_verificacion(id_operacion,id_solicitud,finalidad,accion,id_emision,huella_solicitud,
             clave_huella_version,resultado,http_status,revision_resultado,fallo_contabilizado,bloqueo_hasta_resultado_utc,ocurrido_utc)
            VALUES (?,?,'REGISTRO',?,?,?,1,?,?,?,?,?,?)
            """,op,request,action,emission,fp,result,status,revision,
            result.equals("CODIGO_INCORRECTO")||result.equals("BLOQUEO_INICIADO"),at(block),at(now));
        return new Result(result,op,emission,revision,block);
    }
    public Result emit(Origin origin,Operation credential,UUID request,long revision,RegistrationCommand form) {
        Objects.requireNonNull(request);
        byte[] fp=crypto.fingerprint("EMITIR",revision,null,form,null);
        return db.tx(()-> {
            Row o=db.lockOrigin(origin.id());
            if(o==null||!crypto.matches(o.bytes("credencial_hash"),crypto.credential(origin.secret())))throw fail("ORIGEN_NO_AUTORIZADO");
            Row existing=db.operation(credential.id());
            if(existing!=null){authenticate(existing,credential);if(!origin.id().equals(existing.id("id_control_origen")))throw fail("ORIGEN_NO_AUTORIZADO");}
            Row firstRead=db.receipt(credential.id(),request);
            RegistrationData data=null;UUID email=null;
            if(firstRead==null) {
                data=validator.validate(form);privacy.requireAcceptedVersion(data.versionPrivacidad());
                if(accounts.emailExists(data.correo()))throw RegistrationException.emailUnavailable();
                email=db.ensureEmail(data.correo(),crypto.subject("CORREO",data.correo()),db.now());
            }
            List<UUID> emails=new ArrayList<>();if(email!=null)emails.add(email);
            Row current=db.current(credential.id());if(current!=null)emails.add(current.id("id_control_correo"));
            db.lockEmails(emails);
            Instant now=db.now();
            if(existing==null)db.update("""
                INSERT INTO mycar.operacion_verificacion(id_operacion,finalidad,id_control_origen,secreto_hash,estado,creada_utc,expira_utc,retener_hasta_utc)
                VALUES (?,'REGISTRO',?,?,'ABIERTA',?,?,?) ON CONFLICT DO NOTHING
                """,credential.id(),origin.id(),crypto.credential(credential.secret()),at(now),at(now.plus(Duration.ofHours(24))),at(now.plus(Duration.ofHours(72))));
            Row op=db.lockOperation(credential.id());authenticate(op,credential);
            if(!origin.id().equals(op.id("id_control_origen")))throw fail("ORIGEN_NO_AUTORIZADO");
            // Mandatory second lookup AFTER transactional locks, never trust an earlier miss.
            Result repeated=replay(db.receipt(credential.id(),request),fp);if(repeated!=null)return repeated;
            now=db.now();open(op,revision,now);
            if(now.plus(Duration.ofMinutes(10)).isAfter(op.instant("expira_utc")))throw fail("TIEMPO_PROCESO_INSUFICIENTE");
            if(!policy.technical(o,"solicitudes","solicitudes_desde_utc",60,now))throw fail("LIMITE_TECNICO");
            String limit=policy.emissionLimit(credential.id(),origin.id(),email,now);
            if(limit!=null)return result(limit,op,null,db.email(email).instant("bloqueo_hasta_utc"));
            current=db.current(credential.id());db.close(current,"CANCELADO","SUSTITUIDO",now);
            UUID emission=UUID.randomUUID();long generation=db.one("SELECT coalesce(max(generacion),0)+1 AS n FROM mycar.emision_codigo WHERE id_operacion=?",credential.id()).number("n");
            String code=crypto.generateCode();var sealed=crypto.seal(credential.id(),emission,data.correo(),generation,code);
            db.update("""
                INSERT INTO mycar.emision_codigo(id_emision,id_operacion,finalidad,generacion,id_control_correo,
                 estado_codigo,estado_envio,estado_cupo,verificador,clave_verificador_version,payload_cifrado,nonce,clave_cifrado_id,
                 proveedor,clave_idempotencia_proveedor,creada_utc,reserva_hasta_utc,proximo_intento_utc)
                VALUES (?,?,'REGISTRO',?,?,'RESERVADO','PENDIENTE','RESERVADO',?,1,?,?,'v1','PENDIENTE',?,?,?,?)
                """,emission,credential.id(),generation,email,crypto.verifier(credential.id(),emission,data.correo(),generation,code),
                sealed.ciphertext(),sealed.nonce(),"rf01/"+emission,at(now),at(now.plusSeconds(120)),at(now));
            db.update("UPDATE mycar.operacion_verificacion SET revision=revision+1 WHERE id_operacion=?",credential.id());
            return receipt(credential.id(),request,"EMITIR",emission,fp,"EMISION_CREADA",202,revision+1,null,now);
        });
    }
    private record Context(Row origin,Row op,Row emission,Instant now) {}
    private Context lock(Operation credential,UUID target) {
        Row initial=db.operation(credential.id());authenticate(initial,credential);
        Row origin=db.lockOrigin(initial.id("id_control_origen"));
        Row e=target==null?db.current(credential.id()):db.emission(target);
        if(e!=null&&!credential.id().equals(e.id("id_operacion")))throw fail("EMISION_AJENA");
        db.lockEmails(e==null?List.of():List.of(e.id("id_control_correo")));
        Row op=db.lockOperation(credential.id());authenticate(op,credential);
        return new Context(origin,op,e==null?null:db.emission(e.id("id_emision")),db.now());
    }
    public Result cancel(Operation credential,UUID request,long revision,UUID emission) {
        return close(credential,request,revision,emission,false);
    }
    public Result abandon(Operation credential,UUID request,long revision) {
        return close(credential,request,revision,null,true);
    }
    private Result close(Operation credential,UUID request,long revision,UUID target,boolean abandon) {
        String action=abandon?"ABANDONAR":"CANCELAR";
        byte[] fp=crypto.fingerprint(action,revision,target,null,null);
        return db.tx(()-> {
            db.receipt(credential.id(),request); // speculative read only
            Context c=lock(credential,target);
            Result repeated=replay(db.receipt(credential.id(),request),fp);if(repeated!=null)return repeated;
            if("COMPLETADA".equals(c.op.text("estado")))return result("CUENTA_CREADA",c.op,c.emission,null);
            open(c.op,revision,c.now);
            if(target!=null&&c.emission==null)throw fail("EMISION_NO_ENCONTRADA");
            if(!policy.technical(c.origin,"solicitudes","solicitudes_desde_utc",60,c.now))return result("LIMITE_TECNICO",c.op,c.emission,null);
            db.close(c.emission,"CANCELADO",abandon?"ABANDONO":"CAMBIAR_CORREO",c.now);
            db.update("UPDATE mycar.operacion_verificacion SET revision=revision+1,estado=? WHERE id_operacion=?",abandon?"CANCELADA":"ABIERTA",credential.id());
            return receipt(credential.id(),request,action,c.emission==null?null:c.emission.id("id_emision"),fp,
                abandon?"ABANDONO_CONFIRMADO":"CANCELACION_CONFIRMADA",200,revision+1,null,c.now);
        });
    }
    public Result complete(Operation credential,UUID request,long revision,UUID emission,String code,RegistrationCommand form) {
        byte[] fp=crypto.fingerprint("COMPLETAR",revision,emission,form,code);
        return db.tx(()-> {
            db.receipt(credential.id(),request); // second read below is authoritative
            Context c=lock(credential,emission);
            Result repeated=replay(db.receipt(credential.id(),request),fp);if(repeated!=null)return repeated;
            open(c.op,revision,c.now);
            RegistrationData data=validator.validate(form);privacy.requireAcceptedVersion(data.versionPrivacidad());
            if(code==null||!code.matches("[0-9]{6}"))throw fail("SOLICITUD_INVALIDA");
            Row e=c.emission;if(e==null)throw fail("EMISION_NO_ENCONTRADA");
            Row email=db.email(e.id("id_control_correo"));
            if(!data.correo().equals(email.text("correo_normalizado")))throw fail("CORREO_NO_COINCIDE");
            if(!policy.technical(c.origin,"solicitudes","solicitudes_desde_utc",60,c.now))return result("LIMITE_TECNICO",c.op,e,null);
            if(policy.blocked(email,c.now))return result("BLOQUEO_ACTIVO",c.op,e,email.instant("bloqueo_hasta_utc"));
            if(!"ACTIVO".equals(e.text("estado_codigo")))return result("CODIGO_"+e.text("estado_codigo"),c.op,e,null);
            if(!c.now.isBefore(e.instant("vence_codigo_utc"))) {db.close(e,"VENCIDO","CODIGO_VENCIDO",c.now);return result("CODIGO_VENCIDO",c.op,e,null);}
            if(!crypto.matches(e.bytes("verificador"),crypto.verifier(credential.id(),emission,data.correo(),e.number("generacion"),code))) {
                Instant until=policy.wrongCode(email,c.now);
                return receipt(credential.id(),request,"COMPLETAR",emission,fp,until==null?"CODIGO_INCORRECTO":"BLOQUEO_INICIADO",until==null?422:429,revision,until,c.now);
            }
            // Reuse existing validator / password encoder / account + FREE + preferences initializer.
            // Its JDBC work participates in this outer datasource transaction.
            long user=register.register(form);
            db.update("UPDATE mycar.usuario SET correo_verificado_utc=? WHERE id_usuario=?",at(c.now),user);
            db.close(e,"CONSUMIDO",null,c.now);
            db.update("UPDATE mycar.operacion_verificacion SET estado='COMPLETADA',usuario_resultado=?,completada_utc=?,revision=revision+1 WHERE id_operacion=?",user,at(c.now),credential.id());
            policy.clearFailures(email.id("id_control"),c.now);
            return receipt(credential.id(),request,"COMPLETAR",emission,fp,"CUENTA_CREADA",201,revision+1,null,c.now);
        });
    }
    public State state(Operation credential) {
        return db.tx(()-> {
            Context c=lock(credential,null);
            Row e=c.emission;
            if(e==null)e=db.one("SELECT * FROM mycar.emision_codigo WHERE id_operacion=? ORDER BY generacion DESC LIMIT 1",credential.id());
            return new State(c.op.text("estado"),e==null?null:e.text("estado_codigo"),e==null?null:e.text("estado_envio"),
                e==null?null:e.text("estado_cupo"),c.op.number("revision"),e==null?null:e.id("id_emision"),e==null?null:e.instant("vence_codigo_utc"),
                e==null?null:db.email(e.id("id_control_correo")).instant("bloqueo_hasta_utc"),e==null?null:e.text("error_categoria"));
        });
    }
    private Result result(String code,Row op,Row e,Instant block) {return new Result(code,op.id("id_operacion"),e==null?null:e.id("id_emision"),op.number("revision"),block);}

    // Internal worker access: never a public API, no origin-admission bypass is provided.
    private Context workerLock(UUID emission) {
        Row e=db.emission(emission);if(e==null)throw fail("EMISION_NO_ENCONTRADA");
        Row op=db.operation(e.id("id_operacion"));Row origin=db.lockOrigin(op.id("id_control_origen"));
        db.lockEmails(List.of(e.id("id_control_correo")));op=db.lockOperation(op.id("id_operacion"));
        return new Context(origin,op,db.emission(emission),db.now());
    }
    UUID claim(UUID emission) {
        return db.tx(()-> {
            Context c=workerLock(emission);Row e=c.emission;
            if(!live(c))return null;
            if("ACEPTADO".equals(e.text("estado_envio"))||"RECHAZADO".equals(e.text("estado_envio")))return null;
            if(e.instant("lease_hasta_utc")!=null&&c.now.isBefore(e.instant("lease_hasta_utc")))return null;
            if(e.number("intentos_despacho")>=4||e.bytes("payload_cifrado")==null)return null;
            if(e.instant("proximo_intento_utc")!=null&&c.now.isBefore(e.instant("proximo_intento_utc")))return null;
            UUID lease=UUID.randomUUID();db.update("UPDATE mycar.emision_codigo SET lease_id=?,lease_hasta_utc=? WHERE id_emision=?",lease,at(c.now.plusSeconds(30)),emission);return lease;
        });
    }
    private boolean live(Context c) {
        Row e=c.emission;
        if(!List.of("RESERVADO","ACTIVO").contains(e.text("estado_codigo")))return false;
        if(!"ABIERTA".equals(c.op.text("estado"))||!c.now.isBefore(c.op.instant("expira_utc"))) {
            db.close(e,"CANCELADO","PROCESO_NO_ABIERTO",c.now);return false;
        }
        if(e.instant("primer_despacho_utc")==null&&!c.now.isBefore(e.instant("reserva_hasta_utc"))) {
            db.close(e,"CANCELADO","RESERVA_VENCIDA",c.now);return false;
        }
        if(e.instant("vence_codigo_utc")!=null&&!c.now.isBefore(e.instant("vence_codigo_utc"))) {
            db.close(e,"VENCIDO","CODIGO_VENCIDO",c.now);return false;
        }
        return true;
    }
    MailGateway.Message payload(UUID emission) {
        Row e=db.emission(emission);Row email=db.email(e.id("id_control_correo"));
        return crypto.open(e.id("id_operacion"),emission,email.text("correo_normalizado"),e.number("generacion"),e.bytes("payload_cifrado"),e.bytes("nonce"),e.text("clave_idempotencia_proveedor"));
    }
    boolean authorizeDispatch(UUID emission,UUID lease) {
        return db.tx(()-> {
            Context c=workerLock(emission);Row e=c.emission;
            if(!lease.equals(e.id("lease_id"))||!c.now.isBefore(e.instant("lease_hasta_utc")))return false;
            if(!live(c))return false;
            Instant first=e.instant("primer_despacho_utc");
            if(first==null&&c.now.plus(Duration.ofMinutes(10)).isAfter(c.op.instant("expira_utc"))) {
                db.close(e,"CANCELADO","TIEMPO_PROCESO_INSUFICIENTE",c.now);return false;
            }
            if(!policy.technical(c.origin,"despachos","despachos_desde_utc",12,c.now)) {
                if(first==null)db.close(e,"CANCELADO","LIMITE_TECNICO",c.now);return false;
            }
            if(first==null)first=c.now;
            db.update("""
                UPDATE mycar.emision_codigo SET primer_despacho_utc=?,vence_codigo_utc=?,estado_codigo='ACTIVO',
                 estado_envio='EN_CURSO',estado_cupo='INCIERTO',intentos_despacho=intentos_despacho+1 WHERE id_emision=?
                """,at(first),at(first.plus(Duration.ofMinutes(10))),emission);
            return true;
        });
    }
    void internalFailure(UUID emission,UUID lease) {
        db.tx(()-> {Context c=workerLock(emission);
            if(lease.equals(c.emission.id("lease_id"))&&c.emission.instant("primer_despacho_utc")==null) {
                db.close(c.emission,"CANCELADO","FALLO_PREVIO_DESPACHO",c.now);
                db.update("UPDATE mycar.control_verificacion SET backoff_hasta_utc=? WHERE id_control=?",at(c.now.plusSeconds(30)),c.emission.id("id_control_correo"));
            }
            return null;});
    }
    void deliveryResult(UUID emission,UUID lease,MailGateway.Outcome outcome) {
        db.tx(()-> {
            Context c=workerLock(emission);Row e=c.emission;
            if(outcome instanceof MailGateway.Accepted a) {
                db.update("""
                    UPDATE mycar.emision_codigo SET estado_envio='ACEPTADO',estado_cupo='CONFIRMADO',id_proveedor=?,
                     aceptada_utc=coalesce(aceptada_utc,?),payload_cifrado=NULL,nonce=NULL,clave_cifrado_id=NULL,
                     lease_id=NULL,lease_hasta_utc=NULL,proximo_intento_utc=NULL WHERE id_emision=?
                    """,a.providerId(),at(c.now),emission);
            } else if(!"ACEPTADO".equals(e.text("estado_envio"))) {
                if(outcome instanceof MailGateway.Rejected r&&r.definitivelyNeverAccepted()) {
                    db.close(e,"CANCELADO","ENVIO_RECHAZADO",c.now);
                    db.update("UPDATE mycar.emision_codigo SET estado_envio='RECHAZADO',estado_cupo='LIBERADO',lease_id=NULL,lease_hasta_utc=NULL,payload_cifrado=NULL,nonce=NULL,clave_cifrado_id=NULL WHERE id_emision=?",emission);
                    db.update("UPDATE mycar.control_verificacion SET backoff_hasta_utc=? WHERE id_control=?",at(c.now.plusSeconds(30)),e.id("id_control_correo"));
                } else if(lease.equals(e.id("lease_id"))) {
                    long n=e.number("intentos_despacho");Instant next=e.instant("primer_despacho_utc").plusSeconds(n==1?5:n==2?30:120);
                    db.update("UPDATE mycar.emision_codigo SET estado_envio='INCIERTO',estado_cupo='INCIERTO',lease_id=NULL,lease_hasta_utc=NULL,proximo_intento_utc=? WHERE id_emision=?",at(next),emission);
                    if(n>=4)db.update("UPDATE mycar.emision_codigo SET payload_cifrado=NULL,nonce=NULL,clave_cifrado_id=NULL WHERE id_emision=?",emission);
                } else if(!List.of("RESERVADO","ACTIVO").contains(e.text("estado_codigo"))) {
                    db.update("UPDATE mycar.emision_codigo SET estado_envio='INCIERTO' WHERE id_emision=? AND estado_envio='EN_CURSO'",emission);
                }
            }
            return null;
        });
    }

    /** Explicit bounded maintenance; no production scheduling in this isolated stage. */
    public void maintain() {
        Instant maintenanceNow=db.now();
        for(Row candidate:db.rows("""
            SELECT id_operacion FROM mycar.operacion_verificacion o WHERE retener_hasta_utc<=?
             OR (estado='ABIERTA' AND expira_utc<=?) OR EXISTS(
              SELECT 1 FROM mycar.emision_codigo e WHERE e.id_operacion=o.id_operacion AND
               ((e.estado_codigo IN ('RESERVADO','ACTIVO') AND
                 (e.vence_codigo_utc<=? OR (e.primer_despacho_utc IS NULL AND e.reserva_hasta_utc<=?)))
                OR (e.payload_cifrado IS NOT NULL AND o.expira_utc<=?)))
            ORDER BY id_operacion LIMIT 100
            """,at(maintenanceNow),at(maintenanceNow),at(maintenanceNow),at(maintenanceNow),at(maintenanceNow))) {
            db.tx(()-> {
                UUID id=candidate.id("id_operacion");Row initial=db.operation(id);if(initial==null)return null;
                db.lockOrigin(initial.id("id_control_origen"));
                var emissions=db.rows("SELECT * FROM mycar.emision_codigo WHERE id_operacion=?",id);
                db.lockEmails(emissions.stream().map(e->e.id("id_control_correo")).toList());
                Row op=db.lockOperation(id);if(op==null)return null;Instant now=db.now();
                for(Row e:emissions) {
                    boolean expired=e.instant("vence_codigo_utc")!=null&&!now.isBefore(e.instant("vence_codigo_utc"));
                    boolean unusedExpired=e.instant("primer_despacho_utc")==null&&!now.isBefore(e.instant("reserva_hasta_utc"));
                    if(expired)db.close(e,"VENCIDO","CODIGO_VENCIDO",now);
                    else if(unusedExpired)db.close(e,"CANCELADO","RESERVA_VENCIDA",now);
                    if(expired||unusedExpired||!now.isBefore(op.instant("expira_utc")))
                        db.update("UPDATE mycar.emision_codigo SET payload_cifrado=NULL,nonce=NULL,clave_cifrado_id=NULL,verificador=NULL,clave_verificador_version=NULL WHERE id_emision=?",e.id("id_emision"));
                }
                if("ABIERTA".equals(op.text("estado"))&&!now.isBefore(op.instant("expira_utc"))) {
                    for(Row e:emissions)db.close(e,"CANCELADO","PROCESO_EXPIRADO",now);
                    db.update("UPDATE mycar.operacion_verificacion SET estado='EXPIRADA' WHERE id_operacion=?",id);
                }
                if(!now.isBefore(op.instant("retener_hasta_utc"))) {
                    db.update("DELETE FROM mycar.intento_verificacion WHERE id_operacion=?",id);
                    db.update("DELETE FROM mycar.emision_codigo WHERE id_operacion=?",id);
                    db.update("DELETE FROM mycar.operacion_verificacion WHERE id_operacion=?",id);
                }
                return null;
            });
        }
        db.tx(()-> {
            Instant now=db.now();
            db.update("""
                DELETE FROM mycar.control_verificacion c WHERE
                 ultimo_uso_utc < CAST(? AS TIMESTAMPTZ) - CASE WHEN tipo='CORREO' THEN INTERVAL '7 days' ELSE INTERVAL '30 days' END
                 AND (bloqueo_hasta_utc IS NULL OR bloqueo_hasta_utc<=?)
                 AND (fallos_desde_utc IS NULL OR fallos_desde_utc+INTERVAL '1 hour'<=?)
                 AND (solicitudes_desde_utc IS NULL OR solicitudes_desde_utc+INTERVAL '1 hour'<=?)
                 AND (despachos_desde_utc IS NULL OR despachos_desde_utc+INTERVAL '1 hour'<=?)
                 AND (backoff_hasta_utc IS NULL OR backoff_hasta_utc<=?)
                 AND NOT EXISTS(SELECT 1 FROM mycar.operacion_verificacion o WHERE o.id_control_origen=c.id_control)
                 AND NOT EXISTS(SELECT 1 FROM mycar.emision_codigo e WHERE e.id_control_correo=c.id_control)
                """,at(now),at(now),at(now),at(now),at(now),at(now));
            return null;
        });
    }
}
