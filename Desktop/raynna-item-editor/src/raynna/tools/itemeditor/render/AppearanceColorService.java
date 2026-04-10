package raynna.tools.itemeditor.render;

import raynna.tools.itemeditor.ItemDefinitionsService;

import java.nio.ByteBuffer;
import java.util.LinkedHashMap;
import java.util.Map;

final class AppearanceColorService {
    private static final int APPEARANCE_DEFAULTS_FILE = 3;
    private static final int[] MALE_COLOR_INDICES = {17, 95, 32, 0, 0, 0, 0, 0, 0, 0};
    private static final int[] FEMALE_COLOR_INDICES = {78, 95, 95, 0, 0, 0, 0, 0, 0, 0};

    private final ItemDefinitionsService itemService;
    private final Map<Integer, AppearanceColors> cache = new LinkedHashMap<>(1, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Integer, AppearanceColors> eldest) {
            return size() > 1;
        }
    };

    AppearanceColorService(ItemDefinitionsService itemService) {
        this.itemService = itemService;
    }

    AppearanceColors get() {
        synchronized (cache) {
            AppearanceColors cached = cache.get(APPEARANCE_DEFAULTS_FILE);
            if (cached != null) {
                return cached;
            }
        }
        byte[] data = itemService.loadDefaultsBytes(APPEARANCE_DEFAULTS_FILE);
        if (data == null || data.length == 0) {
            return null;
        }
        AppearanceColors decoded;
        try {
            decoded = decode(data);
        } catch (RuntimeException ignored) {
            return null;
        }
        synchronized (cache) {
            cache.put(APPEARANCE_DEFAULTS_FILE, decoded);
        }
        return decoded;
    }

    int[] buildOriginalColorMap() {
        AppearanceColors colors = get();
        if (colors == null || colors.originalColors() == null) {
            return new int[0];
        }
        int total = 0;
        for (short[] entries : colors.originalColors()) {
            if (entries != null) {
                total += entries.length;
            }
        }
        int[] mapped = new int[total];
        int offset = 0;
        for (short[] entries : colors.originalColors()) {
            if (entries == null) {
                continue;
            }
            for (short entry : entries) {
                mapped[offset++] = entry & 0xFFFF;
            }
        }
        return mapped;
    }

    int[] buildModifiedColorMap(boolean female) {
        AppearanceColors colors = get();
        if (colors == null || colors.originalColors() == null || colors.paletteColors() == null) {
            return new int[0];
        }
        int[] indices = female ? FEMALE_COLOR_INDICES : MALE_COLOR_INDICES;
        int total = 0;
        for (short[] entries : colors.originalColors()) {
            if (entries != null) {
                total += entries.length;
            }
        }
        int[] mapped = new int[total];
        int offset = 0;
        for (int group = 0; group < colors.originalColors().length; group++) {
            short[] originals = colors.originalColors()[group];
            if (originals == null) {
                continue;
            }
            short[][] palettes = group < colors.paletteColors().length ? colors.paletteColors()[group] : null;
            int index = group < indices.length ? indices[group] : 0;
            for (int variant = 0; variant < originals.length; variant++) {
                int replacement = originals[variant] & 0xFFFF;
                if (palettes != null && variant < palettes.length) {
                    short[] options = palettes[variant];
                    if (options != null && index >= 0 && index < options.length) {
                        replacement = options[index] & 0xFFFF;
                    }
                }
                mapped[offset++] = replacement;
            }
        }
        return mapped;
    }

    private static AppearanceColors decode(byte[] data) {
        short[][] originalColors = null;
        short[][][] paletteColors = null;
        ByteBuffer buffer = ByteBuffer.wrap(data);
        while (buffer.hasRemaining()) {
            int opcode = buffer.get() & 0xFF;
            if (opcode == 0) {
                break;
            }
            switch (opcode) {
                case 1 -> {
                    for (int i = 0; i < 4; i++) {
                        buffer.getShort();
                        buffer.getShort();
                    }
                }
                case 2 -> readBigSmart(buffer);
                case 3 -> {
                }
                case 4, 8, 10 -> {
                }
                case 5, 6 -> read24BitInt(buffer);
                case 7 -> {
                    originalColors = new short[10][4];
                    paletteColors = new short[10][4][];
                    for (int group = 0; group < 10; group++) {
                        for (int slot = 0; slot < 4; slot++) {
                            int original = buffer.getShort() & 0xFFFF;
                            if (original == 0xFFFF) {
                                original = -1;
                            }
                            originalColors[group][slot] = (short) original;
                            int count = buffer.getShort() & 0xFFFF;
                            short[] palette = new short[count];
                            for (int i = 0; i < count; i++) {
                                int value = buffer.getShort() & 0xFFFF;
                                if (value == 0xFFFF) {
                                    value = -1;
                                }
                                palette[i] = (short) value;
                            }
                            paletteColors[group][slot] = palette;
                        }
                    }
                }
                case 9, 11 -> buffer.get();
                case 12 -> {
                    buffer.getShort();
                    buffer.getShort();
                }
                case 13 -> {
                    for (int i = 0; i < 5; i++) {
                        buffer.get();
                    }
                }
                default -> throw new IllegalStateException("Unsupported appearance defaults opcode " + opcode);
            }
        }
        return new AppearanceColors(originalColors, paletteColors);
    }

    private static int readBigSmart(ByteBuffer buffer) {
        int peek = buffer.get(buffer.position()) & 0xFF;
        if (peek >= 0x80) {
            return buffer.getInt() & Integer.MAX_VALUE;
        }
        int value = buffer.getShort() & 0xFFFF;
        return value == 32767 ? -1 : value;
    }

    private static int read24BitInt(ByteBuffer buffer) {
        return ((buffer.get() & 0xFF) << 16) | ((buffer.get() & 0xFF) << 8) | (buffer.get() & 0xFF);
    }
}
