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
            for(int i=0;i<rows.length();i++) { JSONObject row=rows.getJSONObject(i);if(pkg.equals(row.optString("pkg"))) {row.put("state",state);if(after!=null)row.put("after",after);if("confirmed".equals(state)&&after!=null)row.put("residual_baseline",after);break;} }
            run.put("bytes",run.optLong("bytes")+Math.max(0,reclaimed));run.put("current",pkg);write(c,run);
        } catch(org.json.JSONException e) { throw new IllegalStateException(e); }
    }
    static void measurement(Context c,String pkg,Long after,int reads,long elapsed){JSONObject run=read(c);JSONArray rows=run.optJSONArray("rows");if(rows==null)return;try{for(int i=0;i<rows.length();i++){JSONObject row=rows.getJSONObject(i);if(pkg.equals(row.optString("pkg"))){row.put("after",after==null?JSONObject.NULL:after);row.put("measurement_reads",reads);row.put("click_to_read_ms",elapsed);break;}}write(c,run);}catch(org.json.JSONException e){throw new IllegalStateException(e);}}
    static void clickInfo(Context c,String pkg,boolean enabled,Boolean accepted,int attempt){JSONObject run=read(c);JSONArray rows=run.optJSONArray("rows");if(rows==null)return;try{for(int i=0;i<rows.length();i++){JSONObject row=rows.getJSONObject(i);if(pkg.equals(row.optString("pkg"))){row.put("button_enabled",enabled);if(!enabled)row.put("disabled_seen",true);if(accepted!=null)row.put("click_accepted",accepted);row.put("clean_attempt",attempt);break;}}write(c,run);}catch(org.json.JSONException e){throw new IllegalStateException(e);}}
    static void detail(Context c,String pkg,String key,String value){JSONObject run=read(c);JSONArray rows=run.optJSONArray("rows");if(rows==null)return;try{for(int i=0;i<rows.length();i++){JSONObject r=rows.getJSONObject(i);if(pkg.equals(r.optString("pkg"))){r.put(key,CleanerService.cut(value,1200));break;}}write(c,run);}catch(org.json.JSONException e){throw new IllegalStateException(e);}}
    static boolean residual(Context c,String pkg){JSONArray rows=read(c).optJSONArray("rows");if(rows!=null)for(int i=0;i<rows.length();i++){JSONObject r=rows.optJSONObject(i);if(r!=null&&pkg.equals(r.optString("pkg")))return "confirmed".equals(r.optString("state"))&&r.optLong("after")>0;}return false;}
    static void reason(Context c,String reason) {JSONObject run=read(c);try {run.put("reason",reason);write(c,run);}catch(org.json.JSONException e){throw new IllegalStateException(e);} }
    static boolean done(String state) {return "confirmed".equals(state)||"already_empty".equals(state)||"small_cache_skipped".equals(state);}
    static List<AppEntry> remaining(Context c) {
        List<AppEntry> result=new ArrayList<>();JSONArray rows=read(c).optJSONArray("rows");if(rows==null)return result;
        for(int i=0;i<rows.length();i++) {JSONObject row=rows.optJSONObject(i);if(row==null||done(row.optString("state")))continue;
            result.add(new AppEntry(row.optString("pkg"),row.optString("label"),row.has("baseline")?row.optLong("baseline"):null,row.optBoolean("system"),true));}
        return result;
    }
    static int count(Context c,String state) {int count=0;JSONArray rows=read(c).optJSONArray("rows");if(rows!=null)for(int i=0;i<rows.length();i++){JSONObject r=rows.optJSONObject(i);if(r!=null&&state.equals(r.optString("state")))count++;}return count;}
    static String reasonLabel(Context c,String state){
        int id="small_cache_skipped".equals(state)?R.string.diag_small:"tiny_decrease".equals(state)?R.string.diag_tiny:"confirmed".equals(state)?R.string.diag_confirmed:"already_empty".equals(state)?R.string.diag_empty:"unconfirmed".equals(state)?R.string.diag_unchanged:"unmeasurable".equals(state)?R.string.diag_null:"cache_click_rejected".equals(state)?R.string.diag_click_rejected:"cache_button_disabled".equals(state)?R.string.diag_disabled:"cache_button_not_found".equals(state)?R.string.diag_not_found:"settings_timeout".equals(state)?R.string.diag_timeout:"settings_failed".equals(state)?R.string.diag_settings_failed:"clicked".equals(state)?R.string.diag_clicked:"started".equals(state)?R.string.diag_started:R.string.diag_pending;
        return c.getString(id)+" ["+state+"]";
    }
    static String diagnostics(Context c){JSONObject run=read(c);JSONArray rows=run.optJSONArray("rows");if(rows==null)return c.getString(R.string.diag_no_run);
        java.util.Map<String,Integer> counts=new java.util.TreeMap<>();for(int i=0;i<rows.length();i++){JSONObject r=rows.optJSONObject(i);if(r!=null){String key=r.optString("state","pending");counts.put(key,counts.getOrDefault(key,0)+1);}}
        StringBuilder s=new StringBuilder(c.getString(R.string.diag_title)+"\n"+c.getString(R.string.ui_001)+"\n"+c.getString(R.string.diag_run_reason)+": "+run.optString("reason")+"\n");
        for(java.util.Map.Entry<String,Integer> entry:counts.entrySet())s.append(entry.getValue()).append(" × ").append(reasonLabel(c,entry.getKey())).append("\n");
        for(int i=0;i<rows.length();i++){JSONObject r=rows.optJSONObject(i);if(r==null)continue;s.append("\n").append(r.optString("label")).append(" ( ").append(r.optString("pkg")).append(" )\n").append(reasonLabel(c,r.optString("state"))).append("\n").append(c.getString(R.string.diag_baseline)).append(": ").append(r.has("baseline")&&!r.isNull("baseline")?r.optLong("baseline")+" B":"?").append("; ").append(c.getString(R.string.diag_after)).append(": ").append(r.has("after")&&!r.isNull("after")?r.optLong("after")+" B":"?").append("\n").append(c.getString(R.string.diag_reads)).append(": ").append(r.optInt("measurement_reads")).append("; ").append(c.getString(R.string.diag_elapsed)).append(": ").append(r.has("click_to_read_ms")?r.optLong("click_to_read_ms")+" ms":"?").append("\n").append(c.getString(R.string.diag_click)).append(": ").append(r.has("click_accepted")?r.optBoolean("click_accepted"):"?").append("; ").append(c.getString(R.string.diag_enabled)).append(": ").append(r.has("button_enabled")?r.optBoolean("button_enabled"):"?").append("; ").append(c.getString(R.string.diag_disabled_seen)).append(": ").append(r.optBoolean("disabled_seen")).append("; ").append(c.getString(R.string.diag_attempt)).append(": ").append(r.optInt("clean_attempt")).append("\n");if("confirmed".equals(r.optString("state"))&&r.optLong("after")>0)s.append(c.getString(R.string.residual_hint)).append("\n");for(String key:new String[]{"matched_cache","clicked_node","screen_after","dialog_result"})if(r.has(key))s.append(key).append(": ").append(r.optString(key)).append("\n");}
        return s.toString();
    }
    static boolean smallSkipped(Context c,String pkg){JSONArray rows=read(c).optJSONArray("rows");if(rows!=null)for(int i=0;i<rows.length();i++){JSONObject r=rows.optJSONObject(i);if(r!=null&&pkg.equals(r.optString("pkg")))return "small_cache_skipped".equals(r.optString("state"));}return false;}
    static boolean completed(Context c,String pkg){JSONArray rows=read(c).optJSONArray("rows");if(rows!=null)for(int i=0;i<rows.length();i++){JSONObject r=rows.optJSONObject(i);if(r!=null&&pkg.equals(r.optString("pkg")))return done(r.optString("state"));}return false;}
    static void apply(Context c,List<AppEntry> apps) {JSONArray rows=read(c).optJSONArray("rows");if(rows==null)return;
        for(AppEntry a:apps)for(int i=0;i<rows.length();i++){JSONObject r=rows.optJSONObject(i);if(r!=null&&a.packageName.equals(r.optString("pkg"))&&done(r.optString("state"))){a.selected=false;}}
    }
}
