package backend.talleres.service;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ValidadorTallerTest {
    private final ValidadorTaller v = new ValidadorTaller();

    @Test void nombreObligatorioMayusculasYTrimea() {
        assertEquals("TALLER CENTRO", v.nombreObligatorio("  Taller Centro  "));
    }

    @Test void nombreVacioRechaza() {
        assertThrows(IllegalArgumentException.class, () -> v.nombreObligatorio("   "));
        assertThrows(IllegalArgumentException.class, () -> v.nombreObligatorio(null));
    }

    @Test void telefonoCelularPeruNormalizaYValida() {
        assertEquals("945612378", v.telefonoOpcional("945612378"));
        assertEquals("977665544", v.telefonoOpcional(" 977665544 "));
        assertEquals("987654321", v.telefonoOpcional("+51 987 654 321"));
        assertEquals("987654321", v.telefonoOpcional("51987654321"));
        assertNull(v.telefonoOpcional("  "));
    }

    @Test void telefonoInvalidoRechaza() {
        assertThrows(IllegalArgumentException.class, () -> v.telefonoOpcional("abc"));
        assertThrows(IllegalArgumentException.class, () -> v.telefonoOpcional("187654321"));
        assertThrows(IllegalArgumentException.class, () -> v.telefonoOpcional("98765432"));
        assertThrows(IllegalArgumentException.class, () -> v.telefonoOpcional("9998887770"));
        assertThrows(IllegalArgumentException.class, () -> v.telefonoOpcional("12ab"));
    }

    @Test void correoOpcionalNormaliza() {
        assertEquals("a@b.com", v.correoOpcional("  A@B.COM "));
        assertNull(v.correoOpcional(null));
    }

    @Test void correoInvalidoRechaza() {
        assertThrows(IllegalArgumentException.class, () -> v.correoOpcional("no-es-correo"));
        assertThrows(IllegalArgumentException.class, () -> v.correoOpcional("taller@"));
        assertThrows(IllegalArgumentException.class, () -> v.correoOpcional("@correo.com"));
        assertThrows(IllegalArgumentException.class, () -> v.correoOpcional("taller@@correo.com"));
        assertThrows(IllegalArgumentException.class, () -> v.correoOpcional("taller correo@ejemplo.com"));
    }

    @Test void direccionMinimo9YMayusculas() {
        assertEquals("AV. LOS OLIVOS 123", v.direccionOpcional(" Av. Los Olivos 123 "));
        assertThrows(IllegalArgumentException.class, () -> v.direccionOpcional("AV. LIMA"));
        assertThrows(IllegalArgumentException.class, () -> v.direccionOpcional("x".repeat(201)));
        assertEquals("NOTA IMPORTANTE", v.notasOpcional(" nota importante "));
        var ex = assertThrows(IllegalArgumentException.class, () -> v.notasOpcional("y".repeat(501)));
        assertEquals("Las notas no pueden superar los 500 caracteres", ex.getMessage());
    }
}
