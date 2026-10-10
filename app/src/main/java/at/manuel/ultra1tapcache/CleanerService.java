package at.manuel.ultra1tapcache;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.Gravity;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Button;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Finite, user-started sessions only. No arbitrary coordinate taps or generic OK clicks. */
public class CleanerService extends AccessibilityService {
    static CleanerService connected;
    static boolean running;
    static String status = "";
    static int attempted, clicked, verified, skipped;
    static long reclaimed;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private List<AppEntry> queue = List.of();
    private int index, generation, appToken;
    private boolean preparing;
    private Runnable watchdog;
    private boolean turbo, cacheClicked, storageOpened, identitySeen, measuring;
    private long deadline, nextAllowed;
    private int uiWait=250,afterWait=400;
    private long clickedAt;
    private boolean clickRejected,disabledSeen;
    private int cleanAttempt;
    private Button stopButton;
    private WindowManager wm;
    private final Runnable tick = () -> {try {step();}catch(RuntimeException e){stop("engine_error",getString(R.string.engine_error)+": "+e.getClass().getSimpleName());}};

    @Override protected void onServiceConnected() { connected = this; }
    static int waitFor(String speed,boolean after){
        if("fast".equals(speed))return after?550:400;
        if("normal".equals(speed))return after?900:650;
        return after?400:250;
    }
    static boolean start(List<AppEntry> entries, boolean fast) {
        if (connected == null || running || entries.isEmpty()) return false;
        try {connected.begin(entries, fast, false);return running;}catch(RuntimeException e){connected.stop("checkpoint_error",connected.getString(R.string.engine_error)+": "+e.getClass().getSimpleName());return false;}
    }
    static boolean resume(boolean fast) {
        if(connected==null||running)return false;
        List<AppEntry> remaining=RunJournal.remaining(connected);if(remaining.isEmpty())return false;
        try{connected.begin(remaining,fast,true);return running;}catch(RuntimeException e){connected.stop("checkpoint_error",connected.getString(R.string.engine_error));return false;}
    }
    static void cancel() { if (connected != null) connected.stop("user_stop",connected.getString(R.string.ui_083)); }
    private void begin(List<AppEntry> entries, boolean fast, boolean resume) {
        generation++; queue = new ArrayList<>(entries); turbo = fast;
        String speed=getSharedPreferences("settings",MODE_PRIVATE).getString("speed",fast?"turbo":"normal");
        uiWait=waitFor(speed,false);afterWait=waitFor(speed,true);
        if(!resume) RunJournal.begin(this,entries);
        attempted=clicked=skipped=0; verified=RunJournal.count(this,"confirmed"); reclaimed=RunJournal.read(this).optLong("bytes"); index=-1;running=true;
        RunJournal.reason(this,"running");
        try {
            wm = getSystemService(WindowManager.class);
            stopButton = new Button(this); stopButton.setText(getString(R.string.ui_084));
            stopButton.setTextColor(Color.WHITE); stopButton.setBackgroundColor(Color.rgb(24,62,80));
            stopButton.setOnClickListener(v -> stop("user_stop",getString(R.string.ui_083)));
            WindowManager.LayoutParams p = new WindowManager.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL, PixelFormat.TRANSLUCENT);
            p.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL; p.y = 80;
            wm.addView(stopButton,p);
        } catch (RuntimeException e) {
            stop("overlay_missing",getString(R.string.ui_085)); return;
        }
        nextApp();
    }
    private void nextApp() {
        handler.removeCallbacks(tick);
        if (!running) return;
        index++;
        if (index >= queue.size()) { stop("finished",getString(R.string.ui_086)); return; }
        attempted++;cleanAttempt=1;disabledSeen=false;clickRejected=false;cacheClicked=storageOpened=identitySeen=measuring=false;preparing=true;appToken++;
        AppEntry app=queue.get(index);status=(index+1)+"/"+queue.size()+" • "+app.label;
        stopButton.setText("STOP • "+(index+1)+"/"+queue.size());
        RunJournal.state(this,app.packageName,"started",null,0);
        int token=generation, current=appToken;
        armWatchdog("baseline_timeout");
        io.execute(()-> {
            Long baseline=AppScanner.cache(this,app.packageName);
            postSafe(()-> {
                if(!running||token!=generation||current!=appToken)return;
                disarmWatchdog();preparing=false;
                if(baseline==null){recordAndAdvance("unmeasurable",null,0);return;}
                app.cacheBytes=baseline;RunJournal.baseline(this,app.packageName,baseline);
                if(baseline==0){recordAndAdvance("already_empty",0L,0);return;}
                if(baseline<=16384){recordAndAdvance("small_cache_skipped",baseline,0);return;}
                deadline=SystemClock.uptimeMillis()+12000;nextAllowed=SystemClock.uptimeMillis()+uiWait;
                try { startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+app.packageName)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));schedule(uiWait); }
                catch(RuntimeException e){recordAndAdvance("settings_failed",null,0);}
            });
        });
    }
    private void postSafe(Runnable action){handler.post(()->{try{action.run();}catch(RuntimeException e){stop("engine_error",getString(R.string.engine_error)+": "+e.getClass().getSimpleName());}});}
    private void disarmWatchdog(){if(watchdog!=null)handler.removeCallbacks(watchdog);watchdog=null;}
    private void armWatchdog(String reason){
        disarmWatchdog();int token=generation,current=appToken;
        watchdog=()-> {if(running&&token==generation&&current==appToken) stop(reason,getString(R.string.measure_timeout));};handler.postDelayed(watchdog,10000);
    }
    private void recordAndAdvance(String state,Long after,long bytes){
        AppEntry app=queue.get(index);RunJournal.state(this,app.packageName,state,after,bytes);
        if(after!=null)app.cacheBytes=after;
        if("confirmed".equals(state)){verified++;reclaimed+=bytes;app.selected=false;}
        else if("already_empty".equals(state)||"small_cache_skipped".equals(state)){app.selected=false;}
        else skipped++;
        measuring=preparing=false;disarmWatchdog();handler.post(()->{try{nextApp();}catch(RuntimeException e){stop("engine_error",getString(R.string.engine_error)+": "+e.getClass().getSimpleName());}});
    }
    private void schedule(long ms) { handler.removeCallbacks(tick); if (running) handler.postDelayed(tick,ms); }
    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if (!running || measuring || preparing) return;
        if (SystemClock.uptimeMillis() >= nextAllowed) schedule(80);
    }
    private void step() {
        if (!running || measuring || preparing) return;
        long now = SystemClock.uptimeMillis();
        if (now >= deadline) { recordAndAdvance(clickRejected?"cache_click_rejected":disabledSeen?"cache_button_disabled":identitySeen?"cache_button_not_found":"settings_timeout",null,0); return; }
        if (now < nextAllowed) { schedule(nextAllowed-now); return; }
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) { schedule(200); return; }
        try {
            // Only Android's actual Settings app may receive clicks. Unknown OEM package fails closed.
            if (!"com.android.settings".contentEquals(root.getPackageName() == null ? "" : root.getPackageName())) { schedule(200); return; }
            AppEntry app = queue.get(index);
            identitySeen = containsIdentity(root, app);
            if (!identitySeen) { schedule(200); return; }
            AccessibilityNodeInfo cache = find(root,true);
            try {
            if (!cacheClicked && cache != null) {
                RunJournal.clickInfo(this,app.packageName,cache.isEnabled(),null,cleanAttempt);
                if (!cache.isEnabled()) { disabledSeen=true;schedule(200);return; }
                boolean accepted=safeClick(cache);RunJournal.clickInfo(this,app.packageName,true,accepted,cleanAttempt);
                if (accepted) {
                    cacheClicked = true; clicked++;clickedAt=SystemClock.uptimeMillis();RunJournal.state(this,app.packageName,"clicked",null,0); nextAllowed = now + (afterWait);
                    schedule(afterWait); return;
                } else clickRejected=true;
            }
            } finally { if(cache!=null && cache!=root)cache.recycle(); }
            if (cacheClicked) {
                // Never confirm an OEM dialog with OK: unknown dialogs time out and get measured honestly.
                measureAndContinue(); return;
            }
            if (!storageOpened) {
                AccessibilityNodeInfo storage = find(root,false);
                try {
                    if (storage != null && safeClick(storage)) {
                        storageOpened = true; nextAllowed = now + uiWait;
                        schedule(uiWait); return;
                    }
                } finally {if(storage!=null && storage!=root)storage.recycle();}
            }
            schedule(200);
        } finally { root.recycle(); }
    }
    private boolean containsIdentity(AccessibilityNodeInfo n, AppEntry app) {
        String text = SafeLabels.normalize(n.getText());
        if (text.equals(SafeLabels.normalize(app.label)) || text.equals(app.packageName)) return true;
        for (int i=0;i<n.getChildCount();i++) {
            AccessibilityNodeInfo child=n.getChild(i); if (child==null) continue;
            boolean found=containsIdentity(child,app); child.recycle(); if(found) return true;
        }
        return false;
    }
    private AccessibilityNodeInfo find(AccessibilityNodeInfo n, boolean cache) {
        if (n.isVisibleToUser() && (cache ? SafeLabels.cache(n.getText()) : SafeLabels.storage(n.getText()))) return n;
        for (int i=0;i<n.getChildCount();i++) {
            AccessibilityNodeInfo child=n.getChild(i); if(child==null) continue;
            AccessibilityNodeInfo found=find(child,cache);
            if (found != null) { if(found!=child)child.recycle(); return found; }
            child.recycle();
        }
        return null;
    }
    private boolean safeClick(AccessibilityNodeInfo node) {
        AccessibilityNodeInfo candidate=node;
        List<AccessibilityNodeInfo> parents=new ArrayList<>();
        try {
            for(int depth=0;depth<3&&candidate!=null;depth++) {
                if(containsDanger(candidate))return false;
                if(candidate.isEnabled()&&candidate.isClickable())return candidate.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                candidate=candidate.getParent();if(candidate!=null)parents.add(candidate);
            }
            return false;
        } finally {for(AccessibilityNodeInfo parent:parents)parent.recycle();}
    }
    private boolean containsDanger(AccessibilityNodeInfo n) {
        if (SafeLabels.danger(n.getText()) || SafeLabels.danger(n.getContentDescription())) return true;
        for(int i=0;i<n.getChildCount();i++) {
            AccessibilityNodeInfo child=n.getChild(i); if(child==null) continue;
            boolean dangerous=containsDanger(child); child.recycle(); if(dangerous) return true;
        }
        return false;
    }
    static boolean measuredDecrease(Long baseline,Long after){return baseline!=null&&after!=null&&baseline-after>4096;}
    static boolean shouldRetry(Long baseline,Long after,int attempt){return baseline!=null&&after!=null&&baseline>=1048576&&!measuredDecrease(baseline,after)&&attempt<2;}
    static long retryDelay(int reads){return reads==1?300:reads==2?450:-1;}
    private void measureAndContinue() {
        if(!running||measuring)return;measuring=true;
        RunJournal.state(this,queue.get(index).packageName,"clicked",null,0);armWatchdog("measurement_timeout");
        measureAttempt(generation,appToken,1);
    }
    private void measureAttempt(int token,int current,int reads){
        if(!running||token!=generation||current!=appToken)return;
        AppEntry app=queue.get(index);
        io.execute(()-> {
            Long after=AppScanner.cache(this,app.packageName);
            postSafe(()-> {
                if(!running||token!=generation||current!=appToken)return;
                RunJournal.measurement(this,app.packageName,after,reads,SystemClock.uptimeMillis()-clickedAt);
                if(measuredDecrease(app.cacheBytes,after)){recordAndAdvance("confirmed",after,app.cacheBytes-after);return;}
                long delay=retryDelay(reads);
                if(after!=null&&delay>=0){handler.postDelayed(()->measureAttempt(token,current,reads+1),delay);return;}
                if(shouldRetry(app.cacheBytes,after,cleanAttempt)){
                    disarmWatchdog();cleanAttempt++;cacheClicked=storageOpened=identitySeen=measuring=disabledSeen=clickRejected=false;appToken++;
                    deadline=SystemClock.uptimeMillis()+12000;nextAllowed=SystemClock.uptimeMillis()+uiWait;
                    try{startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+app.packageName)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));schedule(uiWait);}catch(RuntimeException e){recordAndAdvance("settings_failed",after,0);}return;
                }
                recordAndAdvance(after==null?"unmeasurable":app.cacheBytes!=null&&after<app.cacheBytes?"tiny_decrease":"unconfirmed",after,0);
            });
        });
    }
    private void stop(String reason,String message){
        if(running)try{RunJournal.reason(this,reason);}catch(RuntimeException e){message=getString(R.string.engine_error)+": checkpoint unavailable";}
        finish(message);
    }
    private void finish(String message) {
        boolean wasRunning=running; running=false; generation++; appToken++; handler.removeCallbacksAndMessages(null);
        status=message;disarmWatchdog();preparing=measuring=false;
        if (stopButton!=null && wm!=null) {
            try { wm.removeView(stopButton); } catch (RuntimeException ignored) { }
        }
        stopButton=null; queue=List.of();
        if(wasRunning) {
            Intent i=new Intent(this,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            try { startActivity(i); } catch(RuntimeException ignored) { }
        }
    }
    @Override public void onInterrupt() { stop("service_interrupted",getString(R.string.ui_087)); }
    @Override public void onDestroy() { stop("service_destroyed",getString(R.string.ui_088)); connected=null; io.shutdownNow(); super.onDestroy(); }
}
