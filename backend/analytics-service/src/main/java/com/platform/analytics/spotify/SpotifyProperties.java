package com.platform.analytics.spotify;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Binds the {@code spotify.*} configuration block from application.yml.
 * Holds the Spotify OAuth 2.0 / Web API settings needed for the real
 * Spotify integration (see {@link SpotifySocialMediaClient}).
 * <p>
 * Unlike YouTube/Instagram/Facebook, Spotify has no public API for artist
 * streaming analytics (monthly listeners, streams) — that data lives only
 * in the private "Spotify for Artists" dashboard. This integration instead
 * reflects the connected user's own listening activity: top artists/
 * tracks, recently played, followed artists, and profile follower count.
 * See {@link SpotifySocialMediaClient} for the full field-by-field mapping
 * and which {@code Analytics} fields are genuinely populated vs. left at
 * zero because there is no Spotify equivalent for a personal account.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "spotify")
public class SpotifyProperties {

    /**
     * Master switch. When false, the real Spotify client bean is not
     * created and {@code MockSocialMediaClient} continues to handle
     * Spotify accounts.
     */
    private boolean enabled = false;

    private String clientId;
    private String clientSecret;
    private String redirectUri;
    private String scope = "user-read-email user-read-private user-top-read user-read-recently-played user-follow-read";
    private String authUri = "https://accounts.spotify.com/authorize";
    private String tokenUri = "https://accounts.spotify.com/api/token";
    private String apiBaseUrl = "https://api.spotify.com/v1";

    /**
     * Where to send the user's browser after the OAuth callback completes.
     */
    private String frontendRedirectUri = "http://localhost:3000/dashboard";
}
