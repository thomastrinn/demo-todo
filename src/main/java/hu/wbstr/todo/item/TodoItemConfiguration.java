package hu.wbstr.todo.item;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(TodoItemImageProperties.class)
class TodoItemConfiguration {

    @Bean
    Clock clock() {
        return Clock.systemDefaultZone();
    }

    @Bean
    S3Client s3Client(TodoItemImageProperties properties) {
        return S3Client.builder()
                .endpointOverride(properties.endpoint())
                // Garage serves a virtual-host style bucket only under a configured
                // root_domain.
                .forcePathStyle(true)
                // The default flexible checksum signs the payload in aws-chunked frames.
                // Garage answers "Invalid payload signature" to that.
                .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
                .responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED)
                .region(Region.of(properties.region()))
                .credentialsProvider(credentials(properties))
                .build();
    }

    @Bean
    S3Presigner s3Presigner(TodoItemImageProperties properties) {
        return S3Presigner.builder()
                .endpointOverride(properties.publicEndpoint())
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .region(Region.of(properties.region()))
                .credentialsProvider(credentials(properties))
                .build();
    }

    private static AwsCredentialsProvider credentials(TodoItemImageProperties properties) {
        return StaticCredentialsProvider.create(
                AwsBasicCredentials.create(properties.accessKey(), properties.secretKey()));
    }
}
