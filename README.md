# 🚖 Smart Ride – Microservices-Based Cab Booking System

Spring Boot | Microservices | Docker | JWT | WebSocket | MySQL | Hibernate/JPA

## Architecture

```
Browser (Leaflet map + SockJS/STOMP)
        │
   nginx :3000  (static UI + API gateway / reverse proxy)
   ├── /api/users     → user-service     :8081  → user_db
   ├── /api/rides     → ride-service     :8082  → ride_db      (+ WebSocket /ws)
   └── /api/payments  → payment-service  :8083  → payment_db
                              │  REST call (forwards rider JWT)
                              └──────────► ride-service
                      MySQL 8 (one container, one database per service)
```

| Service | Responsibility |
|---|---|
| user-service | Register/login, BCrypt passwords, issues JWT (roles `RIDER`, `DRIVER`, `ADMIN`), user listing for admin |
| ride-service | Ride lifecycle `REQUESTED → ACCEPTED → STARTED → COMPLETED` (or `CANCELLED`), fare calculation (haversine), real-time tracking over STOMP/WebSocket |
| payment-service | Pays a completed ride; calls ride-service to verify ownership/status, simulated gateway, one payment per ride |

All services validate the same HS256 JWT (shared `JWT_SECRET`), stateless Spring Security, `@PreAuthorize` role checks.

**Real-time tracking:** driver posts GPS to `POST /api/rides/{id}/location` → ride-service broadcasts on
`/topic/rides/{id}/location`; status changes go to `/topic/rides/{id}/status`.
STOMP `CONNECT` requires the JWT, and `SUBSCRIBE` is allowed only for the ride's rider, its driver, or an admin.
Optimistic locking (`@Version`) prevents two drivers accepting the same ride.

## Run (Docker, PowerShell)

```powershell
docker compose up --build
```

Open http://localhost:3000. Demo admin: `admin@smartride.com` / `Admin@123`.

Stop and clean up:

```powershell
docker compose down
docker compose down -v   # also deletes the MySQL volume
```

## Demo flow
1. Register a **Rider** in one browser window and a **Driver** in another (private window).
2. Rider: click the map for pickup and drop, **Request ride**.
3. Driver: **Accept** → **Start trip** → **🚗 Drive trip** (simulates GPS; rider's map moves live) → **Complete**.
4. Rider: **Pay**. Admin sees users, rides and payments.

## REST API

| Method | Path | Role |
|---|---|---|
| POST | /api/users/register, /api/users/login | public |
| GET | /api/users/me | any |
| GET | /api/users | ADMIN |
| POST | /api/rides | RIDER |
| GET | /api/rides/my | RIDER, DRIVER |
| GET | /api/rides/available | DRIVER |
| GET | /api/rides/all | ADMIN |
| GET | /api/rides/{id} | rider/driver of the ride, ADMIN |
| POST | /api/rides/{id}/accept, /start, /complete | DRIVER |
| POST | /api/rides/{id}/cancel | RIDER, DRIVER |
| POST | /api/rides/{id}/location | DRIVER |
| POST | /api/payments | RIDER |
| GET | /api/payments/my | RIDER |
| GET | /api/payments | ADMIN |

Try it from PowerShell:

```powershell
$login = Invoke-RestMethod -Method Post -Uri http://localhost:3000/api/users/login -ContentType 'application/json' -Body (@{email='admin@smartride.com'; password='Admin@123'} | ConvertTo-Json)
Invoke-RestMethod -Uri http://localhost:3000/api/users -Headers @{Authorization = "Bearer $($login.token)"}
```

## Run services locally without Docker (PowerShell)
Requires JDK 17, Maven and a MySQL on `localhost:3306` (root/root), or set `DB_URL`, `DB_USER`, `DB_PASSWORD`.

```powershell
cd user-service;    mvn spring-boot:run     # new terminal each
cd ride-service;    mvn spring-boot:run
cd payment-service; mvn spring-boot:run
```
The UI needs nginx's proxying; simplest is to run only the frontend container: `docker compose up --build frontend` (with the three services running in Docker).

## Production notes
Change `JWT_SECRET` and DB passwords in `.env`; replace `ddl-auto: update` with Flyway migrations; add an API gateway such as Spring Cloud Gateway if you want service discovery; plug a real payment gateway into `PaymentService`.
