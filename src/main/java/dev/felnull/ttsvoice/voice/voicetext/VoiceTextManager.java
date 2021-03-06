package dev.felnull.ttsvoice.voice.voicetext;

import dev.felnull.fnjl.util.FNStringUtil;
import dev.felnull.ttsvoice.Main;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

public class VoiceTextManager {
    private static final VoiceTextManager INSTANCE = new VoiceTextManager();
    private static final String API_URL = "https://api.voicetext.jp/v1/tts";

    public static VoiceTextManager getInstance() {
        return INSTANCE;
    }

    public String getAPIKey() {
        return Main.CONFIG.voiceTextAPIKey();
    }

    public InputStream getVoice(String text, VTVoiceTypes vtVoiceTypes) throws IOException, InterruptedException, URISyntaxException {
        text = URLEncoder.encode(text, StandardCharsets.UTF_8);
        text = new URI(text).toASCIIString();
        var hc = HttpClient.newHttpClient();
        String basic = "Basic " + FNStringUtil.encodeBase64(getAPIKey() + ":");
        var request = HttpRequest.newBuilder(URI.create(API_URL)).header("Authorization", basic).header("Content-Type", "application/x-www-form-urlencoded; charset=utf-8").POST(HttpRequest.BodyPublishers.ofString(String.format("text=%s&speaker=%s", text, vtVoiceTypes.getName()))).version(HttpClient.Version.HTTP_1_1).build();
        var res = hc.send(request, HttpResponse.BodyHandlers.ofInputStream());
        return res.body();
    }
}
