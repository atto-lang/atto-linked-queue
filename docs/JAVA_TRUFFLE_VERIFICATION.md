# Exact-source Java/Truffle queue verification

The production library is **100% Oreslang** in [src/int_linked_queue.ores](../src/int_linked_queue.ores). The JUnit file [tests/integration/java/LinkedQueueIntegrationTest.java](../tests/integration/java/LinkedQueueIntegrationTest.java) is a **test adapter**, not a replacement implementation or Java/JS FFI layer. It compiles and runs the original `.ores` source without rewriting the queue implementation.

## Run against an Oreslang Java compiler checkout

Requires Java 21 and Maven. Check out this repository and `ores-truffle-oreslang/oreslang-source.java`. Copy `tests/integration/java/LinkedQueueIntegrationTest.java` into `oreslang-source.java/src/test/java/dev/oreslang/LinkedQueueIntegrationTest.java`, then from the Java compiler root:

```bash
mvn -B -ntp \
  -Dtest=LinkedQueueIntegrationTest \
  -Doreslang.linkedQueueRoot=/absolute/path/to/oreslang-linked-queue \
  -Dsurefire.useFile=false test
```

The suite reads the **actual** `.ores` files under the configured source root. The private funded Actions mirror deliberately uses `../audit/linked-queue` as its default while copying exact Git blobs and checking source SHA equality; it uses no cross-organization checkout token or transferred secrets.

## Test inventory

- Five positive programs: smoke, stress, instances, edge cases and relink matrix. Each invokes an Oreslang entry function and must print precisely `0` on GraalVM/Truffle, with no host interop authority.
- Five compiler-negative fixtures: simultaneous mutable aliases, read-only mutation, mutable method called from a read-only receiver, and private field mutation. They must **parse successfully** and then fail during TypeChecker/OwnershipChecker, never during an unrelated parse or source import error.
- One library-level parser, type, and ownership check.

The adapter strips test-file *import declarations only for single-unit JUnit compilation*, then concatenates unmodified library source and test source. It renames the test entrypoint and writes its return value to stdout for precise success/failure checking.

## Evidence, limitations and merge requirements

[Funded exact-source Java run #38317989469](https://github.com/agent-pontifex/agent-pontifex-daemon/actions/runs/38317989469) passed 26/26 tests, including dedicated borrowed-argument evaluation-order regressions in [Java PR #768](https://github.com/ores-truffle-oreslang/oreslang-source.java/pull/768). This is an execution test and compiler negative-test gate, **not** evidence that native LLVM Array lowering, multi-threaded race freedom, actor isolation or generic `LinkedQueue<K,V>` are complete.

For concurrent deployment, continue to own the queue through a single actor state callback and prove the single-flight scheduler property before claiming that separate messages are linearizable. Reader-writer locks remain a separate shared-memory adapter design, not an implicit property of `mut self`.


## Independent seeded differential model

`tests/integration/java/LinkedQueueDifferentialTest.java` generates deterministic
Oreslang programs from a Java-only reference model (`ArrayList` insertion
order and `Map` of active values). The generated Oreslang performs every
operation using `IntLinkedQueue`, then checks return values, occupancy,
position order, head/tail payload snapshots, and `validate()` after every step.

The oracle uses two reproducible seeds with 64 mixed operations each,
including invalid out-of-range keys and anchors, failed duplicate insertions,
removal/reuse, clear, and both-end operations. The generator's Java data
structures are **test infrastructure only** and are never imported by the
Oreslang program.

Run via the same compiler checkout and source-root property:

```bash
mvn -B -ntp \
  -Dtest=LinkedQueueIntegrationTest,LinkedQueueDifferentialTest \
  -Doreslang.linkedQueueRoot=/absolute/path/to/oreslang-linked-queue \
  -Dsurefire.useFile=false test
```

This checks single-owner behavioral equivalence, not thread safety, LLVM
lowering, or randomized exhaustive proof. Failing seed values can be rerun
deterministically.

## Generation-ticket differential gate (new, unexecuted)

`tests/integration/java/LinkedQueueTicketDifferentialTest.java` independently
models active keys, list order, slot generations and previously issued tickets.
A directed prelude guarantees successful enqueue, add_to_front, insert_before,\ninsert_after, valid ticket removal, stale ticket rejection and clear/reuse.\nThree fixed Java PRNG seeds then generate 48 additional Oreslang operations each. The generated
Oreslang source exercises all four admissions, invalid/duplicate requests,
remove/dequeue/remove_last, clear, valid and stale `remove_ticket`, ticket
payload reads, linked order and `validate()` after every step. In particular,
the Java oracle retains old tickets across key reuse, while the Oreslang
implementation must reject stale tickets and preserve unrelated live tickets.

To execute, copy **both** Java differential test files and the integration
adapter into the compiler's `src/test/java/dev/oreslang/` test directory, then
run from the exact pinned compiler checkout:

```bash
mvn -B -ntp \
  -Dtest=LinkedQueueIntegrationTest,LinkedQueueDifferentialTest,LinkedQueueTicketDifferentialTest \
  -Doreslang.linkedQueueRoot=/absolute/path/to/oreslang-linked-queue \
  -Dsurefire.useFile=false test
```

This new model has **not been executed** on the draft PR tree. Do not count
existing Java CI runs or successful Java-side source generation as execution.
Require the full Git commit/tree SHA, exact library/test blob SHAs, compiler
commit, real job/step identifiers, raw return values and logs before recording
`generation-differential/java/execution` in the rollout receipt. The receipt
checker only validates declared metadata; it cannot authenticate execution.

This is a bounded queue-local ABA regression, **not** a cross-queue/tenant
identity mechanism, authorization test, thread-safety proof or saturation
test at 2^31-1. Those remain separate release gates.
