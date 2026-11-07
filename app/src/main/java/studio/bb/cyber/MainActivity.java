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
    private static final int BG=0xff0b1018, CARD=0xff172231, INK=0xffedf3fa, MUTED=0xff9cacc1, ACCENT=0xff77afff;
    private static final String TERMUX_PERMISSION="com.termux.permission.RUN_COMMAND";
    private LinearLayout root, content, browserBox, panelHost, networkRows, nav, siteTabs, bottomNav, toolHeader, tabLine, networkCategories;
    private FrameLayout webHost;
    private ProgressBar loading;
    private final ArrayList<BrowserTab> browserTabs=new ArrayList<>();
    private BrowserTab activeTab;
    private String lastTool="Network";
    private boolean splitTools=false;
    private ScrollView terminalScroll;
    private LocalShell localShell;
    private final ArrayList<String> commandHistory=new ArrayList<>();
    private int historyIndex=0;
    private HorizontalScrollView navigationScroll;
    private WebView web;
    private TextView status, consoleOutput, responseOutput, terminalOutput, elementsOutput;
    private EditText address, jsInput, requestUrl, requestHeaders, requestBody, commandInput, projectName, networkFilter;
    private Spinner method;
    private Button sendButton, tabCounter;
    private TextView networkSummary;
    private final Map<String,View> panels=new LinkedHashMap<>();
    private final Map<String,Button> tabs=new LinkedHashMap<>();
    private ArrayList<Capture> captures=new ArrayList<>();
    private ArrayList<String> logs=new ArrayList<>();
    private final ExecutorService http=Executors.newSingleThreadExecutor();
    private String selected="Browser", latestResponse="", previousResponse="";
    private boolean captureEnabled=true, destroyed=false, tabsExpanded=false;
    private String networkCategory="All";
    private LabServer lab;
    private int pendingId=100;
    private ValueCallback<Uri[]> fileCallback;
    private boolean desktopMode=false;

    private static final class BrowserTab {
        WebView view; String title="New tab"; boolean desktop;
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
        TermuxResultReceiver.listener=result->runOnUiThread(()->{if(!destroyed)terminalOutput.setText(result);});
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
    private GradientDrawable box(int color) { GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(14));d.setStroke(dp(1),0xff2a3b50);return d; }
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
        LinearLayout body=column();body.setPadding(dp(12),dp(8),dp(12),dp(18));
        TextView heading=label(title,18,INK);heading.setTypeface(null,Typeface.BOLD);
        LinearLayout headingRow=row();headingRow.addView(heading,new LinearLayout.LayoutParams(0,-2,1));headingRow.addView(icon("ⓘ","About "+name,()->showText(title,caption)));body.addView(headingRow);
        String hint=caption;
        if(name.equals("Network"))hint="Live request metadata · statuses and response bodies need interception";
        else if(name.equals("Console"))hint="Run JavaScript in the active tab";
        else if(name.equals("Elements"))hint="Inspect an element or edit its style";
        else if(name.equals("Repeater"))hint="Edit a request · send · inspect the response";
        else if(name.equals("Analyze"))hint="Page structure and response-header review";
        else if(name.equals("Workspace"))hint="Saved requests and browser data";
        body.addView(label(hint,12,MUTED));
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.addView(body);panels.put(name,scroll);return body;
    }
    @SuppressLint("SetJavaScriptEnabled") private void buildUi() {
        root=column();root.setBackgroundColor(BG);root.setFocusableInTouchMode(true);
        root.setOnApplyWindowInsetsListener((v,insets)->{
            v.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());return insets;
        });
        root.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        status=label("Ready",10,MUTED);
        // Chrome occupies just one toolbar by default. The tab strip is opened on demand.
        tabLine=row();tabLine.setPadding(dp(4),0,dp(6),0);tabLine.setBackgroundColor(0xff111b29);
        HorizontalScrollView siteScroll=new HorizontalScrollView(this);siteScroll.setHorizontalScrollBarEnabled(false);
        siteTabs=row();siteScroll.addView(siteTabs);tabLine.addView(siteScroll,new LinearLayout.LayoutParams(0,dp(46),1));
        tabLine.addView(icon("+","New tab",()->newTab(null)));root.addView(tabLine);
        tabLine.setVisibility(View.GONE);

        LinearLayout urlBar=row();urlBar.setPadding(dp(4),dp(3),dp(4),dp(3));urlBar.setBackgroundColor(0xff111a27);
        urlBar.addView(icon("‹","Back",()->{if(web!=null&&web.canGoBack())web.goBack();}));
        address=edit("Search or enter address",1);
        address.setBackground(box(0xff223146));address.setTextSize(14);
        address.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI);
        address.setImeOptions(EditorInfo.IME_ACTION_GO);address.setSelectAllOnFocus(true);
        address.setOnEditorActionListener((v,action,event)->{if(action==EditorInfo.IME_ACTION_GO){navigate();return true;}return false;});
        LinearLayout.LayoutParams addressLayout=new LinearLayout.LayoutParams(0,dp(44),1);
        addressLayout.setMargins(dp(1),0,dp(1),0);urlBar.addView(address,addressLayout);
        urlBar.addView(icon("⟳","Reload",()->{if(web!=null)web.reload();}));
        tabCounter=icon("1","Show tabs",()->{tabsExpanded=!tabsExpanded;tabLine.setVisibility(tabsExpanded?View.VISIBLE:View.GONE);});
        tabCounter.setTextSize(16);urlBar.addView(tabCounter);
        urlBar.addView(icon("⋮","Browser menu",this::browserMenu));root.addView(urlBar);

        loading=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);
        loading.setProgressTintList(android.content.res.ColorStateList.valueOf(ACCENT));
        root.addView(loading,new LinearLayout.LayoutParams(-1,dp(2)));
        nav=row();navigationScroll=new HorizontalScrollView(this);navigationScroll.setHorizontalScrollBarEnabled(false);navigationScroll.addView(nav);
        for(String name:new String[]{"Network","Console","Elements","Repeater","Analyze"}){
            Button tab=button(name,()->select(name));tabs.put(name,tab);nav.addView(tab);
        }
        root.addView(navigationScroll);
        toolHeader=row();toolHeader.setPadding(dp(10),dp(1),dp(6),dp(1));toolHeader.setBackgroundColor(0xff101927);
        TextView scope=label("DEVTOOLS  /  CURRENT TAB",10,MUTED);toolHeader.addView(scope,new LinearLayout.LayoutParams(0,-2,1));
        toolHeader.addView(button("Split",()->{splitTools=!splitTools;select(selected);}));
        toolHeader.addView(icon("×","Close tools",()->select("Browser")));root.addView(toolHeader);
        content=column();root.addView(content,new LinearLayout.LayoutParams(-1,0,1));
        browserBox=column();webHost=new FrameLayout(this);browserBox.addView(webHost,new LinearLayout.LayoutParams(-1,-1));
        content.addView(browserBox,new LinearLayout.LayoutParams(-1,0,1));
        panelHost=column();content.addView(panelHost,new LinearLayout.LayoutParams(-1,0,1));
        buildNetwork();buildRepeater();buildConsole();buildElements();buildAnalyze();buildTerminal();buildWorkspace();

        bottomNav=row();bottomNav.setPadding(dp(4),dp(1),dp(4),0);bottomNav.setBackgroundColor(0xff111a27);
        String[] names={"Browser","Tools","Terminal","Workspace"};
        String[] symbols={"⌂","⌘","›_","▤"};
        for(int i=0;i<names.length;i++){
            final String name=names[i];
            LinearLayout destination=column();destination.setGravity(Gravity.CENTER);
            TextView symbol=label(symbols[i],18,MUTED);symbol.setGravity(Gravity.CENTER);
            destination.addView(symbol,new LinearLayout.LayoutParams(-1,dp(23)));
            Button b=button(name,()->select(name.equals("Tools")?lastTool:name));b.setTextSize(11);
            b.setPadding(0,0,0,0);b.setBackgroundColor(Color.TRANSPARENT);
            destination.addView(b,new LinearLayout.LayoutParams(-1,dp(32)));
            symbol.setOnClickListener(v->b.performClick());tabs.put(name,b);
            bottomNav.addView(destination,new LinearLayout.LayoutParams(0,dp(58),1));
        }
        root.addView(bottomNav);status.setPadding(dp(12),0,dp(8),0);
        root.addView(status,new LinearLayout.LayoutParams(-1,dp(18)));
        setContentView(root);newTab(null);root.requestFocus();
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
                    runOnUiThread(()->{if(destroyed||!captureEnabled)return;if(!browserTabs.contains(tab))return;if(tab.captures.size()>=200)tab.captures.remove(0);tab.captures.add(capture);if(tab==activeTab&&selected.equals("Network"))renderNetwork();});
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
        consoleOutput.setText(android.text.TextUtils.join("\n\n",logs));renderNetwork();renderTabs();
    }
    private void renderTabs(){
        if(tabCounter!=null)tabCounter.setText(String.valueOf(browserTabs.size()));
        siteTabs.removeAllViews();
        for(BrowserTab tab:browserTabs){
            LinearLayout chip=row();chip.setBackground(box(tab==activeTab?0xff263b57:0xff111a27));
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
    private void browserMenu(){new AlertDialog.Builder(this).setTitle("Browser").setItems(new String[]{"New tab","Local lab","Forward","Desktop site","Open externally","Workspace"},(d,which)->{
        switch(which){case 0:newTab(null);break;case 1:openLab();break;case 2:if(web.canGoForward())web.goForward();break;
        case 3:activeTab.desktop=!activeTab.desktop;String ua=WebSettings.getDefaultUserAgent(this);if(activeTab.desktop)ua=ua.replaceAll("Linux; Android[^)]*","X11; Linux x86_64").replace("; wv","").replace(" Mobile","");web.getSettings().setUserAgentString(ua);web.reload();break;
        case 4:String url=web.getUrl();if(url!=null&&!url.startsWith("https://bb.invalid/")&&(url.startsWith("https://")||url.startsWith("http://")))try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url)));}catch(Exception e){toast("No browser available.");}break;
        case 5:select("Workspace");break;}
    }).show();}
    private void select(String name) {
        selected=name;
        boolean browser=name.equals("Browser");
        boolean tool=tabs.containsKey(name)&&!name.equals("Terminal")&&!name.equals("Workspace")&&!browser;
        if(tool)lastTool=name;
        tabLine.setVisibility(browser&&tabsExpanded?View.VISIBLE:View.GONE);
        root.getChildAt(1).setVisibility(name.equals("Terminal")||name.equals("Workspace")?View.GONE:View.VISIBLE);
        loading.setVisibility(View.INVISIBLE);
        navigationScroll.setVisibility(tool?View.VISIBLE:View.GONE);
        toolHeader.setVisibility(tool?View.VISIBLE:View.GONE);
        boolean split=tool&&splitTools;
        browserBox.setVisibility(browser||split?View.VISIBLE:View.GONE);
        panelHost.setVisibility(browser?View.GONE:View.VISIBLE);
        boolean landscape=getResources().getConfiguration().orientation==android.content.res.Configuration.ORIENTATION_LANDSCAPE;
        if(landscape&&tool){tabLine.setVisibility(View.GONE);root.getChildAt(1).setVisibility(View.GONE);}
        content.setOrientation(landscape?LinearLayout.HORIZONTAL:LinearLayout.VERTICAL);
        browserBox.setLayoutParams(new LinearLayout.LayoutParams(landscape?0:-1,landscape?-1:0,browser?1:0.4f));
        panelHost.setLayoutParams(new LinearLayout.LayoutParams(landscape?0:-1,landscape?-1:0,0.6f));
        panelHost.removeAllViews();View panel=panels.get(name);
        if(panel!=null)panelHost.addView(panel,new LinearLayout.LayoutParams(-1,-1));
        for(Map.Entry<String,Button> entry:tabs.entrySet()){
            boolean active=entry.getKey().equals(name)||(tool&&entry.getKey().equals("Tools"));
            entry.getValue().setTextColor(active?ACCENT:MUTED);
            entry.getValue().setBackgroundColor(active?0xff203049:Color.TRANSPARENT);
        }
        if(name.equals("Network"))renderNetwork();
        if(status!=null)status.setText(name+" · "+(activeTab==null?"":activeTab.title));
    }
    private void navigate() {
        String url=address.getText().toString().trim();if(url.isEmpty())return;if(!url.contains(":")&&(url.contains(" ")||!url.contains(".")))url="https://duckduckgo.com/?q="+Uri.encode(url);else if(!url.contains(":"))url="https://"+url;
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
        toolbar.addView(record);toolbar.addView(button("Clear",()->{captures.clear();renderNetwork();}));
        toolbar.addView(button("Reload",()->{if(web!=null)web.reload();}));
        body.addView(toolbar);

        networkFilter=edit("Filter URLs, methods or hostnames",1);
        networkFilter.setSingleLine(true);networkFilter.setBackground(box(0xff202d41));
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
        if(visible==0)networkRows.addView(label("No matching requests. Reload this page or open the local lab.",13,MUTED));
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
            .setNegativeButton("Close",null).setPositiveButton("To Repeater",(d,w)->{
                requestUrl.setText(capture.url);requestHeaders.setText(headersText(capture.headers,true));
                requestBody.setText("");chooseMethod(capture.method);select("Repeater");
                toast("Only observed metadata copied. Verify headers and enter a body before replay.");
            }).show();
    }
    private void buildRepeater() {
        LinearLayout body=panel("Repeater","Request workbench","Independent HTTP client · verified TLS · no automatic redirects or browser cookie sharing. Responses are limited to 1 MiB.");
        method=new Spinner(this);ArrayAdapter<String> adapter=new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"GET","POST","PUT","DELETE","HEAD","OPTIONS"});method.setAdapter(adapter);body.addView(method);
        requestUrl=edit("Request URL",1);body.addView(requestUrl);requestHeaders=edit("Headers — one Name: value per line",3);body.addView(requestHeaders);requestBody=edit("UTF-8 request body",3);body.addView(requestBody);
        requestHeaders.setTypeface(Typeface.MONOSPACE);requestBody.setTypeface(Typeface.MONOSPACE);
        sendButton=button("Send request",this::sendRequest);
        actions(body,sendButton,button("To Terminal",()->{
            try{commandInput.setText(spec().toCurl());select("Terminal");toast("Prepared only. Review the command, then tap Run.");}catch(Exception e){toast(e.getMessage());}
        }),button("Copy curl",()->{try{String curl=spec().toCurl();confirm("Copy request?","Headers and body may include credentials. The command will be placed on your clipboard.",()->copy(curl));}catch(Exception e){toast(e.getMessage());}}));
        actions(body,button("Compare last two",this::compareResponses),button("Clear response",()->{latestResponse="";previousResponse="";responseOutput.setText("No response yet.");}));
        responseOutput=output("No response yet. Try the local echo endpoint.");body.addView(responseOutput);
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
        LinearLayout body=panel("Console","JavaScript console","Runs in the top-level page. Logs are in memory; max 200 entries. This is not the full Chrome debugger.");
        jsInput=edit("JavaScript expression or statements",2);jsInput.setTypeface(Typeface.MONOSPACE);jsInput.setText("document.title");body.addView(jsInput);
        actions(body,button("Evaluate",this::evaluate),button("Clear logs",()->{logs.clear();consoleOutput.setText("");}),button("Reload page",()->web.reload()));
        consoleOutput=output("Page console messages appear here.");consoleOutput.setContentDescription("Console output");body.addView(consoleOutput);
    }
    private void evaluate(){
        String source=jsInput.getText().toString();if(source.trim().isEmpty())return;final BrowserTab evaluatedTab=activeTab;addLog("> "+source);
        String script="(function(){try{var v=(0,eval)("+JSONObject.quote(source)+");if(v&&typeof v.then==='function')return 'Promise returned; use console.log in .then() to inspect its value';return typeof v==='string'?v:(typeof v==='undefined'?'undefined':JSON.stringify(v,null,2));}catch(e){return e.name+': '+e.message;}})()";
        web.evaluateJavascript(script,value->{try{addTabLog(evaluatedTab,String.valueOf(new JSONTokener(value).nextValue()));}catch(Exception e){addTabLog(evaluatedTab,value);}});
    }
    private void addTabLog(BrowserTab tab,String message){if(message.length()>8000)message=message.substring(0,8000);if(tab.logs.size()>=200)tab.logs.remove(0);tab.logs.add(message);if(tab==activeTab&&consoleOutput!=null)consoleOutput.setText(android.text.TextUtils.join("\n\n",tab.logs));}
    private void addLog(String message){if(message.length()>8000)message=message.substring(0,8000)+"\n[Truncated]";if(logs.size()>=200)logs.remove(0);logs.add(message);if(consoleOutput!=null)consoleOutput.setText(android.text.TextUtils.join("\n\n",logs));}
    private void buildElements(){
        LinearLayout body=panel("Elements","DOM inspector","Read an element snapshot or change an inline style. Top-level document only; changes disappear on reload.");
        EditText selector=edit("CSS selector",1);selector.setText("h1");body.addView(selector);
        EditText property=edit("CSS property",1);property.setText("color");body.addView(property);EditText value=edit("CSS value",1);value.setText("#52e0c4");body.addView(value);
        actions(body,button("Inspect",()->{
            String script="(function(){try{var e=document.querySelector("+JSONObject.quote(selector.getText().toString())+");if(!e)return 'No matching element';return e.outerHTML.slice(0,50000);}catch(e){return e.message;}})()";
            web.evaluateJavascript(script,r->{try{elementsOutput.setText(String.valueOf(new JSONTokener(r).nextValue()));}catch(Exception e){elementsOutput.setText(r);}});
        }),button("Apply style",()->{
            String script="(function(){try{var e=document.querySelector("+JSONObject.quote(selector.getText().toString())+");if(!e)return 'No matching element';e.style.setProperty("+JSONObject.quote(property.getText().toString())+","+JSONObject.quote(value.getText().toString())+");return e.getAttribute('style');}catch(e){return e.message;}})()";
            web.evaluateJavascript(script,r->elementsOutput.setText(r));
        }));elementsOutput=output("Enter a CSS selector, then Inspect.");body.addView(elementsOutput);
    }
    private void buildTerminal(){
        LinearLayout body=column();body.setPadding(dp(12),0,dp(12),dp(4));body.setBackgroundColor(0xff080d15);panels.put("Terminal",body);
        LinearLayout top=row();TextView title=label("›_  Terminal",16,INK);top.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        top.addView(button("New session",this::startLocalShell));top.addView(button("Termux",()->new AlertDialog.Builder(this).setTitle("Termux integration").setItems(new String[]{"Open interactive Termux","Run input in Termux","Connection setup"},(d,n)->{if(n==0)openTermux();else if(n==1)runTermux();else termuxSetup();}).show()));body.addView(top);
        body.addView(label("LOCAL SHELL  ·  Android sandbox  ·  live output",10,ACCENT));
        terminalOutput=output("BB Cyber Studio / local shell\n\nA persistent Android shell in this app's private folder.\nSupports cd, pipes and Android commands.\nNo root, pkg, Python or interactive TUI is bundled.\nUse Termux above for your full Linux environment.\n\nType a command below to start.\n");terminalOutput.setBackgroundColor(0xff080d15);terminalOutput.setTextSize(13);terminalOutput.setPadding(0,dp(12),0,dp(12));
        terminalScroll=new ScrollView(this);terminalScroll.setFillViewport(true);terminalScroll.addView(terminalOutput);body.addView(terminalScroll,new LinearLayout.LayoutParams(-1,0,1));
        actions(body,button("↑",()->terminalHistory(-1)),button("↓",()->terminalHistory(1)),button("/",()->insertCommand("/")),button("-",()->insertCommand("-")),button("|",()->insertCommand("|")),button("Clear",()->terminalOutput.setText("")),button("Stop",()->{if(localShell!=null){localShell.close();localShell=null;appendTerminal("\n[Session stopped]\n");}}));
        LinearLayout input=row();input.addView(label("$",18,ACCENT));commandInput=edit("Enter shell command",1);commandInput.setTypeface(Typeface.MONOSPACE);commandInput.setImeOptions(EditorInfo.IME_ACTION_SEND);
        commandInput.setOnEditorActionListener((v,action,event)->{if(action==EditorInfo.IME_ACTION_SEND){sendLocalCommand();return true;}return false;});
        input.addView(commandInput,new LinearLayout.LayoutParams(0,-2,1));input.addView(button("Run",this::sendLocalCommand));body.addView(input);
    }
    private void appendTerminal(String text){if(destroyed)return;String all=terminalOutput.getText()+text;if(all.length()>100000)all=all.substring(all.length()-100000);terminalOutput.setText(all);terminalScroll.post(()->terminalScroll.fullScroll(View.FOCUS_DOWN));}
    private void startLocalShell(){if(localShell!=null)localShell.close();try{localShell=new LocalShell(getFilesDir(),text->runOnUiThread(()->appendTerminal(text)));appendTerminal("\n[New local session]\n");}catch(Exception e){appendTerminal("\n"+e.getMessage()+"\n");}}
    private void sendLocalCommand(){String command=commandInput.getText().toString();if(command.trim().isEmpty())return;if(command.length()>4096){toast("Local command limit: 4096 characters.");return;}if(localShell==null)startLocalShell();if(localShell==null)return;
        try{localShell.send(command);commandHistory.add(command);if(commandHistory.size()>100)commandHistory.remove(0);historyIndex=commandHistory.size();appendTerminal("$ "+command+"\n");commandInput.setText("");}catch(Exception e){appendTerminal(e.getMessage()+"\n");}
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
    private void termuxSetup(){showText("Connect Termux","1. Install Termux from its official F-Droid or GitHub source.\n\n2. In Termux, add this line to ~/.termux/termux.properties:\n\nallow-external-apps=true\n\nThen run: termux-reload-settings\n\n3. Grant BB Cyber Studio the Run commands in Termux permission when prompted. If unavailable, open Android Settings → Apps → BB Cyber Studio → Permissions → Additional permissions.\n\n4. Tap Run in Termux. Use short, non-interactive commands. Results arrive when the process exits; interactive programs belong in Open Termux for this version.\n\nCommands run with Termux's permissions and can modify its files. No command is run merely by visiting a page or importing a request.");}
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
                intent.putExtra("com.termux.RUN_COMMAND_PATH","$PREFIX/bin/bash");
                intent.putExtra("com.termux.RUN_COMMAND_ARGUMENTS",new String[]{"-lc",command});
                intent.putExtra("com.termux.RUN_COMMAND_WORKDIR","~/");
                intent.putExtra("com.termux.RUN_COMMAND_BACKGROUND",true);intent.putExtra("com.termux.RUN_COMMAND_PENDING_INTENT",pending);
                intent.putExtra("com.termux.RUN_COMMAND_COMMAND_LABEL","BB Cyber Studio");
                android.content.ComponentName started=startService(intent);
                if(started==null)throw new IllegalStateException("Termux service not available");
                terminalOutput.setText("Running… output arrives after completion.\nIf no result arrives, check Setup and Termux permissions.");
            }catch(Exception e){terminalOutput.setText("Could not start Termux: "+e.getMessage()+"\nOpen Setup for connection instructions.");}
        });
    }
    private void buildWorkspace(){
        LinearLayout body=panel("Workspace","Your workspace","Save one named request locally, encrypted with Android Keystore. Console, terminal output and network history are not saved. Browser cookies/storage persist separately.");
        projectName=edit("Workspace name",1);projectName.setText("My workspace");body.addView(projectName);
        actions(body,button("Save request",()->{
            try{RequestSpec request=spec();JSONObject data=new JSONObject();data.put("name",projectName.getText().toString());data.put("method",request.method);data.put("url",request.url);data.put("headers",requestHeaders.getText().toString());data.put("body",request.body);WorkspaceStore.save(this,data.toString());toast("Request saved on this device (encrypted).");}catch(Exception e){toast("Save failed: "+e.getMessage());}
        }),button("Load request",()->{
            try{String content=WorkspaceStore.load(this);if(content==null){toast("No saved workspace.");return;}JSONObject data=new JSONObject(content);projectName.setText(data.getString("name"));chooseMethod(data.getString("method"));requestUrl.setText(data.getString("url"));requestHeaders.setText(data.getString("headers"));requestBody.setText(data.getString("body"));select("Repeater");toast("Request loaded. Review before sending.");}catch(Exception e){toast("Load failed: "+e.getMessage());}
        }));
        actions(body,button("Delete saved request",()->confirm("Delete workspace?","The saved encrypted request will be removed.",()->{try{WorkspaceStore.delete(this);toast("Saved request deleted.");}catch(Exception e){toast(e.getMessage());}})),button("Clear browser data",()->confirm("Clear browser data?","Stops the page, clears cookies, web storage, cache and history, and removes captured request metadata.",()->{
            for(BrowserTab tab:browserTabs){tab.view.stopLoading();tab.view.loadUrl("about:blank");tab.view.clearHistory();tab.captures.clear();tab.logs.clear();}CookieManager.getInstance().removeAllCookies(done->CookieManager.getInstance().flush());WebStorage.getInstance().deleteAllData();web.clearCache(true);web.clearHistory();captures.clear();renderNetwork();logs.clear();consoleOutput.setText("");toast("Browser data cleared.");
        })));
        body.addView(label("WHAT WORKS",12,ACCENT));body.addView(label("Browser · request metadata · JavaScript console · DOM snapshot/style editor · native HTTP Repeater · response comparison · curl export · Termux command results · encrypted request save · offline local lab.",14,INK));
        body.addView(label("NEXT MILESTONES",12,ACCENT));body.addView(label("Full HTTP(S) proxy and Intercept, response/body capture, embedded interactive terminal, isolated browser profiles, HAR import/export, WebSocket inspection. These are not included in 0.2.",14,MUTED));
        body.addView(label("BB Cyber Studio · Between Bytes\nNative Android alpha · no analytics, account or cloud backend.\nRequests go only to destinations you open or send to. Local lab addresses change after restarting the app.",12,MUTED));
        actions(body,button("Source & issues",()->new AlertDialog.Builder(this).setTitle("GitHub").setMessage("Open the public repository in your browser?").setPositiveButton("Open",(d,w)->startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse("https://github.com/khdertaysh1/BB-cyber-studio")))).setNegativeButton("Cancel",null).show()));
    }
    private void copy(String value){((ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("BB Cyber Studio",value));toast("Copied.");}
    private void toast(String message){Toast.makeText(this,message==null?"Unknown error":message,Toast.LENGTH_LONG).show();}
    private void confirm(String title,String message,Runnable accepted){new AlertDialog.Builder(this).setTitle(title).setMessage(message).setNegativeButton("Cancel",null).setPositiveButton("Continue",(d,w)->accepted.run()).show();}
    private void showText(String title,String value){ScrollView scroll=new ScrollView(this);scroll.addView(output(value));new AlertDialog.Builder(this).setTitle(title).setView(scroll).setPositiveButton("Close",null).show();}
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
