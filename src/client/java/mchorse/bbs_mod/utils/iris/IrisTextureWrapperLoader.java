package mchorse.bbs_mod.utils.iris;

import mchorse.bbs_mod.graphics.texture.FormMaterials;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.utils.StringUtils;
import mchorse.bbs_mod.utils.resources.FilteredLink;
import mchorse.bbs_mod.utils.resources.MultiLink;
import net.irisshaders.iris.targets.backed.NativeImageBackedSingleColorTexture;
import net.irisshaders.iris.texture.pbr.PBRType;
import net.irisshaders.iris.texture.pbr.loader.PBRTextureLoader;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.resource.ResourceManager;

public class IrisTextureWrapperLoader implements PBRTextureLoader
{
    public NativeImageBackedSingleColorTexture defaultNormalTexture;
    public NativeImageBackedSingleColorTexture defaultSpecularTexture;

    @Override
    public void load(AbstractTexture abstractTexture, ResourceManager resourceManager, PBRTextureConsumer pbrTextureConsumer)
    {
        if (this.defaultSpecularTexture == null)
        {
            this.defaultNormalTexture = new NativeImageBackedSingleColorTexture(PBRType.NORMAL.getDefaultValue());
            this.defaultSpecularTexture = new NativeImageBackedSingleColorTexture(PBRType.SPECULAR.getDefaultValue());
        }

        if (abstractTexture instanceof IrisTextureWrapper wrapper)
        {
            Link key = wrapper.texture;
            Link normalKey = this.createPrefixedCopy(key, "_n.png");
            Link specularKey = this.createPrefixedCopy(key, "_s.png");

            /* The Material tab's sliders win over the file-based companions: when a
             * form rendered with this texture has them set, FormMaterials serves a
             * synthesized LabPBR map for it; otherwise the _n/_s files apply as usual. */
            pbrTextureConsumer.acceptNormalTexture(new IrisTextureWrapper(normalKey, this.defaultNormalTexture, wrapper.index)
            {
                @Override
                public int getGlId()
                {
                    int id = FormMaterials.getNormalId(key);

                    return id >= 0 ? id : super.getGlId();
                }
            });
            pbrTextureConsumer.acceptSpecularTexture(new IrisTextureWrapper(specularKey, this.defaultSpecularTexture, wrapper.index)
            {
                @Override
                public int getGlId()
                {
                    int id = FormMaterials.getSpecularId(key);

                    return id >= 0 ? id : super.getGlId();
                }
            });
        }
    }

    private Link createPrefixedCopy(Link link, String suffix)
    {
        /* If given texture is a multi-link, then let's copy it and replace any of the normal
         * textures with appropriate suffixes */
        if (link instanceof MultiLink multiLink)
        {
            MultiLink newMultiLink = (MultiLink) multiLink.copy();

            for (FilteredLink child : newMultiLink.children)
            {
                if (child.path != null)
                {
                    child.path = this.createPrefixedCopy(child.path, suffix);
                }
            }

            return newMultiLink;
        }

        return new Link(link.source, StringUtils.removeExtension(link.path) + suffix);
    }
}