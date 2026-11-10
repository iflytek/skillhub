package com.iflytek.skillhub.service;

import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import org.springframework.stereotype.Component;

/** Decodes a final crop and writes fresh pixels, without client-supplied metadata. */
@Component
public class SkillAvatarImageProcessor {
    public static final int MAX_BYTES = 2 * 1024 * 1024;
    public static final int MAX_SIDE = 1024;

    public byte[] normalize(byte[] input) {
        return normalize(input, true);
    }

    /** Shared safe raster normalization; branding logos may be rectangular. */
    public byte[] normalize(byte[] input, boolean requireSquare) {
        if (input == null || input.length == 0 || input.length > MAX_BYTES) {
            throw invalidImage();
        }
        boolean png = input.length >= 8 && (input[0] & 0xff) == 0x89
                && input[1] == 'P' && input[2] == 'N' && input[3] == 'G'
                && (input[4] & 0xff) == 13 && input[5] == 10 && (input[6] & 0xff) == 26 && input[7] == 10;
        boolean jpeg = input.length >= 3 && (input[0] & 0xff) == 0xff
                && (input[1] & 0xff) == 0xd8 && (input[2] & 0xff) == 0xff;
        if (!png && !jpeg) {
            throw invalidImage();
        }
        try (ImageInputStream stream = ImageIO.createImageInputStream(new ByteArrayInputStream(input))) {
            if (stream == null) {
                throw invalidImage();
            }
            var readers = ImageIO.getImageReaders(stream);
            if (!readers.hasNext()) {
                throw invalidImage();
            }
            ImageReader reader = readers.next();
            try {
                if (!((png && "png".equalsIgnoreCase(reader.getFormatName()))
                        || (jpeg && "jpeg".equalsIgnoreCase(reader.getFormatName())))) {
                    throw invalidImage();
                }
                reader.setInput(stream, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width < 1 || height < 1 || (requireSquare && width != height) || width > MAX_SIDE || height > MAX_SIDE) {
                    throw invalidImage();
                }
                BufferedImage decoded = reader.read(0);
                if (decoded == null) {
                    throw invalidImage();
                }
                BufferedImage clean = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
                Graphics2D graphics = clean.createGraphics();
                try {
                    graphics.drawImage(decoded, 0, 0, null);
                } finally {
                    graphics.dispose();
                }
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                if (!ImageIO.write(clean, "png", output) || output.size() > MAX_BYTES) {
                    throw invalidImage();
                }
                return output.toByteArray();
            } finally {
                reader.dispose();
            }
        } catch (IOException | IllegalArgumentException failure) {
            throw invalidImage();
        }
    }

    private DomainBadRequestException invalidImage() {
        return new DomainBadRequestException("error.skill.avatar.invalidImage");
    }
}
