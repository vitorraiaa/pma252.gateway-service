package store.gateway;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController
public class GatewayResource {

    @GetMapping("/health-check")
    public ResponseEntity<Map<String, String>> healthCheck() {
        return ResponseEntity.ok()
            .body(Map.of(
                "osArch", System.getProperty("os.arch"),
                "osName", System.getProperty("os.name"),
                "osVersision", System.getProperty("os.version")
            ));
    }

    @GetMapping("/")
    public ResponseEntity<String> hello() {
        return ResponseEntity.ok()
            .body("API for Store");
    }
    
}
