package com.jagex;

import raynna.tools.itemeditor.ItemDefinitionsService;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Arrays;
import java.util.List;
import java.util.function.IntFunction;
import java.util.zip.CRC32;

public final class CodexTextureProviderFactory {
    private static final int TEXTURE_METADATA_INDEX = 26;

    private CodexTextureProviderFactory() {
    }

    public static Interface_ma create(ItemDefinitionsService service) {
        Class243 metadata = new Class243(
                new SingleFileArchiveBackend(new int[]{0}, archiveId -> archiveId == 0
                        ? service.loadConfigBytes(TEXTURE_METADATA_INDEX, 0, 0)
                        : null),
                false,
                0
        );
        Class243 textures = new Class243(
                new SingleFileArchiveBackend(toIntArray(service.listTextureIds()), service::loadTextureBytes),
                false,
                0
        );
        Class243 sprites = new Class243(
                new SingleFileArchiveBackend(toIntArray(service.listSpriteIds()), service::loadSpriteBytes),
                false,
                0
        );
        return new Class249(metadata, textures, sprites);
    }

    private static int[] toIntArray(List<Integer> values) {
        return values.stream().mapToInt(Integer::intValue).sorted().toArray();
    }

    private static final class SingleFileArchiveBackend extends Class242 {
        private final int[] archiveIds;
        private final IntFunction<byte[]> loader;
        private final Class226 referenceTable;

        private SingleFileArchiveBackend(int[] archiveIds, IntFunction<byte[]> loader) {
            this.archiveIds = archiveIds == null ? new int[0] : Arrays.stream(archiveIds).distinct().sorted().toArray();
            this.loader = loader;
            this.referenceTable = buildReferenceTable(this.archiveIds);
        }

        @Override
        void method2249(int i) {
        }

        @Override
        Class226 method2250(int i) {
            return referenceTable;
        }

        @Override
        byte[] method2251(int archiveId, byte i) {
            return loadArchiveContainer(archiveId);
        }

        @Override
        byte[] method2252(int archiveId) {
            return loadArchiveContainer(archiveId);
        }

        @Override
        int method2253(int archiveId, int i) {
            return exists(archiveId) ? 100 : 0;
        }

        @Override
        Class226 method2254() {
            return referenceTable;
        }

        @Override
        Class226 method2255() {
            return referenceTable;
        }

        @Override
        void method2256(int i, short i2) {
        }

        @Override
        int method2257(int archiveId) {
            return method2253(archiveId, 0);
        }

        @Override
        byte[] method2258(int archiveId) {
            return loadArchiveContainer(archiveId);
        }

        @Override
        byte[] method2259(int archiveId) {
            return loadArchiveContainer(archiveId);
        }

        @Override
        void method2260(int i) {
        }

        @Override
        int method2261(int archiveId) {
            return method2253(archiveId, 0);
        }

        @Override
        byte[] method2262(int archiveId) {
            return loadArchiveContainer(archiveId);
        }

        @Override
        void method2263(int i) {
        }

        @Override
        Class226 method2264() {
            return referenceTable;
        }

        @Override
        void method2265(int i) {
        }

        private boolean exists(int archiveId) {
            return Arrays.binarySearch(archiveIds, archiveId) >= 0;
        }

        private byte[] loadArchiveContainer(int archiveId) {
            if (!exists(archiveId)) {
                return null;
            }
            byte[] payload = loader.apply(archiveId);
            if (payload == null) {
                return null;
            }
            return wrapUncompressed(payload);
        }

        private static Class226 buildReferenceTable(int[] archiveIds) {
            try {
                byte[] payload = buildReferencePayload(archiveIds);
                byte[] container = wrapUncompressed(payload);
                CRC32 crc32 = new CRC32();
                crc32.update(container);
                return new Class226(container, (int) crc32.getValue(), null);
            } catch (IOException ex) {
                throw new UncheckedIOException(ex);
            }
        }

        private static byte[] buildReferencePayload(int[] archiveIds) throws IOException {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            out.writeByte(7);
            out.writeInt(0);
            out.writeByte(0);
            writeBigSmart(out, archiveIds.length);

            int previousArchiveId = 0;
            for (int archiveId : archiveIds) {
                writeBigSmart(out, archiveId - previousArchiveId);
                previousArchiveId = archiveId;
            }
            for (int ignored : archiveIds) {
                out.writeInt(0);
            }
            for (int ignored : archiveIds) {
                out.writeInt(0);
            }
            for (int ignored : archiveIds) {
                writeBigSmart(out, 1);
            }
            for (int ignored : archiveIds) {
                writeBigSmart(out, 0);
            }
            out.flush();
            return bytes.toByteArray();
        }

        private static void writeBigSmart(DataOutputStream out, int value) throws IOException {
            if (value < 0) {
                throw new IllegalArgumentException("Negative big smart value: " + value);
            }
            if (value < 32768) {
                out.writeShort(value);
                return;
            }
            out.writeInt(value | 0x80000000);
        }

        private static byte[] wrapUncompressed(byte[] payload) {
            try {
                ByteArrayOutputStream bytes = new ByteArrayOutputStream(payload.length + 5);
                DataOutputStream out = new DataOutputStream(bytes);
                out.writeByte(0);
                out.writeInt(payload.length);
                out.write(payload);
                out.flush();
                return bytes.toByteArray();
            } catch (IOException ex) {
                throw new UncheckedIOException(ex);
            }
        }
    }
}
