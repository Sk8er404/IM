package org.com.code.im.utils.serializer;

import java.nio.ByteBuffer;

public class VectorSerializer {
    public static byte[] serialize(float[] vector) {
        // 实现将float[]数组转换为byte[]的逻辑

        //在Java中，一个float类型占用4个字节（32位），所以如果有一个长度为n的float数组，它在内存中实际占用的字节数是 n * 4。
        ByteBuffer byteBuffer = ByteBuffer.allocate(vector.length * 4);
        for (float f : vector) {
            byteBuffer.putFloat(f);
        }
        return byteBuffer.array();
    }

    public static float[] deserialize(byte[] bytes) {
        // 实现将byte[]转换为float[]的逻辑
        ByteBuffer byteBuffer = ByteBuffer.wrap(bytes);
        float[] vector = new float[bytes.length / 4];
        for (int i = 0; i < vector.length; i++) {
            vector[i] = byteBuffer.getFloat();
        }
        return vector;
    }
}
