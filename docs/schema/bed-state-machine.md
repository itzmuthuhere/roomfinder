# Bed state machine

```mermaid
stateDiagram-v2
  [*] --> VACANT
  VACANT --> HELD: seeker holds
  HELD --> BOOKED: owner confirms / token paid
  HELD --> VACANT: hold expires
  BOOKED --> OCCUPIED: move-in date
  BOOKED --> VACANT: cancelled
  OCCUPIED --> ON_NOTICE: tenant gives notice
  ON_NOTICE --> VACANT: vacate date
  VACANT --> BLOCKED: owner blocks
  BLOCKED --> VACANT: owner unblocks
```

Invariants:

1. One active hold per bed, enforced by a partial unique index and a conditional update.
2. Every transition writes `bed_state_log` with actor and reason.
3. Holds expire by scheduled job, never by a human action.
4. `ON_NOTICE` beds are bookable with `available_from`.

The concurrency test (50 parallel holds on one bed, exactly one winner) must never be deleted.
