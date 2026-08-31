package com.local.bitget;

import java.awt.Rectangle;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

final class StateStore {
    private final Path path = Paths.get(System.getProperty("user.home"), ".config",
            "bitget-private-terminal", "state.properties");

    WindowState load() {
        Properties values = new Properties();
        if (Files.isRegularFile(path)) {
            try (InputStream input = Files.newInputStream(path)) {
                values.load(input);
            } catch (IOException ignored) {
                return WindowState.defaults();
            }
        }
        WindowState defaults = WindowState.defaults();
        return new WindowState(
                integer(values, "x", defaults.bounds.x), integer(values, "y", defaults.bounds.y),
                Math.max(680, integer(values, "width", defaults.bounds.width)),
                Math.max(480, integer(values, "height", defaults.bounds.height)),
                Boolean.parseBoolean(values.getProperty("compact", "false")));
    }

    void save(Rectangle bounds, boolean compact) {
        Properties values = new Properties();
        values.setProperty("x", Integer.toString(bounds.x));
        values.setProperty("y", Integer.toString(bounds.y));
        values.setProperty("width", Integer.toString(bounds.width));
        values.setProperty("height", Integer.toString(bounds.height));
        values.setProperty("compact", Boolean.toString(compact));
        try {
            Files.createDirectories(path.getParent());
            try (OutputStream output = Files.newOutputStream(path)) {
                values.store(output, "Bitget Private Terminal window state; no credentials");
            }
        } catch (IOException ignored) {
            // Window state is optional. Credentials are handled by GNOME Keyring.
        }
    }

    private static int integer(Properties values, String key, int fallback) {
        try { return Integer.parseInt(values.getProperty(key, Integer.toString(fallback))); }
        catch (NumberFormatException ignored) { return fallback; }
    }

    static final class WindowState {
        final Rectangle bounds;
        final boolean compact;

        WindowState(int x, int y, int width, int height, boolean compact) {
            this.bounds = new Rectangle(x, y, width, height);
            this.compact = compact;
        }

        static WindowState defaults() {
            return new WindowState(80, 60, 1000, 550, false);
        }
    }
}
