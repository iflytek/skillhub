package com.iflytek.skillhub.service.bundle;

import com.iflytek.skillhub.config.SkillPublishProperties;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.storage.ObjectStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Stages and analyzes one uploaded Bundle without retaining the expanded archive in heap memory. */
@Service
public class SkillSuiteBundleArchiveService {

    private static final Logger log = LoggerFactory.getLogger(SkillSuiteBundleArchiveService.class);
    private static final int BUFFER_SIZE = 8192;
    private static final int MAX_MEMBERS = 100;
    private static final int ZIP_CENTRAL_HEADER_SIZE = 46;
    private static final int ZIP_EOCD_MIN_SIZE = 22;
    private static final int ZIP_EOCD_MAX_SEARCH = 65_557;
    private static final int UNIX_FILE_TYPE_MASK = 0170000;
    private static final int UNIX_SYMBOLIC_LINK = 0120000;

    private final SkillSuiteBundlePackageAnalyzer analyzer;
    private final ObjectStorageService objectStorageService;
    private final long maxArchiveSize;
    private final long maxExpandedSize;
    private final long maxSingleFileSize;
    private final int maxFileCount;

    public SkillSuiteBundleArchiveService(
            SkillSuiteBundlePackageAnalyzer analyzer,
            ObjectStorageService objectStorageService,
            SkillPublishProperties properties
    ) {
        this.analyzer = analyzer;
        this.objectStorageService = objectStorageService;
        this.maxArchiveSize = properties.getMaxPackageSize();
        this.maxExpandedSize = properties.getMaxPackageSize();
        this.maxSingleFileSize = properties.getMaxSingleFileSize();
        this.maxFileCount = Math.multiplyExact(properties.getMaxFileCount(), MAX_MEMBERS);
    }

    public StagedBundleAnalysis stageAndAnalyze(MultipartFile upload) throws IOException {
        if (upload == null || upload.isEmpty()) {
            throw invalid("Bundle archive is empty");
        }
        if (upload.getSize() > maxArchiveSize) {
            throw invalid("Bundle archive exceeds max compressed size " + maxArchiveSize);
        }

        String stagingId = UUID.randomUUID().toString();
        String prefix = "temporary/suite-bundles/" + stagingId;
        String archiveObjectKey = prefix + "/bundle.zip";
        Path tempDirectory = Files.createTempDirectory("skillhub-suite-bundle-");
        Path archivePath = tempDirectory.resolve("bundle.zip");
        List<Path> localEntries = new ArrayList<>();
        List<String> uploadedObjectKeys = new ArrayList<>();

        try {
            String archiveSha256 = copyUpload(upload, archivePath);
            rejectSymbolicLinks(archivePath);
            try (InputStream archiveInput = Files.newInputStream(archivePath)) {
                objectStorageService.putObject(
                        archiveObjectKey, archiveInput, Files.size(archivePath), "application/zip");
            }
            uploadedObjectKeys.add(archiveObjectKey);

            List<SkillSuiteBundleStagedEntry> stagedEntries = extractEntries(
                    archivePath, tempDirectory, prefix, localEntries, uploadedObjectKeys);
            SkillSuiteBundlePackageAnalyzer.BundleAnalysis analysis = analyzer.analyze(stagedEntries);
            if (!analysis.confirmable()) {
                deleteUploaded(uploadedObjectKeys);
                return new StagedBundleAnalysis(null, null, analysis, List.of());
            }
            return new StagedBundleAnalysis(
                    archiveObjectKey, archiveSha256, analysis, List.copyOf(uploadedObjectKeys));
        } catch (IOException | RuntimeException exception) {
            deleteUploaded(uploadedObjectKeys);
            throw exception;
        } finally {
            for (Path localEntry : localEntries) {
                Files.deleteIfExists(localEntry);
            }
            Files.deleteIfExists(archivePath);
            Files.deleteIfExists(tempDirectory);
        }
    }

    private String copyUpload(MultipartFile upload, Path archivePath) throws IOException {
        MessageDigest digest = sha256();
        long copied;
        try (InputStream raw = upload.getInputStream();
             DigestInputStream input = new DigestInputStream(raw, digest);
             OutputStream output = new BufferedOutputStream(Files.newOutputStream(archivePath))) {
            copied = copyBounded(input, output, maxArchiveSize, "Bundle archive");
        }
        if (upload.getSize() >= 0 && upload.getSize() != copied) {
            throw invalid("Bundle archive size changed during upload");
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private List<SkillSuiteBundleStagedEntry> extractEntries(
            Path archivePath,
            Path tempDirectory,
            String objectPrefix,
            List<Path> localEntries,
            List<String> uploadedObjectKeys
    ) throws IOException {
        List<SkillSuiteBundleStagedEntry> staged = new ArrayList<>();
        long expandedSize = 0L;
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(archivePath))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory() || entry.getName().endsWith("/") || entry.getName().endsWith("\\")) {
                    zip.closeEntry();
                    continue;
                }
                if (isOsMetadata(entry.getName())) {
                    zip.closeEntry();
                    continue;
                }
                if (staged.size() >= maxFileCount) {
                    throw invalid("Bundle contains more than " + maxFileCount + " files");
                }

                int position = staged.size();
                Path localPath = tempDirectory.resolve("entry-" + position);
                localEntries.add(localPath);
                MessageDigest digest = sha256();
                long size;
                try (OutputStream output = new BufferedOutputStream(Files.newOutputStream(localPath))) {
                    size = copyZipEntry(zip, output, digest, entry.getName());
                }
                expandedSize = Math.addExact(expandedSize, size);
                if (expandedSize > maxExpandedSize) {
                    throw invalid("Bundle expanded content exceeds max size " + maxExpandedSize);
                }

                String objectKey = objectPrefix + "/entries/" + position;
                String contentType = contentType(entry.getName());
                try (InputStream entryInput = Files.newInputStream(localPath)) {
                    objectStorageService.putObject(objectKey, entryInput, size, contentType);
                }
                uploadedObjectKeys.add(objectKey);
                String sha256 = HexFormat.of().formatHex(digest.digest());
                staged.add(new SkillSuiteBundleStagedEntry(
                        entry.getName(), size, contentType, sha256, objectKey,
                        () -> Files.newInputStream(localPath)));
                zip.closeEntry();
            }
        } catch (ArithmeticException exception) {
            throw invalid("Bundle expanded content size overflow");
        }
        return staged;
    }

    private long copyZipEntry(
            InputStream input, OutputStream output, MessageDigest digest, String path
    ) throws IOException {
        byte[] buffer = new byte[BUFFER_SIZE];
        long copied = 0L;
        int read;
        while ((read = input.read(buffer)) != -1) {
            copied += read;
            if (copied > maxSingleFileSize) {
                throw invalid("Bundle file exceeds max size: " + path);
            }
            digest.update(buffer, 0, read);
            output.write(buffer, 0, read);
        }
        return copied;
    }

    private long copyBounded(
            InputStream input, OutputStream output, long maximum, String subject
    ) throws IOException {
        byte[] buffer = new byte[BUFFER_SIZE];
        long copied = 0L;
        int read;
        while ((read = input.read(buffer)) != -1) {
            copied += read;
            if (copied > maximum) {
                throw invalid(subject + " exceeds max size " + maximum);
            }
            output.write(buffer, 0, read);
        }
        return copied;
    }

    /** java.util.zip does not expose Unix modes, so inspect central-directory attributes directly. */
    private void rejectSymbolicLinks(Path archivePath) throws IOException {
        try (FileChannel channel = FileChannel.open(archivePath, StandardOpenOption.READ)) {
            long fileSize = channel.size();
            int tailLength = (int) Math.min(fileSize, ZIP_EOCD_MAX_SEARCH);
            ByteBuffer tail = ByteBuffer.allocate(tailLength).order(ByteOrder.LITTLE_ENDIAN);
            readFully(channel, tail, fileSize - tailLength);
            int eocd = findEndOfCentralDirectory(tail);
            if (eocd < 0 || eocd + ZIP_EOCD_MIN_SIZE > tailLength) {
                throw invalid("Invalid ZIP end-of-central-directory record");
            }
            int entryCount = Short.toUnsignedInt(tail.getShort(eocd + 10));
            long centralSize = Integer.toUnsignedLong(tail.getInt(eocd + 12));
            long centralOffset = Integer.toUnsignedLong(tail.getInt(eocd + 16));
            if (entryCount == 0xffff || centralSize == 0xffffffffL || centralOffset == 0xffffffffL) {
                throw invalid("ZIP64 Bundle archives are not supported");
            }
            if (centralOffset + centralSize > fileSize) {
                throw invalid("Invalid ZIP central-directory bounds");
            }

            long cursor = centralOffset;
            for (int index = 0; index < entryCount; index++) {
                ByteBuffer header = ByteBuffer.allocate(ZIP_CENTRAL_HEADER_SIZE).order(ByteOrder.LITTLE_ENDIAN);
                readFully(channel, header, cursor);
                if (header.getInt(0) != 0x02014b50) {
                    throw invalid("Invalid ZIP central-directory entry");
                }
                int creatorSystem = Byte.toUnsignedInt(header.get(5));
                int nameLength = Short.toUnsignedInt(header.getShort(28));
                int extraLength = Short.toUnsignedInt(header.getShort(30));
                int commentLength = Short.toUnsignedInt(header.getShort(32));
                long externalAttributes = Integer.toUnsignedLong(header.getInt(38));
                int unixMode = (int) (externalAttributes >>> 16);
                if (creatorSystem == 3 && (unixMode & UNIX_FILE_TYPE_MASK) == UNIX_SYMBOLIC_LINK) {
                    throw invalid("Bundle archive must not contain symbolic links");
                }
                cursor += ZIP_CENTRAL_HEADER_SIZE + nameLength + extraLength + commentLength;
                if (cursor > centralOffset + centralSize) {
                    throw invalid("Invalid ZIP central-directory entry bounds");
                }
            }
        }
    }

    private void readFully(FileChannel channel, ByteBuffer buffer, long position) throws IOException {
        while (buffer.hasRemaining()) {
            int read = channel.read(buffer, position + buffer.position());
            if (read < 0) {
                throw invalid("Unexpected end of ZIP archive");
            }
        }
    }

    private int findEndOfCentralDirectory(ByteBuffer tail) {
        byte[] bytes = tail.array();
        int signature = 0x06054b50;
        for (int index = bytes.length - ZIP_EOCD_MIN_SIZE; index >= 0; index--) {
            if ((bytes[index] & 0xff) == (signature & 0xff)
                    && (bytes[index + 1] & 0xff) == ((signature >>> 8) & 0xff)
                    && (bytes[index + 2] & 0xff) == ((signature >>> 16) & 0xff)
                    && (bytes[index + 3] & 0xff) == ((signature >>> 24) & 0xff)) {
                int commentLength = Short.toUnsignedInt(tail.getShort(index + 20));
                if (index + ZIP_EOCD_MIN_SIZE + commentLength == bytes.length) {
                    return index;
                }
            }
        }
        return -1;
    }

    private String contentType(String path) {
        String lower = path.toLowerCase();
        if (lower.endsWith(".py")) return "text/x-python";
        if (lower.endsWith(".json")) return "application/json";
        if (lower.endsWith(".yaml") || lower.endsWith(".yml")) return "application/x-yaml";
        if (lower.endsWith(".txt")) return "text/plain";
        if (lower.endsWith(".md")) return "text/markdown";
        if (lower.endsWith(".html")) return "text/html";
        if (lower.endsWith(".css")) return "text/css";
        if (lower.endsWith(".csv")) return "text/csv";
        if (lower.endsWith(".xml")) return "application/xml";
        if (lower.endsWith(".js") || lower.endsWith(".cjs") || lower.endsWith(".mjs")) {
            return "text/javascript";
        }
        if (lower.endsWith(".ts")) return "text/typescript";
        if (lower.endsWith(".sh") || lower.endsWith(".bash") || lower.endsWith(".zsh")) {
            return "text/x-shellscript";
        }
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".gif")) return "image/gif";
        if (lower.endsWith(".svg")) return "image/svg+xml";
        if (lower.endsWith(".webp")) return "image/webp";
        if (lower.endsWith(".ico")) return "image/x-icon";
        if (lower.endsWith(".pdf")) return "application/pdf";
        if (lower.endsWith(".toml")) return "application/toml";
        return "application/octet-stream";
    }

    private boolean isOsMetadata(String path) {
        String normalized = path.replace('\\', '/');
        if (normalized.equals("__MACOSX") || normalized.startsWith("__MACOSX/")) {
            return true;
        }
        int slash = normalized.lastIndexOf('/');
        String fileName = slash < 0 ? normalized : normalized.substring(slash + 1);
        return fileName.equals(".DS_Store") || fileName.startsWith("._");
    }

    private MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private void deleteUploaded(List<String> objectKeys) {
        if (objectKeys.isEmpty()) {
            return;
        }
        try {
            objectStorageService.deleteObjects(List.copyOf(objectKeys));
        } catch (RuntimeException exception) {
            log.warn("Failed to clean staged Suite Bundle objects: count={}", objectKeys.size(), exception);
        }
    }

    private DomainBadRequestException invalid(String detail) {
        return new DomainBadRequestException("error.suite.bundle.manifest.invalid", detail);
    }

    public record StagedBundleAnalysis(
            String archiveObjectKey,
            String archiveSha256,
            SkillSuiteBundlePackageAnalyzer.BundleAnalysis analysis,
            List<String> objectKeys
    ) {
    }
}
