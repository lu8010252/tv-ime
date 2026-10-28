package com.lu.tvime;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/**
 * 遥控器用的屏幕键盘：英文字母、数字、符号。方向键移动高亮，确定键输入。
 * 自己画、自己管高亮（输入法窗口拿不到焦点，方向键由输入法服务的 onKeyDown 转进来）。
 */
final class KbView extends View {

    static final int CHAR = 0, SHIFT = 1, DEL = 2, SPACE = 3, ENTER = 4, MODE = 5, HIDE = 6, CLEAR = 7;

    static final int ROW_DP = 50;
    /** 固定 5 行高，和左侧数字键盘等高；字母页只有 4 行，每行会自动变高，切页不跳高度。 */
    static final int HEIGHT_DP = ROW_DP * 5;

    static final class Key {
        final String label;     // 显示
        final String out;       // 输出（CHAR 用）
        final int type;
        final float w;

        Key(String label, String out, int type, float w) {
            this.label = label;
            this.out = out;
            this.type = type;
            this.w = w;
        }
    }

    interface Listener {
        void onKey(Key k, boolean shift);
    }

    private final List<List<Key>> letters = new ArrayList<List<Key>>();
    private final List<List<Key>> symbols = new ArrayList<List<Key>>();
    private List<List<Key>> cur;
    private boolean symbolMode;
    private boolean shift;
    private int selRow = 2;
    private int selCol = 0;
    private Listener listener;

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint txt = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rf = new RectF();

    KbView(Context c) {
        super(c);
        txt.setTextAlign(Paint.Align.CENTER);
        txt.setTypeface(Typeface.DEFAULT_BOLD);
        setFocusable(false);
        build();
        cur = letters;
        selectStart();
    }

    void setListener(Listener l) {
        listener = l;
    }

    private static Key c(String s) {
        return new Key(s, s, CHAR, 1f);
    }

    private static List<Key> chars(String s) {
        List<Key> r = new ArrayList<Key>();
        for (int i = 0; i < s.length(); i++) {
            r.add(c(String.valueOf(s.charAt(i))));
        }
        return r;
    }

    private void build() {
        List<Key> r;
        // ---- 字母（数字交给左侧九宫格键盘，这里不再放数字行）----
        r = chars("qwertyuiop");
        r.add(c("-"));
        letters.add(r);
        r = chars("asdfghjkl");
        r.add(c("_"));
        r.add(new Key("⏎", null, ENTER, 1.6f));
        letters.add(r);
        r = new ArrayList<Key>();
        r.add(new Key("⇧", null, SHIFT, 1.6f));
        r.addAll(chars("zxcvbnm,./"));
        letters.add(r);
        r = new ArrayList<Key>();
        r.add(new Key("123?", null, MODE, 1.6f));
        r.add(c("@"));
        r.add(new Key("空格", " ", SPACE, 4.6f));
        r.add(new Key(".com", ".com", CHAR, 1.6f));
        r.add(new Key("⌫", null, DEL, 1.4f));
        r.add(new Key("清空", null, CLEAR, 1.4f));
        r.add(new Key("收起", null, HIDE, 1.4f));
        letters.add(r);
        // ---- 符号（数字同上，交给左侧九宫格）----
        r = chars("!@#$%^&*()");
        r.add(c("-"));
        symbols.add(r);
        r = chars("~+={}[]|\\");
        r.add(c("_"));
        r.add(new Key("⏎", null, ENTER, 1.6f));
        symbols.add(r);
        r = chars(":;\"'<>?/,.");
        r.add(c("`"));
        symbols.add(r);
        r = new ArrayList<Key>();
        r.add(new Key("abc", null, MODE, 1.6f));
        r.add(c("@"));
        r.add(new Key("空格", " ", SPACE, 4.6f));
        r.add(new Key("http://", "http://", CHAR, 1.8f));
        r.add(new Key("⌫", null, DEL, 1.4f));
        r.add(new Key("清空", null, CLEAR, 1.4f));
        r.add(new Key("收起", null, HIDE, 1.4f));
        symbols.add(r);
    }

    private void selectStart() {
        selRow = 0;
        selCol = 0;
    }

    /** 每次进入输入框重置；数字输入直接走左侧九宫格，这里固定回到字母页。 */
    void reset() {
        shift = false;
        symbolMode = false;
        cur = letters;
        selRow = 0;
        selCol = 0;
        invalidate();
    }

    // ---------- 跨栏切换焦点（由 ImeService 调用）----------

    int row() {
        return selRow;
    }

    void focusRow(int r) {
        selRow = Math.max(0, Math.min(r, cur.size() - 1));
        selCol = Math.min(selCol, cur.get(selRow).size() - 1);
    }

    boolean atLeftEdge() {
        return selCol == 0;
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
                // 长按只对删除键连发，其余键按一次输入一次
                Key k = cur.get(selRow).get(selCol);
                if (repeat == 0 || k.type == DEL) {
                    press(k);
                }
                return true;
            default:
                return false;
        }
    }

    private void moveH(int d) {
        List<Key> row = cur.get(selRow);
        selCol = (selCol + d + row.size()) % row.size();
        invalidate();
    }

    private float centerX(int row, int col) {
        List<Key> r = cur.get(row);
        float total = 0;
        for (Key k : r) {
            total += k.w;
        }
        float x = 0;
        for (int i = 0; i < col; i++) {
            x += r.get(i).w;
        }
        return (x + r.get(col).w / 2f) / total;
    }

    private void moveV(int d) {
        int nr = selRow + d;
        if (nr < 0 || nr >= cur.size()) {
            return;
        }
        float cx = centerX(selRow, selCol);
        List<Key> row = cur.get(nr);
        int best = 0;
        float bd = 9;
        for (int i = 0; i < row.size(); i++) {
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
        switch (k.type) {
            case SHIFT:
                shift = !shift;
                invalidate();
                break;
            case MODE:
                symbolMode = !symbolMode;
                cur = symbolMode ? symbols : letters;
                shift = false;
                selRow = Math.min(selRow, cur.size() - 1);
                selCol = Math.min(selCol, cur.get(selRow).size() - 1);
                invalidate();
                break;
            default:
                if (listener != null) {
                    listener.onKey(k, shift);
                }
                // 单次大写：输入一个字母后自动恢复小写
                if (k.type == CHAR && shift) {
                    shift = false;
                    invalidate();
                }
        }
    }

    // ---------- 触摸（手机投屏/触屏电视也能点）----------

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (e.getAction() != MotionEvent.ACTION_UP) {
            return true;
        }
        int rows = cur.size();
        int r = (int) (e.getY() / (getHeight() / (float) rows));
        if (r < 0 || r >= rows) {
            return true;
        }
        List<Key> row = cur.get(r);
        float total = 0;
        for (Key k : row) {
            total += k.w;
        }
        float x = e.getX() / getWidth() * total;
        float acc = 0;
        for (int i = 0; i < row.size(); i++) {
            acc += row.get(i).w;
            if (x <= acc) {
                selRow = r;
                selCol = i;
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
        int rows = cur.size();
        float rh = getHeight() / (float) rows;
        float gap = rh * 0.07f;
        txt.setTextSize(rh * 0.42f);
        for (int r = 0; r < rows; r++) {
            List<Key> row = cur.get(r);
            float total = 0;
            for (Key k : row) {
                total += k.w;
            }
            float unit = getWidth() / total;
            float x = 0;
            for (int i = 0; i < row.size(); i++) {
                Key k = row.get(i);
                float w = k.w * unit;
                rf.set(x + gap, r * rh + gap, x + w - gap, (r + 1) * rh - gap);
                boolean sel = r == selRow && i == selCol;
                boolean special = k.type != CHAR && k.type != SPACE;
                boolean on = k.type == SHIFT && shift;
                fill.setColor(sel ? 0xFF4FC3F7 : on ? 0xFF2E7D32 : special ? 0xFF14202F : 0xFF1B2A3E);
                cv.drawRoundRect(rf, rh * 0.18f, rh * 0.18f, fill);
                txt.setColor(sel ? 0xFF06222F : 0xFFECEFF4);
                String s = k.label;
                if (k.type == CHAR && s.length() == 1 && shift && !symbolMode) {
                    s = s.toUpperCase();
                }
                float ts = s.length() > 3 ? rh * 0.30f : rh * 0.42f;
                txt.setTextSize(ts);
                float ty = rf.centerY() - (txt.descent() + txt.ascent()) / 2f;
                cv.drawText(s, rf.centerX(), ty, txt);
                x += w;
            }
        }
    }

    @Override
    protected void onMeasure(int wSpec, int hSpec) {
        int w = MeasureSpec.getSize(wSpec);
        int rowH = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, ROW_DP, getResources().getDisplayMetrics());
        setMeasuredDimension(w, rowH * (HEIGHT_DP / ROW_DP));
    }
}
