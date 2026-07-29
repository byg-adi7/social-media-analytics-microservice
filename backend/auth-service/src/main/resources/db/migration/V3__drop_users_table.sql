-- Identity is now fully owned by Supabase Auth (see the frontend's
-- Supabase client) - this service no longer stores or authenticates users
-- itself, only validates the JWT Supabase issues. The local users table is
-- unused going forward.
DROP TABLE users;
