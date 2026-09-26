package mchorse.bbs_mod.ui.dashboard.panels.landing;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.settings.values.core.ValueRecentData.Entry;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.ui.framework.elements.utils.UILabel;
import mchorse.bbs_mod.ui.framework.elements.utils.UIRenderable;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.UIUtils;
import mchorse.bbs_mod.ui.utils.context.ContextMenuManager;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.Direction;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.colors.Colors;
import net.fabricmc.loader.api.FabricLoader;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * What an empty tab shows: a menu on the left — new, the list, the folder, the community links —
 * and on the right what was opened last, so the way back into yesterday's work is one click.
 *
 * <p>Nothing here changes files. Renaming, removing, folders, duplicates all live in the data
 * manager the list entry leads to; this screen only opens things.</p>
 */
public class UILandingScreen extends UIElement
{
    /** The card, and the banner it carries, in UI pixels — the editor's preview measures itself against these. */
    public static final int CARD_W = 440;
    public static final int CARD_H = 360;

    /** The banner is the top half of the card, exactly. */
    public static final int BANNER_H = CARD_H / 2;
    private static final int BANNER_MARGIN = 12;
    private static final int PADDING = 10;
    private static final int HEADER_H = 16;
    private static final int HEADER_MARGIN = 6;
    private static final int MENU_W = 150;
    private static final int GUTTER = 20;
    private static final int GROUP_GAP = 10;

    private static final int RECENT_X = PADDING + MENU_W + GUTTER;
    private static final int CONTENT_Y = BANNER_H + BANNER_MARGIN;
    private static final int LIST_Y = CONTENT_Y + HEADER_H + HEADER_MARGIN;

    /** Section titles sit back a little; the entries under them are what the eye is for. */
    private static final int DIMMED = Colors.setA(Colors.WHITE, 0.7F);
    private static final int MUTED = Colors.setA(Colors.WHITE, 0.5F);

    private static final double BANNER_HOLD_SECONDS = 6;
    private static final double BANNER_FADE_SECONDS = 2;

    /** One monotonic clock keeps the banner continuous when switching editor panels. */
    private static long bannerStarted;

    /* Where the community lives; the same in every language, so not in the language files */
    public static final String DISCORD_LINK = "https://discord.gg/66mVb7Ezjj";
    public static final String TUTORIALS_LINK = "https://www.youtube.com/watch?v=yY5uE3PVd5Y&list=PLM5Z4FJ0AVdw";
    public static final String WIKI_LINK = "https://github.com/Wemppy4/bbs-fs/wiki";

    private final ILandingHost host;
    private final LandingBackdrop backdrop = new LandingBackdrop();
    private final UIElement card;
    private final UIElement banner;
    private final UILandingRow list;
    private final UIElement menu;
    private final UILandingRow folder;
    private final UIRecentDataList recent;
    private final String bannerVersion = getReleaseVersion();

    /** Ids the repository reported last; null until it answered, when nothing is filtered out. */
    private Set<String> known;

    /** The width of the list column docked to the left edge; 0 while the column is not on screen. */
    private int listInset;

    public UILandingScreen(ILandingHost host)
    {
        this.host = host;

        /* Centered, with the backdrop showing all around it */
        this.card = new UIElement();
        this.card.relative(this).xy(0.5F, 0.5F).wh(CARD_W, CARD_H).anchor(0.5F);

        this.banner = new UIElement();
        this.banner.relative(this.card).xy(0, 0).w(1F).h(BANNER_H);
        this.banner.add(new UIRenderable((context) -> this.renderBanner(context, this.banner.area)));
        this.banner.add(new UIRenderable((context) -> this.renderBannerCaption(context, this.banner.area)));

        /* The pencil: the same in every tab, one shared banner state behind it */
        UIIcon pencil = new UIIcon(Icons.EDIT, (b) -> this.openBannerEditor());
        pencil.tooltip(UIKeys.PANELS_LANDING_BANNER_EDIT, Direction.BOTTOM);
        pencil.wh(20, 20);
        pencil.relative(this.banner).x(1F, -24).y(4);
        this.banner.add(pencil);

        UILabel title = UI.label(host.getTitle()).color(DIMMED);
        title.labelAnchor(0, 0.5F);
        title.relative(this.card).xy(PADDING, CONTENT_Y).w(MENU_W).h(HEADER_H);

        UILabel recentTitle = UI.label(UIKeys.PANELS_LANDING_RECENT).color(DIMMED);
        recentTitle.labelAnchor(0, 0.5F);
        recentTitle.relative(this.card).xy(RECENT_X, CONTENT_Y).w(CARD_W - RECENT_X - PADDING).h(HEADER_H);

        /* The menu: what leads into the editor first, what leads out of it after a gap */
        IKey createLabel = host.getCreateLabel();
        this.list = new UILandingRow(Icons.MORE, host.getListLabel(), (b) -> host.openDataManager());
        UIElement gap = new UIElement();
        UILandingRow discord = new UILandingRow(Icons.DISCORD, IKey.constant("Discord"), (b) -> UIUtils.openWebLink(DISCORD_LINK));
        UILandingRow tutorials = new UILandingRow(Icons.PLAY, UIKeys.SUPPORTERS_TUTORIALS, (b) -> UIUtils.openWebLink(TUTORIALS_LINK));
        UILandingRow wiki = new UILandingRow(Icons.HELP, UIKeys.SUPPORTERS_WIKI, (b) -> UIUtils.openWebLink(WIKI_LINK));

        this.folder = new UILandingRow(Icons.FOLDER, UIKeys.PANELS_CONTEXT_OPEN, (b) -> this.openFolder());

        gap.h(GROUP_GAP);

        List<UIElement> rows = new ArrayList<>();

        /* Panels backed by assets (the model editor) and by files (the audio editor) have nothing
         * to create; there the list is the way in, and it wears the accent instead */
        if (createLabel == null)
        {
            list.accent();
        }
        else
        {
            UILandingRow create = new UILandingRow(Icons.ADD, createLabel, (b) -> host.addNewData(this.getContext()));

            create.accent();
            rows.add(create);
        }

        rows.add(list);
        rows.add(this.folder);
        rows.add(gap);
        rows.add(discord);
        rows.add(tutorials);
        rows.add(wiki);

        this.menu = UI.column(0, rows.toArray(new UIElement[0]));
        this.menu.relative(this.card).xy(PADDING, LIST_Y).w(MENU_W).h(1F, -(LIST_Y + PADDING));

        this.recent = new UIRecentDataList((entries) -> this.open(entries.get(0)), host::getTabIcon);
        this.recent.relative(this.card).xy(RECENT_X, LIST_Y).w(CARD_W - RECENT_X - PADDING).h(1F, -(LIST_Y + PADDING));
        this.recent.context(this::fillRecentMenu);

        this.card.add(new UIRenderable((context) -> this.renderCard(context, this.card.area)));
        this.card.add(this.banner, title, recentTitle, this.menu);
        this.card.add(new UIRenderable(this::renderEmptyHint), this.recent);
        this.add(new UIRenderable(this::renderBackdrop), this.card);

        this.refresh();
    }

    /** The card in the middle — what a tour points at when it points at the landing screen. */
    public UIElement getCard()
    {
        return this.card;
    }

    /** "BBS FS REOLD 2.7.1" — the mod's own version, without the Minecraft version the build appends. */
    public static String getVersion()
    {
        String version = getReleaseVersion();

        return "BBS FS REOLD" + (version.isEmpty() ? "" : " " + version);
    }

    private void openBannerEditor()
    {
        UIContext context = this.getContext();

        if (context != null)
        {
            UIOverlay.addOverlay(context, new UIBannerEditorPanel(), UIBannerEditorPanel.WIDTH, UIBannerEditorPanel.HEIGHT);
        }
    }

    private static String getReleaseVersion()
    {
        return FabricLoader.getInstance().getModContainer(BBSMod.MOD_ID)
            .map((mod) ->
            {
                String version = mod.getMetadata().getVersion().getFriendlyString();
                int dash = version.lastIndexOf('-');

                return dash > 0 ? version.substring(0, dash) : version;
            })
            .orElse("");
    }

    @Override
    public void setVisible(boolean visible)
    {
        boolean wasVisible = this.isVisible();

        super.setVisible(visible);

        if (visible && !wasVisible)
        {
            this.refresh();
            this.host.requestNames();
        }
    }

    /**
     * The width of the list column the panel docks to the left edge, 0 while it is away.
     * The card stays centered as long as it fits; only when the screen is so narrow that the
     * column would cover it does the card step aside, by just the amount that is needed.
     * While the column is on screen the menu gives up its "list" entry &mdash; the column is
     * that list, and the entry would just point at itself.
     */
    public void setListInset(int inset)
    {
        inset = Math.max(inset, 0);

        if (inset == this.listInset)
        {
            return;
        }

        this.listInset = inset;
        this.list.setVisible(inset == 0);

        this.syncCardShift();
        this.menu.resize();
    }

    private void syncCardShift()
    {
        int shift = Math.max(0, this.listInset + PADDING - (this.area.w - CARD_W) / 2);

        if (shift != this.card.getFlex().x.offset)
        {
            this.card.getFlex().x.offset = shift;
            this.resize();
        }
    }

    @Override
    public void render(UIContext context)
    {
        /* The column can appear and disappear under the screen, and the window can resize;
         * re-check the shift where the screen's own width is known for sure. */
        this.syncCardShift();

        super.render(context);
    }

    /** The repository answered: whatever it no longer has drops out of the list. */
    public void fillNames(Collection<String> names)
    {
        this.known = new HashSet<>(names);

        this.refresh();
    }

    /**
     * Rebuild from the registry. The list is drawn from the settings right away, without waiting
     * for the repository — over the network that answer takes a moment, and the screen must not
     * flash empty every time a tab is emptied.
     */
    private void refresh()
    {
        boolean hasFolder = this.host.getDataFolder() != null;

        if (this.folder.isVisible() != hasFolder)
        {
            this.folder.setVisible(hasFolder);
            this.menu.resize();
        }

        List<Entry> entries = new ArrayList<>();

        for (Entry entry : BBSSettings.recentData.get(this.host.getRecentType()))
        {
            if (this.known == null || this.known.contains(entry.id))
            {
                entries.add(entry);
            }
        }

        this.recent.setList(entries);
        this.recent.deselect();
    }

    private void open(Entry entry)
    {
        this.host.pickData(entry.id);
    }

    private void openFolder()
    {
        File folder = this.host.getDataFolder();

        if (folder != null)
        {
            UIUtils.openFolder(folder);
        }
    }

    private void fillRecentMenu(ContextMenuManager menu)
    {
        Entry entry = this.recent.getEntryAtCursor(this.getContext());

        if (entry == null)
        {
            return;
        }

        menu.action(this.host.getTabIcon(entry.id), UIKeys.PANELS_LANDING_OPEN, () -> this.open(entry));
        menu.action(Icons.MORE, UIKeys.PANELS_LANDING_SHOW_IN_MANAGER, () -> this.host.showInList(entry.id));
        menu.action(Icons.REMOVE, UIKeys.PANELS_LANDING_FORGET, () -> this.forget(entry));
    }

    private void forget(Entry entry)
    {
        BBSSettings.recentData.forget(this.host.getRecentType(), entry.id);
        this.refresh();
    }

    /* Rendering */

    private void renderBackdrop(UIContext context)
    {
        this.backdrop.render(context, this.area);
    }

    private void renderCard(UIContext context, Area area)
    {
        int bg = BBSSettings.raisedSurface();
        int border = BBSSettings.color(BBSSettings.dividerColor(), Colors.A12);

        context.batcher.dropShadow(area.x, area.y, area.ex(), area.ey(), 14, Colors.A50, 0);
        context.batcher.box(area.x, area.y, area.ex(), area.ey(), bg);
        context.batcher.outline(area.x, area.y, area.ex(), area.ey(), border);
    }

    private void renderEmptyHint(UIContext context)
    {
        if (!this.recent.getList().isEmpty())
        {
            return;
        }

        FontRenderer font = context.batcher.getFont();
        Area area = this.recent.area;
        List<String> lines = font.wrap(UIKeys.PANELS_LANDING_RECENT_EMPTY.get(), area.w - PADDING * 2);
        int lineH = font.getHeight() + 2;
        int y = area.my() - lines.size() * lineH / 2;

        for (String line : lines)
        {
            context.batcher.text(line, area.mx() - font.getWidth(line) / 2, y, MUTED, false);

            y += lineH;
        }
    }

    private void renderBannerCaption(UIContext context, Area area)
    {
        FontRenderer font = context.batcher.getFont();
        BannerConfig config = BannerConfig.get();
        int height = font.getHeight() + 14;
        int y = area.ey() - PADDING - height;

        /* These captions sit on artwork in either theme, so use fixed light ink. */
        int ink = 0xfff2f4f8;
        int secondary = 0xffb4bccb;

        context.batcher.gradientVBox(area.x, y - 24, area.ex(), area.ey(), 0x00070910, 0xa0070910);

        if (config.plateEnabled)
        {
            this.renderPlate(context, font, area, ink, secondary);
        }

        if (config.creditEnabled && !config.creditText.trim().isEmpty())
        {
            this.renderCredit(context, font, area, y + 7, ink, secondary);
        }
    }

    /** The "BBS FS REOLD <version>" plate on the left; it keeps its wording and only switches off. */
    private void renderPlate(UIContext context, FontRenderer font, Area area, int ink, int secondary)
    {
        String brand = "\u00a7lBBS FS REOLD";
        int brandWidth = font.getWidth(brand);
        int versionWidth = this.bannerVersion.isEmpty() ? 0 : font.getWidth(this.bannerVersion) + 16;
        int x = area.x + PADDING;
        int height = font.getHeight() + 14;
        int y = area.ey() - PADDING - height;
        int width = brandWidth + versionWidth + 18;

        context.batcher.box(x, y, x + width, y + height, 0xc010141d);
        context.batcher.outline(x, y, x + width, y + height, 0x28f2f4f8);
        context.batcher.box(x, y + 4, x + 2, y + height - 4, Colors.A100 | BBSSettings.primaryColor.get());
        context.batcher.text(brand, x + 9, y + 7, ink, false);

        if (!this.bannerVersion.isEmpty())
        {
            int dividerX = x + 9 + brandWidth + 7;

            context.batcher.box(dividerX, y + 7, dividerX + 1, y + 7 + font.getHeight(), 0x40b4bccb);
            context.batcher.text(this.bannerVersion, dividerX + 8, y + 7, secondary, false);
        }
    }

    /**
     * The credit line on the right, word by word: the pencil picks which word (or all of them)
     * gets the bright ink, the rest stays in the muted tone.
     */
    private void renderCredit(UIContext context, FontRenderer font, Area area, int textY, int ink, int secondary)
    {
        String[] words = BannerConfig.get().creditText.trim().split(" ");

        if (words.length == 0)
        {
            return;
        }

        int space = font.getWidth(" ");
        int width = 0;

        for (String word : words)
        {
            width += font.getWidth(word);
        }

        width += space * (words.length - 1);

        int x = area.ex() - PADDING - width;
        int accent = BannerConfig.get().creditStyle;

        for (int i = 0; i < words.length; i++)
        {
            int color;

            switch (accent)
            {
                case BannerConfig.CREDIT_STYLE_FIRST:
                {
                    color = i == 0 ? ink : secondary;
                    break;
                }
                case BannerConfig.CREDIT_STYLE_ALL:
                {
                    color = ink;
                    break;
                }
                case BannerConfig.CREDIT_STYLE_NONE:
                {
                    color = secondary;
                    break;
                }
                default:
                {
                    /* The stock look: the name at the end in white, the words before it muted. */
                    color = i == words.length - 1 ? ink : secondary;
                }
            }

            context.batcher.text(words[i], x, textY, color, false);
            x += font.getWidth(words[i]) + space;
        }
    }

    private void renderBanner(UIContext context, Area area)
    {
        BannerConfig config = BannerConfig.get();
        List<BannerConfig.Banner> banners = config.banners;

        if (banners.isEmpty())
        {
            return;
        }

        /* Warm the texture cache before timing the slideshow, including after a reload.
         * A GIF answers with the frame its own delays say to show right now. */
        for (BannerConfig.Banner banner : banners)
        {
            BBSModClient.getTextures().getTexture(banner.link);
        }

        if (banners.size() == 1)
        {
            this.renderBannerImage(context, area, banners.get(0), 1F);
            return;
        }

        if (bannerStarted == 0)
        {
            bannerStarted = System.nanoTime();
        }

        double duration = BANNER_HOLD_SECONDS + BANNER_FADE_SECONDS;
        double time = (System.nanoTime() - bannerStarted) / 1_000_000_000.0;
        double cycle = time % (duration * banners.size());
        int current = (int) (cycle / duration);
        float progress = (float) Math.max(0, (cycle % duration - BANNER_HOLD_SECONDS) / BANNER_FADE_SECONDS);
        float alpha = progress * progress * (3F - 2F * progress);

        /* Keep the lower image opaque: fading both layers would darken the midpoint. */
        this.renderBannerImage(context, area, banners.get(current), 1F);

        if (alpha > 0F)
        {
            this.renderBannerImage(context, area, banners.get((current + 1) % banners.size()), alpha);
        }
    }

    private void renderBannerImage(UIContext context, Area area, BannerConfig.Banner banner, float alpha)
    {
        Texture texture = BBSModClient.getTextures().getTexture(banner.link);

        if (texture == null)
        {
            return;
        }

        renderBannerCrop(context.batcher, area, texture, banner.focusX, banner.focusY, banner.zoom, alpha);
    }

    /**
     * Draws {@code texture} into {@code area} cover-cropped: scaled so the image fills the area,
     * zoomed in further by {@code zoom}, and panned so {@code focusX}/{@code focusY} (0..1) pick
     * which slice of the leftover image is shown. Centre focus at zoom 1 is a plain cover crop;
     * {@code alpha} fades the whole slice, for the crossfade between banners.
     */
    public static void renderBannerCrop(Batcher2D batcher, Area area, Texture texture, float focusX, float focusY, float zoom, float alpha)
    {
        float scale = Math.max(area.w / (float) texture.width, area.h / (float) texture.height) * Math.max(zoom, 1F);
        float cropW = area.w / scale;
        float cropH = area.h / scale;
        float u1 = (texture.width - cropW) * MathUtils.clamp(focusX, 0F, 1F);
        float v1 = (texture.height - cropH) * MathUtils.clamp(focusY, 0F, 1F);

        batcher.texturedBox(texture, Colors.setA(Colors.WHITE, alpha), area.x, area.y, area.ex() - area.x, area.ey() - area.y, u1, v1, u1 + cropW, v1 + cropH);
    }

    public static void renderBannerCrop(Batcher2D batcher, Area area, Texture texture, float focusX, float focusY, float zoom, float alpha)
    {
        renderBannerCrop(batcher, area, texture, focusX, focusY, zoom, alpha, 0F, 0F, 0F, 0F);
    }

    public static void renderBannerCrop(Batcher2D batcher, Area area, Texture texture, float focusX, float focusY, float zoom)
    {
        renderBannerCrop(batcher, area, texture, focusX, focusY, zoom, 1F);
    }
}
