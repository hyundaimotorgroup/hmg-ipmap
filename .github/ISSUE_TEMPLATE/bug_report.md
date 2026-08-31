---
name: Bug Report
about: Report a reproducible bug to help us improve
title: "[BUG] "
labels: bug
assignees: ''
---

## Description

<!--
A clear and concise description of the bug.

Example:
The IP lookup endpoint returns an incorrect city name for IPv6 addresses mapped
to IPv4 space (e.g., ::ffff:192.168.1.1 resolves as "Unknown" instead of the correct city).
-->

---

## Steps to Reproduce

<!--
Provide clear steps so we can reproduce the issue.

Example:
1. Send a GET request to `/api/v1/lookup?ip=::ffff:192.168.1.1`
2. Observe the response body
3. Note that `city` is returned as "Unknown"
-->

1.
2.
3.

---

## Expected Behavior

<!--
What did you expect to happen?

Example:
The endpoint should normalize the IPv6-mapped IPv4 address and return the correct city name.
-->

---

## Actual Behavior

<!--
What actually happened?

Example:
The response returns `"city": "Unknown"` instead of the resolved city name.
-->

---

## Environment

<!--
Please complete the fields below. Mark browser fields only if the issue occurs in a web context.
-->

- OS:
- OS Version:
- Application Version:
- Runtime / SDK Version:
- Browser *(if applicable)*:
- Browser Version *(if applicable)*:

---

## Impact

<!-- How severe is this issue? Select one. -->

- [ ] Critical
- [ ] High
- [ ] Medium
- [ ] Low

---

## Error Messages / Logs

<!--
Paste relevant logs, stack traces, or error messages below.
Tip: include the full stack trace if available — partial logs are often not enough to diagnose the root cause.
-->

```text
Paste logs here
```
