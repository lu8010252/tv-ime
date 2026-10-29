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
    private TextView status;
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

    private GradientDrawable rect(int fill, int stroke, int strokeW) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(12));
        if (strokeW > 0) {
            g.setStroke(dp(strokeW), stroke);
        }
        return g;
    }

    private Button button(String s, boolean primary, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(s);
        b.setAllCaps(false);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        b.setStateListAnimator(null);
        b.setPadding(dp(14), dp(12), dp(14), dp(12));
        b.setFocusable(true);
        StateListDrawable bg = new StateListDrawable();
        bg.addState(new int[]{android.R.attr.state_focused}, rect(0xFFB3E5FC, Color.WHITE, 3));
        bg.addState(new int[]{android.R.attr.state_pressed}, rect(0xFFB3E5FC, Color.WHITE, 3));
        bg.addState(new int[]{}, primary ? rect(0xFF4FC3F7, 0, 0) : rect(0xFF1B2A3E, 0xFF2D4059, 1));
        b.setBackground(bg);
        int normal = primary ? 0xFF06222F : 0xFFECEFF4;
        b.setTextColor(new ColorStateList(
                new int[][]{{android.R.attr.state_focused}, {android.R.attr.state_pressed}, {}},
                new int[]{0xFF06222F, 0xFF06222F, normal}));
        b.setOnClickListener(l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(8);
        b.setLayoutParams(lp);
        return b;
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(0xFF0B1220);
        WebServer.start(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.HORIZONTAL);
        root.setBackgroundColor(0xFF0B1220);
        int pad = dp(28);
        root.setPadding(pad, dp(20), pad, dp(20));

        // 左：说明+操作
        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        left.addView(text("电视输入法", 28, 0xFFECEFF4, true));
        left.addView(text("版本 " + versionName(), 13, 0xFF4FC3F7, false));
        left.addView(text("遥控器方向键+确定键打英文/数字；手机扫码打中文，电视实时同步", 14, 0xFF9AA7B8, false));
        status = text("", 16, 0xFFFFB74D, false);
        status.setPadding(0, dp(12), 0, dp(4));
        left.addView(status);

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
        msg = text("", 14, 0xFF9AA7B8, false);
        msg.setPadding(0, dp(8), 0, 0);
        left.addView(msg);

        EditText test = new EditText(this);
        test.setHint("在这里试试打字");
        test.setHintTextColor(0xFF9AA7B8);
        test.setTextColor(0xFFECEFF4);
        test.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        test.setInputType(InputType.TYPE_CLASS_TEXT);
        test.setBackground(rect(0x66101B2B, 0xFF2D4059, 2));
        test.setPadding(dp(12), dp(10), dp(12), dp(10));
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.topMargin = dp(12);
        left.addView(test, tlp);

        TextView help = text("一次性授权（电脑 ADB，或 tv-adb 的「执行命令」）：\n"
                + "pm grant com.lu.tvime " + SECURE + "\n"
                + "授权后点「一键设为默认」即可。没授权也可用 ① ②，或直接执行：\n"
                + "ime enable " + "com.lu.tvime/.ImeService\n"
                + "ime set " + "com.lu.tvime/.ImeService", 12, 0xFF9AA7B8, false);
        help.setTypeface(Typeface.MONOSPACE);
        help.setPadding(0, dp(12), 0, 0);
        help.setTextIsSelectable(true);
        left.addView(help);

        ScrollView sv = new ScrollView(this);
        sv.addView(left);
        root.addView(sv, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));

        // 右：二维码
        LinearLayout right = new LinearLayout(this);
        right.setOrientation(LinearLayout.VERTICAL);
        right.setGravity(Gravity.CENTER_HORIZONTAL);
        right.setPadding(dp(24), 0, 0, 0);
        qr = new ImageView(this);
        qr.setBackgroundColor(Color.WHITE);
        qr.setPadding(dp(8), dp(8), dp(8), dp(8));
        right.addView(qr, new LinearLayout.LayoutParams(dp(230), dp(230)));
        right.addView(text("手机扫码打字", 16, 0xFFECEFF4, true));
        urlView = text("", 11, 0xFF4FC3F7, false);
        urlView.setGravity(Gravity.CENTER);
        right.addView(urlView, new LinearLayout.LayoutParams(dp(230), ViewGroup.LayoutParams.WRAP_CONTENT));
        right.addView(button("重置二维码口令", false, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Net.resetToken(SettingsActivity.this);
                refresh();
                msg.setText("口令已重置，旧二维码作废");
            }
        }), new LinearLayout.LayoutParams(dp(230), ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(right, new LinearLayout.LayoutParams(dp(290), ViewGroup.LayoutParams.MATCH_PARENT));

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
        status.setTextColor(def ? 0xFF66BB6A : 0xFFFFB74D);
        status.setText((en ? "✔ 已启用" : "✘ 还没启用") + "    " + (def ? "✔ 当前就是默认输入法" : "✘ 还不是默认输入法")
                + "\n授权状态：" + (hasSecure() ? "✔ 已授权，可一键设置" : "未授权（见下方命令）"));
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
        msg.setTextColor(0xFFFFB74D);
        msg.setText("正在尝试用 root 设置…（如弹出 root 授权请点允许）");
        final String id = imeId();
        final String cur = Settings.Secure.getString(getContentResolver(), Settings.Secure.DEFAULT_INPUT_METHOD);
        final String prev = Net.prefs(this).getString("prev_ime", "");
        if (on && cur != null && !id.equals(cur)) {
            Net.prefs(this).edit().putString("prev_ime", cur).commit();
        }
        if (!on && prev.length() == 0) {
            msg.setTextColor(0xFFEF5350);
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
                            msg.setTextColor(0xFF66BB6A);
                            msg.setText(on ? "✔ 已用 root 设为默认输入法。点一下下面的输入框试试。"
                                    : "✔ 已还原为 " + prev);
                        } else {
                            msg.setTextColor(0xFFEF5350);
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
                msg.setTextColor(0xFF66BB6A);
                msg.setText("✔ 已设为默认输入法。点一下下面的输入框试试。");
            } else {
                String prev = Net.prefs(this).getString("prev_ime", "");
                if (prev.length() == 0) {
                    msg.setTextColor(0xFFEF5350);
                    msg.setText("没有记录原来的输入法，请用 ② 手动切换");
                    return;
                }
                Settings.Secure.putString(getContentResolver(), Settings.Secure.DEFAULT_INPUT_METHOD, prev);
                msg.setTextColor(0xFF66BB6A);
                msg.setText("✔ 已还原为 " + prev);
            }
            refresh();
        } catch (Exception e) {
            msg.setTextColor(0xFFEF5350);
            msg.setText("设置失败：" + e.getMessage());
        }
    }
}
