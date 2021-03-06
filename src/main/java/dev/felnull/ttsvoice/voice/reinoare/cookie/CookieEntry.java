package dev.felnull.ttsvoice.voice.reinoare.cookie;

import java.util.UUID;

public record CookieEntry(String name, String path, UUID uuid) {
    public String getURL() {
        return CookieManager.getInstance().getFileURL(uuid);
    }
}
