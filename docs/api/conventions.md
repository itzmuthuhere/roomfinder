# API conventions

- Auth: phone OTP → JWT bearer. Owner and seeker roles are separate claims.
- All money in paise, integers. All timestamps ISO-8601 UTC.
- Errors: `{ "code": "BED_NOT_AVAILABLE", "message": "Someone just booked this bed" }` with a real HTTP status.
- Idempotency: booking endpoints accept `Idempotency-Key`; replaying a key returns the original result.
- Pagination: `?page=0&size=20`, response envelope `{ items, page, size, total }`.
- Versioning: path prefix `/v1`. Breaking changes ship as `/v2`, never as a silent change.

## Error codes

| Code | HTTP | Meaning |
| --- | --- | --- |
| BED_NOT_AVAILABLE | 409 | The bed left VACANT before the hold was taken |
| HOLD_EXPIRED | 410 | The hold window passed |
| PG_NOT_LIVE | 404 | Listing is draft, paused or removed |
| OTP_INVALID | 401 | Wrong or expired OTP |
