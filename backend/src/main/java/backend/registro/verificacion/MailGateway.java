package backend.registro.verificacion;

/** Integration boundary only. No production adapter or scheduled caller in stage 1. */
public interface MailGateway {
    Outcome send(Message message);
    record Message(String recipient,String code,String idempotencyKey) {
        @Override public String toString(){return "MailMessage[REDACTED]";}
    }
    sealed interface Outcome permits Accepted,Rejected,Uncertain {}
    record Accepted(String providerId) implements Outcome {
        public Accepted { if(providerId==null||providerId.isBlank()||providerId.length()>128)throw new IllegalArgumentException("Invalid acceptance identifier."); }
    }
    /** True only if the adapter can exclude acceptance of the entire emission, including earlier uncertain calls. */
    record Rejected(boolean definitivelyNeverAccepted) implements Outcome {}
    record Uncertain() implements Outcome {}
}
