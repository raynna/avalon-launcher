package raynna.tools.itemeditor.render;

import raynna.tools.itemeditor.ItemDefinitionsService;

import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.util.LinkedHashMap;
import java.util.Map;

final class BodyKitService {
    private static final int CONFIG_INDEX = 2;
    private static final int BODY_KIT_ARCHIVE = 3;

    private final ItemDefinitionsService itemService;
    private final Map<Integer, BodyKitConfig> cache = new LinkedHashMap<>(128, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Integer, BodyKitConfig> eldest) {
            return size() > 128;
        }
    };

    BodyKitService(ItemDefinitionsService itemService) {
        this.itemService = itemService;
    }

    BodyKitConfig get(int bodyKitId) {
        if (bodyKitId < 0) {
            return null;
        }
        synchronized (cache) {
            BodyKitConfig cached = cache.get(bodyKitId);
            if (cached != null) {
                return cached;
            }
        }
        byte[] data = itemService.loadConfigBytes(CONFIG_INDEX, BODY_KIT_ARCHIVE, bodyKitId);
        if (data == null || data.length == 0) {
            return null;
        }
        BodyKitConfig decoded;
        try {
            decoded = decode(data);
        } catch (RuntimeException ignored) {
            return null;
        }
        synchronized (cache) {
            cache.put(bodyKitId, decoded);
        }
        return decoded;
    }

    private static BodyKitConfig decode(byte[] data) {
        int[] modelIds = null;
        short[] originalColors = null;
        short[] modifiedColors = null;
        ByteBuffer buffer = ByteBuffer.wrap(data);
        while (buffer.hasRemaining()) {
            int opcode = buffer.get() & 0xFF;
            if (opcode == 0) {
                break;
            }
            switch (opcode) {
                case 1 -> buffer.get();
                case 2 -> {
                    int count = buffer.get() & 0xFF;
                    modelIds = new int[count];
                    for (int i = 0; i < count; i++) {
                        modelIds[i] = readBigSmart(buffer);
                    }
                }
                case 3 -> {
                }
                case 40 -> {
                    int count = buffer.get() & 0xFF;
                    originalColors = new short[count];
                    modifiedColors = new short[count];
                    for (int i = 0; i < count; i++) {
                        originalColors[i] = (short) (buffer.getShort() & 0xFFFF);
                        modifiedColors[i] = (short) (buffer.getShort() & 0xFFFF);
                    }
                }
                case 41 -> {
                    int count = buffer.get() & 0xFF;
                    for (int i = 0; i < count; i++) {
                        buffer.getShort();
                        buffer.getShort();
                    }
                }
                default -> {
                    if (opcode >= 60 && opcode < 70) {
                        readBigSmart(buffer);
                    } else {
                        throw new IllegalStateException("Unsupported body kit opcode " + opcode);
                    }
                }
            }
        }
        return new BodyKitConfig(modelIds, originalColors, modifiedColors);
    }

    private static int readBigSmart(ByteBuffer buffer) {
        try {
            if (buffer.get(buffer.position()) < 0) {
                return buffer.getInt() & Integer.MAX_VALUE;
            }
            int value = buffer.getShort() & 0xFFFF;
            return value == 32767 ? -1 : value;
        } catch (IndexOutOfBoundsException | BufferUnderflowException exception) {
            throw new IllegalStateException("Invalid big smart", exception);
        }
    }
}
