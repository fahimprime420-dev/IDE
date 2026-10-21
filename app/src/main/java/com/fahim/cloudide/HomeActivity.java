package com.fahim.cloudide;

import android.Manifest;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.EditText;
import java.util.Locale;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.json.JSONArray;

public class HomeActivity extends AppCompatActivity {
    RecyclerView list;
    View empty, emptyActions;
    TextView emptyTitle, emptyBody;
    EditText search;
    final List<File> items = new ArrayList<>();
    ProjAdapter ad;

    @Override
    protected void onCreate(Bundle b) {
        Ui.edge(this);
        super.onCreate(b);
        setContentView(R.layout.activity_home);
        Ui.insets(findViewById(R.id.root), true, true);
        setSupportActionBar(findViewById(R.id.toolbar));
        search = findViewById(R.id.search);
        emptyActions = findViewById(R.id.emptyActions);
        emptyTitle = findViewById(R.id.emptyTitle);
        emptyBody = findViewById(R.id.emptyBody);
        findViewById(R.id.emptyNew).setOnClickListener(v -> newProject());
        findViewById(R.id.emptyClone).setOnClickListener(v -> cloneRepo());
        search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence x, int a, int c, int d) { }

            @Override
            public void onTextChanged(CharSequence x, int a, int c, int d) { refresh(); }

            @Override
            public void afterTextChanged(Editable e) { }
        });
        list = findViewById(R.id.list);
        empty = findViewById(R.id.empty);
        list.setLayoutManager(new LinearLayoutManager(this));
        ad = new ProjAdapter();
        list.setAdapter(ad);
        ExtendedFloatingActionButton fab = findViewById(R.id.fab);
        fab.setOnClickListener(v -> addMenu());
        if (Build.VERSION.SDK_INT < 30 && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE}, 1);
    }

    @Override
    protected void onResume() {
        super.onResume();
        askStorage();
        refresh();
    }

    void askStorage() {
        if (Build.VERSION.SDK_INT < 30 || Environment.isExternalStorageManager() || Prefs.bool(this, "askedStorage", false)) return;
        Prefs.put(this, "askedStorage", true);
        new MaterialAlertDialogBuilder(this).setTitle("Storage access")
                .setMessage("Allow access to all files to keep projects in shared storage (for example /AppProjects, shared with Termux). Without it, projects live in the app's own folder.")
                .setPositiveButton("Allow", (d, w) -> startActivity(new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:" + getPackageName()))))
                .setNegativeButton("Not now", null).show();
    }

    void refresh() {
        items.clear();
        String q = search == null ? "" : search.getText().toString().trim().toLowerCase(Locale.ROOT);
        int total = 0;
        File[] k = Prefs.root(this).listFiles();
        if (k != null) {
            Arrays.sort(k, (a, c) -> {
                long x = Prefs.opened(this, a.getName()), y = Prefs.opened(this, c.getName());
                return x != y ? Long.compare(y, x) : a.getName().compareToIgnoreCase(c.getName());
            });
            for (File f : k) {
                if (!f.isDirectory() || f.getName().startsWith(".")) continue;
                total++;
                if (q.isEmpty() || f.getName().toLowerCase(Locale.ROOT).contains(q)) items.add(f);
            }
        }
        ad.notifyDataSetChanged();
        boolean filtering = !q.isEmpty() && total > 0;
        empty.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
        emptyTitle.setText(filtering ? "No project matches" : "Start your first project");
        emptyBody.setText(filtering ? "Try a different name." : "Write code on your phone and build the APK in the cloud.");
        emptyActions.setVisibility(filtering ? View.GONE : View.VISIBLE);
        if (getSupportActionBar() != null)
            getSupportActionBar().setSubtitle(total == 0 ? "" : total + (total == 1 ? " project" : " projects"));
    }

    @Override
    public boolean onCreateOptionsMenu(Menu m) {
        getMenuInflater().inflate(R.menu.menu_home, m);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem it) {
        if (it.getItemId() == R.id.m_settings) {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        }
        return super.onOptionsItemSelected(it);
    }

    void openProject(File f) {
        Prefs.touch(this, f.getName());
        startActivity(new Intent(this, EditorActivity.class).putExtra("dir", f.getAbsolutePath()));
    }

    void addMenu() {
        Ui.menu(this, "Add project", new String[]{"New project", "Clone from GitHub", "Open folder in place", "Import folder (copy)"}, i -> {
            if (i == 0) newProject();
            else if (i == 1) cloneRepo();
            else if (i == 2) openPath();
            else importPath();
        });
    }

    void newProject() {
        Ui.menu(this, "Template", new String[]{"Java  -  Views", "Kotlin  -  Views", "Kotlin  -  Jetpack Compose", "C++  -  NDK + CMake (JNI)",
                        "Rust  -  JNI via cargo-ndk", "Flutter  -  Dart", "Custom  -  any language (C, C++, Rust, Go ...)"},
                t -> Ui.form(this, "New project", new String[]{"App name", "Package name"},
                        new String[]{"My App", "com.example.myapp"}, null, "Create", v -> create(t, v[0], v[1])));
    }

    void create(int t, String name, String pkg) {
        if (!name.matches("[A-Za-z0-9][A-Za-z0-9 _-]*") || !pkg.matches("[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)+")) {
            Ui.toast(this, "Use letters and digits for the name, and a package like com.example.app");
            return;
        }
        File dir = new File(Prefs.root(this), name);
        if (dir.exists()) {
            Ui.toast(this, "A project with that name already exists");
            return;
        }
        try {
            Map<String, String> vars = new HashMap<>();
            vars.put("__PKG__", pkg);
            vars.put("__NAME__", name);
            vars.put("__PKGJNI__", pkg.replace("_", "_1").replace('.', '_'));
            String snake = name.toLowerCase().replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
            if (snake.isEmpty() || Character.isDigit(snake.charAt(0))) snake = "app_" + snake;
            vars.put("__SNAKE__", snake);
            vars.put("__ORG__", pkg.substring(0, pkg.lastIndexOf('.')));
            String preset = "gradle";
            if (t <= 4) Io.template(this, "templates/common", "", dir, vars, pkg);
            if (t == 2) Io.template(this, "templates/compose", "", dir, vars, pkg);
            else if (t <= 4) {
                Io.template(this, "templates/views", "", dir, vars, pkg);
                String lang = t == 0 ? "java" : t == 1 ? "kotlin" : t == 3 ? "cpp" : "rust";
                Io.template(this, "templates/" + lang, "", dir, vars, pkg);
                if (t == 4) preset = "rust";
            } else if (t == 5) {
                Io.template(this, "templates/flutter", "", dir, vars, pkg);
                preset = "flutter";
            } else {
                Io.template(this, "templates/custom", "", dir, vars, pkg);
                preset = "custom";
            }
            ProjectConfig pc = new ProjectConfig(dir);
            pc.preset = preset;
            Workflows.write(this, dir, pc);
            openProject(dir);
        } catch (Exception e) {
            Ui.toast(this, String.valueOf(e));
        }
    }

    void openPath() {
        Ui.input(this, "Open folder in place", "Folder path", "/storage/emulated/0/AppProjects/", p -> {
            File d = new File(p);
            if (!d.isDirectory()) {
                Ui.toast(this, "Not a folder, or no storage access");
                return;
            }
            openProject(d);
        });
    }

    void importPath() {
        Ui.input(this, "Import folder (copied)", "Folder path", "/storage/emulated/0/", p -> {
            File s = new File(p);
            if (!s.isDirectory()) {
                Ui.toast(this, "Not a folder, or no storage access");
                return;
            }
            try {
                Io.copy(s, new File(Prefs.root(this), s.getName()));
                refresh();
            } catch (Exception e) {
                Ui.toast(this, String.valueOf(e));
            }
        });
    }

    void cloneRepo() {
        String tok = Prefs.token(this);
        if (tok.isEmpty()) {
            Ui.toast(this, "Add your GitHub token in Settings first");
            startActivity(new Intent(this, SettingsActivity.class));
            return;
        }
        AlertDialog busy = Ui.busy(this, "Loading your repositories...");
        new Thread(() -> {
            try {
                JSONArray a = new Gh(tok, s -> { }).repos();
                String[] names = new String[a.length()];
                for (int k = 0; k < names.length; k++) names[k] = a.getJSONObject(k).getString("full_name");
                runOnUiThread(() -> {
                    busy.dismiss();
                    if (names.length == 0) Ui.toast(this, "No repositories found");
                    else Ui.menu(this, "Clone repository", names, i -> doClone(names[i], tok));
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    busy.dismiss();
                    Ui.toast(this, String.valueOf(e.getMessage()));
                });
            }
        }).start();
    }

    void doClone(String full, String tok) {
        String[] p = full.split("/");
        File dest = new File(Prefs.root(this), p[1]);
        if (dest.exists()) {
            Ui.toast(this, "A project named " + p[1] + " already exists");
            return;
        }
        AlertDialog busy = Ui.busy(this, "Downloading " + full + "...");
        new Thread(() -> {
            try {
                byte[] z = new Gh(tok, s -> { }).zipball(p[0], p[1], "");
                Io.unzipStrip(z, dest);
                ProjectConfig c = ProjectConfig.load(dest);
                c.owner = p[0];
                c.repo = p[1];
                c.save();
                runOnUiThread(() -> {
                    busy.dismiss();
                    refresh();
                    openProject(dest);
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    busy.dismiss();
                    Ui.toast(this, String.valueOf(e.getMessage()));
                });
            }
        }).start();
    }

    void projectMenu(File f) {
        Ui.menu(this, f.getName(), new String[]{"Open", "Rename", "Delete", "Open on GitHub"}, i -> {
            if (i == 0) openProject(f);
            else if (i == 1) Ui.input(this, "Rename project", "Name", f.getName(), n -> {
                if (n.isEmpty() || !f.renameTo(new File(f.getParentFile(), n))) Ui.toast(this, "Rename failed");
                refresh();
            });
            else if (i == 2) Ui.confirm(this, "Delete " + f.getName() + "?",
                    "This deletes the local copy only. The GitHub repository is not touched.", "Delete", () -> {
                        Io.delete(f);
                        refresh();
                    });
            else {
                ProjectConfig c = ProjectConfig.load(f);
                if (c.repo.isEmpty()) Ui.toast(this, "Not linked to GitHub yet");
                else startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/" + c.owner + "/" + c.repo)));
            }
        });
    }

    static String kindOf(File f) {
        if (new File(f, "pubspec.yaml").exists()) return "Flutter";
        if (new File(f, "Cargo.toml").exists() || new File(f, "rust/Cargo.toml").exists()) return "Rust";
        if (new File(f, "package.json").exists()) return "Node";
        if (new File(f, "settings.gradle").exists() || new File(f, "settings.gradle.kts").exists()
                || new File(f, "build.gradle").exists()) return "Android (Gradle)";
        return "Folder";
    }

    class ProjAdapter extends RecyclerView.Adapter<ProjAdapter.VH> {
        class VH extends RecyclerView.ViewHolder {
            TextView name, sub, status, mono;
            View dot;

            VH(View v) {
                super(v);
                name = v.findViewById(R.id.name);
                sub = v.findViewById(R.id.sub);
                status = v.findViewById(R.id.status);
                mono = v.findViewById(R.id.mono);
                dot = v.findViewById(R.id.dot);
            }
        }

        @Override
        public VH onCreateViewHolder(ViewGroup p, int t) {
            return new VH(LayoutInflater.from(p.getContext()).inflate(R.layout.item_project, p, false));
        }

        @Override
        public void onBindViewHolder(VH vh, int i) {
            File f = items.get(i);
            ProjectConfig c = ProjectConfig.load(f);
            String n = f.getName();
            vh.name.setText(n);
            vh.mono.setText(n.substring(0, 1).toUpperCase(Locale.ROOT));
            vh.mono.setBackgroundTintList(ColorStateList.valueOf(Ui.tint(n)));
            vh.sub.setText(c.repo.isEmpty() ? Detect.label(f) : c.owner + "/" + c.repo + (c.branch.isEmpty() ? "" : "  (" + c.branch + ")"));
            long when = c.history.length() > 0 ? c.history.optJSONObject(0).optLong("t") : 0;
            String ago = Human.ago(when, System.currentTimeMillis());
            int dot = MaterialColors.getColor(vh.itemView, com.google.android.material.R.attr.colorOutline, 0xFF888888);
            String st;
            if (c.lastStatus.equals("success")) {
                st = "Last build passed" + (ago.isEmpty() ? "" : " " + ago);
                dot = Ui.OK;
            } else if (c.lastStatus.equals("failed")) {
                st = "Last build failed" + (ago.isEmpty() ? "" : " " + ago);
                dot = Ui.BAD;
            } else st = c.repo.isEmpty() ? "Not linked to GitHub yet" : "Not built yet";
            vh.status.setText(st);
            vh.dot.setBackgroundTintList(ColorStateList.valueOf(dot));
            vh.itemView.setOnClickListener(v -> openProject(f));
            vh.itemView.setOnLongClickListener(v -> {
                projectMenu(f);
                return true;
            });
        }

        @Override
        public int getItemCount() { return items.size(); }
    }
}
