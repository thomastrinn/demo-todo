package hu.wbstr.todo.item;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.net.URI;
import java.time.Duration;

@ConfigurationProperties("todo.images")
record TodoItemImageProperties(
        URI endpoint,
        // The address the client resolves. It differs from the endpoint as soon as the
        // application runs next to the object store instead of on the host.
        URI publicEndpoint,
        String region,
        String bucket,
        String accessKey,
        String secretKey,
        @DefaultValue("5m") Duration presignTtl
) {

    TodoItemImageProperties {
        if (publicEndpoint == null) {
            publicEndpoint = endpoint;
        }
    }
}
