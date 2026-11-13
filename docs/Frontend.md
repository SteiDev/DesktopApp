# SoundLab Frontend

Overview of the screens and the classes that wire them together.

## Core Classes

| Class | Responsibility |
|---|---|
| `ScreenManager.java` | Handles switching from one screen to another |
| `MainMenuController.java` | Handles the dropdown functions and wires the connections between screens |

---

## Screens

### `login`

Handles the username, email, and password.

| Action | Behavior |
|---|---|
| **Login** | Works if the account exists. If it doesn't, either do nothing or automatically create a new account |
| **Register** | Creates a new account. If it already exists, enter the account automatically |
| **Enter as Guest** | Doesn't save any data *(behavior still to be confirmed)* |

After a successful login, goes to the `main_menu` screen.

---

### `main_menu`

| Element | Behavior |
|---|---|
| **Username** | Switches to the `account` screen |
| **Synthesizer** | Opens a dropdown with 3 choices: `organ`, `acoustic_grand_piano`, `synth_lead` |
| **Quiz** | Opens a dropdown with 2 choices: `start_quiz`, `review_past_quiz` |
| **Logout** | Goes back to the `login` screen |

---

### `account`

> 🚧 *TODO: not documented yet.*

---

## Navigation Flow

```
login ──► main_menu ──┬──► account
                      ├──► Synthesizer ──┬──► organ
                      │                  ├──► acoustic_grand_piano
                      │                  └──► synth_lead
                      ├──► Quiz ─────────┬──► start_quiz
                      │                  └──► review_past_quiz
                      └──► logout ──► login
```
