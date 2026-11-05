package backend.registro.verificacion;

import java.util.UUID;

/** No scheduler / bean: invoked exclusively by isolated tests in stage 1. */
public final class VerificationDispatcher {
    private final VerificationService verification;
    private final MailGateway mail;
    public VerificationDispatcher(VerificationService verification,MailGateway mail){this.verification=verification;this.mail=mail;}
    public void dispatch(UUID emission) {
        UUID lease=verification.claim(emission);if(lease==null)return;
        MailGateway.Message message;
        try {message=verification.payload(emission);}
        catch(RuntimeException ex){verification.internalFailure(emission,lease);return;}
        if(!verification.authorizeDispatch(emission,lease))return;
        MailGateway.Outcome outcome;
        try {outcome=mail.send(message);if(outcome==null)outcome=new MailGateway.Uncertain();}
        catch(RuntimeException ex){outcome=new MailGateway.Uncertain();}
        verification.deliveryResult(emission,lease,outcome);
    }
}
