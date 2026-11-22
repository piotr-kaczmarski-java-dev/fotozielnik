package pl.fotozielnik;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Builds JPEGs with real EXIF (a TIFF structure in an APP1 segment) for tests, so EXIF reading is tested
 * against bytes a camera would write - without binary fixture files.
 *
 * <pre>byte[] jpeg = ExifJpeg.of(TestImages.jpeg(1)).make("Canon").dateTimeOriginal("2024:05:12 10:31:07").build();</pre>
 */
public final class ExifJpeg {

    @SuppressWarnings("ArrayRecordComponent") // private value holder, never compared
    private record Entry(int tag, int type, int count, byte[] data) {}

    private static final int ASCII = 2;
    private static final int SHORT = 3;
    private static final int LONG = 4;
    private static final int RATIONAL = 5;

    private final byte[] jpeg;
    private final List<Entry> ifd0 = new ArrayList<>();
    private final List<Entry> exif = new ArrayList<>();
    private final List<Entry> gps = new ArrayList<>();

    private ExifJpeg(byte[] jpeg) {
        this.jpeg = jpeg;
    }

    public static ExifJpeg of(byte[] plainJpeg) {
        return new ExifJpeg(plainJpeg);
    }

    public ExifJpeg make(String v) {
        ifd0.add(ascii(0x010F, v));
        return this;
    }

    public ExifJpeg model(String v) {
        ifd0.add(ascii(0x0110, v));
        return this;
    }

    public ExifJpeg orientation(int v) {
        ifd0.add(new Entry(
                0x0112, SHORT, 1, ByteBuffer.allocate(2).putShort((short) v).array()));
        return this;
    }

    public ExifJpeg dateTimeOriginal(String v) {
        exif.add(ascii(0x9003, v));
        return this;
    }

    public ExifJpeg offsetTimeOriginal(String v) {
        exif.add(ascii(0x9011, v));
        return this;
    }

    public ExifJpeg bodySerial(String v) {
        exif.add(ascii(0xA431, v));
        return this;
    }

    public ExifJpeg lensModel(String v) {
        exif.add(ascii(0xA434, v));
        return this;
    }

    /** Decimal degrees; negative = S / W. */
    public ExifJpeg gps(double lat, double lon) {
        gps.add(ascii(0x0001, lat >= 0 ? "N" : "S"));
        gps.add(rationals(0x0002, dms(Math.abs(lat))));
        gps.add(ascii(0x0003, lon >= 0 ? "E" : "W"));
        gps.add(rationals(0x0004, dms(Math.abs(lon))));
        return this;
    }

    /** UTC date "yyyy:MM:dd" and time h, m, s. */
    public ExifJpeg gpsTime(String date, int h, int m, int s) {
        gps.add(rationals(0x0007, new long[] {h, 1, m, 1, s, 1}));
        gps.add(ascii(0x001D, date));
        return this;
    }

    public byte[] build() {
        byte[] tiff = tiff();
        var out = new ByteArrayOutputStream();
        out.write(jpeg, 0, 2); // SOI
        out.write(0xFF);
        out.write(0xE1); // APP1
        int length = 2 + 6 + tiff.length;
        out.write(length >> 8);
        out.write(length & 0xFF);
        out.writeBytes("Exif\0\0".getBytes(StandardCharsets.US_ASCII));
        out.writeBytes(tiff);
        out.write(jpeg, 2, jpeg.length - 2);
        return out.toByteArray();
    }

    private byte[] tiff() {
        List<Entry> root = new ArrayList<>(ifd0);
        // pointer entries have a fixed size, so offsets can be computed before their values are known
        if (!exif.isEmpty()) {
            root.add(new Entry(0x8769, LONG, 1, new byte[4]));
        }
        if (!gps.isEmpty()) {
            root.add(new Entry(0x8825, LONG, 1, new byte[4]));
        }
        root.sort(Comparator.comparingInt(Entry::tag));
        int rootOffset = 8;
        int exifOffset = rootOffset + size(root);
        int gpsOffset = exifOffset + size(sorted(exif));
        List<Entry> resolved = new ArrayList<>();
        for (Entry e : root) {
            if (e.tag() == 0x8769) {
                resolved.add(new Entry(e.tag(), LONG, 1, int32(exifOffset)));
            } else if (e.tag() == 0x8825) {
                resolved.add(new Entry(e.tag(), LONG, 1, int32(gpsOffset)));
            } else {
                resolved.add(e);
            }
        }
        var out = new ByteArrayOutputStream();
        out.writeBytes(new byte[] {'M', 'M', 0, 42, 0, 0, 0, 8});
        writeIfd(out, resolved, rootOffset);
        if (!exif.isEmpty()) {
            writeIfd(out, sorted(exif), exifOffset);
        }
        if (!gps.isEmpty()) {
            writeIfd(out, sorted(gps), gpsOffset);
        }
        return out.toByteArray();
    }

    private static List<Entry> sorted(List<Entry> entries) {
        return entries.stream().sorted(Comparator.comparingInt(Entry::tag)).toList();
    }

    private static int size(List<Entry> entries) {
        if (entries.isEmpty()) {
            return 0;
        }
        int size = 2 + 12 * entries.size() + 4;
        for (Entry e : entries) {
            if (e.data().length > 4) {
                size += pad(e.data().length);
            }
        }
        return size;
    }

    private static void writeIfd(ByteArrayOutputStream out, List<Entry> entries, int start) {
        var overflow = new ByteArrayOutputStream();
        int dataOffset = start + 2 + 12 * entries.size() + 4;
        out.write(entries.size() >> 8);
        out.write(entries.size() & 0xFF);
        for (Entry e : entries) {
            ByteBuffer b = ByteBuffer.allocate(12);
            b.putShort((short) e.tag()).putShort((short) e.type()).putInt(e.count());
            if (e.data().length <= 4) {
                b.put(e.data());
            } else {
                b.putInt(dataOffset);
                overflow.writeBytes(e.data());
                if (e.data().length % 2 == 1) {
                    overflow.write(0);
                }
                dataOffset += pad(e.data().length);
            }
            out.writeBytes(b.array());
        }
        out.writeBytes(new byte[4]); // no next IFD
        out.writeBytes(overflow.toByteArray());
    }

    private static int pad(int n) {
        return n + (n % 2);
    }

    private static Entry ascii(int tag, String value) {
        byte[] s = (value + "\0").getBytes(StandardCharsets.US_ASCII);
        return new Entry(tag, ASCII, s.length, s);
    }

    private static Entry rationals(int tag, long[] numDen) {
        ByteBuffer b = ByteBuffer.allocate(numDen.length * 4);
        for (long v : numDen) {
            b.putInt((int) v);
        }
        return new Entry(tag, RATIONAL, numDen.length / 2, b.array());
    }

    private static long[] dms(double degrees) {
        long d = (long) degrees;
        double minutesFull = (degrees - d) * 60;
        long m = (long) minutesFull;
        long sTimes1000 = Math.round((minutesFull - m) * 60 * 1000);
        return new long[] {d, 1, m, 1, sTimes1000, 1000};
    }

    private static byte[] int32(int v) {
        return ByteBuffer.allocate(4).putInt(v).array();
    }
}
