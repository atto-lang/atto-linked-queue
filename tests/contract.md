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
- tests/instances.ores: backing-array isolation and snapshot independence.
- tests/negative/aliasing.ores and tests/negative/double_mut_borrow.ores: expected rejection of overlapping exclusive aliases.
- tests/negative/readonly_receiver.ores and tests/negative/readonly_binding.ores: expected rejection of mutation through readonly access.
- tests/negative/escaped_mutation.ores: expected rejection of external mutable field access.
- The tests return 0 on success and a distinct nonzero failure number.

## Execution status

The files are authored against the Java/Truffle Oreslang syntax visible in current upstream examples and tests, but **no Java build, LLVM compiler invocation, LLVM IR verification or Clang execution has succeeded for this branch**. A static source check has inspected all the `.ores` files; that is not a compiler or runtime test. Code has not been compiler-verified. Test programs must not be called 'passing' merely because they are committed.

Current LLVM main documents a restricted stack-class and actor scalar slice. Native array/heap container lowering, generational handles, borrow projections, and generic maps are outstanding. CI should be wired to an exact pinned Java/LLVM compiler tree when available, with real run URLs and compile/output checks. A green text-lint job is not evidence of language execution.

## Acceptance checklist

1. Compile both test fixtures using Java/Truffle; execute them and capture exit/return status.
2. Migrate the exact same files to LLVM without source ifdefs. Check LLVM verifier, Clang execution, ASan/UBSan and platform ABI behavior.
3. Generate property histories with an independent ordered key list, compare results on every operation and assert validate().
4. Add negative compiler tests for aliasing, mutation during iteration, borrowed state after await, cross-actor queue transfer, stale node handles and writer/reader simultaneous admission.
5. Add scheduler-level concurrency tests proving actor single-flight and read/write exclusion, then separate linearizability histories for combined operations.
6. Restore generic API only after native hash lookup and stable handle ownership/drop semantics are implemented and tested.

A borrow checker can establish *memory access exclusion* under the correct ownership model. It does not guarantee fair locks, atomic compound operations, or correct shutdown and cancellation; those require distinct proofs and tests.

## Formatter and field constraints

Every mutating method now uses an explicit `mut self` receiver. Sources use multiline `if condition then` with no semicolon before `then`; [oreslang-format PR #35](https://github.com/ores-truffle-oreslang/oreslang-format/pull/35) updates the formatter for this canonical output. Statements on distinct lines may omit semicolons in Java's parser where the grammar permits, but internal `for` separators and other punctuation are not optional.

The four backing arrays remain `let mut`: two-phase `initialize()` rebinds them after constructing full replacement storage. Stable `final mut` fields require a constructor-only allocation design and compiler changes documented in [FINAL_FIELDS_RFC.md](../docs/FINAL_FIELDS_RFC.md) and [Java issue #758](https://github.com/ores-truffle-oreslang/oreslang-source.java/issues/758).
