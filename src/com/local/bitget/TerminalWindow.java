package com.local.bitget;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.GridLayout;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.text.DecimalFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JScrollBar;
import javax.swing.JSeparator;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import javax.swing.border.Border;
import javax.swing.plaf.basic.BasicScrollBarUI;
import javax.swing.Scrollable;

final class TerminalWindow {
    private static final Color BLACK = new Color(3, 0, 0);
    private static final Color PANEL = new Color(8, 2, 3);
    private static final Color RED = new Color(255, 35, 55);
    private static final Color GREEN = new Color(0, 230, 118);
    private static final Color DIM = new Color(146, 33, 48);
    private static final Color PALE = new Color(226, 145, 157);
    private static final Color LINE = new Color(105, 9, 22);
    private static final Font FONT = new Font("DejaVu Sans Mono", Font.PLAIN, 16);
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("yyyy-MM-dd  HH:mm:ss");
    private static final String MAIN_CARD = "MAIN";
    private static final String COMPACT_CARD = "COMPACT";

    private final JFrame frame = new JFrame("Bitget Private Futures Terminal");
    private final JPanel cards = new JPanel(new CardLayout());
    private final JPanel mainHeader = panel(new BorderLayout(10, 0));
    private final JPanel compactPanel = panel(new BorderLayout(12, 0));
    private final JPanel positionsPanel = panel();
    private final JLabel appTitle = label("[ BITGET // PRIVATE FUTURES TERMINAL // JAVA ]", 16, RED, true);
    private final JLabel totalEquity = label("—", 22, RED, true);
    private final JLabel available = label("—", 18, PALE, true);
    private final JLabel accountPnl = label("—", 18, PALE, true);
    private final JLabel accountMode = label("LOCKED", 16, DIM, true);
    private final JLabel status = label("STARTING MARKET FEED...", 14, DIM, true);
    private final JLabel footerDetail = label("REFRESH 1.0 SEC // READ ONLY", 14, DIM, true);
    private final JLabel apiState = label("API: LOCKED", 14, RED, true);
    private final JLabel clock = label("", 14, DIM, false);
    private final JLabel compactAccount = label("API LOCKED", 16, DIM, true);
    private final JLabel[] priceLabels = new JLabel[3];
    private final JLabel[] changeLabels = new JLabel[3];
    private final JLabel[] compactQuotes = new JLabel[3];
    private final JLabel[] gainerSymbols = new JLabel[5];
    private final JLabel[] gainerChanges = new JLabel[5];
    private final String[] majorSymbols = {"BTCUSDT", "ETHUSDT", "SOLUSDT"};
    private final BitgetClient client = new BitgetClient();
    private final CredentialStore credentialStore = new CredentialStore();
    private final StateStore stateStore = new StateStore();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(task -> {
        Thread thread = new Thread(task, "bitget-refresh");
        thread.setDaemon(true);
        return thread;
    });
    private final AtomicBoolean refreshRunning = new AtomicBoolean();
    private final Timer clockTimer;
    private final WindowInteractionController interaction;
    private JPanel summaryPanel;
    private JPanel contentStack;
    private JPanel dashboardPair;
    private volatile Models.Credentials credentials;
    private Rectangle mainBounds;
    private boolean compact;
    private int responsiveMode = -1;
    private int positionCount;
    private String fullStatus = "STARTING MARKET FEED...";
    private String shortStatus = "STARTING...";
    private SettingsDialog settingsDialog;

    TerminalWindow() {
        frame.setUndecorated(true);
        frame.setAlwaysOnTop(true);
        frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        frame.setBackground(BLACK);

        JPanel root = panel(new BorderLayout());
        root.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(RED, 2), BorderFactory.createEmptyBorder(10, 10, 10, 10)));
        cards.setBackground(BLACK);
        cards.add(buildMainPanel(), MAIN_CARD);
        cards.add(buildCompactPanel(), COMPACT_CARD);
        root.add(cards, BorderLayout.CENTER);
        frame.setContentPane(root);

        StateStore.WindowState state = stateStore.load();
        mainBounds = keepOnScreen(state.bounds);
        frame.setMinimumSize(new Dimension(680, 480));
        frame.setBounds(mainBounds);
        interaction = new WindowInteractionController(frame, mainHeader, compactPanel);

        frame.addComponentListener(new ComponentAdapter() {
            @Override public void componentMoved(ComponentEvent event) { rememberMainBounds(); }
            @Override public void componentResized(ComponentEvent event) {
                rememberMainBounds();
                if (!compact) applyResponsiveLayout();
            }
        });
        frame.addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent event) { close(); }
        });

        try {
            credentials = credentialStore.load();
            if (credentials != null) {
                apiState.setText("API: READY");
                accountMode.setText(credentials.accountMode.toUpperCase(Locale.ROOT));
            }
        } catch (RuntimeException error) {
            setStatus("KEYRING: " + compactMessage(error), "KEYRING ERROR");
        }

        clockTimer = new Timer(1000, event -> clock.setText(LocalDateTime.now().format(CLOCK)));
        clockTimer.setInitialDelay(0);
        if (state.compact) setCompact(true, false);
    }

    void show() {
        frame.setVisible(true);
        applyResponsiveLayout();
        clockTimer.start();
        scheduler.scheduleAtFixedRate(this::refresh, 0, 1, TimeUnit.SECONDS);
    }

    void setCompact(boolean value) {
        setCompact(value, true);
    }

    private void setCompact(boolean value, boolean captureBounds) {
        if (value == compact && frame.isDisplayable()) return;
        if (value) {
            if (captureBounds && !compact) mainBounds = frame.getBounds();
            compact = true;
            ((CardLayout) cards.getLayout()).show(cards, COMPACT_CARD);
            frame.setMinimumSize(new Dimension(620, 76));
            Rectangle screen = usableScreen();
            int width = Math.min(920, screen.width - 20);
            frame.setBounds(screen.x + Math.max(10, (screen.width - width) / 2), screen.y + 8, width, 82);
            interaction.setDragAreas(compactPanel);
            applyFontScale(compactPanel, 1.0);
        } else {
            compact = false;
            ((CardLayout) cards.getLayout()).show(cards, MAIN_CARD);
            frame.setMinimumSize(new Dimension(680, 480));
            frame.setBounds(keepOnScreen(mainBounds));
            interaction.setDragAreas(mainHeader);
            responsiveMode = -1;
            applyResponsiveLayout();
        }
        cards.revalidate();
        cards.repaint();
        stateStore.save(mainBounds, compact);
    }

    JFrame frame() {
        return frame;
    }

    String uiContract() {
        return "{\"runtime\":\"java\",\"undecorated\":" + frame.isUndecorated()
                + ",\"alwaysOnTop\":" + frame.isAlwaysOnTop()
                + ",\"resizable\":" + frame.isResizable()
                + ",\"responsive\":true"
                + ",\"baseFont\":16,\"compactWidth\":920,\"compactHeight\":82"
                + ",\"minWidth\":" + frame.getMinimumSize().width
                + ",\"minHeight\":" + frame.getMinimumSize().height
                + ",\"compact\":" + compact + "}";
    }

    boolean settingsSingletonCheck() {
        openSettings();
        SettingsDialog first = settingsDialog;
        for (int i = 0; i < 10; i++) openSettings();
        return first != null && first == settingsDialog && first.isDisplayable();
    }

    void capture(Path path) {
        BufferedImage image = new BufferedImage(frame.getWidth(), frame.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        frame.paintAll(graphics);
        graphics.dispose();
        try {
            ImageIO.write(image, "png", path.toFile());
        } catch (IOException error) {
            throw new IllegalStateException("cannot save screenshot: " + path, error);
        }
    }

    void close() {
        rememberMainBounds();
        stateStore.save(mainBounds, compact);
        scheduler.shutdownNow();
        clockTimer.stop();
        interaction.close();
        if (settingsDialog != null) settingsDialog.dispose();
        frame.dispose();
    }

    private JPanel buildMainPanel() {
        JPanel main = panel(new BorderLayout(0, 12));
        buildHeader(mainHeader, false);
        main.add(mainHeader, BorderLayout.NORTH);

        contentStack = new ResponsivePanel();
        contentStack.setBackground(BLACK);
        contentStack.setForeground(RED);
        contentStack.setLayout(new BoxLayout(contentStack, BoxLayout.Y_AXIS));
        summaryPanel = panel(new GridLayout(1, 2, 10, 0));
        summaryPanel.add(buildAccountPanel());
        summaryPanel.add(buildMarketPanel());
        summaryPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        contentStack.add(summaryPanel);
        contentStack.add(Box.createVerticalStrut(10));

        dashboardPair = panel(new GridLayout(1, 2, 4, 0));
        dashboardPair.add(section("OPEN FUTURES POSITIONS", buildPositionsPanel()));
        dashboardPair.add(section("24H FUTURES SURGE // TOP 5", buildGainersPanel()));
        dashboardPair.setAlignmentX(Component.LEFT_ALIGNMENT);
        contentStack.add(dashboardPair);
        contentStack.add(Box.createVerticalGlue());

        JScrollPane dashboardScroll = new JScrollPane(contentStack,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        dashboardScroll.setBorder(null);
        dashboardScroll.setBackground(BLACK);
        dashboardScroll.getViewport().setBackground(BLACK);
        dashboardScroll.getVerticalScrollBar().setUnitIncrement(20);
        styleScrollBar(dashboardScroll);
        main.add(dashboardScroll, BorderLayout.CENTER);

        JPanel footer = panel(new BorderLayout());
        footer.add(status, BorderLayout.WEST);
        footer.add(footerDetail, BorderLayout.EAST);
        main.add(footer, BorderLayout.SOUTH);
        return main;
    }

    private JPanel buildCompactPanel() {
        JLabel title = label("[ BITGET // LIVE ]", 16, RED, true);
        compactPanel.add(title, BorderLayout.WEST);
        JPanel quotes = panel(new GridLayout(1, 4, 10, 0));
        for (int i = 0; i < 3; i++) {
            compactQuotes[i] = label(shortSymbol(majorSymbols[i]) + " —", 15, RED, true);
            compactQuotes[i].setHorizontalAlignment(SwingConstants.CENTER);
            quotes.add(compactQuotes[i]);
        }
        compactAccount.setHorizontalAlignment(SwingConstants.CENTER);
        quotes.add(compactAccount);
        compactPanel.add(quotes, BorderLayout.CENTER);
        JPanel actions = panel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        actions.add(button("OPEN", event -> setCompact(false)));
        actions.add(button("X", event -> close()));
        compactPanel.add(actions, BorderLayout.EAST);
        return compactPanel;
    }

    private void buildHeader(JPanel header, boolean ignored) {
        header.add(appTitle, BorderLayout.WEST);
        JPanel right = panel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        right.add(apiState);
        right.add(clock);
        right.add(button("MINI", event -> setCompact(true)));
        right.add(button("CFG", event -> openSettings()));
        right.add(button("X", event -> close()));
        header.add(right, BorderLayout.EAST);
    }

    private JPanel buildAccountPanel() {
        JPanel body = panel();
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.add(accountRow("TOTAL EQUITY", totalEquity, "USDT"));
        body.add(accountRow("AVAILABLE", available, "USDT"));
        body.add(accountRow("UNREALIZED PNL", accountPnl, "USDT"));
        body.add(accountRow("MODE", accountMode, ""));
        return section("ACCOUNT EQUITY", body);
    }

    private JPanel buildMarketPanel() {
        JPanel grid = panel();
        grid.setLayout(new BoxLayout(grid, BoxLayout.Y_AXIS));
        for (int i = 0; i < majorSymbols.length; i++) {
            JPanel quote = panel(new FlowLayout(FlowLayout.LEFT, 8, 2));
            JLabel symbol = label(shortSymbol(majorSymbols[i]) + "/USDT", 14, DIM, true);
            symbol.setPreferredSize(new Dimension(92, 30));
            priceLabels[i] = label("—", 22, RED, true);
            priceLabels[i].setPreferredSize(new Dimension(145, 30));
            changeLabels[i] = label("— / 24H", 15, DIM, true);
            quote.add(symbol);
            quote.add(priceLabels[i]);
            quote.add(changeLabels[i]);
            quote.setAlignmentX(Component.LEFT_ALIGNMENT);
            quote.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
            grid.add(quote);
        }
        return section("MARKET FEED", grid);
    }

    private JPanel buildPositionsPanel() {
        positionsPanel.setLayout(new BoxLayout(positionsPanel, BoxLayout.Y_AXIS));
        showPositionMessage("API KEY REQUIRED // PRESS [CFG]");
        return positionsPanel;
    }

    private JPanel buildGainersPanel() {
        JPanel body = panel();
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        for (int i = 0; i < 5; i++) {
            JPanel row = panel(new BorderLayout());
            gainerSymbols[i] = label(String.format("%02d  —", i + 1), 17, PALE, true);
            gainerChanges[i] = label("—", 17, RED, true);
            row.add(gainerSymbols[i], BorderLayout.WEST);
            row.add(gainerChanges[i], BorderLayout.EAST);
            row.setBorder(BorderFactory.createEmptyBorder(5, 2, 5, 2));
            body.add(row);
        }
        return body;
    }

    private JPanel accountRow(String title, JLabel value, String unit) {
        JPanel row = panel(new FlowLayout(FlowLayout.LEFT, 8, 2));
        JLabel titleLabel = label(title, 13, DIM, true);
        titleLabel.setPreferredSize(new Dimension(130, 30));
        value.setPreferredSize(new Dimension(125, 30));
        row.add(titleLabel);
        row.add(value);
        if (!unit.isEmpty()) row.add(label(unit, 13, DIM, true));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        return row;
    }

    private JPanel section(String title, Component content) {
        JPanel section = panel(new BorderLayout(0, 6));
        section.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(LINE, 1), BorderFactory.createEmptyBorder(8, 10, 9, 10)));
        section.add(label("[ " + title + " ]", 15, RED, true), BorderLayout.NORTH);
        section.add(content, BorderLayout.CENTER);
        return section;
    }

    private void refresh() {
        if (!refreshRunning.compareAndSet(false, true)) return;
        try {
            try {
                Models.Dashboard dashboard = client.fetchMarketDashboard();
                SwingUtilities.invokeLater(() -> updateMarket(dashboard));
            } catch (RuntimeException error) {
                SwingUtilities.invokeLater(() -> setStatus(
                        "MARKET ERROR // " + compactMessage(error), "MARKET ERROR"));
            }
            Models.Credentials current = credentials;
            if (current != null) {
                try {
                    Models.AccountSnapshot account = client.fetchAccount(current);
                    SwingUtilities.invokeLater(() -> updateAccount(account));
                } catch (RuntimeException error) {
                    SwingUtilities.invokeLater(() -> showAccountError(error));
                }
            }
        } finally {
            refreshRunning.set(false);
        }
    }

    private void updateMarket(Models.Dashboard dashboard) {
        for (int i = 0; i < majorSymbols.length; i++) {
            Models.Quote quote = dashboard.quotes.get(majorSymbols[i]);
            priceLabels[i].setText(formatPrice(quote.price));
            priceLabels[i].setForeground(trendColor(quote.changePct));
            String change = String.format(Locale.US, "%+.2f%%", quote.changePct);
            changeLabels[i].setText(change + " / 24H");
            changeLabels[i].setForeground(trendColor(quote.changePct));
            compactQuotes[i].setText(shortSymbol(quote.symbol) + " " + formatPrice(quote.price));
            compactQuotes[i].setForeground(trendColor(quote.changePct));
        }
        for (int i = 0; i < gainerSymbols.length; i++) {
            if (i < dashboard.gainers.size()) {
                Models.Quote quote = dashboard.gainers.get(i);
                gainerSymbols[i].setText(String.format("%02d  %s", i + 1, shortSymbol(quote.symbol)));
                gainerChanges[i].setText(String.format(Locale.US, "%+.2f%%", quote.changePct));
                gainerChanges[i].setForeground(trendColor(quote.changePct));
            }
        }
        setStatus("MARKET FEED ONLINE // " + dashboard.gainers.size() + " CONTRACTS RANKED",
                "MARKET ONLINE // 1 SEC");
    }

    private void updateAccount(Models.AccountSnapshot account) {
        totalEquity.setText(formatMoney(account.totalEquity));
        available.setText(formatMoney(account.available));
        accountPnl.setText(signedMoney(account.unrealizedPnl));
        accountPnl.setForeground(trendColor(account.unrealizedPnl));
        accountMode.setText(account.accountMode);
        apiState.setText("API: ONLINE");
        compactAccount.setText("PNL " + signedMoney(account.unrealizedPnl));
        compactAccount.setForeground(trendColor(account.unrealizedPnl));
        showPositions(account.positions);
        setStatus("PRIVATE API ONLINE // READ ONLY // " + account.accountMode,
                "API ONLINE // " + account.accountMode);
    }

    private void showPositions(List<Models.Position> positions) {
        positionCount = positions.size();
        positionsPanel.removeAll();
        if (positions.isEmpty()) {
            positionsPanel.add(label("NO OPEN FUTURES POSITIONS", 17, DIM, true));
        } else {
            for (Models.Position position : positions) {
                JPanel row = panel(new BorderLayout(12, 0));
                JLabel name = label(position.symbol + "  " + position.side + "  x" + trim(position.leverage), 17, RED, true);
                JLabel detail = label("SIZE " + trim(position.size) + "   ENTRY " + formatPrice(position.entryPrice)
                        + "   MARK " + formatPrice(position.markPrice), 15, PALE, false);
                JLabel pnl = label(signedMoney(position.unrealizedPnl) + "  "
                        + String.format(Locale.US, "%+.2f%%", position.profitRate * 100), 18,
                        trendColor(position.unrealizedPnl), true);
                JPanel text = panel();
                text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
                text.add(name);
                text.add(Box.createVerticalStrut(5));
                text.add(detail);
                row.add(text, BorderLayout.CENTER);
                row.add(pnl, BorderLayout.EAST);
                row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 76));
                row.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createMatteBorder(0, 0, 1, 0, LINE),
                        BorderFactory.createEmptyBorder(10, 2, 10, 2)));
                positionsPanel.add(row);
            }
        }
        positionsPanel.revalidate();
        positionsPanel.repaint();
        applyFontScale(positionsPanel, currentFontScale());
        adjustDashboardHeight();
    }

    private void showPositionMessage(String message) {
        positionCount = 0;
        positionsPanel.removeAll();
        positionsPanel.add(label(message, 17, DIM, true));
        positionsPanel.revalidate();
        positionsPanel.repaint();
        adjustDashboardHeight();
    }

    private void showAccountError(RuntimeException error) {
        apiState.setText("API: ERROR");
        compactAccount.setText("API ERROR");
        compactAccount.setForeground(RED);
        showPositionMessage("PRIVATE API ERROR // OPEN [CFG] // " + compactMessage(error));
        setStatus("PRIVATE API ERROR // " + compactMessage(error), "PRIVATE API ERROR");
    }

    private void openSettings() {
        if (settingsDialog != null && settingsDialog.isDisplayable()) {
            settingsDialog.toFront();
            settingsDialog.requestFocus();
            return;
        }
        settingsDialog = new SettingsDialog();
        settingsDialog.addWindowListener(new WindowAdapter() {
            @Override public void windowClosed(WindowEvent event) { settingsDialog = null; }
        });
        settingsDialog.setVisible(true);
    }

    private void rememberMainBounds() {
        if (!compact && frame.isShowing() && frame.getWidth() >= 680 && frame.getHeight() >= 480) {
            mainBounds = frame.getBounds();
        }
    }

    private Rectangle keepOnScreen(Rectangle requested) {
        Rectangle screen = usableScreen();
        int width = Math.min(Math.max(680, requested.width), screen.width);
        int height = Math.min(Math.max(480, requested.height), screen.height);
        int x = Math.max(screen.x, Math.min(requested.x, screen.x + screen.width - width));
        int y = Math.max(screen.y, Math.min(requested.y, screen.y + screen.height - height));
        return new Rectangle(x, y, width, height);
    }

    private void applyResponsiveLayout() {
        if (compact || summaryPanel == null || dashboardPair == null) return;
        int width = Math.max(frame.getWidth(), 1);
        int mode = width >= 980 ? 0 : width >= 760 ? 1 : 2;
        if (mode == responsiveMode) return;
        responsiveMode = mode;

        if (mode == 0) {
            appTitle.setText("[ BITGET // PRIVATE FUTURES TERMINAL // JAVA ]");
            clock.setVisible(true);
            footerDetail.setVisible(true);
            summaryPanel.setLayout(new GridLayout(1, 2, 10, 0));
            setStackHeight(summaryPanel, 185);
            dashboardPair.setLayout(new GridLayout(1, 2, 4, 0));
            setStackHeight(dashboardPair, splitHeight(230));
        } else if (mode == 1) {
            appTitle.setText("[ BITGET // FUTURES // JAVA ]");
            clock.setVisible(false);
            footerDetail.setVisible(false);
            summaryPanel.setLayout(new GridLayout(1, 2, 8, 0));
            setStackHeight(summaryPanel, 185);
            dashboardPair.setLayout(new GridLayout(2, 1, 0, 8));
            setStackHeight(dashboardPair, splitHeight(380));
        } else {
            appTitle.setText("[ BITGET ]");
            clock.setVisible(false);
            footerDetail.setVisible(false);
            summaryPanel.setLayout(new GridLayout(2, 1, 0, 8));
            setStackHeight(summaryPanel, 380);
            dashboardPair.setLayout(new GridLayout(2, 1, 0, 8));
            setStackHeight(dashboardPair, splitHeight(400));
        }

        status.setText(mode == 0 ? fullStatus : shortStatus);
        applyFontScale(cards, currentFontScale());
        contentStack.revalidate();
        contentStack.repaint();
    }

    private static void setStackHeight(JComponent component, int height) {
        component.setMinimumSize(new Dimension(0, height));
        component.setPreferredSize(new Dimension(0, height));
        component.setMaximumSize(new Dimension(Integer.MAX_VALUE, height));
    }

    private int splitHeight(int base) {
        return base + Math.max(0, positionCount - 2) * 72;
    }

    private void adjustDashboardHeight() {
        if (dashboardPair == null) return;
        int base = responsiveMode == 0 ? 230 : responsiveMode == 1 ? 380 : 400;
        setStackHeight(dashboardPair, splitHeight(base));
        if (contentStack != null) contentStack.revalidate();
    }

    private static void styleScrollBar(JScrollPane scroll) {
        JScrollBar bar = scroll.getVerticalScrollBar();
        bar.setPreferredSize(new Dimension(9, 0));
        bar.setUI(new BasicScrollBarUI() {
            @Override protected void configureScrollBarColors() {
                thumbColor = LINE;
                thumbDarkShadowColor = LINE;
                thumbHighlightColor = RED;
                thumbLightShadowColor = LINE;
                trackColor = BLACK;
                trackHighlightColor = PANEL;
            }

            @Override protected JButton createDecreaseButton(int orientation) { return zeroButton(); }
            @Override protected JButton createIncreaseButton(int orientation) { return zeroButton(); }

            private JButton zeroButton() {
                JButton button = new JButton();
                button.setPreferredSize(new Dimension(0, 0));
                button.setMinimumSize(new Dimension(0, 0));
                button.setMaximumSize(new Dimension(0, 0));
                return button;
            }
        });
    }

    private double currentFontScale() {
        if (compact || responsiveMode == 0) return 1.0;
        return responsiveMode == 1 ? 0.92 : 0.84;
    }

    private static void applyFontScale(Component component, double scale) {
        if (component instanceof JComponent) {
            JComponent swing = (JComponent) component;
            Object base = swing.getClientProperty("baseFontSize");
            if (base instanceof Number && swing.getFont() != null) {
                float size = Math.max(12f, ((Number) base).floatValue() * (float) scale);
                swing.setFont(swing.getFont().deriveFont(size));
            }
        }
        if (component instanceof java.awt.Container) {
            for (Component child : ((java.awt.Container) component).getComponents()) {
                applyFontScale(child, scale);
            }
        }
    }

    private void setStatus(String full, String shortText) {
        fullStatus = full;
        shortStatus = shortText;
        status.setText(responsiveMode <= 0 ? full : shortText);
    }

    private static Rectangle usableScreen() {
        GraphicsDevice device = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
        return device.getDefaultConfiguration().getBounds();
    }

    private static JPanel panel() {
        return panel(new FlowLayout(FlowLayout.LEFT, 0, 0));
    }

    private static JPanel panel(java.awt.LayoutManager layout) {
        JPanel result = new JPanel(layout);
        result.setBackground(BLACK);
        result.setForeground(RED);
        return result;
    }

    private static JLabel label(String text, int size, Color color, boolean bold) {
        JLabel result = new JLabel(text);
        result.setForeground(color);
        result.setFont(FONT.deriveFont(bold ? Font.BOLD : Font.PLAIN, (float) size));
        result.putClientProperty("baseFontSize", Float.valueOf(size));
        result.setAlignmentX(Component.LEFT_ALIGNMENT);
        return result;
    }

    private static JButton button(String text, java.awt.event.ActionListener action) {
        JButton button = new JButton("[ " + text + " ]");
        button.setFont(FONT.deriveFont(Font.BOLD, 15f));
        button.putClientProperty("baseFontSize", Float.valueOf(15));
        button.setForeground(RED);
        button.setBackground(BLACK);
        button.setBorder(BorderFactory.createLineBorder(LINE));
        button.setFocusPainted(false);
        button.setContentAreaFilled(true);
        button.setOpaque(true);
        button.setPreferredSize(new Dimension(Math.max(58, text.length() * 14 + 28), 32));
        button.addActionListener(action);
        return button;
    }

    private static String shortSymbol(String symbol) {
        return symbol == null ? "" : symbol.replace("USDT", "");
    }

    static String formatPrice(double value) {
        if (Math.abs(value) >= 1) return String.format(Locale.US, "%,.2f", value);
        if (Math.abs(value) >= 0.01) return String.format(Locale.US, "%.4f", value);
        return String.format(Locale.US, "%.6f", value);
    }

    static String formatMoney(double value) {
        return String.format(Locale.US, "%,.2f", value);
    }

    static String signedMoney(double value) {
        return String.format(Locale.US, "%+,.2f", value);
    }

    private static String trim(double value) {
        return new DecimalFormat("0.########").format(value);
    }

    private static String compactMessage(Throwable error) {
        Throwable value = error;
        while (value.getCause() != null && value.getCause() != value) value = value.getCause();
        String message = value.getMessage();
        if (message == null || message.trim().isEmpty()) message = value.getClass().getSimpleName();
        message = message.replaceAll("\\s+", " ").trim();
        return message.length() > 110 ? message.substring(0, 110) : message;
    }

    static Color trendColor(double value) {
        if (value > 0) return GREEN;
        if (value < 0) return RED;
        return DIM;
    }

    private static final class ResponsivePanel extends JPanel implements Scrollable {
        private static final long serialVersionUID = 1L;

        @Override public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
        @Override public int getScrollableUnitIncrement(Rectangle visible, int orientation, int direction) { return 20; }
        @Override public int getScrollableBlockIncrement(Rectangle visible, int orientation, int direction) {
            return Math.max(60, visible.height - 40);
        }
        @Override public boolean getScrollableTracksViewportWidth() { return true; }
        @Override public boolean getScrollableTracksViewportHeight() { return false; }
    }

    private final class SettingsDialog extends JDialog {
        private static final long serialVersionUID = 1L;
        private final JTextField apiKey = field(false);
        private final JPasswordField secretKey = (JPasswordField) field(true);
        private final JPasswordField passphrase = (JPasswordField) field(true);
        private final JComboBox<String> mode = new JComboBox<>(new String[]{"AUTO", "CLASSIC", "UTA"});
        private final JLabel result = label("READ-ONLY KEY RECOMMENDED", 14, DIM, true);
        private final JButton testButton;
        private final JButton saveButton;

        SettingsDialog() {
            super(frame, "Bitget API Settings", false);
            setAlwaysOnTop(true);
            setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
            getContentPane().setBackground(BLACK);
            JPanel body = panel();
            body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
            body.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(RED, 2),
                    BorderFactory.createEmptyBorder(20, 22, 20, 22)));
            body.add(label("[ BITGET READ-ONLY API SETTINGS ]", 20, RED, true));
            body.add(Box.createVerticalStrut(18));
            addField(body, "API KEY", apiKey);
            addField(body, "SECRET KEY", secretKey);
            addField(body, "PASSPHRASE", passphrase);
            mode.setFont(FONT.deriveFont(Font.BOLD, 16f));
            mode.setBackground(PANEL);
            mode.setForeground(RED);
            addField(body, "ACCOUNT MODE", mode);
            body.add(Box.createVerticalStrut(8));
            body.add(result);
            body.add(Box.createVerticalStrut(16));

            JPanel actions = panel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
            testButton = button("TEST", event -> testConnection());
            saveButton = button("SAVE", event -> saveAndApply());
            actions.add(testButton);
            actions.add(saveButton);
            actions.add(button("CLEAR", event -> clearCredentials()));
            actions.add(button("CLOSE", event -> dispose()));
            body.add(actions);
            setContentPane(body);
            setSize(680, 540);
            setMinimumSize(new Dimension(620, 500));
            setLocationRelativeTo(frame);

            Models.Credentials current = credentials;
            if (current != null) {
                apiKey.setText(current.apiKey);
                secretKey.setText(current.secretKey);
                passphrase.setText(current.passphrase);
                mode.setSelectedItem(current.accountMode.toUpperCase(Locale.ROOT));
            }
        }

        private void addField(JPanel body, String name, Component input) {
            body.add(label(name, 14, DIM, true));
            body.add(Box.createVerticalStrut(5));
            input.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
            body.add(input);
            body.add(Box.createVerticalStrut(13));
        }

        private Models.Credentials enteredCredentials() {
            Models.Credentials entered = new Models.Credentials(apiKey.getText(),
                    new String(secretKey.getPassword()), new String(passphrase.getPassword()),
                    String.valueOf(mode.getSelectedItem()).toLowerCase(Locale.ROOT));
            entered.validate();
            return entered;
        }

        private void testConnection() {
            Models.Credentials entered;
            try {
                entered = enteredCredentials();
            } catch (RuntimeException error) {
                result.setText(compactMessage(error));
                return;
            }
            setBusy(true, "TESTING CONNECTION...");
            new SwingWorker<Models.AccountSnapshot, Void>() {
                @Override protected Models.AccountSnapshot doInBackground() { return client.fetchAccount(entered); }
                @Override protected void done() {
                    try {
                        Models.AccountSnapshot account = get();
                        result.setText("CONNECTION OK // " + account.accountMode + " // EQ "
                                + formatMoney(account.totalEquity));
                    } catch (Exception error) {
                        result.setText("CONNECTION FAILED // " + compactMessage(error));
                    } finally {
                        setBusy(false, null);
                    }
                }
            }.execute();
        }

        private void saveAndApply() {
            Models.Credentials entered;
            try {
                entered = enteredCredentials();
            } catch (RuntimeException error) {
                result.setText("SAVE FAILED // " + compactMessage(error));
                return;
            }
            setBusy(true, "SAVING TO GNOME KEYRING...");
            Models.Credentials value = entered;
            new SwingWorker<Void, Void>() {
                @Override protected Void doInBackground() {
                    credentialStore.save(value);
                    return null;
                }

                @Override protected void done() {
                    try {
                        get();
                        credentials = value;
                        apiState.setText("API: READY");
                        accountMode.setText(value.accountMode.toUpperCase(Locale.ROOT));
                        result.setText("SAVED TO GNOME KEYRING // APPLIED");
                        scheduler.execute(TerminalWindow.this::refresh);
                    } catch (Exception error) {
                        result.setText("SAVE FAILED // " + compactMessage(error));
                    } finally {
                        setBusy(false, null);
                    }
                }
            }.execute();
        }

        private void clearCredentials() {
            try {
                credentialStore.clear();
                credentials = null;
                apiKey.setText("");
                secretKey.setText("");
                passphrase.setText("");
                apiState.setText("API: LOCKED");
                accountMode.setText("LOCKED");
                compactAccount.setText("API LOCKED");
                compactAccount.setForeground(DIM);
                totalEquity.setText("—");
                available.setText("—");
                accountPnl.setText("—");
                accountPnl.setForeground(DIM);
                showPositionMessage("API KEY REQUIRED // PRESS [CFG]");
                result.setText("GNOME KEYRING ENTRY CLEARED");
            } catch (RuntimeException error) {
                result.setText("CLEAR FAILED // " + compactMessage(error));
            }
        }

        private void setBusy(boolean busy, String message) {
            testButton.setEnabled(!busy);
            saveButton.setEnabled(!busy);
            if (message != null) result.setText(message);
        }

        private JTextField field(boolean password) {
            JTextField field = password ? new JPasswordField() : new JTextField();
            field.setFont(FONT.deriveFont(Font.PLAIN, 17f));
            field.setForeground(PALE);
            field.setCaretColor(RED);
            field.setBackground(PANEL);
            Border border = BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(LINE),
                    BorderFactory.createEmptyBorder(5, 8, 5, 8));
            field.setBorder(border);
            return field;
        }
    }
}
