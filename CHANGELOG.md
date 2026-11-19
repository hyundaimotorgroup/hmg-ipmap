# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.1.1] - 2026-11-19

### Security
- Fixed authorization issues in the user, IP mapping, and location management APIs

### Changed
- Only `ADMIN` can manage accounts. Account creation accepts only the `CLIENT` type; creating another `ADMIN` or a `SUB_CLIENT` is rejected
- A user's type can no longer be changed on update, and `userType` is now optional in update requests (the existing type is retained)
- `CLIENT` and `SUB_CLIENT` credentials can access only the IP location lookup API
- Collection endpoints now enforce a page size of 1 to 100; other values return `400`
- Parent relationships can no longer be assigned (`parentId` is rejected on update and ignored on create)
- New accounts always receive an independently generated API key

### Fixed
- Fixed the IP range not being applied when updating an IP mapping

### Deprecated
- `UserType.SUB_CLIENT` and sub-client provisioning
- `Scope.CLIENT` and `Scope.SUB_CLIENT`
- User parent relationships and `UserRequestDto.userType`
- Legacy source-IP (`X-HMGIPMAP-SOURCEIP`) and parent-based authentication lookups
- The IP span rebuild endpoint, which will move under the ops API

Existing accounts, parent relationships, and scoped data remain stored unchanged. No legacy data is automatically promoted, deleted, or migrated. The deprecated items are planned for removal in a future major release.

## [0.1.0] - 2026-07-24

### Added
- IP geolocation lookup — resolve IPv4 addresses to city, country, continent
- IP mapping management — CIDR, range, single IP, wildcard, and array formats
- Location hierarchy management — continent → country → region → city
- Bulk CSV import via ZIP file upload with Spring Batch processing
- Two-level cache — local Caffeine + distributed Valkey (Redisson)
- API key authentication
- Swagger UI documentation
