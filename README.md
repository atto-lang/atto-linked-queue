# atto-linked-queue

Concurrent queue and collection primitives for AttoLang runtimes and applications.

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

AttoLang is the public name; preserve existing Oreslang/.ores compatibility and use canonical .atto for new portable library source. This README defines scope, not feature readiness.

**Acceptance:** name exact source SHA/tree, runtime/toolchain, positive and negative checks and execution receipt before claiming support. Host-only tests and empty CI steps do not prove guest execution.
