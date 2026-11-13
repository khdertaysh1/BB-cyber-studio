package studio.bb.cyber;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.webkit.*;
import android.widget.*;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Native control surface. Untrusted pages never receive a JavaScript-to-native bridge. */
public final class MainActivity extends Activity {
    private static final int BG=0xff071426, CARD=0xff102847, INK=0xffeff8ff, MUTED=0xff9ebbd1, ACCENT=0xff03cfff;
    private static final String TERMUX_PERMISSION="com.termux.permission.RUN_COMMAND";
    private LinearLayout root, browserBox, panelHost, networkRows, nav, siteTabs, bottomNav, toolHeader, tabLine, networkCategories, toolsSheet, urlBar;
    private FrameLayout webHost, stage;
    private ProgressBar loading;
    private final ArrayList<BrowserTab> browserTabs=new ArrayList<>();
    private BrowserTab activeTab;
    private String lastTool="Network";
    private boolean toolsExpanded=false;
    private ScrollView terminalScroll;
    private LocalShell localShell;
    private final ArrayList<String> commandHistory=new ArrayList<>();
    private int historyIndex=0;
    private HorizontalScrollView navigationScroll;
    private WebView web;
    private TextView status, consoleOutput, responseOutput, terminalOutput, elementsOutput;
    private EditText address, jsInput, requestUrl, requestHeaders, requestBody, commandInput, projectName, networkFilter;
    private Spinner method;
    private Button sendButton, tabCounter, terminalModeButton, consoleCopyButton;
    private TextView repeaterNotice, sheetLabel;
    private LinearLayout repeaterAdvanced;
    private TextView networkSummary, networkMetrics;
    private final Map<String,View> panels=new LinkedHashMap<>();
    private final Map<String,Button> tabs=new LinkedHashMap<>();
    private final Map<String,ImageView> destinationIcons=new LinkedHashMap<>();
    private ArrayList<Capture> captures=new ArrayList<>();
    private ArrayList<String> logs=new ArrayList<>();
    private final ExecutorService http=Executors.newSingleThreadExecutor();
    private String selected="Browser", latestResponse="", previousResponse="";
    private boolean captureEnabled=true, destroyed=false, tabsExpanded=false, networkRefreshPending=false, useTermuxShell=false;
    private String networkCategory="All";
    private LabServer lab;
    private int pendingId=100;
    private ValueCallback<Uri[]> fileCallback;
    private boolean desktopMode=false;

    private static final class BrowserTab {
        WebView view; String title="New tab"; boolean desktop;
        String lastConsoleResult=""; long consoleGeneration;
        final ArrayList<Capture> captures=new ArrayList<>();
        final ArrayList<String> logs=new ArrayList<>();
    }
    private static final class Capture {
        final String url, method, category; final long time; final boolean mainFrame; final Map<String,String> headers;
        Capture(WebResourceRequest r) {
            url=r.getUrl().toString(); method=r.getMethod(); mainFrame=r.isForMainFrame();
            headers=new LinkedHashMap<>(r.getRequestHeaders());category=NetworkClassifier.classify(url,mainFrame,headers);
            time=System.currentTimeMillis();
        }
    }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        buildUi();
        TermuxResultReceiver.listener=result->runOnUiThread(()->{if(!destroyed)appendTerminal("\n"+result+"\n");});
        if(!TermuxResultReceiver.lastResult.isEmpty())appendTerminal(TermuxResultReceiver.lastResult);
        http.execute(()->{try {
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();
            try(InputStream input=getAssets().open("lab.html")) { byte[] buffer=new byte[4096]; int n; while((n=input.read(buffer))!=-1)bytes.write(buffer,0,n); }
            LabServer created=new LabServer(bytes.toByteArray());
            runOnUiThread(()->{if(destroyed){try{created.close();}catch(Exception ignored){}return;}lab=created;
                requestUrl.setText(lab.url()+"echo?from=repeater");});
        } catch(Exception e) { runOnUiThread(()->{if(!destroyed)toast("Local lab: "+e.getMessage());}); }});
        select("Browser");
    }
    private int dp(int value) { return Math.round(value*getResources().getDisplayMetrics().density); }
    private LinearLayout column() { LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.VERTICAL);return v; }
    private LinearLayout row() { LinearLayout v=new LinearLayout(this);v.setGravity(Gravity.CENTER_VERTICAL);return v; }
    private GradientDrawable box(int color) { GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(16));d.setStroke(dp(1),0xff1d578c);return d; }
    private TextView label(String value,int size,int color) {
        TextView v=new TextView(this);v.setText(value);v.setTextSize(size);v.setTextColor(color);v.setPadding(dp(4),dp(5),dp(4),dp(5));return v;
    }
    private TextView output(String initial) { TextView v=label(initial,12,INK);v.setTypeface(Typeface.MONOSPACE);v.setTextIsSelectable(true);v.setPadding(dp(12),dp(12),dp(12),dp(12));v.setBackground(box(BG));return v; }
    private EditText edit(String hint,int lines) {
        EditText v=new EditText(this);v.setTextColor(INK);v.setHintTextColor(MUTED);v.setTextSize(13);v.setTextDirection(View.TEXT_DIRECTION_LTR);v.setHint(hint);v.setContentDescription(hint);
        v.setPadding(dp(12),dp(10),dp(12),dp(10));v.setBackground(box(BG));v.setMinLines(lines);v.setGravity(Gravity.TOP);
        v.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS|(lines>1?InputType.TYPE_TEXT_FLAG_MULTI_LINE:0));
        if(lines==1)v.setSingleLine(true);
        v.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,dp(5),0,dp(5));v.setLayoutParams(lp);return v;
    }
    private Button button(String title,Runnable action) {
        Button b=new Button(this);b.setText(title);b.setAllCaps(false);b.setSingleLine(true);b.setEllipsize(android.text.TextUtils.TruncateAt.END);b.setTextColor(INK);b.setTextSize(12);b.setMinHeight(dp(44));b.setMinimumWidth(0);b.setMinWidth(0);b.setPadding(dp(14),0,dp(14),0);b.setStateListAnimator(null);
        b.setBackground(box(CARD)); b.setOnClickListener(v->action.run());
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,dp(44));lp.setMargins(dp(2),dp(3),dp(2),dp(3));b.setLayoutParams(lp);return b;
    }
    private void actions(LinearLayout parent,Button...buttons) {
        HorizontalScrollView scroller=new HorizontalScrollView(this);scroller.setHorizontalScrollBarEnabled(false);
        LinearLayout line=row();for(Button b:buttons)line.addView(b);scroller.addView(line);parent.addView(scroller);
    }
    private LinearLayout panel(String name,String title,String caption) {
        LinearLayout body=column();body.setPadding(dp(16),dp(14),dp(16),dp(22));
        TextView heading=label(title,18,INK);heading.setTypeface(null,Typeface.BOLD);
        body.addView(heading);
        String hint=caption;
        if(name.equals("Network"))hint="Live request metadata · statuses and response bodies need interception";
        else if(name.equals("Console"))hint="Run JavaScript in the active tab";
        else if(name.equals("Elements"))hint="Inspect an element or edit its style";
        else if(name.equals("Repeater"))hint="Edit a request · send · inspect the response";
        else if(name.equals("Analyze"))hint="Page structure and response-header review";
        else if(name.equals("Workspace"))hint="Saved requests and browser data";
        if(name.equals("Network"))body.addView(label("Metadata only · no response status or body capture",11,MUTED));
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.addView(body);panels.put(name,scroll);return body;
    }
    @SuppressLint("SetJavaScriptEnabled") private void buildUi() {
        root=column();root.setBackgroundColor(BG);root.setFocusableInTouchMode(true);
        root.setOnApplyWindowInsetsListener((v,insets)->{
            v.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),
                insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());return insets;
        });
        root.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        status=label("Ready",10,MUTED);

        tabLine=row();tabLine.setPadding(dp(4),0,dp(6),0);tabLine.setBackgroundColor(0xff0b1d36);
        HorizontalScrollView siteScroll=new HorizontalScrollView(this);siteScroll.setHorizontalScrollBarEnabled(false);
        siteTabs=row();siteScroll.addView(siteTabs);tabLine.addView(siteScroll,new LinearLayout.LayoutParams(0,dp(46),1));
        tabLine.addView(icon("+","New tab",()->newTab(null)));root.addView(tabLine);
        tabLine.setVisibility(View.GONE);

        urlBar=row();urlBar.setPadding(dp(6),dp(3),dp(6),dp(3));urlBar.setBackgroundColor(0xff0b1d36);
        urlBar.addView(icon("‹","Back",()->{if(web!=null&&web.canGoBack())web.goBack();}));
        address=edit("Search or enter address",1);
        address.setBackground(box(0xff132f51));address.setTextSize(14);
        address.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI);
        address.setImeOptions(EditorInfo.IME_ACTION_GO);address.setSelectAllOnFocus(true);
        address.setOnEditorActionListener((v,action,event)->{if(action==EditorInfo.IME_ACTION_GO){navigate();return true;}return false;});
        LinearLayout.LayoutParams addrLp=new LinearLayout.LayoutParams(0,dp(44),1);addrLp.setMargins(dp(1),0,dp(1),0);
        urlBar.addView(address,addrLp);
        urlBar.addView(icon("⟳","Reload",()->{if(web!=null)web.reload();}));
        tabCounter=icon("1","Show tabs",()->{tabsExpanded=!tabsExpanded;tabLine.setVisibility(tabsExpanded?View.VISIBLE:View.GONE);});
        tabCounter.setTextSize(16);urlBar.addView(tabCounter);
        urlBar.addView(icon("⋮","Browser menu",this::browserMenu));root.addView(urlBar);
        loading=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);
        loading.setProgressTintList(android.content.res.ColorStateList.valueOf(ACCENT));
        root.addView(loading,new LinearLayout.LayoutParams(-1,dp(2)));

        // WebView always fills the stage. DevTools is an anchored bottom sheet over it.
        stage=new FrameLayout(this);root.addView(stage,new LinearLayout.LayoutParams(-1,0,1));
        browserBox=column();webHost=new FrameLayout(this);
        browserBox.addView(webHost,new LinearLayout.LayoutParams(-1,-1));
        stage.addView(browserBox,new FrameLayout.LayoutParams(-1,-1));

        toolsSheet=column();toolsSheet.setBackground(sheetBackground());toolsSheet.setElevation(dp(12));
        toolsSheet.setClipToOutline(true);
        LinearLayout grab=row();grab.setGravity(Gravity.CENTER);
        View handle=new View(this);handle.setBackground(box(0xff159bdb));
        grab.addView(handle,new LinearLayout.LayoutParams(dp(42),dp(5)));
        toolsSheet.addView(grab,new LinearLayout.LayoutParams(-1,dp(19)));
        toolHeader=row();toolHeader.setPadding(dp(12),0,dp(8),0);
        sheetLabel=label("BLUE DRAGON · DEVTOOLS",12,INK);sheetLabel.setTypeface(null,Typeface.BOLD);
        toolHeader.addView(sheetLabel,new LinearLayout.LayoutParams(0,-2,1));
        toolHeader.addView(icon("⤢","Expand or collapse tools",()->{toolsExpanded=!toolsExpanded;updateSheetHeight();}));
        toolHeader.addView(icon("⋮","Tool options",this::toolOptionsMenu));
        toolHeader.addView(icon("×","Close tools",()->select("Browser")));
        toolsSheet.addView(toolHeader);

        nav=row();navigationScroll=new HorizontalScrollView(this);navigationScroll.setHorizontalScrollBarEnabled(false);
        for(String name:new String[]{"Network","Console","Repeater","Elements"}){
            Button tab=button(name,()->select(name));tabs.put(name,tab);nav.addView(tab);
        }
        navigationScroll.addView(nav);toolsSheet.addView(navigationScroll);
        panelHost=column();toolsSheet.addView(panelHost,new LinearLayout.LayoutParams(-1,0,1));
        FrameLayout.LayoutParams sheetLp=new FrameLayout.LayoutParams(-1,dp(400),Gravity.BOTTOM);
        stage.addView(toolsSheet,sheetLp);
        stage.addOnLayoutChangeListener((v,left,top,right,bottom,oldLeft,oldTop,oldRight,oldBottom)->{
            if(bottom-top!=oldBottom-oldTop)updateSheetHeight();
        });
        buildNetwork();buildRepeater();buildConsole();buildElements();buildAnalyze();buildTerminal();buildWorkspace();

        // The persistent destinations are intentional: browsing, inspection and shell.
        bottomNav=row();bottomNav.setPadding(dp(8),0,dp(8),0);bottomNav.setBackgroundColor(0xff0b1d36);
        String[] names={"Browser","Tools","Terminal"};
        int[] symbols={R.drawable.ic_nav_browser,R.drawable.ic_nav_tools,R.drawable.ic_nav_terminal};
        for(int i=0;i<names.length;i++){
            final String name=names[i];LinearLayout destination=column();destination.setGravity(Gravity.CENTER);
            ImageView symbol=new ImageView(this);symbol.setImageResource(symbols[i]);symbol.setColorFilter(MUTED);
            LinearLayout.LayoutParams symbolSize=new LinearLayout.LayoutParams(dp(21),dp(21));
            symbolSize.gravity=Gravity.CENTER;destination.addView(symbol,symbolSize);
            destinationIcons.put(name,symbol);
            Button b=button(name,()->select(name.equals("Tools")?lastTool:name));b.setTextSize(11);
            b.setPadding(0,0,0,0);b.setBackgroundColor(Color.TRANSPARENT);
            destination.addView(b,new LinearLayout.LayoutParams(-1,dp(29)));
            symbol.setOnClickListener(v->b.performClick());tabs.put(name,b);
            bottomNav.addView(destination,new LinearLayout.LayoutParams(0,dp(54),1));
        }
        root.addView(bottomNav);status.setVisibility(View.GONE);root.addView(status);
        setContentView(root);newTab(null);root.requestFocus();
    }

    private GradientDrawable sheetBackground(){
        GradientDrawable shape=new GradientDrawable();
        shape.setColor(0xff0b1d36);
        shape.setStroke(dp(1),0xff116ca8);
        shape.setCornerRadii(new float[]{dp(22),dp(22),dp(22),dp(22),0,0,0,0});
        return shape;
    }

    private void updateSheetHeight(){
        if(stage==null||toolsSheet==null)return;
        int available=stage.getHeight();if(available<=0)return;
        boolean full=selected.equals("Terminal")||selected.equals("Workspace")||toolsExpanded;
        int target=full?available:Math.max(dp(225),Math.round(available*0.63f));
        target=Math.min(available,target);
        ViewGroup.LayoutParams current=toolsSheet.getLayoutParams();
        if(current!=null&&current.height!=target){current.height=target;toolsSheet.setLayoutParams(current);}
    }
    private Button icon(String glyph,String description,Runnable action){Button b=button(glyph,action);b.setTextSize(21);b.setContentDescription(description);b.setBackgroundColor(Color.TRANSPARENT);b.setPadding(0,0,0,0);b.setLayoutParams(new LinearLayout.LayoutParams(dp(40),dp(46)));return b;}
    @SuppressLint("SetJavaScriptEnabled") private WebView createWeb(BrowserTab tab){
        WebView web;
        web=new WebView(this);web.setBackgroundColor(BG);web.getSettings().setJavaScriptEnabled(true);web.getSettings().setDomStorageEnabled(true);
        web.getSettings().setAllowFileAccess(false);web.getSettings().setAllowContentAccess(false);
        web.getSettings().setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);web.getSettings().setSupportMultipleWindows(false);
        web.getSettings().setMediaPlaybackRequiresUserGesture(true);CookieManager.getInstance().setAcceptThirdPartyCookies(web,false);
        web.getSettings().setBuiltInZoomControls(true);web.getSettings().setDisplayZoomControls(false);web.getSettings().setLoadWithOverviewMode(true);web.getSettings().setUseWideViewPort(true);
        web.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);
        // No addJavascriptInterface, no SSL error bypass, no shell endpoint exposed to page content.
        web.setWebViewClient(new WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest request){
                String scheme=request.getUrl().getScheme();
                if("https://bb.invalid/local-lab".equals(request.getUrl().toString())){openLab();return true;}
                if(!"http".equalsIgnoreCase(scheme)&&!"https".equalsIgnoreCase(scheme)){toast("Blocked navigation scheme: "+scheme);return true;}return false;
            }
            @Override public void onPageStarted(WebView view,String url,android.graphics.Bitmap favicon){if(tab==activeTab){address.setText(url.startsWith("https://bb.invalid/")?"":url);status.setText("Loading · "+url);}}
            @Override public void onPageFinished(WebView view,String url){if(tab==activeTab)status.setText("Browser · "+tab.captures.size()+" observed requests");}
            @Override public WebResourceResponse shouldInterceptRequest(WebView view,WebResourceRequest request){
                if("https://bb.invalid/start".equals(request.getUrl().toString()))try{return new WebResourceResponse("text/html","UTF-8",getAssets().open("start.html"));}catch(Exception ignored){}
                String scheme=request.getUrl().getScheme();
                if("http".equalsIgnoreCase(scheme)||"https".equalsIgnoreCase(scheme)) {
                    Capture capture=new Capture(request);
                    runOnUiThread(()->{if(destroyed||!captureEnabled)return;if(!browserTabs.contains(tab))return;if(tab.captures.size()>=200)tab.captures.remove(0);tab.captures.add(capture);if(tab==activeTab&&selected.equals("Network"))scheduleNetworkRefresh();});
                }
                return null;
            }
            @Override public void onReceivedError(WebView view,WebResourceRequest request,WebResourceError error){
                if(tab==activeTab&&request.isForMainFrame())status.setText("Navigation error · "+error.getDescription());
            }
            @Override public void onReceivedSslError(WebView view,android.webkit.SslErrorHandler handler,android.net.http.SslError error){handler.cancel();if(tab==activeTab)status.setText("TLS validation failed. Connection blocked.");}
        });
        web.setWebChromeClient(new WebChromeClient(){
            @Override public boolean onConsoleMessage(ConsoleMessage message){addTabLog(tab,message.messageLevel()+"  "+message.message()+"\n"+message.sourceId()+":"+message.lineNumber());return true;}
            @Override public void onProgressChanged(WebView view,int progress){if(tab==activeTab){loading.setProgress(progress);loading.setVisibility(progress==100?View.INVISIBLE:View.VISIBLE);}}
            @Override public void onReceivedTitle(WebView view,String title){tab.title=title==null?"New tab":title;renderTabs();}
            @Override public boolean onShowFileChooser(WebView view,ValueCallback<Uri[]> callback,FileChooserParams params){
                if(fileCallback!=null)fileCallback.onReceiveValue(null);fileCallback=callback;
                try{startActivityForResult(params.createIntent(),51);}catch(Exception e){fileCallback.onReceiveValue(null);fileCallback=null;toast("No file picker is available.");}return true;
            }
        });
        tab.view=web;return web;
    }
    private void newTab(String url){
        if(browserTabs.size()>=8){toast("Close a tab first (8-tab memory limit).");return;}
        BrowserTab tab=new BrowserTab();createWeb(tab);browserTabs.add(tab);activateTab(tab);
        if(url==null)tab.view.loadUrl("https://bb.invalid/start");else tab.view.loadUrl(url);
        select("Browser");
    }
    private void activateTab(BrowserTab tab){
        if(activeTab!=null)activeTab.view.onPause();activeTab=tab;web=tab.view;captures=tab.captures;logs=tab.logs;
        webHost.removeAllViews();webHost.addView(web,new FrameLayout.LayoutParams(-1,-1));web.onResume();
        String url=web.getUrl();address.setText(url==null||url.startsWith("https://bb.invalid/")?"":url);
        consoleOutput.setText(android.text.TextUtils.join("\n\n",logs));
        if(consoleCopyButton!=null)consoleCopyButton.setEnabled(!tab.lastConsoleResult.isEmpty());
        renderNetwork();renderTabs();
    }
    private void renderTabs(){
        if(tabCounter!=null)tabCounter.setText(String.valueOf(browserTabs.size()));
        siteTabs.removeAllViews();
        for(BrowserTab tab:browserTabs){
            LinearLayout chip=row();chip.setBackground(box(tab==activeTab?0xff263b57:0xff0b1d36));
            TextView title=label(tab.title,12,tab==activeTab?INK:MUTED);
            title.setSingleLine(true);title.setEllipsize(android.text.TextUtils.TruncateAt.END);
            title.setContentDescription("Switch tab "+tab.title);
            title.setOnClickListener(v->activateTab(tab));
            chip.addView(title,new LinearLayout.LayoutParams(dp(114),dp(40)));
            Button close=icon("×","Close tab "+tab.title,()->closeTab(tab));
            chip.addView(close,new LinearLayout.LayoutParams(dp(34),dp(40)));siteTabs.addView(chip);
        }
    }
    private void closeTab(BrowserTab tab){
        if(!browserTabs.contains(tab))return;boolean active=tab==activeTab;browserTabs.remove(tab);
        if(active){webHost.removeAllViews();activeTab=null;}tab.view.stopLoading();tab.view.destroy();
        if(browserTabs.isEmpty())newTab(null);else if(active)activateTab(browserTabs.get(browserTabs.size()-1));else renderTabs();
    }
    void openLab(){if(lab==null){toast("Local lab is starting. Try again.");return;}web.loadUrl(lab.url());select("Browser");}
    private void browserMenu(){
        new AlertDialog.Builder(this).setTitle("Blue Dragon").setItems(
            new String[]{"New tab","Forward","Desktop site","Open externally",
                "Page analysis","Saved requests","Clear browser data","App & installation info"},(d,which)->{
            switch(which){
            case 0:newTab(null);break;
            case 1:if(web.canGoForward())web.goForward();break;
            case 2:activeTab.desktop=!activeTab.desktop;
                String ua=WebSettings.getDefaultUserAgent(this);
                if(activeTab.desktop)ua=ua.replaceAll("Linux; Android[^)]*","X11; Linux x86_64")
                    .replace("; wv","").replace(" Mobile","");
                web.getSettings().setUserAgentString(ua);web.reload();break;
            case 3:String url=web.getUrl();
                if(url!=null&&!url.startsWith("https://bb.invalid/")&&
                    (url.startsWith("https://")||url.startsWith("http://")))
                    try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url)));}
                    catch(Exception e){toast("No browser available.");}
                break;
            case 4:openAnalyze();break;
            case 5:openWorkspace();break;
            case 6:confirm("Clear browser data?","Remove cookies, site storage, cache and captured request metadata?",
                this::clearBrowserData);break;
            case 7:showAppInfo();break;
            }
        }).show();
    }
    void openAnalyze(){select("Analyze");}
    void openWorkspace(){select("Workspace");}
    void clearBrowserData(){
        for(BrowserTab tab:browserTabs){
            tab.view.stopLoading();tab.view.loadUrl("about:blank");tab.view.clearHistory();
            tab.captures.clear();tab.logs.clear();tab.lastConsoleResult="";tab.consoleGeneration++;
        }
        CookieManager.getInstance().removeAllCookies(done->CookieManager.getInstance().flush());
        WebStorage.getInstance().deleteAllData();
        if(web!=null){web.clearCache(true);web.clearHistory();}
        captures.clear();renderNetwork();logs.clear();consoleOutput.setText("");consoleCopyButton.setEnabled(false);
        toast("Browsing data cleared.");
    }

    private void select(String name) {
        selected=name;
        boolean browser=name.equals("Browser");
        boolean terminal=name.equals("Terminal");
        boolean workspace=name.equals("Workspace");
        boolean tool=!browser&&!terminal&&!workspace;
        if(tool)lastTool=name;
        tabLine.setVisibility(browser&&tabsExpanded?View.VISIBLE:View.GONE);
        urlBar.setVisibility(terminal||workspace?View.GONE:View.VISIBLE);
        navigationScroll.setVisibility(tool?View.VISIBLE:View.GONE);
        sheetLabel.setText(terminal?"BLUE DRAGON · TERMINAL":workspace?"BLUE DRAGON · SAVED REQUESTS":"BLUE DRAGON · DEVTOOLS");
        toolsSheet.setVisibility(browser?View.GONE:View.VISIBLE);
        // Keep WebView alive below the sheet. The keyboard may resize the stage.
        updateSheetHeight();
        panelHost.removeAllViews();View panel=panels.get(name);
        if(panel!=null)panelHost.addView(panel,new LinearLayout.LayoutParams(-1,-1));
        for(Map.Entry<String,Button> entry:tabs.entrySet()){
            String key=entry.getKey();
            boolean active=key.equals(name)||(tool&&key.equals("Tools"))||(workspace&&key.equals("Tools"));
            entry.getValue().setTextColor(active?ACCENT:MUTED);
            ImageView destinationIcon=destinationIcons.get(key);
            if(destinationIcon!=null)destinationIcon.setColorFilter(active?ACCENT:MUTED);
            if(!key.equals("Browser")&&!key.equals("Tools")&&!key.equals("Terminal"))
                entry.getValue().setBackground(box(active?0xff06538a:0xff0b1d36));
        }
        if(name.equals("Network"))renderNetwork();
        status.setText(name+" · "+(activeTab==null?"":activeTab.title));
    }
    private void toolOptionsMenu(){
        String[] choices;
        if(selected.equals("Console"))choices=new String[]{"Copy all console logs","Clear console","Reload website"};
        else if(selected.equals("Repeater"))choices=new String[]{
            "Copy request as curl","Prepare curl in Terminal","Compare last two responses","Clear response"};
        else if(selected.equals("Network"))choices=new String[]{"Clear request list","Reload website"};
        else if(selected.equals("Terminal"))choices=new String[]{"New shell session","Open Termux","Termux setup","Clear output"};
        else if(selected.equals("Workspace"))choices=new String[]{"Delete saved request","Clear browser data"};
        else choices=new String[]{"Page analysis","Open Network"};
        new AlertDialog.Builder(this).setTitle(selected+" options").setItems(choices,(dialog,which)->{
            if(selected.equals("Console")){
                if(which==0){
                    String value=android.text.TextUtils.join("\n\n",logs);
                    if(value.trim().isEmpty())toast("No logs to copy.");else copy(value);
                }else if(which==1){logs.clear();if(activeTab!=null){activeTab.lastConsoleResult="";activeTab.consoleGeneration++;}consoleOutput.setText("");consoleCopyButton.setEnabled(false);}
                else web.reload();
            }else if(selected.equals("Repeater")){
                if(which==0||which==1)try{
                    String curl=spec().toCurl();
                    if(which==0)confirm("Copy request?","Headers or body may include credentials.",
                        ()->copy(curl));
                    else{commandInput.setText(curl);select("Terminal");
                        toast("Review before running. No command was executed.");}
                }catch(Exception e){toast(e.getMessage());}
                else if(which==2)compareResponses();
                else{previousResponse="";latestResponse="";responseOutput.setText("No response yet.");}
            }else if(selected.equals("Network")){
                if(which==0){captures.clear();renderNetwork();}else web.reload();
            }else if(selected.equals("Terminal")){
                if(which==0)startLocalShell();
                else if(which==1)openTermux();
                else if(which==2)termuxSetup();
                else terminalOutput.setText("");
            }else if(selected.equals("Workspace")){
                if(which==0)confirm("Delete saved request?","The encrypted request will be removed.",()->{
                    try{WorkspaceStore.delete(this);toast("Saved request deleted.");}
                    catch(Exception e){toast(e.getMessage());}
                });
                else confirm("Clear browser data?","Clear cookies and web storage?",this::clearBrowserData);
            }else if(which==0)openAnalyze();else select("Network");
        }).show();
    }

    private void navigate() {
        String url=address.getText().toString().trim();if(url.isEmpty())return;if(!url.contains(":")&&(url.contains(" ")||!url.contains(".")))url="https://www.google.com/search?q="+Uri.encode(url);else if(!url.contains(":"))url="https://"+url;
        try { // Fragments are valid for browsing, unlike native request replay.
            Uri parsed=Uri.parse(url);String scheme=parsed.getScheme();
            if((!"https".equalsIgnoreCase(scheme)&&!"http".equalsIgnoreCase(scheme))||parsed.getHost()==null)throw new IllegalArgumentException("Enter an HTTP(S) address.");
            web.loadUrl(url);
            ((android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(address.getWindowToken(),0);
        }catch(Exception e){toast(e.getMessage());}
    }
    private void buildNetwork() {
        LinearLayout body=panel("Network","Network","Observed WebView requests. The source is request metadata, not an HTTP(S) proxy.");
        LinearLayout toolbar=row();toolbar.setGravity(Gravity.CENTER_VERTICAL);
        Button record=button("●  Recording",()->{});
        record.setTextColor(0xfff18b91);
        record.setOnClickListener(v->{
            captureEnabled=!captureEnabled;
            record.setText(captureEnabled?"●  Recording":"○  Paused");
            record.setTextColor(captureEnabled?0xfff18b91:MUTED);
        });
        toolbar.addView(record);
        toolbar.addView(button("Clear",()->{captures.clear();if(activeTab!=null)activeTab.captures.clear();renderNetwork();}));
        toolbar.addView(button("Reload",()->{if(web!=null)web.reload();}));
        toolbar.addView(button("Copy URLs",()->{
            if(captures.isEmpty()){toast("No requests to copy.");return;}
            StringBuilder urls=new StringBuilder();
            for(Capture item:captures)urls.append(item.method).append(' ').append(item.url).append('\n');
            copy(urls.toString());
        }));
        HorizontalScrollView toolbarScroll=new HorizontalScrollView(this);
        toolbarScroll.setHorizontalScrollBarEnabled(false);toolbarScroll.addView(toolbar);body.addView(toolbarScroll);
        networkMetrics=label("REQUESTS  0   ·   HOSTS  0",12,ACCENT);
        networkMetrics.setPadding(dp(12),dp(12),dp(12),dp(12));
        networkMetrics.setBackground(box(0xff102c4b));body.addView(networkMetrics);

        networkFilter=edit("Filter URLs, methods or hostnames",1);
        networkFilter.setSingleLine(true);networkFilter.setBackground(box(0xff102c4b));
        body.addView(networkFilter);
        networkFilter.addTextChangedListener(new android.text.TextWatcher(){
            public void beforeTextChanged(CharSequence value,int start,int count,int after){}
            public void onTextChanged(CharSequence value,int start,int before,int count){renderNetwork();}
            public void afterTextChanged(android.text.Editable e){}
        });

        HorizontalScrollView categoriesScroll=new HorizontalScrollView(this);
        categoriesScroll.setHorizontalScrollBarEnabled(false);
        networkCategories=row();
        for(String type:new String[]{"All","Doc","API","JS","CSS","Img","Font","Other"}){
            Button chip=button(type,()->{networkCategory=type;renderNetwork();});
            chip.setTextSize(11);chip.setMinHeight(dp(38));
            chip.setLayoutParams(new LinearLayout.LayoutParams(-2,dp(38)));
            networkCategories.addView(chip);
        }
        categoriesScroll.addView(networkCategories);body.addView(categoriesScroll);
        networkSummary=label("0 observed",11,MUTED);
        networkSummary.setPadding(dp(6),dp(10),0,dp(6));body.addView(networkSummary);
        LinearLayout headings=row();headings.setPadding(dp(10),dp(2),dp(9),dp(3));
        headings.addView(label("NAME / URL",10,MUTED),new LinearLayout.LayoutParams(0,-2,1));
        headings.addView(label("TYPE",10,MUTED),new LinearLayout.LayoutParams(dp(55),-2));
        headings.addView(label("STATUS",10,MUTED),new LinearLayout.LayoutParams(dp(52),-2));
        body.addView(headings);
        networkRows=column();body.addView(networkRows);
        body.addView(label("— = unavailable. WebView does not expose response status, body, size or timing here. Resource types are estimated from URL/headers.",11,MUTED));
    }
    private void scheduleNetworkRefresh(){
        if(networkRefreshPending||root==null)return;
        networkRefreshPending=true;
        root.postDelayed(()->{
            networkRefreshPending=false;
            if(!destroyed&&selected.equals("Network"))renderNetwork();
        },180);
    }
    private void renderNetwork() {
        if(networkRows==null||networkFilter==null)return;
        networkRows.removeAllViews();
        String filter=networkFilter.getText().toString().trim().toLowerCase(java.util.Locale.ROOT);
        for(int i=0;i<networkCategories.getChildCount();i++){
            Button chip=(Button)networkCategories.getChildAt(i);
            boolean active=chip.getText().toString().equals(networkCategory);
            chip.setTextColor(active?0xff0b1524:MUTED);
            chip.setBackground(box(active?ACCENT:CARD));
        }
        int visible=0;
        for(int i=captures.size()-1;i>=0;i--) {
            Capture capture=captures.get(i);
            if(!networkCategory.equals("All")&&!networkCategory.equals(capture.category))continue;
            String searchable=(capture.method+" "+capture.url+" "+capture.category).toLowerCase(java.util.Locale.ROOT);
            if(!searchable.contains(filter))continue;
            Uri parsed=Uri.parse(capture.url);
            String host=parsed.getHost()==null?"Unknown host":parsed.getHost();
            String path=parsed.getPath()==null?"":parsed.getPath();
            String filename=path.isEmpty()||path.endsWith("/")?host:path.substring(path.lastIndexOf('/')+1);
            if(filename.isEmpty())filename=host;
            LinearLayout item=row();item.setPadding(dp(8),dp(5),dp(7),dp(5));item.setBackground(box(CARD));
            LinearLayout.LayoutParams itemParams=new LinearLayout.LayoutParams(-1,dp(67));
            itemParams.setMargins(0,dp(2),0,dp(3));item.setLayoutParams(itemParams);
            LinearLayout description=column();
            TextView name=label(filename,13,INK);name.setSingleLine(true);
            name.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
            description.addView(name);
            TextView secondary=label(capture.method+" · "+host+(path.isEmpty()?"":path),10,MUTED);
            secondary.setSingleLine(true);secondary.setEllipsize(android.text.TextUtils.TruncateAt.END);
            description.addView(secondary);
            item.addView(description,new LinearLayout.LayoutParams(0,-2,1));
            TextView category=label(capture.category,11,ACCENT);category.setGravity(Gravity.CENTER);
            item.addView(category,new LinearLayout.LayoutParams(dp(55),-2));
            TextView statusCell=label("—",15,MUTED);statusCell.setGravity(Gravity.CENTER);
            item.addView(statusCell,new LinearLayout.LayoutParams(dp(52),-2));
            item.setContentDescription("Observed "+capture.method+" "+capture.url);
            item.setOnClickListener(v->captureDetails(capture));
            networkRows.addView(item);visible++;
        }
        networkSummary.setText(captures.size()+" observed  ·  "+visible+" shown  ·  Active tab only");
        java.util.HashSet<String> hosts=new java.util.HashSet<>();
        for(Capture capture:captures){String host=Uri.parse(capture.url).getHost();if(host!=null)hosts.add(host);}
        if(networkMetrics!=null)networkMetrics.setText("REQUESTS  "+captures.size()+"   ·   HOSTS  "+hosts.size()+"   ·   SHOWN  "+visible);
        if(visible==0)networkRows.addView(label("No matching requests. Open a website or reload this tab.",13,MUTED));
    }
    private String headersText(Map<String,String> headers,boolean replay) {
        StringBuilder text=new StringBuilder();for(Map.Entry<String,String> h:headers.entrySet()){
            if(replay&&h.getKey().matches("(?i)host|content-length|transfer-encoding|connection|expect|upgrade"))continue;
            text.append(h.getKey()).append(": ").append(h.getValue()).append('\n');
        }return text.toString();
    }
    private void captureDetails(Capture capture) {
        LinearLayout details=column();details.setPadding(dp(10),dp(5),dp(10),dp(5));
        TextView summary=label(capture.method+"  ·  "+capture.category+" (estimated)",12,ACCENT);
        details.addView(summary);
        TextView url=label(capture.url,12,INK);url.setTextIsSelectable(true);details.addView(url);
        TextView view=output("");
        HorizontalScrollView tabScroll=new HorizontalScrollView(this);
        tabScroll.setHorizontalScrollBarEnabled(false);LinearLayout switches=row();
        String general="Request URL: "+capture.url+"\nMethod: "+capture.method+
            "\nMain frame: "+capture.mainFrame+"\nSeen at: "+new java.text.SimpleDateFormat("HH:mm:ss",java.util.Locale.US).format(new java.util.Date(capture.time))+
            "\nStatus: unavailable\nSize: unavailable\nTiming: unavailable\n"+
            "Resource type: estimated from filename or Accept header.";
        String headers="OBSERVED REQUEST HEADERS\n\n"+headersText(capture.headers,false)+
            "\nResponse headers: unavailable (not intercepted).";
        String params=Uri.parse(capture.url).getEncodedQuery();
        String payload="QUERY STRING\n\n"+(params==null||params.isEmpty()?"(no query parameters)":params)+
            "\n\nRequest body: unavailable in WebView callback, including POST bodies.";
        String response="Browser response not captured.\n\nTo inspect an independently sent HTTP response, transfer this request to Repeater. Browser cookies and the original request body will not be copied automatically.";
        for(String tab:new String[]{"General","Headers","Payload","Response"}){
            Button choice=button(tab,()->{
                if(tab.equals("General"))view.setText(general);
                else if(tab.equals("Headers"))view.setText(headers);
                else if(tab.equals("Payload"))view.setText(payload);
                else view.setText(response);
            });
            switches.addView(choice);
        }
        tabScroll.addView(switches);details.addView(tabScroll);
        view.setText(general);
        ScrollView scroll=new ScrollView(this);scroll.addView(view);
        details.addView(scroll,new LinearLayout.LayoutParams(-1,dp(260)));
        new AlertDialog.Builder(this).setTitle("Request details").setView(details)
            .setNegativeButton("Close",null)
            .setNeutralButton("Copy headers",(d,w)->confirm("Copy request metadata?",
                "Request headers may contain private tokens. Review the clipboard before sharing.",
                ()->copy(capture.method+" "+capture.url+"\n"+headersText(capture.headers,false))))
            .setPositiveButton("To Repeater",(d,w)->{
                requestUrl.setText(capture.url);requestHeaders.setText(headersText(capture.headers,true));
                requestBody.setText("");chooseMethod(capture.method);
                repeaterNotice.setText("Imported observed metadata only. Original POST body, cookies and browser responses are unavailable.");
                requestHeaders.setVisibility(View.VISIBLE);
                select("Repeater");
            }).show();
    }
    private void buildRepeater() {
        LinearLayout body=panel("Repeater","HTTP Repeater","Independent client with verified TLS. Response max 1 MiB.");
        repeaterNotice=label("Independent HTTP client · browser cookies and request body are not copied.",11,MUTED);
        body.addView(repeaterNotice);
        LinearLayout requestLine=row();
        method=new Spinner(this);ArrayAdapter<String> adapter=new ArrayAdapter<>(this,
            android.R.layout.simple_spinner_dropdown_item,
            new String[]{"GET","POST","PUT","DELETE","HEAD","OPTIONS"});
        method.setAdapter(adapter);
        requestLine.addView(method,new LinearLayout.LayoutParams(dp(105),dp(52)));
        requestUrl=edit("https://example.com/path",1);
        requestUrl.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI);
        requestLine.addView(requestUrl,new LinearLayout.LayoutParams(0,dp(48),1));
        body.addView(requestLine);

        repeaterAdvanced=column();
        requestHeaders=edit("Headers  ·  one Name: value per line",3);
        requestBody=edit("Body (UTF-8)",3);
        requestHeaders.setTypeface(Typeface.MONOSPACE);requestBody.setTypeface(Typeface.MONOSPACE);
        requestHeaders.setVisibility(View.GONE);requestBody.setVisibility(View.GONE);
        LinearLayout controls=row();
        Button headersToggle=button("Headers ▾",()->{
            boolean show=requestHeaders.getVisibility()!=View.VISIBLE;
            requestHeaders.setVisibility(show?View.VISIBLE:View.GONE);
        });
        Button bodyToggle=button("Body ▾",()->{
            boolean show=requestBody.getVisibility()!=View.VISIBLE;
            requestBody.setVisibility(show?View.VISIBLE:View.GONE);
        });
        controls.addView(headersToggle);controls.addView(bodyToggle);repeaterAdvanced.addView(controls);
        repeaterAdvanced.addView(requestHeaders);repeaterAdvanced.addView(requestBody);
        body.addView(repeaterAdvanced);

        sendButton=button("Send",this::sendRequest);
        sendButton.setTextColor(0xff0c1727);sendButton.setBackground(box(ACCENT));
        actions(body,sendButton,button("Copy response",()->{
            String text=responseOutput.getText().toString();
            if(text.trim().isEmpty()||text.equals("No response yet.")){
                toast("Send a request first.");return;
            }
            confirm("Copy response?","Responses may contain session tokens or private data.",
                ()->copy(text));
        }));
        TextView resultTitle=label("RESPONSE",11,MUTED);body.addView(resultTitle);
        responseOutput=output("No response yet.");responseOutput.setContentDescription("HTTP response output");
        body.addView(responseOutput);
    }
    private void chooseMethod(String value){for(int i=0;i<method.getCount();i++)if(method.getItemAtPosition(i).equals(value)){method.setSelection(i);return;}method.setSelection(0);toast("Unsupported method "+value+"; reset to GET. Review before sending.");}
    private RequestSpec spec(){return new RequestSpec(method.getSelectedItem().toString(),requestUrl.getText().toString(),requestHeaders.getText().toString(),requestBody.getText().toString());}
    private void sendRequest() {
        final RequestSpec request;try{request=spec();}catch(Exception e){toast(e.getMessage());return;}
        sendButton.setEnabled(false);responseOutput.setText("Sending "+request.method+" "+request.url+" …");
        http.execute(()->{
            String answer;try{answer=HttpRunner.execute(request);}catch(Exception e){answer="Request failed: "+e.getClass().getSimpleName()+"\n"+e.getMessage();}
            final String result=answer;runOnUiThread(()->{if(destroyed)return;previousResponse=latestResponse;latestResponse=result;responseOutput.setText(result);sendButton.setEnabled(true);});
        });
    }
    private void compareResponses(){
        if(previousResponse.isEmpty()||latestResponse.isEmpty()){toast("Send two requests first.");return;}
        String[] a=previousResponse.split("\n"),b=latestResponse.split("\n");StringBuilder diff=new StringBuilder("Line-by-line comparison (includes timing / dynamic fields)\n\n");int changed=0;
        for(int i=0;i<Math.max(a.length,b.length);i++){String old=i<a.length?a[i]:"",now=i<b.length?b[i]:"";if(!old.equals(now)){diff.append("Line ").append(i+1).append("\n− ").append(old).append("\n+ ").append(now).append("\n\n");if(++changed>=100){diff.append("[First 100 changed lines]");break;}}}
        if(changed==0)diff.append("Responses are identical.");showText("Response comparison",diff.toString());
    }
    private void buildConsole(){
        LinearLayout body=panel("Console","Console",
            "Evaluate JavaScript and copy the result.");
        TextView guidance=label("JavaScript runs in the active website. Run code only on pages you trust.",11,MUTED);
        body.addView(guidance);
        jsInput=edit("Enter JavaScript",2);
        jsInput.setTypeface(Typeface.MONOSPACE);jsInput.setText("document.title");
        jsInput.setContentDescription("JavaScript input");body.addView(jsInput);
        Button run=button("Run",this::evaluate);
        run.setTextColor(0xff0b1d32);run.setBackground(box(ACCENT));
        consoleCopyButton=button("Copy",this::copyConsoleResult);
        consoleCopyButton.setEnabled(false);
        actions(body,run,consoleCopyButton);
        consoleOutput=output("Run a JavaScript expression to see its result.");
        consoleOutput.setTextIsSelectable(true);
        consoleOutput.setContentDescription("Console output");
        body.addView(consoleOutput);
        body.addView(label("Tip: long-press a line to select text. Copy copies the latest result; ⋮ lets you copy all logs.",11,MUTED));
    }

    private void copyConsoleResult(){
        String value=activeTab==null?"":activeTab.lastConsoleResult;
        if(value==null||value.trim().isEmpty()){
            toast("Run JavaScript first, then tap Copy.");return;
        }
        copy(value);
    }

    private void evaluate(){
        String source=jsInput.getText().toString();
        if(source.trim().isEmpty())return;
        final BrowserTab evaluatedTab=activeTab;
        final long generation=evaluatedTab.consoleGeneration;
        if(consoleCopyButton!=null)consoleCopyButton.setEnabled(false);
        if(evaluatedTab!=null)evaluatedTab.lastConsoleResult="";
        addLog("> "+source);
        String script="(function(){try{var v=(0,eval)("+JSONObject.quote(source)+");if(v&&typeof v.then==='function')return 'Promise returned; use console.log in .then() to inspect its value';return typeof v==='string'?v:(typeof v==='undefined'?'undefined':JSON.stringify(v,null,2));}catch(e){return e.name+': '+e.message;}})()";
        web.evaluateJavascript(script,value->{
            if(destroyed||!browserTabs.contains(evaluatedTab)||evaluatedTab.consoleGeneration!=generation)return;
            String decoded;
            try{Object object=new JSONTokener(value).nextValue();decoded=String.valueOf(object);}
            catch(Exception e){decoded=value;}
            if(evaluatedTab!=null)evaluatedTab.lastConsoleResult=decoded;
            addTabLog(evaluatedTab,decoded);
            if(evaluatedTab==activeTab&&consoleCopyButton!=null)
                consoleCopyButton.setEnabled(decoded!=null&&!decoded.isEmpty());
        });
    }
    private void addTabLog(BrowserTab tab,String message){if(destroyed||tab==null||!browserTabs.contains(tab))return;if(message.length()>8000)message=message.substring(0,8000);if(tab.logs.size()>=200)tab.logs.remove(0);tab.logs.add(message);if(tab==activeTab&&consoleOutput!=null)consoleOutput.setText(android.text.TextUtils.join("\n\n",tab.logs));}
    private void addLog(String message){if(message.length()>8000)message=message.substring(0,8000)+"\n[Truncated]";if(logs.size()>=200)logs.remove(0);logs.add(message);if(consoleOutput!=null)consoleOutput.setText(android.text.TextUtils.join("\n\n",logs));}
    private void buildElements(){
        LinearLayout body=panel("Elements","Elements · DOM","Inspect the HTML structure of the active page.");
        body.addView(label("Find a page element using a CSS selector. Examples: h1, button, #menu or .product.",12,MUTED));
        EditText selector=edit("CSS selector, e.g. h1",1);
        selector.setText("h1");body.addView(selector);
        Button inspect=button("Inspect HTML",()->{
            String script="(function(){try{var e=document.querySelector("+JSONObject.quote(selector.getText().toString())+");if(!e)return 'No matching element';return e.outerHTML.slice(0,50000);}catch(e){return e.message;}})()";
            web.evaluateJavascript(script,r->{
                try{elementsOutput.setText(String.valueOf(new JSONTokener(r).nextValue()));}
                catch(Exception e){elementsOutput.setText(r);}
            });
        });
        inspect.setTextColor(0xff0b1d32);inspect.setBackground(box(ACCENT));
        actions(body,inspect,button("Copy HTML",()->{
            String value=elementsOutput.getText().toString();
            if(value.equals("Inspect an element to see its HTML.")||value.trim().isEmpty()){
                toast("Inspect an element first.");return;
            }
            copy(value);
        }));
        elementsOutput=output("Inspect an element to see its HTML.");
        elementsOutput.setTextIsSelectable(true);elementsOutput.setContentDescription("Inspected HTML output");
        body.addView(elementsOutput);
        body.addView(label("CSS editing is temporary and changes only the WebView on your phone.",11,MUTED));
        LinearLayout cssEditor=column();
        EditText property=edit("CSS property, e.g. color",1);property.setText("color");
        EditText value=edit("CSS value, e.g. #75acfa",1);value.setText("#75acfa");
        cssEditor.addView(property);cssEditor.addView(value);
        cssEditor.addView(button("Apply CSS",()->{
            String key=property.getText().toString().trim();
            if(!key.matches("[-a-zA-Z0-9]+")){toast("Enter a valid CSS property name.");return;}
            String script="(function(){try{var e=document.querySelector("+JSONObject.quote(selector.getText().toString())+");if(!e)return 'No matching element';e.style.setProperty("+JSONObject.quote(key)+","+JSONObject.quote(value.getText().toString())+");return e.outerHTML.slice(0,50000);}catch(e){return e.message;}})()";
            web.evaluateJavascript(script,r->{
                try{elementsOutput.setText(String.valueOf(new JSONTokener(r).nextValue()));}
                catch(Exception e){elementsOutput.setText(r);}
            });
        }));
        cssEditor.setVisibility(View.GONE);
        body.addView(button("Edit CSS ▾",()->{
            cssEditor.setVisibility(cssEditor.getVisibility()==View.VISIBLE?View.GONE:View.VISIBLE);
        }));
        body.addView(cssEditor);
    }
    private void buildTerminal(){
        LinearLayout body=column();body.setPadding(dp(12),dp(6),dp(12),dp(6));
        body.setBackgroundColor(0xff071426);panels.put("Terminal",body);
        LinearLayout top=row();
        TextView title=label("Terminal",17,INK);title.setTypeface(null,Typeface.BOLD);
        top.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        terminalModeButton=button("Android ▾",()->{
            useTermuxShell=!useTermuxShell;
            terminalModeButton.setText(useTermuxShell?"Termux ▾":"Android ▾");
            appendTerminal(useTermuxShell
                ?"\n[Termux mode] Runs through the separate Termux app with permission. Results arrive on completion. Use ⋮ > Termux setup if needed.\n"
                :"\n[Android mode] Local app sandbox shell: no pkg, apt, pip or Linux packages.\n");
        });
        top.addView(terminalModeButton);body.addView(top);
        body.addView(label("ANDROID SHELL / TERMUX CONNECTION · SELECT MODE ABOVE",11,MUTED));
        terminalOutput=output("BB Cyber Studio\n\nAndroid mode runs system shell commands inside the app sandbox, not Linux packages. Switch to Termux for pkg, Python and other Termux-installed tools.\n\n");
        terminalOutput.setTextSize(12);terminalOutput.setBackgroundColor(0xff071426);
        terminalOutput.setContentDescription("Terminal output");
        terminalScroll=new ScrollView(this);terminalScroll.setFillViewport(true);
        terminalScroll.addView(terminalOutput);body.addView(terminalScroll,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout extras=row();
        extras.addView(button("↑",()->terminalHistory(-1)));
        extras.addView(button("↓",()->terminalHistory(1)));
        extras.addView(button("Copy",()->copy(terminalOutput.getText().toString())));
        extras.addView(button("Stop",()->{
            if(localShell!=null){localShell.close();localShell=null;appendTerminal("\n[Local session stopped]\n");}
        }));
        body.addView(extras);
        LinearLayout input=row();input.addView(label("$",18,ACCENT));
        commandInput=edit("Enter shell command",1);commandInput.setTypeface(Typeface.MONOSPACE);
        commandInput.setImeOptions(EditorInfo.IME_ACTION_SEND);
        commandInput.setOnEditorActionListener((v,action,event)->{
            if(action==EditorInfo.IME_ACTION_SEND){sendLocalCommand();return true;}return false;
        });
        input.addView(commandInput,new LinearLayout.LayoutParams(0,-2,1));
        input.addView(button("Run",this::sendLocalCommand));body.addView(input);
    }
    private void appendTerminal(String text){if(destroyed)return;String all=terminalOutput.getText()+text;if(all.length()>100000)all=all.substring(all.length()-100000);terminalOutput.setText(all);terminalScroll.post(()->terminalScroll.fullScroll(View.FOCUS_DOWN));}
    private void startLocalShell(){if(localShell!=null)localShell.close();try{localShell=new LocalShell(getFilesDir(),text->runOnUiThread(()->appendTerminal(text)));appendTerminal("\n[New local session]\n");}catch(Exception e){appendTerminal("\n"+e.getMessage()+"\n");}}
    private void sendLocalCommand(){
        String command=commandInput.getText().toString().trim();
        if(command.isEmpty())return;
        if(command.length()>4096){toast("Command limit: 4096 characters.");return;}
        if(useTermuxShell){runTermux();return;}
        if(command.matches("(?s)^(pkg|apt|apt-get)(\\s+.*)?$")){
            new AlertDialog.Builder(this)
                .setTitle("This is the Android shell")
                .setMessage("pkg and apt work in Termux, not Android's /system/bin/sh. Switch to Termux mode to run the command in your installed Termux environment.")
                .setNegativeButton("Keep Android",null)
                .setPositiveButton("Switch to Termux",(d,w)->{
                    useTermuxShell=true;terminalModeButton.setText("Termux ▾");
                    appendTerminal("\n[Termux mode selected. Review the command then tap Run.]\n");
                }).show();
            return;
        }
        if(localShell==null)startLocalShell();if(localShell==null)return;
        try{
            localShell.send(command);commandHistory.add(command);
            if(commandHistory.size()>100)commandHistory.remove(0);
            historyIndex=commandHistory.size();
            appendTerminal("$ "+command+"\n");commandInput.setText("");
        }catch(Exception e){appendTerminal(e.getMessage()+"\n");}
    }
    private void terminalHistory(int change){historyIndex=Math.max(0,Math.min(commandHistory.size(),historyIndex+change));commandInput.setText(historyIndex==commandHistory.size()?"":commandHistory.get(historyIndex));commandInput.setSelection(commandInput.length());}
    private void insertCommand(String text){int at=Math.max(0,commandInput.getSelectionStart());commandInput.getText().insert(at,text);commandInput.requestFocus();}
    private void buildAnalyze(){
        LinearLayout body=panel("Analyze","Page intelligence","Inspect the loaded page, or review response headers with a separate request. Observations are not confirmed vulnerabilities.");
        TextView result=output("Open a website, then choose Page map or Review headers. The local lab works here too.");
        Button review=button("Review headers",()->{});
        review.setOnClickListener(v->{
            final String url=web.getUrl()==null?"":web.getUrl().split("#",2)[0];
            try{RequestSpec.validateUrl(url);}catch(Exception e){toast(e.getMessage());return;}
            review.setEnabled(false);result.setText("Reviewing "+url+" …");
            http.execute(()->{String report;try{report=SiteReview.inspect(url);}catch(Exception e){report="Could not review headers: "+e.getMessage();}final String reportText=report;
                runOnUiThread(()->{if(!destroyed){result.setText(reportText);review.setEnabled(true);}});
            });
        });
        actions(body,button("Page map",()->{
            try{ByteArrayOutputStream bytes=new ByteArrayOutputStream();try(InputStream input=getAssets().open("page-map.js")){byte[] buffer=new byte[4096];int n;while((n=input.read(buffer))!=-1)bytes.write(buffer,0,n);}
                web.evaluateJavascript(bytes.toString("UTF-8"),r->{try{result.setText(String.valueOf(new JSONTokener(r).nextValue()));}catch(Exception e){result.setText(r);}});
            }catch(Exception e){toast(e.getMessage());}
        }),review,button("Copy report",()->confirm("Copy analysis?","Page URLs may contain sensitive query parameters. Review the report before sharing.",()->copy(result.getText().toString()))));
        body.addView(result);
    }
    private void termuxSetup(){showText("Connect Termux","1. Install Termux from its official F-Droid or GitHub source.\n\n2. In Termux, add this line to ~/.termux/termux.properties:\n\nallow-external-apps=true\n\nThen run: termux-reload-settings\n\n3. Grant Blue Dragon the Run commands in Termux permission when prompted. If unavailable, open Android Settings → Apps → Blue Dragon → Permissions → Additional permissions.\n\n4. Tap Run in Termux. Use short, non-interactive commands. Results arrive when the process exits; interactive programs belong in Open Termux for this version.\n\nCommands run with Termux's permissions and can modify its files. No command is run merely by visiting a page or importing a request.");}
    private void openTermux(){Intent launch=getPackageManager().getLaunchIntentForPackage("com.termux");if(launch==null){termuxSetup();return;}startActivity(launch);}
    private void runTermux(){
        if(checkSelfPermission(TERMUX_PERMISSION)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{TERMUX_PERMISSION},40);toast("Grant permission, then tap Run again.");return;}
        String command=commandInput.getText().toString();if(command.trim().isEmpty())return;
        if(command.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>64000){toast("Command exceeds 64 KB. Save large payloads to a file in Termux instead.");return;}
        confirm("Run this command?",command,()->{
            try{
                Intent callback=new Intent(this,TermuxResultReceiver.class);callback.setAction(getPackageName()+".TERMUX_RESULT."+(++pendingId));
                int flags=PendingIntent.FLAG_ONE_SHOT|PendingIntent.FLAG_CANCEL_CURRENT;
                if(Build.VERSION.SDK_INT>=31)flags|=PendingIntent.FLAG_MUTABLE;
                PendingIntent pending=PendingIntent.getBroadcast(this,pendingId,callback,flags);
                Intent intent=new Intent("com.termux.RUN_COMMAND");intent.setClassName("com.termux","com.termux.app.RunCommandService");
                intent.putExtra("com.termux.RUN_COMMAND_PATH","/data/data/com.termux/files/usr/bin/bash");
                intent.putExtra("com.termux.RUN_COMMAND_ARGUMENTS",new String[]{"-lc",command});
                intent.putExtra("com.termux.RUN_COMMAND_WORKDIR","/data/data/com.termux/files/home");
                intent.putExtra("com.termux.RUN_COMMAND_BACKGROUND",true);intent.putExtra("com.termux.RUN_COMMAND_PENDING_INTENT",pending);
                intent.putExtra("com.termux.RUN_COMMAND_COMMAND_LABEL","BB Cyber Studio");
                android.content.ComponentName started=startService(intent);
                if(started==null)throw new IllegalStateException("Termux service not available");
                appendTerminal("\n[Termux] Executing: "+command+"\nOutput arrives after completion.\n");
            }catch(Exception e){appendTerminal("\nCould not start Termux: "+e.getMessage()+"\nOpen Setup for connection instructions.\n");}
        });
    }
    private void buildWorkspace(){
        LinearLayout body=panel("Workspace","Saved Request",
            "Store one encrypted HTTP request on this device.");
        body.addView(label("Keep a request for later. Only the Repeater method, URL, headers and body are stored, encrypted with Android Keystore.",12,MUTED));
        projectName=edit("Request name",1);projectName.setText("My request");body.addView(projectName);
        actions(body,button("Save",()->{
            try{
                RequestSpec request=spec();JSONObject data=new JSONObject();
                data.put("name",projectName.getText().toString());
                data.put("method",request.method);data.put("url",request.url);
                data.put("headers",requestHeaders.getText().toString());data.put("body",request.body);
                WorkspaceStore.save(this,data.toString());toast("Request saved (encrypted).");
            }catch(Exception e){toast("Save failed: "+e.getMessage());}
        }),button("Load into Repeater",()->{
            try{
                String content=WorkspaceStore.load(this);
                if(content==null){toast("Nothing saved yet.");return;}
                JSONObject data=new JSONObject(content);
                projectName.setText(data.getString("name"));chooseMethod(data.getString("method"));
                requestUrl.setText(data.getString("url"));requestHeaders.setText(data.getString("headers"));
                requestBody.setText(data.getString("body"));select("Repeater");
                toast("Loaded. Review headers and body before sending.");
            }catch(Exception e){toast("Load failed: "+e.getMessage());}
        }));
        body.addView(label("To remove saved requests or clear website data, use the ⋮ tool menu. Browser cookies and WebView storage are not saved in this request file.",11,MUTED));
    }
    AlertDialog showAppInfo(){return showText("App & installation info",AppDiagnostics.describe(this));}
    @Override protected void onPause(){if(web!=null)web.onPause();super.onPause();}
    @Override protected void onResume(){super.onResume();if(web!=null)web.onResume();}
    private void copy(String value){((ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("Blue Dragon",value));toast("Copied.");}
    private void toast(String message){Toast.makeText(this,message==null?"Unknown error":message,Toast.LENGTH_LONG).show();}
    private void confirm(String title,String message,Runnable accepted){new AlertDialog.Builder(this).setTitle(title).setMessage(message).setNegativeButton("Cancel",null).setPositiveButton("Continue",(d,w)->accepted.run()).show();}
    private AlertDialog showText(String title,String value){ScrollView scroll=new ScrollView(this);scroll.addView(output(value));return new AlertDialog.Builder(this).setTitle(title).setView(scroll).setPositiveButton("Close",null).show();}
    @Override public void onBackPressed(){if(!selected.equals("Browser")){select("Browser");return;}if(web.canGoBack())web.goBack();else super.onBackPressed();}
    @Override public void onConfigurationChanged(android.content.res.Configuration configuration){super.onConfigurationChanged(configuration);select(selected);root.requestApplyInsets();root.post(()->{for(Button b:tabs.values()){b.setText(b.getText().toString());b.requestLayout();}root.requestLayout();});}
    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){
        super.onActivityResult(requestCode,resultCode,data);
        if(requestCode==51&&fileCallback!=null){Uri[] uris=WebChromeClient.FileChooserParams.parseResult(resultCode,data);
            if(uris!=null)for(Uri uri:uris)if(!"content".equals(uri.getScheme())){uris=null;break;}
            fileCallback.onReceiveValue(uris);fileCallback=null;
        }
    }
    @Override protected void onDestroy(){destroyed=true;TermuxResultReceiver.listener=null;if(fileCallback!=null){fileCallback.onReceiveValue(null);fileCallback=null;}http.shutdownNow();if(lab!=null)try{lab.close();}catch(Exception ignored){}if(localShell!=null)localShell.close();for(BrowserTab tab:browserTabs){tab.view.stopLoading();tab.view.destroy();}super.onDestroy();}
}
