package mchorse.bbs_mod.ui.film;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.client.FilmEffects;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.input.UITexturePicker;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.UIUtils;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.Direction;

/**
 * A photo laid over the film's preview and export - PNG transparency included. The
 * photo is picked from the mod's textures and placed with sliders (or by dragging
 * right in the preview), and {@link FilmEffects} bakes it into the export texture,
 * so a recorded video carries the overlay exactly as previewed here.
 */
public class UIFilmPhotoOverlayPanel extends UIFilmEffectsOverlayPanel
{
    private static final float SCROLL_SCALE_STEP = 1.1F;

    public UIFilmPhotoOverlayPanel()
    {
        super(UIKeys.FILM_PHOTO_TITLE);

        UIPhotoPreview preview = new UIPhotoPreview();
        UIButton pick = new UIButton(UIKeys.FILM_PHOTO_PICK, (b) -> this.pickTexture());
        UIIcon cover = new UIIcon(Icons.FULLSCREEN, (b) -> this.coverFrame());
        UIIcon reset = new UIIcon(Icons.REFRESH, (b) -> this.reset());

        preview.tooltip(UIKeys.FILM_PHOTO_HINT, Direction.BOTTOM);
        cover.tooltip(UIKeys.FILM_PHOTO_COVER, Direction.LEFT);
        reset.tooltip(UIKeys.FILM_PHOTO_RESET, Direction.LEFT);
        pick.h(20);

        UIElement column = UI.column(4,
            pick,
            this.createRow(UIKeys.FILM_PHOTO_OPACITY, BBSSettings.filmPhotoOpacity, 0D, 100D, 100D),
            this.createRow(UIKeys.FILM_PHOTO_SCALE, BBSSettings.filmPhotoScale, BBSSettings.MIN_FILM_PHOTO_SCALE * 100D, BBSSettings.MAX_FILM_PHOTO_SCALE * 100D, 100D),
            this.createRow(UIKeys.FILM_PHOTO_STRETCH_X, BBSSettings.filmPhotoStretchX, BBSSettings.MIN_FILM_PHOTO_STRETCH * 100D, BBSSettings.MAX_FILM_PHOTO_STRETCH * 100D, 100D),
            this.createRow(UIKeys.FILM_PHOTO_STRETCH_Y, BBSSettings.filmPhotoStretchY, BBSSettings.MIN_FILM_PHOTO_STRETCH * 100D, BBSSettings.MAX_FILM_PHOTO_STRETCH * 100D, 100D),
            this.createRow(UIKeys.GENERAL_X, BBSSettings.filmPhotoX, -BBSSettings.MAX_FILM_PHOTO_OFFSET * 100D, BBSSettings.MAX_FILM_PHOTO_OFFSET * 100D, 100D),
            this.createRow(UIKeys.GENERAL_Y, BBSSettings.filmPhotoY, -BBSSettings.MAX_FILM_PHOTO_OFFSET * 100D, BBSSettings.MAX_FILM_PHOTO_OFFSET * 100D, 100D)
        );

        preview.relative(this.content).xy(PADDING, PADDING).wh(PREVIEW_W, PREVIEW_H);
        column.relative(this.content).x(PREVIEW_W + PADDING * 2).y(PADDING).w(1F, -(PREVIEW_W + PADDING * 3));

        this.icons.add(cover);
        this.icons.add(reset);
        this.content.add(preview, column);
    }

    private void pickTexture()
    {
        UITexturePicker.open(this.getContext(), FilmEffects.getPhotoLink(), (link) ->
        {
            BBSSettings.filmPhotoTexture.set(link == null ? "" : link.toString());
        });
    }

    /** Stretch the photo so it covers the frame exactly, edge to edge. */
    private void coverFrame()
    {
        Texture photo = FilmEffects.getPhotoTexture();

        if (photo == null || photo.width <= 0 || photo.height <= 0)
        {
            return;
        }

        float frameAspect = BBSRendering.getVideoWidth() / (float) BBSRendering.getVideoHeight();
        float photoAspect = photo.width / (float) photo.height;

        BBSSettings.filmPhotoScale.set(1F);
        BBSSettings.filmPhotoStretchX.set(frameAspect / photoAspect);
        BBSSettings.filmPhotoStretchY.set(1F);
        BBSSettings.filmPhotoX.set(0F);
        BBSSettings.filmPhotoY.set(0F);

        this.updateFields();
        UIUtils.playClick();
    }

    private void reset()
    {
        BBSSettings.filmPhotoTexture.set("");
        BBSSettings.filmPhotoOpacity.set(1F);
        BBSSettings.filmPhotoX.set(0F);
        BBSSettings.filmPhotoY.set(0F);
        BBSSettings.filmPhotoScale.set(1F);
        BBSSettings.filmPhotoStretchX.set(1F);
        BBSSettings.filmPhotoStretchY.set(1F);

        this.updateFields();
        UIUtils.playClick();
    }

    /**
     * The shared export preview plus direct manipulation: dragging carries the photo
     * with the cursor, and the mouse wheel scales it around its center.
     */
    private class UIPhotoPreview extends UIExportPreview
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
                float factor = context.mouseWheel > 0D ? SCROLL_SCALE_STEP : 1F / SCROLL_SCALE_STEP;

                BBSSettings.filmPhotoScale.set(BBSSettings.filmPhotoScale.get() * factor);
                UIFilmPhotoOverlayPanel.this.updateFields();

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
            if (this.dragging)
            {
                this.drag(context);
            }

            super.render(context);
        }

        /** Carries the photo with the cursor, mapped through the letterboxed frame. */
        private void drag(UIContext context)
        {
            int dx = context.mouseX - this.lastX;
            int dy = context.mouseY - this.lastY;

            this.lastX = context.mouseX;
            this.lastY = context.mouseY;

            Texture texture = BBSRendering.getTexture();

            if ((dx == 0 && dy == 0) || texture.width <= 0 || texture.height <= 0)
            {
                return;
            }

            /* The photo's position is in NDC units, so a full sweep across the frame
             * as it's shown in the preview is 2 units on either axis */
            float scale = Math.min(this.area.w / (float) texture.width, this.area.h / (float) texture.height);
            float shownW = texture.width * scale;
            float shownH = texture.height * scale;

            BBSSettings.filmPhotoX.set(BBSSettings.filmPhotoX.get() + dx / shownW * 2F);
            BBSSettings.filmPhotoY.set(BBSSettings.filmPhotoY.get() + dy / shownH * 2F);
            UIFilmPhotoOverlayPanel.this.updateFields();
        }
    }
}
