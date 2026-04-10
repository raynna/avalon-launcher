package raynna.tools.itemeditor.render;

import raynna.tools.itemeditor.ItemDefinitionRecord;
import raynna.tools.itemeditor.ItemDefinitionsService;

import java.awt.*;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ItemModelRenderer {
    private static final int CONFIG_INDEX = 2;
    private static final int BODY_KIT_ARCHIVE = 3;
    private static final int BAS_ARCHIVE = 32;
    private static final int CACHE_LIMIT = 96;
    private static final int IMAGE_CACHE_LIMIT = 192;
    private static final int MAX_PROJECTED_COORD = 4096;
    private static final int INVENTORY_RENDER_SCALE = 3;
    private static final int WORN_RENDER_SCALE = 6;
    private static final int INVENTORY_SPRITE_WIDTH = 36;
    private static final int INVENTORY_SPRITE_HEIGHT = 32;
    private static final int INVENTORY_SPRITE_CENTER_X = 16;
    private static final int INVENTORY_SPRITE_CENTER_Y = 16;
    private static final double INVENTORY_FOCAL_LENGTH = 512.0;
    private static final int MAX_RENDER_VERTICES = 12000;
    private static final int MAX_RENDER_FACES = 20000;
    private static final double WORN_FIT_PADDING = 0.72;
    private static final String TRANSIENT_MISSING_MODEL_BYTES = "missing model bytes";
    private static final int MODEL_LOAD_RETRIES = 3;

    private final ItemDefinitionsService itemService;
    private final BodyKitService bodyKitService;
    private final BasDefinitionService basDefinitionService;
    private final AppearanceColorService appearanceColorService;
    private final PlayerAppearanceBuilder appearanceBuilder;
    private final WornPreviewRenderer wornPreviewRenderer;
    private final Map<Integer, BodyKitDefinition> bodyKitCache = new LinkedHashMap<>(128, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Integer, BodyKitDefinition> eldest) {
            return size() > 128;
        }
    };
    private final Map<Integer, BASDefinition> basCache = new LinkedHashMap<>(64, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Integer, BASDefinition> eldest) {
            return size() > 64;
        }
    };
    private final Map<Integer, RsModelData> modelCache = new LinkedHashMap<>(CACHE_LIMIT, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Integer, RsModelData> eldest) {
            return size() > CACHE_LIMIT;
        }
    };
    private final Map<Integer, String> failureCache = new LinkedHashMap<>(CACHE_LIMIT, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Integer, String> eldest) {
            return size() > CACHE_LIMIT;
        }
    };
    private final Map<RenderKey, BufferedImage> imageCache = new LinkedHashMap<>(IMAGE_CACHE_LIMIT, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<RenderKey, BufferedImage> eldest) {
            return size() > IMAGE_CACHE_LIMIT;
        }
    };

    public ItemModelRenderer(ItemDefinitionsService itemService) {
        this.itemService = itemService;
        this.bodyKitService = new BodyKitService(itemService);
        this.basDefinitionService = new BasDefinitionService(itemService);
        this.appearanceColorService = new AppearanceColorService(itemService);
        this.appearanceBuilder = new PlayerAppearanceBuilder(bodyKitService, appearanceColorService, this::getModel);
        this.wornPreviewRenderer = new WornPreviewRenderer(
                appearanceBuilder,
                basDefinitionService,
                (item, model, modelId, width, height, camera, originalColors, modifiedColors, highlightedOriginalColors) ->
                        renderModel(item, originalColors, modifiedColors, model, modelId, width, height,
                                camera.zoom(), camera.rotationX(), camera.rotationY(), camera.rotationZ(), camera.offsetX(), camera.offsetY(), false, highlightedOriginalColors)
        );
    }
    public BufferedImage renderInventory(ItemDefinitionRecord item, int width, int height, int zoom, int rotationX, int rotationY, int rotationZ, int offsetX, int offsetY) {
        return renderInventory(item, item.originalModelColors(), item.modifiedModelColors(), width, height, zoom, rotationX, rotationY, rotationZ, offsetX, offsetY, new int[0]);
    }

    public BufferedImage renderInventory(ItemDefinitionRecord item, int[] originalColors, int[] modifiedColors, int width, int height, int zoom, int rotationX, int rotationY, int rotationZ, int offsetX, int offsetY) {
        return renderInventory(item, originalColors, modifiedColors, width, height, zoom, rotationX, rotationY, rotationZ, offsetX, offsetY, new int[0]);
    }

    public BufferedImage renderInventory(ItemDefinitionRecord item, int[] originalColors, int[] modifiedColors, int width, int height, int zoom, int rotationX, int rotationY, int rotationZ, int offsetX, int offsetY, int[] highlightedOriginalColors) {
        if (item.certTemplateId() == -1 || item.certId() == -1 || item.certId() == item.id()) {
            BufferedImage image = render(item,
                    originalColors,
                    modifiedColors,
                    item.modelId(),
                    width,
                    height,
                    zoom,
                    rotationX,
                    rotationY,
                    rotationZ,
                    offsetX,
                    offsetY,
                    true,
                    highlightedOriginalColors);
            if (image == null) {
                clearImageCacheForModel(item.modelId());
                image = render(item,
                        originalColors,
                        modifiedColors,
                        item.modelId(),
                        width,
                        height,
                        zoom,
                        rotationX,
                        rotationY,
                        rotationZ,
                        offsetX,
                        offsetY,
                        true,
                        highlightedOriginalColors);
            }
            return image;
        }
        ItemDefinitionRecord baseItem = item;
        if (item.certTemplateId() != -1 && item.certTemplateId() != item.id()) {
            try {
                baseItem = itemService.load(item.certTemplateId());
            } catch (RuntimeException ignored) {
                baseItem = item;
            }
        }
        BufferedImage base = render(baseItem,
                baseItem.originalModelColors(),
                baseItem.modifiedModelColors(),
                baseItem.modelId(),
                width,
                height,
                baseItem.modelZoom(),
                rotationX,
                rotationY,
                rotationZ,
                offsetX,
                offsetY,
                true,
                new int[0]);
        if (base == null) {
            return base;
        }
        ItemDefinitionRecord realItem;
        try {
            realItem = itemService.load(item.certId());
        } catch (RuntimeException ignored) {
            return base;
        }
        if (realItem == null || realItem.modelId() < 0) {
            return base;
        }
        int insetWidth = Math.max(10, (int) Math.round(width * 0.23));
        int insetHeight = Math.max(10, (int) Math.round(height * 0.23));
        BufferedImage miniature = render(realItem,
                originalColors,
                modifiedColors,
                realItem.modelId(),
                insetWidth,
                insetHeight,
                realItem.modelZoom(),
                0,
                0,
                0,
                0,
                0,
                true,
                highlightedOriginalColors);
        if (miniature == null) {
            return base;
        }
        BufferedImage composed = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = composed.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2.drawImage(base, 0, 0, null);
        int insetX = (width - miniature.getWidth()) / 2 - Math.max(1, width / 36);
        int insetY = (height - miniature.getHeight()) / 2 - Math.max(1, height / 28);
        g2.drawImage(miniature, insetX, insetY, null);
        g2.dispose();
        synchronized (modelCache) {
            imageCache.put(new RenderKey(item.id(), item.modelId(), width, height, zoom, rotationX, rotationY, rotationZ, offsetX, offsetY, true,
                    java.util.Arrays.hashCode(originalColors), java.util.Arrays.hashCode(modifiedColors), java.util.Arrays.hashCode(highlightedOriginalColors)), composed);
        }
        return composed;
    }

    public BufferedImage renderWorn(ItemDefinitionRecord item, boolean female, int width, int height,
                                        int zoom, int rotationX, int rotationY, int rotationZ, int offsetX, int offsetY, int animationFrame) {
        return renderWorn(item, item.originalModelColors(), item.modifiedModelColors(), female, width, height,
                zoom, rotationX, rotationY, rotationZ, offsetX, offsetY,
                female ? item.femaleWearOffsetX() : item.maleWearOffsetX(),
                female ? item.femaleWearOffsetY() : item.maleWearOffsetY(),
                female ? item.femaleWearOffsetZ() : item.maleWearOffsetZ(),
                animationFrame, new int[0]);
    }

    public BufferedImage renderWorn(ItemDefinitionRecord item, int[] originalColors, int[] modifiedColors, boolean female, int width, int height,
                                    int zoom, int rotationX, int rotationY, int rotationZ, int offsetX, int offsetY, int animationFrame) {
        return renderWorn(item, originalColors, modifiedColors, female, width, height,
                zoom, rotationX, rotationY, rotationZ, offsetX, offsetY,
                female ? item.femaleWearOffsetX() : item.maleWearOffsetX(),
                female ? item.femaleWearOffsetY() : item.maleWearOffsetY(),
                female ? item.femaleWearOffsetZ() : item.maleWearOffsetZ(),
                animationFrame, new int[0]);
    }

    public BufferedImage renderWorn(ItemDefinitionRecord item, int[] originalColors, int[] modifiedColors, boolean female, int width, int height,
                                    int zoom, int rotationX, int rotationY, int rotationZ, int cameraOffsetX, int cameraOffsetY,
                                    int wearOffsetX, int wearOffsetY, int wearOffsetZ, int animationFrame, int[] highlightedOriginalColors) {
        return wornPreviewRenderer.render(item, originalColors, modifiedColors, female, width, height,
                zoom, rotationX, rotationY, rotationZ, cameraOffsetX, cameraOffsetY, wearOffsetX, wearOffsetY, wearOffsetZ, animationFrame, highlightedOriginalColors);
    }
    public List<ModelColorInfo> listModelColors(int modelId) {
        RsModelData model = getModel(modelId);
        if (model == null) {
            return List.of();
        }
        Map<Integer, Integer> counts = new LinkedHashMap<>();
        for (short faceColor : model.faceColors()) {
            int color = faceColor & 0xFFFF;
            counts.merge(color, 1, Integer::sum);
        }
        List<ModelColorInfo> result = new ArrayList<>(counts.size());
        counts.forEach((color, count) -> result.add(new ModelColorInfo(color, count)));
        result.sort(Comparator.comparingInt(ModelColorInfo::color));
        return result;
    }

    public List<ModelColorInfo> listWornModelColors(ItemDefinitionRecord item, boolean female, int[] originalColors, int[] modifiedColors) {
        if (item == null) {
            return List.of();
        }
        int primaryModelId = female ? item.femaleEquip1() : item.maleEquip1();
        int secondaryModelId = female ? item.femaleEquip2() : item.maleEquip2();
        int tertiaryModelId = female ? item.femaleEquip3() : item.maleEquip3();
        int wearOffsetX = female ? item.femaleWearOffsetX() : item.maleWearOffsetX();
        int wearOffsetY = female ? item.femaleWearOffsetY() : item.maleWearOffsetY();
        int wearOffsetZ = female ? item.femaleWearOffsetZ() : item.maleWearOffsetZ();
        RsModelData model = RsModelData.combine(
                translateForWear(recolorWornSource(getModel(primaryModelId), originalColors, modifiedColors), wearOffsetX, wearOffsetY, wearOffsetZ),
                translateForWear(recolorWornSource(getModel(secondaryModelId), originalColors, modifiedColors), wearOffsetX, wearOffsetY, wearOffsetZ),
                translateForWear(recolorWornSource(getModel(tertiaryModelId), originalColors, modifiedColors), wearOffsetX, wearOffsetY, wearOffsetZ)
        );
        if (model == null) {
            return List.of();
        }
        Map<Integer, Integer> counts = new LinkedHashMap<>();
        for (short faceColor : model.faceColors()) {
            int color = faceColor & 0xFFFF;
            counts.merge(color, 1, Integer::sum);
        }
        List<ModelColorInfo> result = new ArrayList<>(counts.size());
        counts.forEach((color, count) -> result.add(new ModelColorInfo(color, count)));
        result.sort(Comparator.comparingInt(ModelColorInfo::color));
        return result;
    }

    public String getFailureReason(int modelId) {
        synchronized (modelCache) {
            return failureCache.get(modelId);
        }
    }

    public String debugDescribeModel(int modelId) {
        StringBuilder builder = new StringBuilder(256);
        builder.append("[model ").append(modelId).append("] ");
        if (modelId < 0) {
            builder.append("invalid");
            return builder.toString();
        }
        byte[] data = itemService.loadModelBytes(modelId);
        builder.append("bytes=").append(data == null ? -1 : data.length);
        synchronized (modelCache) {
            RsModelData cached = modelCache.get(modelId);
            if (cached != null) {
                builder.append(" cached verts=").append(cached.verticesX().length)
                        .append(" faces=").append(cached.faceA().length);
            }
            String failure = failureCache.get(modelId);
            if (failure != null) {
                builder.append(" failure=").append(failure);
            }
        }
        return builder.toString();
    }

    private BufferedImage render(ItemDefinitionRecord item, int[] originalColors, int[] modifiedColors, int modelId, int width, int height, int zoom, int rotationX, int rotationY, int rotationZ, int offsetX, int offsetY, boolean inventoryMode, int[] highlightedOriginalColors) {
        if (modelId < 0) {
            return null;
        }
        RenderKey key = new RenderKey(item.id(), modelId, width, height, zoom, rotationX, rotationY, rotationZ, offsetX, offsetY, inventoryMode,
                java.util.Arrays.hashCode(originalColors), java.util.Arrays.hashCode(modifiedColors), java.util.Arrays.hashCode(highlightedOriginalColors));
        synchronized (modelCache) {
            BufferedImage cached = imageCache.get(key);
            if (cached != null) {
                return cached;
            }
        }
        RsModelData model = getModel(modelId);
        if (model == null) {
            return null;
        }
        return renderModel(item, originalColors, modifiedColors, model, modelId, width, height, zoom, rotationX, rotationY, rotationZ, offsetX, offsetY, inventoryMode, highlightedOriginalColors);
    }

    private BufferedImage renderModel(ItemDefinitionRecord item, int[] originalColors, int[] modifiedColors, RsModelData model, int modelId, int width, int height, int zoom, int rotationX, int rotationY, int rotationZ, int offsetX, int offsetY, boolean inventoryMode, int[] highlightedOriginalColors) {
        RenderKey key = new RenderKey(item.id(), modelId, width, height, zoom, rotationX, rotationY, rotationZ, offsetX, offsetY, inventoryMode,
                java.util.Arrays.hashCode(originalColors), java.util.Arrays.hashCode(modifiedColors), java.util.Arrays.hashCode(highlightedOriginalColors));
        if (model.verticesX().length > MAX_RENDER_VERTICES || model.faceA().length > MAX_RENDER_FACES) {
            synchronized (modelCache) {
                failureCache.put(modelId, "model too complex for live preview");
            }
            return null;
        }
        model = model.recolored(originalColors, modifiedColors);

        int[] verticesX = model.verticesX().clone();
        int[] verticesY = model.verticesY().clone();
        int[] verticesZ = model.verticesZ().clone();

        Bounds rawBounds = boundsOf(verticesX, verticesY, verticesZ);
        scale(verticesX, verticesY, verticesZ, item.modelScaleX(), item.modelScaleY(), item.modelScaleZ());
        checkCancelled();
        Bounds scaledBounds = boundsOf(verticesX, verticesY, verticesZ);
        double modelMinYHalf = scaledBounds.minY() / 2.0;
        int effectiveZ = rotationZ;
        int effectiveY = rotationY;
        int effectiveX = rotationX;
        double xRadians = 0.0;
        double zoomUnits = 0.0;
        double translateX = 0.0;
        double translateY = 0.0;
        double translateZ = 0.0;
        if (inventoryMode) {
            effectiveZ = item.modelRotation3() + rotationZ;
            effectiveY = item.modelRotation2() + rotationY;
            effectiveX = item.modelRotation1() + rotationX;
            xRadians = Math.toRadians(clientAngleToDegrees(effectiveX));
            zoomUnits = zoom * 4.0;
            translateX = (item.modelOffset1() + offsetX) * 4.0;
            translateY = Math.sin(xRadians) * zoomUnits - modelMinYHalf + (item.modelOffset2() + offsetY) * 4.0;
            translateZ = Math.cos(xRadians) * zoomUnits + (item.modelOffset2() + offsetY) * 4.0;
            ViewTransform transform = ViewTransform.identity();
            transform.setAxisRotation(0.0, 0.0, 1.0, Math.toRadians(clientAngleToDegrees(-effectiveZ)));
            transform.rotateAxis(0.0, 1.0, 0.0, Math.toRadians(clientAngleToDegrees(effectiveY)));
            transform.translate(translateX, translateY, translateZ);
            transform.rotateAxis(1.0, 0.0, 0.0, Math.toRadians(clientAngleToDegrees(effectiveX)));
            applyViewTransform(verticesX, verticesY, verticesZ, transform);
        } else {
            effectiveZ = rotationZ;
            effectiveY = rotationY;
            effectiveX = rotationX;
            xRadians = Math.toRadians(clientAngleToDegrees(effectiveX));
            zoomUnits = zoom * 4.0;
            translateX = 0.0;
            translateY = Math.sin(xRadians) * zoomUnits;
            translateZ = Math.cos(xRadians) * zoomUnits;
            ViewTransform transform = ViewTransform.identity();
            transform.setAxisRotation(0.0, 0.0, 1.0, Math.toRadians(clientAngleToDegrees(-effectiveZ)));
            transform.rotateAxis(0.0, 1.0, 0.0, Math.toRadians(clientAngleToDegrees(effectiveY)));
            transform.translate(translateX, translateY, translateZ);
            transform.rotateAxis(1.0, 0.0, 0.0, Math.toRadians(clientAngleToDegrees(effectiveX)));
            applyViewTransform(verticesX, verticesY, verticesZ, transform);
        }
        Bounds transformedBounds = boundsOf(verticesX, verticesY, verticesZ);

        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (int i = 0; i < verticesX.length; i++) {
            if ((i & 1023) == 0) {
                checkCancelled();
            }
            minX = Math.min(minX, verticesX[i]);
            minY = Math.min(minY, verticesY[i]);
            minZ = Math.min(minZ, verticesZ[i]);
            maxX = Math.max(maxX, verticesX[i]);
            maxY = Math.max(maxY, verticesY[i]);
            maxZ = Math.max(maxZ, verticesZ[i]);
        }

        double centerX = inventoryMode ? 0.0 : (minX + maxX) / 2.0;
        double centerY = inventoryMode ? 0.0 : (minY + maxY) / 2.0;
        double centerZ = inventoryMode ? 0.0 : (minZ + maxZ) / 2.0;
        int renderScale = inventoryMode ? INVENTORY_RENDER_SCALE : WORN_RENDER_SCALE;
        int renderWidth = Math.max(1, width * renderScale);
        int renderHeight = Math.max(1, height * renderScale);
        double baseScale = inventoryMode
                ? 1.0
                : Math.min(renderWidth / Math.max(1.0, maxX - minX), renderHeight / Math.max(1.0, maxY - minY)) * (zoom / 2000.0) * WORN_FIT_PADDING;
        double originalBaseScale = baseScale;

        Projection projection = project(verticesX, verticesY, verticesZ, centerX, centerY, centerZ, baseScale, renderWidth, renderHeight, inventoryMode, offsetX, offsetY);
        if (!inventoryMode) {
            for (int attempt = 0; attempt < 6 && projectedAbsMax(projection) > MAX_PROJECTED_COORD; attempt++) {
                double overflow = projectedAbsMax(projection);
                double shrink = Math.max(0.18, (MAX_PROJECTED_COORD * 0.82) / Math.max(1.0, overflow));
                baseScale *= shrink;
                projection = project(verticesX, verticesY, verticesZ, centerX, centerY, centerZ, baseScale, renderWidth, renderHeight, false, offsetX, offsetY);
            }
        }
        int minScreenX = projection.minScreenX();
        int minScreenY = projection.minScreenY();
        int maxScreenX = projection.maxScreenX();
        int maxScreenY = projection.maxScreenY();

        if (Math.abs(minScreenX) > MAX_PROJECTED_COORD || Math.abs(maxScreenX) > MAX_PROJECTED_COORD
                || Math.abs(minScreenY) > MAX_PROJECTED_COORD || Math.abs(maxScreenY) > MAX_PROJECTED_COORD) {
            String reason = "projected bounds too large: [" + minScreenX + "," + minScreenY + "]..[" + maxScreenX + "," + maxScreenY + "]";
            synchronized (modelCache) {
                failureCache.put(modelId, reason);
            }
            return null;
        }

        int[] screenX = projection.screenX();
        int[] screenY = projection.screenY();
        double[] depth = projection.depth();

        List<FaceDraw> faces = new ArrayList<>(model.faceA().length);
        for (int i = 0; i < model.faceA().length; i++) {
            if ((i & 1023) == 0) {
                checkCancelled();
            }
            int a = model.faceA()[i] & 0xFFFF;
            int b = model.faceB()[i] & 0xFFFF;
            int c = model.faceC()[i] & 0xFFFF;
            int cross = (screenX[b] - screenX[a]) * (screenY[c] - screenY[a]) - (screenY[b] - screenY[a]) * (screenX[c] - screenX[a]);
            if (cross >= 0) {
                continue;
            }
            double light = faceLight(verticesX, verticesY, verticesZ, a, b, c);
            int originalFaceColor = resolveOriginalFaceColor(model.faceColors()[i] & 0xFFFF, originalColors, modifiedColors);
            int argb = shadeColor(hslToRgb(model.faceColors()[i] & 0xFFFF), light);
            boolean highlighted = containsHighlightedColor(highlightedOriginalColors, originalFaceColor);
            if (highlighted) {
                argb = blend(argb, 0xFFFFC857, 0.45);
            }
            faces.add(new FaceDraw(a, b, c, (depth[a] + depth[b] + depth[c]) / 3.0, argb, highlighted));
        }
        faces.sort(Comparator.comparingDouble(FaceDraw::depth));

        BufferedImage rawImage = new BufferedImage(renderWidth, renderHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = rawImage.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                inventoryMode ? RenderingHints.VALUE_ANTIALIAS_ON : RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                inventoryMode ? RenderingHints.VALUE_INTERPOLATION_BILINEAR : RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g2.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION,
                inventoryMode ? RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY : RenderingHints.VALUE_ALPHA_INTERPOLATION_SPEED);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        for (FaceDraw face : faces) {
            checkCancelled();
            Path2D path = new Path2D.Double();
            path.moveTo(screenX[face.a()], screenY[face.a()]);
            path.lineTo(screenX[face.b()], screenY[face.b()]);
            path.lineTo(screenX[face.c()], screenY[face.c()]);
            path.closePath();
            g2.setColor(new Color(face.argb(), true));
            g2.fill(path);
            if (face.highlighted()) {
                g2.setColor(new Color(255, 220, 120, 220));
                g2.draw(path);
            } else if (!inventoryMode) {
                g2.setColor(new Color(0, 0, 0, 28));
                g2.draw(path);
            }
        }
        g2.dispose();
        BufferedImage image = downsample(rawImage, width, height);
        synchronized (modelCache) {
            imageCache.put(key, image);
        }
        return image;
    }

    private static RsModelData recolorWornModel(RsModelData model, ItemDefinitionRecord item) {
        if (model == null) {
            return null;
        }
        return model.recolored(item.originalModelColors(), item.modifiedModelColors());
    }

    private static RsModelData recolorWornSource(RsModelData model, int[] originalColors, int[] modifiedColors) {
        if (model == null) {
            return null;
        }
        return model.recolored(originalColors, modifiedColors);
    }

    private static RsModelData translateForWear(RsModelData model, int x, int y, int z) {
        return model == null ? null : model.translated(x, y, z);
    }

    private RsModelData buildBodyModel(ItemDefinitionRecord item, boolean female) {
        int[] looks = female ? new int[]{276, 57, 57, 65, 68, 77, 80} : new int[]{7, 14, 18, 26, 34, 38, 42};
        List<RsModelData> parts = new ArrayList<>(7);
        if (item.equipSlot() != 4) {
            addBodyPart(parts, looks[2]);
        }
        if (item.equipSlot() != 4 || !hideArms(item)) {
            addBodyPart(parts, looks[3]);
        }
        if (item.equipSlot() != 7) {
            addBodyPart(parts, looks[5]);
        }
        if (item.equipSlot() != 0 || !hideHair(item)) {
            addBodyPart(parts, looks[0]);
        }
        if (item.equipSlot() != 9) {
            addBodyPart(parts, looks[4]);
        }
        if (item.equipSlot() != 10) {
            addBodyPart(parts, looks[6]);
        }
        if (!female && (item.equipSlot() != 0 || showBeard(item))) {
            addBodyPart(parts, looks[1]);
        }
        return parts.isEmpty() ? null : RsModelData.combine(parts.toArray(new RsModelData[0]));
    }

    private void addBodyPart(List<RsModelData> parts, int bodyKitId) {
        BodyKitDefinition kit = getBodyKit(bodyKitId);
        if (kit == null || kit.modelIds == null || kit.modelIds.length == 0) {
            return;
        }
        List<RsModelData> models = new ArrayList<>(kit.modelIds.length);
        for (int modelId : kit.modelIds) {
            RsModelData model = getModel(modelId);
            if (model != null) {
                models.add(model);
            }
        }
        if (models.isEmpty()) {
            return;
        }
        RsModelData combined = RsModelData.combine(models.toArray(new RsModelData[0]));
        if (kit.originalColors != null && kit.modifiedColors != null) {
            int[] original = new int[kit.originalColors.length];
            int[] modified = new int[kit.modifiedColors.length];
            for (int i = 0; i < kit.originalColors.length; i++) {
                original[i] = kit.originalColors[i] & 0xFFFF;
                modified[i] = kit.modifiedColors[i] & 0xFFFF;
            }
            combined = combined.recolored(original, modified);
        }
        parts.add(combined);
    }


    private RsModelData[] buildAppearanceParts(ItemDefinitionRecord item, boolean female) {
        RsModelData[] parts = new RsModelData[15];
        int[] looks = female ? new int[]{276, 57, 57, 65, 68, 77, 80} : new int[]{7, 14, 18, 26, 34, 38, 42};
        int equipSlot = item.equipSlot();

        if (equipSlot != 4) {
            parts[4] = buildBodyPartModel(looks[2]);
        }
        if (equipSlot != 4 || !hideArms(item)) {
            parts[6] = buildBodyPartModel(looks[3]);
        }
        if (equipSlot != 7) {
            parts[7] = buildBodyPartModel(looks[5]);
        }
        if (equipSlot != 0 || !hideHair(item)) {
            parts[8] = buildBodyPartModel(looks[0]);
        }
        if (equipSlot != 9) {
            parts[9] = buildBodyPartModel(looks[4]);
        }
        if (equipSlot != 10) {
            parts[10] = buildBodyPartModel(looks[6]);
        }
        if (!female && (equipSlot != 0 || showBeard(item))) {
            parts[11] = buildBodyPartModel(looks[1]);
        }

        int primaryModelId = female ? item.femaleEquip1() : item.maleEquip1();
        int secondaryModelId = female ? item.femaleEquip2() : item.maleEquip2();
        int tertiaryModelId = female ? item.femaleEquip3() : item.maleEquip3();
        int wearOffsetX = female ? item.femaleWearOffsetX() : item.maleWearOffsetX();
        int wearOffsetY = female ? item.femaleWearOffsetY() : item.maleWearOffsetY();
        int wearOffsetZ = female ? item.femaleWearOffsetZ() : item.maleWearOffsetZ();
        RsModelData equipped = RsModelData.combine(
                translateForWear(getModel(primaryModelId), wearOffsetX, wearOffsetY, wearOffsetZ),
                translateForWear(getModel(secondaryModelId), wearOffsetX, wearOffsetY, wearOffsetZ),
                translateForWear(getModel(tertiaryModelId), wearOffsetX, wearOffsetY, wearOffsetZ)
        );
        if (equipped == null) {
            return parts;
        }
        int appearanceSlot = switch (equipSlot) {
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
            default -> -1;
        };
        if (appearanceSlot >= 0) {
            parts[appearanceSlot] = equipped;
        }
        return parts;
    }

    private RsModelData buildBodyPartModel(int bodyKitId) {
        BodyKitDefinition kit = getBodyKit(bodyKitId);
        if (kit == null || kit.modelIds == null || kit.modelIds.length == 0) {
            return null;
        }
        List<RsModelData> models = new ArrayList<>(kit.modelIds.length);
        for (int modelId : kit.modelIds) {
            RsModelData model = getModel(modelId);
            if (model != null) {
                models.add(model);
            }
        }
        if (models.isEmpty()) {
            return null;
        }
        RsModelData combined = RsModelData.combine(models.toArray(new RsModelData[0]));
        if (kit.originalColors != null && kit.modifiedColors != null) {
            int[] original = new int[kit.originalColors.length];
            int[] modified = new int[kit.modifiedColors.length];
            for (int i = 0; i < kit.originalColors.length; i++) {
                original[i] = kit.originalColors[i] & 0xFFFF;
                modified[i] = kit.modifiedColors[i] & 0xFFFF;
            }
            combined = combined.recolored(original, modified);
        }
        return combined;
    }
    private BodyKitDefinition getBodyKit(int bodyKitId) {
        if (bodyKitId < 0) {
            return null;
        }
        synchronized (bodyKitCache) {
            BodyKitDefinition cached = bodyKitCache.get(bodyKitId);
            if (cached != null) {
                return cached;
            }
        }
        byte[] data = itemService.loadConfigBytes(CONFIG_INDEX, BODY_KIT_ARCHIVE, bodyKitId);
        if (data == null) {
            return null;
        }
        BodyKitDefinition decoded = BodyKitDefinition.decode(data);
        synchronized (bodyKitCache) {
            bodyKitCache.put(bodyKitId, decoded);
        }
        return decoded;
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
        if (name.contains("neitiznot faceguard")) {
            return true;
        }
        if (name.contains("mime m")) {
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

    private static int getRenderAnimId(ItemDefinitionRecord item) {
        Object value = item.clientScriptData() == null ? null : item.clientScriptData().get(644);
        return value instanceof Integer integer ? integer : 1426;
    }


    private BASDefinition getBasDefinition(int renderAnimId) {
        if (renderAnimId < 0) {
            return null;
        }
        synchronized (basCache) {
            BASDefinition cached = basCache.get(renderAnimId);
            if (cached != null) {
                return cached;
            }
        }
        byte[] data = itemService.loadConfigBytes(CONFIG_INDEX, BAS_ARCHIVE, renderAnimId);
        if (data == null) {
            return null;
        }
        BASDefinition decoded = BASDefinition.decode(data);
        synchronized (basCache) {
            basCache.put(renderAnimId, decoded);
        }
        return decoded;
    }

    private static RsModelData applyBasTransform(RsModelData model, BASDefinition bas, int slotIndex) {
        if (model == null || bas == null || slotIndex < 0 || bas.transforms == null || slotIndex >= bas.transforms.length) {
            return model;
        }
        int[] transform = bas.transforms[slotIndex];
        if (transform == null) {
            return model;
        }
        return model.rotated(transform[3] << 3, transform[4] << 3, transform[5] << 3)
                .translated(transform[0], transform[1], transform[2]);
    }

    private static int mapEquipSlotToBasIndex(int equipSlot) {
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
            default -> 4;
        };
    }
    private void clearImageCacheForModel(int modelId) {
        synchronized (modelCache) {
            imageCache.entrySet().removeIf(entry -> entry.getKey().modelId() == modelId);
        }
    }

    private static void checkCancelled() {
        if (Thread.currentThread().isInterrupted()) {
            throw new RuntimeException("preview render cancelled");
        }
    }

    private static Bounds boundsOf(int[] x, int[] y, int[] z) {
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (int i = 0; i < x.length; i++) {
            minX = Math.min(minX, x[i]);
            minY = Math.min(minY, y[i]);
            minZ = Math.min(minZ, z[i]);
            maxX = Math.max(maxX, x[i]);
            maxY = Math.max(maxY, y[i]);
            maxZ = Math.max(maxZ, z[i]);
        }
        return new Bounds(minX, minY, minZ, maxX, maxY, maxZ);
    }

    private static String fmt(double value) {
        return String.format(java.util.Locale.ROOT, "%.3f", value);
    }

    private static int projectedAbsMax(Projection projection) {
        return Math.max(
                Math.max(Math.abs(projection.minScreenX()), Math.abs(projection.maxScreenX())),
                Math.max(Math.abs(projection.minScreenY()), Math.abs(projection.maxScreenY()))
        );
    }

    private static Projection project(int[] verticesX, int[] verticesY, int[] verticesZ, double centerX, double centerY, double centerZ, double baseScale, int width, int height, boolean inventoryMode, int offsetX, int offsetY) {
        int[] screenX = new int[verticesX.length];
        int[] screenY = new int[verticesY.length];
        double[] depth = new double[verticesZ.length];
        int minScreenX = Integer.MAX_VALUE;
        int minScreenY = Integer.MAX_VALUE;
        int maxScreenX = Integer.MIN_VALUE;
        int maxScreenY = Integer.MIN_VALUE;
        int wornScreenOffsetX = inventoryMode ? 0 : (int) Math.round(offsetX * 1.6);
        int wornScreenOffsetY = inventoryMode ? 0 : (int) Math.round(offsetY * 1.6);
        for (int i = 0; i < verticesX.length; i++) {
            double x = (verticesX[i] - centerX) * baseScale;
            double y = (verticesY[i] - centerY) * baseScale;
            double z = (verticesZ[i] - centerZ) * baseScale;
            if (inventoryMode) {
                double inventoryScaleX = width / (double) INVENTORY_SPRITE_WIDTH;
                double inventoryScaleY = height / (double) INVENTORY_SPRITE_HEIGHT;
                double spriteCenterX = INVENTORY_SPRITE_CENTER_X * inventoryScaleX;
                double spriteCenterY = INVENTORY_SPRITE_CENTER_Y * inventoryScaleY;
                double focalLength = INVENTORY_FOCAL_LENGTH * ((inventoryScaleX + inventoryScaleY) * 0.5);
                double depthValue = Math.abs(z) < 50.0 ? (z < 0.0 ? -50.0 : 50.0) : z;
                screenX[i] = (int) Math.round(spriteCenterX + (x * focalLength) / depthValue);
                screenY[i] = (int) Math.round(spriteCenterY + (y * focalLength) / depthValue);
                depth[i] = -depthValue;
            } else {
                double depthValue = 900.0 + z;
                if (depthValue < 160.0) {
                    depthValue = 160.0;
                }
                double focalLength = Math.min(width, height) * 1.9;
                screenX[i] = (int) Math.round(width / 2.0 + (x * focalLength) / depthValue) + wornScreenOffsetX;
                screenY[i] = (int) Math.round(height * 0.64 + (y * focalLength) / depthValue) + wornScreenOffsetY;
                depth[i] = -depthValue;
            }
            minScreenX = Math.min(minScreenX, screenX[i]);
            minScreenY = Math.min(minScreenY, screenY[i]);
            maxScreenX = Math.max(maxScreenX, screenX[i]);
            maxScreenY = Math.max(maxScreenY, screenY[i]);
        }
        return new Projection(screenX, screenY, depth, minScreenX, minScreenY, maxScreenX, maxScreenY);
    }

    private static BufferedImage downsample(BufferedImage source, int width, int height) {
        if (source.getWidth() == width && source.getHeight() == height) {
            return source;
        }
        BufferedImage output = new BufferedImage(Math.max(1, width), Math.max(1, height), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = output.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.drawImage(source, 0, 0, width, height, null);
        g2.dispose();
        return output;
    }

    private RsModelData getModel(int modelId) {
        synchronized (modelCache) {
            RsModelData cached = modelCache.get(modelId);
            if (cached != null) {
                return cached;
            }
            String failure = failureCache.get(modelId);
            if (failure != null && !TRANSIENT_MISSING_MODEL_BYTES.equals(failure)) {
                return null;
            }
        }
        byte[] data = null;
        for (int attempt = 0; attempt < MODEL_LOAD_RETRIES && data == null; attempt++) {
            data = itemService.loadModelBytes(modelId);
        }
        if (data == null) {
            synchronized (modelCache) {
                failureCache.put(modelId, TRANSIENT_MISSING_MODEL_BYTES);
            }
            return null;
        }
        RsModelData decoded;
        try {
            decoded = RsModelData.decode(data);
        } catch (RuntimeException exception) {
            synchronized (modelCache) {
                failureCache.put(modelId, exception.getClass().getSimpleName() + (exception.getMessage() == null ? "" : ": " + exception.getMessage()));
            }
            return null;
        }
        synchronized (modelCache) {
            failureCache.remove(modelId);
            modelCache.put(modelId, decoded);
        }
        return decoded;
    }

    private static void scale(int[] x, int[] y, int[] z, int scaleX, int scaleY, int scaleZ) {
        for (int i = 0; i < x.length; i++) {
            x[i] = x[i] * scaleX / 128;
            y[i] = y[i] * scaleY / 128;
            z[i] = z[i] * scaleZ / 128;
        }
    }

    private static void translate(int[] x, int[] y, int[] z, double tx, double ty, double tz) {
        for (int i = 0; i < x.length; i++) {
            x[i] = (int) Math.round(x[i] + tx);
            y[i] = (int) Math.round(y[i] + ty);
            z[i] = (int) Math.round(z[i] + tz);
        }
    }

    private static void applyViewTransform(int[] x, int[] y, int[] z, ViewTransform transform) {
        for (int i = 0; i < x.length; i++) {
            double pointX = x[i];
            double pointY = y[i];
            double pointZ = z[i];
            int newX = (int) Math.round(transform.m00 * pointX + transform.m10 * pointY + transform.m20 * pointZ + transform.tx);
            int newY = (int) Math.round(transform.m01 * pointX + transform.m11 * pointY + transform.m21 * pointZ + transform.ty);
            int newZ = (int) Math.round(transform.m02 * pointX + transform.m12 * pointY + transform.m22 * pointZ + transform.tz);
            x[i] = newX;
            y[i] = newY;
            z[i] = newZ;
        }
    }

    private static void rotateX(int[] y, int[] z, double degrees) {
        double radians = Math.toRadians(degrees);
        double sin = Math.sin(radians);
        double cos = Math.cos(radians);
        for (int i = 0; i < y.length; i++) {
            int newY = (int) Math.round(y[i] * cos + z[i] * sin);
            int newZ = (int) Math.round(z[i] * cos - y[i] * sin);
            y[i] = newY;
            z[i] = newZ;
        }
    }

    private static void rotateY(int[] x, int[] z, double degrees) {
        double radians = Math.toRadians(degrees);
        double sin = Math.sin(radians);
        double cos = Math.cos(radians);
        for (int i = 0; i < x.length; i++) {
            int newX = (int) Math.round(x[i] * cos - z[i] * sin);
            int newZ = (int) Math.round(x[i] * sin + z[i] * cos);
            x[i] = newX;
            z[i] = newZ;
        }
    }

    private static void rotateZ(int[] x, int[] y, double degrees) {
        double radians = Math.toRadians(degrees);
        double sin = Math.sin(radians);
        double cos = Math.cos(radians);
        for (int i = 0; i < x.length; i++) {
            int newX = (int) Math.round(x[i] * cos + y[i] * sin);
            int newY = (int) Math.round(y[i] * cos - x[i] * sin);
            x[i] = newX;
            y[i] = newY;
        }
    }

    private static double faceLight(int[] x, int[] y, int[] z, int a, int b, int c) {
        double abx = x[b] - x[a];
        double aby = y[b] - y[a];
        double abz = z[b] - z[a];
        double acx = x[c] - x[a];
        double acy = y[c] - y[a];
        double acz = z[c] - z[a];
        double nx = aby * acz - abz * acy;
        double ny = abz * acx - abx * acz;
        double nz = abx * acy - aby * acx;
        double length = Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (length == 0.0) {
            return 0.7;
        }
        nx /= length;
        ny /= length;
        nz /= length;
        double lightX = -0.45;
        double lightY = 0.7;
        double lightZ = -0.55;
        double lightLength = Math.sqrt(lightX * lightX + lightY * lightY + lightZ * lightZ);
        lightX /= lightLength;
        lightY /= lightLength;
        lightZ /= lightLength;
        return Math.max(0.28, Math.min(1.0, 0.55 + nx * lightX + ny * lightY + nz * lightZ));
    }

    private static int shadeColor(int rgb, double light) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        r = Math.max(0, Math.min(255, (int) Math.round(r * light)));
        g = Math.max(0, Math.min(255, (int) Math.round(g * light)));
        b = Math.max(0, Math.min(255, (int) Math.round(b * light)));
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private static int resolveOriginalFaceColor(int currentColor, int[] originalColors, int[] modifiedColors) {
        int compare = currentColor & 0xFFFF;
        int count = Math.min(originalColors == null ? 0 : originalColors.length, modifiedColors == null ? 0 : modifiedColors.length);
        for (int i = 0; i < count; i++) {
            if ((modifiedColors[i] & 0xFFFF) == compare) {
                return originalColors[i] & 0xFFFF;
            }
        }
        return compare;
    }

    private static boolean containsHighlightedColor(int[] highlightedOriginalColors, int originalFaceColor) {
        if (highlightedOriginalColors == null || highlightedOriginalColors.length == 0) {
            return false;
        }
        int compare = originalFaceColor & 0xFFFF;
        for (int color : highlightedOriginalColors) {
            if ((color & 0xFFFF) == compare) {
                return true;
            }
        }
        return false;
    }

    private static int blend(int argbA, int argbB, double mix) {
        double clamped = Math.max(0.0, Math.min(1.0, mix));
        int aA = (argbA >>> 24) & 0xFF;
        int rA = (argbA >>> 16) & 0xFF;
        int gA = (argbA >>> 8) & 0xFF;
        int bA = argbA & 0xFF;
        int aB = (argbB >>> 24) & 0xFF;
        int rB = (argbB >>> 16) & 0xFF;
        int gB = (argbB >>> 8) & 0xFF;
        int bB = argbB & 0xFF;
        int a = (int) Math.round(aA + (aB - aA) * clamped);
        int r = (int) Math.round(rA + (rB - rA) * clamped);
        int g = (int) Math.round(gA + (gB - gA) * clamped);
        int b = (int) Math.round(bA + (bB - bA) * clamped);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int hslToRgb(int packed) {
        double hue = ((packed >> 10) & 0x3F) / 64.0;
        double sat = ((packed >> 7) & 0x07) / 8.0;
        double light = (packed & 0x7F) / 128.0;
        sat = Math.min(1.0, sat * 1.25 + 0.08);
        light = Math.max(0.0, Math.min(1.0, light * 1.15));
        return hslToRgb(hue, sat, light);
    }

    private static int hslToRgb(double h, double s, double l) {
        double r;
        double g;
        double b;
        if (s == 0) {
            r = g = b = l;
        } else {
            double q = l < 0.5 ? l * (1 + s) : l + s - l * s;
            double p = 2 * l - q;
            r = hueToRgb(p, q, h + 1.0 / 3.0);
            g = hueToRgb(p, q, h);
            b = hueToRgb(p, q, h - 1.0 / 3.0);
        }
        return ((int) Math.round(r * 255) << 16) | ((int) Math.round(g * 255) << 8) | (int) Math.round(b * 255);
    }

    private static double hueToRgb(double p, double q, double t) {
        if (t < 0) {
            t += 1;
        }
        if (t > 1) {
            t -= 1;
        }
        if (t < 1.0 / 6.0) {
            return p + (q - p) * 6 * t;
        }
        if (t < 1.0 / 2.0) {
            return q;
        }
        if (t < 2.0 / 3.0) {
            return p + (q - p) * (2.0 / 3.0 - t) * 6;
        }
        return p;
    }

    private static double clientAngleToDegrees(int value) {
        int normalized = value & 2047;
        return normalized * (360.0 / 2048.0);
    }

    private record FaceDraw(int a, int b, int c, double depth, int argb, boolean highlighted) {
    }

    private record Bounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        @Override
        public String toString() {
            return "[" + minX + "," + minY + "," + minZ + "]..[" + maxX + "," + maxY + "," + maxZ + "]";
        }
    }

    private record Projection(int[] screenX, int[] screenY, double[] depth, int minScreenX, int minScreenY, int maxScreenX, int maxScreenY) {
    }

    public record ModelColorInfo(int color, int count) {
    }

    private record RenderKey(int itemId, int modelId, int width, int height, int zoom, int rotationX, int rotationY, int rotationZ, int offsetX, int offsetY, boolean inventoryMode, int originalColorsHash, int modifiedColorsHash, int highlightedColorsHash) {
    }

    private static final class ViewTransform {
        private double m00;
        private double m01;
        private double m02;
        private double m10;
        private double m11;
        private double m12;
        private double m20;
        private double m21;
        private double m22;
        private double tx;
        private double ty;
        private double tz;

        private static ViewTransform identity() {
            ViewTransform transform = new ViewTransform();
            transform.m00 = 1.0;
            transform.m11 = 1.0;
            transform.m22 = 1.0;
            return transform;
        }

        private void setAxisRotation(double ax, double ay, double az, double radians) {
            double cos = Math.cos(radians);
            double sin = Math.sin(radians);
            m00 = cos + ax * ax * (1.0 - cos);
            m01 = az * sin + ay * ax * (1.0 - cos);
            m02 = -ay * sin + az * ax * (1.0 - cos);
            m10 = -az * sin + ax * ay * (1.0 - cos);
            m11 = cos + ay * ay * (1.0 - cos);
            m12 = ax * sin + az * ay * (1.0 - cos);
            m20 = ay * sin + ax * az * (1.0 - cos);
            m21 = -ax * sin + ay * az * (1.0 - cos);
            m22 = cos + az * az * (1.0 - cos);
            tx = 0.0;
            ty = 0.0;
            tz = 0.0;
        }

        private void rotateAxis(double ax, double ay, double az, double radians) {
            double cos = Math.cos(radians);
            double sin = Math.sin(radians);
            double r00 = cos + ax * ax * (1.0 - cos);
            double r01 = az * sin + ay * ax * (1.0 - cos);
            double r02 = -ay * sin + az * ax * (1.0 - cos);
            double r10 = -az * sin + ax * ay * (1.0 - cos);
            double r11 = cos + ay * ay * (1.0 - cos);
            double r12 = ax * sin + az * ay * (1.0 - cos);
            double r20 = ay * sin + ax * az * (1.0 - cos);
            double r21 = -ax * sin + ay * az * (1.0 - cos);
            double r22 = cos + az * az * (1.0 - cos);

            double oldM00 = m00;
            double oldM01 = m01;
            double oldM02 = m02;
            double oldM10 = m10;
            double oldM11 = m11;
            double oldM12 = m12;
            double oldM20 = m20;
            double oldM21 = m21;
            double oldM22 = m22;
            double oldTx = tx;
            double oldTy = ty;
            double oldTz = tz;

            m00 = oldM00 * r00 + oldM01 * r10 + oldM02 * r20;
            m01 = oldM00 * r01 + oldM01 * r11 + oldM02 * r21;
            m02 = oldM00 * r02 + oldM01 * r12 + oldM02 * r22;
            m10 = oldM10 * r00 + oldM11 * r10 + oldM12 * r20;
            m11 = oldM10 * r01 + oldM11 * r11 + oldM12 * r21;
            m12 = oldM10 * r02 + oldM11 * r12 + oldM12 * r22;
            m20 = oldM20 * r00 + oldM21 * r10 + oldM22 * r20;
            m21 = oldM20 * r01 + oldM21 * r11 + oldM22 * r21;
            m22 = oldM20 * r02 + oldM21 * r12 + oldM22 * r22;
            tx = oldTx * r00 + oldTy * r10 + oldTz * r20;
            ty = oldTx * r01 + oldTy * r11 + oldTz * r21;
            tz = oldTx * r02 + oldTy * r12 + oldTz * r22;
        }

        private void translate(double dx, double dy, double dz) {
            tx += dx;
            ty += dy;
            tz += dz;
        }
    }


    private static final class BASDefinition {
        private int[][] transforms;
        private int liftOffset;
        private int zoomOffset;

        private static BASDefinition decode(byte[] data) {
            BASDefinition definition = new BASDefinition();
            ByteBuffer buffer = ByteBuffer.wrap(data);
            while (buffer.hasRemaining()) {
                int opcode = buffer.get() & 0xFF;
                if (opcode == 0) {
                    break;
                }
                switch (opcode) {
                    case 26 -> {
                        definition.liftOffset -= (buffer.get() & 0xFF) * 2;
                        definition.zoomOffset += (buffer.get() & 0xFF) * 6;
                    }
                    case 27 -> {
                        if (definition.transforms == null) {
                            definition.transforms = new int[16][];
                        }
                        int slot = buffer.get() & 0xFF;
                        definition.transforms[slot] = new int[6];
                        for (int i = 0; i < 6; i++) {
                            definition.transforms[slot][i] = buffer.getShort();
                        }
                    }
                    case 54 -> {
                        definition.zoomOffset += (buffer.get() & 0xFF) << 4;
                        definition.liftOffset -= (buffer.get() & 0xFF) << 1;
                    }
                    case 1,2,3,4,5,6,7,8,9,38,39,40,41,42,43,44,46,47,48,49,50,51 -> readBigSmart(buffer);
                    case 28 -> {
                        int count = buffer.get() & 0xFF;
                        buffer.position(buffer.position() + count);
                    }
                    case 29,31,34,37,45,53 -> buffer.get();
                    case 30,32,35,36,52,55,56 -> skipBasOpcode(buffer, opcode);
                    case 33 -> buffer.getShort();
                    default -> skipBasOpcode(buffer, opcode);
                }
            }
            return definition;
        }

        private int liftOffset() {
            return liftOffset;
        }

        private int zoomOffset() {
            return zoomOffset;
        }
    }

    private static void skipBasOpcode(ByteBuffer buffer, int opcode) {
        switch (opcode) {
            case 30, 32, 35 -> buffer.getShort();
            case 36 -> buffer.getShort();
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
            default -> {
                if (opcode >= 1 && opcode <= 9 || opcode == 38 || opcode == 39 || opcode == 40 || opcode == 41 || opcode == 42 || opcode == 43 || opcode == 44 || opcode == 46 || opcode == 47 || opcode == 48 || opcode == 49 || opcode == 50 || opcode == 51) {
                    readBigSmart(buffer);
                } else {
                    throw new IllegalStateException("Unsupported BAS opcode " + opcode);
                }
            }
        }
    }

    private static int readBigSmart(ByteBuffer buffer) {
        int peek = buffer.get(buffer.position()) & 0xFF;
        if (peek < 128) {
            int value = buffer.getShort() & 0xFFFF;
            return value == 32767 ? -1 : value;
        }
        return buffer.getInt() & 0x7FFFFFFF;
    }
    private static final class BodyKitDefinition {
        private int[] modelIds;
        private short[] originalColors;
        private short[] modifiedColors;

        private static BodyKitDefinition decode(byte[] data) {
            BodyKitDefinition definition = new BodyKitDefinition();
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
                        definition.modelIds = new int[count];
                        for (int i = 0; i < count; i++) {
                            definition.modelIds[i] = readBigSmart(buffer);
                        }
                    }
                    case 3 -> {
                    }
                    case 40 -> {
                        int count = buffer.get() & 0xFF;
                        definition.originalColors = new short[count];
                        definition.modifiedColors = new short[count];
                        for (int i = 0; i < count; i++) {
                            definition.originalColors[i] = (short) (buffer.getShort() & 0xFFFF);
                            definition.modifiedColors[i] = (short) (buffer.getShort() & 0xFFFF);
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
            return definition;
        }

        private static int readBigSmart(ByteBuffer buffer) {
            int peek = buffer.get(buffer.position()) & 0xFF;
            if (peek < 128) {
                int value = buffer.getShort() & 0xFFFF;
                return value == 32767 ? -1 : value;
            }
            return buffer.getInt() & 0x7FFFFFFF;
        }
    }
}















