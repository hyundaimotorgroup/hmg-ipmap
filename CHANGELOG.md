# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.1.0] - 2026-07-24

### Added
- IP geolocation lookup — resolve IPv4 addresses to city, country, continent
- IP mapping management — CIDR, range, single IP, wildcard, and array formats
- Location hierarchy management — continent → country → region → city
- Bulk CSV import via ZIP file upload with Spring Batch processing
- Two-level cache — local Caffeine + distributed Valkey (Redisson)
- API key authentication
- Swagger UI documentation
