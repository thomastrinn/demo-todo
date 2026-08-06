package hu.wbstr.todo.item;

import org.springframework.http.ContentDisposition;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.io.InputStream;
import java.net.URI;
import java.util.function.Supplier;

@Component
class TodoItemImageStore {

    private final S3Client client;
    private final S3Presigner presigner;
    private final TodoItemImageProperties properties;

    TodoItemImageStore(S3Client client, S3Presigner presigner, TodoItemImageProperties properties) {
        this.client = client;
        this.presigner = presigner;
        this.properties = properties;
    }

    void put(String key, String contentType, long size, InputStream stream) {
        call(() -> client.putObject(
                PutObjectRequest.builder()
                        .bucket(properties.bucket())
                        .key(key)
                        .contentType(contentType)
                        .contentLength(size)
                        .build(),
                RequestBody.fromInputStream(stream, size)));
    }

    URI presignGet(String key, String contentType, String fileName) {
        ContentDisposition disposition = fileName != null
                ? ContentDisposition.inline().filename(fileName).build()
                : ContentDisposition.inline().build();
        GetObjectRequest get = GetObjectRequest.builder()
                .bucket(properties.bucket())
                .key(key)
                .responseContentType(contentType)
                .responseContentDisposition(disposition.toString())
                .build();
        return call(() -> URI.create(presigner.presignGetObject(GetObjectPresignRequest.builder()
                        .signatureDuration(properties.presignTtl())
                        .getObjectRequest(get)
                        .build())
                .url()
                .toString()));
    }

    void delete(String key) {
        call(() -> client.deleteObject(
                DeleteObjectRequest.builder().bucket(properties.bucket()).key(key).build()));
    }

    private static <T> T call(Supplier<T> action) {
        try {
            return action.get();
        } catch (SdkException exception) {
            throw new ImageStoreUnavailableException(exception);
        }
    }
}
