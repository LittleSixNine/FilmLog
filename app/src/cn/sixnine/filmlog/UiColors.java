package cn.sixnine.filmlog;

import android.content.res.Resources;

/** Existing hand-drawn screens share Android day/night resources; image pixels and saved colors never pass here. */
final class UiColors {
    static int onAccent(Resources resources){return resources.getColor(R.color.ui_on_accent,null);}
    static int color(Resources resources,int original){
        int resource;
        switch(original&0xffffff){
            case 0x21170f:
            case 0x1f1612:
                resource=R.color.ui_background;break;
            case 0x30251e:
            case 0x261d18:
            case 0x2c211a:
            case 0x2c241c:
                resource=R.color.ui_card;break;
            case 0x453933:
            case 0x352820:
            case 0x3d2e24:
            case 0x3c3027:
            case 0x4a3c32:
            case 0x39291e:
                resource=R.color.ui_chip;break;
            case 0xf4eee2:
            case 0xf2eadf:
                resource=R.color.ui_ink;break;
            case 0xb4a99f:
            case 0xbfb3a6:
            case 0x9e948c:
            case 0x6b7280:
            case 0x625347:
                resource=R.color.ui_muted;break;
            case 0xef8249:
            case 0xf07f4a:
            case 0xe8906a:
            case 0xffa23a:
            case 0xd35f2e:
            case 0xa35d36:
                resource=R.color.ui_accent;break;
            case 0x554237:
            case 0x49372b:
            case 0x805039:
            case 0x574940:
                resource=R.color.ui_outline;break;
            case 0x39251b:
            case 0x503326:
            case 0x2a160c:
            case 0x372319:
            case 0x402b21:
            case 0x4a2c1e:
            case 0x40311c:
                resource=R.color.ui_selected;break;
            case 0x17100c:
            case 0x180f0a:
                resource=R.color.ui_deep;break;
            case 0x1a0f00:
                resource=R.color.ui_on_accent;break;
            case 0x342025:
                resource=R.color.ui_error_bg;break;
            case 0xef8994:
                resource=R.color.ui_error;break;
            case 0x80434c:
                resource=R.color.ui_error_outline;break;
            case 0x82bf9b:
                resource=R.color.ui_green;break;
            case 0x9bbce3:
                resource=R.color.ui_blue;break;
            case 0x8ac7cd:
                resource=R.color.ui_cyan;break;
            case 0x302f28:
                resource=R.color.ui_tool_bg;break;
            default:return original;
        }
        return (original&0xff000000)|(resources.getColor(resource,null)&0xffffff);
    }
}
