# SuloCore

SuloCore is an all-in-one plugin starter project for Paper 1.21.11.

## Requirements
- Java 21 JDK
- Maven 3.9+
- Paper 1.21.11 server
- MySQL server (optional during initial setup)

## Build
Run this command in the project folder:

```bash
mvn clean package
```

The plugin JAR will be created at:

```text
target/SuloCore-1.0.0.jar
```

Copy the JAR into your Paper server's `plugins` folder and restart the server.

## First setup
1. Start the server once to generate `plugins/SuloCore/config.yml`.
2. Create the MySQL database and a dedicated database user.
3. Edit the database settings in `config.yml`.
4. Set `database.enabled: true` after entering the correct connection details.
5. Restart the server.

## Current starter features
- Plugin startup and shutdown
- `/sulocore` and `/sulocore info`
- `/sulocore reload`
- Turkish and English message files
- HikariCP-based MySQL connection pool

This is the initial project foundation, not the completed all-in-one feature set.
Economy, homes, teleportation, crates, market, moderation, quests and protection
will be implemented in later development stages.


## SuloCore 2.0.0 development
This branch contains the current all-in-one feature implementation draft. Build validation is required before merging.
