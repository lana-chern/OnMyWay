# OnMyWay

OnMyWay is a backend application for discovering and managing places in different cities. The project is implemented as a REST API using Java and Spring Boot, with authentication, JWT-based authorization, and role-based access control.

> **The project is under active development.** The current implementation represents an early stage of the product, and some features and production-oriented concerns are intentionally planned for later iterations.

## Features

The application is designed to provide:

- discovery of cities and places;
- public access to published places;
- creation and management of places by organizers;
- ownership-based access control for place management;
- user registration and authentication;
- JWT-based authorization and role-based access control;
- validation and consistent API error handling.

## Future Development

Planned improvements include:

- database migration management;
- more complete moderator and administrator permissions;
- place deletion and additional ownership rules;
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
- регистрации и аутентификации пользователей;
- авторизации на основе JWT и ролей;
- валидации входных данных и единообразной обработки ошибок API.

## Дальнейшее развитие

В планах:

- управление миграциями базы данных;
- более полная реализация прав модераторов и администраторов;
- удаление мест и дополнительные правила владения;
- улучшение процессов аутентификации и авторизации;
- документирование API с помощью OpenAPI/Swagger;
- пагинация, фильтрация и сортировка поиска мест;
- refresh-токены и механизм отзыва токенов;
- конфигурация развёртывания и production-наблюдаемость.
