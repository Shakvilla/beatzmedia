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

-- ==========================================================================
-- Podcasts (WU-POD-1: Frontend/src/lib/podcast-data.ts `podcasts`)
-- ==========================================================================

-- creator_account_id: the owning creator (tip recipient, WU-POD-2). Dev-seed placeholder account ids
-- (studio authoring / real creator accounts arrive with WU-STU-2). A tip to a show whose
-- creator_account_id is NULL is rejected as TIPS_DISABLED.
INSERT INTO podcast (id, title, publisher, creator_account_id, image, category, description, episode_count, popularity, season_pass_price_minor, season_pass_currency, supports_tips)
VALUES
  ('sincerely-accra', 'Sincerely, Accra', 'YGTV Africa', 'creator-ygtv-africa',
   'https://images.unsplash.com/photo-1516280440614-37939bbacd81?q=80&w=600&auto=format&fit=crop',
   'Culture', 'Honest conversations about life, love and growing up in the city.',
   142, 98, 1200, 'GHS', TRUE),

  ('front-page-gh', 'Front Page', 'Citi Newsroom', 'creator-citi-newsroom',
   'https://images.unsplash.com/photo-1504711434969-e33886168f5c?q=80&w=600&auto=format&fit=crop',
   'News & Politics', 'A daily breakdown of the stories shaping Ghana.',
   410, 95, 1000, 'GHS', TRUE),

  ('cedi-talk', 'Cedi Talk', 'Accra Business Network', 'creator-accra-biz-net',
   'https://images.unsplash.com/photo-1611974789855-9c2a0a7236a3?q=80&w=600&auto=format&fit=crop',
   'Business', 'Money, markets and building wealth in Ghana.',
   88, 90, 1500, 'GHS', TRUE),

  ('konnect-comedy', 'The Konnect', 'Konnect Media', 'creator-konnect-media',
   'https://images.unsplash.com/photo-1521119989659-a83eee488004?q=80&w=600&auto=format&fit=crop',
   'Comedy', 'Three friends, zero filter, plenty of laughs.',
   64, 88, NULL, NULL, TRUE),

  ('black-stars-pod', 'Black Stars Breakdown', 'Joy Sports', 'creator-joy-sports',
   'https://images.unsplash.com/photo-1431324155629-1a6deb1dec8d?q=80&w=600&auto=format&fit=crop',
   'Sports', 'Everything Ghana football, from the GPL to the Black Stars.',
   120, 84, NULL, NULL, TRUE),

  ('tech-nkwa', 'Tech Nkwa', 'Accra Tech Hub', 'creator-accra-tech-hub',
   'https://images.unsplash.com/photo-1518770660439-4636190af475?q=80&w=600&auto=format&fit=crop',
   'Tech', 'Africa''s startup and technology scene, decoded.',
   52, 78, NULL, NULL, TRUE),

  ('asaase-stories', 'Asaase Stories', 'Asaase Radio', 'creator-asaase-radio',
   'https://images.unsplash.com/photo-1457369804613-52c61a468e7d?q=80&w=600&auto=format&fit=crop',
   'Storytelling', 'Folktales and true stories from across the motherland.',
   37, 72, NULL, NULL, TRUE),

  ('well-being-gh', 'Body & Soul GH', 'Wellness Accra', 'creator-wellness-accra',
   'https://images.unsplash.com/photo-1478737270239-2f02b77fc618?q=80&w=600&auto=format&fit=crop',
   'Health', 'Practical wellness and mental health for busy Ghanaians.',
   45, 68, NULL, NULL, TRUE)
ON CONFLICT (id) DO UPDATE
  SET title                   = EXCLUDED.title,
      publisher                = EXCLUDED.publisher,
      creator_account_id       = EXCLUDED.creator_account_id,
      image                    = EXCLUDED.image,
      category                 = EXCLUDED.category,
      description              = EXCLUDED.description,
      episode_count            = EXCLUDED.episode_count,
      popularity               = EXCLUDED.popularity,
      season_pass_price_minor  = EXCLUDED.season_pass_price_minor,
      season_pass_currency     = EXCLUDED.season_pass_currency,
      supports_tips            = EXCLUDED.supports_tips;

-- ==========================================================================
-- Podcast episodes (WU-POD-1: Frontend/src/lib/podcast-data.ts `episodes`)
-- price_minor = GHS(n) * 100 pesewas
-- ==========================================================================

INSERT INTO podcast_episode (id, podcast_id, title, image, description, duration_sec, episode_number, is_premium, price_minor, price_currency, is_early_access, public_at, published_at)
VALUES
  -- Sincerely, Accra — showcase feed (free + premium + early-access)
  ('ep-accra-6', 'sincerely-accra', 'Healing out loud',
   'https://images.unsplash.com/photo-1516280440614-37939bbacd81?q=80&w=600&auto=format&fit=crop',
   'A raw conversation about therapy, faith and forgiveness.',
   3120, 48, TRUE, 500, 'GHS', TRUE, '2026-06-27T00:00:00Z', '2026-06-18T00:00:00Z'),

  ('ep-accra-5', 'sincerely-accra', 'Moving back home after years abroad',
   'https://images.unsplash.com/photo-1516280440614-37939bbacd81?q=80&w=600&auto=format&fit=crop',
   'The reverse culture shock nobody warns you about.',
   2940, 47, FALSE, NULL, NULL, FALSE, NULL, '2026-06-16T00:00:00Z'),

  ('ep-accra-4', 'sincerely-accra', 'Dating in your 30s in Accra',
   'https://images.unsplash.com/photo-1516280440614-37939bbacd81?q=80&w=600&auto=format&fit=crop',
   'Apps, aunties and the pressure to settle down.',
   3360, 46, TRUE, 300, 'GHS', FALSE, NULL, '2026-06-09T00:00:00Z'),

  ('ep-accra-3', 'sincerely-accra', 'Money and friendships',
   'https://images.unsplash.com/photo-1516280440614-37939bbacd81?q=80&w=600&auto=format&fit=crop',
   'What happens when your circle grows at different speeds.',
   3000, 45, TRUE, 300, 'GHS', FALSE, NULL, '2026-06-02T00:00:00Z'),

  ('ep-accra-2', 'sincerely-accra', 'Therapy, the Ghanaian way',
   'https://images.unsplash.com/photo-1516280440614-37939bbacd81?q=80&w=600&auto=format&fit=crop',
   NULL, 2820, 44, FALSE, NULL, NULL, FALSE, NULL, '2026-05-26T00:00:00Z'),

  ('ep-accra-1', 'sincerely-accra', 'Season 2 opener: starting over',
   'https://images.unsplash.com/photo-1516280440614-37939bbacd81?q=80&w=600&auto=format&fit=crop',
   NULL, 2700, 43, FALSE, NULL, NULL, FALSE, NULL, '2026-05-19T00:00:00Z'),

  -- Front Page — news (mostly free, one premium deep dive)
  ('ep-front-2', 'front-page-gh', 'The 2026 budget, explained',
   'https://images.unsplash.com/photo-1504711434969-e33886168f5c?q=80&w=600&auto=format&fit=crop',
   'What the new budget means for your pocket.',
   1620, NULL, FALSE, NULL, NULL, FALSE, NULL, '2026-06-18T00:00:00Z'),

  ('ep-front-1', 'front-page-gh', 'Ad-free deep dive: the cedi vs the dollar',
   'https://images.unsplash.com/photo-1504711434969-e33886168f5c?q=80&w=600&auto=format&fit=crop',
   'Extended, ad-free analysis for supporters.',
   2580, NULL, TRUE, 400, 'GHS', FALSE, NULL, '2026-06-11T00:00:00Z'),

  -- Cedi Talk — business (premium masterclass)
  ('ep-cedi-2', 'cedi-talk', 'Saving in cedis vs dollars',
   'https://images.unsplash.com/photo-1611974789855-9c2a0a7236a3?q=80&w=600&auto=format&fit=crop',
   'How to protect your savings against inflation.',
   2280, NULL, FALSE, NULL, NULL, FALSE, NULL, '2026-06-15T00:00:00Z'),

  ('ep-cedi-1', 'cedi-talk', 'Masterclass: building a MoMo side hustle',
   'https://images.unsplash.com/photo-1611974789855-9c2a0a7236a3?q=80&w=600&auto=format&fit=crop',
   'A step-by-step premium workshop episode.',
   3300, NULL, TRUE, 600, 'GHS', FALSE, NULL, '2026-06-08T00:00:00Z'),

  -- Single free episodes for the rest
  ('ep-konnect-1', 'konnect-comedy', 'Wedding season survival guide',
   'https://images.unsplash.com/photo-1521119989659-a83eee488004?q=80&w=600&auto=format&fit=crop',
   'Aso ebi, MCs and the contribution wahala.',
   3300, NULL, FALSE, NULL, NULL, FALSE, NULL, '2026-06-14T00:00:00Z'),

  ('ep-stars-1', 'black-stars-pod', 'Can the Black Stars bounce back?',
   'https://images.unsplash.com/photo-1431324155629-1a6deb1dec8d?q=80&w=600&auto=format&fit=crop',
   'Breaking down the squad ahead of the qualifiers.',
   2700, NULL, FALSE, NULL, NULL, FALSE, NULL, '2026-06-17T00:00:00Z'),

  ('ep-tech-1', 'tech-nkwa', 'Mobile money and the future of payments',
   'https://images.unsplash.com/photo-1518770660439-4636190af475?q=80&w=600&auto=format&fit=crop',
   'How MoMo reshaped commerce across West Africa.',
   2100, NULL, FALSE, NULL, NULL, FALSE, NULL, '2026-06-12T00:00:00Z'),

  ('ep-asaase-1', 'asaase-stories', 'Why the tortoise has a cracked shell',
   'https://images.unsplash.com/photo-1457369804613-52c61a468e7d?q=80&w=600&auto=format&fit=crop',
   'A retelling of the classic Ananse-era folktale.',
   1500, NULL, FALSE, NULL, NULL, FALSE, NULL, '2026-06-10T00:00:00Z'),

  ('ep-body-1', 'well-being-gh', 'Beating burnout in a hustle culture',
   'https://images.unsplash.com/photo-1478737270239-2f02b77fc618?q=80&w=600&auto=format&fit=crop',
   'Rest is productive too.',
   1980, NULL, FALSE, NULL, NULL, FALSE, NULL, '2026-06-13T00:00:00Z')
ON CONFLICT (id) DO UPDATE
  SET podcast_id      = EXCLUDED.podcast_id,
      title            = EXCLUDED.title,
      image            = EXCLUDED.image,
      description      = EXCLUDED.description,
      duration_sec     = EXCLUDED.duration_sec,
      episode_number   = EXCLUDED.episode_number,
      is_premium       = EXCLUDED.is_premium,
      price_minor      = EXCLUDED.price_minor,
      price_currency   = EXCLUDED.price_currency,
      is_early_access  = EXCLUDED.is_early_access,
      public_at        = EXCLUDED.public_at,
      published_at     = EXCLUDED.published_at;

-- ==========================================================================
-- Events (WU-EVT-1: Frontend/src/lib/event-data.ts `events`)
-- status/soldOut are NEVER seeded — always derived at read time from
-- ticket_tier.sold/capacity (INV-EVT-2). capacity/sold below are chosen so the derived status
-- reproduces the original mock fixture: iron-boy-live and five-star-night land "selling-fast"
-- (low remaining stock), afro-nation-gh's tiers are seeded AT capacity ("sold-out" fixture), and
-- the rest have ample remaining stock ("on-sale").
-- ==========================================================================

INSERT INTO event (id, title, artist_name, artist_id, lineup, image, event_at, doors_time, venue, city, region, category, description, age_restriction, popularity)
VALUES
  ('iron-boy-live', 'Iron Boy Live', 'Black Sherif', 'black-sherif',
   '["Lasmid","Camidoh"]'::jsonb,
   'https://images.unsplash.com/photo-1501386761578-eac5c94b800a?q=80&w=1200&auto=format&fit=crop',
   '2026-07-09T19:00:00Z', '7:00 PM', 'Independence Square', 'Accra', 'Greater Accra', 'Concert',
   'Black Sherif headlines a homecoming show backed by a full live band, with special guests from across the 233.',
   'All ages', 99),

  ('outside-tour-accra', 'Burna Boy — Outside Tour', 'Burna Boy', 'burna-boy',
   '["Asake"]'::jsonb,
   'https://images.unsplash.com/photo-1540039155733-5bb30b53aa14?q=80&w=1200&auto=format&fit=crop',
   '2026-08-15T20:00:00Z', '8:00 PM', 'Accra Sports Stadium', 'Accra', 'Greater Accra', 'Tour',
   'The African Giant brings the Outside Tour to Accra for one massive night.',
   'All ages', 96),

  ('detty-december', 'Detty December Festival', 'Multiple Artists', NULL,
   '["Black Sherif","King Promise","Camidoh","Lasmid","Sarkodie"]'::jsonb,
   'https://images.unsplash.com/photo-1459749411175-04bf5292ceea?q=80&w=1200&auto=format&fit=crop',
   '2026-12-20T16:00:00Z', '4:00 PM', 'Labadi Beach', 'Accra', 'Greater Accra', 'Festival',
   'Ghana''s biggest end-of-year beach festival — a full day of afrobeats, highlife and amapiano.',
   '18+', 94),

  ('five-star-night', 'King Promise: 5 Star Night', 'King Promise', 'king-promise',
   '[]'::jsonb,
   'https://images.unsplash.com/photo-1415201364774-f6f0bb35f28f?q=80&w=1200&auto=format&fit=crop',
   '2026-06-27T21:00:00Z', '9:00 PM', '+233 Jazz Bar & Grill', 'Accra', 'Greater Accra', 'Club Night',
   'An intimate late-night set from King Promise in the heart of Accra.',
   '21+', 88),

  ('asaase-sound-clash', 'Asaase Sound Clash', 'Multiple Artists', NULL,
   '["Lasmid","Camidoh","Asake"]'::jsonb,
   'https://images.unsplash.com/photo-1470229722913-7c0e2dbbafd3?q=80&w=1200&auto=format&fit=crop',
   '2026-09-05T18:00:00Z', '6:00 PM', 'Baba Yara Stadium', 'Kumasi', 'Ashanti', 'Festival',
   'The Garden City''s biggest clash of sounds returns to Baba Yara.',
   'All ages', 82),

  ('sugarcane-listening', 'Sugarcane: Listening Party', 'Camidoh', 'camidoh',
   '[]'::jsonb,
   'https://images.unsplash.com/photo-1566737236500-c8ac43014a67?q=80&w=1200&auto=format&fit=crop',
   '2026-06-28T19:30:00Z', '7:30 PM', 'Community 1 Arts Centre', 'Tema', 'Greater Accra', 'Listening Party',
   'Hear Camidoh''s new project first, with a live Q&A and signing.',
   'All ages', 74),

  ('afro-nation-gh', 'Afro Nation Ghana', 'Multiple Artists', NULL,
   '["Burna Boy","Rema","Black Sherif","Asake"]'::jsonb,
   'https://images.unsplash.com/photo-1533174072545-7a4b6ad7a6c3?q=80&w=1200&auto=format&fit=crop',
   '2026-10-10T15:00:00Z', '3:00 PM', 'Aburi Gardens', 'Aburi', 'Eastern', 'Festival',
   'The world''s biggest afrobeats festival lands in Ghana.',
   '18+', 91)
ON CONFLICT (id) DO UPDATE
  SET title           = EXCLUDED.title,
      artist_name     = EXCLUDED.artist_name,
      artist_id       = EXCLUDED.artist_id,
      lineup          = EXCLUDED.lineup,
      image           = EXCLUDED.image,
      event_at        = EXCLUDED.event_at,
      doors_time      = EXCLUDED.doors_time,
      venue           = EXCLUDED.venue,
      city            = EXCLUDED.city,
      region          = EXCLUDED.region,
      category        = EXCLUDED.category,
      description     = EXCLUDED.description,
      age_restriction = EXCLUDED.age_restriction,
      popularity      = EXCLUDED.popularity,
      updated_at      = now();

-- Ticket tiers (event-data.ts `ticketTiers`). price_minor = GHS(n) * 100 pesewas.
-- capacity/sold chosen per the status-derivation note above; afro-nation-gh tiers are seeded
-- AT capacity (sold = capacity) to reproduce the sold-out fixture end to end.
INSERT INTO ticket_tier (id, event_id, name, price_minor, capacity, sold, perks)
VALUES
  -- iron-boy-live: total remaining 26 <= low-stock threshold -> selling-fast
  ('iron-boy-live-regular', 'iron-boy-live', 'Regular', 15000, 500, 480,
   '["General standing","Access from 6 PM"]'::jsonb),
  ('iron-boy-live-vip', 'iron-boy-live', 'VIP', 40000, 200, 195,
   '["Elevated VIP deck","Dedicated bar","Fast-track entry"]'::jsonb),
  ('iron-boy-live-vvip-table-5', 'iron-boy-live', 'VVIP Table (5)', 250000, 20, 19,
   '["Front-stage table for 5","Bottle service","Backstage tour"]'::jsonb),

  -- outside-tour-accra: ample remaining stock -> on-sale
  ('outside-tour-accra-regular', 'outside-tour-accra', 'Regular', 25000, 5000, 1200, '[]'::jsonb),
  ('outside-tour-accra-vip', 'outside-tour-accra', 'VIP', 60000, 1000, 300,
   '["VIP section","Premium viewing"]'::jsonb),
  ('outside-tour-accra-golden-circle', 'outside-tour-accra', 'Golden Circle', 120000, 300, 50,
   '["Front pit","Exclusive entrance"]'::jsonb),

  -- detty-december: ample remaining stock -> on-sale
  ('detty-december-1-day-pass', 'detty-december', '1-Day Pass', 25000, 3000, 500,
   '["Single-day entry"]'::jsonb),
  ('detty-december-weekend-pass', 'detty-december', 'Weekend Pass', 45000, 1500, 400,
   '["Both days","Re-entry"]'::jsonb),
  ('detty-december-vip-weekend', 'detty-december', 'VIP Weekend', 90000, 500, 100,
   '["VIP zone","Free welcome drinks","Shaded lounge"]'::jsonb),

  -- five-star-night: total remaining 11 <= low-stock threshold -> selling-fast
  ('five-star-night-entry', 'five-star-night', 'Entry', 8000, 300, 290, '[]'::jsonb),
  ('five-star-night-booth-4', 'five-star-night', 'Booth (4)', 150000, 30, 29,
   '["Reserved booth for 4","Bottle service"]'::jsonb),

  -- asaase-sound-clash: ample remaining stock -> on-sale
  ('asaase-sound-clash-regular', 'asaase-sound-clash', 'Regular', 12000, 2000, 400, '[]'::jsonb),
  ('asaase-sound-clash-vip', 'asaase-sound-clash', 'VIP', 35000, 500, 100,
   '["VIP stand","Fast-track entry"]'::jsonb),

  -- sugarcane-listening: ample remaining stock -> on-sale
  ('sugarcane-listening-standard', 'sugarcane-listening', 'Standard', 5000, 200, 50,
   '["Entry","Welcome drink"]'::jsonb),
  ('sugarcane-listening-meet-and-greet', 'sugarcane-listening', 'Meet & Greet', 20000, 30, 5,
   '["Signed merch","Photo with Camidoh"]'::jsonb),

  -- afro-nation-gh: sold = capacity on BOTH tiers -> sold-out fixture
  ('afro-nation-gh-general', 'afro-nation-gh', 'General', 50000, 5000, 5000, '[]'::jsonb),
  ('afro-nation-gh-vip', 'afro-nation-gh', 'VIP', 150000, 1000, 1000, '[]'::jsonb)
ON CONFLICT (event_id, name) DO UPDATE
  SET price_minor = EXCLUDED.price_minor,
      capacity    = EXCLUDED.capacity,
      sold        = EXCLUDED.sold,
      perks       = EXCLUDED.perks;

-- ==========================================================================
-- Store (WU-STO-1: Frontend/src/lib/store-data.ts `allStoreItems`)
-- price_minor = GHS(n) * 100 pesewas (INV-11). BEAT_LICENSE base price = lowest license tier
-- price (INV-STORE-B). stock_remaining is only set for EXCLUSIVE/MERCH (INV-STORE-A/INV-STORE-C).
-- ==========================================================================

-- ---- Hi-Fi: lossless tracks & mastered albums ---------------------------------------------------
INSERT INTO store_item (id, type, title, artist_name, artist_id, image, price_minor, currency, genre, badges, description, popularity, created_at, quality)
VALUES
  ('hifi-love-damini', 'ALBUM', 'Love, Damini (Hi-Fi Master)', 'Burna Boy', 'burna-boy',
   'https://images.unsplash.com/photo-1493225255756-d9584f8606e9?q=80&w=600&auto=format&fit=crop',
   3499, 'GHS', 'Afrobeats', '["HI-FI LOSSLESS"]'::jsonb,
   'The full album remastered in studio-grade lossless audio.', 98, '2026-05-02T00:00:00Z',
   'Lossless • 24-bit/192kHz'),

  ('hifi-iron-boy', 'ALBUM', 'Iron Boy (Hi-Fi Master)', 'Black Sherif', 'black-sherif',
   'https://images.unsplash.com/photo-1614613535308-eb5fbd3d2c17?q=80&w=600&auto=format&fit=crop',
   2999, 'GHS', 'Drill', '["HI-FI LOSSLESS"]'::jsonb,
   NULL, 95, '2026-05-20T00:00:00Z', 'Lossless • 24-bit/96kHz'),

  ('hifi-last-last', 'TRACK', 'Last Last (Hi-Fi)', 'Burna Boy', 'burna-boy',
   'https://images.unsplash.com/photo-1493225255756-d9584f8606e9?q=80&w=600&auto=format&fit=crop',
   450, 'GHS', 'Afrobeats', '["HI-FI LOSSLESS"]'::jsonb,
   NULL, 99, '2026-04-15T00:00:00Z', 'Lossless • 24-bit/192kHz'),

  ('hifi-sugarcane', 'TRACK', 'Sugarcane (Hi-Fi)', 'Camidoh', 'camidoh',
   'https://images.unsplash.com/photo-1504151932400-72d4384f0e6d?q=80&w=600&auto=format&fit=crop',
   400, 'GHS', 'R&B', '["HI-FI LOSSLESS"]'::jsonb,
   NULL, 80, '2026-03-30T00:00:00Z', 'Lossless • 16-bit/44.1kHz'),

  ('hifi-terminator', 'TRACK', 'Terminator (Hi-Fi)', 'King Promise', 'king-promise',
   'https://images.unsplash.com/photo-1470225620780-dba8ba36b745?q=80&w=600&auto=format&fit=crop',
   400, 'GHS', 'Highlife', '["HI-FI LOSSLESS"]'::jsonb,
   NULL, 76, '2026-05-10T00:00:00Z', 'Lossless • 24-bit/96kHz'),

-- ---- Beats: licensable beats & stems (BEAT_LICENSE; base price = LEASE = lowest tier, INV-STORE-B)
  ('beat-konongo-drill', 'BEAT_LICENSE', 'Konongo Drill Type Beat', 'Joker Nharnah', NULL,
   'https://images.unsplash.com/photo-1598488035139-bdbb2231ce04?q=80&w=600&auto=format&fit=crop',
   5000, 'GHS', 'Drill', '["STEMS INCLUDED"]'::jsonb,
   'Hard-hitting Ghanaian drill beat with live highlife guitars. 142 BPM, A minor.', 92, '2026-05-18T00:00:00Z', NULL),

  ('beat-amapiano-log', 'BEAT_LICENSE', 'Amapiano Log Drum Groove', 'KeyzBeatz', NULL,
   'https://images.unsplash.com/photo-1514525253161-7a46d19cd819?q=80&w=600&auto=format&fit=crop',
   6000, 'GHS', 'Amapiano', '["STEMS INCLUDED"]'::jsonb,
   'Smooth amapiano groove with rolling log drums and shakers. 112 BPM.', 88, '2026-05-25T00:00:00Z', NULL),

  ('beat-highlife-soul', 'BEAT_LICENSE', 'Highlife Soul Instrumental', 'GuitarBoy GH', NULL,
   'https://images.unsplash.com/photo-1470225620780-dba8ba36b745?q=80&w=600&auto=format&fit=crop',
   4500, 'GHS', 'Highlife', '[]'::jsonb,
   'Warm highlife instrumental with palm-wine guitar licks. 96 BPM.', 70, '2026-04-28T00:00:00Z', NULL),

  ('beat-afro-fusion', 'BEAT_LICENSE', 'Afro-Fusion Anthem', 'Atown TSB', NULL,
   'https://images.unsplash.com/photo-1508700115892-45ecd05ae2ad?q=80&w=600&auto=format&fit=crop',
   7000, 'GHS', 'Afrobeats', '["STEMS INCLUDED"]'::jsonb,
   'Festival-sized afro-fusion anthem with big horns. 105 BPM.', 85, '2026-06-01T00:00:00Z', NULL),

-- ---- Merch: physical & digital (MERCH; stock_remaining reused for scarcity) ----------------------
  ('merch-bsherif-tee', 'MERCH', 'Iron Boy Tour Tee', 'Black Sherif', 'black-sherif',
   'https://images.unsplash.com/photo-1521572163474-6864f9cf17ab?q=80&w=600&auto=format&fit=crop',
   12000, 'GHS', NULL, '["OFFICIAL"]'::jsonb,
   '100% cotton tee, screen-printed Iron Boy tour artwork.', 90, '2026-05-12T00:00:00Z', NULL),

  ('merch-burna-hoodie', 'MERCH', 'Outside Embroidered Hoodie', 'Burna Boy', 'burna-boy',
   'https://images.unsplash.com/photo-1556821840-3a63f95609a7?q=80&w=600&auto=format&fit=crop',
   28000, 'GHS', NULL, '["OFFICIAL"]'::jsonb,
   NULL, 84, '2026-04-20T00:00:00Z', NULL),

  ('merch-gh-cap', 'MERCH', 'Made in Ghana Snapback', 'BeatzClik', NULL,
   'https://images.unsplash.com/photo-1588850561407-ed78c282e89b?q=80&w=600&auto=format&fit=crop',
   8500, 'GHS', NULL, '[]'::jsonb,
   NULL, 65, '2026-03-15T00:00:00Z', NULL),

  ('merch-villain-vinyl', 'MERCH', 'The Villain I Never Was — Vinyl', 'Black Sherif', 'black-sherif',
   'https://images.unsplash.com/photo-1539375665275-f9de415ef9ac?q=80&w=600&auto=format&fit=crop',
   35000, 'GHS', NULL, '["LIMITED"]'::jsonb,
   'Limited gatefold double vinyl pressing.', 78, '2026-05-30T00:00:00Z', NULL),

-- ---- Exclusives: VIP experiences & limited drops (EXCLUSIVE; dropsAt + stock_remaining) ----------
  ('exclusive-meet-greet', 'EXCLUSIVE', 'VIP Meet & Greet — Accra', 'Black Sherif', 'black-sherif',
   'https://images.unsplash.com/photo-1470229722913-7c0e2dbbafd3?q=80&w=600&auto=format&fit=crop',
   80000, 'GHS', NULL, '["LIMITED","VIP"]'::jsonb,
   'Backstage meet & greet plus signed merch at Independence Square.', 96, '2026-06-05T00:00:00Z', NULL),

  ('exclusive-early-album', 'EXCLUSIVE', 'Early Access: Unreleased EP', 'Camidoh', 'camidoh',
   'https://images.unsplash.com/photo-1504151932400-72d4384f0e6d?q=80&w=600&auto=format&fit=crop',
   5000, 'GHS', NULL, '["EARLY ACCESS"]'::jsonb,
   'Stream the new EP 7 days before public release.', 82, '2026-06-10T00:00:00Z', NULL),

  ('exclusive-signed-vinyl', 'EXCLUSIVE', 'Signed Vinyl + Handwritten Lyrics', 'King Promise', 'king-promise',
   'https://images.unsplash.com/photo-1539375665275-f9de415ef9ac?q=80&w=600&auto=format&fit=crop',
   60000, 'GHS', NULL, '["LIMITED"]'::jsonb,
   NULL, 74, '2026-05-28T00:00:00Z', NULL)

ON CONFLICT (id) DO UPDATE
  SET type        = EXCLUDED.type,
      title       = EXCLUDED.title,
      artist_name = EXCLUDED.artist_name,
      artist_id   = EXCLUDED.artist_id,
      image       = EXCLUDED.image,
      price_minor = EXCLUDED.price_minor,
      currency    = EXCLUDED.currency,
      genre       = EXCLUDED.genre,
      badges      = EXCLUDED.badges,
      description = EXCLUDED.description,
      popularity  = EXCLUDED.popularity,
      created_at  = EXCLUDED.created_at,
      quality     = EXCLUDED.quality;

-- dropsAt / stock_remaining set in a second pass (kept out of the shared INSERT above so the
-- column list stays common across all types; only EXCLUSIVE/MERCH rows are touched, INV-STORE-A).
UPDATE store_item SET stock_remaining = 42  WHERE id = 'merch-bsherif-tee';
UPDATE store_item SET stock_remaining = 18  WHERE id = 'merch-burna-hoodie';
UPDATE store_item SET stock_remaining = 7   WHERE id = 'merch-villain-vinyl';
UPDATE store_item SET drops_at = '2026-07-09T00:00:00Z', stock_remaining = 12  WHERE id = 'exclusive-meet-greet';
UPDATE store_item SET drops_at = '2026-06-25T00:00:00Z', stock_remaining = 500 WHERE id = 'exclusive-early-album';
UPDATE store_item SET stock_remaining = 25  WHERE id = 'exclusive-signed-vinyl';

-- License tiers (store-data.ts `licenseLadder(base)`) — LEASE cheapest, EXCLUSIVE dearest (INV-STORE-B).
INSERT INTO license_option (id, store_item_id, tier, label, price_minor, features, terms, sort_order)
VALUES
  ('beat-konongo-drill-lease',     'beat-konongo-drill',  'LEASE',     'Basic Lease',    5000,   '["Tagged MP3 file","Up to 10,000 streams","Non-exclusive","1 music video"]'::jsonb, 'MP3 • up to 10,000 streams', 0),
  ('beat-konongo-drill-premium',   'beat-konongo-drill',  'PREMIUM',   'Premium Stems',  20000,  '["Untagged WAV + track stems","Up to 100,000 streams","Non-exclusive","Unlimited music videos"]'::jsonb, 'WAV + stems • up to 100,000 streams', 1),
  ('beat-konongo-drill-exclusive', 'beat-konongo-drill',  'EXCLUSIVE', 'Exclusive',      100000, '["WAV + stems + project file","Unlimited streams & sales","Exclusive — beat removed from store","Full ownership transfer"]'::jsonb, 'Full ownership transfer', 2),

  ('beat-amapiano-log-lease',      'beat-amapiano-log',   'LEASE',     'Basic Lease',    6000,   '["Tagged MP3 file","Up to 10,000 streams","Non-exclusive","1 music video"]'::jsonb, 'MP3 • up to 10,000 streams', 0),
  ('beat-amapiano-log-premium',    'beat-amapiano-log',   'PREMIUM',   'Premium Stems',  24000,  '["Untagged WAV + track stems","Up to 100,000 streams","Non-exclusive","Unlimited music videos"]'::jsonb, 'WAV + stems • up to 100,000 streams', 1),
  ('beat-amapiano-log-exclusive',  'beat-amapiano-log',   'EXCLUSIVE', 'Exclusive',      120000, '["WAV + stems + project file","Unlimited streams & sales","Exclusive — beat removed from store","Full ownership transfer"]'::jsonb, 'Full ownership transfer', 2),

  ('beat-highlife-soul-lease',     'beat-highlife-soul',  'LEASE',     'Basic Lease',    4500,   '["Tagged MP3 file","Up to 10,000 streams","Non-exclusive","1 music video"]'::jsonb, 'MP3 • up to 10,000 streams', 0),
  ('beat-highlife-soul-premium',   'beat-highlife-soul',  'PREMIUM',   'Premium Stems',  18000,  '["Untagged WAV + track stems","Up to 100,000 streams","Non-exclusive","Unlimited music videos"]'::jsonb, 'WAV + stems • up to 100,000 streams', 1),
  ('beat-highlife-soul-exclusive', 'beat-highlife-soul',  'EXCLUSIVE', 'Exclusive',      90000,  '["WAV + stems + project file","Unlimited streams & sales","Exclusive — beat removed from store","Full ownership transfer"]'::jsonb, 'Full ownership transfer', 2),

  ('beat-afro-fusion-lease',       'beat-afro-fusion',    'LEASE',     'Basic Lease',    7000,   '["Tagged MP3 file","Up to 10,000 streams","Non-exclusive","1 music video"]'::jsonb, 'MP3 • up to 10,000 streams', 0),
  ('beat-afro-fusion-premium',     'beat-afro-fusion',    'PREMIUM',   'Premium Stems',  28000,  '["Untagged WAV + track stems","Up to 100,000 streams","Non-exclusive","Unlimited music videos"]'::jsonb, 'WAV + stems • up to 100,000 streams', 1),
  ('beat-afro-fusion-exclusive',   'beat-afro-fusion',    'EXCLUSIVE', 'Exclusive',      140000, '["WAV + stems + project file","Unlimited streams & sales","Exclusive — beat removed from store","Full ownership transfer"]'::jsonb, 'Full ownership transfer', 2)
ON CONFLICT (store_item_id, tier) DO UPDATE
  SET label       = EXCLUDED.label,
      price_minor = EXCLUDED.price_minor,
      features    = EXCLUDED.features,
      terms       = EXCLUDED.terms,
      sort_order  = EXCLUDED.sort_order;

-- Merch variants (store-data.ts `variants`).
INSERT INTO merch_variant (id, store_item_id, label, options, sort_order)
VALUES
  ('merch-bsherif-tee-size',   'merch-bsherif-tee',   'Size',   '["S","M","L","XL","XXL"]'::jsonb, 0),
  ('merch-bsherif-tee-colour', 'merch-bsherif-tee',   'Colour', '["Black","Cream"]'::jsonb,        1),
  ('merch-burna-hoodie-size',  'merch-burna-hoodie',  'Size',   '["S","M","L","XL"]'::jsonb,        0),
  ('merch-gh-cap-colour',      'merch-gh-cap',        'Colour', '["Red","Gold","Black"]'::jsonb,    0)
ON CONFLICT (id) DO UPDATE
  SET label      = EXCLUDED.label,
      options    = EXCLUDED.options,
      sort_order = EXCLUDED.sort_order;
