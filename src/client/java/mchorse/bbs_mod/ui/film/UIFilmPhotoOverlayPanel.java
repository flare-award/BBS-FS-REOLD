package mchorse.bbs_mod.ui.film;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.client.FilmEffects;
import mchorse.bbs_mod.client.PhotoLayer;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.UIScrollView;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.input.UITexturePicker;
import mchorse.bbs_mod.ui.framework.elements.input.list.UIStringList;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.UIUtils;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.Direction;
import mchorse.bbs_mod.utils.MathUtils;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * Photo layers laid over the film's preview and export - PNG transparency included.
 * Any number of photos can be stacked, each with its own placement, rotation and
 * fade in/out timing over the film's playback. The selected layer is edited with
 * the sliders (or by dragging right in the preview), and {@link FilmEffects} bakes
 * the whole stack into the export texture, so a recorded video carries the overlay
 * exactly as previewed here.
 */
public class UIFilmPhotoOverlayPanel extends UIFilmEffectsOverlayPanel
{
    private static final float SCROLL_SCALE_STEP = 1.1F;
    private static final int LIST_H = 48;
    private static final int ACTIONS_H = 20;

    /** The live layer stack shared with {@link FilmEffects} */
    private final List<PhotoLayer> layers;
    private final UIStringList layerList;

    public UIFilmPhotoOverlayPanel()
    {
        super(UIKeys.FILM_PHOTO_TITLE);

        this.layers = FilmEffects.getPhotoLayers();

        UIPhotoPreview preview = new UIPhotoPreview();

        this.layerList = new UIStringList((list) -> this.updateFields());
        this.layerList.background();

        UIIcon add = new UIIcon(Icons.ADD, (b) -> this.addLayer());
        UIIcon dupe = new UIIcon(Icons.DUPE, (b) -> this.dupeLayer());
        UIIcon remove = new UIIcon(Icons.REMOVE, (b) -> this.removeLayer());
        UIButton pick = new UIButton(UIKeys.FILM_PHOTO_PICK, (b) -> this.pickTexture());
        UIIcon cover = new UIIcon(Icons.FULLSCREEN, (b) -> this.coverFrame());
        UIIcon reset = new UIIcon(Icons.REFRESH, (b) -> this.reset());

        preview.tooltip(UIKeys.FILM_PHOTO_HINT, Direction.BOTTOM);
        add.tooltip(UIKeys.FILM_PHOTO_LAYER_ADD, Direction.BOTTOM);
        dupe.tooltip(UIKeys.FILM_PHOTO_LAYER_DUPE, Direction.BOTTOM);
        remove.tooltip(UIKeys.FILM_PHOTO_LAYER_REMOVE, Direction.BOTTOM);
        cover.tooltip(UIKeys.FILM_PHOTO_COVER, Direction.LEFT);
        reset.tooltip(UIKeys.FILM_PHOTO_RESET, Direction.LEFT);
        pick.h(20);

        UIElement actions = UI.row(0, add, dupe, remove);

        UIScrollView column = UI.scrollView(4, PADDING,
            pick,
            this.createLayerRow(UIKeys.FILM_PHOTO_OPACITY, (layer) -> layer.opacity, (layer, v) -> layer.opacity = v, 0D, 100D, 100D),
            this.createLayerRow(UIKeys.FILM_PHOTO_SCALE, (layer) -> layer.scale, (layer, v) -> layer.scale = v, BBSSettings.MIN_FILM_PHOTO_SCALE * 100D, BBSSettings.MAX_FILM_PHOTO_SCALE * 100D, 100D),
            this.createLayerRow(UIKeys.FILM_PHOTO_STRETCH_X, (layer) -> layer.stretchX, (layer, v) -> layer.stretchX = v, BBSSettings.MIN_FILM_PHOTO_STRETCH * 100D, BBSSettings.MAX_FILM_PHOTO_STRETCH * 100D, 100D),
            this.createLayerRow(UIKeys.FILM_PHOTO_STRETCH_Y, (layer) -> layer.stretchY, (layer, v) -> layer.stretchY = v, BBSSettings.MIN_FILM_PHOTO_STRETCH * 100D, BBSSettings.MAX_FILM_PHOTO_STRETCH * 100D, 100D),
            this.createLayerRow(UIKeys.GENERAL_X, (layer) -> layer.x, (layer, v) -> layer.x = v, -BBSSettings.MAX_FILM_PHOTO_OFFSET * 100D, BBSSettings.MAX_FILM_PHOTO_OFFSET * 100D, 100D),
            this.createLayerRow(UIKeys.GENERAL_Y, (layer) -> layer.y, (layer, v) -> layer.y = v, -BBSSettings.MAX_FILM_PHOTO_OFFSET * 100D, BBSSettings.MAX_FILM_PHOTO_OFFSET * 100D, 100D),
            this.createLayerRow(UIKeys.FILM_PHOTO_ROTATE, (layer) -> layer.rotate, (layer, v) -> layer.rotate = v, -180D, 180D, 1D),
            this.createLayerRow(UIKeys.FILM_PHOTO_FADE_IN, (layer) -> layer.fadeIn, (layer, v) -> layer.fadeIn = v, 0D, BBSSettings.MAX_FILM_PHOTO_FADE, 1D),
            this.createLayerRow(UIKeys.FILM_PHOTO_FADE_OUT, (layer) -> layer.fadeOut, (layer, v) -> layer.fadeOut = v, 0D, BBSSettings.MAX_FILM_PHOTO_FADE, 1D)
        );

        preview.relative(this.content).xy(PADDING, PADDING).wh(PREVIEW_W, PREVIEW_H);
        this.layerList.relative(this.content).x(PREVIEW_W + PADDING * 2).y(PADDING).w(1F, -(PREVIEW_W + PADDING * 3)).h(LIST_H);
        actions.relative(this.content).x(PREVIEW_W + PADDING * 2).y(PADDING + LIST_H + 2).w(1F, -(PREVIEW_W + PADDING * 3)).h(ACTIONS_H);
        column.relative(this.content).x(PREVIEW_W + PADDING).y(PADDING + LIST_H + ACTIONS_H + 2).w(1F, -(PREVIEW_W + PADDING)).h(1F, -(PADDING + LIST_H + ACTIONS_H + 2));

        this.icons.add(cover);
        this.icons.add(reset);
        this.content.add(preview, this.layerList, actions, column);

        this.fillList();
    }

    /** A slider row that edits a field of whichever layer is selected. */
    private UIElement createLayerRow(IKey label, Function<PhotoLayer, Float> getter, BiConsumer<PhotoLayer, Float> setter, double min, double max, double uiScale)
    {
        return this.createRow(label, () ->
        {
            PhotoLayer layer = this.getLayer();

            return layer == null ? 0D : getter.apply(layer);
        }, (v) ->
        {
            PhotoLayer layer = this.getLayer();

            if (layer != null)
            {
                setter.accept(layer, (float) v);
                this.save();
            }
        }, min, max, uiScale, false);
    }

    private PhotoLayer getLayer()
    {
        int index = this.layerList.getIndex();

        return index >= 0 && index < this.layers.size() ? this.layers.get(index) : null;
    }

    private void save()
    {
        FilmEffects.savePhotoLayers(this.layers);
    }

    /** Rebuild the layer list's labels, keeping the selection in place when possible. */
    private void fillList()
    {
        int index = this.layerList.getIndex();

        this.layerList.clear();

        for (int i = 0; i < this.layers.size(); i++)
        {
            this.layerList.add((i + 1) + ": " + this.getLayerName(this.layers.get(i)));
        }

        if (!this.layers.isEmpty())
        {
            this.layerList.setIndex(MathUtils.clamp(index, 0, this.layers.size() - 1));
        }

        this.updateFields();
    }

    private String getLayerName(PhotoLayer layer)
    {
        if (layer.texture.isEmpty())
        {
            return UIKeys.FILM_PHOTO_NO_TEXTURE.get();
        }

        return layer.texture.substring(layer.texture.lastIndexOf('/') + 1);
    }

    private void addLayer()
    {
        this.layers.add(new PhotoLayer());
        this.save();
        this.fillList();
        this.layerList.setIndex(this.layers.size() - 1);
        this.updateFields();
        this.pickTexture();
    }

    private void dupeLayer()
    {
        PhotoLayer layer = this.getLayer();

        if (layer == null)
        {
            return;
        }

        this.layers.add(this.layerList.getIndex() + 1, layer.copy());
        this.save();
        this.fillList();
        this.layerList.setIndex(this.layerList.getIndex() + 1);
        this.updateFields();
        UIUtils.playClick();
    }

    private void removeLayer()
    {
        PhotoLayer layer = this.getLayer();

        if (layer == null)
        {
            return;
        }

        this.layers.remove(layer);
        this.save();
        this.fillList();
        UIUtils.playClick();
    }

    private void pickTexture()
    {
        PhotoLayer layer = this.getLayer();

        if (layer == null)
        {
            return;
        }

        Link link = layer.texture.isEmpty() ? null : Link.create(layer.texture);

        UITexturePicker.open(this.getContext(), link, (picked) ->
        {
            PhotoLayer current = this.getLayer();

            if (current != null)
            {
                current.texture = picked == null ? "" : picked.toString();

                this.save();
                this.fillList();
            }
        });
    }

    /** Stretch the selected layer so it covers the frame exactly, edge to edge. */
    private void coverFrame()
    {
        PhotoLayer layer = this.getLayer();
        Texture photo = layer == null ? null : FilmEffects.getPhotoTexture(layer);

        if (photo == null || photo.width <= 0 || photo.height <= 0)
        {
            return;
        }

        float frameAspect = BBSRendering.getVideoWidth() / (float) BBSRendering.getVideoHeight();
        float photoAspect = photo.width / (float) photo.height;

        layer.scale = 1F;
        layer.stretchX = frameAspect / photoAspect;
        layer.stretchY = 1F;
        layer.x = 0F;
        layer.y = 0F;
        layer.rotate = 0F;

        this.save();
        this.updateFields();
        UIUtils.playClick();
    }

    private void reset()
    {
        this.layers.clear();
        this.save();
        this.fillList();
        UIUtils.playClick();
    }

    /**
     * The shared export preview plus direct manipulation: dragging carries the
     * selected layer with the cursor, and the mouse wheel scales it around its center.
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
            PhotoLayer layer = UIFilmPhotoOverlayPanel.this.getLayer();

            if (layer != null && this.area.isInside(context) && context.mouseWheel != 0D)
            {
                float factor = context.mouseWheel > 0D ? SCROLL_SCALE_STEP : 1F / SCROLL_SCALE_STEP;

                layer.scale = MathUtils.clamp(layer.scale * factor, BBSSettings.MIN_FILM_PHOTO_SCALE, BBSSettings.MAX_FILM_PHOTO_SCALE);
                UIFilmPhotoOverlayPanel.this.save();
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

        /** Carries the selected layer with the cursor, mapped through the letterboxed frame. */
        private void drag(UIContext context)
        {
            int dx = context.mouseX - this.lastX;
            int dy = context.mouseY - this.lastY;

            this.lastX = context.mouseX;
            this.lastY = context.mouseY;

            PhotoLayer layer = UIFilmPhotoOverlayPanel.this.getLayer();
            Texture texture = BBSRendering.getTexture();

            if (layer == null || (dx == 0 && dy == 0) || texture.width <= 0 || texture.height <= 0)
            {
                return;
            }

            /* The layer's position is in NDC units, so a full sweep across the frame
             * as it's shown in the preview is 2 units on either axis */
            float scale = Math.min(this.area.w / (float) texture.width, this.area.h / (float) texture.height);
            float shownW = texture.width * scale;
            float shownH = texture.height * scale;

            layer.x = MathUtils.clamp(layer.x + dx / shownW * 2F, -BBSSettings.MAX_FILM_PHOTO_OFFSET, BBSSettings.MAX_FILM_PHOTO_OFFSET);
            layer.y = MathUtils.clamp(layer.y + dy / shownH * 2F, -BBSSettings.MAX_FILM_PHOTO_OFFSET, BBSSettings.MAX_FILM_PHOTO_OFFSET);
            UIFilmPhotoOverlayPanel.this.save();
            UIFilmPhotoOverlayPanel.this.updateFields();
        }
    }
}
