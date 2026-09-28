# EventEase

## Database configuration

The backend connects to the existing MySQL database `eventease` using
`DB_URL`, `DB_USERNAME`, and `DB_PASSWORD`. Spring Boot loads these settings
and `JWT_SECRET_BASE64` from the root `.env` file when present. The `.env` file
is ignored by Git and must not be committed. The application does not create or
modify database tables (`spring.jpa.hibernate.ddl-auto=none`); the database
must already have the schema expected by EventEase.

Set the database password and a Base64-encoded JWT signing secret in the
environment before starting the application. Keep these values out of source
control.

```powershell
$env:DB_PASSWORD = "<your-local-MySQL-password>"
$bytes = New-Object byte[] 32
[System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes)
$env:JWT_SECRET_BASE64 = [Convert]::ToBase64String($bytes)
mvn spring-boot:run
```

### Running from IntelliJ IDEA

The application targets Java 21. In IntelliJ, select **File → Project
Structure → Project SDK: 21**, then select the application run configuration
and use `D:\EventEase` as its working directory. The root `.env` is loaded by
Spring Boot and contains:

- `DB_PASSWORD` = the password for your local MySQL `root` account
- `JWT_SECRET_BASE64` = a locally generated Base64 key (at least 32 bytes)

Keep the same generated JWT key for later runs so existing login cookies remain
valid. If you rotate the key, users must sign in again. Do not commit the key
or paste the PowerShell placeholder literally. The startup error explicitly
identifies a missing, invalid, or too-short key.

The default database URL is `jdbc:mysql://localhost:3306/eventease`, and the
default username is `root`. Override them with `DB_URL` and `DB_USERNAME` when
your local MySQL configuration differs.

## Authentication flow

The public landing page is `/`. Protected actions lead unauthenticated visitors
to `/#how-it-works`, where the login and student sign-up forms share one panel.
Student and College Admin login use the role-specific endpoints under
`/api/auth`; successful authentication sets HttpOnly cookies and routes to the
appropriate dashboard. Student registration creates the account with a BCrypt
password hash but does not sign the student in; they must log in afterward.
Login requires only email and password. The backend looks up that email in the
student and college-admin accounts, verifies the BCrypt password, and derives
the role from the matching database record; it rejects ambiguous emails found
in both account tables. Spring Security enforces the role boundary for both
page and API routes.

Student signup hashes passwords with BCrypt before inserting the account, and
login verifies the submitted password with BCrypt rather than comparing
plaintext values. Pre-provisioned college-admin accounts must also store a
BCrypt password hash in the existing `password` column. Entity relationships
map to the existing foreign keys; Jackson reference annotations prevent
recursive serialization, while the API returns DTOs instead of exposing JPA
entities.

Access tokens expire after 30 minutes by default. The HttpOnly refresh token
can renew an access token without asking for the password, but its expiry is
fixed at 14 days from login (`JWT_REFRESH_TOKEN_DAYS`); refreshing access does
not rotate or extend that refresh token. Once it expires, protected page/API
requests are rejected, the cookies are cleared, and the user must log in again.
An access token renewed near that deadline is shortened so it cannot remain
valid past the refresh-token expiry.
The refresh cookie is scoped to `/` so it can renew access for protected pages
as well as `/api` requests. Configure `JWT_ACCESS_TOKEN_MINUTES` and
`JWT_REFRESH_TOKEN_DAYS` to override the defaults.
