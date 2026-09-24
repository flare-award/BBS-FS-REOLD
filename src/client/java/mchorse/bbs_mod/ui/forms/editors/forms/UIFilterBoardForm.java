package mchorse.bbs_mod.ui.forms.editors.forms;

import mchorse.bbs_mod.forms.forms.FilterBoardForm;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.forms.editors.panels.UIFilterBoardFormPanel;
import mchorse.bbs_mod.ui.utils.icons.Icons;

/** Editor for a FilterBoard's world lens and local film filters. */
public class UIFilterBoardForm extends UIForm<FilterBoardForm>
{
    public UIFilterBoardForm()
    {
        super();

        this.defaultPanel = new UIFilterBoardFormPanel(this);

        this.registerPanel(this.defaultPanel, UIKeys.FILM_FILTERS_TITLE, Icons.FILTER);
        this.registerDefaultPanels();
    }
}
