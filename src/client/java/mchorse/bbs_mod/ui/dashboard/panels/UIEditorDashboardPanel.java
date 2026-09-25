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
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlayPanel;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.ui.utils.icons.Icons;

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
            return;
        }

        if (this.dataManager != null)
        {
            /* It comes back later as a floating overlay; its close button and drag must not stay hidden. */
            this.dataManager.close.setVisible(true);
            this.dataManager.movable = true;
            this.remove(this.dataManager);
        }

        this.dataManager = desired;

        if (desired != null)
        {
            /* A column that is always there does not close itself and does not move from its place. */
            desired.close.setVisible(false);
            desired.movable = false;

            this.add(desired);
            desired.relative(this).x(0).y(UIPanelTopBar.HEIGHT).w(DATA_LIST_WIDTH).h(1F, -UIPanelTopBar.HEIGHT);
        }

        if (this.landing != null)
        {
            this.landing.setListInset(this.dataManager == null ? 0 : DATA_LIST_WIDTH);
        }

        this.syncListButton();
        this.resize();
    }

    /** The button that opens the list as an overlay; the column on the landing screen replaces it. */
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
