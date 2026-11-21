package com.casualabsent.riko;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** Minimal streaming client for OpenAI-compatible Chat Completions endpoints. */
public final class OpenAiCompatibleClient {
    private static final int CONNECT_TIMEOUT_MS = 20_000;
    private static final int READ_TIMEOUT_MS = 120_000;
    private static final int MAX_REPLY_CHARS = 32_000;

    public interface StreamListener {
        void onDelta(String text);
    }

    private OpenAiCompatibleClient() {
    }

    public static String streamReply(
            String endpoint,
            String model,
            String apiKey,
            String systemPrompt,
            List<ChatMessage> history,
            String userText,
            StreamListener listener) throws IOException {

        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new IOException("No API key is saved. Open API settings to add one.");
        }

        URL url = new URL(endpoint);
        if (!"https".equalsIgnoreCase(url.getProtocol())) {
            throw new IOException("The API endpoint must use HTTPS.");
        }

        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("POST");
        connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
        connection.setReadTimeout(READ_TIMEOUT_MS);
        connection.setDoOutput(true);
        connection.setUseCaches(false);
        connection.setRequestProperty("Authorization", "Bearer " + apiKey);
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        connection.setRequestProperty("Accept", "text/event-stream");

        try {
            byte[] requestBytes = buildRequest(
                    model, systemPrompt, history, userText)
                    .getBytes(StandardCharsets.UTF_8);
            connection.setFixedLengthStreamingMode(requestBytes.length);

            try (OutputStream output = connection.getOutputStream()) {
                output.write(requestBytes);
                output.flush();
            }

            int statusCode = connection.getResponseCode();
            if (statusCode < 200 || statusCode >= 300) {
                String responseBody = readBody(connection.getErrorStream(), 8_000);
                String detail = extractError(responseBody);
                if (detail.contains(apiKey)) {
                    detail = detail.replace(apiKey, "[hidden]");
                }
                throw new IOException("HTTP " + statusCode + ": " + detail);
            }

            StringBuilder fullReply = new StringBuilder();
            try (InputStream input = connection.getInputStream();
                 BufferedReader reader = new BufferedReader(
                         new InputStreamReader(input, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (!line.startsWith("data:")) {
                        continue;
                    }

                    String data = line.substring(5).trim();
                    if (data.isEmpty()) {
                        continue;
                    }
                    if ("[DONE]".equals(data)) {
                        break;
                    }

                    JSONObject event;
                    try {
                        event = new JSONObject(data);
                    } catch (JSONException exception) {
                        throw new IOException("The API returned an invalid streaming event.",
                                exception);
                    }

                    JSONObject error = event.optJSONObject("error");
                    if (error != null) {
                        String message = error.optString("message", "The API reported an error.");
                        throw new IOException(safeError(message, apiKey));
                    }

                    JSONArray choices = event.optJSONArray("choices");
                    if (choices == null || choices.length() == 0) {
                        continue;
                    }

                    JSONObject choice = choices.optJSONObject(0);
                    JSONObject delta = choice == null ? null : choice.optJSONObject("delta");
                    if (delta == null) {
                        continue;
                    }

                    String piece = delta.optString("content", "");
                    if (piece.isEmpty()) {
                        continue;
                    }

                    if (fullReply.length() + piece.length() > MAX_REPLY_CHARS) {
                        throw new IOException("The reply exceeded the 32,000-character safety limit.");
                    }

                    fullReply.append(piece);
                    if (listener != null) {
                        listener.onDelta(piece);
                    }
                }
            }

            if (fullReply.toString().trim().isEmpty()) {
                throw new IOException(
                        "The API returned no text. Check the model name and Chat Completions endpoint.");
            }
            return fullReply.toString();
        } finally {
            connection.disconnect();
        }
    }

    private static String buildRequest(
            String model,
            String systemPrompt,
            List<ChatMessage> history,
            String userText) throws IOException {
        try {
            JSONArray messages = new JSONArray();
            messages.put(new JSONObject()
                    .put("role", "system")
                    .put("content", systemPrompt));

            if (history != null) {
                for (ChatMessage message : history) {
                    messages.put(new JSONObject()
                            .put("role", message.role)
                            .put("content", message.content));
                }
            }

            messages.put(new JSONObject()
                    .put("role", "user")
                    .put("content", userText));

            return new JSONObject()
                    .put("model", model)
                    .put("stream", true)
                    .put("messages", messages)
                    .toString();
        } catch (JSONException exception) {
            throw new IOException("Could not prepare the chat request.", exception);
        }
    }

    private static String readBody(InputStream input, int maxChars) throws IOException {
        if (input == null) {
            return "";
        }
        StringBuilder body = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(input, StandardCharsets.UTF_8))) {
            char[] buffer = new char[1024];
            int count;
            while ((count = reader.read(buffer)) != -1 && body.length() < maxChars) {
                int accepted = Math.min(count, maxChars - body.length());
                body.append(buffer, 0, accepted);
            }
        }
        return body.toString();
    }

    private static String extractError(String responseBody) {
        if (responseBody == null || responseBody.trim().isEmpty()) {
            return "The server did not provide an error description.";
        }
        try {
            JSONObject json = new JSONObject(responseBody);
            JSONObject error = json.optJSONObject("error");
            if (error != null) {
                return safeError(
                        error.optString("message", "The server rejected the request."),
                        null);
            }
            return safeError(json.optString("message", responseBody), null);
        } catch (JSONException ignored) {
            return safeError(responseBody, null);
        }
    }

    private static String safeError(String message, String apiKey) {
        String safe = message == null ? "Unknown API error." : message.trim();
        if (apiKey != null && !apiKey.isEmpty()) {
            safe = safe.replace(apiKey, "[hidden]");
        }
        safe = safe.replaceAll("[\\r\\n\\t]+", " ");
        if (safe.length() > 350) {
            safe = safe.substring(0, 350) + "…";
        }
        return safe;
    }
}
