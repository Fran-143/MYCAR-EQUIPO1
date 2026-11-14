package backend.documentos;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SupabaseAlmacenamientoDocumentosTest {

    @Test
    void absolutizaRutasRelativasSinExponerSecret() {
        var storage = new SupabaseAlmacenamientoDocumentos(
                "https://demo.supabase.co/",
                "mycar-documentos-privados",
                "sb_secret_test_value_not_logged",
                new ObjectMapper());
        assertEquals(
                "https://demo.supabase.co/storage/v1/object/sign/x",
                storage.absolutizar("/storage/v1/object/sign/x"));
        assertEquals(
                "https://cdn.example/a",
                storage.absolutizar("https://cdn.example/a"));
        assertEquals("https://demo.supabase.co", SupabaseAlmacenamientoDocumentos.normalizarBase("https://demo.supabase.co/"));
    }
}
