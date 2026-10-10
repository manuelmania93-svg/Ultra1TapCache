package at.manuel.ultra1tapcache;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
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
    private Button clean,refresh,usage,access;
    private List<AppEntry> apps=new ArrayList<>();
    private SharedPreferences prefs;
    private boolean scanning, resumed;
    private int generation;
    private final Runnable update=new Runnable() { public void run() { if(resumed) { updateStatus(); handler.postDelayed(this,500); } } };
    @Override public void onCreate(Bundle state) {
        super.onCreate(state); prefs=getSharedPreferences("settings",MODE_PRIVATE); build();
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
        titles.addView(text("Ultra1TapCache",22,WHITE,true)); titles.addView(text("EXPERIMENT • TURBO • CLEAN-FIX 1",10,CYAN,true)); header.addView(titles); root.addView(header);
        subtitle=text("Nur Cache. Keine App-Daten.",13,MUTED,false); subtitle.setPadding(0,dp(12),0,dp(12)); root.addView(subtitle);
        LinearLayout hero=new LinearLayout(this); hero.setOrientation(LinearLayout.VERTICAL); hero.setPadding(dp(18),dp(16),dp(18),dp(16)); hero.setBackground(round(CARD,18));
        hero.addView(text("GEMESSENER CACHE",11,CYAN,true)); total=text("-",34,WHITE,true); hero.addView(total);
        progress=text("Nutzungszugriff aktivieren, dann scannen.",12,MUTED,false); hero.addView(progress); root.addView(hero);
        LinearLayout permissions=new LinearLayout(this); usage=button("1  Nutzungszugriff",()->openUsage()); access=button("2  Bedienungshilfe",()->showAccessibilitySetup());
        permissions.addView(usage,new LinearLayout.LayoutParams(0,dp(48),1)); permissions.addView(access,new LinearLayout.LayoutParams(0,dp(48),1)); root.addView(permissions);
        LinearLayout toggles=new LinearLayout(this); toggles.setOrientation(LinearLayout.VERTICAL);
        toggles.addView(toggle("Vor jeder Reinigung fragen", "ask", true, null));
        toggles.addView(toggle("System-Apps anzeigen (vorsichtig)", "system", false, ()->startScan())); root.addView(toggles);
        LinearLayout commands=new LinearLayout(this);
        refresh=button("Neu scannen",()->startScan()); Button all=button("Alle / keine",()-> {
            boolean select=apps.stream().noneMatch(a->a.selected); for(AppEntry a:apps) a.selected=select && a.cacheBytes!=null && a.cacheBytes>0;
            renderList(); updateStatus();
        }); commands.addView(refresh,new LinearLayout.LayoutParams(0,dp(46),1)); commands.addView(all,new LinearLayout.LayoutParams(0,dp(46),1)); root.addView(commands);
        ScrollView scroll=new ScrollView(this); list=new LinearLayout(this); list.setOrientation(LinearLayout.VERTICAL); scroll.addView(list); root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        clean=button("1 TAP • CACHE LEEREN",()->requestClean()); clean.setTextColor(BG); clean.setBackground(round(CYAN,14)); root.addView(clean,new LinearLayout.LayoutParams(-1,dp(58)));
        TextView footer=text("Cache wird von Android verwaltet. Kein Geschwindigkeitsversprechen.",10,MUTED,false); footer.setPadding(0,dp(8),0,0); root.addView(footer);
    }
    private Switch toggle(String title,String key,boolean fallback,Runnable changed) {
        Switch s=new Switch(this); s.setText(title); s.setTextSize(13); s.setTextColor(WHITE); s.setChecked(prefs.getBoolean(key,fallback)); s.setPadding(0,dp(4),0,dp(4));
        s.setOnCheckedChangeListener((v,on)-> { prefs.edit().putBoolean(key,on).apply(); if(changed!=null) changed.run(); }); return s;
    }
    private void disclosure() {
        new AlertDialog.Builder(this).setTitle("Dein Cache. Deine Kontrolle.").setMessage("Ultra1TapCache ist ein Experiment. Android erlaubt das direkte Löschen fremder Caches nicht. Die Bedienungshilfe öffnet die App-Einstellungen und tippt auf Cache leeren.\n\nKeine Datenlöschung, keine Werbung, kein Netzwerk. STOP bleibt oben sichtbar. Unbekannte OEM-Bildschirme werden übersprungen. Cache kann sofort wieder wachsen; eine große Cache-Menge macht das Handy nicht automatisch langsam.").setPositiveButton("Verstanden",(d,w)->prefs.edit().putBoolean("disclosure_seen",true).apply()).setCancelable(false).show();
    }
    private void showAccessibilitySetup() {
        new AlertDialog.Builder(this).setTitle("Bedienungshilfe aktivieren")
            .setMessage("Zum Reinigen braucht Ultra1TapCache die Bedienungshilfe. Öffne die Einstellungen, wähle Ultra1TapCache und aktiviere den Dienst. Danach zurück zur App und erneut auf Reinigen tippen. Es startet nichts automatisch.\n\nDie App öffnet nur App-Einstellungen und tippt auf Cache leeren. STOP bleibt sichtbar. Kein Internetzugriff.\n\nFalls Android den Schalter sperrt: App-Info > Menü > Eingeschränkte Einstellungen zulassen, dann die Bedienungshilfe aktivieren.")
            .setPositiveButton("Einstellungen öffnen",(d,w)-> {
                try { startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)); }
                catch(RuntimeException e) { new AlertDialog.Builder(this).setTitle("Einstellungen nicht erreichbar").setMessage("Bitte Android-Einstellungen > Bedienungshilfen > Ultra1TapCache manuell öffnen.").setPositiveButton("OK",null).show(); }
            }).setNegativeButton("Abbrechen",null).show();
    }
    private void showFeedback(String message) {
        new AlertDialog.Builder(this).setTitle("Reinigung").setMessage(message).setPositiveButton("OK",null).show();
    }
    private void openUsage() { try { startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS,Uri.parse("package:"+getPackageName()))); } catch(RuntimeException e) { startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)); } }
    private void startScan() {
        if(CleanerService.running) return;
        generation++; int token=generation;
        if(scan!=null) scan.cancel(true); scanning=true; apps=new ArrayList<>(); renderList(); updateStatus();
        boolean include=prefs.getBoolean("system",false);
        scan=worker.submit(()-> {
            List<AppEntry> found=AppScanner.scan(this,include);
            handler.post(()-> { if(isDestroyed() || token!=generation) return; apps=found; scanning=false; renderList(); updateStatus(); });
        });
    }
    private void renderList() {
        list.removeAllViews();
        if(apps.isEmpty()) { TextView t=text(scanning?"Cache-Größen werden gelesen...":"Noch keine Apps. Tippe auf Neu scannen.",14,MUTED,false); t.setPadding(0,dp(24),0,dp(24)); list.addView(t); return; }
        for(AppEntry a:apps) {
            CheckBox row=new CheckBox(this); row.setText(a.label+"\n"+(a.cacheBytes==null?"Nicht messbar":format(a.cacheBytes))+"  •  "+a.packageName); row.setTextSize(13); row.setTextColor(WHITE); row.setChecked(a.selected); row.setPadding(dp(6),dp(8),dp(6),dp(8));
            row.setOnCheckedChangeListener((v,on)-> { a.selected=on; updateStatus(); }); list.addView(row,new LinearLayout.LayoutParams(-1,-2));
        }
    }
    private void requestClean() {
        if(CleanerService.running) { CleanerService.cancel(); return; }
        if(scanning) { showFeedback("Der Scan läuft noch. Bitte kurz warten und danach erneut auf Reinigen tippen."); return; }
        if(CleanerService.connected==null) { showAccessibilitySetup(); return; }
        List<AppEntry> selected=new ArrayList<>(); for(AppEntry a:apps) if(a.selected) selected.add(a);
        if(selected.isEmpty()) { showFeedback("Keine Apps ausgewählt. Wähle mindestens eine App mit Cache aus oder starte einen neuen Scan."); return; }
        Runnable start=()-> {
            if(CleanerService.connected==null) { showAccessibilitySetup(); return; }
            if(!CleanerService.start(selected,true)) showFeedback("Reinigung konnte nicht starten. " + CleanerService.status + "\nBitte den Bedienungshilfe-Dienst prüfen und erneut versuchen.");
            updateStatus();
        };
        if(prefs.getBoolean("ask",true)) new AlertDialog.Builder(this).setTitle(selected.size()+" Apps reinigen?").setMessage("Nur Cache leeren. STOP bleibt auf dem Bildschirm. Nicht erkennbare System-Einstellungen werden übersprungen. Bitte währenddessen nicht selbst durch die Einstellungen navigieren.").setPositiveButton("Reinigen",(d,w)->start.run()).setNegativeButton("Abbrechen",null).show(); else start.run();
    }
    private void updateStatus() {
        if(total==null) return;
        long bytes=0;int unknown=0, selected=0; for(AppEntry a:apps) { if(a.cacheBytes==null) unknown++; else bytes+=a.cacheBytes; if(a.selected) selected++; }
        total.setText(scanning?"Scan...":format(bytes));
        boolean result=CleanerService.attempted>0;
        progress.setText(CleanerService.running?CleanerService.status:scanning?"Apps werden nach Cache sortiert...":result?
            CleanerService.status+"\n"+format(CleanerService.reclaimed)+" nachgemessen • "+CleanerService.verified+" bestätigt\n"+CleanerService.clicked+" Klicks • "+CleanerService.skipped+" unbestätigt/übersprungen":apps.size()+" Apps • "+selected+" ausgewählt • "+unknown+" nicht messbar");
        usage.setText(AppScanner.hasUsage(this)?"✓ Nutzungszugriff":"1 Nutzungszugriff"); access.setText(CleanerService.connected!=null?"✓ Bedienungshilfe":"2 Bedienungshilfe");
        clean.setText(CleanerService.running?"STOP • ABBRECHEN":"1 TAP • "+selected+" APPS REINIGEN"); clean.setEnabled(true); refresh.setEnabled(!scanning && !CleanerService.running);
    }
    @Override protected void onResume() { super.onResume(); resumed=true; handler.post(update); if(apps.isEmpty() && !scanning && !CleanerService.running) startScan(); }
    @Override protected void onPause() { resumed=false; handler.removeCallbacks(update); super.onPause(); }
    @Override protected void onDestroy() { generation++; if(scan!=null) scan.cancel(true); worker.shutdownNow(); handler.removeCallbacksAndMessages(null); super.onDestroy(); }
    private TextView text(String s,int sp,int color,boolean bold) { TextView t=new TextView(this); t.setText(s); t.setTextSize(sp); t.setTextColor(color); if(bold)t.setTypeface(null,Typeface.BOLD); return t; }
    private Button button(String s,Runnable action) { Button b=new Button(this); b.setText(s); b.setTextSize(12); b.setAllCaps(false); b.setTextColor(WHITE); b.setOnClickListener(v->action.run()); return b; }
    private GradientDrawable round(int color,int radius) { GradientDrawable d=new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radius)); return d; }
    private int dp(int v) { return Math.round(v*getResources().getDisplayMetrics().density); }
    static String format(long bytes) { if(bytes<1024)return bytes+" B"; if(bytes<1048576)return String.format(java.util.Locale.GERMAN,"%.1f KB",bytes/1024d); if(bytes<1073741824)return String.format(java.util.Locale.GERMAN,"%.1f MB",bytes/1048576d); return String.format(java.util.Locale.GERMAN,"%.2f GB",bytes/1073741824d); }
}
