package com.lu.tvime;

import android.graphics.Bitmap;
import android.inputmethodservice.InputMethodService;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

/**
 * 电视输入法（毛玻璃风格）：
 *  1) 左侧九宫格数字键盘（789/456/123/删除0回车），中间主键盘（英文/符号），遥控器方向键+确定键打字；
 *     焦点只在其中一栏亮高亮，左右键走到边上再按一次就跨栏；
 *  2) 右侧常驻二维码栏：手机扫码后在手机上打字（含中文），电视输入框实时同步，不需要 ADB，也不用移焦点去呼出。
 */
public class ImeService extends InputMethodService {

    static volatile ImeService instance;
    static final Handler MAIN = new Handler(Looper.getMainLooper());

    private KbView kb;
    private NumPad numpad;
    private ImageView qr;
    private TextView urlView;
    private TextView stateView;
    private long phoneSeen;
    private String qrDrawn;
    /** 遥控器焦点是否在左侧九宫格上（false = 在右侧主键盘）。 */
    private boolean numpadFocus;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        WebServer.start(this);
    }

    @Override
    public void onDestroy() {
        if (instance == this) {
            instance = null;
        }
        WebServer.stop();
        super.onDestroy();
    }

    private int dp(int v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }

    @Override
    public View onCreateInputView() {
        int p = dp(10);
        FrameLayout root = new FrameLayout(this);
        root.setBackground(Glass.bar(this));
        root.setPadding(p, p, p, p);
        if (Build.VERSION.SDK_INT >= 31) {
            // 安卓 12+ 的电视/盒子：让系统真的把键盘后面的画面模糊掉（不支持就忽略）
            try {
                getWindow().getWindow().setBackgroundBlurRadius(dp(28));
            } catch (Throwable ignored) {
            }
        }

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);

        // 左侧：九宫格数字键盘
        numpad = new NumPad(this);
        numpad.setListener(new NumPad.Listener() {
            @Override
            public void onKey(NumPad.Key k) {
                onNumPadKey(k);
            }
        });
        numpad.setTouchHook(new Runnable() {
            @Override
            public void run() {
                setFocus(true);
            }
        });
        bar.addView(numpad, new LinearLayout.LayoutParams(dp(190), dp(KbView.HEIGHT_DP)));

        // 中间：主键盘
        kb = new KbView(this);
        kb.setListener(new KbView.Listener() {
            @Override
            public void onKey(KbView.Key k, boolean shift) {
                onScreenKey(k, shift);
            }
        });
        kb.setTouchHook(new Runnable() {
            @Override
            public void run() {
                setFocus(false);
            }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp.leftMargin = dp(10);
        bar.addView(kb, lp);

        // 右侧：常驻二维码栏（不参与遥控器焦点，所以扫码不用挪光标）
        int qrW = getResources().getConfiguration().screenWidthDp >= 900 ? 160 : 136;
        LinearLayout.LayoutParams qlp = new LinearLayout.LayoutParams(dp(qrW), dp(KbView.HEIGHT_DP));
        qlp.leftMargin = dp(10);
        bar.addView(buildQrPanel(qrW), qlp);

        root.addView(bar, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        setFocus(false);
        refreshLabels();
        return root;
    }

    private LinearLayout buildQrPanel(int widthDp) {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setGravity(Gravity.CENTER);
        panel.setBackground(Glass.card(this, 14));
        panel.setPadding(dp(8), dp(8), dp(8), dp(8));

        TextView t = new TextView(this);
        t.setText("手机扫码输入");
        t.setTextColor(Glass.TEXT);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        t.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, 0, 0, dp(5));
        panel.addView(t);

        int box = widthDp - 24;
        qr = new ImageView(this);
        qr.setBackground(Glass.white(this, 8));
        qr.setPadding(dp(7), dp(7), dp(7), dp(7));
        qr.setScaleType(ImageView.ScaleType.FIT_XY);
        panel.addView(qr, new LinearLayout.LayoutParams(dp(box), dp(box)));

        urlView = new TextView(this);
        urlView.setTextColor(0xFFB4C2D6);
        urlView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 9);
        urlView.setGravity(Gravity.CENTER);
        urlView.setSingleLine(true);
        urlView.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        urlView.setPadding(0, dp(5), 0, 0);
        panel.addView(urlView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        stateView = new TextView(this);
        stateView.setTextColor(0xFF7BD88F);
        stateView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        stateView.setGravity(Gravity.CENTER);
        stateView.setSingleLine(true);
        panel.addView(stateView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        return panel;
    }

    /** 焦点在九宫格（true）还是主键盘（false）；只有焦点所在那一栏画高亮。 */
    private void setFocus(boolean onNumpad) {
        numpadFocus = onNumpad;
        if (numpad != null) {
            numpad.setActive(onNumpad);
        }
        if (kb != null) {
            kb.setActive(!onNumpad);
        }
    }

    @Override
    public boolean onEvaluateInputViewShown() {
        return true;   // 电视遥控器常被当成实体键盘，默认会把软键盘藏起来
    }

    @Override
    public boolean onEvaluateFullscreenMode() {
        return false;
    }

    @Override
    public void onStartInputView(EditorInfo info, boolean restarting) {
        super.onStartInputView(info, restarting);
        WebServer.start(this);
        if (!restarting && kb != null) {
            kb.reset();
            setFocus(false);
        }
        refreshLabels();
        // 网页服务在后台线程里绑端口，端口可能稍后才定；过一会儿再核对一次二维码
        MAIN.postDelayed(new Runnable() {
            @Override
            public void run() {
                refreshLabels();
            }
        }, 1000);
    }

    /** 刷新二维码栏：地址变了才重画二维码（同一个地址不重复生成）。 */
    private void refreshLabels() {
        if (urlView == null || qr == null) {
            return;
        }
        String url = Net.url(this);
        if (url == null) {
            urlView.setText("电视没有联网");
            stateView.setText("");
            qr.setImageBitmap(null);
            qrDrawn = null;
            return;
        }
        urlView.setText(url.replaceAll("\\?k=.*$", "").replace("http://", ""));
        stateView.setText(phoneSeen == 0 ? "等待手机..." : "手机已连接");
        if (!url.equals(qrDrawn)) {
            int px = qr.getLayoutParams().width - qr.getPaddingLeft() - qr.getPaddingRight();
            Bitmap b = Net.qr(url, Math.max(px, 120));
            qr.setImageBitmap(b);
            qrDrawn = b == null ? null : url;
        }
    }

    void setState(String s) {
        phoneSeen = System.currentTimeMillis();
        if (stateView != null) {
            stateView.setText(s);
        }
    }

    // ---------- 遥控器按键 ----------

    private static boolean isNav(int c) {
        return c == KeyEvent.KEYCODE_DPAD_LEFT || c == KeyEvent.KEYCODE_DPAD_RIGHT
                || c == KeyEvent.KEYCODE_DPAD_UP || c == KeyEvent.KEYCODE_DPAD_DOWN
                || c == KeyEvent.KEYCODE_DPAD_CENTER || c == KeyEvent.KEYCODE_ENTER
                || c == KeyEvent.KEYCODE_NUMPAD_ENTER;
    }

    @Override
    public boolean onKeyDown(int code, KeyEvent ev) {
        if (!isInputViewShown()) {
            return super.onKeyDown(code, ev);
        }
        if (kb != null && numpad != null && isNav(code)) {
            // 左右键在「九宫格」和「主键盘」之间换焦点：走到边上再按一次就跨栏
            if (code == KeyEvent.KEYCODE_DPAD_LEFT && !numpadFocus && kb.atLeftEdge()) {
                numpad.focusRow(kb.row());
                setFocus(true);
                return true;
            }
            if (code == KeyEvent.KEYCODE_DPAD_RIGHT && numpadFocus && numpad.atRightEdge()) {
                kb.focusRow(numpad.rowIndex());
                setFocus(false);
                return true;
            }
            return numpadFocus ? numpad.onRemoteKey(code, ev.getRepeatCount())
                    : kb.onRemoteKey(code, ev.getRepeatCount());
        }
        return super.onKeyDown(code, ev);
    }

    @Override
    public boolean onKeyUp(int code, KeyEvent ev) {
        if (kb != null && numpad != null && isInputViewShown() && isNav(code)) {
            return true;
        }
        return super.onKeyUp(code, ev);
    }

    // ---------- 左侧数字键盘 ----------

    private void onNumPadKey(NumPad.Key k) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) {
            return;
        }
        switch (k.type) {
            case NumPad.NUM:
                ic.finishComposingText();
                ic.commitText(k.out, 1);
                break;
            case NumPad.DEL:
                ic.finishComposingText();
                sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL);
                break;
            case NumPad.ENTER:
                ic.finishComposingText();
                enter();
                break;
            default:
        }
    }

    // ---------- 屏幕键盘输入 ----------

    private void onScreenKey(KbView.Key k, boolean shift) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) {
            return;
        }
        switch (k.type) {
            case KbView.CHAR:
                ic.finishComposingText();
                String s = k.out;
                if (shift && s.length() == 1) {
                    s = s.toUpperCase();
                }
                ic.commitText(s, 1);
                break;
            case KbView.SPACE:
                ic.finishComposingText();
                ic.commitText(" ", 1);
                break;
            case KbView.DEL:
                ic.finishComposingText();
                sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL);
                break;
            case KbView.ENTER:
                ic.finishComposingText();
                enter();
                break;
            case KbView.CLEAR:
                clearAll();
                break;
            case KbView.HIDE:
                requestHideSelf(0);
                break;
            default:
        }
    }

    private void enter() {
        EditorInfo ei = getCurrentInputEditorInfo();
        int action = ei == null ? EditorInfo.IME_ACTION_NONE : (ei.imeOptions & EditorInfo.IME_MASK_ACTION);
        boolean noEnter = ei != null && (ei.imeOptions & EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0;
        InputConnection ic = getCurrentInputConnection();
        if (ic != null && !noEnter && action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED) {
            ic.performEditorAction(action);
        } else {
            sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER);
        }
    }

    private void clearAll() {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) {
            return;
        }
        ic.beginBatchEdit();
        ic.finishComposingText();
        ic.performContextMenuAction(android.R.id.selectAll);
        ic.commitText("", 1);
        ic.endBatchEdit();
    }

    // ---------- 手机（HTTP）发来的操作 ----------

    /** 在界面线程执行；返回 "ok" 或失败原因。 */
    static String phoneAct(final String act, final String text, final int code) {
        try {
            if (Looper.myLooper() == Looper.getMainLooper()) {
                return doPhoneAct(act, text, code);
            }
            FutureTask<String> ft = new FutureTask<String>(new Callable<String>() {
                @Override
                public String call() {
                    return doPhoneAct(act, text, code);
                }
            });
            MAIN.post(ft);
            return ft.get(5, TimeUnit.SECONDS);
        } catch (Throwable e) {
            return "执行失败：" + e.getMessage();
        }
    }

    private static String doPhoneAct(String act, String text, int code) {
        ImeService s = instance;
        if (s == null) {
            return "电视输入法没有在运行：请先在电视上把「电视输入法」设为当前输入法";
        }
        InputConnection ic = s.getCurrentInputConnection();
        if (ic == null) {
            return "电视上没有处于输入状态的输入框：先在电视上点开要输入的框";
        }
        if ("live".equals(act)) {
            // 手机上边打边同步：作为"组合中的文字"，随时被下一次覆盖，点发送时才定稿
            ic.setComposingText(text, 1);
            s.setState("手机正在输入...");
        } else if ("text".equals(act)) {
            ic.commitText(text, 1);
            s.setState("已发送");
        } else if ("del".equals(act)) {
            ic.finishComposingText();
            s.sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL);
        } else if ("clear".equals(act)) {
            s.clearAll();
        } else if ("enter".equals(act)) {
            ic.finishComposingText();
            s.enter();
        } else if ("key".equals(act)) {
            s.sendDownUpKeyEvents(code);
        } else {
            return "未知操作 " + act;
        }
        return "ok";
    }
}
