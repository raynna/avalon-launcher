package raynna.tools.itemeditor.render;

import raynna.tools.itemeditor.ItemDefinitionRecord;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntFunction;

final class PlayerAppearanceBuilder {
    private static final int[] MALE_LOOKS = {7, 14, 18, 26, 34, 38, 42};
    private static final int[] FEMALE_LOOKS = {276, 57, 57, 65, 68, 77, 80};
    private static final int SLOT_HEAD = 0;
    private static final int SLOT_CAPE = 1;
    private static final int SLOT_AMULET = 2;
    private static final int SLOT_WEAPON = 3;
    private static final int SLOT_CHEST = 4;
    private static final int SLOT_SHIELD = 5;
    private static final int SLOT_HANDS = 9;
    private static final int SLOT_LEGS = 7;
    private static final int SLOT_FEET = 10;
    private static final int SLOT_AURA = 14;

    private final BodyKitService bodyKitService;
    private final AppearanceColorService appearanceColorService;
    private final IntFunction<RsModelData> modelLoader;

    PlayerAppearanceBuilder(BodyKitService bodyKitService, AppearanceColorService appearanceColorService, IntFunction<RsModelData> modelLoader) {
        this.bodyKitService = bodyKitService;
        this.appearanceColorService = appearanceColorService;
        this.modelLoader = modelLoader;
    }

    BuiltPlayerAppearance build(ItemDefinitionRecord item, boolean female, int[] originalColors, int[] modifiedColors) {
        return build(item, female, originalColors, modifiedColors,
                female ? item.femaleWearOffsetX() : item.maleWearOffsetX(),
                female ? item.femaleWearOffsetY() : item.maleWearOffsetY(),
                female ? item.femaleWearOffsetZ() : item.maleWearOffsetZ());
    }

    BuiltPlayerAppearance build(ItemDefinitionRecord item, boolean female, int[] originalColors, int[] modifiedColors,
                                int wearOffsetX, int wearOffsetY, int wearOffsetZ) {
        RsModelData[] parts = new RsModelData[15];
        int[] looks = female ? FEMALE_LOOKS : MALE_LOOKS;
        int equipSlot = item.equipSlot();

        if (equipSlot != SLOT_CHEST) {
            parts[4] = buildBodyPart(looks[2], female);
        }
        if (equipSlot != SLOT_CHEST || !hideArms(item)) {
            parts[6] = buildBodyPart(looks[3], female);
        }
        if (equipSlot != SLOT_LEGS) {
            parts[7] = buildBodyPart(looks[5], female);
        }
        if (equipSlot != SLOT_HEAD || !hideHair(item)) {
            parts[8] = buildBodyPart(looks[0], female);
        }
        if (equipSlot != SLOT_HANDS) {
            parts[9] = buildBodyPart(looks[4], female);
        }
        if (equipSlot != SLOT_FEET) {
            parts[10] = buildBodyPart(looks[6], female);
        }
        if (!female && (equipSlot != SLOT_HEAD || showBeard(item))) {
            parts[11] = buildBodyPart(looks[1], false);
        }

        int primaryModelId = resolveEquipModelId(item, female, 1);
        int secondaryModelId = resolveEquipModelId(item, female, 2);
        int tertiaryModelId = resolveEquipModelId(item, female, 3);

        RsModelData equipped = RsModelData.combine(
                translateForWear(recolor(modelLoader.apply(primaryModelId), originalColors, modifiedColors), wearOffsetX, wearOffsetY, wearOffsetZ),
                translateForWear(recolor(modelLoader.apply(secondaryModelId), originalColors, modifiedColors), wearOffsetX, wearOffsetY, wearOffsetZ),
                translateForWear(recolor(modelLoader.apply(tertiaryModelId), originalColors, modifiedColors), wearOffsetX, wearOffsetY, wearOffsetZ)
        );
        int appearanceSlot = mapEquipSlotToAppearanceSlot(equipSlot);
        if (appearanceSlot >= 0 && equipped != null) {
            parts[appearanceSlot] = equipped;
        }
        return new BuiltPlayerAppearance(parts, primaryModelId, equipSlot);
    }

    private int resolveEquipModelId(ItemDefinitionRecord item, boolean female, int slot) {
        int preferred = switch (slot) {
            case 1 -> female ? item.femaleEquip1() : item.maleEquip1();
            case 2 -> female ? item.femaleEquip2() : item.maleEquip2();
            case 3 -> female ? item.femaleEquip3() : item.maleEquip3();
            default -> -1;
        };
        if (!female || preferred < 0 || modelLoader.apply(preferred) != null) {
            return preferred;
        }
        return switch (slot) {
            case 1 -> item.maleEquip1();
            case 2 -> item.maleEquip2();
            case 3 -> item.maleEquip3();
            default -> -1;
        };
    }

    private RsModelData buildBodyPart(int bodyKitId, boolean female) {
        BodyKitConfig kit = bodyKitService.get(bodyKitId);
        if (kit == null || kit.modelIds() == null || kit.modelIds().length == 0) {
            return null;
        }
        List<RsModelData> models = new ArrayList<>(kit.modelIds().length);
        for (int modelId : kit.modelIds()) {
            RsModelData model = modelLoader.apply(modelId);
            if (model != null) {
                models.add(model);
            }
        }
        if (models.isEmpty()) {
            return null;
        }
        RsModelData combined = RsModelData.combine(models.toArray(new RsModelData[0]));
        if (combined == null || kit.originalColors() == null || kit.modifiedColors() == null) {
            return applyAppearancePalette(combined, female);
        }
        int[] original = new int[kit.originalColors().length];
        int[] modified = new int[kit.modifiedColors().length];
        for (int i = 0; i < kit.originalColors().length; i++) {
            original[i] = kit.originalColors()[i] & 0xFFFF;
            modified[i] = kit.modifiedColors()[i] & 0xFFFF;
        }
        return applyAppearancePalette(combined.recolored(original, modified), female);
    }

    private static RsModelData recolor(RsModelData model, int[] originalColors, int[] modifiedColors) {
        if (model == null) {
            return null;
        }
        return model.recolored(originalColors, modifiedColors);
    }

    private static RsModelData translateForWear(RsModelData model, int x, int y, int z) {
        return model == null ? null : model.translated(x, y, z);
    }

    private RsModelData applyAppearancePalette(RsModelData model, boolean female) {
        if (model == null) {
            return null;
        }
        int[] original = appearanceColorService.buildOriginalColorMap();
        int[] modified = appearanceColorService.buildModifiedColorMap(female);
        if (original.length == 0 || modified.length == 0) {
            return model;
        }
        return model.recolored(original, modified);
    }

    private static int mapEquipSlotToAppearanceSlot(int equipSlot) {
        return switch (equipSlot) {
            case SLOT_HEAD -> 0;
            case SLOT_CAPE -> 1;
            case SLOT_AMULET -> 2;
            case SLOT_WEAPON -> 3;
            case SLOT_CHEST -> 4;
            case SLOT_SHIELD -> 5;
            case SLOT_LEGS -> 7;
            case SLOT_HANDS -> 9;
            case SLOT_FEET -> 10;
            case 12 -> 12;
            case SLOT_AURA -> 14;
            default -> -1;
        };
    }

    private static boolean hideArms(ItemDefinitionRecord item) {
        String name = safeLowerName(item);
        if (name.contains("d'hide body") || name.contains("dragonhide body") || name.equals("stripy pirate shirt")
                || (name.contains("chainbody") && (name.contains("iron") || name.contains("bronze")
                || name.contains("steel") || name.contains("black") || name.contains("mithril")
                || name.contains("adamant") || name.contains("rune") || name.contains("white")))
                || name.equals("leather body") || name.equals("hardleather body") || name.contains("studded body")) {
            return false;
        }
        return item.equipType() == 6;
    }

    private static boolean hideHair(ItemDefinitionRecord item) {
        String name = safeLowerName(item);
        if (name.contains("ancestral hat")) {
            return false;
        }
        if (name.contains("neitiznot faceguard") || name.contains("mime m")) {
            return true;
        }
        return item.equipType() == 8;
    }

    private static boolean showBeard(ItemDefinitionRecord item) {
        String name = safeLowerName(item);
        return !hideHair(item) || name.contains("horns") || name.contains("hat") || name.contains("coif")
                || name.contains("afro") || name.contains("cowl") || name.contains("mitre")
                || name.contains("bear mask") || name.contains("tattoo") || name.contains("antlers")
                || name.contains("chicken head") || name.contains("headdress") || name.contains("hood")
                || name.contains("bearhead")
                || (name.contains("mask") && !name.contains("h'ween") && !name.contains("mime m"))
                || (name.contains("helm") && !name.contains("full") && !name.contains("flaming"));
    }

    private static String safeLowerName(ItemDefinitionRecord item) {
        return item.name() == null ? "" : item.name().toLowerCase();
    }
}
