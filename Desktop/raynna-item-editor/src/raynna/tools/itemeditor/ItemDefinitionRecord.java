package raynna.tools.itemeditor;

import java.util.Arrays;
import java.util.Map;

public record ItemDefinitionRecord(
        int id,
        String name,
        int modelId,
        int modelZoom,
        int modelRotation1,
        int modelRotation2,
        int modelRotation3,
        int modelOffset1,
        int modelOffset2,
        int modelScaleX,
        int modelScaleY,
        int modelScaleZ,
        boolean stackable,
        int price,
        boolean membersOnly,
        int equipSlot,
        int equipType,
        int maleEquip1,
        int maleEquip2,
        int maleEquip3,
        int femaleEquip1,
        int femaleEquip2,
        int femaleEquip3,
        int maleWearOffsetX,
        int maleWearOffsetY,
        int maleWearOffsetZ,
        int femaleWearOffsetX,
        int femaleWearOffsetY,
        int femaleWearOffsetZ,
        int certId,
        int certTemplateId,
        int lendId,
        int lendTemplateId,
        int teamId,
        String[] groundOptions,
        String[] inventoryOptions,
        int[] originalModelColors,
        int[] modifiedModelColors,
        short[] originalTextureColors,
        short[] modifiedTextureColors,
        Map<Integer, Object> clientScriptData
) {

    public String toDisplayText() {
        StringBuilder builder = new StringBuilder(2048);
        append(builder, "id", id);
        append(builder, "name", name);
        append(builder, "modelId", modelId);
        append(builder, "modelZoom", modelZoom);
        append(builder, "modelRotation1", modelRotation1);
        append(builder, "modelRotation2", modelRotation2);
        append(builder, "modelRotation3", modelRotation3);
        append(builder, "modelOffset1", modelOffset1);
        append(builder, "modelOffset2", modelOffset2);
        append(builder, "modelScaleX", modelScaleX);
        append(builder, "modelScaleY", modelScaleY);
        append(builder, "modelScaleZ", modelScaleZ);
        append(builder, "stackable", stackable);
        append(builder, "price", price);
        append(builder, "membersOnly", membersOnly);
        append(builder, "equipSlot", equipSlot);
        append(builder, "equipType", equipType);
        append(builder, "maleEquip1", maleEquip1);
        append(builder, "maleEquip2", maleEquip2);
        append(builder, "maleEquip3", maleEquip3);
        append(builder, "femaleEquip1", femaleEquip1);
        append(builder, "femaleEquip2", femaleEquip2);
        append(builder, "femaleEquip3", femaleEquip3);
        append(builder, "maleWearOffsetX", maleWearOffsetX);
        append(builder, "maleWearOffsetY", maleWearOffsetY);
        append(builder, "maleWearOffsetZ", maleWearOffsetZ);
        append(builder, "femaleWearOffsetX", femaleWearOffsetX);
        append(builder, "femaleWearOffsetY", femaleWearOffsetY);
        append(builder, "femaleWearOffsetZ", femaleWearOffsetZ);
        append(builder, "certId", certId);
        append(builder, "certTemplateId", certTemplateId);
        append(builder, "lendId", lendId);
        append(builder, "lendTemplateId", lendTemplateId);
        append(builder, "teamId", teamId);
        append(builder, "groundOptions", Arrays.toString(groundOptions));
        append(builder, "inventoryOptions", Arrays.toString(inventoryOptions));
        append(builder, "originalModelColors", Arrays.toString(originalModelColors));
        append(builder, "modifiedModelColors", Arrays.toString(modifiedModelColors));
        append(builder, "originalTextureColors", Arrays.toString(originalTextureColors));
        append(builder, "modifiedTextureColors", Arrays.toString(modifiedTextureColors));
        builder.append("clientScriptData=").append(clientScriptData == null ? "{}" : clientScriptData).append('\n');
        return builder.toString();
    }

    private static void append(StringBuilder builder, String label, Object value) {
        builder.append(label).append('=').append(value).append('\n');
    }
}
