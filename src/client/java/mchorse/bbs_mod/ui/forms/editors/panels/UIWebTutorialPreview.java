package mchorse.bbs_mod.ui.forms.editors.panels;

import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.utils.colors.Colors;

/**
 * The little animated diagram at the top of every tutorial chapter.
 *
 * <p>Everything here is drawn procedurally out of a few dozen boxes - no textures,
 * no models, no simulation - so it costs nothing to keep it moving while the page
 * is open, and it cannot break the way an embedded viewport could. What it buys is
 * that the reader sees what "free end", "taper" or "reel" actually look like before
 * reading a single line about them.</p>
 */
public class UIWebTutorialPreview extends UIElement
{
    public static final int BASICS = 0;
    public static final int ANCHORS = 1;
    public static final int SHAPE = 2;
    public static final int PHYSICS = 3;
    public static final int WIND = 4;
    public static final int SHOOTER = 5;
    public static final int PARTS = 6;
    public static final int MATERIAL = 7;
    public static final int FILMS = 8;
    public static final int RECIPES = 9;
    public static final int PROBLEMS = 10;

    private static final int WEB = 0xffe8e8f0;
    private static final int WEB_DIM = 0x77e8e8f0;
    private static final int ANCHOR = Colors.A100 | Colors.ACTIVE;
    private static final int WEIGHT = 0xffb8c0d0;
    private static final int WALL = 0xff3a3f47;
    private static final int ACCENT = Colors.A100 | Colors.INACTIVE;

    private int type;

    public UIWebTutorialPreview type(int type)
    {
        this.type = type;

        return this;
    }

    @Override
    public void render(UIContext context)
    {
        Area area = this.area;
        Batcher2D batcher = context.batcher;

        batcher.gradientVBox(area.x, area.y, area.ex(), area.ey(), 0x66101014, 0x99101014);
        batcher.outline(area.x, area.y, area.ex(), area.ey(), 0x33ffffff);

        float time = (System.currentTimeMillis() % 600000L) / 1000F;

        switch (this.type)
        {
            case BASICS: this.drawBasics(batcher, area, time); break;
            case ANCHORS: this.drawAnchors(batcher, area, time); break;
            case SHAPE: this.drawShape(batcher, area, time); break;
            case PHYSICS: this.drawPhysics(batcher, area, time); break;
            case WIND: this.drawWind(batcher, area, time); break;
            case SHOOTER: this.drawShooter(batcher, area, time); break;
            case PARTS: this.drawParts(batcher, area, time); break;
            case MATERIAL: this.drawMaterial(batcher, area, time); break;
            case FILMS: this.drawFilms(batcher, area, time); break;
            case RECIPES: this.drawRecipes(batcher, area, time); break;
            default: this.drawProblems(batcher, area, time); break;
        }

        super.render(context);
    }

    /* ---- Chapters ------------------------------------------------------ */

    private void drawBasics(Batcher2D batcher, Area area, float time)
    {
        float x1 = area.x + area.w * 0.2F;
        float y1 = area.y + area.h * 0.28F;
        float x2 = area.x + area.w * 0.8F;
        float y2 = area.y + area.h * 0.42F;
        float sway = (float) Math.sin(time * 1.4F) * area.h * 0.06F;

        this.rope(batcher, x1, y1, x2, y2 + sway, area.h * 0.3F + sway, WEB, 26);
        this.anchor(batcher, x1, y1);
        this.anchor(batcher, x2, y2 + sway);
    }

    private void drawAnchors(Batcher2D batcher, Area area, float time)
    {
        float third = area.w / 3F;
        float swing = (float) Math.sin(time * 1.6F);

        for (int i = 0; i < 3; i++)
        {
            float cx = area.x + third * i;
            float left = cx + third * 0.22F;
            float right = cx + third * 0.78F;
            float top = area.y + area.h * 0.3F;
            float hand = left + swing * third * 0.12F;

            if (i == 0)
            {
                this.rope(batcher, hand, top, right + swing * third * 0.12F, top + area.h * 0.2F, area.h * 0.22F, WEB, 16);
                this.anchor(batcher, hand, top);
                this.anchor(batcher, right + swing * third * 0.12F, top + area.h * 0.2F);
            }
            else if (i == 1)
            {
                this.rope(batcher, hand, top, right, top + area.h * 0.2F, area.h * 0.22F, WEB, 16);
                this.anchor(batcher, hand, top);
                this.wall(batcher, right, top + area.h * 0.2F);
            }
            else
            {
                float tip = top + area.h * 0.45F;

                this.rope(batcher, hand, top, hand + swing * third * 0.18F, tip, area.h * 0.05F, WEB, 16);
                this.anchor(batcher, hand, top);
            }

            batcher.text(this.anchorCaption(i), (int) (cx + third * 0.5F) - this.width(this.anchorCaption(i)) / 2,
                (int) (area.ey() - 12), Colors.LIGHTER_GRAY, true);
        }
    }

    private String anchorCaption(int index)
    {
        if (index == 0)
        {
            return UIKeys.FORMS_EDITORS_WEB_ANCHOR_FOLLOW.get();
        }

        return index == 1 ? UIKeys.FORMS_EDITORS_WEB_ANCHOR_LOCKED.get() : UIKeys.FORMS_EDITORS_WEB_ANCHOR_FREE.get();
    }

    private void drawShape(Batcher2D batcher, Area area, float time)
    {
        float third = area.w / 3F;
        float top = area.y + area.h * 0.3F;
        float bottom = top + area.h * 0.1F;
        float pulse = 0.5F + 0.5F * (float) Math.sin(time * 1.2F);

        /* Sag */
        this.rope(batcher, area.x + third * 0.2F, top, area.x + third * 0.8F, bottom,
            area.h * (0.08F + 0.3F * pulse), WEB, 24);

        /* Taper: the same line drawn thinner and thinner */
        for (int i = 0; i < 18; i++)
        {
            float progress = i / 17F;
            float x = area.x + third * 1.2F + third * 0.6F * progress;
            float y = top + area.h * 0.25F * progress;
            float thickness = 3F - 2.5F * progress;

            batcher.box(x, y - thickness * 0.5F, x + third * 0.05F, y + thickness * 0.5F, WEB);
        }

        /* Strands */
        for (int strand = -1; strand <= 1; strand++)
        {
            this.rope(batcher, area.x + third * 2.2F, top + strand * 1.5F, area.x + third * 2.8F,
                bottom + strand * 1.5F, area.h * 0.18F + strand * 2F, strand == 0 ? WEB : WEB_DIM, 20);
        }
    }

    private void drawPhysics(Batcher2D batcher, Area area, float time)
    {
        float pivotX = area.x + area.w * 0.5F;
        float pivotY = area.y + area.h * 0.2F;
        float length = area.h * 0.5F;
        float decay = (float) Math.exp(-(time % 6F) * 0.35F);
        float angle = (float) Math.sin((time % 6F) * 3.1F) * 0.9F * decay;
        float bobX = pivotX + (float) Math.sin(angle) * length;
        float bobY = pivotY + (float) Math.cos(angle) * length;

        this.rope(batcher, pivotX, pivotY, bobX, bobY, 0F, WEB, 20);
        this.anchor(batcher, pivotX, pivotY);
        batcher.box(bobX - 5F, bobY - 5F, bobX + 5F, bobY + 5F, WEIGHT);
        batcher.outline(bobX - 5F, bobY - 5F, bobX + 5F, bobY + 5F, 0x55000000);
    }

    private void drawWind(Batcher2D batcher, Area area, float time)
    {
        float left = area.x + area.w * 0.14F;
        float right = area.x + area.w * 0.86F;
        float y = area.y + area.h * 0.32F;
        int points = 34;

        for (int i = 0; i <= points; i++)
        {
            float progress = i / (float) points;
            float x = left + (right - left) * progress;
            float wave = (float) Math.sin(progress * 6F - time * 3F) * area.h * 0.13F * progress;

            batcher.box(x - 1F, y + wave + progress * area.h * 0.2F - 1F, x + 1F, y + wave + progress * area.h * 0.2F + 1F, WEB);
        }

        this.anchor(batcher, left, y);

        /* Gust arrows drifting across */
        for (int i = 0; i < 3; i++)
        {
            float offset = ((time * 40F + i * 60F) % (area.w * 0.9F));
            float ax = area.x + area.w * 0.05F + offset;
            float ay = area.y + area.h * 0.72F + i * 6F;

            batcher.box(ax, ay, ax + 14F, ay + 1F, 0x66aaddff);
            batcher.box(ax + 10F, ay - 2F, ax + 14F, ay + 1F, 0x66aaddff);
        }
    }

    private void drawShooter(Batcher2D batcher, Area area, float time)
    {
        float handX = area.x + area.w * 0.16F;
        float handY = area.y + area.h * 0.5F;
        float wallX = area.x + area.w * 0.84F;
        float cycle = time % 4F;
        float flight = Math.min(1F, cycle / 0.8F);

        batcher.box(wallX, area.y + area.h * 0.16F, wallX + 8F, area.ey() - area.h * 0.16F, WALL);
        this.anchor(batcher, handX, handY);

        float tipX = handX + (wallX - handX) * flight;

        if (cycle < 0.8F)
        {
            this.rope(batcher, handX, handY, tipX, handY, 0F, WEB, 16);
            batcher.box(tipX - 3F, handY - 3F, tipX + 3F, handY + 3F, WEB);
        }
        else
        {
            float settle = Math.min(1F, (cycle - 0.8F) * 1.5F);

            this.rope(batcher, handX, handY, wallX, handY, area.h * 0.22F * settle, WEB, 22);
            batcher.box(wallX - 5F, handY - 5F, wallX + 3F, handY + 5F, WEB);
            batcher.box(wallX - 7F, handY - 2F, wallX + 3F, handY + 2F, WEB_DIM);
        }
    }

    private void drawParts(Batcher2D batcher, Area area, float time)
    {
        float pivotX = area.x + area.w * 0.5F;
        float pivotY = area.y + area.h * 0.18F;
        float length = area.h * 0.45F;
        float angle = (float) Math.sin(time * 1.1F) * 0.45F;
        float endX = pivotX + (float) Math.sin(angle) * length;
        float endY = pivotY + (float) Math.cos(angle) * length;
        float midX = (pivotX + endX) * 0.5F;
        float midY = (pivotY + endY) * 0.5F;

        this.rope(batcher, pivotX, pivotY, endX, endY, 0F, WEB, 20);
        this.anchor(batcher, pivotX, pivotY);

        batcher.box(midX - 3F, midY - 3F, midX + 3F, midY + 3F, ACCENT);
        batcher.box(endX - 7F, endY - 4F, endX + 7F, endY + 10F, WEIGHT);
        batcher.outline(endX - 7F, endY - 4F, endX + 7F, endY + 10F, 0x55000000);

        String start = "start";
        String middle = "middle";
        String end = "end";

        batcher.text(start, (int) pivotX + 8, (int) pivotY - 4, Colors.LIGHTER_GRAY, true);
        batcher.text(middle, (int) midX + 8, (int) midY - 4, Colors.LIGHTER_GRAY, true);
        batcher.text(end, (int) endX + 12, (int) endY + 2, Colors.LIGHTER_GRAY, true);
    }

    private void drawMaterial(Batcher2D batcher, Area area, float time)
    {
        float top = area.y + area.h * 0.28F;
        int[] colors = {0xffffffff, 0xaaffffff, 0x66ffffff};

        for (int i = 0; i < colors.length; i++)
        {
            float y = top + i * area.h * 0.18F;

            this.rope(batcher, area.x + area.w * 0.18F, y, area.x + area.w * 0.82F, y, area.h * 0.1F, colors[i], 26);
        }

        float pulse = 0.5F + 0.5F * (float) Math.sin(time * 1.5F);
        int alpha = (int) (60 + 195 * pulse) << 24;

        batcher.box(area.x + area.w * 0.42F, area.ey() - 16F, area.x + area.w * 0.58F, area.ey() - 6F, alpha | 0xffffff);
        batcher.outline(area.x + area.w * 0.42F, area.ey() - 16F, area.x + area.w * 0.58F, area.ey() - 6F, 0x55ffffff);
    }

    private void drawFilms(Batcher2D batcher, Area area, float time)
    {
        float trackY = area.ey() - 18F;
        float left = area.x + 12F;
        float right = area.ex() - 12F;
        float progress = (time % 5F) / 5F;
        float head = left + (right - left) * progress;

        /* The web, changing with the playhead */
        float anchorX = left + (right - left) * 0.2F;
        float tipX = anchorX + (right - left) * 0.5F * progress;
        float top = area.y + area.h * 0.22F;

        this.rope(batcher, anchorX, top, tipX, top + area.h * 0.28F, area.h * (0.05F + 0.25F * progress), WEB, 24);
        this.anchor(batcher, anchorX, top);

        /* Timeline */
        batcher.box(left, trackY, right, trackY + 4F, 0x88000000);

        for (int i = 0; i <= 4; i++)
        {
            float x = left + (right - left) * (i / 4F);

            batcher.box(x - 2F, trackY - 2F, x + 2F, trackY + 6F, ACCENT);
        }

        batcher.box(head - 1F, trackY - 8F, head + 1F, trackY + 12F, Colors.A100 | Colors.CURSOR);
    }

    private void drawRecipes(Batcher2D batcher, Area area, float time)
    {
        float half = area.w * 0.5F;
        float swing = (float) Math.sin(time * 1.3F);

        /* Hanging spider */
        float sx = area.x + half * 0.5F;

        this.rope(batcher, sx, area.y + 8F, sx + swing * 4F, area.y + area.h * 0.55F, 0F, WEB, 16);
        batcher.box(sx + swing * 4F - 4F, area.y + area.h * 0.55F, sx + swing * 4F + 4F, area.y + area.h * 0.55F + 6F, WEIGHT);

        /* Web between two walls */
        float wx1 = area.x + half + 14F;
        float wx2 = area.ex() - 14F;
        float wy = area.y + area.h * 0.3F;

        this.wall(batcher, wx1, wy);
        this.wall(batcher, wx2, wy + 6F);

        for (int i = -1; i <= 1; i++)
        {
            this.rope(batcher, wx1, wy, wx2, wy + 6F, area.h * 0.16F + i * 2F, i == 0 ? WEB : WEB_DIM, 22);
        }
    }

    private void drawProblems(Batcher2D batcher, Area area, float time)
    {
        float half = area.w * 0.5F;
        float top = area.y + area.h * 0.24F;

        /* Jittering rope */
        for (int i = 0; i <= 20; i++)
        {
            float progress = i / 20F;
            float x = area.x + half * (0.2F + 0.6F * progress);
            float noise = (float) Math.sin(time * 40F + i * 2.7F) * 3F * (float) Math.sin(Math.PI * progress);
            float y = top + area.h * 0.35F * (float) Math.sin(Math.PI * progress) + noise;

            batcher.box(x - 1F, y - 1F, x + 1F, y + 1F, 0xffff8866);
        }

        /* Calm rope */
        this.rope(batcher, area.x + half * 1.2F, top, area.x + half * 1.8F, top, area.h * 0.35F, 0xff88ff99, 22);

        batcher.text("!", (int) (area.x + half * 0.48F), (int) (area.ey() - 14), 0xffff8866, true);
        batcher.text("OK", (int) (area.x + half * 1.44F), (int) (area.ey() - 14), 0xff88ff99, true);
    }

    /* ---- Primitives ---------------------------------------------------- */

    private void rope(Batcher2D batcher, float x1, float y1, float x2, float y2, float sag, int color, int points)
    {
        for (int i = 0; i <= points; i++)
        {
            float progress = i / (float) points;
            float x = x1 + (x2 - x1) * progress;
            float y = y1 + (y2 - y1) * progress + (float) Math.sin(Math.PI * progress) * sag;

            batcher.box(x - 1F, y - 1F, x + 1F, y + 1F, color);
        }
    }

    private void anchor(Batcher2D batcher, float x, float y)
    {
        batcher.box(x - 3F, y - 3F, x + 3F, y + 3F, ANCHOR);
        batcher.outline(x - 3F, y - 3F, x + 3F, y + 3F, 0x55000000);
    }

    private void wall(Batcher2D batcher, float x, float y)
    {
        batcher.box(x - 4F, y - 8F, x + 4F, y + 8F, WALL);
        batcher.outline(x - 4F, y - 8F, x + 4F, y + 8F, 0x55000000);
    }

    private int width(String text)
    {
        return Batcher2D.getDefaultTextRenderer().getWidth(text);
    }
}
