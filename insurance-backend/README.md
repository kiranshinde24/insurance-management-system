# Insurance Policy Management System — Java Backend

## New in this version
Six production-style features have been added on top of the original CRUD backend:

1. **Premium Calculation Engine** — `POST /api/policy/calculate-premium` and automatic calculation
   on policy create/update. Formula: `(sumAssured/1000) x baseRate x ageFactor x riskFactor x termYears`.
   `baseRate` / `riskFactor` live on `Policy_Type`; age is derived from the customer's DOB.
   Pass `"autoCalculatePremium": true` (or simply omit `"premium"`) in the policy payload to
   have the backend compute it; pass a positive `"premium"` explicitly to override.
2. **Policy Renewal & Expiry Scheduler** — `PolicyExpiryScheduler` runs daily
   (`app.scheduler.expiry-check-cron` in `application.properties`, default 7 AM) to mark expired
   policies and email renewal reminders 30 days out. Trigger it on demand with
   `POST /api/admin/policies/run-expiry-check` (handy for demos).
3. **PDF Document Generation** — Apache PDFBox generates policy certificates
   (`GET /api/customer/policies/{id}/certificate`, `GET /api/admin/policies/{id}/certificate`)
   and payment receipts (`GET /api/customer/payments/{id}/receipt`, `GET /api/admin/payments/{id}/receipt`).
4. **Email Notifications** — `EmailService` sends policy-issued, claim-approved/rejected,
   payment-received, and renewal-reminder emails via Gmail SMTP (Spring Mail). Disabled by
   default (`app.mail.enabled=false`) so missing credentials never break the app — see Setup below.
5. **Search, Filter & Pagination** — `/api/admin/policies`, `/api/admin/claims`, and
   `/api/admin/customers` now accept optional `page` & `size` query params, returning a
   `PageResponse { content, page, size, totalElements, totalPages }`. Omit both params to get
   the original plain list (fully backward compatible).
6. **Real File Storage for Claim Attachments** — `POST /api/customer/claims` now also accepts
   `multipart/form-data` (fields: `polId`, `amount`, `date`, `description`, `file`). The file is
   saved to `uploads/claims/` on disk and served back at `/uploads/claims/<name>`, instead of
   being stored as a base64 blob in the database. The original JSON/base64 body still works
   for backward compatibility.

### Setup for the new features
- **Database**: run the updated `db-migration.sql` — it now also adds `Sum_Assured` to `Policy`
  and `Base_Rate` / `Risk_Factor` to `Policy_Type`. (With `ddl-auto=update`, Hibernate will also
  add these columns automatically on first run, but running the script keeps things explicit.)
- **Email**: create a Gmail **App Password** (requires 2-Step Verification) at
  https://myaccount.google.com/apppasswords, then export `MAIL_USERNAME` / `MAIL_PASSWORD` /
  `MAIL_ENABLED=true` as environment variables (see "Configure environment" below) — never put
  real credentials directly in a properties file that gets committed.
- **File uploads**: no setup needed — files are written under `./uploads` relative to where the
  app runs. `spring.servlet.multipart.max-file-size` caps uploads at 10MB (adjust as needed).
- **PDFs & scheduler**: no setup needed — both work out of the box with the existing dependencies.

---

## Tech Stack
- **Java 17** + **Spring Boot 3.2**
- **Spring Security** + **JWT** (stateless auth)
- **Spring Data JPA** + **MySQL**
- **Lombok** (boilerplate reduction)
- **Maven** (build tool)

---

## Testing

The project has automated unit and integration tests under `src/test/java`.

**Unit tests** (JUnit 5 + Mockito, `src/test/java/com/insurance/service/`) mock every repository /
collaborator and exercise each service's business logic in isolation:
- `PremiumCalculationServiceTest` — the premium formula, age bands, term rounding, validation
- `AuthServiceTest` — admin/agent/customer login, registration, refresh-token rotation, logout
- `CustomerServiceTest`, `PolicyServiceTest`, `ClaimServiceTest`, `PolicyTypeServiceTest`,
  `AgentServiceTest`, `RefreshTokenServiceTest` — CRUD flows, validation errors, and edge cases
  (not-found, conflict, forbidden) for each domain

**Integration tests** (`@SpringBootTest`, `src/test/java/com/insurance/integration/`) boot the
full Spring context — real Spring Security filter chain, real JWT issuance, real BCrypt hashing —
against an in-memory H2 database (see `src/test/resources/application-test.properties`) instead
of the real MySQL instance:
- `AuthControllerIntegrationTest` — register → login → refresh, duplicate username, wrong password
- `PolicyTypeControllerIntegrationTest` — JWT-gated access (401 without/invalid token),
  create → list → delete, validation and not-found responses

Run everything with:
```bash
mvn test
```
Run a single test class:
```bash
mvn test -Dtest=PremiumCalculationServiceTest
```

---

## Project Structure

```
src/main/java/com/insurance/
├── InsuranceApplication.java          ← Main entry point
├── config/
│   └── SecurityConfig.java            ← CORS + JWT filter chain
├── security/
│   ├── JwtUtil.java                   ← Token generation & validation
│   └── JwtFilter.java                 ← Request filter
├── model/                             ← JPA entities (DB tables)
│   ├── Customer.java
│   ├── Agent.java
│   ├── Policy.java
│   ├── PolicyType.java
│   ├── Payment.java
│   └── Claim.java
├── dto/                               ← Data Transfer Objects
│   ├── AuthDTO.java
│   ├── CustomerDTO.java
│   ├── AgentDTO.java
│   ├── PolicyDTO.java
│   ├── PaymentDTO.java
│   ├── ClaimDTO.java
│   └── DashboardDTO.java
├── repository/                        ← JPA repositories (DB queries)
│   ├── CustomerRepository.java
│   ├── AgentRepository.java
│   ├── PolicyTypeRepository.java
│   ├── PolicyRepository.java
│   ├── PaymentRepository.java
│   └── ClaimRepository.java
├── service/                           ← Business logic
│   ├── AuthService.java
│   ├── CustomerService.java
│   ├── AgentService.java
│   ├── PolicyTypeService.java
│   ├── PolicyService.java
│   ├── PaymentService.java
│   ├── ClaimService.java
│   └── DashboardService.java
└── controller/                        ← REST API endpoints
    ├── AuthController.java
    ├── CustomerController.java
    ├── AgentController.java
    ├── PolicyTypeController.java
    ├── PolicyController.java
    ├── PaymentController.java
    ├── ClaimController.java
    └── DashboardController.java
```

---

## Setup Steps

### 1. Database Setup
```sql
-- Run your original InsuranceDB.sql first, then:
source src/main/resources/db-migration.sql
```

### 2. Configure environment (dev)
Config now lives in environment-specific files — nothing sensitive is committed to the repo.
For local development, `application-dev.properties` already has sane fallbacks for everything
except your DB password, so just export what you need before running:
```bash
export DB_PASSWORD=your-local-mysql-password
# optional — only needed if you want real emails sent locally:
export MAIL_USERNAME=you@gmail.com
export MAIL_PASSWORD=your-gmail-app-password
export MAIL_ENABLED=true
```
See `src/main/resources/application-dev.properties` for the full list of overridable variables
(`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET`, `CORS_ALLOWED_ORIGINS`, `MAIL_*`).

For production, activate the `prod` profile (`SPRING_PROFILES_ACTIVE=prod`) and set every
variable listed in `application-prod.properties` — it has **no fallback defaults**, so the app
fails fast at startup rather than silently running with a missing/blank secret.

### 3. Build and Run
```bash
mvn clean install
mvn spring-boot:run
```
Server starts at: **http://localhost:8080** (profile defaults to `dev`; override with
`SPRING_PROFILES_ACTIVE=prod` for a production run).

---

## Logging

Structured logging via SLF4J + Logback (see `src/main/resources/logback-spring.xml`):
- **dev/test**: human-readable, colored console output, `DEBUG` for `com.insurance`.
- **prod**: JSON logs to stdout (ready for ELK/CloudWatch/Datadog-style aggregators) plus a
  size/time-rotated file under `logs/`.

Every HTTP request gets a short `requestId` (via `RequestLoggingFilter`), echoed back as an
`X-Request-Id` response header and attached to every log line emitted while handling that
request (MDC), so you can grep/filter logs for one request across controllers, services, and
the exception handler. One line is also logged per request/response with method, path, status,
and duration.

Sensitive values (passwords, JWTs, refresh tokens) are never logged — only identifiers like
username, id, and role.

---

## API Reference

All protected endpoints require:
```
Authorization: Bearer <JWT_TOKEN>
```

---

### AUTH

| Method | Endpoint | Description | Auth |
|--------|----------|-------------|------|
| POST | `/api/auth/login` | Login (all roles) | Public |
| POST | `/api/auth/register` | Customer self-registration | Public |

**Login Request:**
```json
{ "username": "admin", "password": "admin@123" }
```
**Login Response:**
```json
{
  "token": "eyJ...",
  "role": "admin",
  "id": 1000,
  "name": "Admin",
  "username": "admin"
}
```

**Register Request:**
```json
{
  "fullname": "John Doe",
  "email": "john@gmail.com",
  "username": "johndoe",
  "password": "John@123",
  "phone": "9876543210",
  "city": "Mumbai",
  "dob": "1995-06-15",
  "pan": "ABCDE1234F",
  "agentId": 1
}
```

---

### ADMIN ENDPOINTS

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/admin/dashboard` | Admin dashboard stats |
| GET | `/api/admin/customers?search=` | List/search customers |
| POST | `/api/admin/customers` | Add customer |
| GET | `/api/admin/agents` | List agents |
| POST | `/api/admin/agents` | Add agent |
| PUT | `/api/admin/agents/{id}` | Edit agent |
| DELETE | `/api/admin/agents/{id}` | Delete agent |
| GET | `/api/admin/policies?search=&status=` | List/filter policies |
| POST | `/api/admin/policies` | Add policy |
| PUT | `/api/admin/policies/{id}` | Edit policy |
| DELETE | `/api/admin/policies/{id}` | Delete policy |
| GET | `/api/admin/payments?mode=` | List/filter payments |
| POST | `/api/admin/payments` | Record payment |
| GET | `/api/admin/claims?status=` | List/filter claims |
| GET | `/api/admin/claims/{id}` | Claim detail |
| PUT | `/api/admin/claims/{id}/approve` | Approve claim |
| PUT | `/api/admin/claims/{id}/reject` | Reject claim |

**Approve Claim body:**
```json
{ "approvedAmount": 5000.00 }
```

---

### POLICY TYPES (Admin/Authenticated)

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/policy-types` | List all types |
| POST | `/api/policy-types` | Add type (admin) |
| PUT | `/api/policy-types/{id}` | Edit type (admin) |
| DELETE | `/api/policy-types/{id}` | Delete type (admin) |

---

### CUSTOMER ENDPOINTS

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/customer/dashboard` | Customer dashboard |
| GET | `/api/customer/profile` | Get own profile |
| PUT | `/api/customer/profile` | Update profile |
| GET | `/api/customer/policies` | My policies |
| GET | `/api/customer/payments` | My payments |
| POST | `/api/customer/payments` | Make payment |
| GET | `/api/customer/claims` | My claims |
| POST | `/api/customer/claims` | Raise a claim |

**Raise Claim body:**
```json
{
  "polId": 1,
  "amount": 8000,
  "date": "2025-06-01",
  "description": "Hospitalization claim",
  "attachment": "base64encodeddata...",
  "attachmentName": "receipt.pdf",
  "attachmentType": "application/pdf"
}
```

---

### AGENT ENDPOINTS

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/agent/dashboard` | Agent dashboard |
| GET | `/api/agent/customers` | My customers |
| GET | `/api/agent/policies` | My policies |
| POST | `/api/agent/policies` | Create policy |
| GET | `/api/agent/policies/next-number` | Generate next policy number |
| GET | `/api/agent/payments` | Payments on my policies |
| GET | `/api/agent/claims` | Claims on my policies |

---

### PUBLIC

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/agents/list` | Agent list for registration dropdown |

---

## Roles Summary

| Role | Access |
|------|--------|
| `admin` | Full access to everything |
| `agent` | Own policies, customers who selected them, commissions |
| `customer` | Own policies, own payments, own claims, own profile |

---

## Connecting Frontend to Backend

Replace all `localStorage` calls in your frontend with `fetch()` API calls.

**Example — Login:**
```javascript
async function doLogin() {
  const res = await fetch('http://localhost:8080/api/auth/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password })
  });
  const data = await res.json();
  if (data.token) {
    localStorage.setItem('token', data.token);
    localStorage.setItem('loggedInUser', JSON.stringify(data));
    redirectByRole(data.role);
  }
}
```

**Example — Authenticated request:**
```javascript
async function loadPolicies() {
  const token = localStorage.getItem('token');
  const res = await fetch('http://localhost:8080/api/customer/policies', {
    headers: { 'Authorization': 'Bearer ' + token }
  });
  return await res.json();
}
```

---

## Default Admin Credentials
```
Username: admin
Password: admin@123
```
