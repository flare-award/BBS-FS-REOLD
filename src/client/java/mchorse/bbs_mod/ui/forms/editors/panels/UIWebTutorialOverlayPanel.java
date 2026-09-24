package mchorse.bbs_mod.ui.forms.editors.panels;

import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.UIScrollView;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
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
 * reads, this is a proper little book: chapters on the left, and on the right a
 * moving diagram of what the chapter is about, the text, and a way to page through
 * it without going back to the list.</p>
 */
public class UIWebTutorialOverlayPanel extends UIOverlayPanel
{
    public UILabelList<Integer> chapters;
    public UIScrollView page;
    public UIWebTutorialPreview preview;
    public UILabel heading;
    public UIText body;
    public UIButton previous;
    public UIButton next;

    private final List<IKey> titles = new ArrayList<>();
    private final List<IKey> texts = new ArrayList<>();

    private int current;

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
        this.chapters.relative(this.content).xy(6, 6).w(132).h(1F, -12);

        for (int i = 0; i < this.titles.size(); i++)
        {
            this.chapters.add(IKey.constant((i + 1) + ". " + this.titles.get(i).get()), i);
        }

        this.preview = new UIWebTutorialPreview();
        this.preview.h(96);

        this.heading = UI.label(IKey.EMPTY).background(Colors.A50).color(Colors.WHITE);
        this.heading.h(20);

        this.body = new UIText().lineHeight(12).color(Colors.LIGHTEST_GRAY, true);

        this.previous = new UIButton(UIKeys.FORMS_EDITORS_WEB_TUTORIAL_PREVIOUS, (b) -> this.step(-1));
        this.next = new UIButton(UIKeys.FORMS_EDITORS_WEB_TUTORIAL_NEXT, (b) -> this.step(1));

        UIElement navigation = UI.row(this.previous, this.next);

        this.page = UI.scrollView(8, 6, this.preview, this.heading, this.body, navigation);
        this.page.relative(this.content).x(144).y(6).w(1F, -150).h(1F, -12);

        this.content.add(this.chapters, this.page);

        this.pick(0);
        this.chapters.setIndex(0);
    }

    private void chapter(IKey title, IKey text)
    {
        this.titles.add(title);
        this.texts.add(text);
    }

    private void step(int direction)
    {
        int index = this.current + direction;

        if (index < 0 || index >= this.texts.size())
        {
            return;
        }

        this.pick(index);
        this.chapters.setIndex(index);
    }

    private void pick(int index)
    {
        if (index < 0 || index >= this.texts.size())
        {
            return;
        }

        this.current = index;
        this.heading.label = IKey.constant("  " + this.titles.get(index).get());
        this.body.text(this.texts.get(index));
        this.preview.type(index);
        this.previous.setEnabled(index > 0);
        this.next.setEnabled(index < this.texts.size() - 1);
        this.page.scroll.scrollTo(0);
        this.page.resize();
    }
}
