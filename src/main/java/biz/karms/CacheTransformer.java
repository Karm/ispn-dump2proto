package biz.karms;

import biz.karms.protostream.ioc.marshallers.AccuracyMarshaller;
import biz.karms.protostream.ioc.marshallers.AccuracyMarshallerV2;
import biz.karms.protostream.ioc.marshallers.BlacklistedRecordListMarshaller;
import biz.karms.protostream.ioc.marshallers.BlacklistedRecordMarshaller;
import biz.karms.protostream.ioc.marshallers.BlacklistedRecordV2ListMarshaller;
import biz.karms.protostream.ioc.marshallers.BlacklistedRecordV2Marshaller;
import biz.karms.protostream.ioc.marshallers.NameNumberMarshaller;
import biz.karms.protostream.ioc.marshallers.NameNumberMarshallerV2;
import biz.karms.protostream.ioc.marshallers.SourceMarshaller;
import biz.karms.protostream.ioc.marshallers.SourceMarshallerV2;
import biz.karms.protostream.ioc.marshallers.TypeIocIDMarshaller;
import biz.karms.protostream.ioc.marshallers.TypeIocIDMarshallerV2;
import biz.karms.sinkit.ejb.cache.pojo.BlacklistedRecord;
import biz.karms.sinkit.ejb.cache.pojo.BlacklistedRecordV2;
import org.apache.commons.lang3.StringUtils;
import org.infinispan.protostream.FileDescriptorSource;
import org.infinispan.protostream.ProtobufUtil;
import org.infinispan.protostream.SerializationContext;
import org.infinispan.protostream.config.Configuration;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

import static biz.karms.Dump2Proto.attr;
import static biz.karms.Dump2Proto.options;
import static biz.karms.protostream.IoCDumper.BLACKLISTEDV2_RECORD_PROTOBUF;
import static biz.karms.protostream.IoCDumper.BLACKLISTED_RECORD_PROTOBUF;

/**
 * @author Michal Karm Babacek
 */
public class CacheTransformer {

    private static final Logger log = Logger.getLogger(CacheTransformer.class.getName());

    public void transform(final File protobufferIn, final File protobufferOut) {
        log.log(Level.INFO, "Cache transformation processing started.");

        final SerializationContext ctx = ProtobufUtil.newSerializationContext(new Configuration.Builder()
                .setLogOutOfSequenceReads(false)
                .build());
        try {
            ctx.registerProtoFiles(FileDescriptorSource.fromResources(BLACKLISTED_RECORD_PROTOBUF));
        } catch (IOException e) {
            log.log(Level.SEVERE, String.format("File %s was not found. Cannot recover, quitting.", BLACKLISTED_RECORD_PROTOBUF));
            return;
        }
        ctx.registerMarshaller(new TypeIocIDMarshaller());
        ctx.registerMarshaller(new NameNumberMarshaller());
        ctx.registerMarshaller(new SourceMarshaller());
        ctx.registerMarshaller(new AccuracyMarshaller());
        ctx.registerMarshaller(new BlacklistedRecordMarshaller());
        ctx.registerMarshaller(new BlacklistedRecordListMarshaller());

        final ArrayList<BlacklistedRecord> records;

        long start = System.currentTimeMillis();

        if (protobufferIn.exists()) {
            try (InputStream is = new FileInputStream(protobufferIn)) {
                final AtomicBoolean running = new AtomicBoolean(true);
                Runnable spinner = () -> {
                    final Random rng = new Random();
                    while (running.get()) {
                        System.out.print("\033[1K\033[1G");
                        System.out.print("Loading protobuffer... ");
                        for (int i = 0; i < rng.nextInt(80); i++) System.out.print("▍");
                        System.out.flush();
                        try {
                            Thread.sleep(500);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }
                };
                new Thread(spinner).start();
                records = ProtobufUtil.readFrom(ctx, is, ArrayList.class);
                running.set(false);
            } catch (IOException e) {
                e.printStackTrace();
                log.log(Level.SEVERE, String.format("%s being empty / non-deserializable is unexpected. Aborting.", protobufferIn.getAbsolutePath()));
                return;
            }
            System.out.print(System.lineSeparator());
            log.log(Level.INFO, "Deserialization of " + records.size() + " records took " + (System.currentTimeMillis() - start) + " ms.");
        } else {
            log.log(Level.SEVERE, String.format("%s does not exist. Aborting.", protobufferIn.getAbsolutePath()));
            return;
        }

        // final ArrayList<BlacklistedRecord> recordsV2 = new ArrayList<>(records.subList(10, 12));
        // records;
        // final SerializationContext ctxV2 = ctx;
        //records = new ArrayList<>(records.parallelStream().filter(r -> r.getCrc64Hash().equals("1625639120484488996")).collect(Collectors.toList()));
        //final ArrayList<BlacklistedRecord> recordsV2 = new ArrayList<>(records.parallelStream().filter(r -> r.getCrc64Hash().equals("15395186840396682663")).collect(Collectors.toList()));
        //  recordsV2.forEach(r -> log.log(Level.INFO, r.toString()));

/*
int most = 0;
String crc64 = "";
for (BlacklistedRecord b: records) {
    int bits = new BigInteger(b.getCrc64Hash()).bitLength();
    if(bits > most) {
        most = bits;
        crc64 = b.getCrc64Hash();
    }
}

log.log(Level.INFO,"MOST BITS NEEDED: "+most+", crc64:"+crc64);
*/

        final SerializationContext ctxV2 = ProtobufUtil.newSerializationContext(new Configuration.Builder()
                .setLogOutOfSequenceReads(false)
                .build());
        try {
            ctxV2.registerProtoFiles(FileDescriptorSource.fromResources(BLACKLISTEDV2_RECORD_PROTOBUF));
        } catch (IOException e) {
            log.log(Level.SEVERE, String.format("File %s was not found. Cannot recover, quitting.", BLACKLISTEDV2_RECORD_PROTOBUF));
            return;
        }
        ctxV2.registerMarshaller(new TypeIocIDMarshallerV2());
        ctxV2.registerMarshaller(new NameNumberMarshallerV2());
        ctxV2.registerMarshaller(new SourceMarshallerV2());
        ctxV2.registerMarshaller(new AccuracyMarshallerV2());
        ctxV2.registerMarshaller(new BlacklistedRecordV2Marshaller());
        ctxV2.registerMarshaller(new BlacklistedRecordV2ListMarshaller());

        int rSize = records.size();
        final ArrayList<BlacklistedRecordV2> recordsV2 = new ArrayList<>(rSize);
        System.out.print(System.lineSeparator());
        for (int i = 0; i < rSize; i++) {
            System.out.print("\033[1K\033[1G");
            System.out.print(String.format("%d/%d ", i, rSize));
            for (int j = 0; j < i % 80; j++) System.out.print("▍");
            System.out.flush();
            BlacklistedRecord br = records.get(i);

            log.log(Level.INFO, br.toString());

            BlacklistedRecordV2 brv2 = new BlacklistedRecordV2(
                    br.getBlackListedDomainOrIP(),
                    new BigInteger(br.getCrc64Hash()),
                    br.getListed(),
                    br.getSources(),
                    br.getAccuracy(),
                    br.getPresentOnWhiteList());
            recordsV2.add(brv2);

        }
        System.out.print(System.lineSeparator());
        log.log(Level.INFO, String.format("Transformation of %d records took %d ms.", records.size(), (System.currentTimeMillis() - start)));


        start = System.currentTimeMillis();
        try (SeekableByteChannel s = Files.newByteChannel(protobufferOut.toPath(), options, attr)) {
            final AtomicBoolean running = new AtomicBoolean(true);
            Runnable spinner = () -> {
                final Random rng = new Random();
                while (running.get()) {
                    System.out.print("\033[1K\033[1G");
                    System.out.print("Saving protobuffer... ");
                    for (int i = 0; i < rng.nextInt(80); i++) System.out.print("▍");
                    System.out.flush();
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            };
            new Thread(spinner).start();
            s.write(ProtobufUtil.toByteBuffer(ctxV2, recordsV2));
            running.set(false);
            System.out.print(System.lineSeparator());
            log.log(Level.INFO, "Serialization of " + recordsV2.size() + " records took " + (System.currentTimeMillis() - start) + " ms.");
        } catch (IOException e) {
            log.log(Level.SEVERE, "Serialization failed.", e);
        }


    }

    private static void printUsage(final String message) {
        //TODO: log ? Is it TUI or logging?
        System.out.println(String.format("%s-h prints usage\n-i input file\n-o output file", (StringUtils.isNotBlank(message)) ? message + "\n" : ""));
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        final CacheTransformer cacheTransformer = new CacheTransformer();
        File infile = null;
        File outfile = null;
        if (args.length < 4) {
            printUsage(null);
            System.exit(1);
        }
        for (int i = 0; i < args.length; i++) {
            if ("-h".equals(args[i])) {
                printUsage(null);
                System.exit(1);
            }
            if ("-i".equals(args[i])) {
                infile = new File(args[i + 1]);
                if (!infile.exists()) {
                    printUsage(String.format("Input file %s does nto exist.", infile));
                    System.exit(1);
                }
                i++;
            }
            if ("-o".equals(args[i])) {
                outfile = new File(args[i + 1]);
                if (!outfile.getParentFile().exists()) {
                    printUsage(String.format("Output file directory %s does not exist. Are you sure it is a correct path?", outfile.getParentFile()));
                    System.exit(1);
                }
                i++;
            }
        }
        if (infile == null || outfile == null) {
            printUsage("Both input -i and output -o files must be set and exist.");
            System.exit(1);
        }
        if (infile.getAbsolutePath().equals(outfile.getAbsolutePath())) {
            printUsage("Both input -i and output -o files are the same. I cannot work with that.");
            System.exit(1);
        }
        cacheTransformer.transform(infile, outfile);
    }
}
