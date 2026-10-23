# Building the project
## Requirements
Before building the project, make sure the following are installed on your system:
- Java 25
- Maven
- Bash (for scripts)
## Build environment
To ensure that no conflicts happen in the building of the project, the `buildenv.sh` script inside `scripts/` directory should be sourced before doing anything in this project.

To source run
```bash
source scripts/buildenv.sh
```
inside the root of the project (where `pom.xml` is located).

If the environment was successfully activated, the shell prompt should change.
## Build commands
### Clean the build files
```bash
mvn clean
```
This will remove the build files in the target folder ensuring you start the build from scratch.

---
### Run the JavaFX program
```bash
mvn javafx:run
```

This will build the project (if needed) and run the program.

---
### Verify the code
```bash
mvn verify
```

This will run all testing tools (unit tests, the style and the bug finder).

This will automatically run on a push/pull request on the `master` branch via Github Actions.

---
### Apply style changes
```bash
mvn spotless:apply
```

Applies the suggested changes from the styler.

The differences between the old and the newly formatted code can be checked via `git diff`.

---
### Get a GUI bug report
```bash
mvn spotbugs:gui
```

Get a GUI report of the bugs found in the code.