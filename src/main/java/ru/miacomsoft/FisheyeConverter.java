package ru.miacomsoft;

import nu.pattern.OpenCV;
import org.opencv.core.*;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

import java.io.IOException;
import java.nio.file.Path;

public class FisheyeConverter implements FileConverter {

    static {
        OpenCV.loadLocally();
    }

    // Угол обзора (FOV) объектива в градусах.
    // Для камер 360 обычно 190-200. Чем больше значение, тем шире зона склейки.
    private static final double FOV_DEGREES = 195.0;

    @Override
    public void convert(Path source, Path target) throws IOException {
        Mat src = Imgcodecs.imread(source.toString());
        if (src.empty()) {
            throw new IOException("Не удалось прочитать изображение: " + source);
        }

        int W = src.cols();
        int H = src.rows();
        int halfW = W / 2;

        // Вырезаем левый и правый объектив
        Mat leftImg = new Mat(src, new Rect(0, 0, halfW, H));
        Mat rightImg = new Mat(src, new Rect(halfW, 0, halfW, H));

        // Размер итоговой панорамы (2:1)
        int W_out = W;
        int H_out = W / 2;

        // Параметры линзы
        float R = Math.min(halfW, H) / 2.0f;
        float cx = halfW / 2.0f;
        float cy = H / 2.0f;
        double FOV_rad = Math.toRadians(FOV_DEGREES);

        // Карты для перепроецирования
        Mat map_x_L = new Mat(H_out, W_out, CvType.CV_32FC1);
        Mat map_y_L = new Mat(H_out, W_out, CvType.CV_32FC1);
        Mat map_x_R = new Mat(H_out, W_out, CvType.CV_32FC1);
        Mat map_y_R = new Mat(H_out, W_out, CvType.CV_32FC1);

        // Маска для плавного смешивания (1.0 - левый глаз, 0.0 - правый)
        Mat mask = new Mat(H_out, W_out, CvType.CV_32FC1);

        for (int y = 0; y < H_out; y++) {
            for (int x = 0; x < W_out; x++) {
                // Долгота и широта
                double lon = (x / (double) W_out) * 2 * Math.PI - Math.PI;
                double lat = (y / (double) H_out) * Math.PI - Math.PI / 2.0;

                // 3D-вектор направления
                double vx = Math.cos(lat) * Math.sin(lon);
                double vy = Math.sin(lat);
                double vz = Math.cos(lat) * Math.cos(lon);

                // --- ЛЕВЫЙ ГЛАЗ (перед) ---
                double theta_L = Math.acos(Math.max(-1.0, Math.min(1.0, vz)));
                double phi_L = Math.atan2(vx, vy);
                double r_L = (R * theta_L) / (FOV_rad / 2.0);
                map_x_L.put(y, x, cx + r_L * Math.sin(phi_L));
                map_y_L.put(y, x, cy + r_L * Math.cos(phi_L));

                // --- ПРАВЫЙ ГЛАЗ (зад) ---
                double theta_R = Math.acos(Math.max(-1.0, Math.min(1.0, -vz)));
                // Для правого глаза инвертируем X, чтобы правильно состыковать
                double phi_R = Math.atan2(-vx, vy);
                double r_R = (R * theta_R) / (FOV_rad / 2.0);
                map_x_R.put(y, x, cx + r_R * Math.sin(phi_R));
                map_y_R.put(y, x, cy + r_R * Math.cos(phi_R));

                // --- МАСКА СМЕШИВАНИЯ ---
                // В центральной зоне (вокруг vz = 0) делаем плавный переход
                // Диапазон перехода: от vz = -0.1 (правое фото) до vz = 0.1 (левое фото)
                double m = 0.5 + (vz / 0.2);
                m = Math.max(0.0, Math.min(1.0, m)); // Ограничиваем от 0.0 до 1.0
                mask.put(y, x, m);
            }
        }

        Mat dst_L = new Mat();
        Mat dst_R = new Mat();

        // Перепроецируем каждый глаз отдельно
        Imgproc.remap(leftImg, dst_L, map_x_L, map_y_L, Imgproc.INTER_LINEAR, Core.BORDER_CONSTANT, new Scalar(0,0,0));
        Imgproc.remap(rightImg, dst_R, map_x_R, map_y_R, Imgproc.INTER_LINEAR, Core.BORDER_CONSTANT, new Scalar(0,0,0));

        // --- СКЛЕЙКА (BLENDING) ---
        // Переводим одноканальную маску в трехканальную (для работы с цветом BGR)
        Mat mask3 = new Mat();
        Imgproc.cvtColor(mask, mask3, Imgproc.COLOR_GRAY2BGR);

        // Инвертируем маску (1.0 - mask) для правого глаза
        Mat invMask3 = new Mat();
        Core.absdiff(mask3, new Scalar(1, 1, 1), invMask3);

        Mat blendL = new Mat();
        Mat blendR = new Mat();

        // Умножаем левое изображение на маску, а правое на инвертированную маску
        Core.multiply(dst_L, mask3, blendL, 1.0, CvType.CV_8UC3);
        Core.multiply(dst_R, invMask3, blendR, 1.0, CvType.CV_8UC3);

        // Складываем результаты
        Mat dst = new Mat();
        Core.add(blendL, blendR, dst);

        // --- СОХРАНЕНИЕ В JPEG ---
        String outPath = target.toString();
        if (!outPath.toLowerCase().endsWith(".jpg") && !outPath.toLowerCase().endsWith(".jpeg")) {
            int dotIndex = outPath.lastIndexOf('.');
            outPath = (dotIndex > 0 ? outPath.substring(0, dotIndex) : outPath) + ".jpg";
        }

        MatOfInt params = new MatOfInt(Imgcodecs.IMWRITE_JPEG_QUALITY, 95);
        Imgcodecs.imwrite(outPath, dst, params);

        // Очистка памяти
        src.release(); leftImg.release(); rightImg.release();
        map_x_L.release(); map_y_L.release(); map_x_R.release(); map_y_R.release();
        mask.release(); mask3.release(); invMask3.release();
        dst_L.release(); dst_R.release(); blendL.release(); blendR.release(); dst.release();
    }
}