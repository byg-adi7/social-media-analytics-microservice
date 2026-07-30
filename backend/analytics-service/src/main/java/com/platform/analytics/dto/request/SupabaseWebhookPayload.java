package com.platform.analytics.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Map;

/**
 * The JSON body Supabase's Database Webhooks feature POSTs on a table
 * change. Only the fields this service actually reads are modeled -
 * {@code old_record} carries the deleted row for a DELETE event.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SupabaseWebhookPayload {

    private String type;
    private String table;
    private String schema;
    private Map<String, Object> record;

    @JsonProperty("old_record")
    private Map<String, Object> oldRecord;
}
