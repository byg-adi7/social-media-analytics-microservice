package com.platform.analytics.service;

import java.util.UUID;

/**
 * Calls Supabase's Admin API (requires the secret service_role key, never
 * exposed to the frontend) for operations the anon-key-only client SDK
 * cannot perform itself.
 */
public interface SupabaseAdminService {

    /**
     * Permanently deletes a user from Supabase Auth. This is what actually
     * triggers Supabase's own "Delete" Database Webhook on auth.users,
     * which {@link com.platform.analytics.controller.WebhookController}
     * already handles to cascade-clean all app-side data for that user -
     * no separate cleanup call is needed here.
     */
    void deleteUser(UUID userId);
}
