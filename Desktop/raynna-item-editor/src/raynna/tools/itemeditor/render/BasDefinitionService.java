package raynna.tools.itemeditor.render;

import raynna.tools.itemeditor.ItemDefinitionsService;

import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.util.LinkedHashMap;
import java.util.Map;

final class BasDefinitionService {
    private static final int CONFIG_INDEX = 2;
    private static final int BAS_ARCHIVE = 32;

    private final ItemDefinitionsService itemService;
    private final Map<Integer, BasDefinition> cache = new LinkedHashMap<>(64, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Integer, BasDefinition> eldest) {
            return size() > 64;
        }
    };

    BasDefinitionService(ItemDefinitionsService itemService) {
        this.itemService = itemService;
    }

    BasDefinition get(int basId) {
        if (basId < 0) {
            return null;
        }
        synchronized (cache) {
            BasDefinition cached = cache.get(basId);
            if (cached != null) {
                return cached;
            }
        }
        byte[] data = itemService.loadConfigBytes(CONFIG_INDEX, BAS_ARCHIVE, basId);
        if (data == null || data.length == 0) {
            return null;
        }
        BasDefinition decoded;
        try {
            decoded = decode(data);
        } catch (RuntimeException ignored) {
            return null;
        }
        synchronized (cache) {
            cache.put(basId, decoded);
        }
        return decoded;
    }

    private static BasDefinition decode(byte[] data) {
        int[][] transforms = null;
        int liftOffset = 0;
        int zoomOffset = 0;
        ByteBuffer buffer = ByteBuffer.wrap(data);
        while (buffer.hasRemaining()) {
            int opcode = buffer.get() & 0xFF;
            if (opcode == 0) {
                break;
            }
            switch (opcode) {
                case 26 -> {
                    liftOffset -= (buffer.get() & 0xFF) * 2;
                    zoomOffset += (buffer.get() & 0xFF) * 6;
                }
                case 27 -> {
                    if (transforms == null) {
                        transforms = new int[16][];
                    }
                    int slot = buffer.get() & 0xFF;
                    int[] transform = new int[6];
                    for (int i = 0; i < 6; i++) {
                        transform[i] = buffer.getShort();
                    }
                    if (slot >= 0 && slot < transforms.length) {
                        transforms[slot] = transform;
                    }
                }
                case 54 -> {
                    zoomOffset += (buffer.get() & 0xFF) << 4;
                    liftOffset -= (buffer.get() & 0xFF) << 1;
                }
                case 1, 2, 3, 4, 5, 6, 7, 8, 9, 38, 39, 40, 41, 42, 43, 44, 46, 47, 48, 49, 50, 51 -> readBigSmart(buffer);
                case 28 -> buffer.position(buffer.position() + (buffer.get() & 0xFF));
                case 29, 31, 34, 37, 45, 53 -> buffer.get();
                case 30, 32, 35, 36, 52, 55, 56 -> skip(buffer, opcode);
                case 33 -> buffer.getShort();
                default -> throw new IllegalStateException("Unsupported BAS opcode " + opcode);
            }
        }
        return new BasDefinition(transforms, liftOffset, zoomOffset);
    }

    private static void skip(ByteBuffer buffer, int opcode) {
        switch (opcode) {
            case 30, 32, 35, 36 -> buffer.getShort();
            case 52 -> {
                int count = buffer.get() & 0xFF;
                for (int i = 0; i < count; i++) {
                    readBigSmart(buffer);
                    buffer.get();
                }
            }
            case 55 -> {
                buffer.get();
                buffer.getShort();
            }
            case 56 -> {
                buffer.get();
                buffer.getShort();
                buffer.getShort();
                buffer.getShort();
            }
            default -> throw new IllegalStateException("Unsupported BAS skip opcode " + opcode);
        }
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
