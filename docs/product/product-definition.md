# RoomFinder Chennai — product definition v1

Source of truth for product behaviour. Written 2026-09-22.

## The journey we are fixing (founder's own, Chennai, first job)

1. Searched road by road on foot.
2. Googled "Boys PG in T Nagar" → ~10 Maps results.
3. Judged them by photos and Google reviews.
4. Called them one by one to find who had vacancy and when the owner would be there.
5. Visited, inspected, liked it.
6. Paid advance and joined.

**Where it breaks:** steps 3 and 4. Maps photos are the owner's marketing, reviews are thin, and vacancy is unknowable without a phone call.

## Why weekly vacancy polling was rejected

An earlier draft proposed a Friday WhatsApp ping to owners to confirm vacancy. Unacceptable for a booking product: a bed taken on Monday would still show free until Friday, and one wasted trip destroys a seeker's trust permanently.

**The app is the source of truth for inventory, not a mirror of it.** That is how RedBus and OYO avoid stale data: the supplier's own daily operations run inside the platform, so inventory updates itself as a by-product. A bus operator's seat chart *is* RedBus's chart.

Consequence: **the owner app comes first and must be useful daily**, because only daily use produces real-time accuracy. Without it, this becomes NoBroker with fewer listings.

## Inventory model (bed-level, real time)

| State | Meaning | Set by |
| --- | --- | --- |
| VACANT | Free now, bookable | Owner, or system when a tenant exits |
| HELD | Reserved for a named seeker, expires automatically | System, on booking |
| BOOKED | Advance paid, awaiting move-in | Owner confirms payment |
| OCCUPIED | Tenant living there | System, on move-in date |
| ON_NOTICE | Occupied, free from a known date; bookable in advance | Owner, when notice is given |
| BLOCKED | Maintenance, owner's own use | Owner |

- A hold expires in 24 hours and the bed returns to VACANT automatically. No human step.
- Two seekers can never hold the same bed.
- ON_NOTICE beds show "available from 5 Oct", which is what most PG demand actually looks like.
- Every change is timestamped and audited.

## Two booking tiers, like Airbnb

**Instant Book** — owners who use the app daily (rent register and notices live here). Booking locks the bed immediately. Ranks first, carries a badge.

**Request to Book** — owners not yet on the register. 30 minutes to accept during the day; no response lapses the request and the seeker sees alternatives. Ranks lower, labelled "confirmation needed".

Instant Book is earned by accuracy. Two false availabilities drops an owner back. Ranking is the only enforcement that works without a contract.

## The guarantee

If a seeker arrives and the bed is not available as booked: full refund of anything paid, plus an alternative arranged from a nearby listed PG the same day. Repeat offenders are dropped. Without this promise, "booking" is a phone call with extra steps.

## Monthly only — nightly stays are a different business

| | Monthly PG (core) | Nightly stay |
| --- | --- | --- |
| Commitment | 6–12 months, ₹15,000–25,000 advance | 1–3 nights, ₹300–800 per bed |
| Book unseen? | Rarely; visiting first is the norm | Yes, routinely |
| Supply | Any PG | Only PGs willing to take short stays |
| Regulation | Tenancy, no lodging licence | Lodging-house rules, guest ID records, possible registration and GST |
| Competitor | Brokers, Maps, NoBroker | OYO, Zostel, budget hotels |

Build the bed-state engine once, launch monthly first, open a nightly toggle later only after a lawyer confirms Tamil Nadu requirements.

## Booking flow

| Step | What happens |
| --- | --- |
| Discover | Filtered search, live bed states, photos and video walkthrough |
| Shortlist | Compare up to 3 |
| Book a visit | Slot chosen; address and contact revealed |
| Visit | Seeker inspects |
| Reserve | ₹500–1,000 token holds the bed 24 hours, adjusted against rent, refunded if the PG differs from the listing |
| Join | Advance and rent paid directly to the owner; the app never holds rent or deposit |

Seekers who cannot visit (moving from another city) get a video walkthrough on call with the caretaker, booked through the app, plus the same guarantee.

## Listing fields

Fixed at onboarding: gender type; sharing types and bed counts; rent per sharing type, deposit, notice period, lock-in; AC/fan, attached or common bath, hot water, lift, power backup; food included, veg/non-veg/both, meal timings; Wi-Fi, washing machine, laundry, housekeeping, parking; rules (entry closing time, visitors, guest stay, alcohol, cooking); documents required; location pin, nearest bus stop/metro, walking minutes to IT parks or colleges; photos per sharing type plus bathroom, kitchen, entrance, corridor; one 60-second video walkthrough; owner/caretaker name and visit timings.

System-owned: bed states, available-from dates, last-updated timestamp.

**Not in V1:** weekly dish schedule. Owners will not maintain it, and a stale menu makes the listing look dead.

## Owner app

1. Bed grid with states, one tap to change
2. Booking and visit requests with accept/decline and a response timer
3. Rent register: who paid, who is pending, one-tap WhatsApp reminder with UPI link
4. Notices: mark a tenant leaving, which automatically opens the bed for advance booking
5. Complaints log
6. Monthly summary: collected, pending, occupancy

Items 3 and 4 are what make 1 and 2 accurate. They are the mechanism, not later features.

## Seeker app

Search and filters; listing page with full details, photos and video; compare; visit booking; token reserve; reviews from placed tenants; after move-in "my PG" with rent due date, complaints and notice request.

## Money

Settled by field test, not at the desk: owner pays per move-in (₹1,000–2,000 or 20% of one month's rent), owner subscription (₹499–999/month including the register), or a seeker-side token. Free until 10 move-ins have happened through the app.
