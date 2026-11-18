import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
public class HashPasswordOnce {
  public static void main(String[] args) {
    if (args.length != 1) System.exit(2);
    var enc = new Argon2PasswordEncoder(16, 32, 1, 19456, 2);
    System.out.print(enc.encode(args[0]));
  }
}
