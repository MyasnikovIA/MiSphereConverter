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
    private final boolean checkBeforeConvert;
    private final String outputPrefix;

    private final AtomicBoolean isCancelled = new AtomicBoolean(false);

    public DirectoryConverter(FileConverter fileConverter, Consumer<String> log,
                              Consumer<Integer> progressCallback, boolean checkBeforeConvert,
                              String outputPrefix) {
        this.fileConverter = fileConverter;
        this.log = log;
        this.progressCallback = progressCallback;
        this.checkBeforeConvert = checkBeforeConvert;
        this.outputPrefix = outputPrefix != null ? outputPrefix : "";
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
        log.accept("Предварительная проверка файлов: " + (checkBeforeConvert ? "ВКЛЮЧЕНА" : "ОТКЛЮЧЕНА"));
        if (!outputPrefix.isEmpty()) {
            log.accept("Префикс результирующих файлов: " + outputPrefix);
        }

        List<Path> fileList = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(sourceDir)) {
            walk.filter(Files::isRegularFile)
                    .filter(p -> p.toString().toLowerCase().matches(".*\\.(png|jpg|jpeg)$"))
                    // Пропускаем файлы, которые уже имеют наш префикс (если сохраняем в ту же папку)
                    .filter(p -> outputPrefix.isEmpty() || !p.getFileName().toString().startsWith(outputPrefix))
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

        fileList.parallelStream().forEach(src -> {
            if (isCancelled.get() || Thread.currentThread().isInterrupted()) {
                return;
            }

            Path rel = sourceDir.relativize(src);
            Path dst = targetDir.resolve(rel);

            // Применяем префикс к имени файла, если он задан
            if (!outputPrefix.isEmpty()) {
                dst = dst.resolveSibling(outputPrefix + dst.getFileName().toString());
            }

            String dstStr = dst.toString();
            dst = Path.of(dstStr.substring(0, dstStr.lastIndexOf('.')) + ".jpg");

            try {
                boolean shouldConvert = true;
                if (checkBeforeConvert) {
                    shouldConvert = fileConverter.isConvertible(src);
                }

                if (!shouldConvert) {
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