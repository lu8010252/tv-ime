package com.lu.tvime;

import android.inputmethodservice.InputMethodService;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

/**
 * 电视输入法：
 *  1) 屏幕键盘（英文/数字/符号），遥控器方向键+确定键打字；
 *  2) 键盘左侧二维码：手机扫码后在手机上打字（含中文），电视输入框实时同步，不需要 ADB。
 */
public class ImeService extends InputMethodService {

    static volatile ImeService instance;
    static final Handler MAIN = new Handler(Looper.getMainLooper());

    private KbView kb;
    private ImageView qr;
    private TextView urlView;
    private TextView stateView;
    private long phoneSeen;

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
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.HORIZONTAL);
        root.setBackgroundColor(0xF00B1220);
        root.setGravity(Gravity.CENTER_VERTICAL);
        int p = dp(10);
        root.setPadding(p, p, p, p);

        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        left.setGravity(Gravity.CENTER_HORIZONTAL);
        qr = new ImageView(this);
        qr.setBackgroundColor(0xFFFFFFFF);
        qr.setPadding(dp(5), dp(5), dp(5), dp(5));
        left.addView(qr, new LinearLayout.LayoutParams(dp(150), dp(150)));
        TextView t = new TextView(this);
        t.setText("手机扫码输入（含中文）");
        t.setTextColor(0xFFECEFF4);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, dp(6), 0, 0);
        left.addView(t);
        urlView = new TextView(this);
        urlView.setTextColor(0xFF9AA7B8);
        urlView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        urlView.setGravity(Gravity.CENTER);
        left.addView(urlView);
        stateView = new TextView(this);
        stateView.setTextColor(0xFF66BB6A);
        stateView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        stateView.setGravity(Gravity.CENTER);
        left.addView(stateView);
        root.addView(left, new LinearLayout.LayoutParams(dp(190), ViewGroup.LayoutParams.WRAP_CONTENT));

        kb = new KbView(this);
        kb.setListener(new KbView.Listener() {
            @Override
            public void onKey(KbView.Key k, boolean shift) {
                onScreenKey(k, shift);
            }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp.leftMargin = dp(10);
        root.addView(kb, lp);
        refreshPanel();
        return root;
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
        int cls = info == null ? 0 : (info.inputType & InputType.TYPE_MASK_CLASS);
        if (!restarting && kb != null) {
            kb.reset(cls == InputType.TYPE_CLASS_NUMBER || cls == InputType.TYPE_CLASS_PHONE
                    || cls == InputType.TYPE_CLASS_DATETIME);
        }
        refreshPanel();
    }

    private void refreshPanel() {
        if (qr == null) {
            return;
        }
        String url = Net.url(this);
        if (url == null) {
            urlView.setText("电视没有联网");
            qr.setImageBitmap(null);
            return;
        }
        qr.setImageBitmap(Net.qr(url, 360));
        urlView.setText(url.replaceAll("\\?k=.*$", ""));
        stateView.setText(phoneSeen == 0 ? "等待手机…" : "手机已连接");
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
        if (kb != null && isInputViewShown() && isNav(code)) {
            return kb.onRemoteKey(code, ev.getRepeatCount());
        }
        return super.onKeyDown(code, ev);
    }

    @Override
    public boolean onKeyUp(int code, KeyEvent ev) {
        if (kb != null && isInputViewShown() && isNav(code)) {
            return true;
        }
        return super.onKeyUp(code, ev);
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
            s.setState("手机正在输入…");
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
