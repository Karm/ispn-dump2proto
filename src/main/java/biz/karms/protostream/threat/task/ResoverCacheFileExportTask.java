package biz.karms.protostream.threat.task;

import biz.karms.Dump2Proto;
import biz.karms.protostream.threat.exception.ResolverProcessingException;
import biz.karms.sinkit.resolver.ResolverConfiguration;
import io.minio.MinioClient;
import io.minio.errors.InsufficientDataException;
import io.minio.errors.InvalidEndpointException;
import io.minio.errors.InvalidPortException;
import io.minio.errors.MinioException;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.StringUtils;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

import static biz.karms.Dump2Proto.*;
import static java.lang.String.format;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

/**
 * Export task which export the given bytes into the file and creates md5 sum file as well
 */
public class ResoverCacheFileExportTask implements ResolverCacheExportTask<ByteBuffer> {
    private static final String fileNameTemplate = "%s_resolver_cache.bin";
    private static final long MIN_VALID_FILE_SIZE_BYTE = 2;

    private static final Logger logger = Logger.getLogger(ResoverCacheFileExportTask.class.getName());

    private final String pathTemplate;

    private final MinioClient minioClient;

    public ResoverCacheFileExportTask() {
        this.pathTemplate = GENERATED_PROTOFILES_DIRECTORY + '/' + fileNameTemplate;
        MinioClient minioClientTmp = null;
        if (!StringUtils.isBlank(Dump2Proto.S3_ENDPOINT)) {
            try {
                minioClientTmp = new MinioClient(Dump2Proto.S3_ENDPOINT, Dump2Proto.S3_ACCESS_KEY, Dump2Proto.S3_SECRET_KEY, true);
            } catch (InvalidEndpointException | InvalidPortException e) {
                logger.log(Level.SEVERE, "MinioClient initialization failed. S3 cannot be used.", e);
            }
        }
        this.minioClient = minioClientTmp;
    }

    /**
     * @see biz.karms.protostream.threat.task.ResolverCacheExportTask#export
     */
    public void export(final ResolverConfiguration resolverConfiguration, final ByteBuffer data) {
        logger.log(Level.INFO, "Entering export...");
        final long start = System.currentTimeMillis();
        final Integer resolverId = Objects.requireNonNull(resolverConfiguration, "resolvers configuration cannot null").getResolverId();

        if (minioClient != null) {
            final String filename = format(fileNameTemplate, resolverId);
            try {
                boolean isExist = minioClient.bucketExists(Dump2Proto.S3_BUCKET_NAME);
                if (isExist) {
                    logger.log(Level.INFO, "Bucket " + Dump2Proto.S3_BUCKET_NAME + " already exist. Using it.");
                } else {
                    logger.log(Level.INFO, "Bucket " + Dump2Proto.S3_BUCKET_NAME + " does not exist. Creating it.");
                    minioClient.makeBucket(Dump2Proto.S3_BUCKET_NAME, Dump2Proto.S3_REGION);
                }
                logger.log(Level.INFO, "S3 upload of file " + filename + " to bucket " + Dump2Proto.S3_BUCKET_NAME + " started.");
                minioClient.putObject(Dump2Proto.S3_BUCKET_NAME, filename, new ProtostreamTransformerTask.BBufferIStream(data), "application/octet-stream");
                logger.log(Level.INFO, "S3 upload of file " + filename + " to bucket " + Dump2Proto.S3_BUCKET_NAME + " finished.");
            } catch (InvalidKeyException e) {
                logger.log(Level.SEVERE, "Check S3 credentials. Upload failed for file: " + filename, e);
            } catch (InsufficientDataException e) {
                logger.log(Level.SEVERE, "Source stream is in error. This should never happen. Upload failed for file: " + filename, e);
            } catch (NoSuchAlgorithmException e) {
                logger.log(Level.SEVERE, "Check S3 endpoint TLS settings. Upload failed for file: " + filename, e);
            } catch (MinioException | IOException e) {
                logger.log(Level.SEVERE, "Endpoint error. Upload failed for file: " + filename, e);
            } catch (XmlPullParserException e) {
                logger.log(Level.SEVERE, "Upload failed for file: " + filename, e);
            }
        }

        if (!Dump2Proto.USE_S3_ONLY) {
            final String path = format(pathTemplate, resolverId);
            final String md5Path = path + ".md5";
            final String tmpPath = path + ".tmp";
            final String tmpMd5Path = md5Path + ".tmp";

            // create tmp file
            try (final SeekableByteChannel byteChannel = Files.newByteChannel(Paths.get(tmpPath), options, attr)) {
                byteChannel.write(data);
            } catch (IOException e) {
                throw new ResolverProcessingException(format("The following exception occurred when the file '%s' was generated", tmpPath), resolverConfiguration,
                        ResolverProcessingTask.EXPORTING);
            }

            // create md5 hash file
            try {
                Files.write(Paths.get(tmpMd5Path), DigestUtils.md5Hex(data.array()).getBytes());
            } catch (IOException e) {
                throw new ResolverProcessingException(format("The following exception occurred when the md5 sum file '%s' was generated", tmpMd5Path), resolverConfiguration,
                        ResolverProcessingTask.EXPORTING);
            }

            // if files are prepared - just switch them to 'latest'
            try {
                if (Paths.get(tmpPath).toFile().length() > MIN_VALID_FILE_SIZE_BYTE) {
                    Files.move(Paths.get(tmpMd5Path), Paths.get(md5Path), REPLACE_EXISTING);
                    Files.move(Paths.get(tmpPath), Paths.get(path), REPLACE_EXISTING);
                } else {
                    throw new ResolverProcessingException(format("Export failed in %d ms, the file %s is smaller or equal to %d bytes and that is certainly invalid.",
                            (System.currentTimeMillis() - start), tmpPath, MIN_VALID_FILE_SIZE_BYTE), resolverConfiguration, ResolverProcessingTask.EXPORTING);
                }
            } catch (IOException e) {
                throw new ResolverProcessingException(format("The following exception occurred when the tmp files '%s'/'%s' were renamed to '%s'/'%s'", tmpPath, tmpMd5Path, path, md5Path), resolverConfiguration,
                        ResolverProcessingTask.EXPORTING);
            }
        }
        logger.log(Level.INFO, "Export finished in " + (System.currentTimeMillis() - start) + " ms. Resolver " + resolverId + " written.");
    }
}
