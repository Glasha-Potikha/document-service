package com.itq.generator.service;

import com.itq.generator.client.DocumentServiceClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentGeneratorService implements CommandLineRunner {
    private final DocumentServiceClient client;

    @Value("${generator.count:10}")
    private int totalDocuments;

    @Value("${generator.batch-size:5}")
    private int batchSize;

    @Value("${generator.delay-ms:100}")
    private int delayMs;

    @Override
    public void run(String... args) {
        LocalDateTime startTime = LocalDateTime.now();
        log.info("===========================================");
        log.info("Запуск генерации документов");
        log.info("Всего документов для создания: {}", totalDocuments);
        log.info("Размер пачки: {}", batchSize);
        log.info("===========================================");

        AtomicInteger created = new AtomicInteger(0);
        AtomicInteger failed = new AtomicInteger(0);

        for (int i = 0; i < totalDocuments; i += batchSize) {
            int currentBatch = Math.min(batchSize, totalDocuments - i);
            log.info("Создание пачки {}/{} ({} документов)",
                    (i / batchSize) + 1,
                    (int) Math.ceil((double) totalDocuments / batchSize),
                    currentBatch);

            LocalDateTime batchStart = LocalDateTime.now();

            for (int j = 0; j < currentBatch; j++) {
                try {
                    String author = String.format("Автор %d", i + j + 1);
                    String title = String.format("Документ %d", i + j + 1);

                    client.createDocument(author, title);
                    created.incrementAndGet();

                    log.debug("Создан документ {}/{}: {} - {}",
                            i + j + 1, totalDocuments, author, title);

                    Thread.sleep(delayMs);

                } catch (Exception e) {
                    failed.incrementAndGet();
                    log.error("Ошибка при создании документа {}/{}", i + j + 1, totalDocuments, e);
                }
            }
            Duration batchDuration = Duration.between(batchStart, LocalDateTime.now());
            log.info("Пачка создана за {} мс", batchDuration.toMillis());
            log.info("Прогресс: {}/{} документов ({}%)\n",
                    created.get(), totalDocuments,
                    (created.get() * 100 / totalDocuments));
        }
        Duration totalDuration = Duration.between(startTime, LocalDateTime.now());
        log.info("===========================================");
        log.info("Генерация завершена!");
        log.info("Всего создано: {} документов", created.get());
        log.info("Ошибок: {}", failed.get());
        log.info("Общее время: {} мс ({} секунд)",
                totalDuration.toMillis(), totalDuration.getSeconds());
        log.info("Средняя скорость: {} док/сек",
                created.get() / Math.max(1, totalDuration.getSeconds()));
        log.info("===========================================");
    }
}
