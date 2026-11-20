# Final-field and mutable-referent RFC

Status: PROPOSED — requires coordinated Java/Truffle, LLVM and oreslang-format admission. Do not use `final mut` in production sources until both compilers recognize and enforce it.

## Motivation: binding identity and mutable payloads are different effects

A queue should have stable backing-array identity, while still allowing writes to array elements:

```ores
define class Queue as
  final mut Array<int> slots = new Array<int>()

  pub put(mut self)(int value) -> void {
    self.slots.add(value)
  }
end
```

The syntax above is **illustrative**, not a verified build fixture. Once initialized, `self.slots = new Array<int>()` is a compile error. `self.slots.add(...)` is admitted only through an exclusive writable receiver/borrow. The immutable counterpart:

```ores
final Map<int, int> m = new Map<int, int>()
final mut Map<int, int> p = new Map<int, int>()
```

Both cannot be rebound. `m.set(k,v)` is rejected through the read-only field view; `p.set(k,v)` requires an exclusive owner or write guard. A final binding is not a guarantee that some *other* authorized alias can never change the referenced heap object: deep immutability requires freezing or a transitive immutable-sharing capability.

## Proposed four permission combinations

| Field spelling | Rebind after initialization | Mutate referent through this field | Relation to existing bindings |
| --- | --- | --- | --- |
| `final T f` | no | no | runtime fixed/read-only; similar to `const` |
| `final mut T f` | no | yes, with exclusive loan | similar to `val` / `const mut` |
| `let T f` | yes | no | existing `let` semantics |
| `let mut T f` | yes | yes, with exclusive loan | existing `let mut` semantics |

`final` is a field-oriented spelling; do not casually rewrite `val` and `const` local bindings or erase their existing meaning. Parsing must be contextual: current actor state already accepts `final name: Type` for a distinct read-only shape. Support both spellings without mistaking `final` for a method name or a user identifier.

## Single-assignment initialization

- A final field is assigned exactly once, by a field initializer or during construction. Constructor assignment consumes a unique uninitialized-field capability.
- No constructor may publish `self` or spawn an actor while required final fields are uninitialized. Reject all paths leaving fields uninitialized and second assignments on either branch.
- `final mut` can mutate the *value* during the constructor and later via an exclusive receiver, but cannot rebind a value after initialization.
- Inherited final fields may be initialized only by the owning base initializer, not by arbitrary child mutations.
- Generic referent write permission must be checked transitively: indexing, nested fields, mutating methods, closures, captured aliases and FFI effects.
- For actors, prohibit direct access to `state` outside a compiler-authorized handle_state callback; `final mut` never bypasses the actor state seal.

The queue currently has two-phase initialize() with transactional replacement of its four arrays. Hence the source deliberately retains `let mut Array<int>` for those four fields until construction can allocate and initialize them atomically. Changing them to `final mut` **now** would make current initialize() invalid.

## Compile-time and runtime hardening gates

1. Lexer/parser/AST accepts `final T f` and `final mut T f` for classes and actors, preserving existing `final name: Type` actor compatibility; rejects duplicate `mut` and `final let`.
2. Static checker denies `self.f = ...` after construction for either final form. Denies mutating method calls through plain `final`; accepts them through `final mut` only with a proven exclusive receiver.
3. Borrow checker rejects two simultaneous exclusive loans, a write while immutable readers are live, moving a still-borrowed final referent, an escaping `&mut` and mutation across suspension/reentrancy.
4. Runtime preserves constructor-once semantics and guards dynamic/FFI reflection paths from bypassing immutable fields. Native LLVM must emit equivalent immutable field metadata/provenance rather than merely parse the keywords.
5. Formatter preserves `final mut` order and emits multiline `if condition then`; semicolons on safe newline statement boundaries remain optional in the language. It must not rewrite `final mut` to `final`.
6. Exact-source Java and LLVM positive/negative tests, actor and shared-memory stress, sanitizer jobs, and formatter idempotence before marking the feature stable.

### Why reader-writer locks are separate

`final mut` answers whether the object identity can change and whether a *permitted owner* can mutate its contents. It is NOT a lock, cannot prove inter-thread exclusion by itself and does not solve logical races. An actor-confined queue needs only its exclusive state loan. A concurrently shared queue needs synchronization/guard admission and memory ordering in addition to the field permissions.
