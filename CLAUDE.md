# ScaScan

Two apps, one product: `android/` is the original, `ios/` is a native SwiftUI
port that is now the active work.

**Read [`ios/ARCHITECTURE.md`](ios/ARCHITECTURE.md) before touching the iOS
app.** It covers the module layout, the nutrition arithmetic (which changed
after v1.1 and is easy to break), the concurrency and localization traps that
have already caused shipped bugs, and how to actually verify a change — the unit
tests can't be run in this project as configured, so "it builds" is not
verification.

The root `README.md` is the monorepo overview (feature parity table, both tech stacks,
both repo layouts). `ios/ARCHITECTURE.md` is still the only source for the iOS
implementation detail — storage, nutrition math, concurrency, localization, and
how to verify a change.

## Working agreements

- The repo owner writes in Italian; reply in Italian. Code, comments and commit
  messages stay in English.
- Commit locally; **push only when explicitly asked.**
- Never rewrite or force-push shared history.
- Don't guess at fixes. Compile a probe, read the SDK header, run the binary,
  check the numbers — then say what was verified and what wasn't.
