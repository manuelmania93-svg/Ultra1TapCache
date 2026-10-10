package at.manuel.ultra1tapcache;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

/** Local checkpoint for one user-started session. Never starts automation by itself. */
final class RunJournal {
    private static SharedPreferences prefs(Context c) { return c.getSharedPreferences("clean_run",Context.MODE_PRIVATE); }
    static JSONObject read(Context c) {
        try { return new JSONObject(prefs(c).getString("run","{}")); }
        catch(Exception e) { return new JSONObject(); }
    }
    private static void write(Context c,JSONObject run) {
        if(!prefs(c).edit().putString("run",run.toString()).commit()) throw new IllegalStateException("Checkpoint write failed");
    }
    static void begin(Context c,List<AppEntry> apps) {
        try {
            JSONObject run=new JSONObject();JSONArray rows=new JSONArray();
            for(AppEntry a:apps) { JSONObject row=new JSONObject();row.put("pkg",a.packageName);row.put("label",a.label);row.put("system",a.system);row.put("baseline",a.cacheBytes);row.put("state","pending");rows.put(row); }
            run.put("rows",rows);run.put("reason","running");run.put("bytes",0L);write(c,run);
        } catch(org.json.JSONException e) { throw new IllegalStateException(e); }
    }
    static void baseline(Context c,String pkg,long bytes){JSONObject run=read(c);JSONArray rows=run.optJSONArray("rows");if(rows==null)return;try{for(int i=0;i<rows.length();i++){JSONObject row=rows.getJSONObject(i);if(pkg.equals(row.optString("pkg"))){row.put("baseline",bytes);break;}}write(c,run);}catch(org.json.JSONException e){throw new IllegalStateException(e);}}
    static void state(Context c,String pkg,String state,Long after,long reclaimed) {
        JSONObject run=read(c);JSONArray rows=run.optJSONArray("rows");if(rows==null)return;
        try {
            for(int i=0;i<rows.length();i++) { JSONObject row=rows.getJSONObject(i);if(pkg.equals(row.optString("pkg"))) {row.put("state",state);if(after!=null)row.put("after",after);break;} }
            run.put("bytes",run.optLong("bytes")+Math.max(0,reclaimed));run.put("current",pkg);write(c,run);
        } catch(org.json.JSONException e) { throw new IllegalStateException(e); }
    }
    static void reason(Context c,String reason) {JSONObject run=read(c);try {run.put("reason",reason);write(c,run);}catch(org.json.JSONException e){throw new IllegalStateException(e);} }
    static boolean done(String state) {return "confirmed".equals(state)||"already_empty".equals(state);}
    static List<AppEntry> remaining(Context c) {
        List<AppEntry> result=new ArrayList<>();JSONArray rows=read(c).optJSONArray("rows");if(rows==null)return result;
        for(int i=0;i<rows.length();i++) {JSONObject row=rows.optJSONObject(i);if(row==null||done(row.optString("state")))continue;
            result.add(new AppEntry(row.optString("pkg"),row.optString("label"),row.has("baseline")?row.optLong("baseline"):null,row.optBoolean("system"),true));}
        return result;
    }
    static int count(Context c,String state) {int count=0;JSONArray rows=read(c).optJSONArray("rows");if(rows!=null)for(int i=0;i<rows.length();i++){JSONObject r=rows.optJSONObject(i);if(r!=null&&state.equals(r.optString("state")))count++;}return count;}
    static void apply(Context c,List<AppEntry> apps) {JSONArray rows=read(c).optJSONArray("rows");if(rows==null)return;
        for(AppEntry a:apps)for(int i=0;i<rows.length();i++){JSONObject r=rows.optJSONObject(i);if(r!=null&&a.packageName.equals(r.optString("pkg"))&&done(r.optString("state"))){if(r.has("after"))a.cacheBytes=r.optLong("after");a.selected=false;}}
    }
}
