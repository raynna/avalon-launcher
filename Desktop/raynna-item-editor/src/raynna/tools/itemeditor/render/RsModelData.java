package raynna.tools.itemeditor.render;

final class RsModelData {
    private static final int MAX_VERTEX_COUNT = 20000;
    private static final int MAX_FACE_COUNT = 30000;

    private final int[] verticesX;
    private final int[] verticesY;
    private final int[] verticesZ;
    private final short[] faceA;
    private final short[] faceB;
    private final short[] faceC;
    private final short[] faceColors;
    private final short[] faceTextures;
    private final byte[] faceTextureCoords;
    private final byte[] textureRenderTypes;
    private final short[] textureTriangleA;
    private final short[] textureTriangleB;
    private final short[] textureTriangleC;
    private final int[] textureScaleX;
    private final int[] textureScaleY;
    private final int[] textureScaleZ;
    private final byte[] textureDirection;
    private final byte[] textureSpeed;
    private final int[] textureTranslation;
    private final int[] textureUTrans;
    private final int[] textureVTrans;
    private final byte[] rawData;
    private final int faceTextureOffset;

    private RsModelData(
            int[] verticesX,
            int[] verticesY,
            int[] verticesZ,
            short[] faceA,
            short[] faceB,
            short[] faceC,
            short[] faceColors,
            short[] faceTextures,
            byte[] faceTextureCoords,
            byte[] textureRenderTypes,
            short[] textureTriangleA,
            short[] textureTriangleB,
            short[] textureTriangleC,
            int[] textureScaleX,
            int[] textureScaleY,
            int[] textureScaleZ,
            byte[] textureDirection,
            byte[] textureSpeed,
            int[] textureTranslation,
            int[] textureUTrans,
            int[] textureVTrans,
            byte[] rawData,
            int faceTextureOffset
    ) {
        this.verticesX = verticesX;
        this.verticesY = verticesY;
        this.verticesZ = verticesZ;
        this.faceA = faceA;
        this.faceB = faceB;
        this.faceC = faceC;
        this.faceColors = faceColors;
        this.faceTextures = faceTextures;
        this.faceTextureCoords = faceTextureCoords;
        this.textureRenderTypes = textureRenderTypes;
        this.textureTriangleA = textureTriangleA;
        this.textureTriangleB = textureTriangleB;
        this.textureTriangleC = textureTriangleC;
        this.textureScaleX = textureScaleX;
        this.textureScaleY = textureScaleY;
        this.textureScaleZ = textureScaleZ;
        this.textureDirection = textureDirection;
        this.textureSpeed = textureSpeed;
        this.textureTranslation = textureTranslation;
        this.textureUTrans = textureUTrans;
        this.textureVTrans = textureVTrans;
        this.rawData = rawData;
        this.faceTextureOffset = faceTextureOffset;
    }

    static RsModelData decode(byte[] data) {
        int marker1 = data[data.length - 1] & 0xFF;
        int marker2 = data[data.length - 2] & 0xFF;
        if (marker1 == 253 && marker2 == 255) {
            return decodeType3(data);
        }
        if (marker1 == 254 && marker2 == 255) {
            return decodeType2(data);
        }
        if (marker1 == 255 && marker2 == 255) {
            return decodeFormat1(data);
        }
        return decodeLegacy(data);
    }

    private static RsModelData decodeType3(byte[] data) {
        ModelDataBuffer footer = new ModelDataBuffer(data);
        footer.position(data.length - 26);
        int vertexCount = footer.readUnsignedShort();
        int faceCount = footer.readUnsignedShort();
        validateCounts(vertexCount, faceCount, data.length);
        int texTriCount = footer.readUnsignedByte();
        int hasFaceTypes = footer.readUnsignedByte();
        int faceRenderPri = footer.readUnsignedByte();
        int hasFaceAlphas = footer.readUnsignedByte();
        int hasFaceSkins = footer.readUnsignedByte();
        int hasFaceTextures = footer.readUnsignedByte();
        int hasVertexSkins = footer.readUnsignedByte();
        int hasAnimaya = footer.readUnsignedByte();
        int xDataLen = footer.readUnsignedShort();
        int yDataLen = footer.readUnsignedShort();
        int zDataLen = footer.readUnsignedShort();
        int faceIdxLen = footer.readUnsignedShort();
        int texIdxLen = footer.readUnsignedShort();
        int vSkinLen = footer.readUnsignedShort();

        int simpleTexCount = 0;
        int complexTexCount = 0;
        int complexType2Count = 0;
        if (texTriCount > 0) {
            ModelDataBuffer textures = new ModelDataBuffer(data);
            for (int i = 0; i < texTriCount; i++) {
                byte type = textures.readByte();
                if (type == 0) {
                    simpleTexCount++;
                } else if (type >= 1 && type <= 3) {
                    complexTexCount++;
                }
                if (type == 2) {
                    complexType2Count++;
                }
            }
        }

        int off = texTriCount;
        int vertexFlagsOff = off;
        off += vertexCount;
        if (hasFaceTypes == 1) {
            off += faceCount;
        }
        int faceCompressOff = off;
        off += faceCount;
        if (faceRenderPri == 255) {
            off += faceCount;
        }
        if (hasFaceSkins == 1) {
            off += faceCount;
        }
        int vertSkinOff = off;
        off += vSkinLen;
        if (hasFaceAlphas == 1) {
            off += faceCount;
        }
        int faceIdxOff = off;
        off += faceIdxLen;
        int faceTextureOff = -1;
        if (hasFaceTextures == 1) {
            faceTextureOff = off;
            off += faceCount * 2;
        }
        int texIdxOff = off;
        off += texIdxLen;
        int faceColorOff = off;
        off += faceCount * 2;
        int xOff = off;
        off += xDataLen;
        int yOff = off;
        off += yDataLen;
        int zOff = off;
        off += zDataLen;
        int simpleTexOff = off;
        off += simpleTexCount * 6;
        off += complexTexCount * 6;
        off += complexTexCount * 6;
        off += complexTexCount * 2;
        off += complexTexCount;
        off += complexTexCount * 2 + complexType2Count * 2;

        short[] faceTextures = readFaceTextures(data, faceTextureOff, faceCount);
        byte[] faceTextureCoords = readFaceTextureCoords(data, texIdxOff, faceCount, faceTextures);
        TextureMapping mapping = readTextureMapping(data, texTriCount, simpleTexOff, 0, simpleTexCount, complexTexCount, complexType2Count, true);
        return decodeCommon(data, vertexCount, faceCount, vertexFlagsOff, xOff, yOff, zOff, vertSkinOff, hasVertexSkins, hasAnimaya,
                faceColorOff, faceIdxOff, faceCompressOff, faceTextures, faceTextureCoords, mapping, faceTextureOff);
    }

    private static RsModelData decodeType2(byte[] data) {
        ModelDataBuffer footer = new ModelDataBuffer(data);
        footer.position(data.length - 23);
        int vertexCount = footer.readUnsignedShort();
        int faceCount = footer.readUnsignedShort();
        validateCounts(vertexCount, faceCount, data.length);
        int texTriCount = footer.readUnsignedByte();
        int hasFaceTypes = footer.readUnsignedByte();
        int faceRenderPri = footer.readUnsignedByte();
        int hasFaceAlphas = footer.readUnsignedByte();
        int hasFaceSkins = footer.readUnsignedByte();
        int hasVertexSkins = footer.readUnsignedByte();
        int hasAnimaya = footer.readUnsignedByte();
        int xDataLen = footer.readUnsignedShort();
        int yDataLen = footer.readUnsignedShort();
        int zDataLen = footer.readUnsignedShort();
        int faceIdxLen = footer.readUnsignedShort();
        int vSkinLen = footer.readUnsignedShort();

        int off = 0;
        int vertexFlagsOff = off;
        off += vertexCount;
        int faceCompressOff = off;
        off += faceCount;
        if (faceRenderPri == 255) {
            off += faceCount;
        }
        if (hasFaceSkins == 1) {
            off += faceCount;
        }
        if (hasFaceTypes == 1) {
            off += faceCount;
        }
        int vertSkinOff = off;
        off += vSkinLen;
        if (hasFaceAlphas == 1) {
            off += faceCount;
        }
        int faceIdxOff = off;
        off += faceIdxLen;
        int faceColorOff = off;
        off += faceCount * 2;
        int simpleTexOff = off;
        off += texTriCount * 6;
        int xOff = off;
        off += xDataLen;
        int yOff = off;
        off += yDataLen;
        int zOff = off;

        TextureMapping mapping = readTextureMapping(data, texTriCount, simpleTexOff, 0, texTriCount, 0, 0, null);
        return decodeCommon(data, vertexCount, faceCount, vertexFlagsOff, xOff, yOff, zOff, vertSkinOff, hasVertexSkins, hasAnimaya,
                faceColorOff, faceIdxOff, faceCompressOff, null, null, mapping, -1);
    }

    private static RsModelData decodeFormat1(byte[] data) {
        ModelDataBuffer footer = new ModelDataBuffer(data);
        footer.position(data.length - 23);
        int vertexCount = footer.readUnsignedShort();
        int faceCount = footer.readUnsignedShort();
        validateCounts(vertexCount, faceCount, data.length);
        int texTriCount = footer.readUnsignedByte();
        int flags = footer.readUnsignedByte();
        boolean hasFaceTypes = (flags & 0x1) == 1;
        boolean extended = (flags & 0x8) == 8;
        int version = 12;
        if (extended) {
            footer.position(footer.position() - 7);
            version = footer.readUnsignedByte();
            footer.position(footer.position() + 6);
        }
        int faceRenderPri = footer.readUnsignedByte();
        int hasFaceAlphas = footer.readUnsignedByte();
        int hasFaceSkins = footer.readUnsignedByte();
        int hasFaceTextures = footer.readUnsignedByte();
        int hasVertexSkins = footer.readUnsignedByte();
        int xDataLen = footer.readUnsignedShort();
        int yDataLen = footer.readUnsignedShort();
        int zDataLen = footer.readUnsignedShort();
        int faceIdxLen = footer.readUnsignedShort();
        int texDataLen = footer.readUnsignedShort();

        int simpleTexCount = 0;
        int complexTexCount = 0;
        int complexType2Count = 0;
        if (texTriCount > 0) {
            ModelDataBuffer textureTypes = new ModelDataBuffer(data);
            for (int i = 0; i < texTriCount; i++) {
                byte type = textureTypes.readByte();
                if (type == 0) {
                    simpleTexCount++;
                }
                if (type >= 1 && type <= 3) {
                    complexTexCount++;
                }
                if (type == 2) {
                    complexType2Count++;
                }
            }
        }

        int off = texTriCount;
        int vertexFlagsOff = off;
        off += vertexCount;
        if (hasFaceTypes) {
            off += faceCount;
        }
        int faceCompressOff = off;
        off += faceCount;
        if (faceRenderPri == 255) {
            off += faceCount;
        }
        if (hasFaceSkins == 1) {
            off += faceCount;
        }
        int vertSkinOff = off;
        if (hasVertexSkins == 1) {
            off += vertexCount;
        }
        if (hasFaceAlphas == 1) {
            off += faceCount;
        }
        int faceIdxOff = off;
        off += faceIdxLen;
        int faceTextureOff = -1;
        if (hasFaceTextures == 1) {
            faceTextureOff = off;
            off += faceCount * 2;
        }
        int texIdxOff = off;
        off += texDataLen;
        int faceColorOff = off;
        off += faceCount * 2;
        int xOff = off;
        off += xDataLen;
        int yOff = off;
        off += yDataLen;
        int zOff = off;
        off += zDataLen;
        int simpleTexOff = off;
        off += simpleTexCount * 6;
        off += complexTexCount * 6;
        off += version < 15 ? complexTexCount * 6 : complexTexCount * 9;
        off += complexTexCount;
        off += complexTexCount;
        off += complexTexCount + complexType2Count * 2;

        short[] faceTextures = readFaceTextures(data, faceTextureOff, faceCount);
        byte[] faceTextureCoords = readFaceTextureCoords(data, texIdxOff, faceCount, faceTextures);
        TextureMapping mapping = readTextureMapping(data, texTriCount, simpleTexOff, version, simpleTexCount, complexTexCount, complexType2Count, true);
        return decodeCommon(data, vertexCount, faceCount, vertexFlagsOff, xOff, yOff, zOff, vertSkinOff, hasVertexSkins, 0,
                faceColorOff, faceIdxOff, faceCompressOff, faceTextures, faceTextureCoords, mapping, faceTextureOff);
    }

    private static RsModelData decodeLegacy(byte[] data) {
        ModelDataBuffer footer = new ModelDataBuffer(data);
        footer.position(data.length - 18);
        int vertexCount = footer.readUnsignedShort();
        int faceCount = footer.readUnsignedShort();
        validateCounts(vertexCount, faceCount, data.length);
        int texTriCount = footer.readUnsignedByte();
        int hasFaceTypes = footer.readUnsignedByte();
        int faceRenderPri = footer.readUnsignedByte();
        int hasFaceAlphas = footer.readUnsignedByte();
        int hasFaceSkins = footer.readUnsignedByte();
        int hasVertexSkins = footer.readUnsignedByte();
        int xDataLen = footer.readUnsignedShort();
        int yDataLen = footer.readUnsignedShort();
        int zDataLen = footer.readUnsignedShort();
        int faceIdxLen = footer.readUnsignedShort();

        int off = 0;
        int vertexFlagsOff = off;
        off += vertexCount;
        int faceCompressOff = off;
        off += faceCount;
        if (faceRenderPri == 255) {
            off += faceCount;
        }
        if (hasFaceSkins == 1) {
            off += faceCount;
        }
        if (hasFaceTypes == 1) {
            off += faceCount;
        }
        int vertSkinOff = off;
        if (hasVertexSkins == 1) {
            off += vertexCount;
        }
        if (hasFaceAlphas == 1) {
            off += faceCount;
        }
        int faceIdxOff = off;
        off += faceIdxLen;
        int faceColorOff = off;
        off += faceCount * 2;
        int simpleTexOff = off;
        off += texTriCount * 6;
        int xOff = off;
        off += xDataLen;
        int yOff = off;
        off += yDataLen;
        int zOff = off;

        TextureMapping mapping = readTextureMapping(data, texTriCount, simpleTexOff, 0, texTriCount, 0, 0, null);
        return decodeCommon(data, vertexCount, faceCount, vertexFlagsOff, xOff, yOff, zOff, vertSkinOff, hasVertexSkins, 0,
                faceColorOff, faceIdxOff, faceCompressOff, null, null, mapping, -1);
    }

    private static RsModelData decodeCommon(
            byte[] data,
            int vertexCount,
            int faceCount,
            int vertexFlagsOff,
            int xOff,
            int yOff,
            int zOff,
            int vertSkinOff,
            int hasVertexSkins,
            int hasAnimaya,
            int faceColorOff,
            int faceIdxOff,
            int faceCompressOff,
            short[] faceTextures,
            byte[] faceTextureCoords,
            TextureMapping textureMapping,
            int faceTextureOffset
    ) {
        validateOffset(vertexFlagsOff, data.length, "vertexFlagsOff");
        validateOffset(xOff, data.length, "xOff");
        validateOffset(yOff, data.length, "yOff");
        validateOffset(zOff, data.length, "zOff");
        validateOffset(vertSkinOff, data.length, "vertSkinOff");
        validateOffset(faceColorOff, data.length, "faceColorOff");
        validateOffset(faceIdxOff, data.length, "faceIdxOff");
        validateOffset(faceCompressOff, data.length, "faceCompressOff");
        int[] verticesX = new int[vertexCount];
        int[] verticesY = new int[vertexCount];
        int[] verticesZ = new int[vertexCount];
        short[] faceA = new short[faceCount];
        short[] faceB = new short[faceCount];
        short[] faceC = new short[faceCount];
        short[] faceColors = new short[faceCount];

        ModelDataBuffer flags = new ModelDataBuffer(data);
        ModelDataBuffer x = new ModelDataBuffer(data);
        ModelDataBuffer y = new ModelDataBuffer(data);
        ModelDataBuffer z = new ModelDataBuffer(data);
        ModelDataBuffer vertexSkins = new ModelDataBuffer(data);
        flags.position(vertexFlagsOff);
        x.position(xOff);
        y.position(yOff);
        z.position(zOff);
        vertexSkins.position(vertSkinOff);

        int vx = 0;
        int vy = 0;
        int vz = 0;
        for (int i = 0; i < vertexCount; i++) {
            int vertexFlags = flags.readUnsignedByte();
            int dx = (vertexFlags & 0x1) != 0 ? x.readSmart() : 0;
            int dy = (vertexFlags & 0x2) != 0 ? y.readSmart() : 0;
            int dz = (vertexFlags & 0x4) != 0 ? z.readSmart() : 0;
            verticesX[i] = vx += dx;
            verticesY[i] = vy += dy;
            verticesZ[i] = vz += dz;
            if (hasVertexSkins == 1) {
                vertexSkins.readUnsignedByte();
            }
        }
        if (hasAnimaya == 1) {
            for (int i = 0; i < vertexCount; i++) {
                int count = vertexSkins.readUnsignedByte();
                vertexSkins.position(vertexSkins.position() + count * 2);
            }
        }

        ModelDataBuffer faceColor = new ModelDataBuffer(data);
        faceColor.position(faceColorOff);
        for (int i = 0; i < faceCount; i++) {
            faceColors[i] = (short) faceColor.readUnsignedShort();
        }

        ModelDataBuffer faceIdx = new ModelDataBuffer(data);
        ModelDataBuffer faceCompress = new ModelDataBuffer(data);
        faceIdx.position(faceIdxOff);
        faceCompress.position(faceCompressOff);
        readFaceIndices(faceIdx, faceCompress, faceA, faceB, faceC);
        return new RsModelData(
                verticesX,
                verticesY,
                verticesZ,
                faceA,
                faceB,
                faceC,
                faceColors,
                faceTextures,
                faceTextureCoords,
                textureMapping.types,
                textureMapping.triangleA,
                textureMapping.triangleB,
                textureMapping.triangleC,
                textureMapping.scaleX,
                textureMapping.scaleY,
                textureMapping.scaleZ,
                textureMapping.direction,
                textureMapping.speed,
                textureMapping.translation,
                textureMapping.uTrans,
                textureMapping.vTrans,
                data.clone(),
                faceTextureOffset
        );
    }

    private static short[] readFaceTextures(byte[] data, int offset, int faceCount) {
        if (offset < 0) {
            return null;
        }
        validateOffset(offset, data.length, "faceTextureOff");
        ModelDataBuffer buffer = new ModelDataBuffer(data);
        buffer.position(offset);
        short[] faceTextures = new short[faceCount];
        for (int i = 0; i < faceCount; i++) {
            int value = buffer.readUnsignedShort();
            faceTextures[i] = (short) (value == 65535 ? -1 : value);
        }
        return faceTextures;
    }

    private static byte[] readFaceTextureCoords(byte[] data, int offset, int faceCount, short[] faceTextures) {
        if (offset < 0 || faceTextures == null) {
            return null;
        }
        validateOffset(offset, data.length, "texIdxOff");
        ModelDataBuffer buffer = new ModelDataBuffer(data);
        buffer.position(offset);
        byte[] coords = new byte[faceCount];
        boolean any = false;
        for (int i = 0; i < faceCount; i++) {
            if (faceTextures[i] != -1) {
                coords[i] = (byte) (buffer.readUnsignedByte() - 1);
                any |= coords[i] >= 0;
            } else {
                coords[i] = -1;
            }
        }
        return any ? coords : null;
    }

    private static TextureMapping readTextureMapping(byte[] data, int texTriCount, Integer simpleTexOff, Integer version,
                                                     Integer simpleTexCount, Integer complexTexCount, Integer complexType2Count,
                                                     Boolean hasComplexLayout) {
        if (texTriCount <= 0 || simpleTexOff == null || simpleTexOff < 0) {
            return TextureMapping.empty();
        }
        byte[] types = new byte[texTriCount];
        if (hasComplexLayout != null) {
            ModelDataBuffer typeBuffer = new ModelDataBuffer(data);
            typeBuffer.position(0);
            for (int i = 0; i < texTriCount; i++) {
                types[i] = typeBuffer.readByte();
            }
        } else {
            java.util.Arrays.fill(types, (byte) 0);
        }

        short[] triangleA = new short[texTriCount];
        short[] triangleB = new short[texTriCount];
        short[] triangleC = new short[texTriCount];
        int[] scaleX = new int[texTriCount];
        int[] scaleY = new int[texTriCount];
        int[] scaleZ = new int[texTriCount];
        byte[] direction = new byte[texTriCount];
        byte[] speed = new byte[texTriCount];
        int[] translation = new int[texTriCount];
        int[] uTrans = new int[texTriCount];
        int[] vTrans = new int[texTriCount];
        ModelDataBuffer simple = new ModelDataBuffer(data);
        simple.position(simpleTexOff);

        ModelDataBuffer complex = null;
        ModelDataBuffer complexData = null;
        ModelDataBuffer complexScale = null;
        ModelDataBuffer complexRot = null;
        ModelDataBuffer complexMore = null;

        if (Boolean.TRUE.equals(hasComplexLayout) && simpleTexCount != null && complexTexCount != null && complexType2Count != null && version != null) {
            int complexTexOff = simpleTexOff + simpleTexCount * 6;
            int complexTexDataOff = complexTexOff + complexTexCount * 6;
            int complexTexScaleOff = complexTexDataOff + complexTexCount * (version < 15 ? 6 : 9);
            int complexTexRotOff = complexTexScaleOff + complexTexCount * 2;
            int complexTexMoreOff = complexTexRotOff + complexTexCount;
            complex = new ModelDataBuffer(data);
            complexData = new ModelDataBuffer(data);
            complexScale = new ModelDataBuffer(data);
            complexRot = new ModelDataBuffer(data);
            complexMore = new ModelDataBuffer(data);
            complex.position(complexTexOff);
            complexData.position(complexTexDataOff);
            complexScale.position(complexTexScaleOff);
            complexRot.position(complexTexRotOff);
            complexMore.position(complexTexMoreOff);
        }

        for (int i = 0; i < texTriCount; i++) {
            int type = types[i] & 0xFF;
            ModelDataBuffer source = type == 0 ? simple : complex;
            if (source == null) {
                continue;
            }
            triangleA[i] = (short) source.readUnsignedShort();
            triangleB[i] = (short) source.readUnsignedShort();
            triangleC[i] = (short) source.readUnsignedShort();
            if (type >= 1 && complexData != null) {
                if (version < 15) {
                    scaleX[i] = complexData.readUnsignedShort();
                    scaleY[i] = version < 14 ? complexData.readUnsignedShort() : complexData.read24BitUnsignedInteger();
                    scaleZ[i] = complexData.readUnsignedShort();
                } else {
                    scaleX[i] = complexData.read24BitUnsignedInteger();
                    scaleY[i] = complexData.read24BitUnsignedInteger();
                    scaleZ[i] = complexData.read24BitUnsignedInteger();
                }
                direction[i] = complexScale.readByte();
                speed[i] = complexRot.readByte();
                translation[i] = complexMore.readByte();
                if (type == 2) {
                    uTrans[i] = complexMore.readByte();
                    vTrans[i] = complexMore.readByte();
                }
            }
        }
        return new TextureMapping(types, triangleA, triangleB, triangleC, scaleX, scaleY, scaleZ, direction, speed, translation, uTrans, vTrans);
    }

    private static void readFaceIndices(ModelDataBuffer faceIdx, ModelDataBuffer faceCompress, short[] faceA, short[] faceB, short[] faceC) {
        short a = 0;
        short b = 0;
        short c = 0;
        int last = 0;
        for (int i = 0; i < faceA.length; i++) {
            int type = faceCompress.readUnsignedByte();
            if (type == 1) {
                a = (short) (faceIdx.readSmart() + last);
                last = a;
                b = (short) (faceIdx.readSmart() + last);
                last = b;
                c = (short) (faceIdx.readSmart() + last);
                last = c;
            } else if (type == 2) {
                b = c;
                c = (short) (faceIdx.readSmart() + last);
                last = c;
            } else if (type == 3) {
                a = c;
                c = (short) (faceIdx.readSmart() + last);
                last = c;
            } else if (type == 4) {
                short swap = a;
                a = b;
                b = swap;
                c = (short) (faceIdx.readSmart() + last);
                last = c;
            }
            faceA[i] = a;
            faceB[i] = b;
            faceC[i] = c;
        }
    }

    private static void validateCounts(int vertexCount, int faceCount, int length) {
        if (vertexCount < 0 || faceCount < 0 || vertexCount > MAX_VERTEX_COUNT || faceCount > MAX_FACE_COUNT) {
            throw new IllegalStateException("unsupported model counts v=" + vertexCount + " f=" + faceCount + " len=" + length);
        }
    }

    private static void validateOffset(int offset, int length, String label) {
        if (offset < 0 || offset > length) {
            throw new IllegalStateException("bad model offset " + label + "=" + offset + " len=" + length);
        }
    }

    RsModelData recolored(int[] originalColors, int[] modifiedColors) {
        short[] recolored = faceColors.clone();
        if (originalColors != null && modifiedColors != null) {
            int count = Math.min(originalColors.length, modifiedColors.length);
            for (int i = 0; i < recolored.length; i++) {
                int color = recolored[i] & 0xFFFF;
                for (int j = 0; j < count; j++) {
                    if (color == (originalColors[j] & 0xFFFF)) {
                        recolored[i] = (short) modifiedColors[j];
                        break;
                    }
                }
            }
        }
        return new RsModelData(verticesX.clone(), verticesY.clone(), verticesZ.clone(), faceA.clone(), faceB.clone(), faceC.clone(), recolored,
                faceTextures == null ? null : faceTextures.clone(),
                faceTextureCoords == null ? null : faceTextureCoords.clone(),
                textureRenderTypes == null ? null : textureRenderTypes.clone(),
                textureTriangleA == null ? null : textureTriangleA.clone(),
                textureTriangleB == null ? null : textureTriangleB.clone(),
                textureTriangleC == null ? null : textureTriangleC.clone(),
                textureScaleX == null ? null : textureScaleX.clone(),
                textureScaleY == null ? null : textureScaleY.clone(),
                textureScaleZ == null ? null : textureScaleZ.clone(),
                textureDirection == null ? null : textureDirection.clone(),
                textureSpeed == null ? null : textureSpeed.clone(),
                textureTranslation == null ? null : textureTranslation.clone(),
                textureUTrans == null ? null : textureUTrans.clone(),
                textureVTrans == null ? null : textureVTrans.clone(),
                rawData, faceTextureOffset);
    }

    RsModelData withFaceTextures(short[] updatedFaceTextures) {
        return new RsModelData(verticesX.clone(), verticesY.clone(), verticesZ.clone(), faceA.clone(), faceB.clone(), faceC.clone(), faceColors.clone(),
                updatedFaceTextures == null ? null : updatedFaceTextures.clone(),
                faceTextureCoords == null ? null : faceTextureCoords.clone(),
                textureRenderTypes == null ? null : textureRenderTypes.clone(),
                textureTriangleA == null ? null : textureTriangleA.clone(),
                textureTriangleB == null ? null : textureTriangleB.clone(),
                textureTriangleC == null ? null : textureTriangleC.clone(),
                textureScaleX == null ? null : textureScaleX.clone(),
                textureScaleY == null ? null : textureScaleY.clone(),
                textureScaleZ == null ? null : textureScaleZ.clone(),
                textureDirection == null ? null : textureDirection.clone(),
                textureSpeed == null ? null : textureSpeed.clone(),
                textureTranslation == null ? null : textureTranslation.clone(),
                textureUTrans == null ? null : textureUTrans.clone(),
                textureVTrans == null ? null : textureVTrans.clone(),
                rawData, faceTextureOffset);
    }

    byte[] encodeFaceTexturePatch(short[] updatedFaceTextures) {
        if (rawData == null || faceTextureOffset < 0 || faceTextures == null || updatedFaceTextures == null || updatedFaceTextures.length != faceTextures.length) {
            return null;
        }
        byte[] encoded = rawData.clone();
        int offset = faceTextureOffset;
        for (short value : updatedFaceTextures) {
            int textureId = value < 0 ? 65535 : value & 0xFFFF;
            encoded[offset++] = (byte) (textureId >> 8);
            encoded[offset++] = (byte) textureId;
        }
        return encoded;
    }

    RsModelData rotated(int rotationX, int rotationY, int rotationZ) {
        int[] x = verticesX.clone();
        int[] y = verticesY.clone();
        int[] z = verticesZ.clone();
        rotateAxis(x, z, rotationY);
        rotateAxis(y, z, -rotationX);
        rotateAxis(x, y, rotationZ);
        return new RsModelData(x, y, z, faceA.clone(), faceB.clone(), faceC.clone(), faceColors.clone(),
                faceTextures == null ? null : faceTextures.clone(),
                faceTextureCoords == null ? null : faceTextureCoords.clone(),
                textureRenderTypes == null ? null : textureRenderTypes.clone(),
                textureTriangleA == null ? null : textureTriangleA.clone(),
                textureTriangleB == null ? null : textureTriangleB.clone(),
                textureTriangleC == null ? null : textureTriangleC.clone(),
                textureScaleX == null ? null : textureScaleX.clone(),
                textureScaleY == null ? null : textureScaleY.clone(),
                textureScaleZ == null ? null : textureScaleZ.clone(),
                textureDirection == null ? null : textureDirection.clone(),
                textureSpeed == null ? null : textureSpeed.clone(),
                textureTranslation == null ? null : textureTranslation.clone(),
                textureUTrans == null ? null : textureUTrans.clone(),
                textureVTrans == null ? null : textureVTrans.clone(),
                rawData, faceTextureOffset);
    }

    RsModelData translated(int dx, int dy, int dz) {
        int[] x = verticesX.clone();
        int[] y = verticesY.clone();
        int[] z = verticesZ.clone();
        for (int i = 0; i < x.length; i++) {
            x[i] += dx;
            y[i] += dy;
            z[i] += dz;
        }
        return new RsModelData(x, y, z, faceA.clone(), faceB.clone(), faceC.clone(), faceColors.clone(),
                faceTextures == null ? null : faceTextures.clone(),
                faceTextureCoords == null ? null : faceTextureCoords.clone(),
                textureRenderTypes == null ? null : textureRenderTypes.clone(),
                textureTriangleA == null ? null : textureTriangleA.clone(),
                textureTriangleB == null ? null : textureTriangleB.clone(),
                textureTriangleC == null ? null : textureTriangleC.clone(),
                textureScaleX == null ? null : textureScaleX.clone(),
                textureScaleY == null ? null : textureScaleY.clone(),
                textureScaleZ == null ? null : textureScaleZ.clone(),
                textureDirection == null ? null : textureDirection.clone(),
                textureSpeed == null ? null : textureSpeed.clone(),
                textureTranslation == null ? null : textureTranslation.clone(),
                textureUTrans == null ? null : textureUTrans.clone(),
                textureVTrans == null ? null : textureVTrans.clone(),
                rawData, faceTextureOffset);
    }

    private static void rotateAxis(int[] a, int[] b, int clientAngle) {
        if (clientAngle == 0) {
            return;
        }
        double radians = Math.toRadians((clientAngle & 2047) * 360.0 / 2048.0);
        double sin = Math.sin(radians);
        double cos = Math.cos(radians);
        for (int i = 0; i < a.length; i++) {
            int newA = (int) Math.round(a[i] * cos - b[i] * sin);
            int newB = (int) Math.round(a[i] * sin + b[i] * cos);
            a[i] = newA;
            b[i] = newB;
        }
    }

    static RsModelData combine(RsModelData... models) {
        int vertexCount = 0;
        int faceCount = 0;
        int active = 0;
        boolean hasAnyTextures = false;
        for (RsModelData model : models) {
            if (model == null) {
                continue;
            }
            vertexCount += model.verticesX.length;
            faceCount += model.faceA.length;
            hasAnyTextures |= model.faceTextures != null;
            active++;
        }
        if (active == 0) {
            return null;
        }
        if (active == 1) {
            for (RsModelData model : models) {
                if (model != null) {
                    return model;
                }
            }
        }
        int[] x = new int[vertexCount];
        int[] y = new int[vertexCount];
        int[] z = new int[vertexCount];
        short[] a = new short[faceCount];
        short[] b = new short[faceCount];
        short[] c = new short[faceCount];
        short[] colors = new short[faceCount];
        short[] textures = hasAnyTextures ? new short[faceCount] : null;
        byte[] textureCoords = hasAnyTextures ? new byte[faceCount] : null;
        if (textures != null) {
            java.util.Arrays.fill(textures, (short) -1);
        }
        if (textureCoords != null) {
            java.util.Arrays.fill(textureCoords, (byte) -1);
        }
        int vertexOffset = 0;
        int faceOffset = 0;
        for (RsModelData model : models) {
            if (model == null) {
                continue;
            }
            System.arraycopy(model.verticesX, 0, x, vertexOffset, model.verticesX.length);
            System.arraycopy(model.verticesY, 0, y, vertexOffset, model.verticesY.length);
            System.arraycopy(model.verticesZ, 0, z, vertexOffset, model.verticesZ.length);
            for (int i = 0; i < model.faceA.length; i++) {
                a[faceOffset + i] = (short) (vertexOffset + (model.faceA[i] & 0xFFFF));
                b[faceOffset + i] = (short) (vertexOffset + (model.faceB[i] & 0xFFFF));
                c[faceOffset + i] = (short) (vertexOffset + (model.faceC[i] & 0xFFFF));
                colors[faceOffset + i] = model.faceColors[i];
                if (textures != null && model.faceTextures != null) {
                    textures[faceOffset + i] = model.faceTextures[i];
                }
                if (textureCoords != null && model.faceTextureCoords != null) {
                    textureCoords[faceOffset + i] = model.faceTextureCoords[i];
                }
            }
            vertexOffset += model.verticesX.length;
            faceOffset += model.faceA.length;
        }
        return new RsModelData(x, y, z, a, b, c, colors, textures, textureCoords,
                null, null, null, null,
                null, null, null,
                null, null, null, null, null,
                null, -1);
    }

    int[] verticesX() { return verticesX; }
    int[] verticesY() { return verticesY; }
    int[] verticesZ() { return verticesZ; }
    short[] faceA() { return faceA; }
    short[] faceB() { return faceB; }
    short[] faceC() { return faceC; }
    short[] faceColors() { return faceColors; }
    short[] faceTextures() { return faceTextures; }
    byte[] faceTextureCoords() { return faceTextureCoords; }
    byte[] textureRenderTypes() { return textureRenderTypes; }
    short[] textureTriangleA() { return textureTriangleA; }
    short[] textureTriangleB() { return textureTriangleB; }
    short[] textureTriangleC() { return textureTriangleC; }
    int[] textureScaleX() { return textureScaleX; }
    int[] textureScaleY() { return textureScaleY; }
    int[] textureScaleZ() { return textureScaleZ; }
    byte[] textureDirection() { return textureDirection; }
    byte[] textureSpeed() { return textureSpeed; }
    int[] textureTranslation() { return textureTranslation; }
    int[] textureUTrans() { return textureUTrans; }
    int[] textureVTrans() { return textureVTrans; }

    private record TextureMapping(byte[] types, short[] triangleA, short[] triangleB, short[] triangleC,
                                  int[] scaleX, int[] scaleY, int[] scaleZ,
                                  byte[] direction, byte[] speed, int[] translation, int[] uTrans, int[] vTrans) {
        private static TextureMapping empty() {
            return new TextureMapping(null, null, null, null, null, null, null, null, null, null, null, null);
        }
    }
}
