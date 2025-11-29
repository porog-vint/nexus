package at.nexus.demo;

import at.nexus.audio.AudioOutputManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sound.sampled.LineUnavailableException;

/**
 * Тест воспроизведения звука через AudioOutputManager.
 * Генерирует простой тестовый звук (beep) и воспроизводит его.
 */
public class AudioOutputDemo {
    private static final Logger log = LoggerFactory.getLogger(AudioOutputDemo.class);

    private static final int SAMPLE_RATE = 16000;
    private static final int DURATION_MS = 1000; // 1 секунда

    public static void main(String[] args) {
        log.info("=== Тест AudioOutputManager ===");

        AudioOutputManager audioOutput = new AudioOutputManager();

        try {
            audioOutput.initialize();
            audioOutput.startPlayback();

            log.info("Генерация тестового звука (beep 440Hz)...");

            // Генерируем звук 440Hz (нота Ля) на 1 секунду
            byte[] beepSound = generateBeep(440, DURATION_MS);

            log.info("Воспроизведение...");
            audioOutput.enqueueAudio(beepSound);

            // Ждём пока воспроизведётся
            Thread.sleep(DURATION_MS + 500);

            audioOutput.stopPlayback();
            audioOutput.close();

            log.info("Тест завершён! Услышал звук?");

        } catch (LineUnavailableException e) {
            log.error("Ошибка инициализации динамиков: {}", e.getMessage());
        } catch (InterruptedException e) {
            log.error("Прерывание: {}", e.getMessage());
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.error("Неожиданная ошибка", e);
        }
    }

    /**
     * Генерируем простой синусоидальный звук (beep)
     * @param frequency Частота в Hz (например 440 для ноты Ля)
     * @param durationMs Длительность в миллисекундах
     */
    private static byte[] generateBeep(int frequency, int durationMs) {
        int numSamples = (SAMPLE_RATE * durationMs) / 1000;
        byte[] audioData = new byte[numSamples * 2]; // 16-bit = 2 bytes per sample

        double angularFrequency = 2.0 * Math.PI * frequency / SAMPLE_RATE;

        for (int i = 0; i < numSamples; i++) {
            // Генерируем синусоиду
            double sample = Math.sin(angularFrequency * i);

            // Конвертируем в 16-bit signed integer
            short sampleValue = (short) (sample * Short.MAX_VALUE * 0.5); // 0.5 для умеренной громкости

            // Записываем в little-endian формат
            audioData[i * 2] = (byte) (sampleValue & 0xFF);
            audioData[i * 2 + 1] = (byte) ((sampleValue >> 8) & 0xFF);
        }

        return audioData;
    }
}