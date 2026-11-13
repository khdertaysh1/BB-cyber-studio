package studio.bb.cyber;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Build;
import android.webkit.WebView;
import java.security.MessageDigest;
import java.util.Locale;

/** Public build metadata only; no browsing history, account IDs or private key material. */
final class AppDiagnostics {
    @SuppressWarnings("deprecation")
    static String describe(Context context) {
        StringBuilder out=new StringBuilder("Blue Dragon\n");
        try {
            PackageManager pm=context.getPackageManager();
            PackageInfo info=pm.getPackageInfo(context.getPackageName(),
                Build.VERSION.SDK_INT>=28?PackageManager.GET_SIGNING_CERTIFICATES:PackageManager.GET_SIGNATURES);
            boolean debug=(info.applicationInfo.flags & ApplicationInfo.FLAG_DEBUGGABLE)!=0;
            out.append("Version: ").append(info.versionName).append(" (")
                .append(Build.VERSION.SDK_INT>=28?info.getLongVersionCode():info.versionCode).append(")\n")
                .append("Package: ").append(context.getPackageName()).append("\n")
                .append("Build: ").append(debug?"DEBUG / testing only":"RELEASE / debugging disabled").append("\n")
                .append("Android API: ").append(Build.VERSION.SDK_INT).append("\n");
            Signature[] signatures=Build.VERSION.SDK_INT>=28&&info.signingInfo!=null?
                info.signingInfo.getApkContentsSigners():info.signatures;
            if(signatures!=null)for(Signature signature:signatures){
                byte[] hash=MessageDigest.getInstance("SHA-256").digest(signature.toByteArray());
                StringBuilder hex=new StringBuilder();for(byte value:hash){if(hex.length()>0)hex.append(':');hex.append(String.format(Locale.ROOT,"%02X",value&255));}
                out.append("Signing certificate SHA-256:\n").append(hex).append("\n");
            }
            PackageInfo provider=WebView.getCurrentWebViewPackage();
            if(provider!=null)out.append("WebView: ").append(provider.packageName).append(" ").append(provider.versionName).append("\n");
            String installer=Build.VERSION.SDK_INT>=30?pm.getInstallSourceInfo(context.getPackageName()).getInstallingPackageName():pm.getInstallerPackageName(context.getPackageName());
            out.append("Installer: ").append(installer==null?"Not reported (for example, sideload or adb)":installer).append("\n");
        }catch(Exception e){out.append("Some build details are unavailable: ").append(e.getClass().getSimpleName()).append("\n");}
        out.append("\nINSTALLATION HELP\n")
            .append("Update rejected: Android requires a compatible signing certificate and version code. Compare the SHA-256 above with the release certificate.\n\n")
            .append("Play Protect scan recommendation: Google may not recognize a sideloaded app. Follow its scan prompt; release signing cannot guarantee removal of that prompt.\n\n")
            .append("Harmful-app warning: keep protection enabled and send the exact message to the developer for investigation.\n\n")
            .append("Uninstalling deletes this app's local data. Release signing setup: docs/SIGNING.md in the source repository.");
        return out.toString();
    }
}
