/*
 * Copyright 2000-2013 Vaadin Ltd.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.vaadin.client.ui;

import com.google.gwt.animation.client.Animation;
import com.google.gwt.aria.client.Roles;
import com.google.gwt.dom.client.Document;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.IFrameElement;
import com.google.gwt.dom.client.Style;
import com.google.gwt.dom.client.Style.BorderStyle;
import com.google.gwt.dom.client.Style.Position;
import com.google.gwt.dom.client.Style.Unit;
import com.google.gwt.event.logical.shared.CloseEvent;
import com.google.gwt.event.logical.shared.CloseHandler;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.ui.PopupPanel;
import com.google.gwt.user.client.ui.RootPanel;
import com.google.gwt.user.client.ui.Widget;
import com.vaadin.client.ApplicationConnection;
import com.vaadin.client.BrowserInfo;
import com.vaadin.client.ComponentConnector;
import com.vaadin.client.Util;
import com.vaadin.client.VConsole;

/**
 * In Vaadin UI this Overlay should always be used for all elements that
 * temporary float over other components like context menus etc. This is to deal
 * stacking order correctly with VWindow objects.
 */
public class VOverlay extends PopupPanel implements CloseHandler<PopupPanel> {

    public static class PositionAndSize {
        private int left, top, width, height;

        public PositionAndSize(int left, int top, int width, int height) {
            super();
            setLeft(left);
            setTop(top);
            setWidth(width);
            setHeight(height);
        }

        public int getLeft() {
            return left;
        }

        public void setLeft(int left) {
            this.left = left;
        }

        public int getTop() {
            return top;
        }

        public void setTop(int top) {
            this.top = top;
        }

        public int getWidth() {
            return width;
        }

        public void setWidth(int width) {
            if (width < 0) {
                width = 0;
            }

            this.width = width;
        }

        public int getHeight() {
            return height;
        }

        public void setHeight(int height) {
            if (height < 0) {
                height = 0;
            }

            this.height = height;
        }

        public void setAnimationFromCenterProgress(double progress) {
            left += (int) (width * (1.0 - progress) / 2.0);
            top += (int) (height * (1.0 - progress) / 2.0);
            width = (int) (width * progress);
            height = (int) (height * progress);
        }
    }

    /*
     * The z-index value from where all overlays live. This can be overridden in
     * any extending class.
     */
    public static int Z_INDEX = 20000;

    private static int leftFix = -1;

    private static int topFix = -1;

    /**
     * Shadow element style. If an extending class wishes to use a different
     * style of shadow, it can use setShadowStyle(String) to give the shadow
     * element a new style name.
     */
    public static final String CLASSNAME_SHADOW = "v-shadow";

    /**
     * Style name for the overlay container element (see
     * {@link #getOverlayContainer()}
     */
    public static final String CLASSNAME_CONTAINER = "v-overlay-container";

    /*
     * The shadow element for this overlay.
     */
    private Element shadow;

    /*
     * The creator of this VOverlay (the widget that made the instance, not the
     * layout parent)
     */
    private Widget owner;

    /*
     * ApplicationConnection that this overlay belongs to, which is needed to
     * create the overlay in the correct container so that the correct styles
     * are applied. If not given, owner will be used to figure out, and as a
     * last fallback, the overlay is created w/o container, potentially missing
     * styles.
     */
    protected ApplicationConnection ac;

    /**
     * The shim iframe behind the overlay, allowing PDFs and applets to be
     * covered by overlays.
     */
    private IFrameElement shimElement;

    /**
     * The HTML snippet that is used to render the actual shadow. In consists of
     * nine different DIV-elements with the following class names:
     * 
     * <pre>
     *   .v-shadow[-stylename]
     *   ----------------------------------------------
     *   | .top-left     |   .top    |     .top-right |
     *   |---------------|-----------|----------------|
     *   |               |           |                |
     *   | .left         |  .center  |         .right |
     *   |               |           |                |
     *   |---------------|-----------|----------------|
     *   | .bottom-left  |  .bottom  |  .bottom-right |
     *   ----------------------------------------------
     * </pre>
     * 
     * See default theme 'shadow.css' for implementation example.
     */
    private static final String SHADOW_HTML = "<div aria-hidden=\"true\" class=\"top-left\"></div><div class=\"top\"></div><div class=\"top-right\"></div><div class=\"left\"></div><div class=\"center\"></div><div class=\"right\"></div><div class=\"bottom-left\"></div><div class=\"bottom\"></div><div class=\"bottom-right\"></div>";

    /**
     * Matches {@link PopupPanel}.ANIMATION_DURATION
     */
    private static final int POPUP_PANEL_ANIMATION_DURATION = 200;

    private boolean sinkShadowEvents = false;

    public VOverlay() {
        super();
        adjustZIndex();
    }

    public VOverlay(boolean autoHide) {
        super(autoHide);
        adjustZIndex();
    }

    public VOverlay(boolean autoHide, boolean modal) {
        super(autoHide, modal);
        adjustZIndex();
    }

    public VOverlay(boolean autoHide, boolean modal, boolean showShadow) {
        super(autoHide, modal);
        setShadowEnabled(showShadow);
        adjustZIndex();
    }

    /**
     * Method to controle whether DOM elements for shadow are added. With this
     * method subclasses can control displaying of shadow also after the
     * constructor.
     * 
     * @param enabled
     *            true if shadow should be displayed
     */
    protected void setShadowEnabled(boolean enabled) {
        if (enabled != isShadowEnabled()) {
            if (enabled) {
                shadow = DOM.createDiv();
                shadow.setClassName(CLASSNAME_SHADOW);
                shadow.setInnerHTML(SHADOW_HTML);
                DOM.setStyleAttribute(shadow, "position", "absolute");
                addCloseHandler(this);
            } else {
                removeShadowIfPresent();
                shadow = null;
            }
        }
    }

    protected boolean isShadowEnabled() {
        return shadow != null;
    }

    protected boolean isShimElementEnabled() {
        return shimElement != null;
    }

    private void removeShimElement() {
        if (shimElement != null) {
            shimElement.removeFromParent();
        }
    }

    private void removeShadowIfPresent() {
        if (isShadowAttached()) {
            // Remove event listener from the shadow
            unsinkShadowEvents();

            shadow.removeFromParent();
        }
    }

    private boolean isShadowAttached() {
        return isShadowEnabled() && shadow.getParentElement() != null;
    }

    private void adjustZIndex() {
        setZIndex(Z_INDEX);
    }

    /**
     * Set the z-index (visual stack position) for this overlay.
     * 
     * @param zIndex
     *            The new z-index
     */
    protected void setZIndex(int zIndex) {
        DOM.setStyleAttribute(getElement(), "zIndex", "" + zIndex);
        if (isShadowEnabled()) {
            DOM.setStyleAttribute(shadow, "zIndex", "" + zIndex);
        }
    }

    @Override
    public void setPopupPosition(int left, int top) {
        // TODO, this should in fact be part of
        // Document.get().getBodyOffsetLeft/Top(). Would require overriding DOM
        // for all permutations. Now adding fix as margin instead of fixing
        // left/top because parent class saves the position.
        Style style = getElement().getStyle();
        style.setMarginLeft(-adjustByRelativeLeftBodyMargin(), Unit.PX);
        style.setMarginTop(-adjustByRelativeTopBodyMargin(), Unit.PX);
        super.setPopupPosition(left, top);
        positionOrSizeUpdated(isAnimationEnabled() ? 0 : 1);
    }

    private IFrameElement getShimElement() {
        if (shimElement == null && needsShimElement()) {
            shimElement = Document.get().createIFrameElement();

            // Insert shim iframe before the main overlay element. It does not
            // matter if it is in front or behind the shadow as we cannot put a
            // shim behind the shadow due to its transparency.
            shimElement.getStyle().setPosition(Position.ABSOLUTE);
            shimElement.getStyle().setBorderStyle(BorderStyle.NONE);
            shimElement.setTabIndex(-1);
            shimElement.setFrameBorder(0);
            shimElement.setMarginHeight(0);
        }
        return shimElement;
    }

    private int getActualTop() {
        int y = getAbsoluteTop();

        /* This is needed for IE7 at least */
        // Account for the difference between absolute position and the
        // body's positioning context.
        y -= Document.get().getBodyOffsetTop();
        y -= adjustByRelativeTopBodyMargin();

        return y;
    }

    private int getActualLeft() {
        int x = getAbsoluteLeft();

        /* This is needed for IE7 at least */
        // Account for the difference between absolute position and the
        // body's positioning context.
        x -= Document.get().getBodyOffsetLeft();
        x -= adjustByRelativeLeftBodyMargin();

        return x;
    }

    private static int adjustByRelativeTopBodyMargin() {
        if (topFix == -1) {
            topFix = detectRelativeBodyFixes("top");
        }
        return topFix;
    }

    private native static int detectRelativeBodyFixes(String axis)
    /*-{
        try {
            var b = $wnd.document.body;
            var cstyle = b.currentStyle ? b.currentStyle : getComputedStyle(b);
            if(cstyle && cstyle.position == 'relative') {
                return b.getBoundingClientRect()[axis];
            }
        } catch(e){}
        return 0;
    }-*/;

    private static int adjustByRelativeLeftBodyMargin() {
        if (leftFix == -1) {
            leftFix = detectRelativeBodyFixes("left");

        }
        return leftFix;
    }

    /*
     * A "thread local" of sorts, set temporarily so that VOverlayImpl knows
     * which VOverlay is using it, so that it can be attached to the correct
     * overlay container.
     * 
     * TODO this is a strange pattern that we should get rid of when possible.
     */
    protected static VOverlay current;

    @Override
    public void show() {
        current = this;
        super.show();
        if (isAnimationEnabled()) {
            new ResizeAnimation().run(POPUP_PANEL_ANIMATION_DURATION);
        } else {
            positionOrSizeUpdated(1.0);
        }
        current = null;
    }

    @Override
    protected void onDetach() {
        super.onDetach();

        // Always ensure shadow is removed when the overlay is removed.
        removeShadowIfPresent();
        removeShimElement();
    }

    @Override
    public void setVisible(boolean visible) {
        super.setVisible(visible);
        if (isShadowEnabled()) {
            shadow.getStyle().setProperty("visibility",
                    visible ? "visible" : "hidden");
        }
        if (isShimElementEnabled()) {
            shimElement.getStyle().setProperty("visibility",
                    visible ? "visible" : "hidden");
        }
    }

    @Override
    public void setWidth(String width) {
        super.setWidth(width);
        positionOrSizeUpdated(1.0);
    }

    @Override
    public void setHeight(String height) {
        super.setHeight(height);
        positionOrSizeUpdated(1.0);
    }

    /**
     * Sets the shadow style for this overlay. Will override any previous style
     * for the shadow. The default style name is defined by CLASSNAME_SHADOW.
     * The given style will be prefixed with CLASSNAME_SHADOW.
     * 
     * @param style
     *            The new style name for the shadow element. Will be prefixed by
     *            CLASSNAME_SHADOW, e.g. style=='foobar' -> actual style
     *            name=='v-shadow-foobar'.
     */
    protected void setShadowStyle(String style) {
        if (isShadowEnabled()) {
            shadow.setClassName(CLASSNAME_SHADOW + "-" + style);
        }
    }

    /**
     * Extending classes should always call this method after they change the
     * size of overlay without using normal 'setWidth(String)' and
     * 'setHeight(String)' methods (if not calling super.setWidth/Height).
     * 
     */
    public void positionOrSizeUpdated() {
        positionOrSizeUpdated(1.0);
    }

    /**
     * @deprecated Call {@link #positionOrSizeUpdated()} instead.
     */
    @Deprecated
    protected void updateShadowSizeAndPosition() {
        positionOrSizeUpdated();
    }

    /**
     * Recalculates proper position and dimensions for the shadow and shim
     * elements. Can be used to animate the related elements, using the
     * 'progress' parameter (used to animate the shadow in sync with GWT
     * PopupPanel's default animation 'PopupPanel.AnimationType.CENTER').
     * 
     * @param progress
     *            A value between 0.0 and 1.0, indicating the progress of the
     *            animation (0=start, 1=end).
     */
    private void positionOrSizeUpdated(final double progress) {
        // Don't do anything if overlay element is not attached
        if (!isAttached()) {
            return;
        }
        // Calculate proper z-index
        String zIndex = null;
        try {
            // Odd behaviour with Windows Hosted Mode forces us to use
            // this redundant try/catch block (See dev.vaadin.com #2011)
            zIndex = DOM.getStyleAttribute(getElement(), "zIndex");
        } catch (Exception ignore) {
            // Ignored, will cause no harm
            zIndex = "1000";
        }
        if (zIndex == null) {
            zIndex = "" + Z_INDEX;
        }
        // Calculate position and size
        if (BrowserInfo.get().isIE()) {
            // Shake IE
            getOffsetHeight();
            getOffsetWidth();
        }

        if (isShadowEnabled() || needsShimElement()) {

            PositionAndSize positionAndSize = new PositionAndSize(
                    getActualLeft(), getActualTop(), getOffsetWidth(),
                    getOffsetHeight());

            // Animate the size
            positionAndSize.setAnimationFromCenterProgress(progress);

            Element container = getElement().getParentElement();

            if (isShadowEnabled()) {
                updateShadowPosition(progress, zIndex, positionAndSize);
                if (shadow.getParentElement() == null) {
                    container.insertBefore(shadow, getElement());
                    sinkShadowEvents();
                }
            }

            if (needsShimElement()) {
                updateShimPosition(positionAndSize);
                if (shimElement.getParentElement() == null) {
                    container.insertBefore(shimElement, getElement());
                }
            }
        }
    }

    private void updateShadowPosition(final double progress, String zIndex,
            PositionAndSize positionAndSize) {
        // Opera needs some shaking to get parts of the shadow showing
        // properly (ticket #2704)
        if (BrowserInfo.get().isOpera()) {
            // Clear the height of all middle elements
            DOM.getChild(shadow, 3).getStyle().setProperty("height", "auto");
            DOM.getChild(shadow, 4).getStyle().setProperty("height", "auto");
            DOM.getChild(shadow, 5).getStyle().setProperty("height", "auto");
        }

        updatePositionAndSize(shadow, positionAndSize);
        DOM.setStyleAttribute(shadow, "zIndex", zIndex);
        DOM.setStyleAttribute(shadow, "display", progress < 0.9 ? "none" : "");

        // Opera fix, part 2 (ticket #2704)
        if (BrowserInfo.get().isOpera()) {
            // We'll fix the height of all the middle elements
            DOM.getChild(shadow, 3)
                    .getStyle()
                    .setPropertyPx("height",
                            DOM.getChild(shadow, 3).getOffsetHeight());
            DOM.getChild(shadow, 4)
                    .getStyle()
                    .setPropertyPx("height",
                            DOM.getChild(shadow, 4).getOffsetHeight());
            DOM.getChild(shadow, 5)
                    .getStyle()
                    .setPropertyPx("height",
                            DOM.getChild(shadow, 5).getOffsetHeight());
        }
    }

    private void updateShimPosition(PositionAndSize positionAndSize) {
        updatePositionAndSize(getShimElement(), positionAndSize);
    }

    /**
     * Returns true if we should add a shim iframe below the overlay to deal
     * with zindex issues with PDFs and applets. Can be overriden to disable
     * shim iframes if they are not needed.
     * 
     * @return true if a shim iframe should be added, false otherwise
     */
    protected boolean needsShimElement() {
        BrowserInfo info = BrowserInfo.get();
        return info.isIE() && info.isBrowserVersionNewerOrEqual(8, 0);
    }

    private void updatePositionAndSize(Element e,
            PositionAndSize positionAndSize) {
        e.getStyle().setLeft(positionAndSize.getLeft(), Unit.PX);
        e.getStyle().setTop(positionAndSize.getTop(), Unit.PX);
        e.getStyle().setWidth(positionAndSize.getWidth(), Unit.PX);
        e.getStyle().setHeight(positionAndSize.getHeight(), Unit.PX);
    }

    protected class ResizeAnimation extends Animation {
        @Override
        protected void onUpdate(double progress) {
            positionOrSizeUpdated(progress);
        }
    }

    @Override
    public void onClose(CloseEvent<PopupPanel> event) {
        removeShadowIfPresent();
    }

    @Override
    public void sinkEvents(int eventBitsToAdd) {
        super.sinkEvents(eventBitsToAdd);
        // Also sink events on the shadow if present
        sinkShadowEvents();
    }

    private void sinkShadowEvents() {
        if (isSinkShadowEvents() && isShadowAttached()) {
            // Sink the same events as the actual overlay has sunk
            DOM.sinkEvents(shadow, DOM.getEventsSunk(getElement()));
            // Send events to VOverlay.onBrowserEvent
            DOM.setEventListener(shadow, this);
        }
    }

    private void unsinkShadowEvents() {
        if (isShadowAttached()) {
            DOM.setEventListener(shadow, null);
            DOM.sinkEvents(shadow, 0);
        }
    }

    /**
     * Enables or disables sinking the events of the shadow to the same
     * onBrowserEvent as events to the actual overlay goes.
     * 
     * Please note, that if you enable this, you can't assume that e.g.
     * event.getEventTarget returns an element inside the DOM structure of the
     * overlay
     * 
     * @param sinkShadowEvents
     */
    protected void setSinkShadowEvents(boolean sinkShadowEvents) {
        this.sinkShadowEvents = sinkShadowEvents;
        if (sinkShadowEvents) {
            sinkShadowEvents();
        } else {
            unsinkShadowEvents();
        }
    }

    protected boolean isSinkShadowEvents() {
        return sinkShadowEvents;
    }

    /**
     * Get owner (Widget that made this VOverlay, not the layout parent) of
     * VOverlay
     * 
     * @return Owner (creator) or null if not defined
     */
    public Widget getOwner() {
        return owner;
    }

    /**
     * Set owner (Widget that made this VOverlay, not the layout parent) of
     * VOverlay
     * 
     * @param owner
     *            Owner (creator) of VOverlay
     */
    public void setOwner(Widget owner) {
        this.owner = owner;
    }

    /**
     * Get the {@link ApplicationConnection} that this overlay belongs to. If
     * it's not set, {@link #getOwner()} is used to figure it out.
     * 
     * @return
     */
    protected ApplicationConnection getApplicationConnection() {
        if (ac != null) {
            return ac;
        } else if (owner != null) {
            ComponentConnector c = Util.findConnectorFor(owner);
            if (c != null) {
                ac = c.getConnection();
            }
            return ac;
        } else {
            return null;
        }
    }

    /**
     * Gets the 'overlay container' element. Tries to find the current
     * {@link ApplicationConnection} using {@link #getApplicationConnection()}.
     * 
     * @return the overlay container element for the current
     *         {@link ApplicationConnection} or another element if the current
     *         {@link ApplicationConnection} cannot be determined.
     */
    public com.google.gwt.user.client.Element getOverlayContainer() {
        ApplicationConnection ac = getApplicationConnection();
        if (ac == null) {
            // could not figure out which one we belong to, styling will
            // probably fail
            VConsole.error("Could not determine ApplicationConnection for Overlay. Overlay will be attached directly to the root panel");
            return RootPanel.get().getElement();
        } else {
            return getOverlayContainer(ac);
        }
    }

    /**
     * Gets the 'overlay container' element pertaining to the given
     * {@link ApplicationConnection}. Each overlay should be created in a
     * overlay container element, so that the correct theme and styles can be
     * applied.
     * 
     * @param ac
     *            A reference to {@link ApplicationConnection}
     * @return The overlay container
     */
    public static com.google.gwt.user.client.Element getOverlayContainer(
            ApplicationConnection ac) {
        String id = ac.getConfiguration().getRootPanelId();
        id = id += "-overlays";
        Element container = DOM.getElementById(id);
        if (container == null) {
            container = DOM.createDiv();
            container.setId(id);
            String styles = ac.getUIConnector().getWidget().getParent()
                    .getStyleName();
            container.addClassName(styles);
            container.addClassName(CLASSNAME_CONTAINER);
            RootPanel.get().getElement().appendChild(container);
        }
        return DOM.asOld(container);
    }

    /**
     * Set the label of the container element, where tooltip, notification and
     * dialgs are added to.
     * 
     * @param applicationConnection
     *            the application connection for which to change the label
     * @param overlayContainerLabel
     *            label for the container
     */
    public static void setOverlayContainerLabel(
            ApplicationConnection applicationConnection,
            String overlayContainerLabel) {
        Roles.getAlertRole().setAriaLabelProperty(
                VOverlay.getOverlayContainer(applicationConnection),
                overlayContainerLabel);
    }

    @Override
    public void center() {
        super.center();

        // Some devices can be zoomed in, we should center to the visual
        // viewport for those devices
        BrowserInfo b = BrowserInfo.get();
        if (b.isAndroid() || b.isIOS()) {
            int left = (getVisualViewportWidth() - getOffsetWidth()) >> 1;
            int top = (getVisualViewportHeight() - getOffsetHeight()) >> 1;
            setPopupPosition(Math.max(Window.getScrollLeft() + left, 0),
                    Math.max(Window.getScrollTop() + top, 0));
        }

    }

    /**
     * Gets the visual viewport width, which is useful for e.g iOS where the
     * view can be zoomed in while keeping the layout viewport intact.
     * 
     * Falls back to layout viewport; for those browsers/devices the difference
     * is that the scrollbar with is included (if there is a scrollbar).
     * 
     * @since 7.0.7
     * @return
     */
    private int getVisualViewportWidth() {
        int w = (int) getSubpixelInnerWidth();
        if (w < 0) {
            return Window.getClientWidth();
        } else {
            return w;
        }
    }

    /**
     * Gets the visual viewport height, which is useful for e.g iOS where the
     * view can be zoomed in while keeping the layout viewport intact.
     * 
     * Falls back to layout viewport; for those browsers/devices the difference
     * is that the scrollbar with is included (if there is a scrollbar).
     * 
     * @since 7.0.7
     * @return
     */
    private int getVisualViewportHeight() {
        int h = (int) getSubpixelInnerHeight();
        if (h < 0) {
            return Window.getClientHeight();
        } else {
            return h;
        }
    }

    private native double getSubpixelInnerWidth()
    /*-{
         return $wnd.innerWidth !== undefined ? $wnd.innerWidth : -1;
    }-*/;

    private native double getSubpixelInnerHeight()
    /*-{
         return $wnd.innerHeight !== undefined ? $wnd.innerHeight :-1;
    }-*/;

}
