package mchorse.bbs_mod.ui.dashboard.panels.landing;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.UIScrollView;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UICirculate;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.UITexturePicker;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.framework.elements.input.text.UITextbox;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlayPanel;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.ui.utils.ScrollDirection;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.UIUtils;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.ui.utils.presets.UICopyPasteController;
import mchorse.bbs_mod.utils.Direction;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.presets.PresetManager;
import mchorse.bbs_mod.utils.resources.GifFrames;

/**
 * The pencil editor for the banners every landing screen shows: the list of what cycles,
 * how each of it is framed, the credit line and the plate under the artwork.
 *
 * <p>Everything here edits the one shared state and saves it as it goes, so the tab the
 * pencil was picked from and the next empty tab of the next editor see the same banner.
 * A picked file may be a picture or a GIF &mdash; the textures manager plays it the
 * same way a static photo is drawn, on a loop.</p>
 */
public class UIBannerEditorPanel extends UIOverlayPanel
{
    public static final int WIDTH = 460;
    public static final int HEIGHT = 440;

    private static final int PADDING = 6;
    private static final int ROW_H = 44;
    private static final int LIST_H = ROW_H * 3;
    private static final int THUMB_W = 64;
    private static final int THUMB_H = 32;
    private static final int PREVIEW_H = 108;
    private static final float SCROLL_ZOOM_STEP = 1.1F;

    private UIScrollView list;
    private UIButton add;
    private UIBannerPreview preview;
    private UITrackpad x;
    private UITrackpad y;
    private UITrackpad zoom;
    private UITextbox creditText;
    private UICirculate creditStyle;
    private UIToggle creditToggle;
    private UIToggle plateToggle;

    private int selected;

    /** Presets of the whole shared state: banners, crops, credit and plate together. */
    private final UICopyPasteController presets = new UICopyPasteController(PresetManager.BANNERS, "_BannerPreset")
        .labels(UIKeys.BANNER_EDITOR_PRESET_COPY, UIKeys.BANNER_EDITOR_PRESET_PASTE)
        .supplier(() -> BannerConfig.get().toData())
        .consumer((data, mouseX, mouseY) ->
        {
            BannerConfig.fromData(data);
            this.selected = 0;
            this.rebuild();
            UIUtils.playClick();
        });

    public UIBannerEditorPanel()
    {
        super(UIKeys.BANNER_EDITOR_TITLE);

        UIIcon presetsButton = new UIIcon(Icons.BUCKET, (b) -> this.presets.openPresets(b.getContext(), b.area.mx(), b.area.ey()));
        presetsButton.tooltip(UIKeys.GENERAL_PRESETS, Direction.LEFT);
        this.icons.add(presetsButton);

        this.list = new UIScrollView(ScrollDirection.VERTICAL);
        this.list.relative(this.content).xy(PADDING, 0).w(1F, -PADDING * 2).h(LIST_H);
        this.list.scroll.scrollSpeed = 51;
        this.list.column(4).vertical().stretch().scroll().padding(0);

        this.add = new UIButton(UIKeys.BANNER_EDITOR_ADD, (b) -> this.addBanner());
        this.add.relative(this.content).x(PADDING).y(LIST_H + PADDING).w(1F, -PADDING * 2).h(20);

        int y = LIST_H + PADDING * 2 + 20;

        this.preview = new UIBannerPreview();
        this.preview.tooltip(UIKeys.BANNER_EDITOR_HINT, Direction.BOTTOM);
        this.preview.relative(this.content).x(PADDING).y(y).w(1F, -PADDING * 2).h(PREVIEW_H);

        y += PREVIEW_H + PADDING;

        this.x = new UITrackpad((v) -> this.setCrop(v.floatValue() / 100F, null, null));
        this.x.limit(0D, 100D, true);
        this.y = new UITrackpad((v) -> this.setCrop(null, v.floatValue() / 100F, null));
        this.y.limit(0D, 100D, true);
        this.zoom = new UITrackpad((v) -> this.setCrop(null, null, v.floatValue()));
        this.zoom.limit(1D, 8D).values(0.05D, 0.01D, 0.25D);

        this.content.add(this.labeledRow(UIKeys.BANNER_EDITOR_X, this.x).y(y));
        y += 20;
        this.content.add(this.labeledRow(UIKeys.BANNER_EDITOR_Y, this.y).y(y));
        y += 20;
        this.content.add(this.labeledRow(UIKeys.BANNER_EDITOR_ZOOM, this.zoom).y(y));
        y += 20;

        y += 20;

        this.creditToggle = new UIToggle(IKey.EMPTY, BannerConfig.get().creditEnabled, (t) ->
        {
            BannerConfig.get().creditEnabled = t.getValue();
            BannerConfig.save();
        });
        this.content.add(this.labeledRow(UIKeys.BANNER_EDITOR_CREDIT, this.creditToggle).y(y));

        y += 20;

        this.creditText = new UITextbox(BannerConfig.MAX_CREDIT_LENGTH, (str) ->
        {
            BannerConfig.get().creditText = str;
            BannerConfig.save();
        });
        this.content.add(this.labeledRow(UIKeys.BANNER_EDITOR_CREDIT_TEXT, this.creditText).y(y));

        y += 20;

        this.creditStyle = new UICirculate((b) ->
        {
            BannerConfig.get().creditStyle = b.getValue();
            BannerConfig.save();
        });
        this.creditStyle.addLabel(UIKeys.BANNER_EDITOR_CREDIT_STYLE_LAST);
        this.creditStyle.addLabel(UIKeys.BANNER_EDITOR_CREDIT_STYLE_FIRST);
        this.creditStyle.addLabel(UIKeys.BANNER_EDITOR_CREDIT_STYLE_ALL);
        this.creditStyle.addLabel(UIKeys.BANNER_EDITOR_CREDIT_STYLE_NONE);
        this.creditStyle.tooltip(UIKeys.BANNER_EDITOR_CREDIT_STYLE);
        this.content.add(this.labeledRow(UIKeys.BANNER_EDITOR_CREDIT_STYLE, this.creditStyle).y(y));

        y += 20;

        this.plateToggle = new UIToggle(IKey.EMPTY, BannerConfig.get().plateEnabled, (t) ->
        {
            BannerConfig.get().plateEnabled = t.getValue();
            BannerConfig.save();
        });
        this.content.add(this.labeledRow(UIKeys.BANNER_EDITOR_PLATE, this.plateToggle).y(y));

        this.content.add(this.list, this.add, this.preview);

        this.rebuild();
    }

    /** The same shape UIValueFactory.column builds for a settings row: a label and a control. */
    private UIElement labeledRow(IKey label, UIElement control)
    {
        UIElement element = new UIElement();

        element.row(0).preferred(0).height(20);
        element.add(UI.label(label, 0).labelAnchor(0, 0.5F), control);
        element.relative(this.content).x(PADDING).w(1F, -PADDING * 2);

        return element;
    }

    /** The banner the crop controls and the preview work on; never out of range. */
    private BannerConfig.Banner selected()
    {
        BannerConfig config = BannerConfig.get();
        this.selected = Math.max(0, Math.min(this.selected, config.banners.size() - 1));

        return config.banners.get(this.selected);
    }

    private void setCrop(Float focusX, Float focusY, Float zoom)
    {
        BannerConfig.Banner banner = this.selected();

        if (focusX != null)
        {
            banner.focusX = MathUtils.clamp(focusX, 0F, 1F);
        }

        if (focusY != null)
        {
            banner.focusY = MathUtils.clamp(focusY, 0F, 1F);
        }

        if (zoom != null)
        {
            banner.zoom = Math.max(zoom, 1F);
        }

        BannerConfig.save();
    }

    /** Rebuild the list rows and resync the fields; every change that reshapes the list ends here. */
    private void rebuild()
    {
        BannerConfig config = BannerConfig.get();
        this.selected = Math.max(0, Math.min(this.selected, config.banners.size() - 1));

        this.list.removeAll();

        for (int i = 0; i < config.banners.size(); i++)
        {
            this.list.add(new BannerRow(i));
        }

        this.creditText.setText(config.creditText);
        this.creditStyle.setValue(config.creditStyle);
        this.updateFields();

        this.list.resize();
    }

    private void updateFields()
    {
        BannerConfig.Banner banner = this.selected();

        this.x.setValue(banner.focusX * 100D);
        this.y.setValue(banner.focusY * 100D);
        this.zoom.setValue(banner.zoom);
    }

    private void addBanner()
    {
        UITexturePicker.open(this.getContext(), null, (link) ->
        {
            if (link == null)
            {
                return;
            }

            BannerConfig config = BannerConfig.get();

            config.banners.add(new BannerConfig.Banner(link));
            this.selected = config.banners.size() - 1;

            BannerConfig.save();
            this.rebuild();
            UIUtils.playClick();
        });
    }

    private void pickBanner(int index)
    {
        BannerConfig.Banner banner = BannerConfig.get().banners.get(index);

        UITexturePicker.open(this.getContext(), banner.link, (link) ->
        {
            if (link == null)
            {
                return;
            }

            /* A new photo keeps the framing the user already set for this slot. */
            banner.link = link;

            BannerConfig.save();
            this.rebuild();
            UIUtils.playClick();
        });
    }

    private void removeBanner(int index)
    {
        BannerConfig config = BannerConfig.get();

        if (config.banners.size() <= 1)
        {
            return;
        }

        config.banners.remove(index);

        if (this.selected >= config.banners.size())
        {
            this.selected = config.banners.size() - 1;
        }

        BannerConfig.save();
        this.rebuild();
        UIUtils.playClick();
    }

    private void resetCrop()
    {
        BannerConfig.Banner banner = this.selected();

        banner.focusX = 0.5F;
        banner.focusY = 0.5F;
        banner.zoom = 1F;

        BannerConfig.save();
        this.updateFields();
        UIUtils.playClick();
    }

    /* A row of the list: the thumbnail, the GIF tag and the two per-banner buttons. */

    private class BannerRow extends UIElement
    {
        private final int index;

        private UIIcon pick;
        private UIIcon remove;

        public BannerRow(int index)
        {
            this.index = index;

            this.h(ROW_H);

            this.pick = new UIIcon(Icons.PICTURE, (b) -> UIBannerEditorPanel.this.pickBanner(this.index));
            this.pick.tooltip(UIKeys.BANNER_EDITOR_PICK, Direction.LEFT);
            this.pick.wh(20, 20);
            this.pick.relative(this).x(1F, -24).y(12);

            this.remove = new UIIcon(Icons.REMOVE, (b) -> UIBannerEditorPanel.this.removeBanner(this.index));
            this.remove.tooltip(UIKeys.BANNER_EDITOR_REMOVE, Direction.LEFT);
            this.remove.wh(20, 20);
            this.remove.relative(this).x(1F, -48).y(12);

            this.add(this.pick, this.remove);
        }

        @Override
        protected boolean subMouseClicked(UIContext context)
        {
            if (context.mouseButton == 0 && this.area.isInside(context))
            {
                /* A click that a button of the row does not take selects the banner. */
                if (!this.pick.area.isInside(context) && !this.remove.area.isInside(context))
                {
                    UIBannerEditorPanel.this.selected = this.index;
                    UIBannerEditorPanel.this.updateFields();
                    UIBannerEditorPanel.this.rebuild();
                    UIUtils.playClick();
                }

                return true;
            }

            return super.subMouseClicked(context);
        }

        @Override
        public void render(UIContext context)
        {
            BannerConfig config = BannerConfig.get();

            if (this.index >= config.banners.size())
            {
                super.render(context);
                return;
            }

            BannerConfig.Banner banner = config.banners.get(this.index);
            boolean isGif = GifFrames.isGif(banner.link);

            if (this.index == UIBannerEditorPanel.this.selected)
            {
                context.batcher.box(this.area.x, this.area.y, this.area.ex(), this.area.ey(), BBSSettings.primaryColor(Colors.A40));
                context.batcher.outline(this.area.x, this.area.y, this.area.ex(), this.area.ey(), BBSSettings.primaryColor(Colors.A80));
            }

            Area thumb = new Area(this.area.x + 2, this.area.my() - THUMB_H / 2, THUMB_W, THUMB_H);

            context.batcher.box(thumb.x, thumb.y, thumb.ex(), thumb.ey(), Colors.BLACK);

            Texture texture = BBSModClient.getTextures().getTexture(banner.link);

            if (texture != null)
            {
                /* The row shows the banner the way the game would show it, framed. */
                UILandingScreen.renderBannerCrop(context.batcher, thumb, texture, banner.focusX, banner.focusY, banner.zoom);
            }

            context.batcher.outline(thumb.x, thumb.y, thumb.ex(), thumb.ey(), BBSSettings.dividerColor());

            if (isGif)
            {
                context.batcher.text("GIF", thumb.ex() - 16, thumb.ey() - 10, Colors.WHITE, true);
            }

            /* The last banner cannot go: the cycle needs one to show. */
            this.remove.setEnabled(config.banners.size() > 1);

            super.render(context);
        }
    }

    /* A live preview of the selected banner with the banner's own proportions. */

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
                BannerConfig.Banner banner = UIBannerEditorPanel.this.selected();
                float factor = context.mouseWheel > 0D ? SCROLL_ZOOM_STEP : 1F / SCROLL_ZOOM_STEP;

                banner.zoom = Math.max(banner.zoom * factor, 1F);
                UIBannerEditorPanel.this.updateFields();
                BannerConfig.save();

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
            BannerConfig.Banner banner = UIBannerEditorPanel.this.selected();
            Texture texture = BBSModClient.getTextures().getTexture(banner.link);

            if (this.dragging)
            {
                this.drag(context, texture);
            }

            context.batcher.box(this.area.x, this.area.y, this.area.ex(), this.area.ey(), Colors.BLACK);

            if (texture != null)
            {
                UILandingScreen.renderBannerCrop(context.batcher, this.area, texture, banner.focusX, banner.focusY, banner.zoom);
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

            BannerConfig.Banner banner = UIBannerEditorPanel.this.selected();

            if (texture == null || (dx == 0 && dy == 0))
            {
                return;
            }

            /* The pan range in screen pixels is what the scaled image overhangs the area by. */
            float zoom = Math.max(banner.zoom, 1F);
            float scale = Math.max(this.area.w / (float) texture.width, this.area.h / (float) texture.height) * zoom;
            float slackX = texture.width * scale - this.area.w;
            float slackY = texture.height * scale - this.area.h;

            if (slackX > 0F)
            {
                banner.focusX = MathUtils.clamp(banner.focusX - dx / slackX, 0F, 1F);
            }

            if (slackY > 0F)
            {
                banner.focusY = MathUtils.clamp(banner.focusY - dy / slackY, 0F, 1F);
            }

            UIBannerEditorPanel.this.updateFields();
            BannerConfig.save();
        }
    }
}
