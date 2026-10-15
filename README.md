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

## Future Development

Planned improvements include:

- moderator permissions for other product areas and a fuller moderation workflow;
- improved authentication and authorization flows;
- API documentation with OpenAPI/Swagger;
- pagination, filtering, and sorting for place searches;
- refresh tokens and token revocation;
- deployment configuration and production observability.

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

## Дальнейшее развитие

В планах:

- права модераторов для других частей продукта и дальнейшее развитие процесса модерации;
- улучшение процессов аутентификации и авторизации;
- документирование API с помощью OpenAPI/Swagger;
- пагинация, фильтрация и сортировка поиска мест;
- refresh-токены и механизм отзыва токенов;
- конфигурация развёртывания и production-наблюдаемость.
