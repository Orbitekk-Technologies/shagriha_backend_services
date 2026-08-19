package com.orbitekk.shagriha.location;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@ConfigurationProperties(prefix = "mapbox")
public class MapboxProperties {
    private String accessToken = "";
    private Duration nearbyCacheTtl = Duration.ofDays(7);

    public String getAccessToken() { return accessToken; }
    public void setAccessToken(String accessToken) { this.accessToken = accessToken; }
    public Duration getNearbyCacheTtl() { return nearbyCacheTtl; }
    public void setNearbyCacheTtl(Duration nearbyCacheTtl) { this.nearbyCacheTtl = nearbyCacheTtl; }
}
