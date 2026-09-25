package mchorse.bbs_mod.ui.dashboard.panels;

import mchorse.bbs_mod.ui.Keys;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.dashboard.UIDashboard;
import mchorse.bbs_mod.ui.dashboard.panels.overlay.UICRUDOverlayPanel;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlayPanel;
import mchorse.bbs_mod.ui.utils.icons.Icons;

public abstract class UICRUDDashboardPanel extends UIEditorDashboardPanel
{
    public UIIcon openOverlay;

    public final UICRUDOverlayPanel overlay;

    public UICRUDDashboardPanel(UIDashboard dashboard)
    {
        super(dashboard);

        this.overlay = this.createOverlayPanel();
        this.openOverlay = new UIIcon(Icons.MORE, (b) -> this.openDataManager());
        this.openOverlay.tooltip(UIKeys.PANELS_KEYS_OPEN_DATA_MANAGER);

        /* The list of what this panel edits is a button of its own, in the same place in every panel */
        this.actions().common(this.openOverlay);

        this.keys().register(Keys.OPEN_DATA_MANAGER, this::openDataManager);
    }

    /**
     * Put the data manager on screen. Opens the overlay directly rather than through the button,
     * so that the keybind does not depend on the button being on the bar. While the list is
     * already kept open as a column of the panel, there is nothing to open.
     */
    public void openDataManager()
    {
        if (this.dataManager == this.overlay)
        {
            return;
        }

        UIContext context = this.getContext();

        if (context != null)
        {
            UIOverlay.addOverlay(context, this.overlay, 200, 0.9F);
        }
    }

    @Override
    protected UIOverlayPanel createDataManager()
    {
        return this.overlay;
    }

    @Override
    protected void syncListButton()
    {
        this.openOverlay.setVisible(this.dataManager != this.overlay);
    }

    protected abstract UICRUDOverlayPanel createOverlayPanel();
}
