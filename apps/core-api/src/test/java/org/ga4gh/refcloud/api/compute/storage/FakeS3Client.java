package org.ga4gh.refcloud.api.compute.storage;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.http.AbortableInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.AbortMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.AbortMultipartUploadResponse;
import software.amazon.awssdk.services.s3.model.CompleteMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CompleteMultipartUploadResponse;
import software.amazon.awssdk.services.s3.model.CompletedPart;
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadResponse;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectResponse;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectsResponse;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.S3Object;
import software.amazon.awssdk.services.s3.model.UploadPartRequest;
import software.amazon.awssdk.services.s3.model.UploadPartResponse;

// an in-memory bucket store with just the calls run storage makes, keyed "bucket/key". list pages are small (2 keys),
// so paging is exercised too
public class FakeS3Client implements S3Client {

    public final Map<String, byte[]> objects = new TreeMap<>();

    public final Map<String, Map<Integer, byte[]>> uploads = new TreeMap<>();

    public int puts;

    public int aborted;

    @Override
    public PutObjectResponse putObject(PutObjectRequest request, RequestBody body) {
        objects.put(request.bucket() + "/" + request.key(), read(body));
        puts++;
        return PutObjectResponse.builder().eTag("\"etag\"").build();
    }

    // suffix ranges ("bytes=-N") only, as run storage reads the end of nextflow.log
    @Override
    public <T> T getObject(GetObjectRequest request, ResponseTransformer<GetObjectResponse, T> transformer) {
        byte[] bytes = existing(request.bucket(), request.key());
        if (request.range() != null) {
            long last = Long.parseLong(request.range().substring("bytes=-".length()));
            if (bytes.length == 0) {
                throw S3Exception.builder()
                        .statusCode(416)
                        .message("range not satisfiable")
                        .build();
            }
            bytes = Arrays.copyOfRange(bytes, (int) Math.max(0, bytes.length - last), bytes.length);
        }
        GetObjectResponse response =
                GetObjectResponse.builder().contentLength((long) bytes.length).build();
        try {
            return transformer.transform(response, AbortableInputStream.create(new ByteArrayInputStream(bytes)));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    @Override
    public ResponseInputStream<GetObjectResponse> getObject(GetObjectRequest request) {
        return getObject(request, ResponseTransformer.toInputStream());
    }

    @Override
    public HeadObjectResponse headObject(HeadObjectRequest request) {
        return HeadObjectResponse.builder()
                .contentLength((long) existing(request.bucket(), request.key()).length)
                .build();
    }

    @Override
    public ListObjectsV2Response listObjectsV2(ListObjectsV2Request request) {
        List<S3Object> matching = new ArrayList<>();
        String start = request.bucket() + "/" + (request.prefix() == null ? "" : request.prefix());
        for (Map.Entry<String, byte[]> entry : objects.entrySet()) {
            String key = entry.getKey().substring(request.bucket().length() + 1);
            if (entry.getKey().startsWith(start)
                    && (request.continuationToken() == null || key.compareTo(request.continuationToken()) > 0)) {
                matching.add(S3Object.builder()
                        .key(key)
                        .size((long) entry.getValue().length)
                        .build());
            }
        }
        boolean truncated = matching.size() > 2;
        List<S3Object> page = truncated ? matching.subList(0, 2) : matching;
        return ListObjectsV2Response.builder()
                .contents(page)
                .isTruncated(truncated)
                .nextContinuationToken(truncated ? page.get(1).key() : null)
                .build();
    }

    @Override
    public DeleteObjectResponse deleteObject(DeleteObjectRequest request) {
        objects.remove(request.bucket() + "/" + request.key());
        return DeleteObjectResponse.builder().build();
    }

    @Override
    public DeleteObjectsResponse deleteObjects(DeleteObjectsRequest request) {
        for (ObjectIdentifier object : request.delete().objects()) {
            objects.remove(request.bucket() + "/" + object.key());
        }
        return DeleteObjectsResponse.builder().build();
    }

    @Override
    public CreateMultipartUploadResponse createMultipartUpload(CreateMultipartUploadRequest request) {
        String uploadId = UUID.randomUUID().toString();
        uploads.put(uploadId, new TreeMap<>());
        return CreateMultipartUploadResponse.builder().uploadId(uploadId).build();
    }

    @Override
    public UploadPartResponse uploadPart(UploadPartRequest request, RequestBody body) {
        uploads.get(request.uploadId()).put(request.partNumber(), read(body));
        return UploadPartResponse.builder()
                .eTag("\"part-" + request.partNumber() + "\"")
                .build();
    }

    @Override
    public CompleteMultipartUploadResponse completeMultipartUpload(CompleteMultipartUploadRequest request) {
        Map<Integer, byte[]> parts = uploads.remove(request.uploadId());
        ByteArrayOutputStream joined = new ByteArrayOutputStream();
        for (CompletedPart part : request.multipartUpload().parts()) {
            joined.writeBytes(parts.get(part.partNumber()));
        }
        objects.put(request.bucket() + "/" + request.key(), joined.toByteArray());
        return CompleteMultipartUploadResponse.builder().build();
    }

    @Override
    public AbortMultipartUploadResponse abortMultipartUpload(AbortMultipartUploadRequest request) {
        uploads.remove(request.uploadId());
        aborted++;
        return AbortMultipartUploadResponse.builder().build();
    }

    @Override
    public String serviceName() {
        return "s3";
    }

    @Override
    public void close() {}

    private byte[] existing(String bucket, String key) {
        byte[] bytes = objects.get(bucket + "/" + key);
        if (bytes == null) {
            throw NoSuchKeyException.builder()
                    .statusCode(404)
                    .message("no such key")
                    .build();
        }
        return bytes;
    }

    private static byte[] read(RequestBody body) {
        try (InputStream in = body.contentStreamProvider().newStream()) {
            return in.readAllBytes();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
