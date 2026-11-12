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

    @Test void telefonoOpcionalExactamente9Digitos() {
        assertEquals("945612378", v.telefonoOpcional("945612378"));
        assertEquals("977665544", v.telefonoOpcional(" 977-665-544 "));
        assertNull(v.telefonoOpcional("  "));
    }

    @Test void telefonoInvalidoRechaza() {
        assertThrows(IllegalArgumentException.class, () -> v.telefonoOpcional("abc"));
        assertThrows(IllegalArgumentException.class, () -> v.telefonoOpcional("+51 999 888 777"));
        assertThrows(IllegalArgumentException.class, () -> v.telefonoOpcional("9998887770"));
        assertThrows(IllegalArgumentException.class, () -> v.telefonoOpcional("12345678"));
        var ex = assertThrows(IllegalArgumentException.class, () -> v.telefonoOpcional("12ab"));
        assertEquals("El teléfono ingresado no es válido", ex.getMessage());
    }

    @Test void correoOpcionalNormaliza() {
        assertEquals("a@b.com", v.correoOpcional("  A@B.COM "));
        assertNull(v.correoOpcional(null));
    }

    @Test void correoInvalidoRechaza() {
        var ex = assertThrows(IllegalArgumentException.class, () -> v.correoOpcional("no-es-correo"));
        assertEquals("El correo ingresado no es válido", ex.getMessage());
    }

    @Test void direccionYNotasMayusculasYLimites() {
        assertEquals("AV. LIMA 1", v.direccionOpcional(" Av. Lima 1 "));
        assertEquals("NOTA IMPORTANTE", v.notasOpcional(" nota importante "));
        assertThrows(IllegalArgumentException.class, () -> v.direccionOpcional("x".repeat(201)));
        var ex = assertThrows(IllegalArgumentException.class, () -> v.notasOpcional("y".repeat(501)));
        assertEquals("Las notas no pueden superar los 500 caracteres", ex.getMessage());
    }
}
