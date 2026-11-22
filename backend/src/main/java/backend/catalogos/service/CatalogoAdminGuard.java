package backend.catalogos.service;

import backend.autenticacion.SessionAuthentication;
import backend.catalogos.CatalogoException;
import backend.cuentas.CuentaAdminService;
import backend.cuentas.CuentaException;
import org.springframework.stereotype.Component;

@Component
public class CatalogoAdminGuard {
    private final CuentaAdminService cuentas;

    public CatalogoAdminGuard(CuentaAdminService cuentas) {
        this.cuentas = cuentas;
    }

    public SessionAuthentication.Principal exigir(SessionAuthentication.Principal principal) {
        try {
            cuentas.exigirAdminMfa(principal);
            return principal;
        } catch (CuentaException ex) {
            if (ex.reason() == CuentaException.Reason.MFA_STEP_UP) throw CatalogoException.mfaStepUp();
            if (ex.reason() == CuentaException.Reason.FORBIDDEN) throw CatalogoException.forbidden(ex.getMessage());
            throw CatalogoException.forbidden(ex.getMessage());
        }
    }
}
