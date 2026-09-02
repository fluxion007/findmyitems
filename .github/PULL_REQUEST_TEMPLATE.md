## What this changes

<!-- In one paragraph, describe the behavior that changes. -->

## Why

<!-- Describe the problem being solved or the issue being closed, such as "Closes #12". -->

## Checks run

- [ ] `./gradlew build` — compilation and JUnit tests
- [ ] `./gradlew runGameTest` — headless server tests
- [ ] `./gradlew runClientGameTest` — required only when this change affects input, screens, or rendering
- [ ] Verified in a single-player world

<!-- Delete lines that do not apply; do not leave them unchecked. -->

## Tests

<!-- Name the test covering this change and what regression it would catch.
     "No test" is acceptable for a rename or documentation change, but not for
     code that moves items. -->
