create table otp_request (
  id          bigserial primary key,
  phone       text not null,
  code_hash   text not null,
  role        text not null check (role in ('OWNER','SEEKER')),
  expires_at  timestamptz not null,
  consumed_at timestamptz,
  attempts    int not null default 0,
  created_at  timestamptz not null default now()
);

-- latest-otp-for-phone lookups and the rate-limit count both filter on (phone, created_at)
create index otp_request_phone_idx on otp_request (phone, created_at desc);
