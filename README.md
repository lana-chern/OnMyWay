# OnMyWay

OnMyWay is a backend application for discovering and managing places in different cities. The project is implemented as a REST API using Java and Spring Boot, with authentication, JWT-based authorization, and role-based access control.

> The project is under active development. Some production-oriented concerns, such as database migrations and extended moderator/admin permissions, are intentionally planned for later iterations.

## Features

- City and place REST API
- Public access to published places
- Place creation by users with the `ORGANIZER` role
- Place updates restricted to the place owner
- User registration and login
- JWT access tokens
- Current-user endpoint
- Role-based authorization
- Bean validation and consistent `ProblemDetail` error responses
- Unit, repository, controller, and security integration tests

## Technology Stack

- **Java 25**
- **Spring Boot 4.1.1**
- Spring Web MVC
- Spring Data JPA / Hibernate
- Spring Security
- JSON Web Tokens with JJWT 0.13.0
- PostgreSQL for runtime
- H2 for tests
- Maven Wrapper
- GitHub Actions for continuous integration

## Domain Overview

The current domain model includes:

- **User** — application account with email, password hash, status, and one or more roles.
- **City** — city in which places are located.
- **Place** — a location associated with a city and an owner. Places have a publication status such as `DRAFT` or `PUBLISHED`.
- **PlacePhoto** — photo information associated with a place.
- **PlaceContact** — contact information associated with a place.
- **PlaceOpeningHours** — opening intervals associated with a place.

Supported user roles include:

- `USER`
- `ORGANIZER`
- `MODERATOR`
- `ADMIN`

At the current stage, creating and updating places requires the `ORGANIZER` role. An organizer can update only places they own.

## API Endpoints

### Authentication

| Method | Endpoint | Description | Access |
|---|---|---|---|
| `POST` | `/api/auth/register` | Register a new user | Public |
| `POST` | `/api/auth/login` | Authenticate and receive a JWT access token | Public |
| `GET` | `/api/auth/me` | Get the currently authenticated user | Authenticated |

Example registration request:

```http
POST /api/auth/register
Content-Type: application/json

{
  "email": "user@example.com",
  "password": "strong-password",
  "displayName": "Example User"
}
```

Example login request:

```http
POST /api/auth/login
Content-Type: application/json

{
  "email": "user@example.com",
  "password": "strong-password"
}
```

Use the returned access token in subsequent authenticated requests:

```http
Authorization: Bearer <access-token>
```

### Cities

| Method | Endpoint | Description | Access |
|---|---|---|---|
| `GET` | `/api/cities` | Get all cities | Public |
| `GET` | `/api/cities/{id}` | Get a city by ID | Public |

### Places

| Method | Endpoint | Description | Access |
|---|---|---|---|
| `GET` | `/api/places?cityId={cityId}` | Get published places for a city | Public |
| `GET` | `/api/places/{id}` | Get a place by ID | Public, subject to publication rules |
| `POST` | `/api/places` | Create a place in `DRAFT` status | `ORGANIZER` |
| `PUT` | `/api/places/{id}` | Update an owned place | Owner with `ORGANIZER` role |

The API currently keeps public reads separate from organizer write operations. More advanced ownership rules and moderator/admin workflows are planned for later development stages.

## Local Setup

### Requirements

Install the following tools:

- JDK 25
- Git
- PostgreSQL, or another PostgreSQL-compatible development environment

The project includes Maven Wrapper, so a separate Maven installation is not required.

### Environment Variables

The application reads database and JWT configuration from environment variables:

| Variable | Required | Description |
|---|---|---|
| `OMW_DB_URL` | Yes | JDBC URL of the PostgreSQL database |
| `OMW_DB_USERNAME` | Yes | Database username |
| `OMW_DB_PASSWORD` | Yes | Database password |
| `OMW_JWT_SECRET` | Yes | Base64-encoded HMAC signing key for JWTs |
| `OMW_JWT_EXPIRATION_MS` | No | JWT lifetime in milliseconds; defaults to `900000` (15 minutes) |

The JWT secret must be a valid Base64-encoded key of sufficient length for the selected HMAC algorithm. Do not commit real secrets to the repository.

Example environment configuration for a local development session:

```text
OMW_DB_URL=jdbc:postgresql://localhost:5432/onmyway
OMW_DB_USERNAME=postgres
OMW_DB_PASSWORD=your-password
OMW_JWT_SECRET=your-base64-encoded-secret
OMW_JWT_EXPIRATION_MS=900000
```

### Run the Application

On Linux or macOS:

```bash
./mvnw spring-boot:run
```

On Windows:

```powershell
.\mvnw.cmd spring-boot:run
```

The application starts with the default Spring Boot configuration unless overridden by environment variables or additional configuration.

## Build and Test

Run the complete verification lifecycle:

Linux/macOS:

```bash
./mvnw clean verify
```

Windows:

```powershell
.\mvnw.cmd clean verify
```

Run tests only:

```bash
./mvnw test
```

The test suite uses H2 and Spring Security test support, so the tests do not require a separate PostgreSQL instance.

## Project Structure

```text
src/
├── main/
│   ├── java/com/onmyway/
│   │   ├── api/            # Services and API DTOs
│   │   ├── auth/           # Authentication and JWT-related logic
│   │   ├── config/         # Spring Security and application configuration
│   │   ├── controllers/    # REST controllers
│   │   └── data/            # Entities and repositories
│   └── resources/
│       └── application.properties
└── test/
    └── java/com/onmyway/   # Unit, repository, controller, and security tests
```

The project is developed through small, focused changes. Each feature is intended to be introduced with automated tests and verified by the GitHub Actions workflow.

## Security Notes

- Passwords are stored as BCrypt hashes rather than plaintext values.
- Authentication is stateless and uses JWT access tokens.
- Invalid, expired, or otherwise unusable bearer tokens are rejected with HTTP `401 Unauthorized`.
- Access to organizer operations is enforced by Spring Security.
- Ownership checks are applied when an organizer updates a place.
- The JWT signing secret must be supplied through environment configuration.

## Current Limitations and Future Work

Planned improvements include:

- Database migration management for schema changes and existing production data
- More complete moderator and administrator permissions
- Place deletion and additional ownership rules
- Improved authentication error response consistency
- API documentation with OpenAPI/Swagger
- Pagination, filtering, and sorting for place searches
- Refresh tokens and token revocation strategy
- Deployment configuration and production observability

## License

This project currently does not specify a separate open-source license.

---

# Русская версия

## OnMyWay

OnMyWay — backend-приложение для поиска и управления местами в разных городах. Проект реализован в виде REST API на Java и Spring Boot и включает аутентификацию, авторизацию на основе JWT и разграничение доступа по ролям.

> Проект находится в активной разработке. Некоторые задачи, связанные с подготовкой к эксплуатации в production, например миграции базы данных и расширенные права модераторов и администраторов, запланированы на последующие этапы.

## Возможности

- REST API для городов и мест
- Публичный доступ к опубликованным местам
- Создание мест пользователями с ролью `ORGANIZER`
- Обновление мест только их владельцами
- Регистрация и вход пользователей
- JWT-токены доступа
- Получение информации о текущем пользователе
- Разграничение доступа по ролям
- Валидация входных данных и единообразные ответы об ошибках через `ProblemDetail`
- Модульные, репозиторные, контроллерные и интеграционные тесты безопасности

## Технологический стек

- **Java 25**
- **Spring Boot 4.1.1**
- Spring Web MVC
- Spring Data JPA / Hibernate
- Spring Security
- JSON Web Tokens с использованием JJWT 0.13.0
- PostgreSQL для запуска приложения
- H2 для тестов
- Maven Wrapper
- GitHub Actions для непрерывной интеграции

## Предметная область

Текущая модель предметной области включает:

- **User** — учётная запись пользователя с email, хешем пароля, статусом и одной или несколькими ролями.
- **City** — город, в котором находятся места.
- **Place** — место, связанное с городом и владельцем. У места есть статус публикации, например `DRAFT` или `PUBLISHED`.
- **PlacePhoto** — информация о фотографиях места.
- **PlaceContact** — контактная информация места.
- **PlaceOpeningHours** — интервалы времени работы места.

Поддерживаемые роли пользователей:

- `USER` — обычный пользователь
- `ORGANIZER` — организатор
- `MODERATOR` — модератор
- `ADMIN` — администратор

На текущем этапе для создания и обновления мест требуется роль `ORGANIZER`. Организатор может изменять только принадлежащие ему места.

## API-эндпоинты

### Аутентификация

| Метод | Эндпоинт | Описание | Доступ |
|---|---|---|---|
| `POST` | `/api/auth/register` | Регистрация нового пользователя | Публичный |
| `POST` | `/api/auth/login` | Аутентификация и получение JWT-токена | Публичный |
| `GET` | `/api/auth/me` | Получение текущего авторизованного пользователя | Требуется аутентификация |

Пример запроса регистрации:

```http
POST /api/auth/register
Content-Type: application/json

{
  "email": "user@example.com",
  "password": "strong-password",
  "displayName": "Example User"
}
```

Пример запроса входа:

```http
POST /api/auth/login
Content-Type: application/json

{
  "email": "user@example.com",
  "password": "strong-password"
}
```

Полученный токен необходимо передавать в последующих защищённых запросах:

```http
Authorization: Bearer <access-token>
```

### Города

| Метод | Эндпоинт | Описание | Доступ |
|---|---|---|---|
| `GET` | `/api/cities` | Получить список всех городов | Публичный |
| `GET` | `/api/cities/{id}` | Получить город по ID | Публичный |

### Места

| Метод | Эндпоинт | Описание | Доступ |
|---|---|---|---|
| `GET` | `/api/places?cityId={cityId}` | Получить опубликованные места города | Публичный |
| `GET` | `/api/places/{id}` | Получить место по ID | Публичный с учётом правил публикации |
| `POST` | `/api/places` | Создать место со статусом `DRAFT` | `ORGANIZER` |
| `PUT` | `/api/places/{id}` | Обновить принадлежащее пользователю место | Владелец с ролью `ORGANIZER` |

На текущем этапе публичные операции чтения отделены от операций записи для организаторов. Более сложные правила владения и сценарии работы модераторов и администраторов будут добавлены позднее.

## Локальная настройка

### Требования

Установите следующие инструменты:

- JDK 25
- Git
- PostgreSQL или совместимое окружение для разработки

В проект уже включён Maven Wrapper, поэтому отдельно устанавливать Maven не требуется.

### Переменные окружения

Приложение получает настройки базы данных и JWT из переменных окружения:

| Переменная | Обязательна | Описание |
|---|---|---|
| `OMW_DB_URL` | Да | JDBC URL базы данных PostgreSQL |
| `OMW_DB_USERNAME` | Да | Имя пользователя базы данных |
| `OMW_DB_PASSWORD` | Да | Пароль базы данных |
| `OMW_JWT_SECRET` | Да | Base64-кодированный ключ HMAC для подписи JWT |
| `OMW_JWT_EXPIRATION_MS` | Нет | Время жизни JWT в миллисекундах; по умолчанию `900000` (15 минут) |

JWT-секрет должен быть корректным Base64-кодированным ключом достаточной длины для выбранного алгоритма HMAC. Не добавляйте реальные секреты в репозиторий.

Пример настройки окружения для локальной разработки:

```text
OMW_DB_URL=jdbc:postgresql://localhost:5432/onmyway
OMW_DB_USERNAME=postgres
OMW_DB_PASSWORD=your-password
OMW_JWT_SECRET=your-base64-encoded-secret
OMW_JWT_EXPIRATION_MS=900000
```

### Запуск приложения

Linux или macOS:

```bash
./mvnw spring-boot:run
```

Windows:

```powershell
.\mvnw.cmd spring-boot:run
```

Приложение запускается с настройками Spring Boot по умолчанию, если они не переопределены переменными окружения или дополнительной конфигурацией.

## Сборка и тестирование

Полный цикл проверки:

Linux/macOS:

```bash
./mvnw clean verify
```

Windows:

```powershell
.\mvnw.cmd clean verify
```

Запуск только тестов:

```bash
./mvnw test
```

Тесты используют H2 и инструменты Spring Security Test, поэтому для их запуска не требуется отдельный экземпляр PostgreSQL.

## Структура проекта

```text
src/
├── main/
│   ├── java/com/onmyway/
│   │   ├── api/            # Сервисы и DTO API
│   │   ├── auth/           # Логика аутентификации и JWT
│   │   ├── config/         # Конфигурация Spring Security и приложения
│   │   ├── controllers/    # REST-контроллеры
│   │   └── data/           # Сущности и репозитории
│   └── resources/
│       └── application.properties
└── test/
    └── java/com/onmyway/   # Модульные, репозиторные, контроллерные и security-тесты
```

Разработка проекта ведётся небольшими изолированными изменениями. Каждая функциональность сопровождается автоматизированными тестами и проверяется через workflow GitHub Actions.

## Важные аспекты безопасности

- Пароли хранятся в виде BCrypt-хешей, а не в открытом виде.
- Аутентификация является stateless и использует JWT-токены доступа.
- Недействительные, просроченные и иным образом некорректные bearer-токены отклоняются с HTTP-статусом `401 Unauthorized`.
- Доступ к операциям организатора контролируется через Spring Security.
- При обновлении места выполняется проверка его владельца.
- JWT-секрет должен передаваться через настройки окружения.

## Текущие ограничения и дальнейшее развитие

В планах находятся:

- Управление миграциями базы данных и обработка существующих production-данных
- Более полная реализация прав модераторов и администраторов
- Удаление мест и дополнительные правила владения
- Улучшение единообразия ответов при ошибках аутентификации
- Документирование API с помощью OpenAPI/Swagger
- Пагинация, фильтрация и сортировка поиска мест
- Refresh-токены и стратегия отзыва токенов
- Конфигурация развёртывания и production-наблюдаемость

## Лицензия

В настоящее время для проекта не указана отдельная лицензия с открытым исходным кодом.
