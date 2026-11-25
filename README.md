> **AI agent instructions (organization-wide):** Before working on code, issues, or pull requests, read and follow the [organization `AGENTS.md`](https://github.com/atto-lang/.github/blob/main/AGENTS.md), including its **merge-first stealth-development policy**. Honor any repository-local `AGENTS.md`, explicit holds, and GitHub-enforced rules.

# atto-linked-queue

Concurrent queue and collection primitives for AttoLang runtimes and applications.

## Naming migration and language authority

This repository is maintained at [atto-lang/atto-linked-queue](https://github.com/atto-lang/atto-linked-queue); use `https://github.com/atto-lang/atto-linked-queue.git` for Git remotes and canonical `.atto` source examples. The normative language contract is [atto-spec](https://github.com/atto-lang/atto-spec), with executable guest evidence tracked by [atto-platform-conformance](https://github.com/atto-lang/atto-platform-conformance). Follow current ownership, borrowing, effects, actor, and syntax rules there; do not infer implemented features from a README or unexecuted CI.

## Purpose

Queue lifecycles, producer/consumer ordering, safe ownership transfer, and concurrency semantics.

## API and contract expectations

- Expose general-purpose interfaces that are usable without an Atto commercial account.
- Document data/ownership semantics, error behavior, and supported runtimes.
- Keep protocol and serialization details portable across relevant AttoLang backends.

## Development and testing

Use checked-in implementation and CI for actual commands. Add behavior-focused tests, including negative cases and lifecycle edge cases; distinguish local unit tests from executed multi-backend conformance.

## Project status

This describes intended scope, not a verified feature matrix. Consult the source tree and exact-commit CI results for implementation status.

## Ecosystem

- [AttoLang](https://github.com/atto-lang)
- [Language specification](https://github.com/atto-lang/atto-spec)
- [Cross-platform conformance](https://github.com/atto-lang/atto-platform-conformance)

## Repository ownership and contribution routing

**Belongs here:** Safe linked FIFO/collection APIs, multi-producer/multi-consumer ownership transfer, bounded capacity and queue lifecycle tests.

**Belongs elsewhere:** Reactive channel adaptors in atto-rx-channels; scheduler/private runtime queues in atto-source.*; language ownership definitions in atto-spec.

**Security and portability boundary:** Define fairness, ABA/generation protection, overflow and cancel-vs-close races; assert no cross-actor aliasing and deterministic reclamation.

AttoLang is the canonical project name; new source, examples and documentation use `.atto` and the `atto-lang` repository organization. This README defines scope, not feature readiness.

**Acceptance:** name exact source SHA/tree, runtime/toolchain, positive and negative checks and execution receipt before claiming support. Host-only tests and empty CI steps do not prove guest execution.

## Legacy migration and verification gates

The archived predecessor is [ores-truffle-oreslang/oreslang-linked-queue](https://github.com/ores-truffle-oreslang/oreslang-linked-queue). This repository is maintained at [atto-lang/atto-linked-queue](https://github.com/atto-lang/atto-linked-queue); do not infer an ID-preserving transfer or source parity from naming or imported history. Preserve working legacy `.ores` inputs and package/CLI aliases until their compiler-backed `.atto` replacements and consumers are verified.

Bounded portable queue and collection data structures, ownership rules and tests belong here; OS scheduling and cloud message-broker operation do not. [atto-lang](https://github.com/atto-lang) owns language, portable libraries, compilers, tooling, tests and examples; [atto-co](https://github.com/atto-co) separately governs hosted deployment, tenant infrastructure, identity and billing. Future transfers or carve-outs require separate source/target owner authorization, independent security review and rollback ownership. Inventory exact SHA/tree, numeric ID, history, access, visibility, protections, PRs, releases, packages, consumers, Actions/runners, secrets, applications, webhooks, deploy keys and Pages. Never mirror private source without explicit authorization. Verify identity/history and restored controls after any authorized transfer, then execute exact-head tests before changing consumers. Zero-step jobs are NOT_EXECUTED; host-only checks do not establish compiled guest execution or production readiness.

Historical planning: [coordination issue #26](https://github.com/ores-truffle-oreslang/.github/issues/26) and [migration runbook #25](https://github.com/ores-truffle-oreslang/.github/pull/25).
