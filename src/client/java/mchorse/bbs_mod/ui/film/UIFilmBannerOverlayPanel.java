package mchorse.bbs_mod.ui.film;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.dashboard.panels.UISelectionScreen;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.input.UITexturePicker;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlayPanel;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.UIUtils;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.Direction;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.colors.Colors;

/**
 * Overlay for customizing the films dashboard's banner photo. The user picks any texture
 * the mod can see, then fits it against the banner's exact proportions: dragging the
 * preview pans the photo, the mouse wheel or the zoom field scales it, and the X/Y fields
 * place it down to a percent. The banner behind the overlay follows every change live.
 */
public class UIFilmBannerOverlayPanel extends UIOverlayPanel
{
    public static final int WIDTH = 400;
    public static final int HEIGHT = 212;

    private static final int PADDING = 6;
    private static final int PREVIEW_H = 131;
    private static final float SCROLL_ZOOM_STEP = 1.1F;

    public UITrackpad x;
    public UITrackpad y;
    public UITrackpad zoom;
    public UIButton pick;
    public UIIcon reset;

    private UIBannerPreview preview;

    /** The user's banner photo, or {@code null} when the stock art is in use. */
    public static Link getCustomBanner()
    {
        String texture = BBSSettings.filmsBannerTexture.get();

        return texture == null || texture.isEmpty() ? null : Link.create(texture);
    }

    public UIFilmBannerOverlayPanel()
    {
        super(UIKeys.FILM_BANNER_TITLE);

        this.preview = new UIBannerPreview();
        this.preview.tooltip(UIKeys.FILM_BANNER_HINT, Direction.BOTTOM);
        this.x = new UITrackpad((v) -> BBSSettings.filmsBannerX.set(v.floatValue() / 100F));
        this.x.limit(0D, 100D).forcedLabel(UIKeys.GENERAL_X);
        this.y = new UITrackpad((v) -> BBSSettings.filmsBannerY.set(v.floatValue() / 100F));
        this.y.limit(0D, 100D).forcedLabel(UIKeys.GENERAL_Y);
        this.zoom = new UITrackpad((v) -> BBSSettings.filmsBannerZoom.set(v.floatValue()));
        this.zoom.limit(BBSSettings.filmsBannerZoom).values(0.05D, 0.01D, 0.25D).forcedLabel(UIKeys.FILM_BANNER_ZOOM);
        this.pick = new UIButton(UIKeys.FILM_BANNER_PICK, (b) -> this.pickTexture());
        this.reset = new UIIcon(Icons.REFRESH, (b) -> this.resetBanner());
        this.reset.tooltip(UIKeys.FILM_BANNER_RESET, Direction.LEFT);

        UIElement fields = UI.row(this.x, this.y, this.zoom);

        this.preview.relative(this.content).xy(PADDING, 0).w(1F, -PADDING * 2).h(PREVIEW_H);
        fields.relative(this.content).x(PADDING).y(PREVIEW_H + PADDING).w(1F, -PADDING * 2).h(20);
        this.pick.relative(this.content).x(PADDING).y(PREVIEW_H + PADDING * 2 + 20).w(1F, -PADDING * 2).h(20);

        this.icons.add(this.reset);
        this.content.add(this.preview, fields, this.pick);
        this.updateFields();
    }

    private void pickTexture()
    {
        UITexturePicker.open(this.getContext(), getCustomBanner(), (link) ->
        {
            BBSSettings.filmsBannerTexture.set(link == null ? "" : link.toString());
        });
    }

    private void resetBanner()
    {
        BBSSettings.filmsBannerTexture.set("");
        BBSSettings.filmsBannerX.set(BBSSettings.DEFAULT_FILMS_BANNER_FOCUS);
        BBSSettings.filmsBannerY.set(BBSSettings.DEFAULT_FILMS_BANNER_FOCUS);
        BBSSettings.filmsBannerZoom.set(BBSSettings.MIN_FILMS_BANNER_ZOOM);

        this.updateFields();
        UIUtils.playClick();
    }

    private void updateFields()
    {
        this.x.setValue(BBSSettings.filmsBannerX.get() * 100D);
        this.y.setValue(BBSSettings.filmsBannerY.get() * 100D);
        this.zoom.setValue(BBSSettings.filmsBannerZoom.get());
    }

    /** The texture currently on the banner - the user's photo, or the stock art as the fallback. */
    private Texture getBannerTexture()
    {
        Link link = getCustomBanner();

        return BBSModClient.getTextures().getTexture(link == null ? UISelectionScreen.getStockBanner() : link);
    }

    /**
     * A live preview with the banner's exact proportions: dragging inside pans the
     * photo pixel for pixel, and scrolling zooms it around its current placement.
     */
    private class UIBannerPreview extends UIElement
    {
        private boolean dragging;
        private int lastX;
        private int lastY;

        @Override
        protected boolean subMouseClicked(UIContext context)
        {
            if (context.mouseButton == 0 && this.area.isInside(context))
            {
                this.dragging = true;
                this.lastX = context.mouseX;
                this.lastY = context.mouseY;

                return true;
            }

            return super.subMouseClicked(context);
        }

        @Override
        protected boolean subMouseScrolled(UIContext context)
        {
            if (this.area.isInside(context) && context.mouseWheel != 0D)
            {
                float factor = context.mouseWheel > 0D ? SCROLL_ZOOM_STEP : 1F / SCROLL_ZOOM_STEP;

                BBSSettings.filmsBannerZoom.set(BBSSettings.filmsBannerZoom.get() * factor);
                UIFilmBannerOverlayPanel.this.updateFields();

                return true;
            }

            return super.subMouseScrolled(context);
        }

        @Override
        protected boolean subMouseReleased(UIContext context)
        {
            this.dragging = false;

            return super.subMouseReleased(context);
        }

        @Override
        public void render(UIContext context)
        {
            Texture texture = UIFilmBannerOverlayPanel.this.getBannerTexture();

            if (this.dragging)
            {
                this.drag(context, texture);
            }

            context.batcher.box(this.area.x, this.area.y, this.area.ex(), this.area.ey(), Colors.A100);

            if (texture != null)
            {
                UISelectionScreen.renderBannerCrop(context.batcher, this.area, texture, BBSSettings.filmsBannerX.get(), BBSSettings.filmsBannerY.get(), BBSSettings.filmsBannerZoom.get());
            }

            context.batcher.outline(this.area.x, this.area.y, this.area.ex(), this.area.ey(), BBSSettings.dividerColor());

            super.render(context);
        }

        /** Pans the crop window so the photo follows the cursor pixel for pixel. */
        private void drag(UIContext context, Texture texture)
        {
            int dx = context.mouseX - this.lastX;
            int dy = context.mouseY - this.lastY;

            this.lastX = context.mouseX;
            this.lastY = context.mouseY;

            if (texture == null || (dx == 0 && dy == 0))
            {
                return;
            }

            /* The pan range in screen pixels is what the scaled image overhangs the area by. */
            float zoom = Math.max(BBSSettings.filmsBannerZoom.get(), 1F);
            float scale = Math.max(this.area.w / (float) texture.width, this.area.h / (float) texture.height) * zoom;
            float slackX = texture.width * scale - this.area.w;
            float slackY = texture.height * scale - this.area.h;

            if (slackX > 0F)
            {
                BBSSettings.filmsBannerX.set(MathUtils.clamp(BBSSettings.filmsBannerX.get() - dx / slackX, 0F, 1F));
            }

            if (slackY > 0F)
            {
                BBSSettings.filmsBannerY.set(MathUtils.clamp(BBSSettings.filmsBannerY.get() - dy / slackY, 0F, 1F));
            }

            UIFilmBannerOverlayPanel.this.updateFields();
        }
    }
}
