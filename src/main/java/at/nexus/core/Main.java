package at.nexus.core;

import at.nexus.audio.AudioInputManager;
import at.nexus.audio.AudioOutputManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.BlockingQueue;

public class Main {
    private static final Logger log = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        log.info("Запуск Nexus Voice AI...");

        AudioInputManager inputManager = new AudioInputManager();
        AudioOutputManager outputManager = new AudioOutputManager();

        try {
            // 1. Инициализация оборудования
            log.info("Инициализация аудио устройств...");
            inputManager.initialize();
            outputManager.initialize();

            // 2. Запуск потоков захватов и воспроизведения
            inputManager.startRecording();
            outputManager.startPlayback();

            log.info("🔴 ЭХО-ТЕСТ ЗАПУЩЕН: Скажите что-нибудь в микрофон (вы должны услышать себя)");
            log.info("Нажмите Ctrl+C для выхода");

            // 3. Главный цикл пересылки данных (Mic -> Speaker)
            // В будущем здесь будет стоять VAD и STT
            BlockingQueue<byte[]> inputQueue = inputManager.getAudioQueue();

            while (true) {
                // Блокируемся, пока не придут данные с микрофона
                byte[] audioData = inputQueue.take();

                // Сразу отправляем их на динамики
                outputManager.enqueueAudio(audioData);
            }

        } catch (Exception e) {
            log.error("Критическая ошибка: ", e);
        } finally {
            // Очистка ресурсов при выходе (если цикл прервется)
            inputManager.close();
            outputManager.close();
        }
    }
}