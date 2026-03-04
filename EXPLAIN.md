# Анализ производительности запросов

## Тестовое окружение

- **Объём данных**: 10 377 документов
  - DRAFT: 10 000
  - APPROVED: 377
  - SUBMITTED: 0
- **СУБД**: PostgreSQL 15 (в Docker)
- **Индексы**: 5 индексов

## 1. Индекс для связи с историей (`idx_history_document_id`)
### Запрос
```sql
EXPLAIN (ANALYZE, BUFFERS)
SELECT * FROM document_status_history 
WHERE document_id = 1;
```
### Результат
```text
Index Scan using idx_history_document_id on document_status_history  
  (cost=0.28..8.86 rows=2 width=123) (actual time=0.072..0.072 rows=0 loops=1)
  Index Cond: (document_id = 1)
  Buffers: shared hit=3 read=2
Planning Time: 0.622 ms
Execution Time: 0.147 ms
```
### Анализ
Индекс используется. При поиске по внешнему ключу выполняется Index Scan, что обеспечивает время ответа менее 0.2 мс. Это критически важно для JOIN операций и каскадных запросов.

## 2. Индекс для связи с реестром (`idx_registry_document_id`)
### Запрос
```sql
EXPLAIN (ANALYZE, BUFFERS)
SELECT * FROM approval_registry 
WHERE document_id = 1;
```
### Результат
```text
Index Scan using approval_registry_document_id_key on approval_registry  
  (cost=0.15..8.17 rows=1 width=39) (actual time=0.039..0.040 rows=0 loops=1)
  Index Cond: (document_id = 1)
  Buffers: shared hit=3 dirtied=2
Planning Time: 0.587 ms
Execution Time: 0.073 ms
```
### Анализ
Уникальный индекс обеспечивает доступ к записи реестра за 0.07 мс. Это гарантирует быструю проверку при утверждении документов.

## 3. Составной индекс для workers (`idx_documents_status_id`)
### Запрос (пакетная выгрузка DRAFT документов)
```sql
EXPLAIN (ANALYZE, BUFFERS)
SELECT id FROM documents 
WHERE status = 'DRAFT' 
ORDER BY id 
LIMIT 100;
```
### Результат
```text
Limit  (cost=0.29..4.63 rows=100 width=8) (actual time=0.060..0.089 rows=100 loops=1)
  Buffers: shared hit=4
  ->  Index Only Scan using idx_documents_status_id on documents  
      (cost=0.29..434.30 rows=9983 width=8) (actual time=0.059..0.082 rows=100 loops=1)
        Index Cond: (status = 'DRAFT'::text)
        Heap Fetches: 0
        Buffers: shared hit=4
Execution Time: 0.123 ms
```
### Анализ:
-Index Only Scan - данные читаются только из индекса, без обращения к таблице

-Heap Fetches: 0 - подтверждает, что все данные получены из индекса

-Время выполнения: 0.123 мс на 100 документов

-Идеально для фоновых workers, которые каждые 30 секунд выбирают пачки документов

## 4. Составной индекс для поиска (`idx_documents_status_created`)
### Запрос (поиск по статусу + период)
```sql
EXPLAIN (ANALYZE, BUFFERS)
SELECT * FROM documents 
WHERE status = 'APPROVED' 
  AND created_at BETWEEN '2026-01-01' AND '2026-12-31'
ORDER BY created_at DESC 
LIMIT 20;
```
### Результат
```text
Limit  (cost=0.29..14.97 rows=20 width=88) (actual time=0.138..0.143 rows=20 loops=1)
  Buffers: shared hit=7
  ->  Index Scan Backward using idx_documents_status_created on documents  
      (cost=0.29..301.40 rows=410 width=88) (actual time=0.136..0.140 rows=20 loops=1)
        Index Cond: ((status)::text = 'APPROVED'::text) 
          AND (created_at >= '2026-01-01'::date) 
          AND (created_at <= '2026-12-31'::date)
        Buffers: shared hit=7
Execution Time: 0.169 ms
```
### Анализ:
-Index Scan с использованием всех условий на уровне индекса

-Сортировка по created_at DESC также использует индекс (Backward Scan)

-Время выполнения: 0.169 мс - мгновенный ответ для UI

-Полностью покрывает требование ТЗ по поиску с фильтрами

## 5. Сложный запрос с JOIN (проверка всех индексов)
### Запрос
```sql
EXPLAIN (ANALYZE, BUFFERS)
SELECT d.*, h.action, r.approved_by
FROM documents d
LEFT JOIN document_status_history h ON d.id = h.document_id
LEFT JOIN approval_registry r ON d.id = r.document_id
WHERE d.status = 'APPROVED'
LIMIT 100;
```
### Результат
```text
Limit  (cost=39.09..44.35 rows=100 width=110) (actual time=0.725..0.774 rows=100 loops=1)
  Buffers: shared hit=15
  ->  Hash Left Join ...
        ->  Index Scan using idx_documents_status on documents d ...
              Index Cond: ((status)::text = 'APPROVED'::text)
        ->  Seq Scan on approval_registry r ...
Execution Time: 0.934 ms
```
### Анализ:
-Индекс idx_documents_status используется для первичной фильтрации

-JOIN выполняются через Hash, так как выборка небольшая (377 строк)

-Общее время < 1 мс на 100 записей с двумя JOIN

## 6. Индекс для поиска по статусу (`idx_documents_status`)
- **На объёме данных**: 10 457 документов
  - DRAFT: 0
  - APPROVED: 9677
  - SUBMITTED: 780

### Запрос
```sql
EXPLAIN (ANALYZE, BUFFERS)
SELECT * FROM documents 
WHERE status = 'SUBMITTED';
```
### Результат
```text
Index Scan using idx_documents_status on documents  (cost=0.29..68.99 rows=1160 width=91) (actual time=0.017..0.133 rows=680 loops=1)
   Index Cond: ((status)::text = 'SUBMITTED'::text)
   Buffers: shared hit=61
 Planning:
   Buffers: shared hit=12
 Planning Time: 0.259 ms
 Execution Time: 0.215 ms
```
### Анализ
- Время выполнения: 0.215 мс на 680 документов
- Все данные из кэша (shared hit=61)
- Это подтверждает правило: индекс эффективен при выборке < 15% таблицы

### Вывод - Индексы работают корректно и полностью покрывают требования технического задания.