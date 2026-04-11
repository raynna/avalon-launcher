package raynna.tools.itemeditor;

import com.alex.io.OutputStream;
import com.alex.store.Store;
import com.alex.utils.Constants;

import java.nio.ByteBuffer;
import java.nio.BufferUnderflowException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class ItemDefinitionsService {
    private static final int TEXTURES_INDEX = 9;
    private static final int SPRITES_INDEX = 8;

    private final Path cachePath;
    private final Store store;
    private final com.alex.store.Index index;
    private final com.alex.store.Index modelIndex;
    private final Object storeLock = new Object();
    private final Map<Integer, ItemDefinitionRecord> rawRecordCache = new HashMap<>();
    private final Map<Integer, ItemDefinitionRecord> resolvedRecordCache = new HashMap<>();
    private final Map<Integer, byte[]> defaultsFileCache = new HashMap<>();

    public ItemDefinitionsService(Path cachePath) {
        this.cachePath = cachePath;
        try {
            this.store = new Store(normalizeStorePath(cachePath));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to open cache store at " + cachePath, e);
        }
        this.index = store.getIndexes()[Constants.ITEM_DEFINITIONS_INDEX];
        this.modelIndex = store.getIndexes()[Constants.MODELS_INDEX];
        if (index == null) {
            throw new IllegalStateException("Missing item definitions index " + Constants.ITEM_DEFINITIONS_INDEX + " in " + cachePath);
        }
        if (modelIndex == null) {
            throw new IllegalStateException("Missing model index " + Constants.MODELS_INDEX + " in " + cachePath);
        }
    }

    private static String normalizeStorePath(Path cachePath) {
        String path = cachePath.toAbsolutePath().normalize().toString();
        if (!path.endsWith("/") && !path.endsWith("\\")) {
            path += java.io.File.separator;
        }
        return path;
    }

    public Path getCachePath() {
        return cachePath;
    }

    public void save(ItemDefinitionRecord record) {
        if (record == null) {
            throw new IllegalArgumentException("record is null");
        }
        int archiveId = record.id() >>> 8;
        int fileId = record.id() & 0xFF;
        byte[] encoded = encode(record);
        synchronized (storeLock) {
            if (!index.putFile(archiveId, fileId, encoded)) {
                throw new IllegalStateException("Failed to write item " + record.id() + " to cache.");
            }
        }
        rawRecordCache.remove(record.id());
        resolvedRecordCache.remove(record.id());
    }

    public byte[] loadModelBytes(int modelId) {
        if (modelId < 0) {
            return null;
        }
        for (int attempt = 0; attempt < 4; attempt++) {
            synchronized (storeLock) {
                byte[] data = modelIndex.getFile(modelId);
                if (data == null && modelIndex.archiveExists(modelId) && modelIndex.getLastFileId(modelId) >= 0) {
                    for (int fileId = 0; fileId <= modelIndex.getLastFileId(modelId); fileId++) {
                        if (!modelIndex.fileExists(modelId, fileId)) {
                            continue;
                        }
                        data = modelIndex.getFile(modelId, fileId);
                        if (data != null) {
                            break;
                        }
                    }
                }
                if (data != null) {
                    return data;
                }
                modelIndex.resetCachedFiles();
            }
            if (attempt < 3) {
                try {
                    Thread.sleep(15L * (attempt + 1));
                } catch (InterruptedException interruptedException) {
                    Thread.currentThread().interrupt();
                    return null;
                }
            }
        }
        return null;
    }

    private byte[] loadItemBytes(int itemId) {
        if (itemId < 0) {
            return null;
        }
        int archiveId = itemId >>> 8;
        int fileId = itemId & 0xFF;
        for (int attempt = 0; attempt < 4; attempt++) {
            synchronized (storeLock) {
                if (!index.archiveExists(archiveId)) {
                    return null;
                }
                byte[] data = index.getFile(archiveId, fileId);
                if ((data == null || data.length == 0) && index.fileExists(archiveId, fileId)) {
                    data = index.getFile(archiveId, fileId);
                }
                if (data != null && data.length > 0) {
                    return data;
                }
                index.resetCachedFiles();
            }
            if (attempt < 3) {
                try {
                    Thread.sleep(15L * (attempt + 1));
                } catch (InterruptedException interruptedException) {
                    Thread.currentThread().interrupt();
                    return null;
                }
            }
        }
        return null;
    }

    public void saveModelBytes(int modelId, byte[] data) {
        if (modelId < 0 || data == null || data.length == 0) {
            throw new IllegalArgumentException("invalid model save");
        }
        synchronized (storeLock) {
            if (!modelIndex.putFile(modelId, 0, data)) {
                throw new IllegalStateException("Failed to write model " + modelId + " to cache.");
            }
            modelIndex.resetCachedFiles();
        }
    }

    public List<Integer> listTextureIds() {
        return listIndexArchiveIds(TEXTURES_INDEX);
    }

    public List<Integer> listSpriteIds() {
        return listIndexArchiveIds(SPRITES_INDEX);
    }

    public byte[] loadTextureBytes(int textureId) {
        return loadSingleFileIndexBytes(TEXTURES_INDEX, textureId);
    }

    public byte[] loadSpriteBytes(int spriteId) {
        return loadSingleFileIndexBytes(SPRITES_INDEX, spriteId);
    }

    public byte[] loadConfigBytes(int indexId, int archiveId, int fileId) {
        if (indexId < 0 || indexId >= store.getIndexes().length) {
            return null;
        }
        com.alex.store.Index configIndex = store.getIndexes()[indexId];
        if (configIndex == null || archiveId < 0 || fileId < 0) {
            return null;
        }
        synchronized (storeLock) {
            if (!configIndex.archiveExists(archiveId) || !configIndex.fileExists(archiveId, fileId)) {
                return null;
            }
            return configIndex.getFile(archiveId, fileId);
        }
    }

    private byte[] loadSingleFileIndexBytes(int indexId, int archiveId) {
        if (archiveId < 0 || indexId < 0 || indexId >= store.getIndexes().length) {
            return null;
        }
        com.alex.store.Index targetIndex = store.getIndexes()[indexId];
        if (targetIndex == null) {
            return null;
        }
        synchronized (storeLock) {
            byte[] data = targetIndex.getFile(archiveId);
            if (data == null && targetIndex.archiveExists(archiveId) && targetIndex.getLastFileId(archiveId) >= 0) {
                for (int fileId = 0; fileId <= targetIndex.getLastFileId(archiveId); fileId++) {
                    if (!targetIndex.fileExists(archiveId, fileId)) {
                        continue;
                    }
                    data = targetIndex.getFile(archiveId, fileId);
                    if (data != null) {
                        break;
                    }
                }
            }
            return data;
        }
    }

    private List<Integer> listIndexArchiveIds(int indexId) {
        if (indexId < 0 || indexId >= store.getIndexes().length) {
            return List.of();
        }
        com.alex.store.Index targetIndex = store.getIndexes()[indexId];
        if (targetIndex == null) {
            return List.of();
        }
        List<Integer> ids = new ArrayList<>();
        synchronized (storeLock) {
            for (int archiveId = 0; archiveId <= targetIndex.getLastArchiveId(); archiveId++) {
                if (targetIndex.archiveExists(archiveId)) {
                    ids.add(archiveId);
                }
            }
        }
        return ids;
    }

    public byte[] loadDefaultsBytes(int fileId) {
        if (fileId < 0) {
            return null;
        }
        byte[] cached = defaultsFileCache.get(fileId);
        if (cached != null) {
            return cached.length == 0 ? null : cached.clone();
        }
        byte[] resolved = null;
        for (com.alex.store.Index candidate : store.getIndexes()) {
            if (candidate == null) {
                continue;
            }
            synchronized (storeLock) {
                if (candidate.archiveExists(fileId)) {
                    resolved = candidate.getFile(fileId);
                    if (resolved != null && resolved.length > 0) {
                        break;
                    }
                }
                if (candidate.archiveExists(0) && candidate.fileExists(0, fileId)) {
                    resolved = candidate.getFile(0, fileId);
                    if (resolved != null && resolved.length > 0) {
                        break;
                    }
                }
            }
        }
        defaultsFileCache.put(fileId, resolved == null ? new byte[0] : resolved.clone());
        return resolved == null ? null : resolved.clone();
    }

    public List<Integer> listItemIds() {
        List<Integer> ids = new ArrayList<>();
        synchronized (storeLock) {
            for (int archiveId = 0; archiveId <= index.getLastArchiveId(); archiveId++) {
                if (!index.archiveExists(archiveId)) {
                    continue;
                }
                int lastFileId = index.getLastFileId(archiveId);
                for (int fileId = 0; fileId <= lastFileId; fileId++) {
                    if (!index.fileExists(archiveId, fileId)) {
                        continue;
                    }
                    ids.add((archiveId << 8) | fileId);
                }
            }
        }
        Collections.sort(ids);
        return ids;
    }

    public List<ItemListEntry> listItems() {
        List<Integer> ids = listItemIds();
        Map<Integer, ItemDefinitionRecord> records = new LinkedHashMap<>(ids.size());
        Map<Integer, List<Integer>> notedByBase = new HashMap<>();

        for (Integer id : ids) {
            ItemDefinitionRecord record;
            try {
                record = load(id, false);
            } catch (RuntimeException exception) {
                record = new ItemDefinitionRecord(
                        id,
                        "decode_failed",
                        0,
                        2000,
                        0,
                        0,
                        0,
                        0,
                        0,
                        128,
                        128,
                        128,
                        false,
                        1,
                        false,
                        -1,
                        -1,
                        -1,
                        -1,
                        -1,
                        -1,
                        -1,
                        -1,
                        0,
                        0,
                        0,
                        0,
                        0,
                        0,
                        -1,
                        -1,
                        -1,
                        -1,
                        -1,
                        new String[]{null, null, "take", null, null},
                        new String[]{null, null, null, null, "drop"},
                        null,
                        null,
                        null,
                        null,
                        Map.<Integer, Object>of(-1, exception.getClass().getSimpleName() + (exception.getMessage() == null ? "" : ": " + exception.getMessage()))
                );
            }
            records.put(id, record);
            if (record.certTemplateId() != -1 && record.certId() != -1) {
                notedByBase.computeIfAbsent(record.certId(), ignored -> new ArrayList<>()).add(record.id());
            }
        }

        List<ItemListEntry> entries = new ArrayList<>(ids.size());
        for (Integer id : ids) {
            ItemDefinitionRecord record = records.get(id);
            List<Integer> notedIds = notedByBase.getOrDefault(id, List.of());
            entries.add(new ItemListEntry(
                    id,
                    record.name(),
                    record.certTemplateId() != -1 && record.certId() != -1,
                    record.certId(),
                    notedIds,
                    buildDisplayName(record, records.get(record.certId()), notedIds)
            ));
        }
        return entries;
    }

    public ItemDefinitionRecord load(int itemId) {
        return load(itemId, true, new HashSet<>());
    }

    public String debugDescribeItemLoad(int itemId) {
        StringBuilder builder = new StringBuilder(512);
        long start = System.nanoTime();
        builder.append("[item ").append(itemId).append("] debug start").append('\n');
        builder.append("cache raw=").append(rawRecordCache.containsKey(itemId))
                .append(" resolved=").append(resolvedRecordCache.containsKey(itemId)).append('\n');
        int archiveId = itemId >>> 8;
        int fileId = itemId & 0xFF;
        boolean archiveExists;
        boolean fileExists;
        synchronized (storeLock) {
            archiveExists = index.archiveExists(archiveId);
            fileExists = archiveExists && index.fileExists(archiveId, fileId);
        }
        builder.append("archive=").append(archiveId)
                .append(" file=").append(fileId)
                .append(" archiveExists=").append(archiveExists);
        if (archiveExists) {
            builder.append(" fileExists=").append(fileExists);
        }
        builder.append('\n');
        byte[] data = loadItemBytes(itemId);
        builder.append("definitionBytes=").append(data == null ? -1 : data.length).append('\n');
        try {
            ItemDefinitionRecord raw = load(itemId, false, new HashSet<>());
            appendRecordDebug(builder, "raw", raw);
        } catch (RuntimeException exception) {
            builder.append("rawLoadFail=").append(exception.getClass().getSimpleName());
            if (exception.getMessage() != null) {
                builder.append(": ").append(exception.getMessage());
            }
            builder.append('\n');
        }
        try {
            ItemDefinitionRecord resolved = load(itemId, true, new HashSet<>());
            appendRecordDebug(builder, "resolved", resolved);
        } catch (RuntimeException exception) {
            builder.append("resolvedLoadFail=").append(exception.getClass().getSimpleName());
            if (exception.getMessage() != null) {
                builder.append(": ").append(exception.getMessage());
            }
            builder.append('\n');
        }
        builder.append("elapsedMs=").append((System.nanoTime() - start) / 1_000_000.0).append('\n');
        return builder.toString();
    }

    private ItemDefinitionRecord load(int itemId, boolean resolveTemplates) {
        return load(itemId, resolveTemplates, new HashSet<>());
    }

    private ItemDefinitionRecord load(int itemId, boolean resolveTemplates, Set<Integer> visiting) {
        Map<Integer, ItemDefinitionRecord> cache = resolveTemplates ? resolvedRecordCache : rawRecordCache;
        ItemDefinitionRecord cached = cache.get(itemId);
        if (cached != null) {
            return cached;
        }
        if (resolveTemplates) {
            ItemDefinitionRecord rawCached = rawRecordCache.get(itemId);
            if (rawCached != null) {
                if (!visiting.add(itemId)) {
                    return rawCached;
                }
                ItemDefinitionRecord resolved = rawCached;
                try {
                    if (rawCached.certTemplateId() != -1 && rawCached.certId() != -1 && rawCached.certId() != itemId) {
                        resolved = applyNote(rawCached, load(rawCached.certId(), false, visiting));
                    } else if (rawCached.lendTemplateId() != -1 && rawCached.lendId() != -1 && rawCached.lendId() != itemId) {
                        resolved = applyLend(rawCached, load(rawCached.lendId(), false, visiting));
                    }
                } catch (RuntimeException ignored) {
                    resolved = rawCached;
                } finally {
                    visiting.remove(itemId);
                }
                resolvedRecordCache.put(itemId, resolved);
                return resolved;
            }
        }
        int archiveId = itemId >>> 8;
        int fileId = itemId & 0xFF;
        if (!index.archiveExists(archiveId)) {
            throw new IllegalArgumentException("Missing item archive " + archiveId + " for item " + itemId);
        }
        byte[] data = loadItemBytes(itemId);
        if (data == null || data.length == 0) {
            throw new IllegalArgumentException("Missing item file " + fileId + " in archive " + archiveId + " for item " + itemId);
        }
        ItemDefinitionRecord record = decode(itemId, data);
        rawRecordCache.put(itemId, record);
        if (!resolveTemplates) {
            return record;
        }
        if (!visiting.add(itemId)) {
            return record;
        }
        ItemDefinitionRecord resolved = record;
        try {
            if (record.certTemplateId() != -1 && record.certId() != -1 && record.certId() != itemId) {
                resolved = applyNote(record, load(record.certId(), false, visiting));
            } else if (record.lendTemplateId() != -1 && record.lendId() != -1 && record.lendId() != itemId) {
                resolved = applyLend(record, load(record.lendId(), false, visiting));
            }
        } catch (RuntimeException ignored) {
            resolved = record;
        } finally {
            visiting.remove(itemId);
        }
        resolvedRecordCache.put(itemId, resolved);
        return resolved;
    }

    private static void appendRecordDebug(StringBuilder builder, String label, ItemDefinitionRecord record) {
        builder.append(label)
                .append(" name=").append(record.name())
                .append(" model=").append(record.modelId())
                .append(" zoom=").append(record.modelZoom())
                .append(" rot=").append(record.modelRotation1()).append('/').append(record.modelRotation2()).append('/').append(record.modelRotation3())
                .append(" off=").append(record.modelOffset1()).append('/').append(record.modelOffset2())
                .append(" scale=").append(record.modelScaleX()).append('/').append(record.modelScaleY()).append('/').append(record.modelScaleZ())
                .append(" note=").append(record.certId()).append('/').append(record.certTemplateId())
                .append(" lend=").append(record.lendId()).append('/').append(record.lendTemplateId())
                .append(" male=").append(record.maleEquip1()).append('/').append(record.maleEquip2()).append('/').append(record.maleEquip3())
                .append(" female=").append(record.femaleEquip1()).append('/').append(record.femaleEquip2()).append('/').append(record.femaleEquip3());
        Object warning = record.clientScriptData() == null ? null : record.clientScriptData().get(-1);
        if (warning != null) {
            builder.append(" warn=").append(warning);
        }
        builder.append('\n');
    }

    private ItemDefinitionRecord applyNote(ItemDefinitionRecord note, ItemDefinitionRecord realItem) {
        return new ItemDefinitionRecord(
                note.id(),
                realItem.name(),
                note.modelId(),
                note.modelZoom(),
                note.modelRotation1(),
                note.modelRotation2(),
                note.modelRotation3(),
                note.modelOffset1(),
                note.modelOffset2(),
                note.modelScaleX(),
                note.modelScaleY(),
                note.modelScaleZ(),
                true,
                realItem.price(),
                realItem.membersOnly(),
                realItem.equipSlot(),
                realItem.equipType(),
                realItem.maleEquip1(),
                realItem.maleEquip2(),
                realItem.maleEquip3(),
                realItem.femaleEquip1(),
                realItem.femaleEquip2(),
                realItem.femaleEquip3(),
                realItem.maleWearOffsetX(),
                realItem.maleWearOffsetY(),
                realItem.maleWearOffsetZ(),
                realItem.femaleWearOffsetX(),
                realItem.femaleWearOffsetY(),
                realItem.femaleWearOffsetZ(),
                note.certId(),
                note.certTemplateId(),
                note.lendId(),
                note.lendTemplateId(),
                realItem.teamId(),
                note.groundOptions(),
                note.inventoryOptions(),
                realItem.originalModelColors(),
                realItem.modifiedModelColors(),
                realItem.originalTextureColors(),
                realItem.modifiedTextureColors(),
                realItem.clientScriptData()
        );
    }

    private ItemDefinitionRecord applyLend(ItemDefinitionRecord lent, ItemDefinitionRecord realItem) {
        String[] inventoryOptions = new String[]{null, null, null, null, "Discard"};
        if (realItem.inventoryOptions() != null) {
            for (int i = 0; i < Math.min(4, realItem.inventoryOptions().length); i++) {
                inventoryOptions[i] = realItem.inventoryOptions()[i];
            }
        }
        return new ItemDefinitionRecord(
                lent.id(),
                realItem.name(),
                lent.modelId(),
                lent.modelZoom(),
                lent.modelRotation1(),
                lent.modelRotation2(),
                lent.modelRotation3(),
                lent.modelOffset1(),
                lent.modelOffset2(),
                lent.modelScaleX(),
                lent.modelScaleY(),
                lent.modelScaleZ(),
                lent.stackable(),
                0,
                realItem.membersOnly(),
                realItem.equipSlot(),
                realItem.equipType(),
                realItem.maleEquip1(),
                realItem.maleEquip2(),
                realItem.maleEquip3(),
                realItem.femaleEquip1(),
                realItem.femaleEquip2(),
                realItem.femaleEquip3(),
                realItem.maleWearOffsetX(),
                realItem.maleWearOffsetY(),
                realItem.maleWearOffsetZ(),
                realItem.femaleWearOffsetX(),
                realItem.femaleWearOffsetY(),
                realItem.femaleWearOffsetZ(),
                lent.certId(),
                lent.certTemplateId(),
                lent.lendId(),
                lent.lendTemplateId(),
                realItem.teamId(),
                realItem.groundOptions() == null ? new String[]{null, null, "take", null, null} : realItem.groundOptions(),
                inventoryOptions,
                realItem.originalModelColors(),
                realItem.modifiedModelColors(),
                realItem.originalTextureColors(),
                realItem.modifiedTextureColors(),
                realItem.clientScriptData()
        );
    }

    private String buildDisplayName(ItemDefinitionRecord record, ItemDefinitionRecord certRecord, List<Integer> notedIds) {
        StringBuilder builder = new StringBuilder();
        boolean noted = record.certTemplateId() != -1 && record.certId() != -1;
        String name = record.name();
        if (noted && (name == null || name.isBlank() || "null".equalsIgnoreCase(name)) && certRecord != null) {
            name = certRecord.name();
        }
        if (name == null || name.isBlank()) {
            name = "null";
        }
        builder.append(name);
        if (noted) {
            builder.append(" (Noted)");
        }
        builder.append(" (").append(record.id()).append(")");
        return builder.toString();
    }

    private ItemDefinitionRecord decode(int itemId, byte[] data) {
        DecoderState state = new DecoderState(itemId);
        ByteBuffer buffer = ByteBuffer.wrap(data);
        while (buffer.hasRemaining()) {
            int opcode = buffer.get() & 0xFF;
            if (opcode == 0) {
                break;
            }
            try {
                state.readOpcode(buffer, opcode);
            } catch (BufferUnderflowException exception) {
                state.markDecodeWarning("BufferUnderflowException while reading opcode " + opcode + " for item " + itemId);
                break;
            } catch (IllegalStateException exception) {
                if (exception.getMessage() != null && exception.getMessage().startsWith("Unsupported item opcode ")) {
                    state.markDecodeWarning(exception.getMessage());
                    break;
                }
                throw exception;
            }
        }
        return state.toRecord();
    }

    private static final class DecoderState {
        private final int id;
        private String name = "null";
        private int modelId;
        private int modelZoom = 2000;
        private int modelRotation1;
        private int modelRotation2;
        private int modelRotation3;
        private int modelOffset1;
        private int modelOffset2;
        private int modelScaleX = 128;
        private int modelScaleY = 128;
        private int modelScaleZ = 128;
        private int stackable;
        private int price = 1;
        private boolean membersOnly;
        private int maleEquip1 = -1;
        private int femaleEquip1 = -1;
        private int maleEquip2 = -1;
        private int femaleEquip2 = -1;
        private String[] groundOptions = new String[]{null, null, "take", null, null};
        private String[] inventoryOptions = new String[]{null, null, null, null, "drop"};
        private int[] originalModelColors;
        private int[] modifiedModelColors;
        private short[] originalTextureColors;
        private short[] modifiedTextureColors;
        private int maleEquipModelId3 = -1;
        private int femaleEquipModelId3 = -1;
        private int maleWearOffsetX;
        private int maleWearOffsetY;
        private int maleWearOffsetZ;
        private int femaleWearOffsetX;
        private int femaleWearOffsetY;
        private int femaleWearOffsetZ;
        private int certId = -1;
        private int certTemplateId = -1;
        private int lendId = -1;
        private int lendTemplateId = -1;
        private int teamId = -1;
        private int equipSlot = -1;
        private int equipType = -1;
        private int[] stackIds;
        private int[] stackAmounts;
        private Map<Integer, Object> clientScriptData;
        private String decodeWarning;

        private DecoderState(int id) {
            this.id = id;
        }

        private void readOpcode(ByteBuffer buffer, int opcode) {
            switch (opcode) {
                case 1 -> modelId = readBigSmart(buffer);
                case 2 -> name = readString(buffer);
                case 4 -> modelZoom = readUnsignedShort(buffer);
                case 5 -> modelRotation1 = readUnsignedShort(buffer);
                case 6 -> modelRotation2 = readUnsignedShort(buffer);
                case 7 -> modelOffset1 = signedShort(readUnsignedShort(buffer));
                case 8 -> modelOffset2 = signedShort(readUnsignedShort(buffer));
                case 11 -> stackable = 1;
                case 12 -> price = buffer.getInt();
                case 13 -> equipSlot = buffer.get() & 0xFF;
                case 14 -> equipType = buffer.get() & 0xFF;
                case 16 -> membersOnly = true;
                case 18 -> readUnsignedShort(buffer);
                case 23 -> maleEquip1 = readBigSmart(buffer);
                case 24 -> maleEquip2 = readBigSmart(buffer);
                case 25 -> femaleEquip1 = readBigSmart(buffer);
                case 26 -> femaleEquip2 = readBigSmart(buffer);
                case 27 -> buffer.get();
                case 30, 31, 32, 33, 34 -> groundOptions[opcode - 30] = readString(buffer);
                case 35, 36, 37, 38, 39 -> inventoryOptions[opcode - 35] = readString(buffer);
                case 40 -> {
                    int length = buffer.get() & 0xFF;
                    originalModelColors = new int[length];
                    modifiedModelColors = new int[length];
                    for (int i = 0; i < length; i++) {
                        originalModelColors[i] = readUnsignedShort(buffer);
                        modifiedModelColors[i] = readUnsignedShort(buffer);
                    }
                }
                case 41 -> {
                    int length = buffer.get() & 0xFF;
                    originalTextureColors = new short[length];
                    modifiedTextureColors = new short[length];
                    for (int i = 0; i < length; i++) {
                        originalTextureColors[i] = (short) readUnsignedShort(buffer);
                        modifiedTextureColors[i] = (short) readUnsignedShort(buffer);
                    }
                }
                case 42 -> {
                    int length = buffer.get() & 0xFF;
                    for (int i = 0; i < length; i++) {
                        buffer.get();
                    }
                }
                case 43 -> buffer.getInt();
                case 44, 45 -> readUnsignedShort(buffer);
                case 65 -> {
                }
                case 78 -> maleEquipModelId3 = readBigSmart(buffer);
                case 79 -> femaleEquipModelId3 = readBigSmart(buffer);
                case 90, 91, 92, 93 -> readBigSmart(buffer);
                case 95 -> modelRotation3 = readUnsignedShort(buffer);
                case 96 -> buffer.get();
                case 97 -> certId = readUnsignedShort(buffer);
                case 98 -> certTemplateId = readUnsignedShort(buffer);
                case 100, 101, 102, 103, 104, 105, 106, 107, 108, 109 -> {
                    if (stackIds == null) {
                        stackIds = new int[10];
                        stackAmounts = new int[10];
                        Arrays.fill(stackIds, -1);
                    }
                    stackIds[opcode - 100] = readUnsignedShort(buffer);
                    stackAmounts[opcode - 100] = readUnsignedShort(buffer);
                }
                case 110 -> modelScaleX = readUnsignedShort(buffer);
                case 111 -> modelScaleY = readUnsignedShort(buffer);
                case 112 -> modelScaleZ = readUnsignedShort(buffer);
                case 113, 114 -> buffer.get();
                case 115 -> teamId = buffer.get() & 0xFF;
                case 121 -> lendId = readUnsignedShort(buffer);
                case 122 -> lendTemplateId = readUnsignedShort(buffer);
                case 125 -> {
                    maleWearOffsetX = buffer.get();
                    maleWearOffsetY = buffer.get();
                    maleWearOffsetZ = buffer.get();
                }
                case 126 -> {
                    femaleWearOffsetX = buffer.get();
                    femaleWearOffsetY = buffer.get();
                    femaleWearOffsetZ = buffer.get();
                }
                case 127, 128, 129, 130 -> {
                    buffer.get();
                    readUnsignedShort(buffer);
                }
                case 132 -> {
                    int length = buffer.get() & 0xFF;
                    for (int i = 0; i < length; i++) {
                        readUnsignedShort(buffer);
                    }
                }
                case 134 -> buffer.get();
                case 139 -> readUnsignedShort(buffer);
                case 140 -> readUnsignedShort(buffer);
                case 142, 143, 144, 145, 146 -> readUnsignedShort(buffer);
                case 150, 151, 152, 153, 154 -> readUnsignedShort(buffer);
                case 249 -> {
                    int length = buffer.get() & 0xFF;
                    if (clientScriptData == null) {
                        clientScriptData = new LinkedHashMap<>(length);
                    }
                    for (int i = 0; i < length; i++) {
                        boolean stringValue = (buffer.get() & 0xFF) == 1;
                        int key = read24BitInt(buffer);
                        Object value = stringValue ? readString(buffer) : buffer.getInt();
                        clientScriptData.put(key, value);
                    }
                }
                default -> throw new IllegalStateException("Unsupported item opcode " + opcode + " for item " + id);
            }
        }

        private void markDecodeWarning(String warning) {
            this.decodeWarning = warning;
        }

        private ItemDefinitionRecord toRecord() {
            Map<Integer, Object> params = clientScriptData == null ? new LinkedHashMap<>() : new LinkedHashMap<>(clientScriptData);
            if (decodeWarning != null) {
                params.put(-1, decodeWarning);
            }
            return new ItemDefinitionRecord(
                    id,
                    name,
                    modelId,
                    modelZoom,
                    modelRotation1,
                    modelRotation2,
                    modelRotation3,
                    modelOffset1,
                    modelOffset2,
                    modelScaleX,
                    modelScaleY,
                    modelScaleZ,
                    stackable == 1,
                    price,
                    membersOnly,
                    equipSlot,
                    equipType,
                    maleEquip1,
                    maleEquip2,
                    maleEquipModelId3,
                    femaleEquip1,
                    femaleEquip2,
                    femaleEquipModelId3,
                    maleWearOffsetX,
                    maleWearOffsetY,
                    maleWearOffsetZ,
                    femaleWearOffsetX,
                    femaleWearOffsetY,
                    femaleWearOffsetZ,
                    certId,
                    certTemplateId,
                    lendId,
                    lendTemplateId,
                    teamId,
                    groundOptions,
                    inventoryOptions,
                    originalModelColors,
                    modifiedModelColors,
                    originalTextureColors,
                    modifiedTextureColors,
                    params
            );
        }

        private static int readBigSmart(ByteBuffer buffer) {
            int peek = buffer.get(buffer.position()) & 0xFF;
            if (peek < 128) {
                int value = readUnsignedShort(buffer);
                return value == 32767 ? -1 : value;
            }
            return buffer.getInt() & 0x7FFFFFFF;
        }

        private static int readUnsignedShort(ByteBuffer buffer) {
            return buffer.getShort() & 0xFFFF;
        }

        private static int read24BitInt(ByteBuffer buffer) {
            return ((buffer.get() & 0xFF) << 16) | ((buffer.get() & 0xFF) << 8) | (buffer.get() & 0xFF);
        }

        private static int signedShort(int value) {
            return value > 32767 ? value - 65536 : value;
        }

        private static String readString(ByteBuffer buffer) {
            int start = buffer.position();
            while (buffer.hasRemaining() && buffer.get() != 0) {
                // scan
            }
            int end = buffer.position() - 1;
            return new String(buffer.array(), start, Math.max(0, end - start), StandardCharsets.ISO_8859_1);
        }
    }

    public record ItemListEntry(
            int id,
            String name,
            boolean noted,
            int baseItemId,
            List<Integer> notedVariantIds,
            String displayName
    ) {
        public boolean matches(String filter) {
            if (filter == null || filter.isBlank()) {
                return true;
            }
            String normalized = filter.toLowerCase();
            if (displayName.toLowerCase().contains(normalized) || name.toLowerCase().contains(normalized)) {
                return true;
            }
            if (String.valueOf(id).contains(normalized)) {
                return true;
            }
            if (baseItemId != -1 && String.valueOf(baseItemId).contains(normalized)) {
                return true;
            }
            for (Integer notedId : notedVariantIds) {
                if (String.valueOf(notedId).contains(normalized)) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public String toString() {
            return displayName;
        }
    }

    private byte[] encode(ItemDefinitionRecord record) {
        OutputStream out = new OutputStream(512);

        if (record.modelId() >= 0) {
            out.writeByte(1);
            out.writeBigSmart(record.modelId());
        }
        out.writeByte(2);
        out.writeString(record.name() == null ? "null" : record.name());
        out.writeByte(4);
        out.writeShort(record.modelZoom());
        out.writeByte(5);
        out.writeShort(record.modelRotation1());
        out.writeByte(6);
        out.writeShort(record.modelRotation2());
        writeSignedShortOpcode(out, 7, record.modelOffset1());
        writeSignedShortOpcode(out, 8, record.modelOffset2());
        if (record.stackable()) {
            out.writeByte(11);
        }
        out.writeByte(12);
        out.writeInt(record.price());
        if (record.equipSlot() >= 0) {
            out.writeByte(13);
            out.writeByte(record.equipSlot());
        }
        if (record.equipType() >= 0) {
            out.writeByte(14);
            out.writeByte(record.equipType());
        }
        if (record.membersOnly()) {
            out.writeByte(16);
        }
        writeBigSmartOpcode(out, 23, record.maleEquip1());
        writeBigSmartOpcode(out, 24, record.maleEquip2());
        writeBigSmartOpcode(out, 25, record.femaleEquip1());
        writeBigSmartOpcode(out, 26, record.femaleEquip2());
        writeOptionOpcodes(out, 30, record.groundOptions());
        writeOptionOpcodes(out, 35, record.inventoryOptions());
        writeColorPairs(out, record.originalModelColors(), record.modifiedModelColors());
        writeTexturePairs(out, record.originalTextureColors(), record.modifiedTextureColors());
        writeBigSmartOpcode(out, 78, record.maleEquip3());
        writeBigSmartOpcode(out, 79, record.femaleEquip3());
        if (record.modelRotation3() != 0) {
            out.writeByte(95);
            out.writeShort(record.modelRotation3());
        }
        writeUnsignedShortOpcode(out, 97, record.certId());
        writeUnsignedShortOpcode(out, 98, record.certTemplateId());
        if (record.modelScaleX() != 128) {
            out.writeByte(110);
            out.writeShort(record.modelScaleX());
        }
        if (record.modelScaleY() != 128) {
            out.writeByte(111);
            out.writeShort(record.modelScaleY());
        }
        if (record.modelScaleZ() != 128) {
            out.writeByte(112);
            out.writeShort(record.modelScaleZ());
        }
        if (record.teamId() >= 0) {
            out.writeByte(115);
            out.writeByte(record.teamId());
        }
        writeUnsignedShortOpcode(out, 121, record.lendId());
        writeUnsignedShortOpcode(out, 122, record.lendTemplateId());
        writeSignedByteTriple(out, 125, record.maleWearOffsetX(), record.maleWearOffsetY(), record.maleWearOffsetZ());
        writeSignedByteTriple(out, 126, record.femaleWearOffsetX(), record.femaleWearOffsetY(), record.femaleWearOffsetZ());
        writeParams(out, record.clientScriptData());
        out.writeByte(0);

        return Arrays.copyOf(out.getBuffer(), out.getOffset());
    }

    private static void writeBigSmartOpcode(OutputStream out, int opcode, int value) {
        if (value < 0) {
            return;
        }
        out.writeByte(opcode);
        out.writeBigSmart(value);
    }

    private static void writeUnsignedShortOpcode(OutputStream out, int opcode, int value) {
        if (value < 0) {
            return;
        }
        out.writeByte(opcode);
        out.writeShort(value);
    }

    private static void writeSignedShortOpcode(OutputStream out, int opcode, int value) {
        if (value == 0) {
            return;
        }
        out.writeByte(opcode);
        out.writeShort(value < 0 ? value + 65536 : value);
    }

    private static void writeSignedByteTriple(OutputStream out, int opcode, int x, int y, int z) {
        if (x == 0 && y == 0 && z == 0) {
            return;
        }
        out.writeByte(opcode);
        out.writeByte((byte) x);
        out.writeByte((byte) y);
        out.writeByte((byte) z);
    }

    private static void writeOptionOpcodes(OutputStream out, int baseOpcode, String[] values) {
        if (values == null) {
            return;
        }
        for (int i = 0; i < Math.min(5, values.length); i++) {
            String value = values[i];
            if (value == null || value.isBlank()) {
                continue;
            }
            out.writeByte(baseOpcode + i);
            out.writeString(value);
        }
    }

    private static void writeColorPairs(OutputStream out, int[] original, int[] modified) {
        if (original == null || modified == null || original.length == 0 || modified.length == 0) {
            return;
        }
        int count = Math.min(original.length, modified.length);
        out.writeByte(40);
        out.writeByte(count);
        for (int i = 0; i < count; i++) {
            out.writeShort(original[i]);
            out.writeShort(modified[i]);
        }
    }

    private static void writeTexturePairs(OutputStream out, short[] original, short[] modified) {
        if (original == null || modified == null || original.length == 0 || modified.length == 0) {
            return;
        }
        int count = Math.min(original.length, modified.length);
        out.writeByte(41);
        out.writeByte(count);
        for (int i = 0; i < count; i++) {
            out.writeShort(original[i] & 0xFFFF);
            out.writeShort(modified[i] & 0xFFFF);
        }
    }

    private static void writeParams(OutputStream out, Map<Integer, Object> params) {
        if (params == null || params.isEmpty()) {
            return;
        }
        List<Map.Entry<Integer, Object>> entries = params.entrySet().stream()
                .filter(entry -> entry.getKey() >= 0 && entry.getValue() != null)
                .toList();
        if (entries.isEmpty()) {
            return;
        }
        out.writeByte(249);
        out.writeByte(entries.size());
        for (Map.Entry<Integer, Object> entry : entries) {
            Object value = entry.getValue();
            boolean stringValue = value instanceof String;
            out.writeByte(stringValue ? 1 : 0);
            out.write24BitInt(entry.getKey());
            if (stringValue) {
                out.writeString((String) value);
            } else if (value instanceof Number number) {
                out.writeInt(number.intValue());
            } else {
                out.writeString(String.valueOf(value));
            }
        }
    }
}
