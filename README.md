> **AI agent instructions (organization-wide):** Before working on code, issues, or pull requests, read and follow the [organization `AGENTS.md`](https://github.com/ores-truffle-oreslang/.github/blob/main/AGENTS.md), including its **merge-first stealth-development policy**. Honor any repository-local `AGENTS.md`, explicit holds, and GitHub-enforced rules.

# oreslang-linked-queue

## Oreslang to AttoLang migration (2026-11-18)

**AttoLang** (formerly **Oreslang**) is the current public language identity. This `ores-truffle-oreslang/oreslang-linked-queue` checkout retains old naming. Its AttoLang counterpart for new contributions is [atto-lang/atto-linked-queue](https://github.com/atto-lang/atto-linked-queue); this link **does not imply an in-place transfer, identical history, migrated issues/PRs or feature parity**. The old source and commands below remain useful as historical checkout documentation, not a guarantee of current language conformance.

**[atto-lang/atto-spec](https://github.com/atto-lang/atto-spec)** is the canonical language specification. The portable library source belongs in [atto-common-source](https://github.com/atto-lang/atto-common-source), shared positive/negative guest tests in [atto-platform-conformance](https://github.com/atto-lang/atto-platform-conformance), and compiler-specific implementations in `atto-source.*`. The language's current proposed direction includes canonical `.atto`, `snake_case`, `borrow!`/`take!`/`share!`/`copy!`/`copy!!`, [implicit borrow defaults](https://github.com/atto-lang/atto-spec/blob/main/language/implicit-argument-borrow-v1.md), [`define const` including permitted `os.read_env`](https://github.com/atto-lang/atto-spec/blob/main/language/file-module-const-v1.md), and [`<<<`/`>>>`/`ashr` shifts vs. `<<`/`>>` streams](https://github.com/atto-lang/atto-spec/blob/main/language/bitwise-and-stream-operators-v1.md). Check individual spec admission status; **a design target is not proof of execution**.

Keep legacy `.ores` fixtures, CLI/package/ABI names, and version-pinned commands where they document code still in this tree. Avoid Python in new AttoLang tooling/tests/CI; Rust support tools are preferred. Only claim cross-backend support with exact SHA/tree, target/toolchain, real executed positive and negative guest tests; report failed, unsupported and not-executed explicitly. Empty-step/host-only CI is not guest conformance.

An **Oreslang-source** port of [ORESoftware/linked-queue](https://github.com/ORESoftware/linked-queue).

**Status: experimental.** The first implementation is a bounded integer-key/integer-value doubly linked queue. Unlike the generic TypeScript version, it uses private index-based arrays for prev, next, occupancy and values, avoiding cyclic heap pointers and exposing no node aliases.

**Backend limitation (2026-10-12):** Java/Truffle has native Oreslang collection members, but the published LLVM source-compiler slice does not yet lower array-backed classes or general heap-linked graphs. This code has **not** been verified by a native LLVM compiler or a multithreaded runtime. No Java/JS interop is used in library source.

## API and complexity

IntLinkedQueue exposes enqueue, add_to_front, dequeue, remove_last, remove(key), insert_before, insert_after, contains, get, first, last and clear. Individual hot-path operations run in O(1), initialization/clear in O(capacity), and memory is O(capacity). Keys are unique integers in [0, capacity), so this is not yet a drop-in generic K/V port. Missing reads return Option rather than colliding with integer zero. Invalid keys and duplicate insertions do not mutate the queue.

The TypeScript original has arbitrary K/V, iterator, random-key and async methods. They are tracked as future work; the source's three insertion methods are advertised in its README but currently throw not implemented.


## Java/Truffle verification

The single-owner Oreslang queue now compiles and runs under the Java/Truffle source compiler with no Java/JS code in the library. An exact-source, funded [GraalVM CI run #38316842944](https://github.com/agent-pontifex/agent-pontifex-daemon/actions/runs/38316842944) compiled and executed all five positive fixtures (`smoke`, `stress`, `instances`, `edge_cases`, `relink_matrix`) and verified all five negative fixtures fail. Two formerly accepted simultaneous mutable-borrow arguments are rejected by the merged [Java borrow checker fix #767](https://github.com/ores-truffle-oreslang/oreslang-source.java/pull/767). The same run passed 18 ownership-related JUnit tests, including six invocation-borrow regressions. The LLVM backend and true multithreaded/actor runtime have **not** been verified; this remains an experimental bounded-integer queue.

## Ownership and concurrency

One mutable owner per queue, with explicit `mut self` receivers on all mutating operations. A borrow checker should forbid simultaneous mutable aliases while allowing immutable readers; it cannot prove logical FIFO correctness or that a sequence of several calls is atomic. The default deployment is actor-confined with serialized mutations, avoiding any lock. A future shared-memory wrapper may use reader/writer guards, but they must be nonescaping, cancellation-safe and not held over await. Actor scheduler carriers must never park on a contested lock.

## Example

```ores
import class IntLinkedQueue from "./src/int_linked_queue";

pub routine main() -> int {
  val IntLinkedQueue queue = new IntLinkedQueue(16);
  if !queue.initialize() then
    return 1;
  fi
  queue.enqueue(3, 42);
  queue.enqueue(4, 99);
  val entry = queue.dequeue().unwrap();
  stdio.println(entry.value); // 42
  return 0;
}
```

See examples/basic.ores, docs/CONCURRENCY.md, docs/FINAL_FIELDS_RFC.md and tests/contract.md for details. Source tests cover smoke, stress, boundaries, per-instance isolation and intentional ownership/privacy failures.

The canonical `if condition then` formatter is merged as [oreslang-format PR #35](https://github.com/ores-truffle-oreslang/oreslang-format/pull/35); the proposed final-field compiler support is tracked in [Java issue #758](https://github.com/ores-truffle-oreslang/oreslang-source.java/issues/758).

Outstanding work is tracked in [native compiler parity](../../issues/1), [generic queue support](../../issues/2) and [actor/RW concurrency adapters](../../issues/3).

## Development policy: no Python

Python is prohibited in this repository for source code, tests, build tooling, code generation, CI, and automation. Existing Python checks must be ported to behavior-equivalent Rust with equivalent adversarial coverage before removal. Do not add Python source/notebooks, Python dependency manifests, `python`/`python3`, `pip`, `pytest`, `poetry`, `uv`, or Python setup actions. Rust tooling must pass `cargo fmt`, `cargo clippy -- -D warnings`, and `cargo test`.
