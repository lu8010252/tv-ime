package com.lu.tvime;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/**
 * 遥控器用的九宫格数字键盘：789 / 456 / 123 / 删除0回车，共 4 行，
 * 行高和右侧主键盘一一对齐（左右跨栏时停在同一行）。
 */
final class NumPad extends View {

    static final int NUM = 0, DEL = 1, ENTER = 2;
    private static final int COLS = 3;

    static final class Key {
        final String label;   // 显示
        final String out;     // 输出（NUM 用）
        final int type;
        final float span;     // 占几列

        Key(String label, String out, int type, float span) {
            this.label = label;
            this.out = out;
            this.type = type;
            this.span = span;
        }
    }

    interface Listener {
        void onKey(Key k);
    }

    private final List<List<Key>> rows = new ArrayList<List<Key>>();
    private int selRow = 1;
    private int selCol = 1;
    private Listener listener;
    /** 遥控器焦点在不在这一栏：不在时不画高亮。一开始焦点在主键盘上。 */
    private boolean active;
    private Runnable touchHook;

    private final Paint txt = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rf = new RectF();

    NumPad(Context c) {
        super(c);
        txt.setTextAlign(Paint.Align.CENTER);
        txt.setTypeface(Typeface.DEFAULT_BOLD);
        setFocusable(false);
        build();
    }

    void setListener(Listener l) {
        listener = l;
    }

    void setActive(boolean a) {
        if (active != a) {
            active = a;
            invalidate();
        }
    }

    /** 触屏点这一栏时通知外面把焦点切过来。 */
    void setTouchHook(Runnable r) {
        touchHook = r;
    }

    private static Key n(String s) {
        return new Key(s, s, NUM, 1f);
    }

    private void build() {
        List<Key> r = new ArrayList<Key>();
        r.add(n("7"));
        r.add(n("8"));
        r.add(n("9"));
        rows.add(r);

        r = new ArrayList<Key>();
        r.add(n("4"));
        r.add(n("5"));
        r.add(n("6"));
        rows.add(r);

        r = new ArrayList<Key>();
        r.add(n("1"));
        r.add(n("2"));
        r.add(n("3"));
        rows.add(r);

        r = new ArrayList<Key>();
        r.add(new Key("删除", null, DEL, 1f));
        r.add(n("0"));
        r.add(new Key("回车", null, ENTER, 1f));
        rows.add(r);
    }

    private List<Key> row() {
        return rows.get(selRow);
    }

    // ---------- 跨栏切换焦点（由 ImeService 调用）----------

    int rowIndex() {
        return selRow;
    }

    void focusRow(int r) {
        selRow = Math.max(0, Math.min(r, rows.size() - 1));
        selCol = Math.min(selCol, row().size() - 1);
    }

    boolean atRightEdge() {
        return selCol == row().size() - 1;
    }

    // ---------- 按键（遥控器）----------

    /** 返回 true 表示已处理。 */
    boolean onRemoteKey(int code, int repeat) {
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
                moveH(-1);
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                moveH(1);
                return true;
            case KeyEvent.KEYCODE_DPAD_UP:
                moveV(-1);
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                moveV(1);
                return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
                // 长按只对删除键连发
                Key k = row().get(selCol);
                if (repeat == 0 || k.type == DEL) {
                    press(k);
                }
                return true;
            default:
                return false;
        }
    }

    private void moveH(int d) {
        // 不环绕：左边没有东西，右边由 ImeService 判定跨栏到主键盘
        int n = row().size();
        int nc = Math.max(0, Math.min(selCol + d, n - 1));
        if (nc != selCol) {
            selCol = nc;
            invalidate();
        }
    }

    /** 某个键中心在本行总宽度里的相对位置（0..1），跨行对齐用。 */
    private float centerX(int r, int c) {
        List<Key> row = rows.get(r);
        float total = 0;
        for (Key k : row) {
            total += k.span;
        }
        float x = 0;
        for (int i = 0; i < c; i++) {
            x += row.get(i).span;
        }
        return (x + row.get(c).span / 2f) / total;
    }

    private void moveV(int d) {
        int nr = selRow + d;
        if (nr < 0 || nr >= rows.size()) {
            return;
        }
        float cx = centerX(selRow, selCol);
        List<Key> r = rows.get(nr);
        int best = 0;
        float bd = Float.MAX_VALUE;
        for (int i = 0; i < r.size(); i++) {
            float dd = Math.abs(centerX(nr, i) - cx);
            if (dd < bd) {
                bd = dd;
                best = i;
            }
        }
        selRow = nr;
        selCol = best;
        invalidate();
    }

    private void press(Key k) {
        if (listener != null) {
            listener.onKey(k);
        }
    }

    // ---------- 触摸（触屏电视/手机投屏）----------

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (e.getAction() != MotionEvent.ACTION_UP) {
            return true;
        }
        int n = rows.size();
        int r = (int) (e.getY() / (getHeight() / (float) n));
        if (r < 0 || r >= n) {
            return true;
        }
        List<Key> row = rows.get(r);
        float total = 0;
        for (Key k : row) {
            total += k.span;
        }
        float x = e.getX() / getWidth() * total;
        float acc = 0;
        for (int i = 0; i < row.size(); i++) {
            acc += row.get(i).span;
            if (x <= acc) {
                selRow = r;
                selCol = i;
                if (touchHook != null) {
                    touchHook.run();
                }
                press(row.get(i));
                invalidate();
                break;
            }
        }
        return true;
    }

    // ---------- 绘制 ----------

    @Override
    protected void onDraw(Canvas cv) {
        int n = rows.size();
        float rh = getHeight() / (float) n;
        float cu = getWidth() / (float) COLS;
        float gap = rh * 0.07f;
        for (int r = 0; r < n; r++) {
            List<Key> row = rows.get(r);
            float x = 0;
            for (int i = 0; i < row.size(); i++) {
                Key k = row.get(i);
                float w = k.span * cu;
                rf.set(x + gap, r * rh + gap, x + w - gap, (r + 1) * rh - gap);
                boolean sel = active && r == selRow && i == selCol;
                Glass.key(cv, rf, sel ? Glass.SEL : k.type != NUM ? Glass.SPECIAL : Glass.NORMAL);
                txt.setColor(sel ? Glass.TEXT_SEL : Glass.TEXT);
                String s = k.label;
                Glass.fit(txt, s, rh * (s.length() > 2 ? 0.34f : 0.44f), rf.width() * 0.88f);
                float ty = rf.centerY() - (txt.descent() + txt.ascent()) / 2f;
                cv.drawText(s, rf.centerX(), ty, txt);
                x += w;
            }
        }
    }

    @Override
    protected void onMeasure(int wSpec, int hSpec) {
        // 高度由布局参数给定（和右侧键盘等高），5 行平分
        setMeasuredDimension(MeasureSpec.getSize(wSpec), MeasureSpec.getSize(hSpec));
    }
}
