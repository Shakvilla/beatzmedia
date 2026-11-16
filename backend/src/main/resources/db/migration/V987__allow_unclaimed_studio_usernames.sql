-- A studio profile can be created by the profile-image upload before the artist has
-- chosen a username. Keep usernames unique once claimed, but allow multiple blank shells.
DROP INDEX ux_studio_profile_username;

CREATE UNIQUE INDEX ux_studio_profile_username
    ON studio_profile (lower(username))
    WHERE username <> '';
