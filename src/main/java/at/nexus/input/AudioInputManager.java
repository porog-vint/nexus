package at.nexus.input;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javax.sound.sampled.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Менеджер для захвата аудио с микрофона.
 *
 * Захватывает звук с микрофона в реальном времени и помещает
 * аудио данные в очередь для последующей обработки (STT, VAD).
 *
 * Формат: 16kHz, 16-bit, mono - стандарт для speech-to-text.
 * Работает в отдельном потоке для минимальной задержки.
 */

public class AudioInputManager {
    private static final Logger log = LoggerFactory.getLogger(AudioInputManager.class);

    // Аудио параметры
    private static final int SAMPLE_RATE = 16000; // 16kHz оптимально для речи
    private static final int SAMPLE_SIZE_BITS = 16;
    private static final int CHANNELS = 1; // mono

    private TargetDataLine microphone;
    private final BlockingQueue<byte[]> audioQueue;
    private volatile boolean isRecording;
    private Thread recordingThread;

    public AudioInputManager() {
        this.audioQueue = new LinkedBlockingQueue<>();
        this.isRecording = false;
    }

    /**
     * Инициализация микрофона
     */
    public void initialize() throws LineUnavailableException {
        AudioFormat format = new AudioFormat(
                SAMPLE_RATE,
                SAMPLE_SIZE_BITS,
                CHANNELS,
                true, // signed
                false // little endian
        );

        DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);

        if (!AudioSystem.isLineSupported(info)) {
            throw new LineUnavailableException("Микрофон не поддерживается");
        }

        microphone = (TargetDataLine) AudioSystem.getLine(info);

        // ИСПРАВЛЕНО: Увеличен буфер до 1 секунды для стабильности
        int bufferSize = SAMPLE_RATE * SAMPLE_SIZE_BITS * CHANNELS / 8; // 1 секунда
        microphone.open(format, bufferSize);

        log.info("Микрофон инициализирован: {}Hz, {} бит, {} канал(ов), буфер {} байт",
                SAMPLE_RATE, SAMPLE_SIZE_BITS, CHANNELS, bufferSize);
    }

    /**
     * Начать запись
     */
    public void startRecording() {
        if (isRecording) {
            log.warn("Запись уже идёт");
            return;
        }

        isRecording = true;
        microphone.start();

        recordingThread = new Thread(this::recordingLoop, "AudioInput-Thread");
        recordingThread.start();

        log.info("Запись начата");
    }

    /**
     * Остановить запись
     */
    public void stopRecording() {
        if (!isRecording) {
            return;
        }

        isRecording = false;

        if (recordingThread != null) {
            try {
                recordingThread.join(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        microphone.stop();
        log.info("Запись остановлена");
    }

    /**
     * Закрыть микрофон
     */
    public void close() {
        stopRecording();
        if (microphone != null) {
            microphone.close();
            log.info("Микрофон закрыт");
        }
    }

    /**
     * Получить очередь аудио данных
     */
    public BlockingQueue<byte[]> getAudioQueue() {
        return audioQueue;
    }

    /**
     * Основной цикл записи
     */
    private void recordingLoop() {
        // ИСПРАВЛЕНО: Читаем порциями по 100ms для плавности
        int chunkSize = SAMPLE_RATE * SAMPLE_SIZE_BITS * CHANNELS / 8 / 10; // 100ms
        byte[] buffer = new byte[chunkSize];

        log.info("Цикл записи запущен (размер чанка: {} байт)", chunkSize);

        while (isRecording) {
            int bytesRead = microphone.read(buffer, 0, buffer.length);

            if (bytesRead > 0) {
                byte[] audioData = new byte[bytesRead];
                System.arraycopy(buffer, 0, audioData, 0, bytesRead);

                try {
                    audioQueue.put(audioData);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        log.info("Цикл записи завершён");
    }
}