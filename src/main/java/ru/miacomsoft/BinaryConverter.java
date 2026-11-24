package ru.miacomsoft;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Интерфейс для использования конвертера как библиотеки.
 * Позволяет обрабатывать бинарные данные без привязки к файловой системе,
 * как в синхронном, так и в асинхронном режимах.
 */
public interface BinaryConverter {

    /**
     * Проверка бинарных данных на соответствие формату.
     */
    boolean isConvertible(byte[] imageData);

    /**
     * Синхронная конвертация бинарных данных.
     * @return byte[] с результатом (JPEG) или null, если файл не подошел
     */
    byte[] convert(byte[] imageData) throws IOException;

    // =================================================================
    // СИНХРОННЫЙ РЕЖИМ (Работа с потоками)
    // =================================================================

    default void convertStream(InputStream input, OutputStream output, boolean checkFormat, Consumer<String> logCallback) throws IOException {
        byte[] inputData = input.readAllBytes();

        if (checkFormat) {
            if (!isConvertible(inputData)) {
                if (logCallback != null) logCallback.accept("Файл не является подходящим исходником (пропущен).");
                return;
            }
        }

        byte[] result = convert(inputData);
        if (result != null) {
            output.write(result);
            output.flush();
            if (logCallback != null) logCallback.accept("Конвертация потока успешно завершена.");
        } else {
            if (logCallback != null) logCallback.accept("Не удалось сконвертировать данные.");
        }
    }

    // =================================================================
    // АСИНХРОННЫЙ РЕЖИМ (Callback / CompletableFuture)
    // =================================================================

    /**
     * Асинхронная конвертация байтового массива.
     * Возвращает CompletableFuture, который завершится результатом (или null).
     */
    default CompletableFuture<byte[]> convertAsync(byte[] imageData, boolean checkFormat, Consumer<String> logCallback) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (checkFormat && !isConvertible(imageData)) {
                    if (logCallback != null) logCallback.accept("Файл не является подходящим исходником (пропущен).");
                    return null;
                }
                return convert(imageData);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    /**
     * Асинхронная конвертация с колбэками успеха и ошибки.
     */
    default void convertAsync(byte[] imageData, boolean checkFormat,
                              Consumer<String> logCallback,
                              Consumer<byte[]> onSuccess,
                              Consumer<Throwable> onError) {
        convertAsync(imageData, checkFormat, logCallback)
                .thenAccept(onSuccess)
                .exceptionally(ex -> {
                    if (onError != null) onError.accept(ex);
                    return null;
                });
    }

    /**
     * Асинхронная конвертация файла в файл.
     * @param onFinish Колбэк, который запустится после завершения (в том же фоновом потоке).
     */
    default CompletableFuture<Void> convertFileAsync(Path source, Path target, boolean checkFormat,
                                                     Consumer<String> logCallback, Runnable onFinish) {
        return CompletableFuture.runAsync(() -> {
            try {
                byte[] inputData = Files.readAllBytes(source);
                if (checkFormat && !isConvertible(inputData)) {
                    if (logCallback != null) logCallback.accept("Файл не является подходящим исходником (пропущен).");
                    return;
                }
                byte[] result = convert(inputData);
                if (result != null) {
                    if (target.getParent() != null) Files.createDirectories(target.getParent());
                    Files.write(target, result);
                    if (logCallback != null) logCallback.accept("Конвертация файла успешно завершена.");
                }
            } catch (IOException e) {
                if (logCallback != null) logCallback.accept("Ошибка: " + e.getMessage());
                throw new RuntimeException(e);
            } finally {
                if (onFinish != null) onFinish.run();
            }
        });
    }
}