import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.DataOutputStream;
import java.io.FileOutputStream;
import java.nio.FloatBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;

/**
 * ML spike (ADR-0010): the BioCLIP image encoder through ONNX Runtime in plain Java, preprocessing in Java.
 * Writes the embeddings (float32 little-endian, n x dim) for compare_java.py and prints CPU time per image.
 *
 * java -cp models/onnxruntime-1.31.1.jar java/OnnxSpike.java models/bioclip-image.onnx \
 *      models/bioclip-preprocess.json data/bioclip-files.json data data/bioclip-java-emb.bin [threads]
 */
public class OnnxSpike {

    public static void main(String[] args) throws Exception {
        Path model = Path.of(args[0]);
        String pre = Files.readString(Path.of(args[1]));
        List<String> files = strings(Files.readString(Path.of(args[2])));
        Path dataDir = Path.of(args[3]);
        Path out = Path.of(args[4]);
        int threads = args.length > 5 ? Integer.parseInt(args[5]) : 1;

        int resize = (int) numbers(field(pre, "resize"))[0];
        double[] crop = numbers(field(pre, "crop"));
        double[] mean = numbers(field(pre, "mean"));
        double[] std = numbers(field(pre, "std"));
        int size = (int) crop[0];

        var env = OrtEnvironment.getEnvironment();
        var opts = new OrtSession.SessionOptions();
        opts.setIntraOpNumThreads(threads);
        opts.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT);
        long tLoad = System.nanoTime();
        try (OrtSession session = env.createSession(model.toString(), opts);
                var os = new DataOutputStream(new FileOutputStream(out.toFile()))) {
            System.out.printf("model %s loaded in %d ms, %d intra-op threads%n", model.getFileName(), (System.nanoTime() - tLoad) / 1_000_000, threads);
            long prepNs = 0, inferNs = 0;
            int dim = 0;
            for (int i = 0; i < files.size(); i++) {
                long t0 = System.nanoTime();
                float[] pixels = preprocess(ImageIO.read(dataDir.resolve(files.get(i)).toFile()), resize, size, mean, std);
                long t1 = System.nanoTime();
                try (var input = OnnxTensor.createTensor(env, FloatBuffer.wrap(pixels), new long[] {1, 3, size, size});
                        var result = session.run(Map.of("pixels", input))) {
                    float[] emb = ((float[][]) result.get(0).getValue())[0];
                    dim = emb.length;
                    for (float v : emb) {
                        os.writeInt(Integer.reverseBytes(Float.floatToIntBits(v)));
                    }
                }
                long t2 = System.nanoTime();
                if (i >= 3) { // skip warm-up
                    prepNs += t1 - t0;
                    inferNs += t2 - t1;
                }
            }
            int n = files.size() - 3;
            System.out.printf("%d images, dim %d: preprocess %.0f ms + inference %.0f ms per image%n",
                    files.size(), dim, prepNs / 1e6 / n, inferNs / 1e6 / n);
        }
    }

    /** Resize shortest side to `resize` (bicubic, halving first for quality), center crop, CHW, normalise. */
    static float[] preprocess(BufferedImage src, int resize, int size, double[] mean, double[] std) {
        BufferedImage img = src;
        double scale = (double) resize / Math.min(img.getWidth(), img.getHeight());
        int tw = (int) Math.round(img.getWidth() * scale), th = (int) Math.round(img.getHeight() * scale);
        while (img.getWidth() / 2 >= tw && img.getHeight() / 2 >= th) {
            img = draw(img, img.getWidth() / 2, img.getHeight() / 2);
        }
        img = draw(img, tw, th);
        int x0 = (tw - size) / 2, y0 = (th - size) / 2;
        float[] out = new float[3 * size * size];
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                int rgb = img.getRGB(x0 + x, y0 + y);
                int p = y * size + x;
                out[p] = (float) ((((rgb >> 16) & 0xFF) / 255.0 - mean[0]) / std[0]);
                out[size * size + p] = (float) ((((rgb >> 8) & 0xFF) / 255.0 - mean[1]) / std[1]);
                out[2 * size * size + p] = (float) (((rgb & 0xFF) / 255.0 - mean[2]) / std[2]);
            }
        }
        return out;
    }

    static BufferedImage draw(BufferedImage img, int w, int h) {
        BufferedImage o = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = o.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(img, 0, 0, w, h, null);
        g.dispose();
        return o;
    }

    static String field(String json, String name) {
        Matcher m = Pattern.compile("\"" + name + "\"\\s*:\\s*(\\[[^\\]]*\\]|[-0-9.eE]+)").matcher(json);
        if (!m.find()) throw new IllegalArgumentException("missing " + name);
        return m.group(1);
    }

    static double[] numbers(String s) {
        return Pattern.compile("[-0-9.eE]+").matcher(s).results().mapToDouble(r -> Double.parseDouble(r.group())).toArray();
    }

    static List<String> strings(String json) {
        List<String> out = new ArrayList<>();
        Matcher m = Pattern.compile("\"([^\"]*)\"").matcher(json);
        while (m.find()) out.add(m.group(1));
        return out;
    }
}
