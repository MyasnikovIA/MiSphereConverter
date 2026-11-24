package ru.miacomsoft;

import javax.swing.SwingUtilities;

public class Main {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new ConverterFrame().setVisible(true));
    }
}