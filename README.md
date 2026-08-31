# hmg-ipmap

<p>
  <img src="https://img.shields.io/badge/status-active-brightgreen.svg" alt="active" />
   <img
   src="https://img.shields.io/badge/version-v0.1.0-green.svg"
   alt="Version: v0.1.0"/>
  <a href="https://opensource.org/licenses/Apache-2.0">
    <img
      src="https://img.shields.io/badge/License-Apache%202.0-blue.svg"
      alt="License: Apache 2.0"/>
  </a>
</p>

## Overview

hmg-IPMap is an open-source IP geolocation platform that resolves IPv4 addresses to geographic locations (city, country, continent). It supports bulk IP data ingestion via CSV/ZIP uploads, custom IP range management, and high-performance lookups through a two-level cache (Caffeine + Valkey).


## Key Features

- **IP Geolocation Lookup** — resolve IP → city, country, continent
- **IP Mapping Management** — create and manage IP ranges (CIDR, range, single IP, wildcard, array)
- **Location Management** — hierarchical CRUD (continent → country → region → city)
- **Bulk CSV Import** — upload and process large IP database files via ZIP
- **High-performance lookups** — two-level cache (local Caffeine + distributed Valkey)

## Getting Started

### Prerequisites

- **Java 25** — [Eclipse Temurin](https://adoptium.net/temurin/releases/?version=25) or any JDK 25 compliant distribution
- **Gradle** — included via the Gradle wrapper (`./gradlew`), no separate install needed
- **Docker** — for running PostgreSQL and Valkey locally

### Installation

```bash
git clone https://github.com/hyundaimotorgroup/hmg-ipmap.git
cd hmg-ipmap
```

**1. Start local services**

Copy the environment template and set your local credentials:

```bash
cp docker/.env.example docker/.env
# Edit docker/.env and replace the placeholder values with your own passwords
```

Then start the services:

```bash
docker-compose -f docker/docker-compose.yml up -d
```

| Service | Port | Credentials |
|---|---|---|
| PostgreSQL 17.6 | `5432` | user: `postgres`, password: see `docker/.env`, db: `ipmap` |
| Valkey 8.1.5 | `6379` | no auth |

**2. Apply the database schema**

```bash
docker exec -i db psql -U postgres -d ipmap < hmg-ipmap-api/src/main/resources/db/schema.sql
```

**3. Configure local properties**

Create `hmg-ipmap-api/src/main/resources/application-local.properties` (already in `.gitignore`):

```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/ipmap
spring.datasource.username=app_user
spring.datasource.password=<APP_USER_PASSWORD from docker/.env>

spring.data.redis.host=localhost
spring.data.redis.port=6379
redisson.mode=single
redisson.address=redis://localhost:6379

app.ingestion.upload.folder=/path/to/your/upload/folder
```

Create the upload directory:

```bash
mkdir -p /path/to/your/upload/folder
```

**4. Install git hooks**

Run once after cloning. The pre-commit hook enforces Spotless formatting on every commit.

```bash
cd hmg-ipmap-api
./gradlew installGitHooks
```

**5. Run the application**

```bash
cd hmg-ipmap-api
./gradlew bootRun --args='--spring.profiles.active=local'
```

The application starts at `http://localhost:8080`. Swagger UI is available at `http://localhost:8080/swagger-ui.html`.

### Build

All Gradle commands are run from the `hmg-ipmap-api/` directory.

```bash
# Fast build — skip tests
./gradlew build -x test

# Full build including tests
./gradlew build
```

### Test

All test commands are run from the `hmg-ipmap-api/` directory.

```bash
./gradlew test

# Run a specific test class
./gradlew test --tests "com.hmg.ipmap.ingestion.CsvProcessingServiceTest"

# Run a specific test method
./gradlew test --tests "com.hmg.ipmap.ingestion.CsvProcessingServiceTest.methodName"
```

## Usage

Resolve an IPv4 address to a geographic location:

```bash
curl -H "X-HMGIPMAP-APIKEY: your-api-key" \
  "http://localhost:8080/api/v1/ip-location?ip=8.8.8.8"
```

All API requests require an API key header:

```
X-HMGIPMAP-APIKEY: <your-api-key>
```

Full endpoint documentation is available via Swagger UI at `/swagger-ui.html` when the application is running.

## Documentation

Once the application is running, you can explore the API documentation via Swagger UI:

- `/swagger-ui.html`


## Contributing

Contributions are welcome.

Before contributing, please review:

- [Contributing Guide](.github/CONTRIBUTING.md)
- [Contributor License Agreement (Korean)](https://github.com/hyundaimotorgroup/.github/blob/main/legal/CLA-KOR.md)
- [Contributor License Agreement (English)](https://github.com/hyundaimotorgroup/.github/blob/main/legal/CLA-ENG.md)

If CLA verification is enabled, contributors may be required to complete the Contributor License Agreement (CLA) process before their pull request can be merged.

**Submitting a pull request:**
1. Fork the repository and create a branch from `main`
2. Follow the Installation steps to set up your local environment
3. Make your changes and ensure all tests pass (`./gradlew test`)
4. Run code formatting before committing (`./gradlew spotlessApply`)
5. Open a pull request with a clear description of what was changed and why

**Commit message format** — this project enforces [Conventional Commits](https://www.conventionalcommits.org/). The `commit-msg` hook validates this automatically after running `./gradlew installGitHooks`.

```
<type>(<scope>): <description>

# Types: feat, fix, docs, style, refactor, perf, test, build, ci, chore, revert
```

## Project Policies

- [Code of Conduct](.github/CODE_OF_CONDUCT.md)
- [Security Policy](.github/SECURITY.md)

## Support

Before creating a new issue, please search existing issues to avoid duplicates.

For bug reports, feature requests, and general project discussions:

- [GitHub Issues](https://github.com/hyundaimotorgroup/hmg-ipmap/issues)
- [Issue Templates](.github/ISSUE_TEMPLATE/)

For security concerns:

- Follow the instructions in [Security Policy](.github/SECURITY.md).


## Releases

Release notes and version history are available in the following locations:
- [Changelog](./CHANGELOG.md)
- [Release Notes](https://github.com/hyundaimotorgroup/hmg-ipmap/releases)

This project follows [Semantic Versioning](https://semver.org/).

## License
This project is licensed under the <a href="https://www.apache.org/licenses/LICENSE-2.0">Apache License 2.0</a>.

See the [LICENSE](LICENSE) file for details.

Copyright (c) 2026 Hyundai Motor Group.
