# oreslang-linked-queue

An **Oreslang-source** port of [ORESoftware/linked-queue](https://github.com/ORESoftware/linked-queue).

**Status: experimental.** The first implementation is a bounded integer-key/integer-value doubly linked queue. Unlike the generic TypeScript version, it uses private index-based arrays for prev, next, occupancy and values, avoiding cyclic heap pointers and exposing no node aliases.

**Backend limitation (2026-10-12):** Java/Truffle has native Oreslang collection members, but the published LLVM source-compiler slice does not yet lower array-backed classes or general heap-linked graphs. This code has **not** been verified by a native LLVM compiler or a multithreaded runtime. No Java/JS interop is used in library source.

## API and complexity

IntLinkedQueue exposes enqueue, add_to_front, dequeue, remove_last, remove(key), insert_before, insert_after, contains, get, first, last and clear. Individual hot-path operations run in O(1), initialization/clear in O(capacity), and memory is O(capacity). Keys are unique integers in [0, capacity), so this is not yet a drop-in generic K/V port. Missing reads return Option rather than colliding with integer zero. Invalid keys and duplicate insertions do not mutate the queue.

The TypeScript original has arbitrary K/V, iterator, random-key and async methods. They are tracked as future work; the source's three insertion methods are advertised in its README but currently throw not implemented.

## Ownership and concurrency

One mutable owner per queue. A borrow checker should forbid simultaneous mutable aliases while allowing immutable readers; it cannot prove logical FIFO correctness or that a sequence of several calls is atomic. The default deployment is actor-confined with serialized mutations, avoiding any lock. A future shared-memory wrapper may use reader/writer guards, but they must be nonescaping, cancellation-safe and not held over await. Actor scheduler carriers must never park on a contested lock.

## Example

```ores
import class IntLinkedQueue from "./src/int_linked_queue";

pub routine main(): int {
  let IntLinkedQueue queue = new IntLinkedQueue(16);
  if !queue.initialize(); do return 1; fi
  queue.enqueue(3, 42);
  queue.enqueue(4, 99);
  val entry = queue.dequeue().unwrap();
  stdio.println(entry.value); // 42
  return 0;
}
```

See examples/basic.ores, docs/CONCURRENCY.md and tests/contract.md for details. Source tests currently include smoke, stress, per-instance isolation and two intentionally failing ownership/privacy fixtures.

Outstanding work is tracked in [native compiler parity](../../issues/1), [generic queue support](../../issues/2) and [actor/RW concurrency adapters](../../issues/3).
