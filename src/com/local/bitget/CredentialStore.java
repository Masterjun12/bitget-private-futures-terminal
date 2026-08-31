package com.local.bitget;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;

final class CredentialStore {
    private static final String SERVICE = "bitget-private-terminal";
    private final String executable;

    CredentialStore() {
        Path standard = Paths.get("/usr/bin/secret-tool");
        executable = Files.isExecutable(standard) ? standard.toString() : "secret-tool";
    }

    Models.Credentials load() {
        ProcessResult result = run(null, "lookup", "service", SERVICE);
        if (result.exitCode != 0 || result.output.trim().isEmpty()) return null;
        Object decoded = MiniJson.parse(result.output.trim());
        if (!(decoded instanceof Map)) throw new StoreException("GNOME Keyring data is invalid");
        Map<?, ?> data = (Map<?, ?>) decoded;
        Models.Credentials credentials = new Models.Credentials(
                text(data.get("api_key")), text(data.get("secret_key")),
                text(data.get("passphrase")), text(data.get("account_mode")));
        credentials.validate();
        return credentials;
    }

    void save(Models.Credentials credentials) {
        credentials.validate();
        Map<String, String> data = new LinkedHashMap<>();
        data.put("api_key", credentials.apiKey);
        data.put("secret_key", credentials.secretKey);
        data.put("passphrase", credentials.passphrase);
        data.put("account_mode", credentials.accountMode);
        ProcessResult result = run(MiniJson.stringify(data), "store",
                "--label=Bitget Private Terminal API credentials", "service", SERVICE);
        if (result.exitCode != 0) throw new StoreException("GNOME Keyring save failed: " + compact(result.output));
    }

    void clear() {
        ProcessResult result = run(null, "clear", "service", SERVICE);
        if (result.exitCode != 0 && !result.output.trim().isEmpty()) {
            throw new StoreException("GNOME Keyring clear failed: " + compact(result.output));
        }
    }

    private ProcessResult run(String input, String... arguments) {
        String[] command = new String[arguments.length + 1];
        command[0] = executable;
        System.arraycopy(arguments, 0, command, 1, arguments.length);
        try {
            Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
            if (input != null) {
                try (OutputStream output = process.getOutputStream()) {
                    output.write(input.getBytes(StandardCharsets.UTF_8));
                }
            } else {
                process.getOutputStream().close();
            }
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            int exitCode = process.waitFor();
            return new ProcessResult(exitCode, output);
        } catch (IOException error) {
            throw new StoreException("secret-tool is unavailable; install libsecret-tools", error);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new StoreException("GNOME Keyring operation interrupted", error);
        }
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static String compact(String message) {
        String clean = message == null ? "" : message.replaceAll("\\s+", " ").trim();
        return clean.length() > 140 ? clean.substring(0, 140) : clean;
    }

    private static final class ProcessResult {
        final int exitCode;
        final String output;
        ProcessResult(int exitCode, String output) {
            this.exitCode = exitCode;
            this.output = output;
        }
    }

    static final class StoreException extends RuntimeException {
        private static final long serialVersionUID = 1L;
        StoreException(String message) { super(message); }
        StoreException(String message, Throwable cause) { super(message, cause); }
    }
}
