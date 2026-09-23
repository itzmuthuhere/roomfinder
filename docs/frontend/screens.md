# Screens

| Screen | Audience | Purpose | Key states |
| --- | --- | --- | --- |
| Search | Seeker | Filter by area, budget, sharing, gender, food | empty, loading, no results, error |
| Listing | Seeker | Photos, video, rules, documents, bed availability | bed available / on notice / full |
| Visit booking | Seeker | Choose slot, confirm | slot taken, hold failed |
| My bookings | Seeker | Upcoming visits, holds with countdown | expired hold |
| Bed grid | Owner | See and change bed states | offline, conflicting update |
| Rent register | Owner | Who paid, who is pending, send reminder | partial payment |
| Notices | Owner | Mark leaving tenant, set available-from | |

Every screen documents its empty, loading and error state. A screen without those three is not finished.
