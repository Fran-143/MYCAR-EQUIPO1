package backend.registro.verificacion;

import java.time.*;
import java.util.UUID;
import static backend.registro.verificacion.VerificationRepository.*;

/** PROVISIONAL global email block: internal only, not enabled for app users. */
public final class VerificationPolicy {
    private final VerificationRepository db;
    VerificationPolicy(VerificationRepository db){this.db=db;}
    boolean blocked(Row email,Instant now) {return email.instant("bloqueo_hasta_utc")!=null&&now.isBefore(email.instant("bloqueo_hasta_utc"));}
    boolean technical(Row origin,String counter,String since,int max,Instant now) {
        Instant start=origin.instant(since);long count=origin.number(counter);
        if(start==null||!now.isBefore(start.plus(Duration.ofHours(1)))) {start=now;count=0;}
        if(count>=max)return false;
        // column names are private constants, never caller-controlled.
        db.update("UPDATE mycar.control_verificacion SET "+counter+"=?,"+since+"=?,ultimo_uso_utc=? WHERE id_control=?",count+1,at(start),at(now),origin.id("id_control"));
        return true;
    }
    String emissionLimit(UUID op,UUID origin,UUID email,Instant now) {
        Row c=db.email(email);
        if(blocked(c,now))return "BLOQUEO_ACTIVO";
        if(c.instant("backoff_hasta_utc")!=null&&now.isBefore(c.instant("backoff_hasta_utc")))return "PAUSA_TECNICA";
        var rows=db.rows("""
            SELECT e.*,o.id_control_origen FROM mycar.emision_codigo e
             JOIN mycar.operacion_verificacion o USING(id_operacion,finalidad)
            WHERE e.id_control_correo=? OR e.id_operacion=? OR o.id_control_origen=?
            """,email,op,origin);
        int byEmail=0,byOp=0,byOrigin=0;
        for(Row e:rows) {
            boolean reserved="RESERVADO".equals(e.text("estado_cupo"))&&now.isBefore(e.instant("reserva_hasta_utc"));
            Instant first=e.instant("primer_despacho_utc");
            boolean sent=first!=null&&first.isAfter(now.minus(Duration.ofHours(1)))
                &&("CONFIRMADO".equals(e.text("estado_cupo"))||"INCIERTO".equals(e.text("estado_cupo")));
            if(!reserved&&!sent)continue;
            if(email.equals(e.id("id_control_correo"))) {
                byEmail++;
                if(reserved)return "RESERVA_PENDIENTE";
                if(now.isBefore(first.plus(Duration.ofMinutes(10))))return "ESPERA_REENVIO";
            }
            if(op.equals(e.id("id_operacion")))byOp++;
            if(origin.equals(e.id("id_control_origen")))byOrigin++;
        }
        return byEmail>=3||byOp>=6||byOrigin>=6?"LIMITE_ENVIO":null;
    }
    Instant wrongCode(Row email,Instant now) {
        Instant start=email.instant("fallos_desde_utc");int count=(int)email.number("fallos");
        Instant block=email.instant("bloqueo_hasta_utc");
        if(start==null||!now.isBefore(start.plus(Duration.ofHours(1)))||(block!=null&&!now.isBefore(block))) {start=now;count=0;}
        count++;Instant until=count==3?now.plus(Duration.ofHours(1)):null;
        db.update("UPDATE mycar.control_verificacion SET fallos=?,fallos_desde_utc=?,bloqueo_hasta_utc=?,ultimo_uso_utc=? WHERE id_control=?",count,at(start),at(until),at(now),email.id("id_control"));
        return until;
    }
    void clearFailures(UUID email,Instant now) {
        db.update("UPDATE mycar.control_verificacion SET fallos=0,fallos_desde_utc=NULL,bloqueo_hasta_utc=NULL,ultimo_uso_utc=? WHERE id_control=?",at(now),email);
    }
}
