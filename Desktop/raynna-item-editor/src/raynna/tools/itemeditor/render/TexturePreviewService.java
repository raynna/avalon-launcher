package raynna.tools.itemeditor.render;

import com.jagex.Class53;
import com.jagex.CodexTextureProviderFactory;
import com.jagex.Interface_ma;
import raynna.tools.itemeditor.ItemDefinitionsService;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class TexturePreviewService {
    private static final int THUMB_SIZE = 42;

    private final ItemDefinitionsService itemService;
    private final Map<Integer, TextureThumbnail> thumbnailCache = new HashMap<>();
    private final Map<Integer, BufferedImage> textureImageCache = new HashMap<>();
    private Interface_ma textureProvider;

    TexturePreviewService(ItemDefinitionsService itemService) {
        this.itemService = itemService;
    }

    List<TextureThumbnail> listTextureThumbnails() {
        List<TextureThumbnail> thumbnails = new ArrayList<>();
        for (int textureId : itemService.listTextureIds()) {
            thumbnails.add(getTextureThumbnail(textureId));
        }
        return thumbnails;
    }

    TextureThumbnail getTextureThumbnail(int textureId) {
        return thumbnailCache.computeIfAbsent(textureId, this::loadThumbnail);
    }

    BufferedImage getTextureImage(int textureId) {
        return textureImageCache.computeIfAbsent(textureId, this::loadTextureImage);
    }

    private TextureThumbnail loadThumbnail(int textureId) {
        BufferedImage image = getTextureImage(textureId);
        if (image == null) {
            image = missingThumb(textureId);
        } else {
            image = scaleToThumb(image);
        }
        return new TextureThumbnail(textureId, image);
    }

    private BufferedImage loadTextureImage(int textureId) {
        return decodeTextureImage(textureId);
    }

    private BufferedImage decodeTextureImage(int textureId) {
        try {
            Interface_ma provider = getTextureProvider();
            if (provider == null || !provider.method170(textureId, (short) 0)) {
                return null;
            }
            Class53 details = provider.method174(textureId, 0);
            if (details == null) {
                return null;
            }
            int size = details.aBoolean518 ? 64 : 128;
            int[] pixels = details.anInt528 == 2
                    ? provider.method172(textureId, 1.0F, size, size, false, (byte) 0)
                    : provider.method171(textureId, 1.0F, size, size, false, 0);
            if (pixels == null || pixels.length == 0) {
                return null;
            }
            BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
            image.setRGB(0, 0, size, size, pixels, 0, size);
            return image;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private Interface_ma getTextureProvider() {
        if (textureProvider == null) {
            textureProvider = CodexTextureProviderFactory.create(itemService);
        }
        return textureProvider;
    }

    private static BufferedImage scaleToThumb(BufferedImage source) {
        BufferedImage output = new BufferedImage(THUMB_SIZE, THUMB_SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = output.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2.setColor(new Color(26, 26, 26));
        g2.fillRect(0, 0, THUMB_SIZE, THUMB_SIZE);
        double scale = Math.min((THUMB_SIZE - 4) / (double) Math.max(1, source.getWidth()), (THUMB_SIZE - 4) / (double) Math.max(1, source.getHeight()));
        int width = Math.max(1, (int) Math.round(source.getWidth() * scale));
        int height = Math.max(1, (int) Math.round(source.getHeight() * scale));
        int x = (THUMB_SIZE - width) / 2;
        int y = (THUMB_SIZE - height) / 2;
        g2.drawImage(source, x, y, width, height, null);
        g2.setColor(new Color(0, 0, 0, 80));
        g2.drawRect(0, 0, THUMB_SIZE - 1, THUMB_SIZE - 1);
        g2.dispose();
        return output;
    }

    private static BufferedImage missingThumb(int textureId) {
        BufferedImage output = new BufferedImage(THUMB_SIZE, THUMB_SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = output.createGraphics();
        g2.setColor(new Color(34, 34, 34));
        g2.fillRect(0, 0, THUMB_SIZE, THUMB_SIZE);
        g2.setColor(new Color(180, 80, 80));
        g2.drawRect(1, 1, THUMB_SIZE - 3, THUMB_SIZE - 3);
        g2.drawLine(8, 8, THUMB_SIZE - 9, THUMB_SIZE - 9);
        g2.drawLine(THUMB_SIZE - 9, 8, 8, THUMB_SIZE - 9);
        g2.setColor(new Color(220, 220, 220));
        g2.setFont(new Font("SansSerif", Font.BOLD, 9));
        String text = String.valueOf(textureId);
        FontMetrics metrics = g2.getFontMetrics();
        g2.drawString(text, Math.max(2, (THUMB_SIZE - metrics.stringWidth(text)) / 2), THUMB_SIZE - 6);
        g2.dispose();
        return output;
    }

    record TextureThumbnail(int id, BufferedImage image) {
        @Override
        public String toString() {
            return String.valueOf(id);
        }
    }
}
