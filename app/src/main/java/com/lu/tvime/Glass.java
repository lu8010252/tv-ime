package com.lu.tvime;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;

/**
 * 统一的视觉风格：深蓝夜色底板 + 磨砂玻璃按键。
 * 电视多数是安卓 5~9，没有系统级背景模糊，所以用「半透明层次 + 顶部高光 + 细亮边 + 底部阴影」来模拟；
 * 安卓 12+ 另外会尝试系统模糊。焦点键用亮色渐变 + 外发光，隔着客厅也能一眼看到。
 */
final class Glass {

    static final int NORMAL = 0, SPECIAL = 1, ON = 2, SEL = 3, PRIMARY = 4;

    // 调色板
    static final int TEXT = 0xFFEEF3FB;
    static final int TEXT_DIM = 0xFFAEBBD2;
    static final int TEXT_SEL = 0xFF06233A;
    static final int TEXT_ON = 0xFFFFFFFF;
    static final int TEXT_PRIMARY = 0xFF04282A;
    static final int ACCENT = 0xFF5CC8FF;
    static final int OK = 0xFF56D39A;
    static final int WARN = 0xFFFFC857;

    static final Typeface MEDIUM = Typeface.create("sans-serif-medium", Typeface.NORMAL);

    // 每种按键状态的上下渐变色、边框渐变色（下标 = 状态常量）
    private static final int[] FILL_TOP = {0xE6394967, 0xE62B3752, 0xF08A7BFF, 0xFFF7FCFF, 0xF04FD8BE};
    private static final int[] FILL_BOT = {0xE62A3752, 0xE61F2940, 0xF0604FDB, 0xFFA6E0FF, 0xF02AA891};
    private static final int[] EDGE_TOP = {0x4CFFFFFF, 0x2EFFFFFF, 0xA0E6E1FF, 0xFFFFFFFF, 0x9EE6FFF8};
    private static final int[] EDGE_BOT = {0x14FFFFFF, 0x0CFFFFFF, 0x40B9AFFF, 0xB0DFF4FF, 0x3C9CF0E2};

    // 焦点键的外发光：由外到内三层，越往里越亮
    private static final float[] GLOW_SPREAD = {0.20f, 0.12f, 0.06f};
    private static final int[] GLOW_ALPHA = {0x1C, 0x30, 0x58};

    private static final Paint FILL = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Paint EDGE = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final RectF TMP = new RectF();

    private Glass() {
    }

    static int dp(Context c, float v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, c.getResources().getDisplayMetrics());
    }

    static int textColor(int state) {
        switch (state) {
            case SEL:
                return TEXT_SEL;
            case ON:
                return TEXT_ON;
            case PRIMARY:
                return TEXT_PRIMARY;
            case SPECIAL:
                return TEXT_DIM;
            default:
                return TEXT;
        }
    }

    /** 整个输入法的底板：上圆角、深蓝渐变、左右两团柔光、顶部一道渐变细线。 */
    static Drawable bar(Context c) {
        return new BarDrawable(dp(c, 20), Math.max(2, dp(c, 2)), Math.max(1, dp(c, 1)));
    }

    /** 一块磨砂卡片（二维码栏用）。 */
    static Drawable card(Context c, float radiusDp) {
        GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x2E9DB4E0, 0x129DB4E0});
        d.setCornerRadius(dp(c, radiusDp));
        d.setStroke(dp(c, 1), 0x34FFFFFF);
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
        float rad = h * 0.24f;
        float sw = Math.max(1f, h * 0.022f);
        FILL.setShader(null);
        FILL.setStyle(Paint.Style.FILL);
        if (state == SEL) {
            // 选中：外面套几层淡青色光晕
            for (int i = 0; i < GLOW_SPREAD.length; i++) {
                float s = h * GLOW_SPREAD[i];
                FILL.setColor((GLOW_ALPHA[i] << 24) | 0x5CC8FF);
                TMP.set(rf.left - s, rf.top - s, rf.right + s, rf.bottom + s);
                cv.drawRoundRect(TMP, rad + s, rad + s, FILL);
            }
        } else {
            // 其他键：底部一点柔和阴影，有浮起来的感觉
            FILL.setColor(state == SPECIAL ? 0x40000000 : 0x5A000000);
            float dy = h * 0.05f;
            TMP.set(rf.left, rf.top + dy, rf.right, rf.bottom + dy);
            cv.drawRoundRect(TMP, rad, rad, FILL);
        }
        cv.save();
        cv.translate(rf.left, rf.top);
        FILL.setShader(new LinearGradient(0, 0, 0, h, FILL_TOP[state], FILL_BOT[state], Shader.TileMode.CLAMP));
        TMP.set(0, 0, w, h);
        cv.drawRoundRect(TMP, rad, rad, FILL);
        if (state != SEL) {
            // 顶部高光
            FILL.setShader(new LinearGradient(0, 0, 0, h * 0.5f, 0x2CFFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
            TMP.set(sw, sw, w - sw, h * 0.5f);
            cv.drawRoundRect(TMP, rad, rad, FILL);
        }
        FILL.setShader(null);
        // 上亮下暗的细边，像玻璃的切边
        EDGE.setStyle(Paint.Style.STROKE);
        EDGE.setStrokeWidth(state == SEL ? sw * 1.5f : sw);
        EDGE.setShader(new LinearGradient(0, 0, 0, h, EDGE_TOP[state], EDGE_BOT[state], Shader.TileMode.CLAMP));
        TMP.set(sw * 0.5f, sw * 0.5f, w - sw * 0.5f, h - sw * 0.5f);
        cv.drawRoundRect(TMP, rad, rad, EDGE);
        EDGE.setShader(null);
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

    /** 底板：只在顶部有圆角，所以自己画。 */
    private static final class BarDrawable extends Drawable {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        private final RectF box = new RectF();
        private final float r;
        private final float line;
        private final float stroke;

        BarDrawable(float r, float line, float stroke) {
            this.r = r;
            this.line = line;
            this.stroke = stroke;
        }

        @Override
        public void draw(Canvas cv) {
            Rect b = getBounds();
            float w = b.width();
            if (w <= 0 || b.height() <= 0) {
                return;
            }
            box.set(b.left, b.top, b.right, b.bottom);
            path.reset();
            path.addRoundRect(box, new float[]{r, r, r, r, 0, 0, 0, 0}, Path.Direction.CW);

            p.setStyle(Paint.Style.FILL);
            p.setShader(new LinearGradient(0, b.top, 0, b.bottom, 0xF01C2A46, 0xF60A1121, Shader.TileMode.CLAMP));
            cv.drawPath(path, p);
            // 左上青、右上紫的两团柔光
            p.setShader(new RadialGradient(b.left + w * 0.14f, b.top, w * 0.46f,
                    0x305CC8FF, 0x005CC8FF, Shader.TileMode.CLAMP));
            cv.drawPath(path, p);
            p.setShader(new RadialGradient(b.right - w * 0.10f, b.top, w * 0.40f,
                    0x2CA98BFF, 0x00A98BFF, Shader.TileMode.CLAMP));
            cv.drawPath(path, p);
            // 整圈细亮边
            p.setShader(null);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(stroke);
            p.setColor(0x2EFFFFFF);
            cv.drawPath(path, p);
            // 顶部渐变细线
            p.setStyle(Paint.Style.FILL);
            p.setShader(new LinearGradient(b.left + r, 0, b.right - r, 0,
                    new int[]{0x005CC8FF, 0xCC5CC8FF, 0xCCA98BFF, 0x00A98BFF},
                    new float[]{0f, 0.3f, 0.7f, 1f}, Shader.TileMode.CLAMP));
            cv.drawRect(b.left + r, b.top, b.right - r, b.top + line, p);
            p.setShader(null);
        }

        @Override
        public void setAlpha(int alpha) {
        }

        @Override
        public void setColorFilter(ColorFilter cf) {
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }
}
