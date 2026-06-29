-- R__seed_dev_data.sql
-- Repeatable seed for dev/test profiles only. Flyway re-runs on checksum change.
-- All INSERTs are idempotent (ON CONFLICT DO UPDATE / DO NOTHING).
-- Source: Frontend/src/lib/mock-data.ts + lyrics-data.ts.
-- Money fields: decimal cedis × 100 → BIGINT pesewas (INV-11).
-- PRD §5.4 / data-and-migrations §4.2 / §8.

-- ==========================================================================
-- Artist profiles (mock-data.ts `artists`)
-- ==========================================================================

INSERT INTO artist_profile (id, name, image, cover_image, verified, monthly_listeners, followers, bio, location, genres, created_at, updated_at)
VALUES
  ('black-sherif', 'Black Sherif',
   'https://images.unsplash.com/photo-1619983081563-430f63602796?q=80&w=600&auto=format&fit=crop',
   'https://images.unsplash.com/photo-1493225457284-06f22b161460?q=80&w=2000&auto=format&fit=crop',
   TRUE, 2400000, 2400000,
   'Mohammed Ismail Sherif, known as Black Sherif, is a Ghanaian rapper from Konongo whose drill-meets-highlife storytelling has carried Ghanaian music to a global audience.',
   'Konongo, Ghana', ARRAY['Drill','Hiplife'], now(), now()),

  ('burna-boy', 'Burna Boy',
   'https://images.unsplash.com/photo-1493225255756-d9584f8606e9?q=80&w=600&auto=format&fit=crop',
   NULL, TRUE, 18200000, 12400000, NULL, 'Port Harcourt, Nigeria',
   ARRAY['Afrobeats'], now(), now()),

  ('asake', 'Asake',
   'https://images.unsplash.com/photo-1514525253161-7a46d19cd819?q=80&w=600&auto=format&fit=crop',
   NULL, TRUE, 9100000, 6300000, NULL, 'Lagos, Nigeria',
   ARRAY['Afrobeats','Amapiano'], now(), now()),

  ('king-promise', 'King Promise',
   'https://images.unsplash.com/photo-1470225620780-dba8ba36b745?q=80&w=600&auto=format&fit=crop',
   NULL, TRUE, 3200000, 2100000, NULL, 'Accra, Ghana',
   ARRAY['Afrobeats','Highlife'], now(), now()),

  ('lasmid', 'Lasmid',
   'https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?q=80&w=600&auto=format&fit=crop',
   NULL, TRUE, 1400000, 900000, NULL, 'Accra, Ghana',
   ARRAY['Hiplife','Afrobeats'], now(), now()),

  ('camidoh', 'Camidoh',
   'https://images.unsplash.com/photo-1504151932400-72d4384f0e6d?q=80&w=600&auto=format&fit=crop',
   NULL, TRUE, 2100000, 1300000, NULL, 'Accra, Ghana',
   ARRAY['Afrobeats','R&B'], now(), now()),

  ('rema', 'Rema',
   'https://images.unsplash.com/photo-1508700115892-45ecd05ae2ad?q=80&w=600&auto=format&fit=crop',
   NULL, TRUE, 24000000, 15000000, NULL, 'Benin City, Nigeria',
   ARRAY['Afrobeats'], now(), now())
ON CONFLICT (id) DO UPDATE
  SET name              = EXCLUDED.name,
      image             = EXCLUDED.image,
      cover_image       = EXCLUDED.cover_image,
      verified          = EXCLUDED.verified,
      monthly_listeners = EXCLUDED.monthly_listeners,
      followers         = EXCLUDED.followers,
      bio               = EXCLUDED.bio,
      location          = EXCLUDED.location,
      genres            = EXCLUDED.genres,
      updated_at        = now();

-- ==========================================================================
-- Shows (mock-data.ts `upcomingShows`)
-- ==========================================================================

INSERT INTO artist_show (id, artist_id, date, city, venue, position)
VALUES
  ('show-bsherif-1', 'black-sherif', '2026-05-22', 'Accra',  'Independence Square', 0),
  ('show-bsherif-2', 'black-sherif', '2026-06-14', 'Kumasi', 'Baba Yara Stadium',   1),
  ('show-bsherif-3', 'black-sherif', '2026-07-09', 'Tema',   'Beach Festival',       2)
ON CONFLICT (id) DO UPDATE
  SET artist_id = EXCLUDED.artist_id,
      date      = EXCLUDED.date,
      city      = EXCLUDED.city,
      venue     = EXCLUDED.venue,
      position  = EXCLUDED.position;

-- ==========================================================================
-- Albums (mock-data.ts `albums`)
-- ==========================================================================

INSERT INTO album (id, title, artist_id, artist_name, year, cover_image, genres, list_price_minor)
VALUES
  ('iron-boy', 'Iron Boy', 'black-sherif', 'Black Sherif', 2024,
   'https://images.unsplash.com/photo-1614613535308-eb5fbd3d2c17?q=80&w=600&auto=format&fit=crop',
   ARRAY['Hiplife','Drill'], 0),

  ('love-damini', 'Love, Damini', 'burna-boy', 'Burna Boy', 2022,
   'https://images.unsplash.com/photo-1493225255756-d9584f8606e9?q=80&w=600&auto=format&fit=crop',
   ARRAY['Afrobeats'], 0),

  ('the-villain-i-never-was', 'The Villain I Never Was', 'black-sherif', 'Black Sherif', 2022,
   'https://images.unsplash.com/photo-1619983081563-430f63602796?q=80&w=600&auto=format&fit=crop',
   ARRAY['Drill','Hiplife'], 0)
ON CONFLICT (id) DO UPDATE
  SET title            = EXCLUDED.title,
      artist_id        = EXCLUDED.artist_id,
      artist_name      = EXCLUDED.artist_name,
      year             = EXCLUDED.year,
      cover_image      = EXCLUDED.cover_image,
      genres           = EXCLUDED.genres,
      list_price_minor = EXCLUDED.list_price_minor;

-- ==========================================================================
-- Tracks (mock-data.ts `tracks`)
-- price_minor = GHS(n) * 100 pesewas; e.g. GHS(2.5) = 250
-- ==========================================================================

INSERT INTO track (id, title, artist_id, artist_name, album_id, album_title, duration_sec, image, ownership, price_minor, plays, quality, year, status)
VALUES
  -- love-damini tracks
  ('last-last', 'Last Last', 'burna-boy', 'Burna Boy', 'love-damini', 'Love, Damini',
   172, 'https://images.unsplash.com/photo-1493225255756-d9584f8606e9?q=80&w=600&auto=format&fit=crop',
   'owned', NULL, 1200000000, 'Lossless • 24-bit/192kHz', 2022, 'ready'),

  ('its-plenty', 'It''s Plenty', 'burna-boy', 'Burna Boy', 'love-damini', 'Love, Damini',
   194, 'https://images.unsplash.com/photo-1470225620780-dba8ba36b745?q=80&w=600&auto=format&fit=crop',
   'for-sale', 250, 84000000, NULL, 2022, 'ready'),

  ('for-my-hand', 'For My Hand', 'burna-boy', 'Burna Boy ft. Ed Sheeran', 'love-damini', 'Love, Damini',
   195, 'https://images.unsplash.com/photo-1508700115892-45ecd05ae2ad?q=80&w=600&auto=format&fit=crop',
   'for-sale', 300, 210000000, NULL, 2022, 'ready'),

  -- the-villain-i-never-was tracks
  ('kwaku-the-traveller', 'Kwaku the Traveller', 'black-sherif', 'Black Sherif',
   'the-villain-i-never-was', 'The Villain I Never Was',
   195, 'https://images.unsplash.com/photo-1619983081563-430f63602796?q=80&w=600&auto=format&fit=crop',
   'for-sale', 300, 124000000, NULL, 2022, 'ready'),

  ('soja', 'Soja', 'black-sherif', 'Black Sherif',
   'the-villain-i-never-was', 'The Villain I Never Was',
   218, 'https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?q=80&w=600&auto=format&fit=crop',
   'owned', NULL, 8900000, NULL, 2022, 'ready'),

  ('45', '45', 'black-sherif', 'Black Sherif',
   'the-villain-i-never-was', 'The Villain I Never Was',
   241, 'https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?q=80&w=600&auto=format&fit=crop',
   'for-sale', 250, 6200000, NULL, 2022, 'ready'),

  -- iron-boy tracks
  ('iron-boy-intro', 'Iron Boy (intro)', 'black-sherif', 'Black Sherif',
   'iron-boy', 'Iron Boy',
   72, 'https://images.unsplash.com/photo-1614613535308-eb5fbd3d2c17?q=80&w=600&auto=format&fit=crop',
   'free', NULL, 0, NULL, 2024, 'ready'),

  ('konongo-zongo-ii', 'Konongo Zongo II', 'black-sherif', 'Black Sherif',
   'iron-boy', 'Iron Boy',
   222, 'https://images.unsplash.com/photo-1514525253161-7a46d19cd819?q=80&w=600&auto=format&fit=crop',
   'for-sale', 250, 8400000, NULL, 2024, 'ready'),

  ('hold-on', 'Hold On', 'black-sherif', 'Black Sherif',
   'iron-boy', 'Iron Boy',
   218, 'https://images.unsplash.com/photo-1470225620780-dba8ba36b745?q=80&w=600&auto=format&fit=crop',
   'owned', NULL, 24100000, NULL, 2024, 'ready'),

  ('mountains', 'Mountains', 'black-sherif', 'Black Sherif',
   'iron-boy', 'Iron Boy',
   241, 'https://images.unsplash.com/photo-1493225255756-d9584f8606e9?q=80&w=600&auto=format&fit=crop',
   'for-sale', 250, 12800000, NULL, 2024, 'ready'),

  ('akwasidae', 'Akwasidae', 'black-sherif', 'Black Sherif',
   'iron-boy', 'Iron Boy',
   198, 'https://images.unsplash.com/photo-1619983081563-430f63602796?q=80&w=600&auto=format&fit=crop',
   'owned', NULL, 18200000, NULL, 2024, 'ready'),

  ('jah-jah', 'Jah Jah', 'black-sherif', 'Black Sherif',
   'iron-boy', 'Iron Boy',
   242, 'https://images.unsplash.com/photo-1508700115892-45ecd05ae2ad?q=80&w=600&auto=format&fit=crop',
   'for-sale', 250, 6400000, NULL, 2024, 'ready'),

  -- standalone tracks (no album)
  ('sungba', 'Sungba', 'asake', 'Asake', NULL, NULL,
   165, 'https://images.unsplash.com/photo-1514525253161-7a46d19cd819?q=80&w=600&auto=format&fit=crop',
   'for-sale', 250, 95000000, NULL, 2022, 'ready'),

  ('terminator', 'Terminator', 'king-promise', 'King Promise', NULL, NULL,
   210, 'https://images.unsplash.com/photo-1470225620780-dba8ba36b745?q=80&w=600&auto=format&fit=crop',
   'for-sale', 250, 32000000, NULL, 2023, 'ready'),

  ('friday-night', 'Friday Night', 'lasmid', 'Lasmid', NULL, NULL,
   185, 'https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?q=80&w=600&auto=format&fit=crop',
   'owned', NULL, 14000000, NULL, 2023, 'ready'),

  ('sugarcane', 'Sugarcane', 'camidoh', 'Camidoh', NULL, NULL,
   188, 'https://images.unsplash.com/photo-1504151932400-72d4384f0e6d?q=80&w=600&auto=format&fit=crop',
   'for-sale', 250, 40000000, NULL, 2022, 'ready'),

  ('calm-down', 'Calm Down', 'rema', 'Rema, Selena Gomez', NULL, NULL,
   219, 'https://images.unsplash.com/photo-1508700115892-45ecd05ae2ad?q=80&w=600&auto=format&fit=crop',
   'for-sale', 300, 900000000, NULL, 2022, 'ready')

ON CONFLICT (id) DO UPDATE
  SET title        = EXCLUDED.title,
      artist_id    = EXCLUDED.artist_id,
      artist_name  = EXCLUDED.artist_name,
      album_id     = EXCLUDED.album_id,
      album_title  = EXCLUDED.album_title,
      duration_sec = EXCLUDED.duration_sec,
      image        = EXCLUDED.image,
      ownership    = EXCLUDED.ownership,
      price_minor  = EXCLUDED.price_minor,
      plays        = EXCLUDED.plays,
      quality      = EXCLUDED.quality,
      year         = EXCLUDED.year,
      status       = EXCLUDED.status;

-- ==========================================================================
-- Track credits (mock-data.ts `tracks[last-last].credits`)
-- ==========================================================================

INSERT INTO track_credit (track_id, role, names)
VALUES
  ('last-last', 'Producer',           ARRAY['Chopstix']),
  ('last-last', 'Songwriter',         ARRAY['Damini Ebunoluwa Ogulu', 'Mikael Haataja', 'Samuel Haataja']),
  ('last-last', 'Mixing Engineer',    ARRAY['Jesse Ray Ernster']),
  ('last-last', 'Mastering Engineer', ARRAY['Colin Leonard'])
ON CONFLICT (track_id, role) DO UPDATE
  SET names = EXCLUDED.names;

-- ==========================================================================
-- Lyrics (lyrics-data.ts — SPECIFIC entries for 'last-last' and 'kwaku-the-traveller')
-- ==========================================================================

INSERT INTO lyrics (track_id)
VALUES ('last-last'), ('kwaku-the-traveller')
ON CONFLICT (track_id) DO NOTHING;

INSERT INTO lyric_line (track_id, t_sec, text)
VALUES
  -- last-last
  ('last-last',  0,  '♪'),
  ('last-last',  6,  'No place feels the same since you left'),
  ('last-last',  12, 'City lights, but the night feels cold'),
  ('last-last',  19, 'I been moving, I been running solo'),
  ('last-last',  26, 'Counting every mile on the road'),
  ('last-last',  34, 'Last last, everybody go feel it'),
  ('last-last',  41, 'Last last, na so the story go'),
  ('last-last',  49, 'Tell them say I dey alright now'),
  ('last-last',  57, 'Even when the morning slow'),
  ('last-last',  66, '♪'),
  ('last-last',  74, 'Hold on, the sun dey come'),
  ('last-last',  82, 'Hold on, we no go run'),
  -- kwaku-the-traveller
  ('kwaku-the-traveller',  0,  '♪'),
  ('kwaku-the-traveller',  7,  'Packed my bags before the sunrise'),
  ('kwaku-the-traveller',  14, 'Konongo boy, but the world is wide'),
  ('kwaku-the-traveller',  22, 'Every wrong turn taught me something'),
  ('kwaku-the-traveller',  30, 'Now I carry it all with pride'),
  ('kwaku-the-traveller',  39, 'Who never make mistake before?'),
  ('kwaku-the-traveller',  47, 'Raise your hand if your heart is pure'),
  ('kwaku-the-traveller',  56, 'I traveled far to find my sound'),
  ('kwaku-the-traveller',  64, 'And I am never coming down')
ON CONFLICT (track_id, t_sec) DO UPDATE
  SET text = EXCLUDED.text;

-- ==========================================================================
-- Playlists (mock-data.ts `playlists`)
-- ==========================================================================

INSERT INTO playlist (id, title, description, creator, creator_avatar, image, is_public, followers)
VALUES
  ('vibes-from-the-233', 'Vibes from the 233',
   'The best of Ghanaian drill, highlife, and afrobeats. Hand-picked for your weekend vibes.',
   'Ama Serwaa', 'https://i.pravatar.cc/100?img=11',
   'https://images.unsplash.com/photo-1493225255756-d9584f8606e9?q=80&w=600&auto=format&fit=crop',
   TRUE, 1240),

  ('made-in-ghana', 'Made in Ghana',
   'Eighty of the finest records to ever come out of Ghana.',
   'BeatzClik', NULL,
   'https://images.unsplash.com/photo-1516280440502-86ec1ed6f0c4?q=80&w=600&auto=format&fit=crop',
   TRUE, 80000),

  ('hiplife-throwback', 'Hiplife Throwback',
   NULL, 'BeatzClik', NULL,
   'https://images.unsplash.com/photo-1470225620780-dba8ba36b745?q=80&w=600&auto=format&fit=crop',
   TRUE, 22000),

  -- A private playlist for testing the 404 / existence-hidden behaviour (LLFR-CATALOG-01.7)
  ('private-test-playlist', 'My Private Playlist',
   'This playlist is private.', 'Kojo', NULL,
   'https://images.unsplash.com/photo-1516280440502-86ec1ed6f0c4?q=80&w=600&auto=format&fit=crop',
   FALSE, 0)
ON CONFLICT (id) DO UPDATE
  SET title          = EXCLUDED.title,
      description    = EXCLUDED.description,
      creator        = EXCLUDED.creator,
      creator_avatar = EXCLUDED.creator_avatar,
      image          = EXCLUDED.image,
      is_public      = EXCLUDED.is_public,
      followers      = EXCLUDED.followers;

-- Playlist tracks (ordered)
INSERT INTO playlist_track (playlist_id, track_id, position)
VALUES
  -- vibes-from-the-233: last-last, kwaku-the-traveller, sungba, terminator, friday-night
  ('vibes-from-the-233', 'last-last',            0),
  ('vibes-from-the-233', 'kwaku-the-traveller',  1),
  ('vibes-from-the-233', 'sungba',               2),
  ('vibes-from-the-233', 'terminator',           3),
  ('vibes-from-the-233', 'friday-night',         4),
  -- made-in-ghana
  ('made-in-ghana', 'kwaku-the-traveller',  0),
  ('made-in-ghana', 'soja',                 1),
  ('made-in-ghana', 'friday-night',         2),
  ('made-in-ghana', 'sugarcane',            3),
  ('made-in-ghana', 'terminator',           4),
  -- hiplife-throwback
  ('hiplife-throwback', 'soja',      0),
  ('hiplife-throwback', '45',        1),
  ('hiplife-throwback', 'akwasidae', 2),
  -- private playlist
  ('private-test-playlist', 'last-last', 0)
ON CONFLICT (playlist_id, position) DO UPDATE
  SET track_id = EXCLUDED.track_id;


-- Browse categories (WU-CAT-2: mock-data.ts browseCategories)
INSERT INTO browse_category (id, title, color_class) VALUES
  ('afrobeats',  'Afrobeats',  'from-orange-500 to-amber-400'),
  ('hiplife',    'Hiplife',    'from-purple-500 to-pink-400'),
  ('highlife',   'Highlife',   'from-green-500 to-teal-400'),
  ('amapiano',   'Amapiano',   'from-blue-500 to-cyan-400'),
  ('drill',      'Drill',      'from-red-500 to-rose-400'),
  ('gospel',     'Gospel',     'from-yellow-500 to-lime-400'),
  ('rb',         'R&B',        'from-indigo-500 to-violet-400'),
  ('reggae',     'Reggae',     'from-emerald-500 to-green-400'),
  ('jazz',       'Jazz',       'from-slate-500 to-gray-400')
ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title, color_class = EXCLUDED.color_class;
