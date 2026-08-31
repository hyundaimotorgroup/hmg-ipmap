---
name: Feature Request
about: Suggest an idea for this project
title: "[FEATURE] "
labels: enhancement
assignees: ''
---

## Description

<!--
A clear and concise description of the feature you'd like to see.

Example:
Add support for bulk IP lookups via a single POST request that accepts a list of IP addresses
and returns a list of resolved locations, reducing the number of round trips for batch use cases.
-->

---

## Problem Statement

<!--
Is your feature request related to a problem? Describe it.

Example:
Currently, resolving 1,000 IPs requires 1,000 separate GET requests. This is impractical
for batch processing pipelines that need to resolve large volumes of IPs efficiently.
-->

---

## Proposed Solution

<!--
Describe the solution or feature you want.

Example:
A new endpoint POST /api/v1/lookup/batch that accepts { "ips": ["1.2.3.4", "5.6.7.8"] }
and returns an array of resolved location objects, processed in a single database query.
-->

---

## Alternative Approaches

<!--
Have you considered any alternative solutions or workarounds?

Example:
- Client-side batching with concurrent requests (limited by rate limiting)
- A streaming endpoint using Server-Sent Events
-->

---

## Use Cases

<!--
Describe specific use cases where this feature would be valuable.

Example:
- Analytics pipelines that enrich event logs with geolocation data
- Security tools that need to geolocate IPs from access logs in bulk
-->

---

## Additional Context

<!--
Add any other context, mockups, or screenshots about the feature request here.
-->
