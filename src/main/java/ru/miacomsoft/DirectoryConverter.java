package ru.miacomsoft;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;

public class DirectoryConverter {

    private final FileConverter fileConverter;
    private final Consumer<String> log;
    private final Consumer<Integer> progressCallback; // Callback для обновления прогресс-бара

    public DirectoryConverter(FileConverter fileConverter, Consumer<String> log, Consumer<Integer> progressCallback) {
        this.fileConverter = fileConverter;
        this.log = log;
        this.progressCallback = progressCallback;
    }

    public void convert(Path sourceDir, Path targetDir) throws IOException {
        if (!Files.isDirectory(sourceDir)) {
            throw new IOException("Исходный каталог не найден: " + sourceDir);
        }
        Files.createDirectories(targetDir);

        log.accept("Источник:  " + sourceDir.toAbsolutePath());
        log.accept("Результат: " + targetDir.toAbsolutePath());

        // Сначала собираем все файлы в список, чтобы знать общее количество для прогресс-бара
        List<Path> fileList = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(sourceDir)) {
            walk.filter(Files::isRegularFile)
                    .filter(p -> p.toString().toLowerCase().matches(".*\\.(png|jpg|jpeg)$"))
                    .forEach(fileList::add);
        }

        int totalFiles = fileList.size();
        if (totalFiles == 0) {
            log.accept("В исходном каталоге не найдено подходящих изображений.");
            if (progressCallback != null) progressCallback.accept(100);
            return;
        }

        int[] counter = {0, 0}; // [OK, FAIL]
        int processed = 0;

        for (Path src : fileList) {
            // Проверка на остановку
            if (Thread.currentThread().isInterrupted()) {
                log.accept("--- Обработка ПРЕРВАНА пользователем ---");
                break;
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

            processed++;
            // Вычисляем процент и отправляем в интерфейс
            if (progressCallback != null) {
                int percent = (int) (((double) processed / totalFiles) * 100);
                progressCallback.accept(percent);
            }
        }

        log.accept("Успешно: " + counter[0] + ", с ошибками: " + counter[1]);
    }
}