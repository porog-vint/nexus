package at.nexus.output;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sound.sampled.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Менеджер для воспроизведения аудио через динамики.
 * 
 * Принимает аудио данные в очередь и воспроизводит их
 * в отдельном потоке. Используется для озвучки ответов AI (TTS).
 * 
 * Поддерживает остановку воспроизведения и очистку очереди.
 */
public class AudioOutputManager {
    private static final Logger log = LoggerFactory.getLogger(AudioOutputManager.class);
    
    // Аудио параметры (совпадают с input для совместимости)
    private static final int SAMPLE_RATE = 16000;
    private static final int SAMPLE_SIZE_BITS = 16;
    private static final int CHANNELS = 1;
    
    private SourceDataLine speaker;
    private final BlockingQueue<byte[]> audioQueue;
    private volatile boolean isPlaying;
    private Thread playbackThread;
    
    public AudioOutputManager() {
        this.audioQueue = new LinkedBlockingQueue<>();
        this.isPlaying = false;
    }
    
    /**
     * Инициализация динамиков
     */
    public void initialize() throws LineUnavailableException {
        AudioFormat format = new AudioFormat(
            SAMPLE_RATE,
            SAMPLE_SIZE_BITS,
            CHANNELS,
            true,
            false
        );
        
        DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);
        
        if (!AudioSystem.isLineSupported(info)) {
            throw new LineUnavailableException("Динамики не поддерживаются");
        }
        
        speaker = (SourceDataLine) AudioSystem.getLine(info);
        speaker.open(format);
        
        log.info("Динамики инициализированы: {}Hz, {} бит, {} канал(ов)", 
                 SAMPLE_RATE, SAMPLE_SIZE_BITS, CHANNELS);
    }
    
    /**
     * Начать воспроизведение
     */
    public void startPlayback() {
        if (isPlaying) {
            log.warn("Воспроизведение уже идёт");
            return;
        }
        
        isPlaying = true;
        speaker.start();
        
        playbackThread = new Thread(this::playbackLoop, "AudioOutput-Thread");
        playbackThread.start();
        
        log.info("Воспроизведение начато");
    }
    
    /**
     * Остановить воспроизведение
     */
    public void stopPlayback() {
        if (!isPlaying) {
            return;
        }
        
        isPlaying = false;
        
        if (playbackThread != null) {
            try {
                playbackThread.join(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        
        speaker.stop();
        speaker.flush(); // Очищаем буфер
        audioQueue.clear(); // Очищаем очередь
        
        log.info("Воспроизведение остановлено");
    }
    
    /**
     * Закрыть динамики
     */
    public void close() {
        stopPlayback();
        if (speaker != null) {
            speaker.close();
            log.info("Динамики закрыты");
        }
    }
    
    /**
     * Добавить аудио данные в очередь для воспроизведения
     */
    public void enqueueAudio(byte[] audioData) {
        if (audioData != null && audioData.length > 0) {
            try {
                audioQueue.put(audioData);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("Ошибка добавления в очередь", e);
            }
        }
    }
    
    /**
     * Получить очередь аудио данных (для прямой записи)
     */
    public BlockingQueue<byte[]> getAudioQueue() {
        return audioQueue;
    }
    
    /**
     * Проверить играет ли что-то сейчас
     */
    public boolean isPlaying() {
        return isPlaying && !audioQueue.isEmpty();
    }
    
    /**
     * Основной цикл воспроизведения
     */
    private void playbackLoop() {
        log.info("Цикл воспроизведения запущен");
        
        while (isPlaying) {
            try {
                // Берём данные из очереди (ждём если пусто)
                byte[] audioData = audioQueue.poll(100, java.util.concurrent.TimeUnit.MILLISECONDS);
                
                if (audioData != null) {
                    speaker.write(audioData, 0, audioData.length);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        
        log.info("Цикл воспроизведения завершён");
    }
}