package com.local.bitget;

import java.awt.Component;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.LinkedHashSet;
import java.util.Set;
import javax.swing.AbstractButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import javax.swing.text.JTextComponent;

final class WindowInteractionController implements AutoCloseable {
    private static final int NORTH = 1;
    private static final int SOUTH = 2;
    private static final int WEST = 4;
    private static final int EAST = 8;
    private static final int MARGIN = 12;

    private final JFrame frame;
    private final Set<Component> resizeComponents = new LinkedHashSet<>();
    private final Set<Component> moveComponents = new LinkedHashSet<>();
    private Point startPointer;
    private Rectangle startBounds;
    private int resizeEdge;
    private boolean moving;

    private final MouseAdapter resizeListener = new MouseAdapter() {
        @Override public void mouseMoved(MouseEvent event) {
            if (!isInteracting()) frame.setCursor(cursorFor(edgeAt(event.getLocationOnScreen())));
        }

        @Override public void mousePressed(MouseEvent event) {
            if (!SwingUtilities.isLeftMouseButton(event)) return;
            int edge = edgeAt(event.getLocationOnScreen());
            if (edge == 0) return;
            startPointer = event.getLocationOnScreen();
            startBounds = frame.getBounds();
            resizeEdge = edge;
            moving = false;
        }

        @Override public void mouseDragged(MouseEvent event) {
            if (resizeEdge != 0 && startPointer != null && startBounds != null) {
                resize(event.getLocationOnScreen());
            }
        }

        @Override public void mouseReleased(MouseEvent event) {
            if (resizeEdge != 0) reset();
        }
    };

    private final MouseAdapter moveListener = new MouseAdapter() {
        @Override public void mousePressed(MouseEvent event) {
            if (!SwingUtilities.isLeftMouseButton(event) || edgeAt(event.getLocationOnScreen()) != 0) return;
            startPointer = event.getLocationOnScreen();
            startBounds = frame.getBounds();
            moving = true;
            resizeEdge = 0;
        }

        @Override public void mouseDragged(MouseEvent event) {
            if (!moving || startPointer == null || startBounds == null) return;
            Point current = event.getLocationOnScreen();
            frame.setLocation(startBounds.x + current.x - startPointer.x,
                    startBounds.y + current.y - startPointer.y);
        }

        @Override public void mouseReleased(MouseEvent event) {
            if (moving) reset();
        }
    };

    WindowInteractionController(JFrame frame, Component... initialDragAreas) {
        this.frame = frame;
        addResizeListeners(frame.getContentPane());
        setDragAreas(initialDragAreas);
    }

    void setDragAreas(Component... areas) {
        for (Component component : moveComponents) {
            component.removeMouseListener(moveListener);
            component.removeMouseMotionListener(moveListener);
        }
        moveComponents.clear();
        for (Component area : areas) addMoveListeners(area);
    }

    boolean isInteracting() {
        return moving || resizeEdge != 0;
    }

    private void addResizeListeners(Component component) {
        if (resizeComponents.add(component)) {
            component.addMouseListener(resizeListener);
            component.addMouseMotionListener(resizeListener);
        }
        if (component instanceof Container) {
            for (Component child : ((Container) component).getComponents()) addResizeListeners(child);
        }
    }

    private void addMoveListeners(Component component) {
        if (component instanceof AbstractButton || component instanceof JTextComponent
                || component instanceof JComboBox) return;
        if (moveComponents.add(component)) {
            component.addMouseListener(moveListener);
            component.addMouseMotionListener(moveListener);
        }
        if (component instanceof Container) {
            for (Component child : ((Container) component).getComponents()) addMoveListeners(child);
        }
    }

    private int edgeAt(Point pointer) {
        Point origin = frame.getLocationOnScreen();
        int x = pointer.x - origin.x;
        int y = pointer.y - origin.y;
        int edge = 0;
        if (y >= 0 && y <= MARGIN) edge |= NORTH;
        else if (y <= frame.getHeight() && y >= frame.getHeight() - MARGIN) edge |= SOUTH;
        if (x >= 0 && x <= MARGIN) edge |= WEST;
        else if (x <= frame.getWidth() && x >= frame.getWidth() - MARGIN) edge |= EAST;
        return edge;
    }

    private void resize(Point current) {
        int dx = current.x - startPointer.x;
        int dy = current.y - startPointer.y;
        Rectangle next = new Rectangle(startBounds);
        Dimension minimum = frame.getMinimumSize();
        if ((resizeEdge & EAST) != 0) next.width = Math.max(minimum.width, startBounds.width + dx);
        if ((resizeEdge & SOUTH) != 0) next.height = Math.max(minimum.height, startBounds.height + dy);
        if ((resizeEdge & WEST) != 0) {
            int width = Math.max(minimum.width, startBounds.width - dx);
            next.x = startBounds.x + startBounds.width - width;
            next.width = width;
        }
        if ((resizeEdge & NORTH) != 0) {
            int height = Math.max(minimum.height, startBounds.height - dy);
            next.y = startBounds.y + startBounds.height - height;
            next.height = height;
        }
        frame.setBounds(next);
    }

    private void reset() {
        startPointer = null;
        startBounds = null;
        resizeEdge = 0;
        moving = false;
    }

    private static Cursor cursorFor(int edge) {
        switch (edge) {
            case NORTH: return Cursor.getPredefinedCursor(Cursor.N_RESIZE_CURSOR);
            case SOUTH: return Cursor.getPredefinedCursor(Cursor.S_RESIZE_CURSOR);
            case WEST: return Cursor.getPredefinedCursor(Cursor.W_RESIZE_CURSOR);
            case EAST: return Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR);
            case NORTH | WEST: return Cursor.getPredefinedCursor(Cursor.NW_RESIZE_CURSOR);
            case NORTH | EAST: return Cursor.getPredefinedCursor(Cursor.NE_RESIZE_CURSOR);
            case SOUTH | WEST: return Cursor.getPredefinedCursor(Cursor.SW_RESIZE_CURSOR);
            case SOUTH | EAST: return Cursor.getPredefinedCursor(Cursor.SE_RESIZE_CURSOR);
            default: return Cursor.getDefaultCursor();
        }
    }

    @Override public void close() {
        for (Component component : moveComponents) {
            component.removeMouseListener(moveListener);
            component.removeMouseMotionListener(moveListener);
        }
        for (Component component : resizeComponents) {
            component.removeMouseListener(resizeListener);
            component.removeMouseMotionListener(resizeListener);
        }
        moveComponents.clear();
        resizeComponents.clear();
    }
}
