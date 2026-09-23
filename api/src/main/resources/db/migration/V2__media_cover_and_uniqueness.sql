alter table media add column is_cover boolean not null default false;
alter table media add column created_at timestamptz not null default now();

-- at most one cover photo per PG
create unique index media_one_cover_per_pg on media (pg_id) where kind = 'PHOTO' and is_cover;

-- at most one video per PG
create unique index media_one_video_per_pg on media (pg_id) where kind = 'VIDEO';
