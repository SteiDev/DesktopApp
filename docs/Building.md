# Building the project
## Requirements
Before building the project, make sure the following are installed on your system:
- Java 25
- Maven (for building the project)
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
```bash
mvn clean
```
This will remove the build files in the target folder ensuring you start the build from scratch.

```bash
mvn test
```

This will run the tests for the project.

```bash
mvn javafx:run
```

This will build the project (if needed) and run the program.