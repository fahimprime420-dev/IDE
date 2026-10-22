package com.fahim.cloudide;

import android.content.Context;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputLayout;
import java.io.File;

/**
 * "GitHub and build settings". Grouped into Repository, Build and Access, with real controls
 * (toggles, a build type picker) instead of eleven free-text boxes. Save validates in place and
 * only closes when the settings are usable, so it can never bounce the user back to this screen.
 */
final class ProjectSettingsDialog {
    private static final String[] PRESET_LABELS = {"Auto detect", "Gradle (Android)", "Rust", "Flutter", "Node", "Custom script"};

    private ProjectSettingsDialog() { }

    static void show(Context c, File proj, ProjectConfig cfg, Runnable saved) {
        String o = cfg.owner.isEmpty() ? Prefs.owner(c) : cfg.owner;
        String r = cfg.repo.isEmpty() ? cfg.slug() : cfg.repo;

        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(Ui.dp(c, 24), Ui.dp(c, 4), Ui.dp(c, 24), Ui.dp(c, 8));

        // ---- Repository
        box.addView(section(c, "Repository", "Where your code is pushed", false));
        TextInputLayout repoL = Ui.field(c, "Repository", (o.isEmpty() ? "" : o + "/") + r, false);
        repoL.setHelperText("owner/name, or paste the GitHub link");
        box.addView(repoL);
        TextInputLayout branchL = Ui.field(c, "Branch", cfg.branch, false);
        branchL.setHelperText("Leave empty to use the default branch");
        box.addView(branchL);
        box.addView(label(c, "If the repository has to be created"));
        MaterialButtonToggleGroup vis = toggle(c, new String[]{"Private", "Public"}, cfg.priv ? 0 : 1);
        box.addView(vis);

        // ---- Build
        box.addView(section(c, "Build", "What GitHub Actions runs for you", true));
        final int[] preset = {Math.max(0, indexOf(Workflows.PRESETS, cfg.preset))};
        TextInputLayout typeL = new TextInputLayout(c, null,
                com.google.android.material.R.attr.textInputOutlinedExposedDropdownMenuStyle);
        typeL.setHint("Build type");
        MaterialAutoCompleteTextView type = new MaterialAutoCompleteTextView(typeL.getContext());
        type.setInputType(InputType.TYPE_NULL);
        type.setSimpleItems(PRESET_LABELS);
        type.setText(PRESET_LABELS[preset[0]], false);
        type.setOnItemClickListener((p, v, pos, id) -> preset[0] = pos);
        typeL.addView(type);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(-1, -2);
        tp.topMargin = Ui.dp(c, 8);
        typeL.setLayoutParams(tp);
        box.addView(typeL);
        TextInputLayout taskL = Ui.field(c, "Gradle task", cfg.task, false);
        taskL.setHelperText("assembleDebug builds a debug APK");
        box.addView(taskL);
        TextInputLayout wfL = Ui.field(c, "Workflow file", cfg.workflow, false);
        box.addView(wfL);
        TextInputLayout outL = Ui.field(c, "Output files", cfg.artifact, false);
        outL.setHelperText("Leave empty for the default of the build type");
        box.addView(outL);

        // ---- Toolchain
        box.addView(section(c, "Toolchain", "Versions used on the build machine", true));
        TextInputLayout gradleL = Ui.field(c, "Gradle version", cfg.gradle, false);
        gradleL.setHelperText("Default 9.8.2. Any exact version, or current");
        box.addView(gradleL);
        TextInputLayout jdkL = Ui.field(c, "JDK version", cfg.jdk, false);
        jdkL.setHelperText("Temurin: 17, 21, 25 and newer");
        box.addView(jdkL);
        box.addView(label(c, "Java compiler for plain Java projects"));
        MaterialButtonToggleGroup comp = toggle(c, new String[]{"javac", "ecj"}, "ecj".equals(cfg.compiler) ? 1 : 0);
        box.addView(comp);

        // ---- Access
        box.addView(section(c, "Access", "Only stored on this device", true));
        TextInputLayout tokL = Ui.field(c, "Token for this project", "", true);
        boolean hasTok = !Prefs.projectToken(c, proj.getName()).isEmpty();
        tokL.setHelperText(hasTok ? "A project token is saved. Leave empty to keep it, type - to remove it"
                : "Optional. Empty uses the token from app Settings");
        box.addView(tokL);

        ScrollView sv = new ScrollView(c);
        sv.addView(box);

        AlertDialog dlg = new MaterialAlertDialogBuilder(c)
                .setCustomTitle(title(c, "GitHub and build", proj.getName()))
                .setView(sv)
                .setPositiveButton("Save", null)
                .setNegativeButton("Cancel", null)
                .create();
        dlg.show();

        repoL.getEditText().addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int d) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int d) { repoL.setError(null); }
            @Override public void afterTextChanged(Editable s) { }
        });

        dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String repoText = Ui.text(repoL);
            String[] pr = ProjectConfig.parseRepo(repoText);
            if (!repoText.isEmpty() && pr == null) {
                repoL.setError("Use owner/name, or a link like github.com/owner/name");
                repoL.requestFocus();
                return;
            }
            if (pr != null) {
                if (!pr[0].isEmpty()) cfg.owner = pr[0];
                cfg.repo = pr[1];
            }
            if (cfg.repo.isEmpty()) cfg.repo = cfg.slug();
            cfg.branch = Ui.text(branchL);
            cfg.workflow = orDefault(Ui.text(wfL), "build.yml");
            cfg.task = orDefault(Ui.text(taskL), "assembleDebug");
            cfg.gradle = orDefault(Ui.text(gradleL), "9.8.2");
            cfg.jdk = orDefault(Ui.text(jdkL), "21");
            cfg.priv = selected(vis) == 0;
            cfg.compiler = selected(comp) == 1 ? "ecj" : "javac";
            cfg.preset = Workflows.PRESETS[preset[0]];
            cfg.artifact = Ui.text(outL);
            String tok = Ui.text(tokL);
            if (tok.equals("-")) Prefs.setProjectToken(c, proj.getName(), "");
            else if (!tok.isEmpty()) Prefs.setProjectToken(c, proj.getName(), tok);
            cfg.save();
            dlg.dismiss();
            if (saved != null) saved.run();
        });
    }

    // ------------------------------------------------------------ small view helpers
    private static View title(Context c, String main, String sub) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(Ui.dp(c, 24), Ui.dp(c, 24), Ui.dp(c, 24), Ui.dp(c, 8));
        TextView t = new TextView(c);
        t.setText(main);
        t.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_HeadlineSmall);
        t.setTextColor(MaterialColors.getColor(c, com.google.android.material.R.attr.colorOnSurface, 0xFF000000));
        TextView s = new TextView(c);
        s.setText(sub);
        s.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium);
        s.setTextColor(MaterialColors.getColor(c, com.google.android.material.R.attr.colorOnSurfaceVariant, 0xFF666666));
        l.addView(t);
        l.addView(s);
        return l;
    }

    private static View section(Context c, String name, String sub, boolean divider) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = Ui.dp(c, divider ? 24 : 12);
        l.setLayoutParams(lp);
        if (divider) {
            View d = new View(c);
            d.setBackgroundColor(MaterialColors.getColor(c, com.google.android.material.R.attr.colorOutlineVariant, 0x33888888));
            LinearLayout.LayoutParams dp = new LinearLayout.LayoutParams(-1, Ui.dp(c, 1));
            dp.bottomMargin = Ui.dp(c, 16);
            d.setLayoutParams(dp);
            l.addView(d);
        }
        TextView t = new TextView(c);
        t.setText(name);
        t.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleMedium);
        t.setTextColor(MaterialColors.getColor(c, androidx.appcompat.R.attr.colorPrimary, 0xFF006B5E));
        TextView s = new TextView(c);
        s.setText(sub);
        s.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall);
        s.setTextColor(MaterialColors.getColor(c, com.google.android.material.R.attr.colorOnSurfaceVariant, 0xFF666666));
        l.addView(t);
        l.addView(s);
        return l;
    }

    private static View label(Context c, String text) {
        TextView t = new TextView(c);
        t.setText(text);
        t.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall);
        t.setTextColor(MaterialColors.getColor(c, com.google.android.material.R.attr.colorOnSurfaceVariant, 0xFF666666));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = Ui.dp(c, 16);
        lp.bottomMargin = Ui.dp(c, 6);
        t.setLayoutParams(lp);
        return t;
    }

    private static MaterialButtonToggleGroup toggle(Context c, String[] labels, int selected) {
        MaterialButtonToggleGroup g = new MaterialButtonToggleGroup(c);
        g.setSingleSelection(true);
        g.setSelectionRequired(true);
        g.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
        for (String s : labels) {
            MaterialButton b = new MaterialButton(c, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
            b.setId(View.generateViewId());
            b.setText(s);
            b.setAllCaps(false);
            b.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
            g.addView(b);
        }
        g.check(g.getChildAt(Math.max(0, Math.min(selected, labels.length - 1))).getId());
        return g;
    }

    private static int selected(MaterialButtonToggleGroup g) {
        for (int i = 0; i < g.getChildCount(); i++)
            if (((MaterialButton) g.getChildAt(i)).isChecked()) return i;
        return 0;
    }

    private static int indexOf(String[] a, String v) {
        for (int i = 0; i < a.length; i++) if (a[i].equals(v)) return i;
        return -1;
    }

    private static String orDefault(String v, String d) { return v.isEmpty() ? d : v; }
}
