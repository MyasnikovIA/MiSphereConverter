package ru.miacomsoft;

import java.io.IOException;
import java.nio.file.Path;

public interface FileConverter {
    // Проверка, подходит ли файл для конвертации
    boolean isConvertible(Path source) throws IOException;

    // Сама конвертация
    void convert(Path source, Path target) throws IOException;
}