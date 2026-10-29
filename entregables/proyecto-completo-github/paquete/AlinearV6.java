import java.nio.file.*;
import java.sql.*;
import java.util.*;
import org.flywaydb.core.Flyway;

/** Mantenimiento local fuera del proyecto. Sin V7, tablas nuevas, borrado ni cambios de datos. */
class AlinearV6 {
    static String env(String k) { return Objects.requireNonNull(System.getenv(k), k); }
    static final Map<String,Integer> previas=Map.of("1",-704795757,"2",2110156963,"3",-2041438498,"4",-1055621336,"5",1766351818);
    static Map<String,Long> contar(Connection c) throws Exception {
        var out=new TreeMap<String,Long>(); var nombres=new ArrayList<String>();
        try(var s=c.createStatement();var r=s.executeQuery("SELECT tablename FROM pg_tables WHERE schemaname='mycar' AND tablename <> 'flyway_schema_history'")) {
            while(r.next()) nombres.add(r.getString(1));
        }
        for(String n:nombres) { if(!n.matches("[a-z_]+")) throw new IllegalStateException("Tabla inesperada");
            try(var s=c.createStatement();var r=s.executeQuery("SELECT count(*) FROM mycar."+n)) {r.next();out.put(n,r.getLong(1));}}
        return out;
    }
    public static void main(String[] args) throws Exception {
        String url=env("MYCAR_ACTUALIZAR_URL"), user=env("MYCAR_ACTUALIZAR_USER"), password=env("MYCAR_ACTUALIZAR_PASSWORD");
        Path carpeta=Path.of(args[0]); int esperado=Integer.parseInt(args[1]); int anterior=790664128;
        Flyway flyway=Flyway.configure().dataSource(url,user,password).defaultSchema("mycar")
            .locations("filesystem:"+carpeta.toAbsolutePath()).load();
        Map<String,Long> antes;
        try(var c=DriverManager.getConnection(url,user,password)) {
            var versiones=new HashMap<String,Integer>();
            try(var s=c.createStatement();var r=s.executeQuery("SELECT version,checksum,success FROM mycar.flyway_schema_history WHERE version IS NOT NULL")) {
                while(r.next()) {if(!r.getBoolean(3)) throw new IllegalStateException("Hay una migracion fallida; no se modifico nada");versiones.put(r.getString(1),r.getInt(2));}}
            if(versiones.size()!=6 || !versiones.keySet().containsAll(Set.of("1","2","3","4","5","6"))) throw new IllegalStateException("El historial no coincide con V1-V6 revisado");
            for(var e:previas.entrySet()) if(!e.getValue().equals(versiones.get(e.getKey()))) throw new IllegalStateException("Checksum previo distinto: V"+e.getKey());
            int actual=versiones.get("6"); if(actual!=anterior && actual!=esperado) throw new IllegalStateException("V6 desconocida; no se modifico nada");
            antes=contar(c);
            if(actual==anterior && actual!=esperado) {
                if(antes.getOrDefault("alerta",0L)!=0) throw new IllegalStateException("Hay alertas de la version anterior; conservarlas requiere revision antes de cambiar de contrato");
                try(var s=c.createStatement();var r=s.executeQuery("SELECT count(*) FROM mycar.operacion_idempotente WHERE tipo_operacion='PROGRAMAR_ALERTA'")) {r.next();if(r.getLong(1)!=0)throw new IllegalStateException("Hay operaciones RF22 anteriores; no se modifico nada");}
                if(args.length<3 || !args[2].equals("--aplicar-v6-publicada")) throw new IllegalStateException("Requiere la opcion explicita --aplicar-v6-publicada");
                String sql=Files.readString(carpeta.resolve("V6__rf22_tiempo_sin_lectura.sql"));
                c.setAutoCommit(false);
                try(var s=c.createStatement()) {s.execute(sql);c.commit();} catch(Exception ex) {c.rollback();throw ex;}
            }
        }
        // La copia de seguridad se toma antes de invocar este auxiliar. Solo V6 puede diferir.
        flyway.repair(); flyway.validate();
        try(var c=DriverManager.getConnection(url,user,password)) {
            if(!antes.equals(contar(c))) throw new IllegalStateException("Cambió el numero de filas; revisar el respaldo");
            try(var s=c.createStatement();var r=s.executeQuery("SELECT checksum FROM mycar.flyway_schema_history WHERE version='6' AND success")) {if(!r.next() || r.getInt(1)!=esperado)throw new IllegalStateException("V6 no quedo alineada");}
        }
        System.out.println("V6 publicada aplicada y validada; filas conservadas. No se creo V7.");
    }
}
