package mchorse.bbs_mod.ui.forms.editors.forms;

import mchorse.bbs_mod.forms.forms.FilterBoardForm;
import mchorse.bbs_mod.ui.Keys;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.forms.editors.panels.UIFilterBoardFormPanel;
import mchorse.bbs_mod.ui.forms.editors.panels.UIMaterialFormPanel;
import mchorse.bbs_mod.ui.utils.icons.Icons;

/** Editor for a FilterBoard's billboard mask and per-form film filters. */
public class UIFilterBoardForm extends UIForm<FilterBoardForm>
{
    private final UIFilterBoardFormPanel filterBoardPanel;

    public UIFilterBoardForm()
    {
        super();

        this.filterBoardPanel = new UIFilterBoardFormPanel(this);
        this.defaultPanel = this.filterBoardPanel;

        this.registerPanel(this.defaultPanel, UIKeys.FILM_FILTERS_TITLE, Icons.FILTER);
        this.registerPanel(new UIMaterialFormPanel(this), UIKeys.FORMS_EDITORS_MATERIAL_TITLE, Icons.MATERIAL);
        this.registerDefaultPanels();

        this.defaultPanel.keys().register(Keys.FORMS_PICK_TEXTURE, () ->
        {
            if (this.view != this.filterBoardPanel)
            {
                this.setPanel(this.filterBoardPanel);
            }

            this.filterBoardPanel.pick.clickItself();
        });
    }
}
