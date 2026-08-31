package com.local.bitget;

import java.awt.GraphicsEnvironment;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.event.InputEvent;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

public final class Main {
    private Main() {}

    public static void main(String[] args) throws Exception {
        System.setProperty("awt.useSystemAAFontSettings", "on");
        System.setProperty("swing.aatext", "true");
        installCrashHandler();

        Arguments options = Arguments.parse(args);
        if (options.check) {
            Models.Dashboard dashboard = new BitgetClient().fetchMarketDashboard();
            System.out.println(toJson(dashboard));
            return;
        }
        if (GraphicsEnvironment.isHeadless()) {
            throw new IllegalStateException("A desktop display is required");
        }

        AtomicReference<TerminalWindow> reference = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            TerminalWindow window = new TerminalWindow();
            reference.set(window);
            window.show();
            if (options.forceCompact != null) window.setCompact(options.forceCompact);
            if (options.requestedSize != null) window.frame().setSize(options.requestedSize);
        });
        TerminalWindow window = reference.get();

        if (options.uiCheck) {
            SwingUtilities.invokeAndWait(() -> {
                System.out.println(window.uiContract());
                window.close();
            });
            return;
        }
        if (options.interactionCheck) {
            interactionCheck(window);
            return;
        }
        if (options.settingsCheck) {
            SwingUtilities.invokeAndWait(() -> {
                boolean singleton = window.settingsSingletonCheck();
                System.out.println("{\"repeatedClicks\":10,\"singleDialog\":" + singleton + "}");
                window.close();
                if (!singleton) System.exit(4);
            });
            return;
        }
        if (options.screenshot != null) {
            CountDownLatch finished = new CountDownLatch(1);
            SwingUtilities.invokeLater(() -> {
                Timer timer = new Timer(3000, event -> {
                    ((Timer) event.getSource()).stop();
                    try {
                        window.capture(options.screenshot);
                    } finally {
                        window.close();
                        finished.countDown();
                    }
                });
                timer.setRepeats(false);
                timer.start();
            });
            finished.await();
        }
    }

    private static void interactionCheck(TerminalWindow window) throws Exception {
        Robot robot = new Robot();
        robot.setAutoDelay(45);
        Thread.sleep(250);
        Rectangle initial = window.frame().getBounds();
        robot.mouseMove(initial.x + 180, initial.y + 30);
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        robot.mouseMove(initial.x + 130, initial.y + 65);
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        Thread.sleep(250);
        Rectangle moved = window.frame().getBounds();
        boolean movedOk = Math.abs(moved.x - initial.x) >= 40 && moved.y >= initial.y + 25;

        robot.mouseMove(moved.x + moved.width - 4, moved.y + moved.height - 4);
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        robot.mouseMove(moved.x + moved.width + 56, moved.y + moved.height + 36);
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        Thread.sleep(250);
        Rectangle resized = window.frame().getBounds();
        boolean resizedOk = resized.width >= moved.width + 45 && resized.height >= moved.height + 25;
        System.out.println("{\"moved\":" + movedOk + ",\"resized\":" + resizedOk
                + ",\"from\":\"" + initial.width + "x" + initial.height
                + "\",\"to\":\"" + resized.width + "x" + resized.height + "\"}");
        SwingUtilities.invokeAndWait(window::close);
        if (!movedOk || !resizedOk) System.exit(3);
    }

    private static String toJson(Models.Dashboard dashboard) {
        StringBuilder output = new StringBuilder("{\"runtime\":\"java\",\"quotes\":{");
        boolean first = true;
        for (Models.Quote quote : dashboard.quotes.values()) {
            if (!first) output.append(',');
            first = false;
            output.append('"').append(quote.symbol).append("\":{\"price\":")
                    .append(quote.price).append(",\"change_pct\":").append(quote.changePct).append('}');
        }
        output.append("},\"gainers\":[");
        first = true;
        for (Models.Quote quote : dashboard.gainers) {
            if (!first) output.append(',');
            first = false;
            output.append("{\"symbol\":\"").append(quote.symbol).append("\",\"price\":")
                    .append(quote.price).append(",\"change_pct\":").append(quote.changePct).append('}');
        }
        return output.append("],\"timestamp_ms\":").append(dashboard.timestampMs).append('}').toString();
    }

    private static void installCrashHandler() {
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            writeCrashLog(thread, error);
            if (!GraphicsEnvironment.isHeadless()) {
                SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(null,
                        "Bitget Terminal error:\n" + safeMessage(error)
                                + "\n\nLog: ~/.local/state/bitget-private-terminal/error.log",
                        "Bitget Private Terminal", JOptionPane.ERROR_MESSAGE));
            }
        });
    }

    private static void writeCrashLog(Thread thread, Throwable error) {
        Path log = Paths.get(System.getProperty("user.home"), ".local", "state",
                "bitget-private-terminal", "error.log");
        StringWriter trace = new StringWriter();
        error.printStackTrace(new PrintWriter(trace));
        String entry = "\n[" + java.time.LocalDateTime.now() + "] thread=" + thread.getName()
                + "\n" + trace;
        try {
            Files.createDirectories(log.getParent());
            Files.write(log, entry.getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND);
        } catch (IOException ignored) {
            // Nothing else can be done if the user's state directory is unavailable.
        }
    }

    private static String safeMessage(Throwable error) {
        Throwable value = error;
        while (value.getCause() != null && value.getCause() != value) value = value.getCause();
        String message = value.getMessage();
        return message == null || message.trim().isEmpty() ? value.getClass().getSimpleName() : message;
    }

    private static final class Arguments {
        boolean check;
        boolean uiCheck;
        boolean interactionCheck;
        boolean settingsCheck;
        Path screenshot;
        Boolean forceCompact;
        Dimension requestedSize;

        static Arguments parse(String[] args) {
            Arguments result = new Arguments();
            for (int i = 0; i < args.length; i++) {
                switch (args[i]) {
                    case "--check": result.check = true; break;
                    case "--ui-check": result.uiCheck = true; break;
                    case "--interaction-check":
                        result.interactionCheck = true;
                        result.forceCompact = Boolean.FALSE;
                        break;
                    case "--settings-check": result.settingsCheck = true; break;
                    case "--compact": result.forceCompact = Boolean.TRUE; break;
                    case "--full": result.forceCompact = Boolean.FALSE; break;
                    case "--screenshot":
                        if (++i >= args.length) throw new IllegalArgumentException("--screenshot requires a file");
                        result.screenshot = Paths.get(args[i]);
                        break;
                    case "--size":
                        if (++i >= args.length) throw new IllegalArgumentException("--size requires WIDTHxHEIGHT");
                        String[] size = args[i].toLowerCase(Locale.ROOT).split("x", 2);
                        if (size.length != 2) throw new IllegalArgumentException("invalid --size value");
                        result.requestedSize = new Dimension(Integer.parseInt(size[0]), Integer.parseInt(size[1]));
                        break;
                    default: throw new IllegalArgumentException("unknown option: " + args[i]);
                }
            }
            return result;
        }
    }
}
