import java.util.regex.Pattern;
import java.util.regex.Matcher;
public class TestRegex {
    public static void main(String[] args) {
        try {
            Pattern p = Pattern.compile("(?<amount>\\d+)|(?<amount>[a-z]+)");
            System.out.println("Compiled successfully.");
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }
    }
}
