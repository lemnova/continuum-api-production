package tech.lemnova.continuum.infra.vault;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "vault.b2")
public class VaultConfig {
    private String endpoint;
    private String bucketName;
    private String accessKey;
    private String secretKey;
    private String region;
}
