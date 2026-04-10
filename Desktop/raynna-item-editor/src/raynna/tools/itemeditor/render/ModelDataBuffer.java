package raynna.tools.itemeditor.render;

final class ModelDataBuffer {
    private final byte[] data;
    private int position;

    ModelDataBuffer(byte[] data) {
        this.data = data;
    }

    int position() {
        return position;
    }

    void position(int position) {
        this.position = position;
    }

    int readUnsignedByte() {
        ensureAvailable(1);
        return data[position++] & 0xFF;
    }

    byte readByte() {
        ensureAvailable(1);
        return data[position++];
    }

    int readUnsignedShort() {
        ensureAvailable(2);
        int value = ((data[position] & 0xFF) << 8) | (data[position + 1] & 0xFF);
        position += 2;
        return value;
    }

    int read24BitUnsignedInteger() {
        ensureAvailable(3);
        int value = ((data[position] & 0xFF) << 16) | ((data[position + 1] & 0xFF) << 8) | (data[position + 2] & 0xFF);
        position += 3;
        return value;
    }

    int readSmart() {
        ensureAvailable(1);
        int peek = data[position] & 0xFF;
        if (peek < 128) {
            return readUnsignedByte() - 64;
        }
        return readUnsignedShort() - 49152;
    }

    private void ensureAvailable(int count) {
        if (position < 0 || position + count > data.length) {
            throw new IllegalStateException("Model buffer underflow at " + position + " requesting " + count + " of " + data.length);
        }
    }
}
