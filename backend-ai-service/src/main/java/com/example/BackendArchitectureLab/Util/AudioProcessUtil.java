package com.example.BackendArchitectureLab.Util;

import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import ws.schild.jave.Encoder;
import ws.schild.jave.MultimediaObject;
import ws.schild.jave.encode.AudioAttributes;
import ws.schild.jave.encode.EncodingAttributes;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;

@Component
public class AudioProcessUtil {

    public float[] convertTo16kMonoFloatArray(MultipartFile file) throws Exception {
        File tempInput = File.createTempFile("whisper_in_", ".tmp");
        File tempOutput = File.createTempFile("whisper_out_", ".wav");

        try {
            file.transferTo(tempInput);

            AudioAttributes audio = new AudioAttributes();
            audio.setCodec("pcm_s16le");
            audio.setBitRate(256000);
            audio.setChannels(1);
            audio.setSamplingRate(16000);

            EncodingAttributes attrs = new EncodingAttributes();
            attrs.setOutputFormat("wav");
            attrs.setAudioAttributes(audio);

            Encoder encoder = new Encoder();
            encoder.encode(new MultimediaObject(tempInput), tempOutput, attrs);

            return parseWavToFloatArray(tempOutput);

        } finally {
            if (tempInput.exists()) tempInput.delete();
            if (tempOutput.exists()) tempOutput.delete();
        }
    }

    private float[] parseWavToFloatArray(File wavFile) throws IOException {
        byte[] bytes = Files.readAllBytes(wavFile.toPath());
        int headerSize = 44;
        if (bytes.length <= headerSize) {
            return new float[0];
        }

        int dataLength = bytes.length - headerSize;
        float[] floatArray = new float[dataLength / 2];

        ByteBuffer buffer = ByteBuffer.wrap(bytes, headerSize, dataLength);
        buffer.order(ByteOrder.LITTLE_ENDIAN);

        for (int i = 0; i < floatArray.length; i++) {
            floatArray[i] = buffer.getShort() / 32768.0f;
        }

        return floatArray;
    }
}
