package studio.bb.cyber;

import org.junit.Test;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import static org.junit.Assert.assertEquals;

public final class NetworkClassifierTest {
    @Test public void scriptAndStylesWithQueryStrings() {
        assertEquals("JS",NetworkClassifier.classify("https://test.invalid/app.min.js?v=9",false,Collections.emptyMap()));
        assertEquals("CSS",NetworkClassifier.classify("https://test.invalid/assets/main.css?cache=1",false,Collections.emptyMap()));
    }
    @Test public void documentAndImageAndFont() {
        assertEquals("Doc",NetworkClassifier.classify("https://test.invalid/",true,Collections.emptyMap()));
        assertEquals("Img",NetworkClassifier.classify("https://test.invalid/logo.svg",false,Collections.emptyMap()));
        assertEquals("Font",NetworkClassifier.classify("https://test.invalid/fonts/site.woff2",false,Collections.emptyMap()));
    }
    @Test public void apiIsHeuristicNotClaimedXhr() {
        assertEquals("API",NetworkClassifier.classify("https://test.invalid/api/users",false,Collections.emptyMap()));
        Map<String,String> headers=new HashMap<>(); headers.put("Accept","application/json, */*");
        assertEquals("API",NetworkClassifier.classify("https://test.invalid/data",false,headers));
        assertEquals("Other",NetworkClassifier.classify("https://test.invalid/faviconless",false,Collections.emptyMap()));
    }
}
