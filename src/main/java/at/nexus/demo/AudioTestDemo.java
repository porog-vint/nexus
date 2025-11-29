package at.nexus.demo;

import at.nexus.input.AudioInputManager;
import at.nexus.output.AudioOutputManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.SourceDataLine;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Scanner;
import java.util.concurrent.BlockingQueue;

/**
 * Комплексный тест аудио системы.
 * Меню для выбора тестов: input, output, полный цикл.
 */
public class AudioTestDemo {
    private static final Logger log = LoggerFactory.getLogger(AudioTestDemo.class);
    private static final Scanner scanner = new Scanner(System.in);

    public static void main(String[] args) {
        log.info("=== Комплексный тест аудио системы ===\n");

        while (true) {
            System.out.println("\nВыбери тест:");
            System.out.println("1 - Тест записи с микрофона (Input)");
            System.out.println("2 - Тест воспроизведения звука (Output)");
            System.out.println("3 - Полный цикл: запись + воспроизведение");
            System.out.println("0 - Выход");
            System.out.print("\nВввод: ");

            String choice = scanner.nextLine().trim();

            switch (choice) {
                case "1" -> testInput();
                case "2" -> testOutput();
                case "3" -> testFullCycle();
                case "0" -> {
                    log.info("Завершение");
                    return;
                }
                default -> System.out.println("Неверный выбор!");
            }
        }
    }

    /**
     * Тест 1: Запись с микрофона с визуализацией
     */
    private static void testInput() {
        log.info("\n=== Тест Input ===");
        AudioInputManager audioInput = new AudioInputManager();

        try {
            audioInput.initialize();
            audioInput.startRecording();

            BlockingQueue<byte[]> audioQueue = audioInput.getAudioQueue();

            log.info("Запись 5 секунд... Говори в микрофон!\n");

            long startTime = System.currentTimeMillis();
            int chunksReceived = 0;

            while (System.currentTimeMillis() - startTime < 5000) {
                byte[] audioData = audioQueue.poll();
                if (audioData != null) {
                    chunksReceived++;

                    if (chunksReceived % 20 == 0) {
                        double volume = calculateVolume(audioData);
                        System.out.println(String.format("%2d сек | %s | %3.0f%%",
                                (chunksReceived / 20) / 5,
                                createBar(volume),
                                volume));
                    }
                }
                Thread.sleep(10);
            }

            audioInput.stopRecording();
            audioInput.close();

            log.info("\nПолучено {} чанков. Микрофон работает!", chunksReceived);

        } catch (Exception e) {
            log.error("Ошибка", e);
        }
    }

    /**
     * Тест 2: Воспроизведение синусоиды
     */
    private static void testOutput() {
        log.info("\n=== Тест Output ===");
        AudioOutputManager audioOutput = new AudioOutputManager();

        try {
            audioOutput.initialize();
            audioOutput.startPlayback();

            log.info("Воспроизведение тестового звука (440Hz, 1 сек)...");

            byte[] beep = generateBeep(440, 1000);
            audioOutput.enqueueAudio(beep);

            Thread.sleep(1500);

            audioOutput.stopPlayback();
            audioOutput.close();

            log.info("Динамики работают!");

        } catch (Exception e) {
            log.error("Ошибка", e);
        }
    }

    /**
     * Тест 3: Запись голоса + воспроизведение
     */
    private static void testFullCycle() {
        log.info("\n=== Тест полного цикла ===");

        AudioInputManager audioInput = new AudioInputManager();

        try {
            // Запись
            audioInput.initialize();
            audioInput.startRecording();

            log.info("Запись 10 секунд... Говори в микрофон!");

            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            BlockingQueue<byte[]> inputQueue = audioInput.getAudioQueue();
            long startTime = System.currentTimeMillis();
            int chunks = 0;

            while (System.currentTimeMillis() - startTime < 10000) {
                byte[] data = inputQueue.take(); // Используем take() вместо poll()
                buffer.write(data);
                chunks++;
            }

            audioInput.stopRecording();
            audioInput.close();

            byte[] recorded = buffer.toByteArray();
            log.info("Записано {} байт ({} чанков)", recorded.length, chunks);

            if (recorded.length == 0) {
                log.error("Ничего не записано!");
                return;
            }

            Thread.sleep(500);

            // Воспроизведение напрямую
            log.info("Воспроизведение...");
            playAudioDirect(recorded);

            log.info("Полный цикл завершён!");

        } catch (Exception e) {
            log.error("Ошибка", e);
        }
    }

    /**
     * Прямое воспроизведение без очереди
     */
    private static void playAudioDirect(byte[] audioData) throws Exception {
        AudioFormat format = new AudioFormat(16000, 16, 1, true, false);

        log.info("Формат воспроизведения: {}Hz, {} бит, {} канал(ов)",
                (int)format.getSampleRate(),
                format.getSampleSizeInBits(),
                format.getChannels());
        log.info("Размер данных: {} байт", audioData.length);

        DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);
        SourceDataLine speaker = (SourceDataLine) AudioSystem.getLine(info);

        speaker.open(format);
        speaker.start();

        ByteArrayInputStream bais = new ByteArrayInputStream(audioData);
        byte[] buffer = new byte[4096];
        int bytesRead;
        int totalWritten = 0;

        while ((bytesRead = bais.read(buffer)) != -1) {
            speaker.write(buffer, 0, bytesRead);
            totalWritten += bytesRead;
        }

        log.info("Записано в speaker: {} байт", totalWritten);

        speaker.drain();
        speaker.stop();
        speaker.close();
    }

    private static double calculateVolume(byte[] audioData) {
        long sum = 0;
        for (int i = 0; i < audioData.length - 1; i += 2) {
            short sample = (short) ((audioData[i + 1] << 8) | (audioData[i] & 0xFF));
            sum += sample * sample;
        }
        double rms = Math.sqrt((double) sum / (audioData.length / 2));
        return Math.min(100, (rms / 32768.0) * 100 * 5);
    }

    private static String createBar(double volume) {
        int bars = (int) (volume / 5);
        bars = Math.min(20, Math.max(0, bars));
        StringBuilder bar = new StringBuilder();
        for (int i = 0; i < 20; i++) {
            bar.append(i < bars ? "█" : "░");
        }
        return bar.toString();
    }

    private static byte[] generateBeep(int frequency, int durationMs) {
        int numSamples = (16000 * durationMs) / 1000;
        byte[] audioData = new byte[numSamples * 2];
        double angularFreq = 2.0 * Math.PI * frequency / 16000;

        for (int i = 0; i < numSamples; i++) {
            double sample = Math.sin(angularFreq * i);
            short value = (short) (sample * Short.MAX_VALUE * 0.5);
            audioData[i * 2] = (byte) (value & 0xFF);
            audioData[i * 2 + 1] = (byte) ((value >> 8) & 0xFF);
        }
        return audioData;
    }
}