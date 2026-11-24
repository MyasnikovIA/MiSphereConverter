package ru.miacomsoft;

import java.nio.file.Files;
import java.nio.file.Path;

public class CliRunner {

    public static void run(String[] args) {
        Path inputPath = Path.of(args[0]);

        if (!Files.exists(inputPath)) {
            System.err.println("ОШИБКА: Указанный путь не существует: " + inputPath.toAbsolutePath());
            System.exit(1);
        }

        FisheyeConverter converter = new FisheyeConverter();
        System.out.println("Запуск в консольном режиме...");

        try {
            if (Files.isRegularFile(inputPath)) {
                // --- Режим 1: Конвертация одного файла ---
                Path outputPath;
                if (args.length > 1) {
                    outputPath = Path.of(args[1]);
                } else {
                    // Если выходной файл не указан, добавляем префикс в ту же папку
                    String fileName = inputPath.getFileName().toString();
                    int dotIndex = fileName.lastIndexOf('.');
                    String baseName = (dotIndex > 0) ? fileName.substring(0, dotIndex) : fileName;
                    outputPath = inputPath.resolveSibling("pano_" + baseName + ".jpg");
                }
                System.out.println("Конвертация файла: " + inputPath);
                System.out.println("Сохранение в:     " + outputPath);
                converter.convert(inputPath, outputPath);
                System.out.println("Успешно завершено.");

            } else if (Files.isDirectory(inputPath)) {
                // --- Режим 2: Конвертация каталога ---
                Path outputDir;
                String prefix = "";

                if (args.length > 1) {
                    outputDir = Path.of(args[1]);
                } else {
                    // Если выходной каталог не указан, сохраняем туда же с префиксом
                    outputDir = inputPath;
                    prefix = "pano_";
                }

                System.out.println("Исходный каталог:  " + inputPath);
                System.out.println("Каталог результата: " + outputDir);
                if (!prefix.isEmpty()) {
                    System.out.println("Будет использован префикс: " + prefix);
                }

                DirectoryConverter dc = new DirectoryConverter(
                        converter,
                        System.out::println,  // Лог в консоль
                        null,                 // Прогресс-бар не нужен в консоли
                        true,                 // Включаем проверку формата
                        prefix                // Префикс для файлов
                );
                dc.convert(inputPath, outputDir);
                System.out.println("Обработка каталога завершена.");
            }
        } catch (Exception e) {
            System.err.println("КРИТИЧЕСКАЯ ОШИБКА: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}