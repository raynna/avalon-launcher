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

    private RsModelData(int[] verticesX, int[] verticesY, int[] verticesZ, short[] faceA, short[] faceB, short[] faceC, short[] faceColors) {
        this.verticesX = verticesX;
        this.verticesY = verticesY;
        this.verticesZ = verticesZ;
        this.faceA = faceA;
        this.faceB = faceB;
        this.faceC = faceC;
        this.faceColors = faceColors;
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
        int faceTypesOff = off;
        if (hasFaceTypes == 1) {
            off += faceCount;
        }
        int faceCompressOff = off;
        off += faceCount;
        int facePriOff = off;
        if (faceRenderPri == 255) {
            off += faceCount;
        }
        int faceSkinOff = off;
        if (hasFaceSkins == 1) {
            off += faceCount;
        }
        int vertSkinOff = off;
        off += vSkinLen;
        int faceAlphaOff = off;
        if (hasFaceAlphas == 1) {
            off += faceCount;
        }
        int faceIdxOff = off;
        off += faceIdxLen;
        int faceTexMapOff = off;
        if (hasFaceTextures == 1) {
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
        off += simpleTexCount * 6;
        off += complexTexCount * 6;
        off += complexTexCount * 6;
        off += complexTexCount * 2;
        off += complexTexCount;
        off += complexTexCount * 2 + complexType2Count * 2;

        return decodeCommon(data, vertexCount, faceCount, vertexFlagsOff, xOff, yOff, zOff, vertSkinOff, hasVertexSkins, hasAnimaya, faceColorOff, faceIdxOff, faceCompressOff);
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
        int facePriOff = off;
        if (faceRenderPri == 255) {
            off += faceCount;
        }
        int faceSkinOff = off;
        if (hasFaceSkins == 1) {
            off += faceCount;
        }
        int faceTypeOff = off;
        if (hasFaceTypes == 1) {
            off += faceCount;
        }
        int vertSkinOff = off;
        off += vSkinLen;
        int faceAlphaOff = off;
        if (hasFaceAlphas == 1) {
            off += faceCount;
        }
        int faceIdxOff = off;
        off += faceIdxLen;
        int faceColorOff = off;
        off += faceCount * 2;
        off += texTriCount * 6;
        int xOff = off;
        off += xDataLen;
        int yOff = off;
        off += yDataLen;
        int zOff = off;

        return decodeCommon(data, vertexCount, faceCount, vertexFlagsOff, xOff, yOff, zOff, vertSkinOff, hasVertexSkins, hasAnimaya, faceColorOff, faceIdxOff, faceCompressOff);
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
        boolean hasFaceLabels = (flags & 0x2) == 2;
        boolean hasParticleEffects = (flags & 0x4) == 4;
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
        int faceTypeOff = off;
        if (hasFaceTypes) {
            off += faceCount;
        }
        int faceCompressOff = off;
        off += faceCount;
        int facePriOff = off;
        if (faceRenderPri == 255) {
            off += faceCount;
        }
        int faceSkinOff = off;
        if (hasFaceSkins == 1) {
            off += faceCount;
        }
        int vertSkinOff = off;
        if (hasVertexSkins == 1) {
            off += vertexCount;
        }
        int faceAlphaOff = off;
        if (hasFaceAlphas == 1) {
            off += faceCount;
        }
        int faceIdxOff = off;
        off += faceIdxLen;
        int faceTextureOff = off;
        if (hasFaceTextures == 1) {
            off += faceCount * 2;
        }
        int textureCoordOff = off;
        off += texDataLen;
        int faceColorOff = off;
        off += faceCount * 2;
        int xOff = off;
        off += xDataLen;
        int yOff = off;
        off += yDataLen;
        int zOff = off;
        off += zDataLen;
        int simpleTextureOff = off;
        off += simpleTexCount * 6;
        int complexTextureOff = off;
        off += complexTexCount * 6;
        int complexTextureScaleOff = off;
        if (version < 15) {
            off += complexTexCount * 6;
        } else {
            off += complexTexCount * 9;
        }
        int complexTextureRotationOff = off;
        off += complexTexCount;
        int complexTextureDirectionOff = off;
        off += complexTexCount;
        int complexTextureTranslationOff = off;
        off += complexTexCount + complexType2Count * 2;
        validateOffset(faceTextureOff, data.length, "faceTextureOff");
        validateOffset(textureCoordOff, data.length, "textureCoordOff");
        validateOffset(simpleTextureOff, data.length, "simpleTextureOff");
        validateOffset(complexTextureOff, data.length, "complexTextureOff");
        validateOffset(complexTextureScaleOff, data.length, "complexTextureScaleOff");
        validateOffset(complexTextureRotationOff, data.length, "complexTextureRotationOff");
        validateOffset(complexTextureDirectionOff, data.length, "complexTextureDirectionOff");
        validateOffset(complexTextureTranslationOff, data.length, "complexTextureTranslationOff");
        return decodeCommon(data, vertexCount, faceCount, vertexFlagsOff, xOff, yOff, zOff, vertSkinOff, hasVertexSkins, 0, faceColorOff, faceIdxOff, faceCompressOff);
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
        int facePriOff = off;
        if (faceRenderPri == 255) {
            off += faceCount;
        }
        int faceSkinOff = off;
        if (hasFaceSkins == 1) {
            off += faceCount;
        }
        int faceTypeOff = off;
        if (hasFaceTypes == 1) {
            off += faceCount;
        }
        int vertSkinOff = off;
        if (hasVertexSkins == 1) {
            off += vertexCount;
        }
        int faceAlphaOff = off;
        if (hasFaceAlphas == 1) {
            off += faceCount;
        }
        int faceIdxOff = off;
        off += faceIdxLen;
        int faceColorOff = off;
        off += faceCount * 2;
        off += texTriCount * 6;
        int xOff = off;
        off += xDataLen;
        int yOff = off;
        off += yDataLen;
        int zOff = off;

        return decodeCommon(data, vertexCount, faceCount, vertexFlagsOff, xOff, yOff, zOff, vertSkinOff, hasVertexSkins, 0, faceColorOff, faceIdxOff, faceCompressOff);
    }

    private static RsModelData decodeCommon(byte[] data, int vertexCount, int faceCount, int vertexFlagsOff, int xOff, int yOff, int zOff, int vertSkinOff, int hasVertexSkins, int hasAnimaya, int faceColorOff, int faceIdxOff, int faceCompressOff) {
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
        return new RsModelData(verticesX, verticesY, verticesZ, faceA, faceB, faceC, faceColors);
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
        return new RsModelData(verticesX.clone(), verticesY.clone(), verticesZ.clone(), faceA.clone(), faceB.clone(), faceC.clone(), recolored);
    }

    RsModelData rotated(int rotationX, int rotationY, int rotationZ) {
        int[] x = verticesX.clone();
        int[] y = verticesY.clone();
        int[] z = verticesZ.clone();
        rotateAxis(x, z, rotationY);
        rotateAxis(y, z, -rotationX);
        rotateAxis(x, y, rotationZ);
        return new RsModelData(x, y, z, faceA.clone(), faceB.clone(), faceC.clone(), faceColors.clone());
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
        return new RsModelData(x, y, z, faceA.clone(), faceB.clone(), faceC.clone(), faceColors.clone());
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
        for (RsModelData model : models) {
            if (model == null) {
                continue;
            }
            vertexCount += model.verticesX.length;
            faceCount += model.faceA.length;
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
            }
            vertexOffset += model.verticesX.length;
            faceOffset += model.faceA.length;
        }
        return new RsModelData(x, y, z, a, b, c, colors);
    }

    int[] verticesX() { return verticesX; }
    int[] verticesY() { return verticesY; }
    int[] verticesZ() { return verticesZ; }
    short[] faceA() { return faceA; }
    short[] faceB() { return faceB; }
    short[] faceC() { return faceC; }
    short[] faceColors() { return faceColors; }
}

