package eu.arrowhead.kalix.dto.json;

import eu.arrowhead.kalix.dto.WriteException;
import eu.arrowhead.kalix.dto.Format;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public class JSONWriter {
    private JSONWriter() {}

    private static byte[] HEX = new byte[]{
        '0', '1', '2', '3', '4', '5', '6', '7',
        '8', '9', 'A', 'B', 'C', 'D', 'E', 'F'};

    public static void writeTo(double number, final ByteBuffer target) throws WriteException {
        if (!Double.isFinite(number)) {
            throw new WriteException(Format.JSON, "NaN, +Infinify and " +
                "-Infinity cannot be represented in JSON");
        }
        target.put(Double.toString(number)
            .getBytes(StandardCharsets.ISO_8859_1));
    }

    public static void writeTo(final String string, final ByteBuffer target) {
        for (var b : string.getBytes(StandardCharsets.UTF_8)) {
            if (b < ' ') {
                target.put((byte) '\\');
                switch (b) {
                case '\b': b = 'b'; break;
                case '\f': b = 'f'; break;
                case '\n': b = 'n'; break;
                case '\r': b = 'r'; break;
                default:
                    target.put((byte) 'u');
                    target.put((byte) '0');
                    target.put((byte) '0');
                    target.put(HEX[(b & 0xF0) >>> 4]);
                    target.put(HEX[(b & 0x0F)]);
                    continue;
                }
            }
            target.put(b);
        }
    }

}