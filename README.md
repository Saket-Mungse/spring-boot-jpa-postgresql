# HRMS Construction — Java Backend Developer Assignment

## Forked From
[bezkoder/spring-boot-jpa-postgresql](https://github.com/bezkoder/spring-boot-jpa-postgresql) — chosen for its clean Spring Boot 3 + JPA + PostgreSQL structure with minimal boilerplate, making it easy to extend without fighting existing code.

---

## Setup Instructions

### Prerequisites
- Java 17+
- Maven 3.9+
- Docker (for Redis)
- A Supabase account (free tier)

### 1. Clone the repo
```bash
git clone https://github.com/Saket-Mungse/spring-boot-jpa-postgresql.git
cd spring-boot-jpa-postgresql
```

### 2. Supabase setup
1. Go to https://supabase.com and create a new project
2. Go to **Connect** → **Direct** → **Pooler settings**
3. Copy the JDBC URL (Session Pooler, port 5432)
4. Update `src/main/resources/application.properties`:
```properties
spring.datasource.url=jdbc:postgresql://aws-1-ap-southeast-1.pooler.supabase.com:5432/postgres
spring.datasource.username=postgres.YOUR_PROJECT_ID
spring.datasource.password=YOUR_PASSWORD
```

### 3. Start Redis
```bash
docker run -d --name redis-hrms -p 6379:6379 redis:latest
```

### 4. Run the app
```bash
mvn spring-boot:run
```
App starts on `http://localhost:8080`. Hibernate auto-creates all tables on first run.

---

## API Endpoints

### Workers
| Method | URL | Description |
|--------|-----|-------------|
| POST | /api/workers | Create worker |
| GET | /api/workers | List all workers |

### Sites
| Method | URL | Description |
|--------|-----|-------------|
| POST | /api/sites | Create site |
| GET | /api/sites | List all sites |

### Attendance
| Method | URL | Description |
|--------|-----|-------------|
| POST | /api/attendance/clock-in | Clock in a worker |
| POST | /api/attendance/clock-out | Clock out a worker |
| GET | /api/attendance/active | Active workers (Redis) |
| GET | /api/attendance/log?workerId=1&from=...&to=... | Paginated history |

### Overtime
| Method | URL | Description |
|--------|-----|-------------|
| GET | /api/overtime/summary/{workerId}?month=2026-05 | Monthly summary |
| POST | /api/overtime/settle/{workerId}?month=2026-05 | Settle overtime |

---

## AI Tools Used
- **Claude (Anthropic)** — Used for entity design review, business logic for overtime calculation, ticket root-cause analysis, and Redis caching strategy
- **GitHub Copilot** — Used for boilerplate service/repository generation and repetitive getter/setter code

---

## Design Decisions

### Schema
- `BigDecimal` for all monetary values (dailyWageRate, amount) — floating point errors in payroll are real bugs
- Enum stored as `STRING` not `ORDINAL` — adding enum values later won't silently corrupt existing data
- Partial unique index on `(worker_id) WHERE clock_out IS NULL` prevents double clock-in at DB level, not just in Java
- DB-level CHECK constraints on designation and settlement_status enums

### Caching
- Per-worker Redis keys (`active_workers:{id}`) with 16h TTL instead of a single hash — individual TTL per worker, clean expiry
- `GET /active` reads exclusively from Redis — zero DB hits for the most frequent supervisor query
- `NoOpCacheManager` fallback — app starts and serves all requests even when Redis is completely offline
- `CacheErrorHandler` swallows Redis errors mid-request silently

### Transactions
- Settlement wraps entire batch in one `@Transactional` method — all 22 entries settle together or none do
- SMS notification uses `@TransactionalEventListener(AFTER_COMMIT)` — fires only after DB commit succeeds, never inside the transaction
- External API calls moved outside `@Transactional` — DB connections not held hostage while waiting on third-party APIs

### Connection Pooling
- `application-staging.yml` has HikariCP `max-lifetime: 270000` (shorter than Supabase's 300s idle timeout)
- `keepalive-time: 60000` pings connections every 60s to prevent silent drops
- Uses Session Pooler URL (PgBouncer) not direct connection

### Things I'd do differently with more time
- Add JWT authentication
- Write unit tests for overtime calculation edge cases (60h cap, 1.5x vs 2x boundary)
- Move Supabase password fully to environment variables using Spring Cloud Config
- Add Swagger/OpenAPI documentation
- Add a partial unique index migration script instead of relying on Hibernate ddl-auto

---

## Ticket Summary

| Ticket | Problem | Fix |
|--------|---------|-----|
| LF-201 | CORS blocked frontend | SecurityFilterChain + CorsConfigurationSource bean + env-specific origins in yml |
| LF-202 | App crashes when Redis down | CacheErrorHandler + connect timeout + NoOpCacheManager fallback |
| LF-203 | N+1 queries + no pagination | JOIN FETCH in repository + Pageable through all layers + PagedResponse |
| LF-204 | Partial settlement + premature SMS | Atomic @Transactional + @TransactionalEventListener(AFTER_COMMIT) |
| LF-205 | Connection pool exhaustion on staging | HikariCP tuning in staging profile + external API call outside transaction |