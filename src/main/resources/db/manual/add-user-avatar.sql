-- Run once in Supabase SQL Editor before starting the updated backend.
-- Adds attributes only. This is not a Flyway migration and creates no tables.
ALTER TABLE public.users
    ADD COLUMN IF NOT EXISTS avatar_url TEXT,
    ADD COLUMN IF NOT EXISTS avatar_public_id VARCHAR(255);
