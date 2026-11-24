package ru.miacomsoft;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.nio.file.Path;
import java.util.List;
import java.util.prefs.Preferences;

public class ConverterFrame extends JFrame {

    private static final String PREF_SOURCE = "sourceDir";
    private static final String PREF_TARGET = "targetDir";

    private final Preferences prefs;
    private final JTextField sourceField = new JTextField(30);
    private final JTextField targetField = new JTextField(30);
    private final JTextArea logArea = new JTextArea(15, 70);

    private final JButton convertButton = new JButton("Конвертировать в панораму");
    private final JButton stopButton = new JButton("Остановить");

    // Ссылка на текущий рабочий поток, чтобы можно было его прервать
    private SwingWorker<Void, String> currentWorker;

    public ConverterFrame() {
        super("MiSphere Converter (360 -> Панорама)");
        prefs = Preferences.userNodeForPackage(ConverterFrame.class);

        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout(10, 10));
        ((JComponent) getContentPane()).setBorder(new EmptyBorder(10, 10, 10, 10));

        // ----- Верхняя панель (выбор путей) -----
        JPanel top = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(3, 3, 3, 3);
        c.fill = GridBagConstraints.HORIZONTAL;

        // Строка 1: Исходный каталог
        c.gridx = 0; c.gridy = 0; c.weightx = 0;
        top.add(new JLabel("Исходный каталог:"), c);
        c.gridx = 1; c.weightx = 1;
        top.add(sourceField, c);
        c.gridx = 2; c.weightx = 0;
        JButton srcBtn = new JButton("...");
        srcBtn.addActionListener(e -> chooseFolder(sourceField));
        top.add(srcBtn, c);

        // Строка 2: Результирующий каталог
        c.gridx = 0; c.gridy = 1; c.weightx = 0;
        top.add(new JLabel("Результат:"), c);
        c.gridx = 1; c.weightx = 1;
        top.add(targetField, c);
        c.gridx = 2; c.weightx = 0;
        JButton tgtBtn = new JButton("...");
        tgtBtn.addActionListener(e -> chooseFolder(targetField));
        top.add(tgtBtn, c);

        // Строка 3: Кнопки управления
        c.gridx = 0; c.gridy = 2; c.gridwidth = 3; c.weightx = 1;
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
        buttonPanel.add(convertButton);
        buttonPanel.add(stopButton);
        top.add(buttonPanel, c);

        add(top, BorderLayout.NORTH);

        // ----- Лог (центр) -----
        logArea.setEditable(false);
        logArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        add(new JScrollPane(logArea), BorderLayout.CENTER);

        // Восстанавливаем последние настройки
        sourceField.setText(prefs.get(PREF_SOURCE, ""));
        targetField.setText(prefs.get(PREF_TARGET, ""));

        // Кнопка остановки изначально неактивна
        stopButton.setEnabled(false);

        // Слушатели кнопок
        convertButton.addActionListener(e -> runConversion());
        stopButton.addActionListener(e -> stopConversion());

        // Сохраняем настройки при закрытии
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                savePreferences();
            }
        });

        setSize(850, 600);
        setLocationRelativeTo(null);
    }

    private void savePreferences() {
        prefs.put(PREF_SOURCE, sourceField.getText().trim());
        prefs.put(PREF_TARGET, targetField.getText().trim());
    }

    private void chooseFolder(JTextField target) {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setAcceptAllFileFilterUsed(false);

        String currentText = target.getText().trim();
        if (!currentText.isEmpty()) {
            java.io.File currentDir = new java.io.File(currentText);
            if (currentDir.exists() && currentDir.isDirectory()) {
                chooser.setCurrentDirectory(currentDir);
            }
        }

        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            target.setText(chooser.getSelectedFile().getAbsolutePath());
        }
    }

    private void runConversion() {
        String src = sourceField.getText().trim();
        String tgt = targetField.getText().trim();
        if (src.isEmpty() || tgt.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Укажите оба каталога.", "Внимание", JOptionPane.WARNING_MESSAGE);
            return;
        }

        savePreferences();

        // Блокируем старт, разблокируем стоп
        convertButton.setEnabled(false);
        stopButton.setEnabled(true);
        logArea.setText("");

        final Path sourcePath = Path.of(src);
        final Path targetPath = Path.of(tgt);

        currentWorker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() {
                DirectoryConverter converter = new DirectoryConverter(new FisheyeConverter(), this::publish);
                try {
                    converter.convert(sourcePath, targetPath);
                } catch (Exception ex) {
                    publish("КРИТИЧЕСКАЯ ОШИБКА: " + ex.getMessage());
                    ex.printStackTrace();
                }
                return null;
            }

            @Override
            protected void process(List<String> chunks) {
                for (String s : chunks) {
                    logArea.append(s + System.lineSeparator());
                }
                logArea.setCaretPosition(logArea.getDocument().getLength());
            }

            @Override
            protected void done() {
                // Возвращаем кнопки в исходное состояние
                convertButton.setEnabled(true);
                stopButton.setEnabled(false);

                if (isCancelled()) {
                    logArea.append("=== Процесс остановлен ===" + System.lineSeparator());
                } else {
                    logArea.append("=== Обработка завершена ===" + System.lineSeparator());
                }
                currentWorker = null;
            }
        };

        currentWorker.execute();
    }

    private void stopConversion() {
        if (currentWorker != null && !currentWorker.isDone()) {
            // Вызов cancel(true) отправляет потоку сигнал прерывания (interrupt)
            currentWorker.cancel(true);
            logArea.append(">>> Отправлен сигнал остановки..." + System.lineSeparator());
            stopButton.setEnabled(false); // Блокируем кнопку, чтобы не спамили
        }
    }
}