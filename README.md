# OnMyWay

OnMyWay is a backend application for discovering and managing places in different cities. The project is implemented as a REST API using Java and Spring Boot, with authentication, JWT-based authorization, and role-based access control.

> **The project is under active development.** The current implementation represents an early stage of the product, and some features and production-oriented concerns are intentionally planned for later iterations.

## Features

The application is designed to provide:

- discovery of cities and places;
- public access to published places;
- creation and management of places by organizers;
- ownership-based access control for place management;
- submission of draft or rejected places for moderation;
- a moderation queue with publish/reject actions for moderators and administrators;
- soft deletion of places through the `ARCHIVED` status;
- administrator access to manage places across owners;
- user registration and authentication;
- JWT-based authorization and role-based access control;
- validation and consistent API error handling.

## Place moderation workflow

- Newly created places start in `DRAFT`.
- An organizer can submit their own `DRAFT` or `REJECTED` place for moderation, changing its status to `PENDING_MODERATION`.
- Moderators and administrators can list pending places and publish or reject them.
- Only places in `PENDING_MODERATION` can be published or rejected.
- A place rejected by a moderator can be updated by its owner and submitted again.
- Public place endpoints return only `PUBLISHED` places.
- Moderators can edit non-archived places but cannot archive them through the place deletion endpoint; organizers can archive their own places, and administrators can archive any place.

## API documentation

Interactive Swagger UI: `http://localhost:8080/swagger-ui.html`  
OpenAPI specification: `http://localhost:8080/v3/api-docs`

The OpenAPI document is generated from the Spring MVC controllers and includes JWT bearer authentication metadata. Documentation endpoints are public; protected API operations still enforce the existing role-based security rules.

## Local Docker deployment

Requirements: Docker Engine/Desktop with the Docker Compose plugin.

1. Copy `.env.example` to `.env`.
2. Set `POSTGRES_PASSWORD` to a local database password.
3. Set `OMW_JWT_SECRET` to a Base64-encoded cryptographic key of at least 32 bytes. Generate one with `openssl rand -base64 32`. Do not commit `.env`.
4. Start the stack: `docker compose up --build -d`.
5. Follow startup logs: `docker compose logs -f app`.
6. Open Swagger UI at `http://localhost:8080/swagger-ui.html`.

The API is published on `127.0.0.1:8080`. PostgreSQL is reachable only by services on the Compose network at `db:5432`; its port is not published on the host. This keeps the database inaccessible directly from the host unless you explicitly add a port mapping. PostgreSQL data is stored in the named Docker volume `onmyway_postgres_data` and survives container recreation. To stop the stack without deleting data, run `docker compose down`. **Do not run `docker compose down -v` unless you intend to delete the local database volume.**

To rebuild after code changes, run `docker compose up --build -d`. This local setup is intended for development and is not a production-hardened VPS configuration.

## Future Development

Planned improvements include:

- moderator permissions for other product areas and a fuller moderation workflow;
- further authentication and authorization hardening;
- VPS deployment, HTTPS, backups, and production observability.

---

# Русская версия

# OnMyWay

OnMyWay — backend-приложение для поиска и управления местами в разных городах. Проект реализован в виде REST API на Java и Spring Boot и включает аутентификацию, авторизацию на основе JWT и разграничение доступа по ролям.

> **Проект находится в активной разработке.** Текущая реализация представляет собой ранний этап продукта, а некоторые функции и задачи, связанные с подготовкой к эксплуатации в production, запланированы на последующие этапы.

## Возможности

Приложение предназначено для:

- поиска городов и мест;
- публичного доступа к опубликованным местам;
- создания и управления местами организаторами;
- разграничения доступа к управлению местами на основе владения;
- отправки черновиков и отклонённых мест на модерацию;
- очереди модерации с возможностью публикации и отклонения мест модераторами и администраторами;
- мягкого удаления мест через статус `ARCHIVED`;
- управления местами разных владельцев администраторами;
- регистрации и аутентификации пользователей;
- авторизации на основе JWT и ролей;
- валидации входных данных и единообразной обработки ошибок API.

## Процесс модерации мест

- Новое место создаётся со статусом `DRAFT`.
- Организатор может отправить своё место со статусом `DRAFT` или `REJECTED` на модерацию. Статус изменится на `PENDING_MODERATION`.
- Модераторы и администраторы могут просматривать очередь ожидающих модерации мест, публиковать их или отклонять.
- Публиковать и отклонять можно только места со статусом `PENDING_MODERATION`.
- Владелец может исправить отклонённое место и отправить его на модерацию повторно.
- Публичные эндпоинты возвращают только места со статусом `PUBLISHED`.
- Модераторы могут редактировать неархивированные места, но не могут архивировать их через эндпоинт удаления. Организатор может архивировать собственные места, администратор — любые.

## Локальный запуск через Docker

Требования: установленный Docker Engine/Desktop и плагин Docker Compose.

1. Скопируйте `.env.example` в файл `.env`.
2. Задайте `POSTGRES_PASSWORD` — пароль локальной базы данных.
3. Задайте `OMW_JWT_SECRET` — криптографический ключ в Base64 длиной не менее 32 байт. Его можно сгенерировать командой `openssl rand -base64 32`. Не добавляйте `.env` в Git.
4. Запустите приложение и базу: `docker compose up --build -d`.
5. Посмотрите логи приложения: `docker compose logs -f app`.
6. Откройте Swagger UI: `http://localhost:8080/swagger-ui.html`.

API опубликован на `127.0.0.1:8080`. PostgreSQL доступен только сервисам внутри сети Compose по адресу `db:5432`; порт базы не опубликован на хост-машине. Поэтому напрямую подключиться к базе с компьютера нельзя, пока явно не добавлена публикация порта. Данные PostgreSQL хранятся в именованном Docker volume `onmyway_postgres_data` и сохраняются при пересоздании контейнеров. Для остановки без удаления данных выполните `docker compose down`. **Не используйте `docker compose down -v`, если не хотите удалить локальную базу.**

После изменений кода пересоберите приложение командой `docker compose up --build -d`. Эта конфигурация предназначена для локальной разработки, а не для production-развёртывания на VPS.

## Дальнейшее развитие

В планах:

- права модераторов для других частей продукта и дальнейшее развитие процесса модерации;
- улучшение процессов аутентификации и авторизации;
- документирование API с помощью OpenAPI/Swagger;
- пагинация, фильтрация и сортировка поиска мест;
- дальнейшее усиление аутентификации и авторизации;
- развёртывание на VPS, HTTPS, резервное копирование и production-наблюдаемость.
