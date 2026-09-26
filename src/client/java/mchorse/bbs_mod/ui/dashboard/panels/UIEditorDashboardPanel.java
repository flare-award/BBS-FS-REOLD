package mchorse.bbs_mod.ui.dashboard.panels;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.Keys;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.dashboard.UIDashboard;
import mchorse.bbs_mod.ui.dashboard.panels.bar.UIPanelActionBar;
import mchorse.bbs_mod.ui.dashboard.panels.bar.UIPanelTopBar;
import mchorse.bbs_mod.ui.dashboard.panels.landing.ILandingHost;
import mchorse.bbs_mod.ui.dashboard.panels.landing.UILandingScreen;
import mchorse.bbs_mod.ui.dashboard.panels.tabs.IUITabsHost;
import mchorse.bbs_mod.ui.dashboard.panels.tabs.UITabList;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.utils.UIDraggable;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.MathUtils;
import org.lwjgl.glfw.GLFW;

import java.util.Collection;

/**
 * A dashboard panel that edits something: a strip of tabs and actions along the top, and the
 * editor filling everything underneath it.
 *
 * <p>Where the actions sit and how they look is decided once, here and in {@link UIPanelTopBar},
 * so every editor panel reads the same way. Subclasses only contribute buttons through
 * {@link #actions()} and place their content with {@link #layoutUnderTopBar(UIElement)}.</p>
 *
 * <p>Tabs come with the panel rather than being switched on per panel: whatever an editor opens,
 * it can have several of them open at once. A subclass only says what an id means — see
 * {@link IUITabsHost}.</p>
 *
 * <p>So does the landing screen of an empty tab: the panel calls {@link #mountLanding()} and this
 * class owns the rest — where it sits, when it shows, and what it knows still exists.</p>
 */
public abstract class UIEditorDashboardPanel extends UIDashboardPanel implements IUITabsHost, ILandingHost
{
    /** How wide the embedded list column is; the same width the list takes as a floating overlay. */
    public static final int DATA_LIST_WIDTH = 200;

    public final UIPanelTopBar topBar;
    public final UIElement editor;
    public final UITabList tabs;

    /** The screen of an empty tab, or null for a panel that never called {@link #mountLanding()}. */
    public UILandingScreen landing;

    /**
     * The list of what this panel edits, kept as a column on the left while
     * {@code BBSSettings.openDataList} is on; null while it is off.
     */
    public UIOverlayPanel dataManager;

    /** How thin the column may get: the list has to stay readable. */
    private static final int DATA_LIST_MIN_WIDTH = 160;

    /** The width the column asks for; the user can drag its right edge within the limits. */
    private int dataListWidth = DATA_LIST_WIDTH;

    /** The drag handle on the column's right edge; attached while the column is on screen. */
    private UIDraggable dataListEdge;

    protected boolean update;

    public UIEditorDashboardPanel(UIDashboard dashboard)
    {
        super(dashboard);

        this.tabs = new UITabList(this);

        this.topBar = new UIPanelTopBar();
        this.topBar.relative(this).w(1F).h(UIPanelTopBar.HEIGHT);
        this.tabs.setBar(this.topBar.enableTabs(this.tabs));

        this.editor = new UIElement();

        this.layoutUnderTopBar(this.editor);
        this.add(this.topBar, this.editor);

        this.keys().register(Keys.OPEN_NEW_TAB, this.tabs::addTab);

        this.onOpen(() -> this.update = true);
        this.onAppear(this::requestNamesWhenStale);
    }

    /**
     * The name list is refreshed once per screen, when this panel is actually looked at — and
     * again whenever the landing screen is the thing being looked at, because its list of what
     * was opened last is only as honest as the names behind it.
     */
    private void requestNamesWhenStale()
    {
        if (this.update || (this.landing != null && this.landing.isVisible()))
        {
            this.update = false;

            this.requestNames();
        }
    }

    /** The buttons of this panel's top bar. */
    public UIPanelActionBar actions()
    {
        return this.topBar.actions;
    }

    /**
     * Stretch an element across everything below the top bar — the editor itself, and the
     * selection screens that cover it.
     */
    protected <T extends UIElement> T layoutUnderTopBar(T element)
    {
        element.relative(this).y(UIPanelTopBar.HEIGHT).w(1F).h(1F, -UIPanelTopBar.HEIGHT);

        return element;
    }

    /**
     * The list of what this panel edits, built to sit as a column on the left, or null for a
     * panel without a list. Called only while the "open list" setting is on, and expected to be
     * cheap: it is asked again every frame while the panel is on screen.
     */
    protected UIOverlayPanel createDataManager()
    {
        return null;
    }

    /**
     * Keep the list on screen as a column of the panel while the setting is on and the landing
     * screen is what the panel shows — the column belongs next to the menu of an empty tab, and
     * an opened tab keeps its editor unshifted, with the list reachable from the top bar.
     */
    public void syncDataManager()
    {
        UIOverlayPanel desired = BBSSettings.openDataList.get() && this.landing != null && this.landing.isVisible()
            ? this.createDataManager()
            : null;

        if (desired == this.dataManager)
        {
            /* The width is one value for every tab: another tab may have dragged the shared
             * column while this one was off screen, and this one follows it. */
            int persisted = (int) BBSSettings.editorLayoutSettings.getSplitSize("open_data_list", DATA_LIST_WIDTH);

            if (persisted != this.dataListWidth && this.landing != null && this.landing.area.w > 0)
            {
                this.dataListWidth = MathUtils.clamp(persisted, DATA_LIST_MIN_WIDTH, this.dataListMaxWidth());
                desired.w(this.dataListWidth);
                desired.resize();
                this.landing.setListInset(this.dataListWidth);
            }

            return;
        }

        if (this.dataManager != null)
        {
            /* It comes back later as a floating overlay: its close button, drag and corner grip
             * must not stay hidden, and the column's placement must not travel with it, or the
             * next open lands wherever the column sat rather than where overlays belong. */
            this.dataManager.close.setVisible(true);
            this.dataManager.movable = true;
            this.dataManager.setResizable(true);
            this.dataManager.resetFlex();
            this.remove(this.dataManager);

            if (this.dataListEdge != null)
            {
                this.dataListEdge.removeFromParent();
            }
        }

        this.dataManager = desired;

        if (desired != null)
        {
            /* A column that is always there does not close itself, does not move from its place
             * and does not resize from a corner. Whatever the panel was last placed as (a
             * floating overlay's anchor included) is thrown away, so the column lands exactly
             * where it is told. */
            desired.close.setVisible(false);
            desired.movable = false;
            desired.setResizable(false);
            desired.resetFlex();

            /* The setting can be switched on while the list is still open as a floating overlay;
             * it cannot be in two places at once, so the floating copy &mdash; and the empty
             * overlay container that would keep blocking the screen with it &mdash; goes first. */
            UIElement parent = desired.getParent();

            if (parent instanceof UIOverlay)
            {
                parent.removeFromParent();
            }

            if (desired.hasParent())
            {
                desired.removeFromParent();
            }

            if (this.dataListEdge == null)
            {
                this.dataListEdge = new UIDraggable(this::dragDataListWidth)
                    .cursors(GLFW.GLFW_HRESIZE_CURSOR, GLFW.GLFW_HRESIZE_CURSOR)
                    .dragEnd(() -> BBSSettings.editorLayoutSettings.setSplitSize("open_data_list", this.dataListWidth));
            }

            /* The width the user last dragged to, the default until there is one; a screen
             * that got narrower since must not inherit a column it cannot fit. */
            this.dataListWidth = (int) BBSSettings.editorLayoutSettings.getSplitSize("open_data_list", DATA_LIST_WIDTH);

            if (this.landing != null && this.landing.area.w > 0)
            {
                this.dataListWidth = MathUtils.clamp(this.dataListWidth, DATA_LIST_MIN_WIDTH, this.dataListMaxWidth());
            }

            this.add(desired);
            desired.relative(this).x(0).y(UIPanelTopBar.HEIGHT).w(this.dataListWidth).h(1F, -UIPanelTopBar.HEIGHT);

            /* No handle to see, no strip to show: a three-pixel seam hugging the column's right
             * edge, inside it, where the cursor says "drag" and that is all. */
            this.dataListEdge.relative(desired).x(1F, -3).y(0).w(3).h(1F);
            desired.add(this.dataListEdge);
        }

        if (this.landing != null)
        {
            this.landing.setListInset(this.dataManager == null ? 0 : this.dataListWidth);
        }

        this.syncListButton();
        this.resize();
    }

    /**
     * How wide the column may get: the card in the middle of the screen stays clear, with a
     * breath of space between the two.
     */
    private int dataListMaxWidth()
    {
        if (this.landing == null || this.landing.area.w <= 0)
        {
            return DATA_LIST_MIN_WIDTH;
        }

        return Math.max(DATA_LIST_MIN_WIDTH, (this.landing.area.w - UILandingScreen.CARD_W) / 2 - 12);
    }

    /**
     * Drag the column's right edge: wider or thinner, but never so wide that it reaches the
     * card in the middle of the screen, and never so thin that the list cannot be read.
     */
    private void dragDataListWidth(UIContext context)
    {
        UIOverlayPanel column = this.dataManager;

        if (column == null || this.landing == null || this.landing.area.w <= 0)
        {
            return;
        }

        int width = MathUtils.clamp(context.mouseX - column.area.x, DATA_LIST_MIN_WIDTH, this.dataListMaxWidth());

        if (width == this.dataListWidth)
        {
            return;
        }

        this.dataListWidth = width;
        column.w(width);
        column.resize();
        this.landing.setListInset(width);
    }

    /**
     * Hook for the button that opens the list as a floating overlay. The button itself is
     * always on the panel's top bar; the column on the landing screen takes the place of the
     * landing's own list entry, not of this button.
     */
    protected void syncListButton()
    {}

    @Override
    public void render(UIContext context)
    {
        /* A setting flipped while the panel is on screen lands here, the way the panel bar side does. */
        this.syncDataManager();

        super.render(context);
    }

    /* The landing screen of an empty tab */

    /**
     * Give this panel the landing screen, on top of whatever it has put on screen so far.
     *
     * <p>Called by the panel at the end of its own constructor rather than from here: the screen
     * asks the panel what it edits and what it can create, and a panel still under construction
     * has no answer yet — {@code overlay} in particular is assigned by a constructor that has not
     * run.</p>
     */
    protected void mountLanding()
    {
        this.landing = new UILandingScreen(this);

        this.add(this.layoutUnderTopBar(this.landing));

        this.syncLanding();
        this.syncDataManager();
    }

    /**
     * An empty tab shows the landing screen, anything else shows the editor.
     *
     * <p>Goes by the id of the tab rather than by whether the document itself has arrived: over
     * the network that takes a moment, and the landing screen must not flash in the meantime.</p>
     */
    public void syncLanding()
    {
        if (this.landing != null)
        {
            this.landing.setVisible(this.tabs.getCurrentId() == null);
        }
    }

    /* IUITabsHost — what a tab holds is the subclass's business */

    /**
     * Final, so that showing a tab and syncing the landing screen cannot come apart. The panel
     * says what an id means in {@link #showTab(String)}.
     */
    @Override
    public final void openTab(String id)
    {
        this.showTab(id);
        this.syncLanding();
    }

    /** Show whatever this id refers to; null means show nothing. */
    protected abstract void showTab(String id);

    @Override
    public IKey getNewTabLabel()
    {
        return UIKeys.PANELS_TABS_NEW_TAB;
    }

    /** Icon of a tab; the id is null for a tab with nothing open in it. */
    @Override
    public Icon getTabIcon(String id)
    {
        return id == null ? Icons.SEARCH : Icons.FOLDER;
    }

    /* ILandingHost — opening a document is opening a tab, everywhere */

    @Override
    public void pickData(String id)
    {
        this.tabs.pick(id);
    }

    /** Ask what still exists; the answer is expected back through {@link #fillNames(Collection)}. */
    @Override
    public abstract void requestNames();

    /** The ids that still exist. */
    public void fillNames(Collection<String> names)
    {
        if (this.landing != null)
        {
            this.landing.fillNames(names);
        }
    }
}
