package mchorse.bbs_mod.camera.clips.misc;

import mchorse.bbs_mod.camera.clips.CameraClip;
import mchorse.bbs_mod.camera.data.Position;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.utils.clips.Clip;
import mchorse.bbs_mod.utils.clips.ClipContext;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.keyframes.factories.KeyframeFactories;

import java.util.ArrayList;
import java.util.List;

/**
 * Camera clip that lays an animated photo over the film's frame.
 *
 * <p>While the clip plays, it contributes one photo layer whose texture,
 * opacity, placement, stretching and rotation are all keyframed over the
 * clip's span. The texture channel steps through numbered files the same way
 * billboard textures do: keyframe {@code photo_1.png} at the start and
 * {@code photo_20.png} at the end, and every numbered photo in between shows
 * up in order, paced by how far apart the keyframes sit. The client's film
 * effects draw these layers on top of the regular photo layer stack, in
 * preview and export alike.</p>
 */
public class PhotoClip extends CameraClip
{
    public final KeyframeChannel<Link> texture = new KeyframeChannel<>("texture", KeyframeFactories.LINK);
    public final KeyframeChannel<Double> opacity = new KeyframeChannel<>("opacity", KeyframeFactories.DOUBLE);
    public final KeyframeChannel<Double> x = new KeyframeChannel<>("x", KeyframeFactories.DOUBLE);
    public final KeyframeChannel<Double> y = new KeyframeChannel<>("y", KeyframeFactories.DOUBLE);
    public final KeyframeChannel<Double> scale = new KeyframeChannel<>("scale", KeyframeFactories.DOUBLE);
    public final KeyframeChannel<Double> stretchX = new KeyframeChannel<>("stretch_x", KeyframeFactories.DOUBLE);
    public final KeyframeChannel<Double> stretchY = new KeyframeChannel<>("stretch_y", KeyframeFactories.DOUBLE);
    public final KeyframeChannel<Double> rotate = new KeyframeChannel<>("rotate", KeyframeFactories.DOUBLE);
    public final KeyframeChannel<Double> flip = new KeyframeChannel<>("flip", KeyframeFactories.DOUBLE);
    public final KeyframeChannel<Double> layerMode = new KeyframeChannel<>("layer_mode", KeyframeFactories.DOUBLE);

    /** Photo layers contributed by playing photo clips this frame, in play order. */
    public static List<State> getStates(ClipContext context)
    {
        return context.clipData.get("photo_data", ArrayList::new);
    }

    public PhotoClip()
    {
        this.add(this.texture);
        this.add(this.opacity);
        this.add(this.x);
        this.add(this.y);
        this.add(this.scale);
        this.add(this.stretchX);
        this.add(this.stretchY);
        this.add(this.rotate);
        this.add(this.flip);
        this.add(this.layerMode);
    }

    @Override
    protected void applyClip(ClipContext context, Position position)
    {
        if (this.texture.isEmpty())
        {
            return;
        }

        float tick = context.relativeTick + context.transition;
        Link link = this.texture.interpolate(tick);

        if (link == null)
        {
            return;
        }

        State state = new State();

        state.texture = link.toString();
        state.opacity = (float) this.channelValue(this.opacity, tick, 1D);
        state.x = (float) this.channelValue(this.x, tick, 0D);
        state.y = (float) this.channelValue(this.y, tick, 0D);
        state.scale = (float) this.channelValue(this.scale, tick, 1D);
        state.stretchX = (float) this.channelValue(this.stretchX, tick, 1D);
        state.stretchY = (float) this.channelValue(this.stretchY, tick, 1D);
        state.rotate = (float) this.channelValue(this.rotate, tick, 0D);
        state.flip = (float) this.channelValue(this.flip, tick, 0D);
        state.layerMode = (float) this.channelValue(this.layerMode, tick, 0D);

        getStates(context).add(state);
    }

    private double channelValue(KeyframeChannel<Double> channel, float tick, double defaultValue)
    {
        Double value = channel.isEmpty() ? null : channel.interpolate(tick);

        return value == null ? defaultValue : value;
    }

    @Override
    protected Clip create()
    {
        return new PhotoClip();
    }

    /** One photo layer's worth of values, interpolated at the current tick. */
    public static class State
    {
        public String texture = "";
        public float opacity = 1F;
        public float x;
        public float y;
        public float scale = 1F;
        public float stretchX = 1F;
        public float stretchY = 1F;
        public float rotate;
        public float flip;
        public float layerMode;
    }
}
