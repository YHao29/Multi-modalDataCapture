import org.junit.jupiter.api.Test;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

public class Test_Path {
    @Test
    public void read_config() {
        Properties properties = new Properties();
        // load properties with specified Encoding
        try (InputStream inputStream = new FileInputStream("audio/audio.properties")) {
            // load properties
            properties.load(inputStream);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        System.out.println(
                new String(properties.getProperty("record.devices").getBytes(StandardCharsets.UTF_8))
        );
    }
}
