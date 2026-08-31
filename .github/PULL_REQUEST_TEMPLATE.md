## Description

<!--
What changed and why? Briefly describe the problem this PR solves or the feature it adds.

Example:
The IP lookup endpoint was returning incorrect city names for IPv6 addresses mapped
to IPv4 space. This PR fixes the normalization logic in IpResolutionService so that
::ffff:x.x.x.x addresses are correctly resolved before querying the database.
-->

---

## Related Issue

<!--
Examples:
- Fixes #123
- Closes #456
- Resolves #789
-->

Fixes #

---

## Type of Change

<!-- Select all that apply. -->

- [ ] Bug fix
- [ ] New feature
- [ ] Documentation update
- [ ] Performance improvement
- [ ] Refactoring
- [ ] Test improvement
- [ ] Build / CI update
- [ ] Dependency update
- [ ] Other

---

## Changes Made

<!--
Examples:
- Fixed IPv6-to-IPv4 normalization in IpResolutionService
- Added unit tests for edge cases in IpAddressUtils
- Updated API docs to clarify IPv6 support
-->

-
-

---

## Testing

- [ ] Tested locally
- [ ] Existing tests pass
- [ ] New tests added where appropriate
- [ ] Documentation updated where appropriate

<details>
<summary>Test Environment (Optional)</summary>

<!--
Fill in only if relevant (e.g., OS-specific or runtime-specific behavior).
-->

- OS:
- Runtime / Language Version:

</details>

---

## Breaking Changes

<!--
Does this PR introduce any breaking changes? If not, write "None".

Example:
The `/api/v1/ip-lookup` response now returns `regionName` instead of `region`.
Consumers must update their field mapping accordingly.
-->

None

---

## Screenshots (Optional)

---

## Contributor License Agreement (CLA)

Contributions to this project are subject to the Contributor License Agreement (CLA). Contributors may be required to complete the CLA process before this PR can be merged.

---

## Checklist

- [ ] My changes follow the project's [contribution guidelines](CONTRIBUTING.md).
- [ ] I have performed a self-review of my changes.
- [ ] My changes generate no new warnings or errors.
- [ ] No sensitive information (credentials, keys, tokens, secrets, internal URLs/IPs) is included in this PR.