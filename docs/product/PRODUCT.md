# RoomFinder — product context

The single page a coding session should read to understand *what* is being built and *why*.
Full market research lives outside the repo, in the Claude project "product".

## What it is

A monthly PG (paying guest) booking app for Chennai. Seekers find verified PGs with real-time
bed availability, book a visit, then move in. PG owners run their daily operations in the same
system — bed states, rent register, notices — which is what keeps availability true.

## Who it serves

**Seeker:** 21–28, arriving in Chennai for a job or college, budget ₹7,000–15,000/month,
searching from another city, decides in 3–10 days, high anxiety.

**Owner (the one who pays):** 1–8 PG buildings, 40–400 beds. An empty bed costs ₹8,000–15,000
a month. Today they pay brokers roughly half a month's rent per tenant.

## The problem being solved

Today: Google "Boys PG in T Nagar" → 10 Maps results → judge by the owner's own photos →
call ten numbers to find who has a vacancy → visit → pay advance.

Steps 3 and 4 are broken: photos are marketing, reviews are thin, vacancy is unknowable without
a phone call. Steps 5 and 6 are fine — nobody pays ₹20,000 of advance for a room they have not
seen, and the product must not pretend otherwise.

## Product rules (these shape every feature)

1. **The app books a visit, not a bed sight-unseen.** An optional token hold comes after the visit.
2. **Real-time availability or nothing.** The database is the source of truth. Never cache it,
   never poll owners weekly, never show a bed whose state we are unsure of.
3. **The owner app must be useful daily.** Rent register and notices are not "later features";
   they are the mechanism that keeps bed states accurate.
4. **We never hold rent or deposits.** Money goes tenant → owner directly. No escrow.
5. **Owner tools are free forever.** We charge for tenants delivered, not for software.
6. **One micro-market first.** 25+ PGs and 500+ beds live in one Chennai area before any seeker
   traffic. A search returning four results kills the app's reputation permanently.
7. **Monthly stays only.** Nightly stays need lodging-house registration and are out of scope.

## Competitive position

Google Maps is the real competitor, not NoBroker. Maps has directions and reviews but cannot
have: vacancy today, the real rent and deposit, neutral photos, house rules, or reviews from
people who actually lived there. Freshness of data is the entire moat.

## Monetisation (to be settled by field test, not in code)

Owner pays per move-in (₹1,000–2,000 or 20% of one month's rent), or an owner subscription, or
a seeker-side verified-visit fee. Nothing is charged until 10 move-ins have happened through the
app. Do not build billing before that decision.

## Deliberately out of scope

Nightly stays, rent collection by us, deposits held by us, lending, brokerage, a social feed,
roommate matching, and anything that requires a licence.
