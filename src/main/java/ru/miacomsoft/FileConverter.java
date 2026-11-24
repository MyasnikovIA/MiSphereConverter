package ru.miacomsoft;

import java.io.IOException;
import java.nio.file.Path;

public interface FileConverter {
    void convert(Path source, Path target) throws IOException;
}