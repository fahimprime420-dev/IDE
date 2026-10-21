package com.fahim.cloudide;

import java.io.File;
import org.json.JSONArray;
import org.json.JSONObject;

/** Per-project GitHub/build settings, stored in .cloudide/project.json (never pushed). */
final class ProjectConfig {
    final File dir;
    String owner = "", repo = "", branch = "", workflow = "build.yml", task = "assembleDebug";
    String gradle = "8.13", jdk = "17", lastStatus = "", preset = "auto", applied = "", artifact = "";
    boolean priv = true;
    JSONObject pushed = new JSONObject();
    /** Newest first; each entry has t (start, ms), ms (duration), result, task, run (number) and url. */
    JSONArray history = new JSONArray();

    ProjectConfig(File d) { dir = d; }

    static ProjectConfig load(File d) {
        ProjectConfig c = new ProjectConfig(d);
        try {
            File f = new File(d, ".cloudide/project.json");
            if (f.exists()) {
                JSONObject j = new JSONObject(Io.read(f));
                c.owner = j.optString("owner");
                c.repo = j.optString("repo");
                c.branch = j.optString("branch");
                c.workflow = j.optString("workflow", "build.yml");
                c.task = j.optString("task", "assembleDebug");
                c.gradle = j.optString("gradle", "8.13");
                c.jdk = j.optString("jdk", "17");
                c.priv = j.optBoolean("private", true);
                c.lastStatus = j.optString("lastStatus");
                c.preset = j.optString("preset", "auto");
                c.applied = j.optString("applied");
                c.artifact = j.optString("artifact");
                JSONObject p = j.optJSONObject("pushed");
                if (p != null) c.pushed = p;
                JSONArray hs = j.optJSONArray("history");
                if (hs != null) c.history = hs;
            }
        } catch (Exception ignored) {
        }
        return c;
    }

    void save() {
        try {
            JSONObject j = new JSONObject();
            j.put("owner", owner).put("repo", repo).put("branch", branch).put("workflow", workflow);
            j.put("task", task).put("gradle", gradle).put("jdk", jdk).put("private", priv);
            j.put("lastStatus", lastStatus).put("pushed", pushed);
            j.put("preset", preset).put("applied", applied).put("artifact", artifact).put("history", history);
            Io.write(new File(dir, ".cloudide/project.json"), j.toString(1));
        } catch (Exception ignored) {
        }
    }

    /** Adds a build to the front of the history and keeps the latest 25. */
    void addHistory(JSONObject entry) {
        JSONArray n = new JSONArray();
        n.put(entry);
        for (int i = 0; i < history.length() && n.length() < 25; i++) n.put(history.opt(i));
        history = n;
    }

    String slug() { return dir.getName().toLowerCase().replaceAll("[^a-z0-9._-]+", "-"); }
}
