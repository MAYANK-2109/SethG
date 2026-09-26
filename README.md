# Seth G ♻️

> **डिजिटल कबाड़ी वाला App** — A digital earnings tracker for informal waste collectors (kabadiwalas)

[![SIH 2026](https://img.shields.io/badge/SIH-2026-green)](https://www.sih.gov.in/)
[![Android](https://img.shields.io/badge/Android-API%2023%2B-brightgreen?logo=android)](https://developer.android.com)
[![Node.js](https://img.shields.io/badge/Node.js-18%2B-green?logo=node.js)](https://nodejs.org)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-15%2B-blue?logo=postgresql)](https://postgresql.org)

---

## Architecture Decisions

| Concern | Choice | Reason |
|---------|--------|--------|
| Backend framework | **Node.js + Express** | Lightweight, fast startup, vast ecosystem, ideal for REST + JWT |
| Database | **PostgreSQL** | Structured relational schema for users↔earnings, strong ACID guarantees, better analytics queries |
| Android UI | **Jetpack Compose** | Declarative, less boilerplate, Material 3 built-in |
| DI | **Hilt** | Official Android DI, KSP-powered, minimal runtime overhead |
| Offline cache | **Room** | SQLite-backed, Flow integration, stable |
| Auth storage | **EncryptedSharedPreferences** | AES-256-GCM, backed by Android Keystore |

---

## Project Structure

```
SethG/
├── android/                   # Android Kotlin/Compose app
│   ├── app/src/main/
│   │   ├── java/com/sethg/app/
│   │   │   ├── data/
│   │   │   │   ├── local/          # Room DB, DataStore, SecureTokenStore
│   │   │   │   ├── remote/         # Retrofit service, TokenAuthenticator
│   │   │   │   └── repository/     # Repositories with cache fallback
│   │   │   ├── di/                 # Hilt modules
│   │   │   ├── domain/model/       # Domain models, Result sealed class
│   │   │   └── ui/
│   │   │       ├── navigation/     # NavHost + Screen sealed class
│   │   │       ├── screen/         # Language, Login, Register, Main screens
│   │   │       ├── theme/          # Material3 theme, typography, colors
│   │   │       └── viewmodel/      # Language, Auth, Dashboard, Profile VMs
│   │   └── res/
│   │       └── values{,-en,-hi,-mr,-te}/strings.xml
│   └── build.gradle.kts
│
├── backend/                   # Node.js + Express API
│   └── src/
│       ├── controllers/       # authController, userController, earningsController
│       ├── db/                # pool.js, migrate.js (schema + migration)
│       ├── middleware/        # authenticate.js (JWT), errorHandler.js
│       └── routes/            # auth.js, user.js, earnings.js
│
└── README.md
```

---

## Backend Setup

### Prerequisites
- Node.js ≥ 18
- PostgreSQL ≥ 15

### 1. Install dependencies

```bash
cd backend
npm install
```

### 2. Configure environment

```bash
cp .env.example .env
# Edit .env with your PostgreSQL credentials and JWT secrets
```

```env
NODE_ENV=development
PORT=3000
DB_HOST=localhost
DB_PORT=5432
DB_NAME=sethg_db
DB_USER=postgres
DB_PASSWORD=your_password
JWT_ACCESS_SECRET=change_this_in_production
JWT_REFRESH_SECRET=change_this_too
JWT_ACCESS_EXPIRES_IN=15m
JWT_REFRESH_EXPIRES_IN=30d
```

### 3. Create database

```bash
psql -U postgres -c "CREATE DATABASE sethg_db;"
```

### 4. Run migrations

```bash
npm run migrate
```

This creates tables: `users`, `refresh_tokens`, `earnings`.

### 5. Start development server

```bash
npm run dev
```

API will be available at `http://localhost:3000`

### API Endpoints

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| POST | `/auth/register` | ❌ | Register new user |
| POST | `/auth/login` | ❌ | Login, get tokens |
| POST | `/auth/refresh` | ❌ | Refresh access token |
| POST | `/auth/logout` | ❌ | Revoke refresh token |
| GET | `/user/profile` | ✅ | Get current user profile |
| PUT | `/user/profile` | ✅ | Update profile / change password |
| GET | `/earnings/today` | ✅ | Today's earnings summary |
| GET | `/earnings/weekly` | ✅ | This week's earnings + daily breakdown |
| GET | `/earnings/monthly` | ✅ | This month's earnings + weekly breakdown |
| POST | `/earnings` | ✅ | Add a new earning entry |
| GET | `/health` | ❌ | Health check |

### Example: Register

```bash
curl -X POST http://localhost:3000/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Ramesh Kumar",
    "phone": "+919876543210",
    "password": "secret123",
    "confirmPassword": "secret123",
    "language": "hi"
  }'
```

### Example: Add Earning (demo seeding)

```bash
curl -X POST http://localhost:3000/earnings \
  -H "Authorization: Bearer <access_token>" \
  -H "Content-Type: application/json" \
  -d '{"amount": 250.50, "material": "plastic", "weight_kg": 8.5}'
```

---

## Android App Setup

### Prerequisites
- Android Studio Ladybug (2024.2) or later
- JDK 17
- Android device/emulator with API 23+

### 1. Open the project

```
Android Studio → Open → select android/ folder
```

### 2. Configure the backend URL

Edit `android/app/build.gradle.kts`:

```kotlin
buildConfigField("String", "BASE_URL", "\"http://YOUR_SERVER_IP:3000/\"")
```

> For emulator connecting to localhost: use `10.0.2.2` instead of `localhost`
> For physical device: use your computer's local IP (e.g. `192.168.1.5`)

### 3. Build and run

```
Android Studio → Run → Select device → ▶
```

Or via command line:
```bash
cd android
./gradlew installDebug
```

### App Flow

```
First launch:
  Language Selection → Login / Register → Dashboard

Subsequent launches:
  Dashboard (if token valid) or Login (if token expired)
```

---

## Database Schema

```sql
-- Users
CREATE TABLE users (
  id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  name          VARCHAR(120) NOT NULL,
  phone         VARCHAR(20) UNIQUE,
  email         VARCHAR(254) UNIQUE,
  password_hash TEXT NOT NULL,
  photo_url     TEXT,
  language      VARCHAR(10) DEFAULT 'en',
  created_at    TIMESTAMPTZ DEFAULT NOW(),
  updated_at    TIMESTAMPTZ DEFAULT NOW()
);

-- JWT Refresh Tokens (hashed for security)
CREATE TABLE refresh_tokens (
  id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id     UUID REFERENCES users(id) ON DELETE CASCADE,
  token_hash  TEXT UNIQUE NOT NULL,
  expires_at  TIMESTAMPTZ NOT NULL,
  created_at  TIMESTAMPTZ DEFAULT NOW()
);

-- Earnings / Transactions
CREATE TABLE earnings (
  id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id     UUID REFERENCES users(id) ON DELETE CASCADE,
  amount      NUMERIC(12,2) NOT NULL CHECK (amount >= 0),
  material    VARCHAR(80),       -- e.g. "plastic", "metal", "paper"
  weight_kg   NUMERIC(8,3),
  note        TEXT,
  earned_at   TIMESTAMPTZ DEFAULT NOW(),
  created_at  TIMESTAMPTZ DEFAULT NOW()
);
```

---

## Security Features

| Feature | Implementation |
|---------|---------------|
| Password storage | bcrypt with cost factor 12 |
| JWT signing | HS256, separate secrets for access/refresh |
| Token storage (Android) | EncryptedSharedPreferences (AES-256-GCM) |
| Refresh token DB | SHA-256 hashed — plain token never stored |
| Token rotation | New refresh token on every refresh |
| Input validation | express-validator on every endpoint |
| Secrets | Environment variables only, never committed |

---

## Offline Support

- Room database caches the last-fetched earnings for all 3 periods
- On network failure, repositories fall back to cached data
- Dashboard shows an **offline badge** (wifi-off icon + amber label)
- Flow-based observation ensures UI updates automatically when cache changes

---

## Low-Memory Device Optimizations

- `minSdk = 23` covers ~97% of active devices
- `android:largeHeap="false"` — avoids memory hogging
- R8 + ProGuard minification enabled in release builds (`isMinifyEnabled = true`, `isShrinkResources = true`)
- Coil for memory-efficient image loading
- System font (no bundled font files)
- Conservative OkHttp timeouts (30s) for slow networks
- Small Room DB footprint (only summary data cached, not full transaction history)

---

## Localization

| Language | Code | File |
|----------|------|------|
| Hindi (default) | `hi` | `values/strings.xml` |
| English | `en` | `values-en/strings.xml` |
| Marathi | `mr` | `values-mr/strings.xml` |
| Telugu | `te` | `values-te/strings.xml` |

Language is stored in DataStore. The user selects it on first launch and can change it in profile settings.

---

## Low-Literacy UX Design

- **Large numerals**: `displayLarge` (48sp ExtraBold) for earnings amounts
- **Emoji icons**: ☀️ 📅 🏆 ♻️ used as visual anchors alongside text
- **Bilingual labels**: Every label shows both Hindi + English
- **Color-coded cards**: Each earnings period has a distinct gradient color
- **Minimal text**: Icons and numbers over paragraphs
- **Dark high-contrast theme**: Earthy green + amber for readability

---

## Contributing

1. Fork the repository
2. Create a feature branch: `git checkout -b feature/add-earning-history`
3. Commit changes: `git commit -m 'Add earning history screen'`
4. Push: `git push origin feature/add-earning-history`
5. Open a Pull Request

---

## License

MIT © SIH 2026 — Seth G Team
