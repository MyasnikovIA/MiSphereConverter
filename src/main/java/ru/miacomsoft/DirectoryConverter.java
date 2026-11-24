package ru.miacomsoft;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.function.Consumer;
import java.util.stream.Stream;

public class DirectoryConverter {

    private final FileConverter fileConverter;
    private final Consumer<String> log;

    public DirectoryConverter(FileConverter fileConverter, Consumer<String> log) {
        this.fileConverter = fileConverter;
        this.log = log;
    }

    public void convert(Path sourceDir, Path targetDir) throws IOException {
        if (!Files.isDirectory(sourceDir)) {
            throw new IOException("Исходный каталог не найден: " + sourceDir);
        }
        Files.createDirectories(targetDir);

        log.accept("Источник:  " + sourceDir.toAbsolutePath());
        log.accept("Результат: " + targetDir.toAbsolutePath());

        int[] counter = {0, 0};

        try (Stream<Path> walk = Files.walk(sourceDir)) {
            // Используем итератор вместо forEach, чтобы можно было прервать цикл
            Iterator<Path> it = walk.filter(Files::isRegularFile).iterator();
            while (it.hasNext()) {
                // Проверка: если пользователь нажал "Остановить", поток будет прерван
                if (Thread.currentThread().isInterrupted()) {
                    log.accept("--- Обработка ПРЕРВАНА пользователем ---");
                    break;
                }

                Path src = it.next();

                // Конвертируем только изображения
                String fileName = src.getFileName().toString().toLowerCase();
                if (!fileName.matches(".*\\.(png|jpg|jpeg)$")) {
                    continue;
                }

                Path rel = sourceDir.relativize(src);
                Path dst = targetDir.resolve(rel);

                // Меняем расширение на .jpg для результата
                String dstStr = dst.toString();
                dst = Path.of(dstStr.substring(0, dstStr.lastIndexOf('.')) + ".jpg");

                try {
                    Path parent = dst.getParent();
                    if (parent != null) {
                        Files.createDirectories(parent);
                    }
                    fileConverter.convert(src, dst);
                    counter[0]++;
                    log.accept("[OK]   " + rel + " -> " + dst.getFileName());
                } catch (Exception ex) {
                    counter[1]++;
                    log.accept("[FAIL] " + rel + " : " + ex.getMessage());
                }
            }
        }

        log.accept("Успешно: " + counter[0] + ", с ошибками: " + counter[1]);
    }
}