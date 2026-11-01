package com.lu.tvime;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** 设置页：把本输入法启用并设为默认，看手机扫码地址，试打字。 */
public class SettingsActivity extends Activity {

    private static final String SECURE = "android.permission.WRITE_SECURE_SETTINGS";
    private TextView chipEnabled;
    private TextView chipDefault;
    private TextView chipGrant;
    private TextView msg;
    private ImageView qr;
    private TextView urlView;

    private int dp(int v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }

    private String versionName() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "?";
        }
    }

    private String imeId() {
        return new ComponentName(this, ImeService.class).flattenToShortString();
    }

    private boolean hasSecure() {
        return checkSelfPermissionCompat() == PackageManager.PERMISSION_GRANTED;
    }

    private int checkSelfPermissionCompat() {
        return checkCallingOrSelfPermission(SECURE);
    }

    // ---- 配色（和键盘保持一致的夜色 + 青色）----
    private static final int C_TEXT = 0xFFEAF0FA;
    private static final int C_DIM = 0xFF93A3BC;
    private static final int C_ACCENT = 0xFF5CC8FF;
    private static final int C_OK = 0xFF56D39A;
    private static final int C_WARN = 0xFFFFC857;
    private static final int C_CARD = 0xFF16213A;
    private static final int C_LINE = 0xFF2A3A5C;

    private TextView text(String s, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        if (bold) {
            t.setTypeface(Typeface.DEFAULT_BOLD);
        }
        return t;
    }

    private GradientDrawable rect(int fill, int stroke, int strokeW, int radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(radiusDp));
        if (strokeW > 0) {
            g.setStroke(dp(strokeW), stroke);
        }
        return g;
    }

    /** 状态小胶囊：绿色 = 正常，琥珀色 = 还没做。 */
    private TextView chip() {
        TextView t = text("", 14, C_DIM, true);
        t.setSingleLine(true);
        t.setPadding(dp(14), dp(6), dp(14), dp(6));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = dp(8);
        t.setLayoutParams(lp);
        return t;
    }

    private void setChip(TextView t, String s, boolean ok) {
        int c = ok ? C_OK : C_WARN;
        t.setText((ok ? "✔ " : "• ") + s);
        t.setTextColor(c);
        t.setBackground(rect((c & 0x00FFFFFF) | 0x26000000, (c & 0x00FFFFFF) | 0x66000000, 1, 16));
    }

    private Button button(String s, boolean primary, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(s);
        b.setAllCaps(false);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        b.setStateListAnimator(null);
        b.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        b.setMinHeight(dp(52));
        b.setMinimumHeight(dp(52));
        b.setPadding(dp(18), dp(10), dp(18), dp(10));
        b.setFocusable(true);
        StateListDrawable bg = new StateListDrawable();
        bg.addState(new int[]{android.R.attr.state_focused}, rect(0xFFE3F5FF, Color.WHITE, 3, 14));
        bg.addState(new int[]{android.R.attr.state_pressed}, rect(0xFFBFE9FF, Color.WHITE, 3, 14));
        if (primary) {
            GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                    new int[]{0xFF5CC8FF, 0xFF3C93F0});
            g.setCornerRadius(dp(14));
            bg.addState(new int[]{}, g);
        } else {
            bg.addState(new int[]{}, rect(C_CARD, C_LINE, 1, 14));
        }
        b.setBackground(bg);
        int normal = primary ? 0xFF04222E : C_TEXT;
        b.setTextColor(new ColorStateList(
                new int[][]{{android.R.attr.state_focused}, {android.R.attr.state_pressed}, {}},
                new int[]{0xFF06233A, 0xFF06233A, normal}));
        b.setOnClickListener(l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(10);
        b.setLayoutParams(lp);
        return b;
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(0xFF0A1020);
        WebServer.start(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.HORIZONTAL);
        root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xFF0A1020, 0xFF131E38}));
        int pad = dp(32);
        root.setPadding(pad, dp(24), pad, dp(24));

        // 左：说明+操作
        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(text("电视输入法", 30, C_TEXT, true));
        TextView ver = text("版本 " + versionName(), 12, C_ACCENT, true);
        ver.setPadding(dp(10), dp(3), dp(10), dp(3));
        ver.setBackground(rect(0x265CC8FF, 0x665CC8FF, 1, 10));
        LinearLayout.LayoutParams vlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        vlp.leftMargin = dp(12);
        head.addView(ver, vlp);
        left.addView(head);

        TextView sub = text("遥控器方向键+确定键打英文/数字；手机扫码打中文，电视实时同步", 14, C_DIM, false);
        sub.setPadding(0, dp(6), 0, dp(14));
        left.addView(sub);

        LinearLayout chips = new LinearLayout(this);
        chips.setOrientation(LinearLayout.HORIZONTAL);
        chipEnabled = chip();
        chipDefault = chip();
        chipGrant = chip();
        chips.addView(chipEnabled);
        chips.addView(chipDefault);
        chips.addView(chipGrant);
        left.addView(chips);

        TextView step = text("设置步骤", 12, C_DIM, true);
        step.setPadding(dp(2), dp(18), 0, 0);
        left.addView(step);

        left.addView(button("① 打开系统「输入法设置」（启用本输入法）", false, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openImeSettings();
            }
        }));
        left.addView(button("② 切换输入法（选「电视输入法」）", false, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                if (imm != null) {
                    imm.showInputMethodPicker();
                }
            }
        }));
        left.addView(button("一键设为默认输入法（有 root 自动完成；否则需先授权）", true, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setDefault(true);
            }
        }));
        left.addView(button("还原为原来的输入法", false, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setDefault(false);
            }
        }));
        msg = text("", 14, C_DIM, false);
        msg.setPadding(dp(2), dp(10), 0, 0);
        left.addView(msg);

        EditText test = new EditText(this);
        test.setHint("在这里试试打字");
        test.setHintTextColor(C_DIM);
        test.setTextColor(C_TEXT);
        test.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        test.setInputType(InputType.TYPE_CLASS_TEXT);
        StateListDrawable tbg = new StateListDrawable();
        tbg.addState(new int[]{android.R.attr.state_focused}, rect(0x55101B2B, C_ACCENT, 2, 14));
        tbg.addState(new int[]{}, rect(0x55101B2B, C_LINE, 2, 14));
        test.setBackground(tbg);
        test.setPadding(dp(16), dp(12), dp(16), dp(12));
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.topMargin = dp(14);
        left.addView(test, tlp);

        TextView help = text("一次性授权（电脑 ADB，或 tv-adb 的「执行命令」）：\n"
                + "pm grant com.lu.tvime " + SECURE + "\n"
                + "授权后点「一键设为默认」即可。没授权也可用 ① ②，或直接执行：\n"
                + "ime enable " + "com.lu.tvime/.ImeService\n"
                + "ime set " + "com.lu.tvime/.ImeService", 12, C_DIM, false);
        help.setTypeface(Typeface.MONOSPACE);
        help.setBackground(rect(0x33000000, C_LINE, 1, 12));
        help.setPadding(dp(14), dp(12), dp(14), dp(12));
        help.setTextIsSelectable(true);
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hlp.topMargin = dp(14);
        left.addView(help, hlp);

        ScrollView sv = new ScrollView(this);
        sv.addView(left);
        root.addView(sv, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));

        // 右：二维码卡片
        LinearLayout right = new LinearLayout(this);
        right.setOrientation(LinearLayout.VERTICAL);
        right.setGravity(Gravity.CENTER_HORIZONTAL);
        GradientDrawable card = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x2A9DB4E0, 0x0E9DB4E0});
        card.setCornerRadius(dp(20));
        card.setStroke(dp(1), 0x34FFFFFF);
        right.setBackground(card);
        right.setPadding(dp(20), dp(20), dp(20), dp(20));
        qr = new ImageView(this);
        qr.setBackground(rect(Color.WHITE, 0, 0, 14));
        qr.setPadding(dp(10), dp(10), dp(10), dp(10));
        right.addView(qr, new LinearLayout.LayoutParams(dp(230), dp(230)));
        TextView qt = text("手机扫码打字", 17, C_TEXT, true);
        qt.setPadding(0, dp(14), 0, dp(4));
        right.addView(qt);
        urlView = text("", 11, C_ACCENT, false);
        urlView.setGravity(Gravity.CENTER);
        right.addView(urlView, new LinearLayout.LayoutParams(dp(230), ViewGroup.LayoutParams.WRAP_CONTENT));
        Button reset = button("重置二维码口令", false, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Net.resetToken(SettingsActivity.this);
                refresh();
                msg.setTextColor(C_DIM);
                msg.setText("口令已重置，旧二维码作废");
            }
        });
        reset.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(dp(230), ViewGroup.LayoutParams.WRAP_CONTENT);
        rlp.topMargin = dp(14);
        right.addView(reset, rlp);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(dp(290), ViewGroup.LayoutParams.WRAP_CONTENT);
        clp.leftMargin = dp(24);
        clp.gravity = Gravity.CENTER_VERTICAL;
        root.addView(right, clp);

        setContentView(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        String id = imeId();
        String enabled = Settings.Secure.getString(getContentResolver(), Settings.Secure.ENABLED_INPUT_METHODS);
        String cur = Settings.Secure.getString(getContentResolver(), Settings.Secure.DEFAULT_INPUT_METHOD);
        boolean en = enabled != null && enabled.contains(id);
        boolean def = id.equals(cur);
        boolean grant = hasSecure();
        setChip(chipEnabled, en ? "已启用" : "未启用", en);
        setChip(chipDefault, def ? "默认输入法" : "非默认", def);
        setChip(chipGrant, grant ? "已授权" : "未授权", grant);
        String url = Net.url(this);
        if (url == null) {
            urlView.setText("电视没有联网");
            qr.setImageBitmap(null);
        } else {
            qr.setImageBitmap(Net.qr(url, 460));
            urlView.setText(url);
        }
    }

    private void openImeSettings() {
        try {
            startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS));
        } catch (Exception e) {
            msg.setText("这台电视没有「输入法设置」页面，改用下方 adb 命令，或先授权再一键设置。");
        }
    }

    /** 没有 WRITE_SECURE_SETTINGS 时，尝试用 root（su）直接授权并启用/切换，不用手敲命令。 */
    private void setDefaultByRoot(final boolean on) {
        msg.setTextColor(C_WARN);
        msg.setText("正在尝试用 root 设置…（如弹出 root 授权请点允许）");
        final String id = imeId();
        final String cur = Settings.Secure.getString(getContentResolver(), Settings.Secure.DEFAULT_INPUT_METHOD);
        final String prev = Net.prefs(this).getString("prev_ime", "");
        if (on && cur != null && !id.equals(cur)) {
            Net.prefs(this).edit().putString("prev_ime", cur).commit();
        }
        if (!on && prev.length() == 0) {
            msg.setTextColor(0xFFFF6B6B);
            msg.setText("没有记录原来的输入法，请用 ② 手动切换");
            return;
        }
        final String[] cmds = on
                ? new String[]{"pm grant " + getPackageName() + " " + SECURE,
                        "ime enable " + id, "ime set " + id}
                : new String[]{"ime set " + prev};
        new Thread(new Runnable() {
            @Override
            public void run() {
                final String r = runSu(cmds);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        refresh();
                        String now = Settings.Secure.getString(getContentResolver(),
                                Settings.Secure.DEFAULT_INPUT_METHOD);
                        boolean ok = on ? id.equals(now) : (now != null && now.equals(prev));
                        if (ok) {
                            msg.setTextColor(C_OK);
                            msg.setText(on ? "✔ 已用 root 设为默认输入法。点一下下面的输入框试试。"
                                    : "✔ 已还原为 " + prev);
                        } else {
                            msg.setTextColor(0xFFFF6B6B);
                            msg.setText("root 设置没成功（没有 root，或没允许授权）。输出：\n" + r
                                    + "\n可改用下方命令，在 root 终端里复制粘贴执行。");
                        }
                    }
                });
            }
        }).start();
    }

    private static String runSu(String[] cmds) {
        StringBuilder out = new StringBuilder();
        try {
            Process p = Runtime.getRuntime().exec("su");
            java.io.OutputStream os = p.getOutputStream();
            for (String c : cmds) {
                os.write((c + " 2>&1\n").getBytes("UTF-8"));
            }
            os.write("exit\n".getBytes("UTF-8"));
            os.flush();
            java.io.BufferedReader br = new java.io.BufferedReader(
                    new java.io.InputStreamReader(p.getInputStream(), "UTF-8"));
            String line;
            while ((line = br.readLine()) != null) {
                out.append(line).append('\n');
            }
            p.waitFor();
        } catch (Throwable t) {
            out.append(t.getMessage());
        }
        return out.toString().trim();
    }

    private void setDefault(boolean on) {
        if (!hasSecure()) {
            setDefaultByRoot(on);
            return;
        }
        try {
            String id = imeId();
            String enabled = Settings.Secure.getString(getContentResolver(), Settings.Secure.ENABLED_INPUT_METHODS);
            String cur = Settings.Secure.getString(getContentResolver(), Settings.Secure.DEFAULT_INPUT_METHOD);
            if (on) {
                if (!id.equals(cur)) {
                    Net.prefs(this).edit().putString("prev_ime", cur == null ? "" : cur).commit();
                }
                if (enabled == null || !enabled.contains(id)) {
                    Settings.Secure.putString(getContentResolver(), Settings.Secure.ENABLED_INPUT_METHODS,
                            (enabled == null || enabled.length() == 0) ? id : enabled + ":" + id);
                }
                Settings.Secure.putString(getContentResolver(), Settings.Secure.DEFAULT_INPUT_METHOD, id);
                msg.setTextColor(C_OK);
                msg.setText("✔ 已设为默认输入法。点一下下面的输入框试试。");
            } else {
                String prev = Net.prefs(this).getString("prev_ime", "");
                if (prev.length() == 0) {
                    msg.setTextColor(0xFFFF6B6B);
                    msg.setText("没有记录原来的输入法，请用 ② 手动切换");
                    return;
                }
                Settings.Secure.putString(getContentResolver(), Settings.Secure.DEFAULT_INPUT_METHOD, prev);
                msg.setTextColor(C_OK);
                msg.setText("✔ 已还原为 " + prev);
            }
            refresh();
        } catch (Exception e) {
            msg.setTextColor(0xFFFF6B6B);
            msg.setText("设置失败：" + e.getMessage());
        }
    }
}
