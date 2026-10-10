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
    private int index, generation;
    private boolean turbo, cacheClicked, storageOpened, identitySeen, measuring;
    private long deadline, nextAllowed;
    private Button stopButton;
    private WindowManager wm;
    private final Runnable tick = () -> step();

    @Override protected void onServiceConnected() { connected = this; }
    static boolean start(List<AppEntry> entries, boolean fast) {
        if (connected == null || running || entries.isEmpty()) return false;
        connected.begin(entries, fast); return running;
    }
    static void cancel() { if (connected != null) connected.finish(connected.getString(R.string.ui_083)); }
    private void begin(List<AppEntry> entries, boolean fast) {
        generation++; queue = new ArrayList<>(entries); turbo = fast;
        attempted = clicked = verified = skipped = 0; reclaimed = 0; index = -1; running = true;
        try {
            wm = getSystemService(WindowManager.class);
            stopButton = new Button(this); stopButton.setText(getString(R.string.ui_084));
            stopButton.setTextColor(Color.WHITE); stopButton.setBackgroundColor(Color.rgb(24,62,80));
            stopButton.setOnClickListener(v -> finish(getString(R.string.ui_083)));
            WindowManager.LayoutParams p = new WindowManager.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL, PixelFormat.TRANSLUCENT);
            p.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL; p.y = 80;
            wm.addView(stopButton,p);
        } catch (RuntimeException e) {
            finish(getString(R.string.ui_085)); return;
        }
        nextApp();
    }
    private void nextApp() {
        handler.removeCallbacks(tick);
        if (!running) return;
        index++;
        if (index >= queue.size()) { finish(getString(R.string.ui_086)); return; }
        attempted++; cacheClicked = storageOpened = identitySeen = measuring = false;
        AppEntry app = queue.get(index);
        status = (index + 1) + "/" + queue.size() + " • " + app.label;
        stopButton.setText("STOP • " + (index + 1) + "/" + queue.size());
        deadline = SystemClock.uptimeMillis() + 12000; nextAllowed = SystemClock.uptimeMillis() + (turbo ? 250 : 650);
        try {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + app.packageName));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent); schedule(turbo ? 250 : 650);
        } catch (RuntimeException e) { skipped++; nextApp(); }
    }
    private void schedule(long ms) { handler.removeCallbacks(tick); if (running) handler.postDelayed(tick,ms); }
    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if (!running || measuring) return;
        if (SystemClock.uptimeMillis() >= nextAllowed) schedule(80);
    }
    private void step() {
        if (!running || measuring) return;
        long now = SystemClock.uptimeMillis();
        if (now >= deadline) { skipped++; nextApp(); return; }
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
            if (!cacheClicked && cache != null) {
                if (!cache.isEnabled()) { skipped++; nextApp(); return; }
                if (safeClick(cache)) {
                    cacheClicked = true; clicked++; nextAllowed = now + (turbo ? 400 : 900);
                    schedule(turbo ? 400 : 900); return;
                }
            }
            if (cacheClicked) {
                // Never confirm an OEM dialog with OK: unknown dialogs time out and get measured honestly.
                measureAndContinue(); return;
            }
            if (!storageOpened) {
                AccessibilityNodeInfo storage = find(root,false);
                if (storage != null && safeClick(storage)) {
                    storageOpened = true; nextAllowed = now + (turbo ? 250 : 650);
                    schedule(turbo ? 250 : 650); return;
                }
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
            if (found != null) return found;
            child.recycle();
        }
        return null;
    }
    private boolean safeClick(AccessibilityNodeInfo node) {
        // A clickable parent must not combine Cache with a separate Clear data control.
        AccessibilityNodeInfo candidate=node;
        for (int depth=0;depth<3 && candidate!=null;depth++) {
            if (containsDanger(candidate)) return false;
            if (candidate.isEnabled() && candidate.isClickable()) return candidate.performAction(AccessibilityNodeInfo.ACTION_CLICK);
            candidate=candidate.getParent();
        }
        return false;
    }
    private boolean containsDanger(AccessibilityNodeInfo n) {
        if (SafeLabels.danger(n.getText()) || SafeLabels.danger(n.getContentDescription())) return true;
        for(int i=0;i<n.getChildCount();i++) {
            AccessibilityNodeInfo child=n.getChild(i); if(child==null) continue;
            boolean dangerous=containsDanger(child); child.recycle(); if(dangerous) return true;
        }
        return false;
    }
    private void measureAndContinue() {
        if (!running || measuring) return;
        measuring=true;
        int token=generation; AppEntry app=queue.get(index);
        io.execute(() -> {
            Long after=AppScanner.cache(this,app.packageName);
            handler.post(() -> {
                if (!running || token!=generation) return;
                if (app.cacheBytes!=null && after!=null && after<app.cacheBytes) { reclaimed+=app.cacheBytes-after; verified++; }
                else skipped++;
                nextApp();
            });
        });
    }
    private void finish(String message) {
        boolean wasRunning=running; running=false; generation++; handler.removeCallbacksAndMessages(null);
        status=message;
        if (stopButton!=null && wm!=null) {
            try { wm.removeView(stopButton); } catch (RuntimeException ignored) { }
        }
        stopButton=null; queue=List.of();
        if(wasRunning) {
            Intent i=new Intent(this,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            try { startActivity(i); } catch(RuntimeException ignored) { }
        }
    }
    @Override public void onInterrupt() { finish(getString(R.string.ui_087)); }
    @Override public void onDestroy() { finish(getString(R.string.ui_088)); connected=null; io.shutdownNow(); super.onDestroy(); }
}
