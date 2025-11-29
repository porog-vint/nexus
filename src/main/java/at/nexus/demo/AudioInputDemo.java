package at.nexus.demo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sound.sampled.*;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class AudioInputDemo {
    private static final Logger log = LoggerFactory.getLogger(AudioInputDemo.class);

    private static final int SAMPLE_RATE = 44100;
    private static final int SAMPLE_SIZE_BITS = 16;
    private static final int CHANNELS = 2;
    private static final double AMPLIFICATION = 2.0; // Усиление в 2.5 раза

    public static void main(String[] args) {
        log.info("=== Тест записи и воспроизведения (высокое качество + усиление) ===");

        try {
            AudioFormat format = new AudioFormat(
                    SAMPLE_RATE,
                    SAMPLE_SIZE_BITS,
                    CHANNELS,
                    true,
                    false
            );

            DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
            TargetDataLine microphone = (TargetDataLine) AudioSystem.getLine(info);

            int bufferSize = SAMPLE_RATE * SAMPLE_SIZE_BITS * CHANNELS / 8 / 10;
            microphone.open(format, bufferSize);
            microphone.start();

            log.info("Запись 10 секунд... Говори в микрофон!");

            ByteArrayOutputStream audioBuffer = new ByteArrayOutputStream();
            byte[] buffer = new byte[bufferSize];
            long startTime = System.currentTimeMillis();

            while (System.currentTimeMillis() - startTime < 10000) {
                int bytesRead = microphone.read(buffer, 0, buffer.length);
                if (bytesRead > 0) {
                    audioBuffer.write(buffer, 0, bytesRead);
                }
            }

            microphone.stop();
            microphone.close();

            log.info("Запись завершена. Размер: {} байт", audioBuffer.size());

            // Усиливаем звук
            byte[] amplifiedAudio = amplifyAudio(audioBuffer.toByteArray());
            log.info("Звук усилен в {} раз", AMPLIFICATION);

            // Воспроизведение
            log.info("Воспроизведение...");
            playAudio(amplifiedAudio, format);

            log.info("Тест завершён!");

        } catch (Exception e) {
            log.error("Ошибка", e);
        }
    }

    /**
     * Усиливаем аудио сигнал
     */
    private static byte[] amplifyAudio(byte[] audioData) {
        byte[] amplified = new byte[audioData.length];

        for (int i = 0; i < audioData.length - 1; i += 2) {
            // Читаем 16-bit sample
            short sample = (short) ((audioData[i + 1] << 8) | (audioData[i] & 0xFF));

            // Усиливаем
            int amplifiedSample = (int) (sample * AMPLIFICATION);

            // Ограничиваем чтобы не было клиппинга
            amplifiedSample = Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, amplifiedSample));

            // Записываем обратно
            amplified[i] = (byte) (amplifiedSample & 0xFF);
            amplified[i + 1] = (byte) ((amplifiedSample >> 8) & 0xFF);
        }

        return amplified;
    }

    private static void playAudio(byte[] audioData, AudioFormat format) throws LineUnavailableException, IOException {
        DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);
        SourceDataLine speaker = (SourceDataLine) AudioSystem.getLine(info);

        speaker.open(format);
        speaker.start();

        ByteArrayInputStream bais = new ByteArrayInputStream(audioData);
        byte[] buffer = new byte[4096];
        int bytesRead;

        while ((bytesRead = bais.read(buffer)) != -1) {
            speaker.write(buffer, 0, bytesRead);
        }

        speaker.drain();
        speaker.stop();
        speaker.close();
    }
}