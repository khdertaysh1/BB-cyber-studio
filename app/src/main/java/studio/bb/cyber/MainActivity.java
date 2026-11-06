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
    private static final int BG=0xff0b1220, CARD=0xff142137, INK=0xffe5edf8, MUTED=0xff9eafc4, ACCENT=0xff52e0c4;
    private static final String TERMUX_PERMISSION="com.termux.permission.RUN_COMMAND";
    private LinearLayout root, content, browserBox, panelHost, networkRows, nav;
    private HorizontalScrollView navigationScroll;
    private WebView web;
    private TextView status, consoleOutput, responseOutput, terminalOutput, elementsOutput;
    private EditText address, jsInput, requestUrl, requestHeaders, requestBody, commandInput, projectName, networkFilter;
    private Spinner method;
    private Button sendButton;
    private final Map<String,View> panels=new LinkedHashMap<>();
    private final Map<String,Button> tabs=new LinkedHashMap<>();
    private final ArrayList<Capture> captures=new ArrayList<>();
    private final ArrayList<String> logs=new ArrayList<>();
    private final ExecutorService http=Executors.newSingleThreadExecutor();
    private String selected="Browser", latestResponse="", previousResponse="";
    private boolean captureEnabled=true, destroyed=false;
    private LabServer lab;
    private int pendingId=100;
    private ValueCallback<Uri[]> fileCallback;
    private boolean desktopMode=false;

    private static final class Capture {
        final String url, method; final Map<String,String> headers;
        Capture(WebResourceRequest r) { url=r.getUrl().toString(); method=r.getMethod(); headers=new LinkedHashMap<>(r.getRequestHeaders()); }
    }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        buildUi();
        TermuxResultReceiver.listener=result->runOnUiThread(()->{if(!destroyed)terminalOutput.setText(result);});
        terminalOutput.setText(TermuxResultReceiver.lastResult);
        http.execute(()->{try {
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();
            try(InputStream input=getAssets().open("lab.html")) { byte[] buffer=new byte[4096]; int n; while((n=input.read(buffer))!=-1)bytes.write(buffer,0,n); }
            LabServer created=new LabServer(bytes.toByteArray());
            runOnUiThread(()->{if(destroyed){try{created.close();}catch(Exception ignored){}return;}lab=created;
                web.loadUrl(lab.url());address.setText(lab.url());requestUrl.setText(lab.url()+"echo?from=repeater");});
        } catch(Exception e) { runOnUiThread(()->{if(!destroyed)toast("Local lab: "+e.getMessage());}); }});
        select("Browser");
    }
    private int dp(int value) { return Math.round(value*getResources().getDisplayMetrics().density); }
    private LinearLayout column() { LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.VERTICAL);return v; }
    private LinearLayout row() { LinearLayout v=new LinearLayout(this);v.setGravity(Gravity.CENTER_VERTICAL);return v; }
    private GradientDrawable box(int color) { GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(10));d.setStroke(dp(1),0xff263850);return d; }
    private TextView label(String value,int size,int color) {
        TextView v=new TextView(this);v.setText(value);v.setTextSize(size);v.setTextColor(color);v.setPadding(dp(4),dp(5),dp(4),dp(5));return v;
    }
    private TextView output(String initial) { TextView v=label(initial,12,INK);v.setTypeface(Typeface.MONOSPACE);v.setTextIsSelectable(true);v.setPadding(dp(12),dp(12),dp(12),dp(12));v.setBackground(box(BG));return v; }
    private EditText edit(String hint,int lines) {
        EditText v=new EditText(this);v.setTextColor(INK);v.setHintTextColor(MUTED);v.setTextSize(13);v.setHint(hint);v.setContentDescription(hint);
        v.setPadding(dp(12),dp(10),dp(12),dp(10));v.setBackground(box(BG));v.setMinLines(lines);v.setGravity(Gravity.TOP);
        v.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS|(lines>1?InputType.TYPE_TEXT_FLAG_MULTI_LINE:0));
        if(lines==1)v.setSingleLine(true);
        v.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,dp(5),0,dp(5));v.setLayoutParams(lp);return v;
    }
    private Button button(String title,Runnable action) {
        Button b=new Button(this);b.setText(title);b.setAllCaps(false);b.setTextColor(ACCENT);b.setTextSize(12);b.setMinHeight(dp(48));b.setMinimumWidth(0);
        b.setBackground(box(CARD)); b.setOnClickListener(v->action.run());
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,dp(48));lp.setMargins(dp(2),dp(3),dp(2),dp(3));b.setLayoutParams(lp);return b;
    }
    private void actions(LinearLayout parent,Button...buttons) {
        HorizontalScrollView scroller=new HorizontalScrollView(this);scroller.setHorizontalScrollBarEnabled(false);
        LinearLayout line=row();for(Button b:buttons)line.addView(b);scroller.addView(line);parent.addView(scroller);
    }
    private LinearLayout panel(String name,String title,String caption) {
        LinearLayout body=column();body.setPadding(dp(12),dp(8),dp(12),dp(18));
        TextView heading=label(title,22,INK);heading.setTypeface(null,Typeface.BOLD);body.addView(heading);body.addView(label(caption,12,MUTED));
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.addView(body);panels.put(name,scroll);return body;
    }
    @SuppressLint("SetJavaScriptEnabled") private void buildUi() {
        root=column();root.setBackgroundColor(BG);root.setFocusableInTouchMode(true);
        root.setOnApplyWindowInsetsListener((v,insets)->{
            v.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());return insets;
        });
        LinearLayout brand=row();brand.setPadding(dp(12),dp(4),dp(8),dp(2));
        TextView logo=label("BB",24,ACCENT);logo.setTypeface(null,Typeface.BOLD);brand.addView(logo);
        LinearLayout title=column();title.addView(label("CYBER STUDIO",15,INK));title.addView(label("WORKSPACE  /  0.1 ALPHA",9,MUTED));brand.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        brand.addView(button("Local lab",()->{if(lab!=null){web.loadUrl(lab.url());select("Browser");}}));root.addView(brand);
        nav=row();navigationScroll=new HorizontalScrollView(this);navigationScroll.setHorizontalScrollBarEnabled(false);navigationScroll.addView(nav);
        for(String name:new String[]{"Browser","Network","Repeater","Console","Elements","Analyze","Terminal","Workspace"}) {
            Button tab=button(name,()->select(name));tabs.put(name,tab);nav.addView(tab);
        }
        root.addView(navigationScroll);
        content=column();root.addView(content,new LinearLayout.LayoutParams(-1,0,1));
        browserBox=column();LinearLayout urlBar=row();urlBar.setPadding(dp(8),dp(4),dp(8),dp(4));
        urlBar.addView(button("‹",()->{if(web.canGoBack())web.goBack();}));
        address=edit("https://example.com",1);address.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI);address.setImeOptions(EditorInfo.IME_ACTION_GO);
        address.setOnEditorActionListener((v,action,event)->{if(action==EditorInfo.IME_ACTION_GO){navigate();return true;}return false;});
        urlBar.addView(address,new LinearLayout.LayoutParams(0,-2,1));urlBar.addView(button("Go",this::navigate));browserBox.addView(urlBar);
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
                if(!"http".equalsIgnoreCase(scheme)&&!"https".equalsIgnoreCase(scheme)){toast("Blocked navigation scheme: "+scheme);return true;}return false;
            }
            @Override public void onPageStarted(WebView view,String url,android.graphics.Bitmap favicon){address.setText(url);status.setText("Loading · "+url);}
            @Override public void onPageFinished(WebView view,String url){status.setText("Browser · "+captures.size()+" observed requests");}
            @Override public WebResourceResponse shouldInterceptRequest(WebView view,WebResourceRequest request){
                String scheme=request.getUrl().getScheme();
                if("http".equalsIgnoreCase(scheme)||"https".equalsIgnoreCase(scheme)) {
                    Capture capture=new Capture(request);
                    runOnUiThread(()->{if(destroyed||!captureEnabled)return;if(captures.size()>=200)captures.remove(0);captures.add(capture);if(selected.equals("Network"))renderNetwork();});
                }
                return null;
            }
            @Override public void onReceivedError(WebView view,WebResourceRequest request,WebResourceError error){
                if(request.isForMainFrame())status.setText("Navigation error · "+error.getDescription());
            }
            @Override public void onReceivedSslError(WebView view,android.webkit.SslErrorHandler handler,android.net.http.SslError error){handler.cancel();status.setText("TLS validation failed. Connection blocked.");}
        });
        web.setWebChromeClient(new WebChromeClient(){
            @Override public boolean onConsoleMessage(ConsoleMessage message){addLog(message.messageLevel()+"  "+message.message()+"\n"+message.sourceId()+":"+message.lineNumber());return true;}
            @Override public void onProgressChanged(WebView view,int progress){if(progress<100)status.setText("Loading "+progress+"%");}
            @Override public boolean onShowFileChooser(WebView view,ValueCallback<Uri[]> callback,FileChooserParams params){
                if(fileCallback!=null)fileCallback.onReceiveValue(null);fileCallback=callback;
                try{startActivityForResult(params.createIntent(),51);}catch(Exception e){fileCallback.onReceiveValue(null);fileCallback=null;toast("No file picker is available.");}return true;
            }
        });
        actions(browserBox,button("Reload",()->web.reload()),button("Forward",()->{if(web.canGoForward())web.goForward();}),button("Desktop",()->{
            desktopMode=!desktopMode;String ua=WebSettings.getDefaultUserAgent(this);
            if(desktopMode)ua=ua.replaceAll("Linux; Android[^)]*","X11; Linux x86_64").replace("; wv","").replaceAll(" Version/[0-9.]+","").replace(" Mobile","");
            web.getSettings().setUserAgentString(ua);web.reload();toast(desktopMode?"Desktop user agent enabled.":"Mobile user agent enabled.");
        }),button("Analyze page",()->select("Analyze")),button("External browser",()->{
            String url=web.getUrl();if(url!=null&&(url.startsWith("https://")||url.startsWith("http://")))try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url)));}catch(Exception e){toast("No external browser available.");}
        }));
        browserBox.addView(web,new LinearLayout.LayoutParams(-1,0,1));content.addView(browserBox,new LinearLayout.LayoutParams(-1,0,1));
        panelHost=column();content.addView(panelHost,new LinearLayout.LayoutParams(-1,0,1));
        buildNetwork();buildRepeater();buildConsole();buildElements();buildAnalyze();buildTerminal();buildWorkspace();
        status=label("Ready · local workspace",10,MUTED);status.setMaxLines(1);root.addView(status);setContentView(root);root.requestFocus();
    }
    private void select(String name) {
        selected=name;boolean browser=name.equals("Browser"),split=name.equals("Console")||name.equals("Elements");
        browserBox.setVisibility(browser||split?View.VISIBLE:View.GONE);panelHost.setVisibility(browser?View.GONE:View.VISIBLE);
        boolean landscape=getResources().getConfiguration().orientation==android.content.res.Configuration.ORIENTATION_LANDSCAPE;
        content.setOrientation(landscape?LinearLayout.HORIZONTAL:LinearLayout.VERTICAL);
        browserBox.setLayoutParams(new LinearLayout.LayoutParams(landscape?0:-1,landscape?-1:0,browser?1:0.42f));
        panelHost.setLayoutParams(new LinearLayout.LayoutParams(landscape?0:-1,landscape?-1:0,0.58f));
        panelHost.removeAllViews();View panel=panels.get(name);if(panel!=null)panelHost.addView(panel,new LinearLayout.LayoutParams(-1,-1));
        for(Map.Entry<String,Button> entry:tabs.entrySet()){boolean active=entry.getKey().equals(name);entry.getValue().setTextColor(active?BG:MUTED);entry.getValue().setBackground(box(active?ACCENT:CARD));}
        navigationScroll.post(()->{Button active=tabs.get(selected);if(active!=null)navigationScroll.smoothScrollTo(Math.max(0,active.getLeft()-dp(80)),0);});
        if(name.equals("Network"))renderNetwork();
        if(status!=null)status.setText(name+" · BB Cyber Studio");
    }
    private void navigate() {
        String url=address.getText().toString().trim();if(!url.contains(":"))url="https://"+url;
        try { // Fragments are valid for browsing, unlike native request replay.
            Uri parsed=Uri.parse(url);String scheme=parsed.getScheme();
            if((!"https".equalsIgnoreCase(scheme)&&!"http".equalsIgnoreCase(scheme))||parsed.getHost()==null)throw new IllegalArgumentException("Enter an HTTP(S) address.");
            web.loadUrl(url);
            ((android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(address.getWindowToken(),0);
        }catch(Exception e){toast(e.getMessage());}
    }
    private void buildNetwork() {
        LinearLayout body=panel("Network","Network activity","Browser request metadata · last 200 entries. This alpha does not capture response bodies, POST bodies, every redirect, service worker request or WebSocket frame.");
        networkFilter=edit("Filter by URL or method",1);body.addView(networkFilter);
        networkFilter.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int a){}public void onTextChanged(CharSequence s,int st,int before,int count){renderNetwork();}public void afterTextChanged(android.text.Editable e){}});
        Button pause=button("Pause capture",()->{});pause.setOnClickListener(v->{captureEnabled=!captureEnabled;pause.setText(captureEnabled?"Pause capture":"Resume capture");});
        actions(body,pause,button("Clear",()->{captures.clear();renderNetwork();}));
        networkRows=column();body.addView(networkRows);
    }
    private void renderNetwork() {
        if(networkRows==null)return;networkRows.removeAllViews();String filter=networkFilter.getText().toString().toLowerCase(java.util.Locale.ROOT);
        int visible=0;
        for(int i=captures.size()-1;i>=0;i--) {
            Capture capture=captures.get(i);if(!(capture.method+" "+capture.url).toLowerCase(java.util.Locale.ROOT).contains(filter))continue;
            TextView item=label(capture.method+"  ·  "+Uri.parse(capture.url).getHost()+"\n"+capture.url,12,INK);item.setPadding(dp(12),dp(12),dp(12),dp(12));item.setBackground(box(CARD));
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,dp(4),0,dp(4));item.setLayoutParams(lp);item.setOnClickListener(v->captureDetails(capture));networkRows.addView(item);visible++;
        }
        if(visible==0)networkRows.addView(label("No matching requests. Open Local lab and tap Send GET.",14,MUTED));
    }
    private String headersText(Map<String,String> headers,boolean replay) {
        StringBuilder text=new StringBuilder();for(Map.Entry<String,String> h:headers.entrySet()){
            if(replay&&h.getKey().matches("(?i)host|content-length|transfer-encoding|connection|expect|upgrade"))continue;
            text.append(h.getKey()).append(": ").append(h.getValue()).append('\n');
        }return text.toString();
    }
    private void captureDetails(Capture capture) {
        TextView details=output(capture.method+" "+capture.url+"\n\n"+headersText(capture.headers,false)+"\nRequest body: unavailable in this alpha.");
        ScrollView scroll=new ScrollView(this);scroll.addView(details);
        new AlertDialog.Builder(this).setTitle("Observed request").setView(scroll).setNegativeButton("Close",null)
            .setPositiveButton("To Repeater",(d,w)->{
                requestUrl.setText(capture.url);requestHeaders.setText(headersText(capture.headers,true));requestBody.setText("");
                chooseMethod(capture.method);select("Repeater");
                toast("Metadata copied. Body and browser cookies may be missing; review before sending.");
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
        consoleOutput=output("Page console messages appear here.");body.addView(consoleOutput);
    }
    private void evaluate(){
        String source=jsInput.getText().toString();if(source.trim().isEmpty())return;addLog("> "+source);
        String script="(function(){try{var v=(0,eval)("+JSONObject.quote(source)+");if(v&&typeof v.then==='function')return 'Promise returned; use console.log in .then() to inspect its value';return typeof v==='string'?v:(typeof v==='undefined'?'undefined':JSON.stringify(v,null,2));}catch(e){return e.name+': '+e.message;}})()";
        web.evaluateJavascript(script,value->{try{addLog(String.valueOf(new JSONTokener(value).nextValue()));}catch(Exception e){addLog(value);}});
    }
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
        LinearLayout body=panel("Terminal","Termux commands","Real commands via the installed Termux app. This alpha is a command runner, not an embedded interactive PTY. Output appears when the command finishes.");
        commandInput=edit("Shell command to run in Termux",4);commandInput.setTypeface(Typeface.MONOSPACE);commandInput.setText("pwd && python --version");body.addView(commandInput);
        actions(body,button("Run in Termux",this::runTermux),button("Open Termux",this::openTermux),button("Setup",this::termuxSetup));
        actions(body,button("Clear output",()->{TermuxResultReceiver.lastResult="";terminalOutput.setText("");}));terminalOutput=output("No command result yet.");body.addView(terminalOutput);
    }
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
            web.stopLoading();web.loadUrl("about:blank");CookieManager.getInstance().removeAllCookies(done->CookieManager.getInstance().flush());WebStorage.getInstance().deleteAllData();web.clearCache(true);web.clearHistory();captures.clear();renderNetwork();logs.clear();consoleOutput.setText("");toast("Browser data cleared.");
        })));
        body.addView(label("WHAT WORKS",12,ACCENT));body.addView(label("Browser · request metadata · JavaScript console · DOM snapshot/style editor · native HTTP Repeater · response comparison · curl export · Termux command results · encrypted request save · offline local lab.",14,INK));
        body.addView(label("NEXT MILESTONES",12,ACCENT));body.addView(label("Full HTTP(S) proxy and Intercept, response/body capture, embedded interactive terminal, browser tabs and isolated profiles, HAR import/export, WebSocket inspection. These are not included in 0.1.",14,MUTED));
        body.addView(label("BB Cyber Studio · Between Bytes\nNative Android alpha · no analytics, account or cloud backend.\nRequests go only to destinations you open or send to. Local lab addresses change after restarting the app.",12,MUTED));
        actions(body,button("Source & issues",()->new AlertDialog.Builder(this).setTitle("GitHub").setMessage("Open the public repository in your browser?").setPositiveButton("Open",(d,w)->startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse("https://github.com/khdertaysh1/BB-cyber-studio")))).setNegativeButton("Cancel",null).show()));
    }
    private void copy(String value){((ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("BB Cyber Studio",value));toast("Copied.");}
    private void toast(String message){Toast.makeText(this,message==null?"Unknown error":message,Toast.LENGTH_LONG).show();}
    private void confirm(String title,String message,Runnable accepted){new AlertDialog.Builder(this).setTitle(title).setMessage(message).setNegativeButton("Cancel",null).setPositiveButton("Continue",(d,w)->accepted.run()).show();}
    private void showText(String title,String value){ScrollView scroll=new ScrollView(this);scroll.addView(output(value));new AlertDialog.Builder(this).setTitle(title).setView(scroll).setPositiveButton("Close",null).show();}
    @Override public void onBackPressed(){if(!selected.equals("Browser")){select("Browser");return;}if(web.canGoBack())web.goBack();else super.onBackPressed();}
    @Override public void onConfigurationChanged(android.content.res.Configuration configuration){super.onConfigurationChanged(configuration);select(selected);}
    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){
        super.onActivityResult(requestCode,resultCode,data);
        if(requestCode==51&&fileCallback!=null){Uri[] uris=WebChromeClient.FileChooserParams.parseResult(resultCode,data);
            if(uris!=null)for(Uri uri:uris)if(!"content".equals(uri.getScheme())){uris=null;break;}
            fileCallback.onReceiveValue(uris);fileCallback=null;
        }
    }
    @Override protected void onDestroy(){destroyed=true;TermuxResultReceiver.listener=null;if(fileCallback!=null){fileCallback.onReceiveValue(null);fileCallback=null;}http.shutdownNow();if(lab!=null)try{lab.close();}catch(Exception ignored){}web.stopLoading();web.destroy();super.onDestroy();}
}
