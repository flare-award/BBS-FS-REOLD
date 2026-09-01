package mchorse.bbs_mod.ui.forms.editors.panels;

import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.elements.UIScrollView;
import mchorse.bbs_mod.ui.framework.elements.input.list.UILabelList;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.utils.UILabel;
import mchorse.bbs_mod.ui.framework.elements.utils.UIText;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.utils.colors.Colors;

import java.util.ArrayList;
import java.util.List;

/**
 * The web form's own manual.
 *
 * <p>The web has more knobs than any other form in here, and most of them only
 * make sense together (length against sag, damping against stiffness, the shooter
 * against the anchor mode). Rather than stuffing all of that into tooltips nobody
 * reads, the panel gets a chapter list on the left and a scrollable page on the
 * right - every field explained, plus how to animate them in a film.</p>
 */
public class UIWebTutorialOverlayPanel extends UIOverlayPanel
{
    public UILabelList<Integer> chapters;
    public UIScrollView page;
    public UILabel heading;
    public UIText body;

    private final List<IKey> titles = new ArrayList<>();
    private final List<IKey> texts = new ArrayList<>();

    public UIWebTutorialOverlayPanel()
    {
        super(UIKeys.FORMS_EDITORS_WEB_TUTORIAL_TITLE);

        this.chapter(UIKeys.FORMS_EDITORS_WEB_TUTORIAL_BASICS, UIKeys.FORMS_EDITORS_WEB_TUTORIAL_BASICS_TEXT);
        this.chapter(UIKeys.FORMS_EDITORS_WEB_TUTORIAL_ANCHORS, UIKeys.FORMS_EDITORS_WEB_TUTORIAL_ANCHORS_TEXT);
        this.chapter(UIKeys.FORMS_EDITORS_WEB_TUTORIAL_SHAPE, UIKeys.FORMS_EDITORS_WEB_TUTORIAL_SHAPE_TEXT);
        this.chapter(UIKeys.FORMS_EDITORS_WEB_TUTORIAL_PHYSICS, UIKeys.FORMS_EDITORS_WEB_TUTORIAL_PHYSICS_TEXT);
        this.chapter(UIKeys.FORMS_EDITORS_WEB_TUTORIAL_WIND, UIKeys.FORMS_EDITORS_WEB_TUTORIAL_WIND_TEXT);
        this.chapter(UIKeys.FORMS_EDITORS_WEB_TUTORIAL_SHOOTER, UIKeys.FORMS_EDITORS_WEB_TUTORIAL_SHOOTER_TEXT);
        this.chapter(UIKeys.FORMS_EDITORS_WEB_TUTORIAL_PARTS, UIKeys.FORMS_EDITORS_WEB_TUTORIAL_PARTS_TEXT);
        this.chapter(UIKeys.FORMS_EDITORS_WEB_TUTORIAL_MATERIAL, UIKeys.FORMS_EDITORS_WEB_TUTORIAL_MATERIAL_TEXT);
        this.chapter(UIKeys.FORMS_EDITORS_WEB_TUTORIAL_FILMS, UIKeys.FORMS_EDITORS_WEB_TUTORIAL_FILMS_TEXT);
        this.chapter(UIKeys.FORMS_EDITORS_WEB_TUTORIAL_RECIPES, UIKeys.FORMS_EDITORS_WEB_TUTORIAL_RECIPES_TEXT);
        this.chapter(UIKeys.FORMS_EDITORS_WEB_TUTORIAL_PROBLEMS, UIKeys.FORMS_EDITORS_WEB_TUTORIAL_PROBLEMS_TEXT);

        this.chapters = new UILabelList<>((list) ->
        {
            if (!list.isEmpty())
            {
                this.pick(list.get(0).value);
            }
        });
        this.chapters.background();
        this.chapters.relative(this.content).xy(6, 6).w(110).h(1F, -12);

        for (int i = 0; i < this.titles.size(); i++)
        {
            this.chapters.add(this.titles.get(i), i);
        }

        this.heading = UI.label(IKey.EMPTY).color(Colors.WHITE);
        this.body = new UIText().lineHeight(12).color(Colors.LIGHTEST_GRAY, true);

        this.page = UI.scrollView(6, 6, this.heading, this.body);
        this.page.relative(this.content).x(122).y(6).w(1F, -128).h(1F, -12);

        this.content.add(this.chapters, this.page);

        this.pick(0);
        this.chapters.setIndex(0);
    }

    private void chapter(IKey title, IKey text)
    {
        this.titles.add(title);
        this.texts.add(text);
    }

    private void pick(int index)
    {
        if (index < 0 || index >= this.texts.size())
        {
            return;
        }

        this.heading.label = this.titles.get(index);
        this.body.text(this.texts.get(index));
        this.page.scroll.scrollTo(0);
        this.page.resize();
    }
}
