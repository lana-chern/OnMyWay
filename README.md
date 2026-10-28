# OnMyWay

OnMyWay is a Java backend for discovering and managing places across cities. It exposes a REST API built with Spring Boot and PostgreSQL. The project is under active development; this README describes the functionality currently implemented.

## Tech stack

- Java 25
- Spring Boot 4
- Spring MVC REST API
- Spring Data JPA / Hibernate
- PostgreSQL 17
- Flyway database migrations
- Spring Security, JWT access tokens, refresh-token rotation and revocation
- Springdoc OpenAPI / Swagger UI
- Docker Compose
- Maven; JUnit and Spring Boot Test, with PostgreSQL integration tests using Testcontainers

## Current functionality

### Cities and places

- Public endpoints for listing cities and retrieving a city by ID.
- Public search for published places by city, with optional case-insensitive name search and pagination.
- Place details are public only while the place is in the `PUBLISHED` state.
- Organizers can create and manage their own places. Place data includes a name, description, coordinates, photos, contacts and opening hours.
- Moderators can manage non-archived places; administrators can manage places across owners.
- Deleting a place is a soft delete: its status becomes `ARCHIVED`.

### Place lifecycle and moderation

Places use the following workflow:

1. A newly created place starts in `DRAFT`.
2. An organizer submits their own draft or rejected place for moderation; its status becomes `PENDING_MODERATION`.
3. Moderators and administrators can review the moderation queue and publish or reject pending places.
4. A rejected place can be corrected by its owner and submitted again.
5. Archived places are not available through public place endpoints.

Place updates use optimistic locking. Update requests must include the version most recently read by the client. Moderation publish/reject requests must also include the version reviewed by the moderator. Stale versions are rejected, preventing a decision from being applied to data that has changed since it was read.

### Authentication and authorization

- User registration, login and current-user lookup.
- Registration supports regular user and organizer account types; privileged moderator/admin roles are not self-assigned through public registration.
- JWT bearer authentication and role-based access rules.
- Refresh tokens can be rotated and revoked through the authentication API.
- Login and registration attempt throttling. Cloudflare Turnstile can be configured for additional verification after suspicious login activity.
- Passwords are stored as password hashes.
- API validation and consistent error handling.

## API overview

The API is available locally at `http://localhost:8080`.

| Method | Endpoint | Access / purpose |
| --- | --- | --- |
| `GET` | `/api/cities` | Public list of cities |
| `GET` | `/api/cities/{id}` | Public city details |
| `GET` | `/api/places?cityId={id}&q={text}&page=0&size=20` | Public paginated search of published places |
| `GET` | `/api/places/{id}` | Public details for a published place |
| `POST` | `/api/auth/register` | Register a user or organizer |
| `POST` | `/api/auth/login` | Authenticate and receive tokens |
| `POST` | `/api/auth/refresh` | Rotate a refresh token |
| `POST` | `/api/auth/logout` | Revoke a refresh token |
| `GET` | `/api/auth/me` | Current authenticated user |
| `GET` | `/api/places/{id}/manage` | Read a place for management, subject to permissions |
| `POST` | `/api/places` | Create a place (organizer) |
| `PUT` | `/api/places/{id}` | Update a place; request must include its current `version` |
| `DELETE` | `/api/places/{id}` | Archive a place (owner/organizer or administrator, subject to permissions) |
| `POST` | `/api/places/{id}/submit-for-moderation` | Submit a place for moderation (owner/organizer) |
| `GET` | `/api/moderation/places/pending` | List pending places (moderator/admin) |
| `POST` | `/api/moderation/places/{id}/publish` | Publish a pending place using the reviewed version (moderator/admin) |
| `POST` | `/api/moderation/places/{id}/reject` | Reject a pending place using the reviewed version (moderator/admin) |

The `q` parameter is optional. Page numbering starts at zero; `size` must be between 1 and 100. Protected operations require a bearer access token and the appropriate role. The API documentation describes request and response schemas.

## API documentation

- Swagger UI: `http://localhost:8080/swagger-ui.html`
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`

The documentation endpoints are public. API operations continue to enforce authentication and authorization.

## Local setup with Docker Compose

### Requirements

- Docker Engine/Desktop with the Docker Compose plugin.
- A Base64-encoded JWT signing secret containing at least 32 bytes of cryptographic key material.

### Start the application

1. Copy `.env.example` to `.env`.
2. Set a local database password in `POSTGRES_PASSWORD`.
3. Set `OMW_JWT_SECRET` to a Base64-encoded secret. Generate one with:

   ```sh
   openssl rand -base64 32
   ```

4. Start the application and PostgreSQL:

   ```sh
   docker compose up --build -d
   ```

5. Follow application logs:

   ```sh
   docker compose logs -f app
   ```

6. Open Swagger UI at `http://localhost:8080/swagger-ui.html`.

The API port is bound to `127.0.0.1:8080`. PostgreSQL is available to services on the Compose network at `db:5432`; its port is not published on the host. Database schema changes are applied by Flyway during application startup.

PostgreSQL data is stored in the named volume `onmyway_postgres_data` and survives container recreation. To stop the stack without deleting database data:

```sh
docker compose down
```

**Do not run `docker compose down -v` unless you intend to delete the local database volume.**

This Compose setup is intended for local development, not production deployment. Do not commit `.env` or real credentials to Git.

## Tests

Run the test suite with Maven:

```sh
mvn test
```

The test suite includes unit tests, API/controller tests, security access-rule tests and PostgreSQL integration tests. Some integration tests require Docker because they use Testcontainers.

## Roadmap

Potential follow-up work includes:

- further end-to-end coverage of place versioning and concurrent updates;
- continued authentication and authorization hardening;
- production deployment planning, including HTTPS, backups, monitoring and operational configuration.

---

# Русская версия

OnMyWay — backend на Java для поиска и управления местами в разных городах. Приложение предоставляет REST API на Spring Boot и использует PostgreSQL. Проект находится в активной разработке; ниже описана функциональность, реализованная на текущий момент.

## Технологический стек

- Java 25
- Spring Boot 4
- Spring MVC REST API
- Spring Data JPA / Hibernate
- PostgreSQL 17
- Flyway для миграций базы данных
- Spring Security, JWT access-токены, ротация и отзыв refresh-токенов
- Springdoc OpenAPI / Swagger UI
- Docker Compose
- Maven; JUnit и Spring Boot Test, интеграционные тесты PostgreSQL с Testcontainers

## Реализованная функциональность

### Города и места

- Публичное получение списка городов и информации о городе по ID.
- Публичный поиск опубликованных мест по городу с необязательным поиском по названию без учёта регистра и пагинацией.
- Публичные эндпоинты возвращают место только в статусе `PUBLISHED`.
- Организаторы могут создавать места и управлять собственными местами. Данные места включают название, описание, координаты, фотографии, контакты и часы работы.
- Модераторы могут управлять неархивированными местами; администраторы — местами разных владельцев.
- Удаление места выполняется мягко: статус меняется на `ARCHIVED`.

### Жизненный цикл места и модерация

Рабочий процесс места:

1. Новое место создаётся в статусе `DRAFT`.
2. Организатор отправляет собственное место в статусе `DRAFT` или `REJECTED` на модерацию; статус меняется на `PENDING_MODERATION`.
3. Модераторы и администраторы просматривают очередь и публикуют или отклоняют ожидающие места.
4. Владелец может исправить отклонённое место и отправить его на модерацию повторно.
5. Архивные места недоступны через публичные эндпоинты.

Для обновления места используется optimistic locking. Запрос на изменение должен содержать версию, которую клиент получил при последнем чтении. Запросы модератора на публикацию или отклонение также должны содержать версию рассмотренного места. Устаревшая версия отклоняется, чтобы решение не применялось к данным, изменившимся после просмотра.

### Аутентификация и авторизация

- Регистрация пользователя, вход и получение данных текущего пользователя.
- При публичной регистрации можно создать обычного пользователя или организатора; привилегированные роли модератора и администратора нельзя назначить себе через публичную регистрацию.
- JWT Bearer-аутентификация и разграничение доступа по ролям.
- Ротация refresh-токенов и их отзыв через API аутентификации.
- Ограничение частоты попыток входа и регистрации. При подозрительной активности входа можно включить дополнительную проверку Cloudflare Turnstile.
- Пароли хранятся в виде хешей.
- Валидация запросов и единообразная обработка ошибок.

## Основные эндпоинты API

API доступен локально по адресу `http://localhost:8080`.

| Метод | Эндпоинт | Доступ / назначение |
| --- | --- | --- |
| `GET` | `/api/cities` | Публичный список городов |
| `GET` | `/api/cities/{id}` | Публичная информация о городе |
| `GET` | `/api/places?cityId={id}&q={text}&page=0&size=20` | Публичный поиск опубликованных мест с пагинацией |
| `GET` | `/api/places/{id}` | Публичная информация об опубликованном месте |
| `POST` | `/api/auth/register` | Регистрация пользователя или организатора |
| `POST` | `/api/auth/login` | Вход и получение токенов |
| `POST` | `/api/auth/refresh` | Ротация refresh-токена |
| `POST` | `/api/auth/logout` | Отзыв refresh-токена |
| `GET` | `/api/auth/me` | Данные текущего пользователя |
| `GET` | `/api/places/{id}/manage` | Получение места для управления с проверкой прав |
| `POST` | `/api/places` | Создание места (организатор) |
| `PUT` | `/api/places/{id}` | Обновление места; запрос должен содержать актуальную `version` |
| `DELETE` | `/api/places/{id}` | Архивация места с учётом прав владельца или администратора |
| `POST` | `/api/places/{id}/submit-for-moderation` | Отправка места на модерацию владельцем/организатором |
| `GET` | `/api/moderation/places/pending` | Очередь модерации (модератор/администратор) |
| `POST` | `/api/moderation/places/{id}/publish` | Публикация с проверкой рассмотренной версии (модератор/администратор) |
| `POST` | `/api/moderation/places/{id}/reject` | Отклонение с проверкой рассмотренной версии (модератор/администратор) |

Параметр `q` необязательный. Нумерация страниц начинается с нуля; `size` должен быть от 1 до 100. Для защищённых операций нужен Bearer access-токен и соответствующая роль. Схемы запросов и ответов описаны в документации API.

## Документация API

- Swagger UI: `http://localhost:8080/swagger-ui.html`
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`

Эндпоинты документации публичны. Для остальных операций продолжают действовать правила аутентификации и авторизации.

## Локальный запуск через Docker Compose

### Требования

- Docker Engine/Desktop с плагином Docker Compose.
- Base64-кодированный секрет для подписи JWT, содержащий не менее 32 байт криптографического ключевого материала.

### Запуск

1. Скопируйте `.env.example` в файл `.env`.
2. Укажите локальный пароль базы данных в `POSTGRES_PASSWORD`.
3. Укажите `OMW_JWT_SECRET` — Base64-кодированный секрет. Его можно сгенерировать командой:

   ```sh
   openssl rand -base64 32
   ```

4. Запустите приложение и PostgreSQL:

   ```sh
   docker compose up --build -d
   ```

5. Просматривайте логи приложения:

   ```sh
   docker compose logs -f app
   ```

6. Откройте Swagger UI: `http://localhost:8080/swagger-ui.html`.

Порт API привязан к `127.0.0.1:8080`. PostgreSQL доступен сервисам внутри сети Compose по адресу `db:5432`; порт базы не опубликован на хосте. Миграции схемы применяются Flyway при запуске приложения.

Данные PostgreSQL хранятся в именованном Docker volume `onmyway_postgres_data` и сохраняются при пересоздании контейнеров. Чтобы остановить сервисы, не удаляя данные базы:

```sh
docker compose down
```

**Не запускайте `docker compose down -v`, если не собираетесь удалять локальный volume базы данных.**

Эта конфигурация Compose предназначена для локальной разработки, а не для production-развёртывания. Не коммитьте файл `.env` и реальные учётные данные в Git.

## Тесты

Запуск тестов через Maven:

```sh
mvn test
```

Набор включает unit-тесты, тесты API/контроллеров, правила доступа и интеграционные тесты PostgreSQL. Для части интеграционных тестов требуется Docker, поскольку они используют Testcontainers.

## Дальнейшее развитие

Возможные следующие этапы:

- дальнейшее end-to-end тестирование версионирования мест и конкурентных изменений;
- последовательное усиление аутентификации и авторизации;
- подготовка к production-развёртыванию: HTTPS, резервное копирование, мониторинг и эксплуатационные настройки.
