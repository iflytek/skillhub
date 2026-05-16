package com.iflytek.skillhub.domain.media;

import java.util.List;

/**
 * Coordinates upload, lookup and visibility for media assets.
 *
 * <p>Storage IO is delegated to {@link MediaStorage} so the domain layer can
 * stay free of S3 / MinIO specifics. {@link MediaValidator} is used to enforce
 * file-header and size policy before persistence.
 */
public class MediaAssetService {

    private final MediaAssetRepository repository;
    private final MediaValidator validator;
    private final MediaStorage storage;
    private final MediaHasher hasher;

    public MediaAssetService(MediaAssetRepository repository,
                             MediaValidator validator,
                             MediaStorage storage,
                             MediaHasher hasher) {
        this.repository = repository;
        this.validator = validator;
        this.storage = storage;
        this.hasher = hasher;
    }

    public MediaAsset upload(UploadCommand command) {
        if (command.bytes() == null || command.bytes().length == 0) {
            throw new MediaException("error.media.empty");
        }
        byte[] header = new byte[Math.min(16, command.bytes().length)];
        System.arraycopy(command.bytes(), 0, header, 0, header.length);

        MediaType detected = validator.validateAndClassify(header, command.bytes().length, command.contentType());
        String sha256 = hasher.sha256(command.bytes());
        String objectKey = "media/" + command.ownerType().name().toLowerCase() + "/" + command.ownerId() + "/" + sha256
                + extensionFor(command.contentType(), detected);
        storage.put(objectKey, command.bytes(), command.contentType());

        MediaAsset asset = new MediaAsset(command.ownerType(), command.ownerId(),
                detected, command.role(), objectKey, command.contentType(),
                command.bytes().length, sha256, command.uploader());
        asset.setAltText(command.altText());
        asset.setFilePath(command.filename());
        return repository.save(asset);
    }

    public MediaAsset get(Long id) {
        return repository.findById(id).orElseThrow(() -> new MediaException("error.media.notFound"));
    }

    public List<MediaAsset> listByOwner(MediaOwnerType ownerType, Long ownerId) {
        return repository.findByOwner(ownerType, ownerId);
    }

    public byte[] read(Long id) {
        MediaAsset asset = get(id);
        return storage.get(asset.getObjectKey());
    }

    private String extensionFor(String contentType, MediaType detected) {
        return switch (detected) {
            case GIF -> ".gif";
            case IMAGE -> switch (contentType.toLowerCase()) {
                case "image/png" -> ".png";
                case "image/webp" -> ".webp";
                case "image/jpeg", "image/jpg" -> ".jpg";
                default -> ".bin";
            };
        };
    }

    public record UploadCommand(MediaOwnerType ownerType,
                                Long ownerId,
                                MediaAssetRole role,
                                byte[] bytes,
                                String contentType,
                                String filename,
                                String altText,
                                String uploader) {}

    /** Storage adapter — implementations live in {@code skillhub-storage}. */
    public interface MediaStorage {
        void put(String key, byte[] bytes, String contentType);

        byte[] get(String key);
    }

    /** Hashing seam so unit tests can stub deterministically. */
    public interface MediaHasher {
        String sha256(byte[] bytes);
    }
}
