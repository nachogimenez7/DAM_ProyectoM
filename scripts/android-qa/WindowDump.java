import java.io.File;
import java.lang.reflect.Method;

/** Shell-only QA utility: read the current accessibility tree without waiting for a ticking timer to be idle. */
public final class WindowDump {
    public static void main(String[] args) throws Exception {
        Class<?> wrapperClass = Class.forName("com.android.uiautomator.core.UiAutomationShellWrapper");
        Object wrapper = wrapperClass.getDeclaredConstructor().newInstance();
        wrapperClass.getMethod("connect").invoke(wrapper);
        try {
            Object automation = wrapperClass.getMethod("getUiAutomation").invoke(wrapper);
            Object root = null;
            for (int attempt = 0; attempt < 40 && root == null; attempt++) {
                Thread.sleep(50);
                root = automation.getClass().getMethod("getRootInActiveWindow").invoke(automation);
            }
            if (root == null) throw new IllegalStateException("No active accessibility root");
            Class<?> dumper = Class.forName("com.android.uiautomator.core.AccessibilityNodeInfoDumper");
            for (Method method : dumper.getMethods()) {
                if (method.getName().equals("dumpWindowToFile") && method.getParameterCount() == 5) {
                    method.invoke(null, root, new File(args[0]), 0, Integer.parseInt(args[1]), Integer.parseInt(args[2]));
                    return;
                }
            }
            throw new IllegalStateException("Platform accessibility dumper unavailable");
        } finally {
            wrapperClass.getMethod("disconnect").invoke(wrapper);
        }
    }
}
