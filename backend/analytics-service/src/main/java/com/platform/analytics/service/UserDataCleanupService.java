package com.platform.analytics.service;

import java.util.UUID;

/**
 * Cascades deletion of a user's data across every table this service owns,
 * for when the user themselves is deleted upstream (in Supabase Auth, which
 * this service has no other visibility into - see the webhook that calls
 * this).
 */
public interface UserDataCleanupService {

    void deleteAllDataForUser(UUID userId);
}
