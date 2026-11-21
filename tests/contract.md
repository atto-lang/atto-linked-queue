# Port contract and test status

Original behavioral reference: https://github.com/ORESoftware/linked-queue at master, src/linked-queue.ts (inspected 2026-10-12).

## Implemented first slice

| Original behavior | Oreslang behavior | Notes |
| --- | --- | --- |
| enqueue/enq/push | enqueue(key,value) | O(1), bounded int keys |
| addToFront/unshift | add_to_front(key,value) | O(1) |
| dequeue/deq/shift | dequeue() | Option<QueueEntry>, no sentinel collision |
| pop/removeLast | remove_last() | O(1) |
| remove(key) | remove(key) | O(1) |
| contains/get | contains/get | O(1), value is Option<int> |
| first/last/peek | first()/last() | snapshot entries |
| getOrderedList | ordered_keys() | fresh Array<int>, O(n) |
| removeAll/clear | clear() | O(capacity), scrubs links and values |
| insertInFrontOf/insertBehind | insert_before/insert_after | Implemented in Oreslang (the TS source currently throws not-implemented) |

Not ported yet: arbitrary generic key/value types, unbounded growth, hashing, random key/item, true forward/reverse iterators, map/filter/forEach and asynchronous traversal. These must be added only with explicit ownership/snapshot guarantees. Unlike JavaScript's arbitrary-key map, the first slice uses caller-supplied integer keys as stable bounded indices.

## Exact source-level test fixtures

- tests/smoke.ores: empty/singleton/middle/head/tail, insertion at ends and anchors, rejection as no-op, value zero vs absence, reuse, clear.
- tests/stress.ores: repeated relinks under validate(), full capacity, mass removals and reuse.
- tests/edge_cases.ores: capacity boundaries, uninitialized rejection, failed-insert atomicity, detached snapshots and repeated clear.
- tests/relink_matrix.ores: singleton capacity, insertion at both ends, middle removal, key reuse and FIFO draining (executed).
- tests/instances.ores: backing-array isolation and snapshot independence.
- tests/generation_tickets.ores: generation starts at 1, advances on each re-admission, remains stable after rejected duplicate insertion, rejects stale reads/removal after unlink and clear, and preserves unrelated ticket validity.
- tests/integration/java/LinkedQueueTicketDifferentialTest.java: independent Java state-machine oracle driving real Oreslang source, with mandatory directed transitions and three 48-step seeded traces; **unexecuted** on the current draft head.
- tests/negative/aliasing.ores and tests/negative/double_mut_borrow.ores: expected rejection of overlapping exclusive aliases.
- tests/negative/readonly_receiver.ores and tests/negative/readonly_binding.ores: expected rejection of mutation through readonly access.
- tests/negative/escaped_mutation.ores: expected rejection of external mutable field access.
- The Oreslang fixtures return 0 on success and a distinct nonzero failure number.
- tools/source-contract-check/src/main.rs: Rust unit tests enforce receiver signatures, duplicate declaration rejection, mutable backing-array declarations, legacy formatter rejection, fail-closed path traversal and rejection of symlinked source. These are static policy tests, **not** compiler/runtime evidence.

## Execution status

**Current CI admission blocker (2026-10-30):** main commit `704e1a317c79deb7049c915c00b7fbf274792d36` has failed source-contract run `40446300427`, whose sole job `121998300903` completed with **zero executed steps**. This is not evidence that the Rust checker or Oreslang code ran. No private-source cross-organization mirror or external Actions budget may be used without explicit transfer authorization and independent destination approval.


**Historical pre-ticket Java/Truffle source compilation and execution: PASS for the original five positives.** In [exact-source GraalVM CI #38316842944](https://github.com/agent-pontifex/agent-pontifex-daemon/actions/runs/38316842944), all five positive queue fixtures compiled and executed successfully, all five negative fixtures were rejected, and 18 Java ownership regression tests passed. The aliasing tests exposed an actual defect on Java main, fixed in [merged compiler PR #767](https://github.com/ores-truffle-oreslang/oreslang-source.java/pull/767). The mirror compared Git blob SHA values to the source branches; no private checkout tokens or secrets were required.

**Not verified for this new ticket change:** Java/Truffle six-fixture guest execution, saturation behavior at the numeric bound, or LLVM native source lowering, LLVM IR/Clang execution, ASan/UBSan, multithreaded/actor stress, formal proofs and the generic K/V queue. The library remains an experimental bounded integer implementation.

## Acceptance checklist

1. Compile all six positive fixtures and all five negative fixtures using Java/Truffle; execute positives and capture return statuses, and prove negative cases fail for the intended semantic reasons.
2. Migrate the exact same files to LLVM without source ifdefs. Check LLVM verifier, Clang execution, ASan/UBSan and platform ABI behavior.
3. Generate property histories with an independent ordered key list, compare results on every operation and assert validate().
4. Add negative compiler tests for aliasing, mutation during iteration, borrowed state after await, cross-actor queue transfer, stale node handles and writer/reader simultaneous admission.
5. Add scheduler-level concurrency tests proving actor single-flight and read/write exclusion, then separate linearizability histories for combined operations.
6. Restore generic API only after native hash lookup and stable handle ownership/drop semantics are implemented and tested.

A borrow checker can establish *memory access exclusion* under the correct ownership model. It does not guarantee fair locks, atomic compound operations, or correct shutdown and cancellation; those require distinct proofs and tests.

## Formatter and field constraints

Every mutating method now uses an explicit `mut self` receiver. Sources use multiline `if condition then` with no semicolon before `then`; [oreslang-format PR #35](https://github.com/ores-truffle-oreslang/oreslang-format/pull/35) updates the formatter for this canonical output. Statements on distinct lines may omit semicolons in Java's parser where the grammar permits, but internal `for` separators and other punctuation are not optional.

The five backing arrays remain `let mut`: two-phase `initialize()` rebinds them after constructing full replacement storage. Stable `final mut` fields require a constructor-only allocation design and compiler changes documented in [FINAL_FIELDS_RFC.md](../docs/FINAL_FIELDS_RFC.md) and [Java issue #758](https://github.com/ores-truffle-oreslang/oreslang-source.java/issues/758).

## 2026-10-12 follow-up hardening

The read-only observer receiver check was corrected after a failing adversarial test exposed that an anchored method-name regex could still match `pub contains(mut self)`. The negative lookahead now rejects that signature. `validate()` additionally checks that vacated slots retain no payload values. These Oreslang fixture changes have passed Java source compilation and runtime tests on the exact-source runner; LLVM is still untested.

Exact-source Python validation (not Oreslang execution) passed in [funded CI #38312417357](https://github.com/agent-pontifex/agent-pontifex-daemon/actions/runs/38312417357): six adversarial unit tests and the full static checker. All 14 mirrored source/test Git blob SHAs were identical to the checked queue PR tree, except that the corrected checker was mirrored from its subsequent commit `af27743ab696cd0e957739c24ac04e2bb47ae69c` and verified separately byte-for-byte.
