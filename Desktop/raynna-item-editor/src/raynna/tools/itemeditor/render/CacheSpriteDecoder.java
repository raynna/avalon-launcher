package raynna.tools.itemeditor.render;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

final class CacheSpriteDecoder {
    private CacheSpriteDecoder() {
    }

    static List<BufferedImage> decode(byte[] data) {
        if (data == null || data.length < 2) {
            return List.of();
        }
        SpriteBuffer buffer = new SpriteBuffer(data);
        buffer.position = data.length - 2;
        int frames = buffer.readUnsignedShort();
        int[] xOffsets = new int[frames];
        int[] yOffsets = new int[frames];
        int[] innerWidths = new int[frames];
        int[] innerHeights = new int[frames];
        byte[][] pixels = new byte[frames][];
        byte[][] alphas = new byte[frames][];
        boolean[] hasAlpha = new boolean[frames];

        buffer.position = data.length - frames * 8 - 7;
        buffer.readUnsignedShort();
        buffer.readUnsignedShort();
        int paletteSize = buffer.readUnsignedByte() + 1;
        for (int i = 0; i < frames; i++) {
            xOffsets[i] = buffer.readUnsignedShort();
        }
        for (int i = 0; i < frames; i++) {
            yOffsets[i] = buffer.readUnsignedShort();
        }
        for (int i = 0; i < frames; i++) {
            innerWidths[i] = buffer.readUnsignedShort();
        }
        for (int i = 0; i < frames; i++) {
            innerHeights[i] = buffer.readUnsignedShort();
        }

        buffer.position = data.length - frames * 8 - (paletteSize - 1) * 3 - 7;
        int[] palette = new int[paletteSize];
        for (int i = 1; i < paletteSize; i++) {
            palette[i] = buffer.read24BitInt();
            if (palette[i] == 0) {
                palette[i] = 1;
            }
        }

        buffer.position = 0;
        for (int frame = 0; frame < frames; frame++) {
            int width = innerWidths[frame];
            int height = innerHeights[frame];
            int pixelCount = width * height;
            byte[] framePixels = new byte[pixelCount];
            byte[] frameAlpha = new byte[pixelCount];
            pixels[frame] = framePixels;
            alphas[frame] = frameAlpha;
            boolean anyAlpha = false;
            int flags = buffer.readUnsignedByte();
            if ((flags & 0x1) == 0) {
                for (int i = 0; i < pixelCount; i++) {
                    framePixels[i] = buffer.readByte();
                }
                if ((flags & 0x2) != 0) {
                    for (int i = 0; i < pixelCount; i++) {
                        byte alpha = frameAlpha[i] = buffer.readByte();
                        if (alpha != -1) {
                            anyAlpha = true;
                        }
                    }
                }
            } else {
                for (int x = 0; x < width; x++) {
                    for (int y = 0; y < height; y++) {
                        framePixels[x + y * width] = buffer.readByte();
                    }
                }
                if ((flags & 0x2) != 0) {
                    for (int x = 0; x < width; x++) {
                        for (int y = 0; y < height; y++) {
                            byte alpha = frameAlpha[x + y * width] = buffer.readByte();
                            if (alpha != -1) {
                                anyAlpha = true;
                            }
                        }
                    }
                }
            }
            hasAlpha[frame] = anyAlpha;
        }

        List<BufferedImage> images = new ArrayList<>(frames);
        for (int frame = 0; frame < frames; frame++) {
            int width = innerWidths[frame];
            int height = innerHeights[frame];
            if (width <= 0 || height <= 0) {
                continue;
            }
            int[] argb = new int[width * height];
            for (int i = 0; i < argb.length; i++) {
                int color = palette[pixels[frame][i] & 0xFF];
                int alpha = hasAlpha[frame] ? (alphas[frame][i] & 0xFF) : 0xFF;
                argb[i] = (alpha << 24) | color;
            }
            BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            image.setRGB(0, 0, width, height, argb, 0, width);
            images.add(image);
        }
        return images;
    }

    private static final class SpriteBuffer {
        private final byte[] data;
        private int position;

        private SpriteBuffer(byte[] data) {
            this.data = data;
        }

        private byte readByte() {
            return data[position++];
        }

        private int readUnsignedByte() {
            return data[position++] & 0xFF;
        }

        private int readUnsignedShort() {
            int value = ((data[position] & 0xFF) << 8) | (data[position + 1] & 0xFF);
            position += 2;
            return value;
        }

        private int read24BitInt() {
            int value = ((data[position] & 0xFF) << 16) | ((data[position + 1] & 0xFF) << 8) | (data[position + 2] & 0xFF);
            position += 3;
            return value;
        }
    }
}
