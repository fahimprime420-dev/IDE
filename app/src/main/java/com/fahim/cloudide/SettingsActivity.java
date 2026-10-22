package com.fahim.cloudide;

import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;

public class SettingsActivity extends AppCompatActivity {
    LinearLayout box;

    @Override
    protected void onCreate(Bundle b) {
        Ui.edge(this);
        super.onCreate(b);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        MaterialToolbar tb = new MaterialToolbar(this);
        tb.setTitle("Settings");
        tb.setNavigationIcon(R.drawable.ic_back);
        tb.setNavigationOnClickListener(v -> finish());
        root.addView(tb, new LinearLayout.LayoutParams(-1, Ui.dp(this, 56)));
        ScrollView sv = new ScrollView(this);
        box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(0, 0, 0, Ui.dp(this, 32));
        sv.addView(box);
        root.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));
        setContentView(root);
        Ui.insets(root, true, true);
        render();
    }

    @Override
    protected void onResume() {
        super.onResume();
        render();
    }

    int color(int attr, int def) { return MaterialColors.getColor(this, attr, def); }

    void header(String t) {
        TextView v = new TextView(this);
        v.setText(t);
        v.setTextSize(13);
        v.setTypeface(null, android.graphics.Typeface.BOLD);
        v.setTextColor(color(androidx.appcompat.R.attr.colorPrimary, 0xFF3B6FD4));
        v.setPadding(Ui.dp(this, 20), Ui.dp(this, 22), Ui.dp(this, 20), Ui.dp(this, 6));
        box.addView(v);
    }

    LinearLayout texts(String title, String sub) {
        LinearLayout t = new LinearLayout(this);
        t.setOrientation(LinearLayout.VERTICAL);
        TextView a = new TextView(this);
        a.setText(title);
        a.setTextSize(16);
        a.setTextColor(color(com.google.android.material.R.attr.colorOnSurface, 0xFF000000));
        t.addView(a);
        if (sub != null && !sub.isEmpty()) {
            TextView s = new TextView(this);
            s.setText(sub);
            s.setTextSize(13);
            s.setTextColor(color(com.google.android.material.R.attr.colorOnSurfaceVariant, 0xFF666666));
            t.addView(s);
        }
        return t;
    }

    void ripple(View v) {
        TypedValue tv = new TypedValue();
        getTheme().resolveAttribute(android.R.attr.selectableItemBackground, tv, true);
        v.setBackgroundResource(tv.resourceId);
    }

    void row(String title, String sub, View.OnClickListener l) {
        LinearLayout r = new LinearLayout(this);
        r.setPadding(Ui.dp(this, 20), Ui.dp(this, 12), Ui.dp(this, 20), Ui.dp(this, 12));
        r.addView(texts(title, sub), new LinearLayout.LayoutParams(0, -2, 1f));
        ripple(r);
        r.setOnClickListener(l);
        box.addView(r);
    }

    void toggle(String title, String sub, String key, boolean def) {
        LinearLayout r = new LinearLayout(this);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(Ui.dp(this, 20), Ui.dp(this, 10), Ui.dp(this, 20), Ui.dp(this, 10));
        r.addView(texts(title, sub), new LinearLayout.LayoutParams(0, -2, 1f));
        MaterialSwitch s = new MaterialSwitch(this);
        s.setChecked(Prefs.bool(this, key, def));
        s.setOnCheckedChangeListener((bv, on) -> Prefs.put(this, key, on));
        r.addView(s);
        ripple(r);
        r.setOnClickListener(v -> s.toggle());
        box.addView(r);
    }

    static int idxOf(String[] a, String v) {
        for (int i = 0; i < a.length; i++) if (a[i].equals(v)) return i;
        return -1;
    }

    void render() {
        box.removeAllViews();
        String tok = Prefs.token(this), owner = Prefs.owner(this);
        header("GitHub");
        row("Username", owner.isEmpty() ? "Not set (default owner for new repositories)" : owner,
                v -> Ui.input(this, "GitHub username", "Username", Prefs.owner(this), s -> {
                    String clean = ProjectConfig.owner(s);
                    if (clean.isEmpty() && !s.isEmpty()) Ui.toast(this, "That does not look like a GitHub username");
                    else Prefs.put(this, "owner", clean);
                    render();
                }));
        row("Access token", tok.isEmpty() ? "Not set" : "Saved and encrypted (ends in " + tok.substring(Math.max(0, tok.length() - 4)) + ")",
                v -> Ui.form(this, "GitHub token", new String[]{"Personal access token"}, new String[]{""}, new boolean[]{true}, "Save", r -> {
                    if (!r[0].isEmpty()) Prefs.setToken(this, r[0]);
                    render();
                }));
        row("Create a token", "Opens GitHub with the repo and workflow scopes selected",
                v -> startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/settings/tokens/new?scopes=repo,workflow&description=CloudIDE"))));
        row("Test connection", "Checks the token and its scopes", v -> testConnection());
        row("Remove token", "Delete the saved token from this device", v -> {
            Prefs.setToken(this, "");
            render();
        });

        header("Appearance");
        String[] nightN = {"Follow system", "Light", "Dark"};
        int night = Prefs.sp(this).getInt("night", 2);
        row("App theme", nightN[night], v -> Ui.choose(this, "App theme", nightN, night, i -> {
            Prefs.sp(this).edit().putInt("night", i).apply();
            App.applyTheme(this);
            render();
        }));
        String[] et = new String[EditorTheme.ALL.length + 1];
        et[0] = "Auto (follows app theme)";
        for (int k = 0; k < EditorTheme.ALL.length; k++) et[k + 1] = EditorTheme.ALL[k].name;
        int ecur = Math.min(Prefs.sp(this).getInt("etheme", 0), et.length - 1);
        row("Editor color theme", et[ecur], v -> Ui.choose(this, "Editor theme", et, ecur, i -> {
            Prefs.sp(this).edit().putInt("etheme", i).apply();
            render();
        }));
        String[] fs = {"10", "11", "12", "13", "14", "15", "16", "18", "20", "22", "24"};
        row("Font size", (int) Prefs.font(this) + " sp (you can also pinch to zoom in the editor)",
                v -> Ui.choose(this, "Font size", fs, idxOf(fs, String.valueOf((int) Prefs.font(this))), i -> {
                    Prefs.sp(this).edit().putFloat("font", Float.parseFloat(fs[i])).apply();
                    render();
                }));

        header("Editor");
        String[] tabs = {"2", "4", "8"};
        row("Tab width", Prefs.tab(this) + " spaces", v -> Ui.choose(this, "Tab width", tabs, idxOf(tabs, String.valueOf(Prefs.tab(this))), i -> {
            Prefs.sp(this).edit().putInt("tab", Integer.parseInt(tabs[i])).apply();
            render();
        }));
        toggle("Word wrap", "Wrap long lines", "wrap", true);
        toggle("Line numbers", "Show the gutter", "lines", true);
        toggle("Auto save", "Save after you stop typing and when switching files", "autosave", true);
        toggle("Auto-close brackets and quotes", "Type ( and get ()", "pairs", true);
        toggle("Highlight matching brackets", "", "bracket", true);
        toggle("Autocomplete", "Keywords, words from the file, and snippets (try sout, fori, linear)", "complete", true);

        header("Storage");
        row("Projects folder", Prefs.root(this).getAbsolutePath() + "\nTap to change (blank = default)",
                v -> Ui.input(this, "Projects folder", "Folder path", Prefs.str(this, "root"), s -> {
                    Prefs.put(this, "root", s);
                    render();
                }));
        String access = Build.VERSION.SDK_INT >= 30 ? (Environment.isExternalStorageManager() ? "Granted" : "Not granted. Tap to allow")
                : "Managed by the storage permission prompt";
        row("All-files access", access, v -> {
            if (Build.VERSION.SDK_INT >= 30)
                startActivity(new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:" + getPackageName())));
        });

        final java.io.File builds = new java.io.File(getExternalFilesDir(null), "builds");
        long used = Io.size(builds);
        row("Build downloads", used == 0 ? "Nothing stored" : ApkInfo.size(used) + " of downloaded APKs and build outputs. Tap to delete.",
                v -> {
                    if (Io.size(builds) == 0) return;
                    Ui.confirm(this, "Delete build downloads?", "Local copies of built APKs are removed. Your GitHub runs are not touched.", "Delete", () -> {
                        Io.delete(builds);
                        render();
                    });
                });

        header("About");
        String ver = "";
        try {
            ver = " " + getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception ignored) {
        }
        row("CloudIDE" + ver, "Edit on your phone, build on GitHub Actions", v -> { });
    }

    void testConnection() {
        String tok = Prefs.token(this);
        if (tok.isEmpty()) {
            Ui.toast(this, "Save a token first");
            return;
        }
        AlertDialog busy = Ui.busy(this, "Contacting GitHub...");
        new Thread(() -> {
            String msg;
            try {
                Gh g = new Gh(tok, s -> { });
                String login = g.whoami().getString("login");
                String sc = g.scopes();
                if (sc.isEmpty())
                    msg = "Signed in as " + login + ".\nFine-grained token: scopes cannot be listed. It needs Contents, Workflows, Actions (read and write) and Administration (to create repositories).";
                else
                    msg = "Signed in as " + login + ".\nScopes: " + sc + (sc.contains("repo") && sc.contains("workflow")
                            ? "\nAll required scopes are present." : "\nMissing scope: you need both repo and workflow.");
            } catch (Exception e) {
                msg = String.valueOf(e.getMessage());
            }
            final String m = msg;
            runOnUiThread(() -> {
                busy.dismiss();
                new MaterialAlertDialogBuilder(this).setTitle("GitHub").setMessage(m).setPositiveButton("OK", null).show();
            });
        }).start();
    }
}
