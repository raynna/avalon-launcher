package raynna.tools.itemeditor.render;

import raynna.tools.itemeditor.ItemDefinitionRecord;

import java.awt.image.BufferedImage;

final class WornPreviewRenderer {
    @FunctionalInterface
    interface Rasterizer {
        BufferedImage render(ItemDefinitionRecord item, RsModelData model, int modelId, int width, int height, WidgetModelCamera camera,
                             int[] originalColors, int[] modifiedColors, int[] highlightedOriginalColors);
    }

    private final PlayerAppearanceBuilder appearanceBuilder;
    private final BasDefinitionService basDefinitions;
    private final Rasterizer rasterizer;

    WornPreviewRenderer(PlayerAppearanceBuilder appearanceBuilder, BasDefinitionService basDefinitions, Rasterizer rasterizer) {
        this.appearanceBuilder = appearanceBuilder;
        this.basDefinitions = basDefinitions;
        this.rasterizer = rasterizer;
    }

    BufferedImage render(ItemDefinitionRecord item, int[] originalColors, int[] modifiedColors, boolean female, int width, int height,
                         int zoom, int rotationX, int rotationY, int rotationZ, int cameraOffsetX, int cameraOffsetY,
                         int wearOffsetX, int wearOffsetY, int wearOffsetZ, int animationFrame, int[] highlightedOriginalColors) {
        BuiltPlayerAppearance built = appearanceBuilder.build(item, female, originalColors, modifiedColors, wearOffsetX, wearOffsetY, wearOffsetZ);
        int primaryModelId = built.primaryModelId();
        try {
            RsModelData[] parts = built.parts();
            int nonNullParts = countNonNullParts(parts);
            int renderAnimId = getRenderAnimId(item);
            BasDefinition bas = basDefinitions.get(renderAnimId);
            for (int i = 0; i < parts.length; i++) {
                parts[i] = applyBasTransform(parts[i], bas, i);
            }
            RsModelData assembled = RsModelData.combine(parts);
            if (assembled == null) {
                return null;
            }
            return rasterizer.render(item, assembled, primaryModelId, width, height,
                    buildCamera(item, bas, zoom, rotationX, rotationY, rotationZ, cameraOffsetX, cameraOffsetY, built.equipSlot(), animationFrame),
                    originalColors, modifiedColors, highlightedOriginalColors);
        } catch (RuntimeException exception) {
            if ("preview render cancelled".equals(exception.getMessage())) {
                throw exception;
            }
            System.out.println("[worn item " + item.id() + "] fallback female=" + female + " primary=" + primaryModelId
                    + " equipSlot=" + built.equipSlot() + " reason=" + exception.getClass().getSimpleName()
                    + (exception.getMessage() == null ? "" : ": " + exception.getMessage()));
        }
        RsModelData primary = appearanceBuilder.build(item, female, originalColors, modifiedColors, wearOffsetX, wearOffsetY, wearOffsetZ).parts()[mapPrimaryFallbackSlot(item.equipSlot())];
        if (primary == null) {
            return null;
        }
        return rasterizer.render(item, primary, primaryModelId, width, height,
                buildCamera(item, null, zoom, rotationX, rotationY, rotationZ, cameraOffsetX, cameraOffsetY, item.equipSlot(), animationFrame),
                originalColors, modifiedColors, highlightedOriginalColors);
    }

    private static int countNonNullParts(RsModelData[] parts) {
        int count = 0;
        for (RsModelData part : parts) {
            if (part != null) {
                count++;
            }
        }
        return count;
    }

    private static WidgetModelCamera buildCamera(ItemDefinitionRecord item, BasDefinition bas, int zoom, int rotationX, int rotationY, int rotationZ,
                                                 int offsetX, int offsetY, int equipSlot, int animationFrame) {
        int baseZoom = switch (equipSlot) {
            case 0, 1, 2, 4 -> 3100;
            case 3, 5 -> 3500;
            case 7 -> 2950;
            default -> 2850;
        };
        int cameraOffsetY = bas == null ? 0 : bas.liftOffset();
        if (bas != null) {
            baseZoom += bas.zoomOffset();
        }
        int pitch = rotationX + ((equipSlot == 3 || equipSlot == 5) ? 120 : 104);
        int yaw = rotationY + ((equipSlot == 3 || equipSlot == 5) ? 84 : 64);
        int roll = rotationZ;
        int finalZoom = Math.max(1200, baseZoom + (zoom - 2000));
        int finalOffsetY = cameraOffsetY + offsetY;
        return new WidgetModelCamera(finalZoom, pitch, yaw, roll, offsetX, finalOffsetY);
    }

    private static int mapPrimaryFallbackSlot(int equipSlot) {
        return switch (equipSlot) {
            case 0 -> 0;
            case 1 -> 1;
            case 2 -> 2;
            case 3 -> 3;
            case 4 -> 4;
            case 5 -> 5;
            case 7 -> 7;
            case 9 -> 9;
            case 10 -> 10;
            case 12 -> 12;
            case 14 -> 14;
            default -> 4;
        };
    }

    private static int getRenderAnimId(ItemDefinitionRecord item) {
        Object value = item.clientScriptData() == null ? null : item.clientScriptData().get(644);
        return value instanceof Integer integer ? integer : 1426;
    }

    private static RsModelData applyBasTransform(RsModelData model, BasDefinition bas, int slotIndex) {
        if (model == null || bas == null || slotIndex < 0 || bas.transforms() == null || slotIndex >= bas.transforms().length) {
            return model;
        }
        int[] transform = bas.transforms()[slotIndex];
        if (transform == null) {
            return model;
        }
        return model.rotated(transform[3] << 3, transform[4] << 3, transform[5] << 3)
                .translated(transform[0], transform[1], transform[2]);
    }
}
