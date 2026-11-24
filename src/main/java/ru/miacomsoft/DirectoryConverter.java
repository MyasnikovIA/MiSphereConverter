package ru.miacomsoft;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Stream;

public class DirectoryConverter {

    private final FileConverter fileConverter;
    private final Consumer<String> log;
    private final Consumer<Integer> progressCallback;

    // Флаг для безопасной остановки многопоточного процесса
    private final AtomicBoolean isCancelled = new AtomicBoolean(false);

    public DirectoryConverter(FileConverter fileConverter, Consumer<String> log, Consumer<Integer> progressCallback) {
        this.fileConverter = fileConverter;
        this.log = log;
        this.progressCallback = progressCallback;
    }

    public void cancel() {
        isCancelled.set(true);
    }

    public void convert(Path sourceDir, Path targetDir) throws IOException {
        if (!Files.isDirectory(sourceDir)) {
            throw new IOException("Исходный каталог не найден: " + sourceDir);
        }
        Files.createDirectories(targetDir);

        log.accept("Источник:  " + sourceDir.toAbsolutePath());
        log.accept("Результат: " + targetDir.toAbsolutePath());

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

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);
        AtomicInteger skipCount = new AtomicInteger(0);
        AtomicInteger processedCount = new AtomicInteger(0);

        // Используем parallelStream() для многопоточной обработки (задействует все ядра CPU)
        fileList.parallelStream().forEach(src -> {
            if (isCancelled.get() || Thread.currentThread().isInterrupted()) {
                return; // Пропускаем файл, если нажата остановка
            }

            Path rel = sourceDir.relativize(src);
            Path dst = targetDir.resolve(rel);

            String dstStr = dst.toString();
            dst = Path.of(dstStr.substring(0, dstStr.lastIndexOf('.')) + ".jpg");

            try {
                if (!fileConverter.isConvertible(src)) {
                    skipCount.incrementAndGet();
                    synchronized (log) { log.accept("[SKIP] " + rel + " (не рыбий глаз или уже панорама)"); }
                } else {
                    Path parent = dst.getParent();
                    if (parent != null) {
                        Files.createDirectories(parent);
                    }
                    fileConverter.convert(src, dst);
                    successCount.incrementAndGet();
                    synchronized (log) { log.accept("[OK]   " + rel + " -> " + dst.getFileName()); }
                }
            } catch (Exception ex) {
                failCount.incrementAndGet();
                synchronized (log) { log.accept("[FAIL] " + rel + " : " + ex.getMessage()); }
            }

            // Обновление прогресса
            int current = processedCount.incrementAndGet();
            if (progressCallback != null) {
                int percent = (int) (((double) current / totalFiles) * 100);
                progressCallback.accept(percent);
            }
        });

        if (isCancelled.get()) {
            log.accept("--- Обработка ПРЕРВАНА пользователем ---");
        }

        log.accept("Успешно: " + successCount.get() + ", пропущено: " + skipCount.get() + ", с ошибками: " + failCount.get());
    }
}