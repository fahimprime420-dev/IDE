package com.fahim.cloudide;

import android.content.Context;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

final class Io {
    static byte[] bytes(InputStream in) throws IOException {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        byte[] b = new byte[8192];
        int n;
        while ((n = in.read(b)) > 0) o.write(b, 0, n);
        in.close();
        return o.toByteArray();
    }

    static String read(File f) throws IOException {
        return new String(bytes(new FileInputStream(f)), StandardCharsets.UTF_8);
    }

    static void write(File f, String s) throws IOException {
        if (f.getParentFile() != null) f.getParentFile().mkdirs();
        try (FileOutputStream o = new FileOutputStream(f)) {
            o.write(s.getBytes(StandardCharsets.UTF_8));
        }
    }

    static void delete(File f) {
        File[] k = f.listFiles();
        if (k != null) for (File c : k) delete(c);
        f.delete();
    }

    static void copy(File s, File d) throws IOException {
        if (s.isDirectory()) {
            d.mkdirs();
            File[] k = s.listFiles();
            if (k != null) for (File f : k) copy(f, new File(d, f.getName()));
        } else {
            if (d.getParentFile() != null) d.getParentFile().mkdirs();
            try (FileOutputStream o = new FileOutputStream(d)) {
                o.write(bytes(new FileInputStream(s)));
            }
        }
    }

    static long size(File f) {
        if (f.isFile()) return f.length();
        long n = 0;
        File[] k = f.listFiles();
        if (k != null) for (File c : k) n += size(c);
        return n;
    }

    static String rel(File root, File f) {
        String r = root.getAbsolutePath(), p = f.getAbsolutePath();
        return p.startsWith(r) ? p.substring(Math.min(p.length(), r.length() + 1)) : p;
    }

    static boolean isBinary(String n) {
        return n.toLowerCase(Locale.ROOT).matches(".*\\.(png|jpg|jpeg|gif|webp|ico|jar|aar|apk|aab|zip|so|dex|ttf|otf|keystore|jks|class|mp3|mp4|ogg|pdf|bin)");
    }

    static boolean skipDir(String n) {
        return n.equals("build") || n.equals(".gradle") || n.equals(".git") || n.equals(".idea")
                || n.equals(".cloudide") || n.equals("node_modules");
    }

    static void collect(File dir, List<File> out) {
        File[] k = dir.listFiles();
        if (k == null) return;
        Arrays.sort(k);
        for (File f : k) {
            String n = f.getName();
            if (f.isDirectory()) {
                if (!skipDir(n)) collect(f, out);
            } else if (f.length() < 25L * 1024 * 1024 && !n.equals("local.properties") && !n.endsWith(".iml")) {
                out.add(f);
            }
        }
    }

    static List<File> projectFiles(File root) {
        List<File> l = new ArrayList<>();
        collect(root, l);
        return l;
    }

    /** Git blob SHA-1 of a file, identical to what GitHub stores. */
    static String sha(File f) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-1");
        md.update(("blob " + f.length() + "\0").getBytes("UTF-8"));
        try (FileInputStream in = new FileInputStream(f)) {
            byte[] b = new byte[8192];
            int n;
            while ((n = in.read(b)) > 0) md.update(b, 0, n);
        }
        StringBuilder sb = new StringBuilder();
        for (byte x : md.digest()) sb.append(String.format("%02x", x));
        return sb.toString();
    }

    static void template(Context c, String dir, String rel, File out, Map<String, String> vars, String pkg) throws IOException {
        String full = rel.isEmpty() ? dir : dir + "/" + rel;
        String[] kids = c.getAssets().list(full);
        if (kids != null && kids.length > 0) {
            for (String k : kids) template(c, dir, rel.isEmpty() ? k : rel + "/" + k, out, vars, pkg);
            return;
        }
        String s = new String(bytes(c.getAssets().open(full)), StandardCharsets.UTF_8);
        for (Map.Entry<String, String> e : vars.entrySet()) s = s.replace(e.getKey(), e.getValue());
        String path = rel.replace("PKGPATH", pkg.replace('.', '/')).replace("dot_", ".");
        write(new File(out, path), s);
    }

    /** Extracts a GitHub zipball into out, dropping the top-level folder. */
    static void unzipStrip(byte[] zip, File out) throws IOException {
        out.mkdirs();
        String base = out.getCanonicalPath() + File.separator;
        ZipInputStream zi = new ZipInputStream(new ByteArrayInputStream(zip));
        ZipEntry e;
        while ((e = zi.getNextEntry()) != null) {
            String n = e.getName();
            int i = n.indexOf('/');
            if (i < 0) continue;
            n = n.substring(i + 1);
            if (n.isEmpty()) continue;
            File f = new File(out, n);
            if (!f.getCanonicalPath().startsWith(base)) continue;
            if (e.isDirectory()) {
                f.mkdirs();
                continue;
            }
            f.getParentFile().mkdirs();
            try (FileOutputStream o = new FileOutputStream(f)) {
                byte[] b = new byte[8192];
                int r;
                while ((r = zi.read(b)) > 0) o.write(b, 0, r);
            }
        }
        zi.close();
    }
}
