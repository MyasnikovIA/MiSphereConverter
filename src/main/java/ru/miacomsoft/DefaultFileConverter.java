package ru.miacomsoft;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Set;

public class DefaultFileConverter implements FileConverter {

    private static final Set<String> IMAGE_EXT = Set.of(
            "jpg", "jpeg", "png", "bmp", "gif", "tif", "tiff");

    @Override
    public void convert(Path source, Path target) throws IOException {
        String name = source.getFileName().toString();
        int dot = name.lastIndexOf('.');
        String ext = (dot >= 0 ? name.substring(dot + 1) : "").toLowerCase(Locale.ROOT);

        if (IMAGE_EXT.contains(ext)) {
            BufferedImage img = ImageIO.read(source.toFile());
            if (img != null) {
                Path out = replaceExtension(target, "png");
                ImageIO.write(img, "png", out.toFile());
                return;
            }
            // если ImageIO не смог прочитать — падаем в копирование
        }
        // не изображение (или ImageIO не справился) — копируем как есть
        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
    }

    private static Path replaceExtension(Path path, String newExt) {
        String fileName = path.getFileName().toString();
        int dot = fileName.lastIndexOf('.');
        String base = (dot >= 0) ? fileName.substring(0, dot) : fileName;
        return path.resolveSibling(base + "." + newExt);
    }
}