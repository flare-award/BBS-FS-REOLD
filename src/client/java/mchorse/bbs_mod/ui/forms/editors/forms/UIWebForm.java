package mchorse.bbs_mod.ui.forms.editors.forms;

import mchorse.bbs_mod.forms.forms.WebForm;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.forms.editors.panels.UIWebFormPanel;
import mchorse.bbs_mod.ui.forms.editors.panels.UIMaterialFormPanel;
import mchorse.bbs_mod.ui.utils.icons.Icons;

public class UIWebForm extends UIForm<WebForm>
{
    public UIWebFormPanel webPanel;

    public UIWebForm()
    {
        super();

        this.webPanel = new UIWebFormPanel(this);
        this.defaultPanel = this.webPanel;

        this.registerPanel(this.defaultPanel, UIKeys.FORMS_EDITORS_WEB_TITLE, Icons.PHYSICS);
        this.registerPanel(new UIMaterialFormPanel(this), UIKeys.FORMS_EDITORS_MATERIAL_TITLE, Icons.MATERIAL);
        this.registerDefaultPanels();
    }
}
