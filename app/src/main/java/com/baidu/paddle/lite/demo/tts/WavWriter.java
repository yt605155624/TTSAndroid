package com.baidu.paddle.lite.demo.tts;

import static java.lang.Math.abs;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public class WavWriter {
    public void rawToWave(String file, float[] data, int samplerate) throws IOException {

        File waveFile = new File(file);// creating the empty wav file.
        waveFile.createNewFile();

        DataOutputStream output = null;//following block is converting raw to wav.
        try {
            output = new DataOutputStream(new FileOutputStream(waveFile));
            // WAVE header
            writeString(output, "RIFF"); // chunk id
            writeInt(output, 36 + data.length * 2); // chunk size
            writeString(output, "WAVE"); // format
            writeString(output, "fmt "); // subchunk 1 id
            writeInt(output, 16); // subchunk 1 size
            writeShort(output, (short) 1); // audio format (1 = PCM)
            writeShort(output, (short) 1); // number of channels
            writeInt(output, samplerate); // sample rate
            writeInt(output, samplerate * 2); // byte rate
            writeShort(output, (short) 2); // block align
            writeShort(output, (short) 16); // bits per sample
            writeString(output, "data"); // subchunk 2 id
            writeInt(output, data.length * 2); // subchunk 2 size
            short[] short_data = FloatArray2ShortArray(data);
            for (int i = 0; i < short_data.length; i++) {
                writeShort(output, short_data[i]);
            }
        } finally {
            if (output != null) {
                output.close();
            }
        }
    }

    private void writeInt(final DataOutputStream output, final int value) throws IOException {
        output.write(value);
        output.write(value >> 8);
        output.write(value >> 16);
        output.write(value >> 24);
    }

    private void writeShort(final DataOutputStream output, final short value) throws IOException {
        output.write(value);
        output.write(value >> 8);
    }

    private void writeString(final DataOutputStream output, final String value) throws IOException {
        for (int i = 0; i < value.length(); i++) {
            output.write(value.charAt(i));
        }
    }

    public static short[] FloatArray2ShortArray(float[] values) {
        float mmax = (float) 0.01;
        short[] ret = new short[values.length];

        for (int i = 0; i < values.length; i++) {
            if (abs(values[i]) > mmax) {
                mmax = abs(values[i]);
            }
        }

        for (int i = 0; i < values.length; i++) {
            values[i] = values[i] * (32767 / mmax);
            ret[i] = (short) (values[i]);
        }
        return ret;
    }
}
