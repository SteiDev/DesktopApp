# DesktopApp JavaFX Project Review

## Bugs and small mistakes

- **Typo `PASSWORD_STREGTH`**, and the javadoc in `hash()` says `passwordHash` where it means `rawPassword`.
- **`login()` is faster for unknown emails than for wrong passwords.** BCrypt only runs when the email exists, so an attacker can learn which emails are registered by timing. Run a dummy `verify` against a fixed hash when the user isn't found.
- **`buildenv.sh` hardcodes `/usr/lib/jvm/openjdk25`.** That only works on some Linux distros and breaks on macOS or Debian-style names like `java-25-openjdk-amd64`. The `(venv)` prompt is copied from Python. `exit 1` in a sourced script also kills the user's shell.

## Architecture

- **It isn't a JavaFX app yet.** `Main` is a hello-world label and nothing connects to the `accounts` package. You'll need login and register screens, a session holding the current `User`, and probably `javafx-fxml` (not in the pom) plus a `module-info.java` or a clear classpath setup.
- **`SQLiteUserDatabase` creates its own `BCryptPasswordService`.** You already have `IPasswordService`, so inject it through the constructor, which also lets tests use a cheap fake.
- **The database layer shouldn't hash or verify passwords.** Move that into an `AuthService` and let the repository only store and fetch rows. `IUserDatabase` currently mixes authentication, user CRUD and statistics; split it into `UserRepository`, `StatsRepository` and `AuthService`.
- **`setQuizzesCompleted` and `setAccuracy` are lossy.** The caller reads, computes and writes absolute values, so two writes can overwrite each other. Store `quizzes_completed`, `correct_answers` and `total_answers`, expose one atomic `recordQuiz(id, correct, total)` using `UPDATE ... SET x = x + ?`, and derive accuracy.
- **`updateField`/`getField` with whitelists is more machinery than needed.** With two stats columns, plain explicit SQL is simpler and SpotBugs won't flag dynamic SQL.
- **No schema versioning.** `CREATE TABLE IF NOT EXISTS` can't evolve. Use `PRAGMA user_version` with a small migration list, or Flyway.
- **Consider `PRAGMA journal_mode=WAL`** in the `SQLiteConfig` for better behavior with a GUI reading while writing.

## Validation and security

- **`saveUser` validates username and email but not the password.** The `IllegalArgumentException` only comes from deeper in `hash()`. Add a minimum length and a basic email format check (in the service layer).
- **BCrypt only uses the first 72 bytes.** Depending on the Spring Security version it either truncates silently or throws. Pick a policy (maximum length) and add a test.
- **No rate limiting or lockout on login.**
- **Local SQLite accounts can't be shared with the web Frontend.** Email verification via Jakarta Mail (the TODO) would also mean shipping SMTP credentials inside a desktop app, which is unsafe. If the desktop and web apps are meant to share accounts, the desktop app should talk to a backend API, and that decision changes the design of the whole `accounts` package.

## JavaFX-specific (for when the UI is added)

- **Never call the DB or BCrypt on the FX thread.** Strength 12 takes about a quarter of a second, which freezes the UI. Use `Task`/`Service` or an executor and update the UI with `Platform.runLater`.
- **Use a UI architecture** such as MVVM or MVC with FXML, with view models that depend on the interfaces, so they can be unit tested without launching JavaFX.
- **Use TestFX**, or at least ViewModel tests, since no UI code is tested yet.

## Build, CI and repo hygiene

- **`build/` is stale Gradle output** (Gradle reports and class files) in a Maven project. Delete it. Check `git ls-files` to make sure `.idea/workspace.xml` and `logs/app.log` aren't tracked, since `.idea/` is only partly ignored.
- **Tests live in the default package.** Move them to `org.ssd.project.accounts` to match the classes and get package-private access.
- **Wildcard `import java.sql.*;`** goes against the Google style you enforce. `importOrder` won't expand it.
- **Add the Maven wrapper** (`mvn wrapper:wrapper`). Your `.gitignore` already anticipates it, and it removes the "install Maven" requirement.
- **Add JaCoCo** for coverage, and consider `maven-enforcer-plugin` to require Java 25, which makes `buildenv.sh` less necessary.
- **Add Dependabot or Renovate**, since many versions are pinned by hand.
- **CI only triggers on `master`.** Check that matches your default branch, and run tests on Windows and macOS too if you plan to ship a desktop app.
- **Logs go to relative `logs/`.** That depends on the working directory. For a packaged app, use a user data directory (and the same for `data/accounts.db`).
- **Packaging.** There's no way to produce a distributable yet. Look at `jlink` plus `jpackage`, or the `javafx-maven-plugin` jlink goal.

## What's already good

- Parameterized SQL throughout.
- Atomic user and stats creation.
- Foreign keys enforced.
- Case-insensitive unique email.
- Logging ids rather than emails.
- Thorough tests, including rollback and foreign-key behavior.
- Formatting and SpotBugs enforced in CI.

## Suggested next steps

1. Fix the quick bugs (logback package name, `User.toString()`, typos, error message, `returnTrue()`).
2. Refactor into `AuthService` and `UserRepository` with injected dependencies.
3. Build the FXML login screen with a background-thread login.
4. Decide whether accounts stay local or move behind a backend shared with the web Frontend.
