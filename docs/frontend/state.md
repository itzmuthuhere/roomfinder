# Frontend state and data fetching

- Server state through a thin fetch wrapper with typed responses; no global store.
- Bed availability is never cached beyond the current screen; re-fetch on focus.
- Optimistic UI is allowed for owner bed-state toggles, but always reconciled with the server response.
- Offline: owner app queues nothing. A failed state change must be shown as failed, not silently swallowed.
