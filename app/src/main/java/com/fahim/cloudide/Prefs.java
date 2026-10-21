package com.fahim.cloudide;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Environment;
import java.io.File;

final class Prefs {
    static SharedPreferences sp(Context c) {
        return c.getApplicationContext().getSharedPreferences("cloudide", Context.MODE_PRIVATE);
    }

    static boolean bool(Context c, String k, boolean d) { return sp(c).getBoolean(k, d); }

    static void put(Context c, String k, boolean v) { sp(c).edit().putBoolean(k, v).apply(); }

    static String str(Context c, String k) { return sp(c).getString(k, ""); }

    static void put(Context c, String k, String v) { sp(c).edit().putString(k, v).apply(); }

    static String token(Context c) { return Secure.dec(str(c, "token")); }

    static void setToken(Context c, String t) { put(c, "token", t.isEmpty() ? "" : Secure.enc(t)); }

    static String projectToken(Context c, String p) { return Secure.dec(str(c, "pt_" + p)); }

    static void setProjectToken(Context c, String p, String t) { put(c, "pt_" + p, t.isEmpty() ? "" : Secure.enc(t)); }

    static String tokenFor(Context c, String project) {
        String t = projectToken(c, project);
        return t.isEmpty() ? token(c) : t;
    }

    static String owner(Context c) { return str(c, "owner"); }

    static void touch(Context c, String project) { sp(c).edit().putLong("opened_" + project, System.currentTimeMillis()).apply(); }

    static long opened(Context c, String project) { return sp(c).getLong("opened_" + project, 0L); }

    static float font(Context c) { return sp(c).getFloat("font", 14f); }

    static int tab(Context c) { return sp(c).getInt("tab", 4); }

    static File root(Context c) {
        String p = str(c, "root");
        File d;
        if (!p.isEmpty()) d = new File(p);
        else if (Build.VERSION.SDK_INT >= 30 && Environment.isExternalStorageManager())
            d = new File(Environment.getExternalStorageDirectory(), "AppProjects");
        else d = new File(c.getExternalFilesDir(null), "projects");
        d.mkdirs();
        return d;
    }
}
