package com.lu.tvime;

import android.inputmethodservice.InputMethodService;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
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
 * 电视输入法：
 *  1) 左侧九宫格数字键盘（789/456/123/⌫0⏎），右侧主键盘（英文/符号），遥控器方向键+确定键打字；
 *  2) 二维码面板默认隐藏，按九宫格底部「扫码」键呼出：手机扫码后在手机上打字（含中文），
 *     电视输入框实时同步，不需要 ADB；按确定/返回键或点「收起」关闭。
 */
public class ImeService extends InputMethodService {

    static volatile ImeService instance;
    static final Handler MAIN = new Handler(Looper.getMainLooper());

    private KbView kb;
    private NumPad numpad;
    private LinearLayout qrPanel;
    private ImageView qr;
    private TextView urlView;
    private TextView stateView;
    private long phoneSeen;
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
        root.setBackgroundColor(0xF00B1220);
        root.setPadding(p, p, p, p);

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);

        // 左侧：九宫格数字键盘（原来二维码占的那一栏）
        numpad = new NumPad(this);
        numpad.setListener(new NumPad.Listener() {
            @Override
            public void onKey(NumPad.Key k) {
                onNumPadKey(k);
            }
        });
        bar.addView(numpad, new LinearLayout.LayoutParams(dp(190), dp(KbView.HEIGHT_DP)));

        // 右侧：主键盘
        kb = new KbView(this);
        kb.setListener(new KbView.Listener() {
            @Override
            public void onKey(KbView.Key k, boolean shift) {
                onScreenKey(k, shift);
            }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp.leftMargin = dp(10);
        bar.addView(kb, lp);

        root.addView(bar, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        // 二维码面板：默认隐藏，按「扫码」键弹出，盖住整个键盘
        qrPanel = buildQrPanel();
        qrPanel.setVisibility(View.GONE);
        root.addView(qrPanel, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                dp(KbView.HEIGHT_DP)));

        refreshLabels();
        return root;
    }

    private LinearLayout buildQrPanel() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setGravity(Gravity.CENTER);
        panel.setBackgroundColor(0xF00B1220);   // 不透明，盖住下面的键盘

        qr = new ImageView(this);
        qr.setBackgroundColor(0xFFFFFFFF);
        qr.setPadding(dp(5), dp(5), dp(5), dp(5));
        panel.addView(qr, new LinearLayout.LayoutParams(dp(140), dp(140)));

        TextView t = new TextView(this);
        t.setText("手机扫码输入（含中文）");
        t.setTextColor(0xFFECEFF4);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, dp(4), 0, 0);
        panel.addView(t);

        urlView = new TextView(this);
        urlView.setTextColor(0xFF9AA7B8);
        urlView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        urlView.setGravity(Gravity.CENTER);
        panel.addView(urlView);

        stateView = new TextView(this);
        stateView.setTextColor(0xFF66BB6A);
        stateView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        stateView.setGravity(Gravity.CENTER);
        panel.addView(stateView);

        // 触屏电视/投屏可以直接点；遥控器按确定键或返回键也能收起
        TextView close = new TextView(this);
        close.setText("收起");
        close.setTextColor(0xFF9AA7B8);
        close.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        close.setGravity(Gravity.CENTER);
        close.setPadding(dp(18), dp(8), dp(18), dp(8));
        close.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hideQr();
            }
        });
        panel.addView(close);
        return panel;
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
            numpadFocus = false;
        }
        refreshLabels();
    }

    /** 只更新二维码面板上的文字和状态；二维码图片在真正弹出面板时才画。 */
    private void refreshLabels() {
        if (urlView == null) {
            return;
        }
        String url = Net.url(this);
        if (url == null) {
            urlView.setText("电视没有联网");
            stateView.setText("");
            return;
        }
        urlView.setText(url.replaceAll("\\?k=.*$", ""));
        stateView.setText(phoneSeen == 0 ? "等待手机…" : "手机已连接");
    }

    private void toggleQr() {
        if (qrPanel == null) {
            return;
        }
        if (qrPanel.getVisibility() == View.VISIBLE) {
            hideQr();
        } else {
            showQr();
        }
    }

    private void showQr() {
        refreshLabels();
        String url = Net.url(this);
        qr.setImageBitmap(url == null ? null : Net.qr(url, 360));
        qrPanel.setVisibility(View.VISIBLE);
    }

    private void hideQr() {
        if (qrPanel != null) {
            qrPanel.setVisibility(View.GONE);
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
        // 二维码面板盖着键盘时：确定/返回收起，方向键吞掉，避免在看不见的键盘上盲打
        if (qrPanel != null && qrPanel.getVisibility() == View.VISIBLE) {
            if (code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_DPAD_CENTER
                    || code == KeyEvent.KEYCODE_ENTER || code == KeyEvent.KEYCODE_NUMPAD_ENTER) {
                hideQr();
                return true;
            }
            if (isNav(code)) {
                return true;
            }
        }
        if (kb != null && numpad != null && isNav(code)) {
            // 左右键在「九宫格」和「主键盘」之间换焦点：走到边上再按一次就跨栏
            if (code == KeyEvent.KEYCODE_DPAD_LEFT && !numpadFocus && kb.atLeftEdge()) {
                numpadFocus = true;
                numpad.focusRow(kb.row());
                kb.invalidate();
                numpad.invalidate();
                return true;
            }
            if (code == KeyEvent.KEYCODE_DPAD_RIGHT && numpadFocus && numpad.atRightEdge()) {
                numpadFocus = false;
                kb.focusRow(numpad.rowIndex());
                kb.invalidate();
                numpad.invalidate();
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
        if (k.type == NumPad.SCAN) {
            toggleQr();
            return;
        }
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
