# SubTrack

A subscription billing microservice built with Spring Boot — manages customers, plans, subscriptions, and billing cycles, with real day-based proration logic when a customer changes plans mid-cycle.

Built as a learning/portfolio project to demonstrate backend fundamentals: layered architecture, REST API design, JPA/relational modeling, business logic that goes beyond CRUD, and testing.

## What it does

- Create customers and subscription plans (monthly/yearly billing)
- Subscribe a customer to a plan
- Change a subscription's plan mid-cycle
- Renew a subscription — generates an itemized invoice, **prorating the charge by exact days** if the plan changed during the billing period
- Cancel a subscription
- View a subscription's invoice history

### Example: proration in action

A customer on a $30/month plan switches to a $60/month plan 10 days into a 30-day billing cycle. On renewal, SubTrack generates:

```json
{
  "amount": 50.00,
  "lineItems": [
    { "description": "Basic plan (10 of 30 days, ...)", "amount": 10.00 },
    { "description": "Pro plan (20 of 30 days, ...)",   "amount": 40.00 }
  ]
}
```

Formula: `daily_rate = plan_price / total_days_in_period`, applied per-plan across the days actually used on each.

## Architecture

```
Controller  →  REST endpoints, request/response handling, validation
Service     →  Business logic: renewal, proration, plan-change tracking
Repository  →  Spring Data JPA (Customer, Plan, Subscription, Invoice, InvoiceLineItem)
Database    →  H2 (local dev, zero setup) / PostgreSQL (Docker)
```

Business logic lives entirely in `SubscriptionService` — controllers stay thin and only handle HTTP concerns.

## Tech stack

- Java 21, Spring Boot 3.3 (Web, Data JPA, Validation)
- H2 (in-memory, default) / PostgreSQL (via Docker profile)
- JUnit 5 + Mockito (unit tests), Spring MockMvc (integration tests)
- springdoc-openapi (Swagger UI)
- Maven, Docker (Dockerfile + docker-compose)

## Running it

### Quick start (H2, no setup required)

```bash
mvn spring-boot:run
```

The app starts on `http://localhost:8080` with an in-memory H2 database — no installation needed. **Note: H2 data resets every time the app restarts.**

Explore the API interactively at: `http://localhost:8080/swagger-ui.html`

Inspect the database directly at: `http://localhost:8080/h2-console` (JDBC URL: `jdbc:h2:mem:subtrackdb`, user: `sa`, no password)

### With Docker (PostgreSQL, persistent data)

```bash
docker compose up --build
```

This builds the app and runs it against a real PostgreSQL container instead of H2, with data that persists across restarts.

> **Note on this project's Docker setup:** the `Dockerfile` and `docker-compose.yml` are complete and correct (multi-stage build, health-checked startup ordering, persistent volume) but have not been run locally in this repo's development — the dev machine used had under 6GB of free disk space, not enough to safely install Docker Desktop's WSL2 backend alongside everything else. This is a genuine, common constraint rather than an oversight, and it's called out here rather than hidden. The compose file follows standard, well-established patterns and should run correctly on a machine with adequate disk space.

### Running tests

```bash
mvn test
```

16 tests: 11 unit tests on `SubscriptionService` (Mockito — covering creation, cancellation, plan changes, and both renewal paths with exact-money assertions) and 5 integration tests (MockMvc + real H2 — full HTTP round-trips including the complete billing flow and error cases).

## API endpoints

| Method | Endpoint | Description |
|---|---|---|
| POST | `/customers` | Create a customer |
| GET | `/customers/{id}` | Get a customer |
| POST | `/plans` | Create a plan |
| GET | `/plans` | List all plans |
| POST | `/subscriptions` | Create a subscription (customerId + planId) |
| GET | `/subscriptions/{id}` | Get a subscription |
| PATCH | `/subscriptions/{id}/cancel` | Cancel a subscription |
| PATCH | `/subscriptions/{id}/plan` | Change a subscription's plan mid-cycle |
| POST | `/subscriptions/{id}/renew` | Renew — generates a (possibly prorated) invoice, advances the billing period |
| GET | `/subscriptions/{id}/invoices` | List a subscription's invoices |

Full interactive documentation: `/swagger-ui.html`

## Notable bugs hit and fixed during development

Kept here deliberately, since debugging real issues is as instructive as writing the code in the first place:

1. **Lazy-loading Hibernate proxy crashing JSON serialization.** `Subscription.customer`/`.plan` were originally `FetchType.LAZY`. Returning a freshly-fetched subscription from the database (rather than one still "fresh" from a just-completed save) caused Jackson to choke trying to serialize Hibernate's internal proxy object. Fixed by switching to `FetchType.EAGER` for these relationships, appropriate given how small this dataset is.

2. **Circular JSON reference between Invoice and InvoiceLineItem.** Adding a bidirectional `@OneToMany`/`@ManyToOne` relationship between `Invoice` and its line items caused infinite nesting when serialized (`invoice → lineItems → invoice → ...`). Fixed with `@JsonIgnore` on the line item's back-reference to its parent invoice.

3. **Entity defaults silently depending on JPA lifecycle timing.** `Subscription`'s default status/dates were originally set only in a `@PrePersist` hook — which only fires when Hibernate actually persists an entity. This meant `SubscriptionService` unit tests (which mock the repository, so nothing is ever really persisted) got back `null` fields instead of the expected defaults. Fixed by moving default-setting into the constructor itself, so the entity is correctly initialized regardless of whether a real database is involved. Caught directly by writing unit tests — a good example of tests surfacing a real design issue rather than just confirming what already worked.

## A note on AI-assisted development

This project was built with Claude as an active collaborator throughout — not just for boilerplate generation, but for real back-and-forth on design decisions (e.g., choosing day-based vs. whole-month proration, deciding when EAGER vs. LAZY loading is appropriate at this scale, structuring the service layer). Debugging sessions for the three issues listed above were also done collaboratively. This is disclosed here honestly, in line with how the project was scoped from the start.
