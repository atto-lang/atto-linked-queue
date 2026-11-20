# Concurrency and memory-safety contract

## Baseline implementation: one owner, no lock

The current IntLinkedQueue stores four private arrays and three scalar metadata fields. Links are integer keys; the queue instance alone owns them. The only exported item is a new QueueEntry value (int key, int value), never a reference to internal arrays. An insertion/relink/removal consists of multiple writes. Treat **each public operation as requiring exclusive mutable access to the entire queue**; do not share this object across threads with unguarded method calls.

Required borrow/effect rules (normative design, not proof of current LLVM enforcement):

1. Only one live mutable loan (&mut Queue) OR any number of immutable loans (&Queue); never both. Subfield borrows of head/tail/count/arrays must be recognized as aliases of the same queue.
2. A method returning a fresh value/snapshot transfers no loan. No iterator, callback, or returned object may retain a mutable alias into this queue.
3. A mutable loan must not cross await/suspend, actor send, foreign callback, or a reentrant actor turn. A read loan cannot be upgraded to write while other readers are alive.
4. Moving a queue transfers its ownership. A moved-from queue cannot be called or dropped twice. Heap/arena reclamation must wait for all pinned readers and pending callbacks.
5. A failed operation (duplicate or invalid key/anchor) leaves the full public state and every link unchanged.

Java/Truffle source collection support does NOT imply native LLVM arrays, alias tracking, drop glue or threading semantics. LLVM must fail closed on unsupported constructs, rather than silently compiling unsafe shared access.

## Preferred concurrent adapter: an actor

Put the queue inside the actor's private state. Actors provide a **single executing turn** at a time, regardless of carrier or preemption; messages are serialized through its mailbox. Producers send enqueue requests; consumers send dequeue requests with replies. The queue is never sent or shared. If an actor is reentrant at suspend, the compiler must end each state-loan before suspension and revalidate state on resume. A returned queued item is a value snapshot.

For state-sealed actors, the intended direction is:

~~~ores
// DESIGN SKETCH ONLY: not compiled by the current LLVM source slice.
define serial actor QueueOwner as
  state { queue: IntLinkedQueue }
  pub enqueue(int key, int value): bool using state.write {
    return self.handle_state(critical |s| -> s.queue.enqueue(key, value));
  }
end
~~~

Here critical scopes the *state callback*, not an OS mutex or blocking carrier. The runtime must prohibit direct self.state access from other actor methods. Requests that need linearizability across multiple operations should be handled in **one** actor turn/callback, not separate round trips.

## Alternative concurrent adapter: reader-writer lock

Only offer SharedLinkedQueue when there is measured shared-memory demand. A borrow checker alone cannot infer safe synchronization when multiple threads intentionally share a mutable queue; it must demand an explicit synchronized sharing capability.

- Acquire READ for contains, get, size, first, last and any snapshot copy. Many readers can coexist, never overlap a writer.
- Acquire WRITE for initialize, enqueue, add_to_front, insert_before, insert_after, remove, dequeue, remove_last and clear. Relinking head, tail, count and neighbor slots is one atomic protected operation.
- Use a single lock for the whole queue. Per-node locks are unjustified without ordering proofs and ABA-safe lifetime reclamation. Writer-preference or bounded fairness avoids starvation; use a single FIFO admission queue if practical.
- Guards are affine/linear, non-clonable and non-escapable. Release exactly once on return, throw, panic, cancellation and actor shutdown. Do not hold synchronous locks over suspend/await, and never park an actor carrier.
- Readers receive immutable snapshots or an explicit nonescaping read guard. No live iterator may outlast its guard. A lock-free peek of head/count is unsafe: it may observe a half-relinked chain.
- Handle lock poison/failure explicitly. Do not treat lock acquisition or failure to enqueue as success.

Suggested source-level words such as critical-read and critical-write are **not implemented keywords**. A future language feature may lower them to compiler-owned guard/ticket effects, but cannot make unprotected field access safe by mere naming.

## Data races versus logical races

Ownership protects data-race freedom *if the runtime enforces the borrow rules on all aliases*. It does not automatically guarantee:
- read-then-remove atomicity (another request may change state between steps);
- fairness, cancellation safety and exactly-once delivery;
- FIFO across multiple producer threads unless sends have a defined admission order;
- ordering between an actor's continuation before and after await.

For these, provide a combined actor message/transaction or hold one verified write guard over the entire synchronous operation.

## Linearization points and invariants

For a guarded/shared queue, each successful operation linearizes while it holds the write permit, after all neighbor/head/tail/count writes and before releasing the permit. Failed operations linearize at validation under the same permit. Reads linearize while the read guard is held. For an actor-confined queue, the same applies within its exclusive state turn.

Invariants:
- Empty iff count=0 iff head=tail=-1.
- A nonempty head has prev=-1 and tail has next=-1.
- Every active key belongs to the one forward chain exactly once, and the reverse chain is its inverse.
- Exactly count active keys exist; no inactive slot retains a link; links never point to inactive/out-of-range keys.
- Any surviving key has its original stored value, except after explicit removal and reinsertion of that key.
- No mutation on duplicate key, invalid key or missing anchor.
- Removal of head/tail/middle updates both adjacent links; clear drops every slot and makes reuse safe.

## Verification gates before declaring concurrency safety

1. Java compiler/type/effect pass on valid queue/tests and negative alias/borrow escapes.
2. Native LLVM compile -> verified IR -> Clang execution on the **exact same** Oreslang source, first with single-owner arrays and then, if needed, generational heap handles.
3. Property/state-machine tests against an independent array oracle, randomized operation traces and explicit validation after each operation.
4. Concurrent actor stress with many producers/consumers and checks for no lost, duplicate, or out-of-order admissions relative to a recorded serial history.
5. Shared adapter: ThreadSanitizer where supported, forced reader/writer/cancel race schedules, fairness/starvation measurements, and a linearizability checker.
6. Fault injection and ASan/UBSan: allocation failures, invalid keys, double release, stale generation, close during wait and arena retirement.
7. Formal checks: actor single-flight invariant and read/write exclusivity are distinct invariants; prove both on state transitions, then tie the compiler's guards to the checked runtime transitions.

Until these gates pass, this is a **single-owner library prototype**, not a thread-safe public queue.
