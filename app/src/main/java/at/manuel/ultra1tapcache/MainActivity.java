package at.manuel.ultra1tapcache;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.ComponentName;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.widget.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class MainActivity extends Activity {
    private final int BG=Color.rgb(7,17,31), CARD=Color.rgb(18,35,54), CYAN=Color.rgb(57,217,244), WHITE=Color.rgb(236,245,255), MUTED=Color.rgb(152,176,199);
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private Future<?> scan;
    private LinearLayout root,list;
    private TextView total,subtitle,progress;
    private Button clean,refresh,usage,access,resume;
    private List<AppEntry> apps=new ArrayList<>();
    private SharedPreferences prefs;
    private boolean scanning, resumed, recheckUsage;
    private int generation;
    private int setupShown;
    private boolean setupPaused;
    private AlertDialog setupDialog;
    private final Runnable update=new Runnable() { public void run() { if(resumed) { updateStatus(); handler.postDelayed(this,500); } } };
    @Override public void onCreate(Bundle state) {
        super.onCreate(state); if(state!=null){setupShown=state.getInt("setup_shown");setupPaused=state.getBoolean("setup_paused");} prefs=getSharedPreferences("settings",MODE_PRIVATE); build();
        if(!prefs.getBoolean("disclosure_seen",false)) disclosure();
    }
    private void build() {
        root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(BG); root.setPadding(dp(20),dp(14),dp(20),dp(10));
        root.setOnApplyWindowInsetsListener((v,insets)-> {
            android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars());
            v.setPadding(dp(20)+bars.left,dp(14)+bars.top,dp(20)+bars.right,dp(10)+bars.bottom); return insets;
        });
        setContentView(root);
        LinearLayout header=new LinearLayout(this); header.setGravity(Gravity.CENTER_VERTICAL);
        ImageView icon=new ImageView(this); icon.setImageResource(at.manuel.ultra1tapcache.R.drawable.app_icon); icon.setScaleType(ImageView.ScaleType.FIT_CENTER); header.addView(icon,new LinearLayout.LayoutParams(dp(46),dp(54)));
        LinearLayout titles=new LinearLayout(this); titles.setOrientation(LinearLayout.VERTICAL); titles.setPadding(dp(12),0,0,0);
        titles.addView(text(getString(R.string.app_name),22,WHITE,true)); titles.addView(text(getString(R.string.ui_001),10,CYAN,true)); header.addView(titles); root.addView(header);
        subtitle=text(getString(R.string.ui_002),13,MUTED,false); subtitle.setPadding(0,dp(12),0,dp(12)); root.addView(subtitle);
        LinearLayout hero=new LinearLayout(this); hero.setOrientation(LinearLayout.VERTICAL); hero.setPadding(dp(18),dp(16),dp(18),dp(16)); hero.setBackground(round(CARD,18));
        hero.addView(text(getString(R.string.ui_003),11,CYAN,true)); total=text("-",34,WHITE,true); hero.addView(total);
        progress=text(getString(R.string.ui_004),12,MUTED,false); hero.addView(progress); root.addView(hero);
        TextView selectionHint=text(getString(R.string.large_default_hint),11,MUTED,false);root.addView(selectionHint);
        LinearLayout permissions=new LinearLayout(this); usage=button(getString(R.string.ui_005),()->openUsage()); access=button(getString(R.string.ui_006),()->showAccessibilitySetup());
        permissions.addView(usage,new LinearLayout.LayoutParams(0,dp(48),1)); permissions.addView(access,new LinearLayout.LayoutParams(0,dp(48),1)); root.addView(permissions);
        Button setup=button(getString(R.string.setup_help),()->showAndroidSetupHelp());
        root.addView(setup,new LinearLayout.LayoutParams(-1,dp(44)));
        LinearLayout shortcuts=new LinearLayout(this);
        shortcuts.addView(button(getString(R.string.all_apps_settings),()->openAllApps()),new LinearLayout.LayoutParams(0,dp(48),1));
        shortcuts.addView(button(getString(R.string.open_app_info),()->openAppInfo()),new LinearLayout.LayoutParams(0,dp(48),1));root.addView(shortcuts);
        root.addView(button(getString(R.string.diag_title),()->showRunDiagnostics()),new LinearLayout.LayoutParams(-1,dp(44)));
        LinearLayout toggles=new LinearLayout(this); toggles.setOrientation(LinearLayout.VERTICAL);
        toggles.addView(text(getString(R.string.cleaning_speed),13,WHITE,true));
        Spinner speed=new Spinner(this);String[] speedKeys={"normal","fast","turbo"};
        ArrayAdapter<String> adapter=new ArrayAdapter<>(this,android.R.layout.simple_spinner_item,new String[]{getString(R.string.speed_normal),getString(R.string.speed_fast),getString(R.string.speed_turbo)}) { @Override public View getView(int pos,View convert,android.view.ViewGroup parent){View v=super.getView(pos,convert,parent);((TextView)v).setTextColor(WHITE);return v;} };adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);speed.setAdapter(adapter);
        String savedSpeed=prefs.getString("speed",prefs.getBoolean("turbo",true)?"turbo":"normal");speed.setSelection("normal".equals(savedSpeed)||"slow".equals(savedSpeed)?0:"fast".equals(savedSpeed)?1:2);
        speed.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onNothingSelected(AdapterView<?> p){}public void onItemSelected(AdapterView<?> p,View v,int pos,long id){prefs.edit().putString("speed",speedKeys[pos]).apply();}});
        toggles.addView(speed,new LinearLayout.LayoutParams(-1,dp(44)));
        toggles.addView(toggle(getString(R.string.ui_008), "ask", true, null));
        toggles.addView(toggle(getString(R.string.ui_009), "show_zero", false, ()->{ renderList(); updateStatus(); })); root.addView(toggles);
        LinearLayout commands=new LinearLayout(this);
        refresh=button(getString(R.string.ui_010),()->startScan()); Button all=button(getString(R.string.ui_011),()-> {
            boolean select=apps.stream().noneMatch(a->a.selected); for(AppEntry a:apps) a.selected=select && !a.system && a.enabled && a.cacheBytes!=null && a.cacheBytes>0;
            renderList(); updateStatus();
        }); commands.addView(refresh,new LinearLayout.LayoutParams(0,dp(46),1)); commands.addView(all,new LinearLayout.LayoutParams(0,dp(46),1)); root.addView(commands);
        Button allSystem=button(getString(R.string.select_all_system),()->confirmSelectAll());
        root.addView(allSystem,new LinearLayout.LayoutParams(-1,dp(44)));
        resume=button(getString(R.string.resume_cleaning),()->requestResume());root.addView(resume,new LinearLayout.LayoutParams(-1,dp(44)));
        ScrollView scroll=new ScrollView(this); list=new LinearLayout(this); list.setOrientation(LinearLayout.VERTICAL); scroll.addView(list); root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        clean=button(getString(R.string.ui_012),()->requestClean()); clean.setTextColor(BG); clean.setBackground(round(CYAN,14)); root.addView(clean,new LinearLayout.LayoutParams(-1,dp(58)));
        TextView footer=text(getString(R.string.ui_013),10,MUTED,false); footer.setPadding(0,dp(8),0,0); root.addView(footer);
    }
    private Switch toggle(String title,String key,boolean fallback,Runnable changed) {
        Switch s=new Switch(this); s.setText(title); s.setTextSize(13); s.setTextColor(WHITE); s.setChecked(prefs.getBoolean(key,fallback)); s.setPadding(0,dp(4),0,dp(4));
        s.setOnCheckedChangeListener((v,on)-> { prefs.edit().putBoolean(key,on).apply(); if(changed!=null) changed.run(); }); return s;
    }
    private void disclosure() {
        new AlertDialog.Builder(this).setTitle(getString(R.string.ui_014)).setMessage(getString(R.string.ui_015)).setPositiveButton(getString(R.string.ui_016),(d,w)->{prefs.edit().putBoolean("disclosure_seen",true).apply();handler.post(()->maybeGuideSetup());}).setCancelable(false).show();
    }
    static int missingSetupStep(boolean usageReady,boolean helperReady){return !usageReady?1:!helperReady?2:0;}
    static boolean restrictedSetupPossible(int sdk,String installer,boolean helperEnabled){return sdk>=33&&!helperEnabled&&!"com.android.vending".equals(installer);}
    private boolean possibleRestrictedSettings(){
        String installer=null;
        try{installer=getPackageManager().getInstallSourceInfo(getPackageName()).getInstallingPackageName();}catch(android.content.pm.PackageManager.NameNotFoundException|RuntimeException ignored){}
        return restrictedSetupPossible(android.os.Build.VERSION.SDK_INT,installer,accessibilityEnabled());
    }
    private void maybeGuideSetup(){
        if(!resumed||isFinishing()||CleanerService.running||setupPaused||!prefs.getBoolean("disclosure_seen",false)||setupDialog!=null)return;
        int step=missingSetupStep(AppScanner.hasUsage(this)&&AppScanner.readState!=AppScanner.DENIED,accessibilityEnabled()&&CleanerService.connected!=null);
        if(possibleRestrictedSettings()&&(setupShown&4)==0)step=4;
        if(step==0||(setupShown&step)!=0)return;
        final int targetStep=step;
        setupShown|=step;
        final boolean[] jumping={false};
        setupDialog=new AlertDialog.Builder(this).setTitle(getString(R.string.guided_setup_title))
            .setMessage(getString(step==4?R.string.restricted_possible:step==1?R.string.guided_usage:R.string.guided_helper))
            .setPositiveButton(getString(step==4?R.string.open_app_info:step==1?R.string.ui_037:R.string.ui_021),(d,w)->{
                jumping[0]=true;if(targetStep==4)openAppInfo();else if(targetStep==1)launchUsage(true);else {recheckUsage=true;try{startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));}catch(RuntimeException e){showFeedback(getString(R.string.ui_023));}}
            })
            .setNeutralButton(getString(targetStep==4?R.string.setup_help:R.string.open_app_info),(d,w)->{jumping[0]=true;if(targetStep==4)showAndroidSetupHelp();else openAppInfo();})
            .setNegativeButton(getString(R.string.ui_025),null).create();
        setupDialog.setOnDismissListener(d->{setupDialog=null;if(!jumping[0])setupPaused=true;});setupDialog.show();
    }
    @Override protected void onSaveInstanceState(Bundle out){super.onSaveInstanceState(out);out.putInt("setup_shown",setupShown);out.putBoolean("setup_paused",setupPaused);}
    private boolean accessibilityEnabled() {
        String enabled=Settings.Secure.getString(getContentResolver(),Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if(enabled==null) return false;
        ComponentName target=new ComponentName(this,CleanerService.class);
        for(String value:enabled.split(":")) if(target.equals(ComponentName.unflattenFromString(value))) return true;
        return false;
    }
    private void showAccessibilitySetup() {
        boolean enabled=accessibilityEnabled();
        String disconnected=enabled?getString(R.string.ui_017):"";
        new AlertDialog.Builder(this).setTitle(enabled?getString(R.string.ui_018):getString(R.string.ui_019))
            .setMessage(disconnected+getString(R.string.ui_020))
            .setPositiveButton(getString(R.string.ui_021),(d,w)-> {
                try { startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)); }
                catch(RuntimeException e) { new AlertDialog.Builder(this).setTitle(getString(R.string.ui_022)).setMessage(getString(R.string.ui_023)).setPositiveButton(getString(R.string.ui_024),null).show(); }
            }).setNeutralButton(getString(R.string.open_app_info),(d,w)->showAndroidSetupHelp()).setNegativeButton(getString(R.string.ui_025),null).show();
    }
    private void showRunDiagnostics(){
        String report=RunJournal.diagnostics(this);TextView content=text(report,13,WHITE,false);content.setTextIsSelectable(true);content.setPadding(dp(16),dp(8),dp(16),dp(8));ScrollView scroll=new ScrollView(this);scroll.addView(content);
        new AlertDialog.Builder(this).setTitle(getString(R.string.diag_title)).setView(scroll).setMessage(getString(R.string.diag_privacy))
            .setPositiveButton(getString(R.string.diag_share),(d,w)->{Intent send=new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_SUBJECT,getString(R.string.diag_title)).putExtra(Intent.EXTRA_TEXT,report);try{startActivity(Intent.createChooser(send,getString(R.string.diag_share)));}catch(RuntimeException e){showFeedback(getString(R.string.ui_040));}})
            .setNegativeButton(getString(R.string.ui_024),null).show();
    }
    private void openAllApps(){
        try{startActivity(new Intent(Settings.ACTION_MANAGE_ALL_APPLICATIONS_SETTINGS));}
        catch(RuntimeException e){try{startActivity(new Intent(Settings.ACTION_APPLICATION_SETTINGS));}catch(RuntimeException fallback){showFeedback(getString(R.string.ui_040));}}
    }
    private void openAppInfo() {
        recheckUsage=true;
        try { startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName()))); }
        catch(RuntimeException e) { recheckUsage=false; showFeedback(getString(R.string.ui_040)); }
    }
    private void showAndroidSetupHelp() {
        new AlertDialog.Builder(this).setTitle(getString(R.string.setup_title))
            .setMessage(getString(R.string.setup_steps))
            .setPositiveButton(getString(R.string.open_app_info),(d,w)->openAppInfo())
            .setNegativeButton(getString(R.string.ui_025),null).show();
    }
    private void showFeedback(String message) {
        new AlertDialog.Builder(this).setTitle(getString(R.string.ui_026)).setMessage(message).setPositiveButton(getString(R.string.ui_024),null).show();
    }
    private void openUsage() {
        String state=AppScanner.readState==AppScanner.VERIFIED?getString(R.string.ui_027):
            AppScanner.readState==AppScanner.DENIED?(AppScanner.hasUsage(this)?getString(R.string.ui_028):getString(R.string.ui_029)):
            getString(R.string.ui_030);
        String details=state+getString(R.string.ui_031)+AppScanner.modeName(AppScanner.usageMode(this))+getString(R.string.ui_032)+android.os.Process.myUid()
            +getString(R.string.ui_033)+getPackageName()+getString(R.string.ui_034)+String.valueOf(AppScanner.probePackage)
            +(AppScanner.probeError==null?"":"\n\n"+AppScanner.probeError);
        new AlertDialog.Builder(this).setTitle(getString(R.string.ui_035))
            .setMessage(details+getString(R.string.ui_036))
            .setPositiveButton(getString(R.string.ui_037),(d,w)->launchUsage(true))
            .setNeutralButton(getString(R.string.ui_038),(d,w)->launchUsage(false))
            .setNegativeButton(getString(R.string.ui_039),(d,w)->showAndroidSetupHelp()).show();
    }
    private void launchUsage(boolean specific) {
        recheckUsage=true;
        Intent intent=new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS);
        if(specific) intent.setData(Uri.parse("package:"+getPackageName()));
        try { startActivity(intent); }
        catch(RuntimeException e) {
            try { startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)); }
            catch(RuntimeException fallback) { recheckUsage=false; showFeedback(getString(R.string.ui_041)); }
        }
    }
    private void startScan() {
        if(CleanerService.running) return;
        generation++; int token=generation;
        if(scan!=null) scan.cancel(true); scanning=true; apps=new ArrayList<>(); renderList(); updateStatus();

        scan=worker.submit(()-> {
            List<AppEntry> found=AppScanner.scan(this,false);
            handler.post(()-> { if(isDestroyed() || token!=generation) return; apps=found; selectLargeByDefault(apps);RunJournal.apply(this,apps); scanning=false; renderList(); updateStatus(); maybeGuideSetup(); });
        });
    }
    private void renderList() {
        list.removeAllViews();
        if(apps.isEmpty()) { TextView t=text(scanning?getString(R.string.ui_042):getString(R.string.ui_043),14,MUTED,false); t.setPadding(0,dp(24),0,dp(24)); list.addView(t); return; }
        int visible=0;
        for(AppEntry a:apps) {
            if(a.cacheBytes!=null && a.cacheBytes==0 && !prefs.getBoolean("show_zero",false)) continue;
            visible++;
            CheckBox row=new CheckBox(this); row.setText(a.label+(RunJournal.smallSkipped(this,a.packageName)?getString(R.string.small_saved):RunJournal.completed(this,a.packageName)?getString(R.string.done_saved):"")+(a.system?getString(R.string.ui_044):"")+(!a.enabled?getString(R.string.ui_045):"")+getString(R.string.ui_046)+(a.cacheBytes==null?getString(R.string.ui_047):format(a.cacheBytes))+"  •  "+a.packageName); row.setTextSize(13); row.setTextColor(WHITE); row.setChecked(a.selected); row.setEnabled(a.enabled && a.cacheBytes!=null && a.cacheBytes>0); row.setPadding(dp(6),dp(8),dp(6),dp(8));
            row.setOnCheckedChangeListener((v,on)-> { a.selected=on; updateStatus(); }); list.addView(row,new LinearLayout.LayoutParams(-1,-2));
        }
        if(visible==0) { TextView t=text(getString(R.string.ui_048),13,MUTED,false); list.addView(t); }
    }
    static void selectLargeByDefault(List<AppEntry> entries){for(AppEntry a:entries)a.selected=!a.system&&a.enabled&&a.cacheBytes!=null&&a.cacheBytes>1048576;}
    static void selectAllMeasured(List<AppEntry> entries) {
        for(AppEntry a:entries) a.selected=a.enabled && a.cacheBytes!=null && a.cacheBytes>0;
    }
    private void confirmSelectAll() {
        if(scanning || CleanerService.running) return;
        new AlertDialog.Builder(this).setTitle(getString(R.string.select_system_title))
            .setMessage(getString(R.string.select_system_body))
            .setPositiveButton(getString(R.string.select_confirm),(d,w)-> { selectAllMeasured(apps); renderList(); updateStatus(); })
            .setNegativeButton(getString(R.string.ui_025),null).show();
    }
    private void requestResume(){
        if(CleanerService.running||scanning)return;
        if(CleanerService.connected==null){showAccessibilitySetup();return;}
        int remaining=RunJournal.remaining(this).size();if(remaining==0)return;
        new AlertDialog.Builder(this).setTitle(getString(R.string.resume_cleaning))
            .setMessage(getString(R.string.resume_explain)+"\n"+remaining+" "+getString(R.string.remaining_apps))
            .setPositiveButton(getString(R.string.resume_cleaning),(d,w)->{if(!CleanerService.resume(prefs.getBoolean("turbo",true)))showFeedback(getString(R.string.resume_failed));updateStatus();})
            .setNegativeButton(getString(R.string.ui_025),null).show();
    }
    private String journalReason(String reason){
        if("running".equals(reason)&&!CleanerService.running)return getString(R.string.process_stopped);
        if("finished".equals(reason))return getString(R.string.ui_086);
        if("user_stop".equals(reason))return getString(R.string.ui_083);
        if("measurement_timeout".equals(reason)||"baseline_timeout".equals(reason))return getString(R.string.measure_timeout);
        if("service_destroyed".equals(reason))return getString(R.string.ui_088);
        if("service_interrupted".equals(reason))return getString(R.string.ui_087);
        if("overlay_missing".equals(reason))return getString(R.string.ui_085);
        return getString(R.string.engine_error)+" ("+reason+")";
    }
    private void requestClean() {
        if(CleanerService.running) { CleanerService.cancel(); return; }
        if(scanning) { showFeedback(getString(R.string.ui_049)); return; }
        if(CleanerService.connected==null) { showAccessibilitySetup(); return; }
        List<AppEntry> selected=new ArrayList<>(); for(AppEntry a:apps) if(a.selected) selected.add(a);
        if(selected.isEmpty()) { showFeedback(getString(R.string.ui_050)); return; }
        Runnable start=()-> {
            if(CleanerService.connected==null) { showAccessibilitySetup(); return; }
            if(!CleanerService.start(selected,prefs.getBoolean("turbo",true))) showFeedback(getString(R.string.ui_051) + CleanerService.status + getString(R.string.ui_052));
            updateStatus();
        };
        if(prefs.getBoolean("ask",true)) new AlertDialog.Builder(this).setTitle(selected.size()+getString(R.string.ui_053)).setMessage(getString(R.string.ui_054)).setPositiveButton(getString(R.string.ui_055),(d,w)->start.run()).setNegativeButton(getString(R.string.ui_025),null).show(); else start.run();
    }
    private void updateStatus() {
        if(total==null) return;
        long bytes=0;int unknown=0, selected=0, zero=0; for(AppEntry a:apps) { if(a.cacheBytes==null) unknown++; else { bytes+=a.cacheBytes; if(a.cacheBytes==0)zero++; } if(a.selected) selected++; }
        total.setText(scanning?getString(R.string.ui_056):AppScanner.readState==AppScanner.DENIED?getString(R.string.ui_057):format(bytes));
        org.json.JSONObject journal=RunJournal.read(this);
        boolean result=CleanerService.attempted>0;
        progress.setText(CleanerService.running?CleanerService.status:scanning?getString(R.string.ui_058):result?
            CleanerService.status+getString(R.string.ui_046)+format(CleanerService.reclaimed)+getString(R.string.ui_059)+CleanerService.verified+getString(R.string.ui_060)+CleanerService.clicked+getString(R.string.ui_061)+CleanerService.skipped+getString(R.string.ui_062):apps.size()+getString(R.string.ui_063)+selected+getString(R.string.ui_064)+zero+getString(R.string.ui_065)+unknown+getString(R.string.ui_066));
        if(!CleanerService.running && !scanning && AppScanner.readState==AppScanner.DENIED)
            progress.append(getString(R.string.ui_067)+getString(R.string.ui_068));
        else if(!CleanerService.running && !scanning && unknown>0 && AppScanner.lastError!=null) progress.append(getString(R.string.ui_069)+AppScanner.lastError);
        usage.setText(scanning?getString(R.string.ui_070):AppScanner.readState==AppScanner.VERIFIED?getString(R.string.ui_071):AppScanner.readState==AppScanner.DENIED?getString(R.string.ui_072):getString(R.string.ui_073)); boolean enabled=accessibilityEnabled();
        access.setText(CleanerService.connected!=null?getString(R.string.ui_074):enabled?getString(R.string.ui_075):getString(R.string.ui_076));
        if(!CleanerService.running && !scanning && CleanerService.connected==null)
            progress.append(enabled?getString(R.string.ui_077):getString(R.string.ui_078));
        if(!CleanerService.running && journal.has("rows")) {
            int remaining=RunJournal.remaining(this).size();
            progress.append("\n"+journalReason(journal.optString("reason"))+"\n"+RunJournal.count(this,"confirmed")+" "+getString(R.string.confirmed_saved)+" • "+RunJournal.count(this,"small_cache_skipped")+" "+getString(R.string.small_count)+" • "+remaining+" "+getString(R.string.remaining_apps));
            String current=journal.optString("current");if(!current.isEmpty() && !"finished".equals(journal.optString("reason")))progress.append("\n"+getString(R.string.last_app)+": "+current);
        }
        resume.setVisibility(!CleanerService.running && !RunJournal.remaining(this).isEmpty()?View.VISIBLE:View.GONE);resume.setEnabled(!scanning);
        clean.setText(CleanerService.running?getString(R.string.ui_079):getString(R.string.ui_080)+selected+getString(R.string.ui_081)); clean.setEnabled(true); refresh.setEnabled(!scanning && !CleanerService.running);
    }
    @Override protected void onResume() { super.onResume(); resumed=true; RunJournal.apply(this,apps); renderList(); handler.post(update); handler.post(()->maybeGuideSetup()); if(recheckUsage && !CleanerService.running) { recheckUsage=false; startScan(); } else if(apps.isEmpty() && !scanning && !CleanerService.running) startScan(); }
    @Override protected void onPause() { resumed=false; handler.removeCallbacks(update); super.onPause(); }
    @Override protected void onDestroy() { generation++; if(scan!=null) scan.cancel(true); worker.shutdownNow(); handler.removeCallbacksAndMessages(null); super.onDestroy(); }
    private TextView text(String s,int sp,int color,boolean bold) { TextView t=new TextView(this); t.setText(s); t.setTextSize(sp); t.setTextColor(color); if(bold)t.setTypeface(null,Typeface.BOLD); return t; }
    private Button button(String s,Runnable action) { Button b=new Button(this); b.setText(s); b.setTextSize(12); b.setAllCaps(false); b.setTextColor(WHITE); b.setOnClickListener(v->action.run()); return b; }
    private GradientDrawable round(int color,int radius) { GradientDrawable d=new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radius)); return d; }
    private int dp(int v) { return Math.round(v*getResources().getDisplayMetrics().density); }
    static String format(long bytes) { if(bytes<1024)return bytes+" B"; if(bytes<1048576)return String.format(java.util.Locale.getDefault(),"%.1f KB",bytes/1024d); if(bytes<1073741824)return String.format(java.util.Locale.getDefault(),"%.1f MB",bytes/1048576d); return String.format(java.util.Locale.getDefault(),"%.2f GB",bytes/1073741824d); }
}
