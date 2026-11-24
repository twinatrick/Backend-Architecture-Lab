package com.example.BackendArchitectureLab.Util;

import com.example.BackendArchitectureLab.Config.MealProperties;
import com.example.BackendArchitectureLab.Exception.AppException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.Locale;

@Component
@RequiredArgsConstructor
public class MealImageValidator {
    private final MealProperties properties;

    public ValidatedImage validate(MultipartFile upload) throws IOException {
        if (upload == null || upload.isEmpty() || upload.getSize() > properties.getMaxPhotoBytes()) {
            throw new AppException("INVALID_PHOTO_SIZE", "圖片必須有內容且不得超過大小上限", 400);
        }
        Path temporary = Files.createTempFile("meal-image-", ".bin");
        try {
            MessageDigest digest = sha256();
            long total = 0;
            try (InputStream input = new DigestInputStream(upload.getInputStream(), digest);
                 OutputStream output = Files.newOutputStream(temporary)) {
                byte[] buffer = new byte[8192];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    total += count;
                    if (total > properties.getMaxPhotoBytes()) throw new AppException("INVALID_PHOTO_SIZE", "圖片超過大小上限", 400);
                    output.write(buffer, 0, count);
                }
            }
            String type = inspect(temporary);
            return new ValidatedImage(temporary, type, total, HexFormat.of().formatHex(digest.digest()));
        } catch (IOException | RuntimeException exception) {
            Files.deleteIfExists(temporary);
            throw exception;
        }
    }

    private String inspect(Path file) throws IOException {
        try (ImageInputStream input = ImageIO.createImageInputStream(file.toFile())) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) invalid();
            ImageReader reader = readers.next();
            try {
                String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                if (!format.equals("jpeg") && !format.equals("png")) invalid();
                reader.setInput(input, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width < 1 || height < 1 || width > properties.getMaxDimension() || height > properties.getMaxDimension()
                        || (long) width * height > properties.getMaxPixels()) invalid();
                // 縮減解碼輸出，避免一次配置二千萬像素的完整點陣圖。
                ImageReadParam parameters = reader.getDefaultReadParam();
                int sample = Math.max(1, (Math.max(width, height) + 1023) / 1024);
                parameters.setSourceSubsampling(sample, sample, 0, 0);
                BufferedImage decoded = reader.read(0, parameters);
                if (decoded == null) invalid();
                decoded.flush();
                return format.equals("jpeg") ? "image/jpeg" : "image/png";
            } finally {
                reader.dispose();
            }
        }
    }

    private static void invalid() {
        throw new AppException("INVALID_PHOTO_FORMAT", "僅接受可解碼且尺寸符合限制的 JPEG 或 PNG", 400);
    }

    public static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("執行環境缺少 SHA-256", exception);
        }
    }

    public record ValidatedImage(Path file, String contentType, long byteSize, String hash) implements AutoCloseable {
        @Override
        public void close() throws IOException {
            Files.deleteIfExists(file);
        }
    }
}
