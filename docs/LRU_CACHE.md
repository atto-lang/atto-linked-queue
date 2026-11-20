# Native bounded integer LRU cache (AttoLang/Oreslang)

Implementation: [src/int_lru_cache.ores](../src/int_lru_cache.ores). No Java, JavaScript, pointers, FFI or runtime reflection. All four integer columns are preallocated before initialization publishes them. Head is the least recently used key; tail is the most recently used.

## Contract

- Construct `new IntLruCache(keyspace, max_entries)` with integer keys in `[0, keyspace)`, `1 <= max_entries <= keyspace <= 65536`.
- Call `initialize()` once. A bad configuration or duplicate initialization fails without publishing a partially initialized cache.
- `put(key, value)` inserts/overwrites and moves the key to MRU, evicting one LRU key when inserting at capacity. Returns false for invalid keys or an uninitialized cache. Zero values are valid.
- `get(key)` returns `Option<int>` and refreshes recency. `peek(key)` reads without touching. `touch(key)` refreshes without allocating a node.
- `remove(key)` returns an owned snapshot `Option<int>`, unlinks, and zeroes the discarded value. `clear()` scrubs all slots.
- `least_recent_key()`, `most_recent_key()`, and `ordered_keys()` are observational; ordered_keys returns a detached snapshot.
- `validate()` checks forward/reverse links, occupancy, zero-scrubbed free slots, head/tail and size invariants. O(keyspace), never on hot path.

`put`, `peek`, `get`, `touch`, `contains`, and `remove` are O(1), apart from any backend allocation performed to represent returned `Option` values. Initialization/clear/validation are O(keyspace); diagnostic ordered_keys is O(entries). Storage is O(keyspace), not O(max_entries). There is no TTL, arbitrary-key hashing, concurrent access or generic K/V yet.

## Ownership and security

Instances are **single-owner/actor-confined**, not thread-safe. The direct-address key is not a generation-protected handle or security capability: a stale key can refer to a later insertion, so callers must enforce tenant/actor authority independently. No cache operation grants authority to an actor. Internal arrays and links never escape; no blocking I/O occurs. A producer must not share a mutable cache between Tasks without an independently checked exclusion/ownership protocol.

This is a **language-level implementation candidate**, not an LLVM-native cache runtime primitive or a full cross-backend performance claim. The source loader and Array<int> lowering must actually compile this file on each backend before any parity claim.

## Native guest acceptance

- `tests/lru_smoke.ores`: initialization, invalid keys, capacity one, missing/zero values, get-vs-peek recency, overwrite, remove, eviction, stale-key reuse, scrub.
- `tests/lru_stress.ores`: bounded churn, 2,048 insertions, repeated link validation, deletion and reuse.
- Both must be compiled and run as guest code with exit 0 by Java/GraalVM, LLVM, Wasm, Cranelift and .NET on their exact source heads. **Do not count a host reimplementation, source inspection, or steps=null as guest execution.**
- Add a reference-model differential generator, independent owner/actor tests, abrupt cancellation and AOT allocation/GC measurements after guest execution exists.
- Do not enable shared-state access or merge under a release-parity label without independent review and nonzero-step exact-head CI receipts.
