package ru.miacomsoft;

import nu.pattern.OpenCV;
import org.opencv.core.*;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class FisheyeConverter implements FileConverter, BinaryConverter {

    static {
        OpenCV.loadLocally();
    }

    private static final double FOV_DEGREES = 195.0;

    // --- КЭШ КАРТ ИСКАЖЕНИЙ ---
    private int lastW = -1;
    private int lastH = -1;
    private Mat map_x_L, map_y_L, map_x_R, map_y_R;
    private Mat mask3, invMask3;

    // ==========================================
    // РЕАЛИЗАЦИЯ FILE CONVERTER (Работа с файлами)
    // ==========================================

    @Override
    public boolean isConvertible(Path source) throws IOException {
        Mat src = Imgcodecs.imread(source.toString());
        if (src.empty()) return false;
        boolean result = isConvertibleMat(src);
        src.release();
        return result;
    }

    @Override
    public void convert(Path source, Path target) throws IOException {
        Mat src = Imgcodecs.imread(source.toString());
        if (src.empty()) throw new IOException("Не удалось прочитать: " + source);

        Mat dst = processImage(src);

        // Убедимся, что папка существует
        if (target.getParent() != null) {
            Files.createDirectories(target.getParent());
        }

        String outPath = target.toString();
        if (!outPath.toLowerCase().endsWith(".jpg") && !outPath.toLowerCase().endsWith(".jpeg")) {
            int dotIndex = outPath.lastIndexOf('.');
            outPath = (dotIndex > 0 ? outPath.substring(0, dotIndex) : outPath) + ".jpg";
        }

        MatOfInt params = new MatOfInt(Imgcodecs.IMWRITE_JPEG_QUALITY, 95);
        Imgcodecs.imwrite(outPath, dst, params);

        src.release();
        dst.release();
    }

    // ==========================================
    // РЕАЛИЗАЦИЯ BINARY CONVERTER (Библиотека)
    // ==========================================

    @Override
    public boolean isConvertible(byte[] imageData) {
        MatOfByte mob = new MatOfByte(imageData);
        Mat src = Imgcodecs.imdecode(mob, Imgcodecs.IMREAD_COLOR);
        mob.release();
        if (src.empty()) return false;
        boolean result = isConvertibleMat(src);
        src.release();
        return result;
    }

    @Override
    public byte[] convert(byte[] imageData) throws IOException {
        MatOfByte mob = new MatOfByte(imageData);
        Mat src = Imgcodecs.imdecode(mob, Imgcodecs.IMREAD_COLOR);
        mob.release();

        if (src.empty()) return null;

        Mat dst = processImage(src);
        src.release();

        MatOfByte outMob = new MatOfByte();
        MatOfInt params = new MatOfInt(Imgcodecs.IMWRITE_JPEG_QUALITY, 95);
        Imgcodecs.imencode(".jpg", dst, outMob, params);
        dst.release();

        byte[] result = outMob.toArray();
        outMob.release();
        return result;
    }

    // ==========================================
    // ЯДРО ОБРАБОТКИ (Общее для файлов и потоков)
    // ==========================================

    private boolean isConvertibleMat(Mat src) {
        int W = src.cols();
        int H = src.rows();

        double ratio = (double) W / H;
        if (ratio < 1.8 || ratio > 2.2) return false;

        int checkSize = Math.min(W, H) / 10;
        if (checkSize < 10) checkSize = 10;

        double avgCornerBrightness = (
                getBrightness(src, 0, 0, checkSize) +
                        getBrightness(src, W - checkSize, 0, checkSize) +
                        getBrightness(src, 0, H - checkSize, checkSize) +
                        getBrightness(src, W - checkSize, H - checkSize, checkSize)
        ) / 4.0;

        return avgCornerBrightness < 20.0;
    }

    private double getBrightness(Mat img, int x, int y, int size) {
        if (x < 0) x = 0;
        if (y < 0) y = 0;
        if (x + size > img.cols()) size = img.cols() - x;
        if (y + size > img.rows()) size = img.rows() - y;
        Mat roi = img.submat(y, y + size, x, x + size);
        Scalar mean = Core.mean(roi);
        roi.release();
        return (mean.val[0] + mean.val[1] + mean.val[2]) / 3.0;
    }

    private synchronized void generateMaps(int W_out, int H_out) {
        if (W_out == lastW && H_out == lastH) return;

        if (map_x_L != null) {
            map_x_L.release(); map_y_L.release(); map_x_R.release(); map_y_R.release();
            mask3.release(); invMask3.release();
        }

        int halfW = W_out / 2;
        float R = Math.min(halfW, H_out) / 2.0f;
        float cx = halfW / 2.0f;
        float cy = H_out / 2.0f;
        double FOV_rad = Math.toRadians(FOV_DEGREES);

        map_x_L = new Mat(H_out, W_out, CvType.CV_32FC1);
        map_y_L = new Mat(H_out, W_out, CvType.CV_32FC1);
        map_x_R = new Mat(H_out, W_out, CvType.CV_32FC1);
        map_y_R = new Mat(H_out, W_out, CvType.CV_32FC1);
        Mat mask = new Mat(H_out, W_out, CvType.CV_32FC1);

        float[] arrXL = new float[W_out * H_out];
        float[] arrYL = new float[W_out * H_out];
        float[] arrXR = new float[W_out * H_out];
        float[] arrYR = new float[W_out * H_out];
        float[] arrMask = new float[W_out * H_out];

        int idx = 0;
        for (int y = 0; y < H_out; y++) {
            for (int x = 0; x < W_out; x++) {
                double lon = (x / (double) W_out) * 2 * Math.PI - Math.PI;
                double lat = (y / (double) H_out) * Math.PI - Math.PI / 2.0;

                double vx = Math.cos(lat) * Math.sin(lon);
                double vy = Math.sin(lat);
                double vz = Math.cos(lat) * Math.cos(lon);

                double theta_L = Math.acos(Math.max(-1.0, Math.min(1.0, vz)));
                double phi_L = Math.atan2(vx, vy);
                double r_L = (R * theta_L) / (FOV_rad / 2.0);
                arrXL[idx] = (float) (cx + r_L * Math.sin(phi_L));
                arrYL[idx] = (float) (cy + r_L * Math.cos(phi_L));

                double theta_R = Math.acos(Math.max(-1.0, Math.min(1.0, -vz)));
                double phi_R = Math.atan2(-vx, vy);
                double r_R = (R * theta_R) / (FOV_rad / 2.0);
                arrXR[idx] = (float) (cx + r_R * Math.sin(phi_R));
                arrYR[idx] = (float) (cy + r_R * Math.cos(phi_R));

                double m = 0.5 + (vz / 0.2);
                arrMask[idx] = (float) Math.max(0.0, Math.min(1.0, m));
                idx++;
            }
        }

        map_x_L.put(0, 0, arrXL); map_y_L.put(0, 0, arrYL);
        map_x_R.put(0, 0, arrXR); map_y_R.put(0, 0, arrYR);
        mask.put(0, 0, arrMask);

        mask3 = new Mat();
        Imgproc.cvtColor(mask, mask3, Imgproc.COLOR_GRAY2BGR);
        invMask3 = new Mat();
        Core.absdiff(mask3, new Scalar(1, 1, 1), invMask3);
        mask.release();

        lastW = W_out;
        lastH = H_out;
    }

    private Mat processImage(Mat src) {
        int W = src.cols();
        int H = src.rows();
        int halfW = W / 2;
        int W_out = W;
        int H_out = W / 2;

        generateMaps(W_out, H_out);

        Mat leftImg = new Mat(src, new Rect(0, 0, halfW, H));
        Mat rightImg = new Mat(src, new Rect(halfW, 0, halfW, H));

        Mat dst_L = new Mat();
        Mat dst_R = new Mat();

        Imgproc.remap(leftImg, dst_L, map_x_L, map_y_L, Imgproc.INTER_LINEAR, Core.BORDER_CONSTANT, new Scalar(0,0,0));
        Imgproc.remap(rightImg, dst_R, map_x_R, map_y_R, Imgproc.INTER_LINEAR, Core.BORDER_CONSTANT, new Scalar(0,0,0));

        Mat blendL = new Mat();
        Mat blendR = new Mat();

        Core.multiply(dst_L, mask3, blendL, 1.0, CvType.CV_8UC3);
        Core.multiply(dst_R, invMask3, blendR, 1.0, CvType.CV_8UC3);

        Mat dst = new Mat();
        Core.add(blendL, blendR, dst);

        leftImg.release(); rightImg.release();
        dst_L.release(); dst_R.release();
        blendL.release(); blendR.release();

        return dst;
    }
}