package com.lu.tvime;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;

/**
 * 毛玻璃风格（半透明渐变 + 细亮边 + 顶部高光）。
 * 电视多数是安卓 5~9，没有系统级背景模糊，所以用半透明层次来模拟；安卓 12+ 另外会尝试系统模糊。
 */
final class Glass {

    static final int NORMAL = 0, SPECIAL = 1, ON = 2, SEL = 3;

    static final int TEXT = 0xFFF2F6FB;
    static final int TEXT_SEL = 0xFF0A2A3B;

    private static final Paint FILL = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Paint EDGE = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final RectF TMP = new RectF();

    private Glass() {
    }

    static int dp(Context c, float v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, c.getResources().getDisplayMetrics());
    }

    /** 整个输入法的底板：上圆角、深色半透明渐变、细亮边。 */
    static Drawable bar(Context c) {
        float r = dp(c, 16);
        GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xCC2A3C5A, 0xE00C1424});
        d.setCornerRadii(new float[]{r, r, r, r, 0, 0, 0, 0});
        d.setStroke(dp(c, 1), 0x44FFFFFF);
        return d;
    }

    /** 一块磨砂卡片（二维码栏用）。 */
    static Drawable card(Context c, float radiusDp) {
        GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x40FFFFFF, 0x16FFFFFF});
        d.setCornerRadius(dp(c, radiusDp));
        d.setStroke(dp(c, 1), 0x3CFFFFFF);
        return d;
    }

    /** 二维码底（必须是纯白才好扫）。 */
    static Drawable white(Context c, float radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(0xFFFFFFFF);
        d.setCornerRadius(dp(c, radiusDp));
        return d;
    }

    /** 画一个玻璃按键；state 见上面的常量。 */
    static void key(Canvas cv, RectF rf, int state) {
        float w = rf.width();
        float h = rf.height();
        float rad = h * 0.22f;
        float sw = Math.max(1f, h * 0.025f);
        int top;
        int bot;
        int edge;
        switch (state) {
            case SEL:
                top = 0xF4EAF8FF;
                bot = 0xE48FD6F7;
                edge = 0xFFFFFFFF;
                break;
            case ON:
                top = 0xD070C874;
                bot = 0xA6379A3C;
                edge = 0x88CFF5D0;
                break;
            case SPECIAL:
                top = 0x30B8C8E4;
                bot = 0x12B8C8E4;
                edge = 0x2CFFFFFF;
                break;
            default:
                top = 0x48FFFFFF;
                bot = 0x1CFFFFFF;
                edge = 0x34FFFFFF;
        }
        if (state == SEL) {
            // 选中：外面再套一圈淡青色光晕
            float halo = h * 0.055f;
            EDGE.setStyle(Paint.Style.STROKE);
            EDGE.setStrokeWidth(halo);
            EDGE.setColor(0x906EC8F5);
            TMP.set(rf.left - halo * 0.5f, rf.top - halo * 0.5f, rf.right + halo * 0.5f, rf.bottom + halo * 0.5f);
            cv.drawRoundRect(TMP, rad + halo * 0.5f, rad + halo * 0.5f, EDGE);
        }
        cv.save();
        cv.translate(rf.left, rf.top);
        FILL.setStyle(Paint.Style.FILL);
        FILL.setShader(new LinearGradient(0, 0, 0, h, top, bot, Shader.TileMode.CLAMP));
        TMP.set(0, 0, w, h);
        cv.drawRoundRect(TMP, rad, rad, FILL);
        // 顶部高光
        FILL.setShader(new LinearGradient(0, 0, 0, h * 0.5f, 0x2EFFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
        TMP.set(sw, sw, w - sw, h * 0.5f);
        cv.drawRoundRect(TMP, rad, rad, FILL);
        FILL.setShader(null);
        // 细亮边
        EDGE.setStyle(Paint.Style.STROKE);
        EDGE.setStrokeWidth(state == SEL ? sw * 1.6f : sw);
        EDGE.setColor(edge);
        TMP.set(sw * 0.5f, sw * 0.5f, w - sw * 0.5f, h - sw * 0.5f);
        cv.drawRoundRect(TMP, rad, rad, EDGE);
        cv.restore();
    }

    /** 文字太宽就缩小，保证不超出按键。 */
    static void fit(Paint p, String s, float size, float maxW) {
        p.setTextSize(size);
        float tw = p.measureText(s);
        if (tw > maxW && tw > 0) {
            p.setTextSize(size * maxW / tw);
        }
    }
}
