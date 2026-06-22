package com.example.BackendArchitectureLab.Util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AudioProcessUtilTest {

    @Mock
    private MultipartFile mockFile;

    private final AudioProcessUtil util = new AudioProcessUtil();

    private byte[] createMinimalWav(short... samples) {
        int numSamples = samples.length;
        int bytesPerSample = 2;
        int dataSize = numSamples * bytesPerSample;
        int fileSize = 44 + dataSize;

        ByteBuffer buf = ByteBuffer.allocate(fileSize);
        buf.order(ByteOrder.LITTLE_ENDIAN);

        buf.put((byte) 'R').put((byte) 'I').put((byte) 'F').put((byte) 'F');
        buf.putInt(fileSize - 8);
        buf.put((byte) 'W').put((byte) 'A').put((byte) 'V').put((byte) 'E');
        buf.put((byte) 'f').put((byte) 'm').put((byte) 't').put((byte) ' ');
        buf.putInt(16);
        buf.putShort((short) 1);
        buf.putShort((short) 1);
        buf.putInt(16000);
        buf.putInt(16000 * bytesPerSample);
        buf.putShort((short) bytesPerSample);
        buf.putShort((short) (bytesPerSample * 8));
        buf.put((byte) 'd').put((byte) 'a').put((byte) 't').put((byte) 'a');
        buf.putInt(dataSize);
        for (short s : samples) {
            buf.putShort(s);
        }

        return buf.array();
    }

    private float[] invokeParseWav(byte[] wavBytes) throws Exception {
        Method method = AudioProcessUtil.class.getDeclaredMethod("parseWavToFloatArray", File.class);
        method.setAccessible(true);
        File tempWav = File.createTempFile("test_", ".wav");
        try {
            Files.write(tempWav.toPath(), wavBytes);
            return (float[]) method.invoke(util, tempWav);
        } finally {
            tempWav.delete();
        }
    }

    @Test
    @DisplayName("空的 WAV（不足 header 大小）應回傳空陣列")
    void emptyWavShouldReturnEmptyArray() throws Exception {
        float[] result = invokeParseWav(new byte[10]);
        assertNotNull(result);
        assertEquals(0, result.length);
    }

    @Test
    @DisplayName("恰好 header 大小的 WAV 應回傳空陣列")
    void headerOnlyWavShouldReturnEmptyArray() throws Exception {
        float[] result = invokeParseWav(new byte[44]);
        assertNotNull(result);
        assertEquals(0, result.length);
    }

    @Test
    @DisplayName("有效的 WAV 應正確解析 PCM 資料為 float 陣列")
    void validWavShouldParseCorrectly() throws Exception {
        short s1 = (short) (0.5f * 32768);
        short s2 = (short) (-0.5f * 32768);
        float[] result = invokeParseWav(createMinimalWav(s1, s2));

        assertNotNull(result);
        assertEquals(2, result.length);
        assertEquals(0.5f, result[0], 0.001);
        assertEquals(-0.5f, result[1], 0.001);
    }

    @Test
    @DisplayName("PCM 最大值 (32767) 應轉換為接近 1.0 的 float")
    void maxPcmValue() throws Exception {
        float[] result = invokeParseWav(createMinimalWav((short) 32767));
        assertEquals(1, result.length);
        assertEquals(32767 / 32768.0f, result[0], 0.0001);
    }

    @Test
    @DisplayName("PCM 最小值 (-32768) 應轉換為 -1.0")
    void minPcmValue() throws Exception {
        float[] result = invokeParseWav(createMinimalWav((short) -32768));
        assertEquals(1, result.length);
        assertEquals(-1.0f, result[0], 0.0001);
    }

    @Test
    @DisplayName("PCM 零值應轉換為 0.0f")
    void zeroPcmValue() throws Exception {
        float[] result = invokeParseWav(createMinimalWav((short) 0));
        assertEquals(1, result.length);
        assertEquals(0.0f, result[0], 0.0001);
    }

    @Test
    @DisplayName("多個連續音訊 frame 應全部正確轉換")
    void multipleFrames() throws Exception {
        float[] result = invokeParseWav(createMinimalWav(
                (short) 0, (short) 16384, (short) -16384, (short) 32767, (short) -32768));

        assertEquals(5, result.length);
        assertEquals(0.0f, result[0], 0.001);
        assertEquals(16384 / 32768.0f, result[1], 0.001);
        assertEquals(-16384 / 32768.0f, result[2], 0.001);
        assertEquals(32767 / 32768.0f, result[3], 0.001);
        assertEquals(-1.0f, result[4], 0.001);
    }

    @Test
    @DisplayName("WAV 含奇數長度 data 應正確處理（捨棄最後一個不完整 byte）")
    void oddDataLength() throws Exception {
        byte[] wav = createMinimalWav((short) 100, (short) 200);
        byte[] padded = new byte[wav.length + 1];
        System.arraycopy(wav, 0, padded, 0, wav.length);
        padded[padded.length - 1] = 0x55;
        wav = padded;

        int headerSize = 44;
        int sampleCount = (wav.length - headerSize) / 2;
        File tempWav = File.createTempFile("odd_", ".wav");
        try {
            Files.write(tempWav.toPath(), wav);
            Method method = AudioProcessUtil.class.getDeclaredMethod("parseWavToFloatArray", File.class);
            method.setAccessible(true);
            float[] result = (float[]) method.invoke(util, tempWav);
            assertEquals(sampleCount, result.length);
        } finally {
            tempWav.delete();
        }
    }

    @Test
    @DisplayName("convertTo16kMonoFloatArray 應傳播檔案寫入例外")
    void convertShouldPropagateWriteException() throws Exception {
        doThrow(new IOException("disk full")).when(mockFile).transferTo(any(File.class));

        Exception ex = assertThrows(Exception.class,
                () -> util.convertTo16kMonoFloatArray(mockFile));
        assertTrue(ex.getMessage().contains("disk full"));
    }

    @Test
    @DisplayName("convertTo16kMonoFloatArray 應清理暫存檔案即使轉換失敗")
    void convertShouldCleanupTempFilesOnError() throws Exception {
        doThrow(new RuntimeException("convert fail")).when(mockFile).transferTo(any(File.class));

        assertThrows(Exception.class, () -> util.convertTo16kMonoFloatArray(mockFile));
    }

    @Test
    @DisplayName("convertTo16kMonoFloatArray 應傳播轉換過程中的例外")
    void convertShouldPropagateConversionException() throws Exception {
        doAnswer(invocation -> {
            File f = invocation.getArgument(0);
            Files.write(f.toPath(), new byte[]{0, 0, 0, 0});
            return null;
        }).when(mockFile).transferTo(any(File.class));

        Exception ex = assertThrows(Exception.class,
                () -> util.convertTo16kMonoFloatArray(mockFile));
        assertNotNull(ex);
    }
}
