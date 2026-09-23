create extension if not exists postgis;

create table app_user (
  id          bigserial primary key,
  phone       text unique not null,
  name        text,
  role        text not null check (role in ('OWNER','SEEKER','ADMIN')),
  created_at  timestamptz not null default now()
);

create table pg_property (
  id              bigserial primary key,
  owner_id        bigint not null references app_user(id),
  name            text not null,
  gender_type     text not null check (gender_type in ('BOYS','GIRLS','UNISEX','WORKING_WOMEN')),
  address         text not null,
  area            text not null,
  location        geography(point,4326) not null,
  entry_close_time time,
  visitors_allowed boolean,
  food_included   boolean,
  food_type       text,
  meal_timings    jsonb,
  amenities       jsonb,
  docs_required   jsonb,
  notice_period_days int,
  lock_in_months  int,
  status          text not null default 'DRAFT' check (status in ('DRAFT','LIVE','PAUSED','REMOVED')),
  instant_book    boolean not null default false,
  created_at      timestamptz not null default now()
);
create index pg_property_location_idx on pg_property using gist (location);
create index pg_property_area_idx on pg_property (area) where status = 'LIVE';

create table room (
  id            bigserial primary key,
  pg_id         bigint not null references pg_property(id),
  room_no       text,
  sharing_type  text not null check (sharing_type in ('SINGLE','DOUBLE','TRIPLE','FOUR_PLUS')),
  ac            boolean not null default false,
  attached_bath boolean not null default false,
  rent_per_bed  int not null,
  deposit_amount int not null
);

create table bed (
  id               bigserial primary key,
  room_id          bigint not null references room(id),
  bed_label        text,
  state            text not null default 'VACANT'
                   check (state in ('VACANT','HELD','BOOKED','OCCUPIED','ON_NOTICE','BLOCKED')),
  available_from   date,
  current_tenant_id bigint references app_user(id),
  state_changed_at timestamptz not null default now()
);
create index bed_state_idx on bed (state);
create index bed_room_idx on bed (room_id);

create table bed_state_log (
  id         bigserial primary key,
  bed_id     bigint not null references bed(id),
  from_state text,
  to_state   text not null,
  changed_by bigint references app_user(id),
  reason     text,
  created_at timestamptz not null default now()
);

create table booking (
  id              bigserial primary key,
  bed_id          bigint not null references bed(id),
  seeker_id       bigint not null references app_user(id),
  kind            text not null check (kind in ('VISIT','RESERVE')),
  status          text not null check (status in ('REQUESTED','CONFIRMED','EXPIRED','CANCELLED','COMPLETED','MOVED_IN','NO_SHOW')),
  visit_slot      timestamptz,
  hold_expires_at timestamptz,
  token_amount    int not null default 0,
  created_at      timestamptz not null default now()
);
create unique index one_active_hold_per_bed
  on booking (bed_id)
  where kind = 'RESERVE' and status in ('REQUESTED','CONFIRMED');
create index booking_expiry_idx on booking (hold_expires_at) where status = 'CONFIRMED';

create table tenancy (
  id            bigserial primary key,
  bed_id        bigint not null references bed(id),
  tenant_id     bigint not null references app_user(id),
  move_in_date  date not null,
  rent_amount   int not null,
  rent_due_day  int not null default 5,
  notice_date   date,
  vacate_date   date,
  status        text not null check (status in ('ACTIVE','ON_NOTICE','ENDED'))
);

create table rent_payment (
  id         bigserial primary key,
  tenancy_id bigint not null references tenancy(id),
  for_month  date not null,
  amount     int not null,
  paid_on    date,
  status     text not null check (status in ('PENDING','PAID','PARTIAL'))
);

create table media (
  id         bigserial primary key,
  pg_id      bigint not null references pg_property(id),
  room_id    bigint references room(id),
  url        text not null,
  kind       text not null check (kind in ('PHOTO','VIDEO')),
  sort_order int not null default 0
);

create table review (
  id              bigserial primary key,
  pg_id           bigint not null references pg_property(id),
  tenant_id       bigint not null references app_user(id),
  tenancy_id      bigint not null references tenancy(id),
  food            int, cleanliness int, water int, wifi int, owner_behaviour int,
  deposit_returned boolean,
  comment         text,
  created_at      timestamptz not null default now()
);
