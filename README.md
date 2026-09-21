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
