package biz.karms.protostream.threat.task;

import biz.karms.protostream.threat.exception.ResolverProcessingException;
import biz.karms.sinkit.resolver.ResolverConfiguration;
import org.apache.commons.codec.digest.DigestUtils;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

import static biz.karms.Dump2Proto.GENERATED_PROTOFILES_DIRECTORY;
import static biz.karms.Dump2Proto.attr;
import static biz.karms.Dump2Proto.options;
import static java.lang.String.format;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

/**
 * Export task which export the given bytes into the file and creates md5 sum file as well
 */
public class ResoverCacheFileExportTask implements ResolverCacheExportTask<ByteBuffer> {
    private static final String fileNameTemplate = "/%s_resolver_cache.bin";

    private static final Logger logger = Logger.getLogger(ResoverCacheFileExportTask.class.getName());

    private final String pathTemplate;

    public ResoverCacheFileExportTask() {
        this.pathTemplate = GENERATED_PROTOFILES_DIRECTORY + fileNameTemplate;
    }

    /**
     * @see biz.karms.protostream.threat.task.ResolverCacheExportTask#export
     */
    public void export(ResolverConfiguration resolverConfiguration, final ByteBuffer data) {
        logger.log(Level.INFO, "Entering export...");
        final long start = System.currentTimeMillis();
        final Integer resolverId = Objects.requireNonNull(resolverConfiguration, "resolvers configuration cannot null").getResolverId();

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
            Files.move(Paths.get(tmpMd5Path), Paths.get(md5Path), REPLACE_EXISTING);
            Files.move(Paths.get(tmpPath), Paths.get(path), REPLACE_EXISTING);
        } catch (IOException e) {
            throw new ResolverProcessingException(format("The following exception occurred when the tmp files '%s'/'%s' were renamed to '%s'/'%s'", tmpPath, tmpMd5Path, path, md5Path), resolverConfiguration,
                    ResolverProcessingTask.EXPORTING);
        }

        logger.log(Level.INFO, "export finished in " + (System.currentTimeMillis() - start) + " ms.");
    }
}
