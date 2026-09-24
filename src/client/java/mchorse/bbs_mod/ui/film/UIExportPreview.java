package mchorse.bbs_mod.ui.film;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.utils.colors.Colors;

/**
 * A live look at the film's export texture, letterboxed into this element's area.
 * The texture is re-rendered with the active {@link mchorse.bbs_mod.client.FilmEffects}
 * every frame, so whatever the effect overlays tweak shows up here instantly.
 */
public class UIExportPreview extends UIElement
{
    @Override
    public void render(UIContext context)
    {
        Texture texture = BBSRendering.getTexture();

        context.batcher.box(this.area.x, this.area.y, this.area.ex(), this.area.ey(), Colors.A100);

        if (texture.width > 0 && texture.height > 0)
        {
            float scale = Math.min(this.area.w / (float) texture.width, this.area.h / (float) texture.height);
            int w = Math.round(texture.width * scale);
            int h = Math.round(texture.height * scale);
            int x = this.area.mx(w);
            int y = this.area.my(h);

            /* The export texture's first row is the frame's bottom, so V is flipped
             * exactly the way the film panel's own preview flips it */
            context.batcher.texturedBox(texture.id, Colors.WHITE, x, y, w, h, 0, texture.height, texture.width, 0, texture.width, texture.height);
        }

        context.batcher.outline(this.area.x, this.area.y, this.area.ex(), this.area.ey(), BBSSettings.dividerColor());

        super.render(context);
    }
}
