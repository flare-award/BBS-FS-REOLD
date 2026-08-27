package mchorse.bbs_mod.ui.framework.elements.buttons;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.ui.framework.elements.utils.ITextColoring;
import mchorse.bbs_mod.ui.utils.UIConstants;
import mchorse.bbs_mod.utils.colors.Colors;

import java.util.function.Consumer;

public class UIButton extends UIClickable<UIButton> implements ITextColoring
{
    public IKey label;

    public int textColor = Colors.WHITE;
    public boolean textShadow = true;

    public boolean custom;
    public int customColor;
    public int customHighlightColor;
    public boolean background = true;

    public UIButton(IKey label, Consumer<UIButton> callback)
    {
        super(callback);

        this.label = label;
        this.h(UIConstants.CONTROL_HEIGHT);
    }

    public UIButton color(int color)
    {
        this.custom = true;
        this.customColor = color & Colors.RGB;
        this.customHighlightColor = this.customColor;

        return this;
    }

    public UIButton color(int color, int highlightColor)
    {
        this.custom = true;
        this.customColor = color;
        this.customHighlightColor = highlightColor;

        return this;
    }

    public UIButton textColor(int color, boolean shadow)
    {
        this.textColor = color;
        this.textShadow = shadow;

        return this;
    }

    public UIButton background(boolean background)
    {
        this.background = background;

        return this;
    }

    @Override
    public void setColor(int color, boolean shadow)
    {
        this.textColor = color;
        this.textShadow = shadow;
    }

    @Override
    protected UIButton get()
    {
        return this;
    }

    @Override
    protected void renderSkin(UIContext context)
    {
        int color = (this.custom ? this.customColor : BBSSettings.primaryColor.get() | Colors.A100);

        if (this.hover)
        {
            color = this.custom ? this.customHighlightColor : Colors.mulRGB(color, 0.85F);
        }

        if (this.background)
        {
            if (!this.custom && BBSSettings.isPrimaryGradient())
            {
                /* The accent flows into the second color; hovering dims both ends the
                 * same way the flat fill dims, so the gradient darkens as one piece. */
                int end = BBSSettings.primaryColorEnd() | Colors.A100;

                if (this.hover)
                {
                    end = Colors.mulRGB(end, 0.85F);
                }

                context.batcher.gradientSurfaceBox(this.area.x, this.area.y, this.area.ex(), this.area.ey(), color | Colors.A100, end | Colors.A100, true, false);
            }
            else
            {
                context.batcher.surfaceBox(this.area.x, this.area.y, this.area.ex(), this.area.ey(), color | Colors.A100, true, false);
            }
        }

        FontRenderer font = context.batcher.getFont();
        String label = font.limitToWidth(this.label.get(), this.area.w - 4);
        int x = this.area.mx(font.getWidth(label));
        int y = this.area.my(font.getHeight());

        context.batcher.text(label, x, y, Colors.mulRGB(this.textColor, this.hover ? 0.9F : 1F), this.textShadow);

        this.renderLockedArea(context);
    }
}