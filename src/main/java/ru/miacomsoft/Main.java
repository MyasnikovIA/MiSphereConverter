package ru.miacomsoft;

import javax.swing.SwingUtilities;

public class Main {
    public static void main(String[] args) {
        if (args.length == 0) {
            // Запуск графического интерфейса
            SwingUtilities.invokeLater(() -> new ConverterFrame().setVisible(true));
        } else {
            // Запуск в консольном режиме
            CliRunner.run(args);
        }
    }
}