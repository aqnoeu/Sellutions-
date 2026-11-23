package com.aqnoeu.sellutions;

import static org.junit.Assert.*;
import android.webkit.WebView;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.core.app.ActivityScenario;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@RunWith(AndroidJUnit4.class)
public class NavigationTest {
    private String js(ActivityScenario<MainActivity> scenario, String expression) throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<String> value = new AtomicReference<>("null");
        scenario.onActivity(activity -> {
            android.view.ViewGroup root = activity.findViewById(android.R.id.content);
            android.view.View content = root.getChildAt(0);
            if (content instanceof WebView) {
                ((WebView) content).evaluateJavascript(expression, result -> {
                    value.set(result);
                    done.countDown();
                });
            } else {
                done.countDown();
            }
        });
        assertTrue("JavaScript callback timeout", done.await(15, TimeUnit.SECONDS));
        return value.get();
    }

    @Test public void navigationAndTemplateEditor() throws Exception {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            // Wait for the packaged HTML to load.
            String ready = "false";
            for (int i=0;i<30;i++) {
                ready = js(scenario, "!!document.getElementById('tab-home')");
                if ("true".equals(ready)) break;
                Thread.sleep(250);
            }
            assertEquals("true", ready);
            assertEquals("true", js(scenario, "(openTab('catalog'), document.getElementById('tab-catalog').classList.contains('active'))"));
            assertEquals("true", js(scenario, "(openTab('queue'), document.getElementById('tab-queue').classList.contains('active'))"));
            assertEquals("true", js(scenario, "(()=>{const nav=document.querySelector('.bottom'); const r=nav.getBoundingClientRect(); return r.height>=65&&r.bottom<=window.innerHeight+2&&r.top>=0})()"));
            assertEquals("true", js(scenario, "(openTab('groups'), document.getElementById('tab-groups').classList.contains('active'))"));
            assertEquals("true", js(scenario, "(openTab('settings'), document.getElementById('tab-settings').classList.contains('active'))"));
            assertEquals("true", js(scenario, "!!document.getElementById('updateStatus') && !!window.AndroidUpdates"));
            assertEquals("true", js(scenario, "typeof window.AndroidUpdates.check==='function'"));
            assertEquals("true", js(scenario, "!Array.from(document.querySelectorAll('#tab-settings button')).some(b=>b.textContent.includes('Autorizar acesso'))"));
            assertEquals("true", js(scenario, "document.getElementById('appVersion').textContent.includes('0.1.0')"));
            assertEquals("\"Teste de atualização\"", js(scenario, "(sellutionsUpdateStatus('Teste de atualização'), document.getElementById('updateStatus').textContent)"));
            assertEquals("true", js(scenario, "(openTemplateEditor(), !!document.getElementById('templateName'))"));
            assertEquals("true", js(scenario, "document.querySelector('.overlay').classList.contains('open')"));
            assertEquals("true", js(scenario, "(closeSheet(), !document.querySelector('.overlay').classList.contains('open'))"));
        }
    }

    @Test public void brandAssetsLoad() throws Exception {
        try(ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            String result = "false";
            for(int i=0;i<40;i++) {
                result=js(scenario, "Array.from(document.images).filter(x=>x.src.endsWith('.svg')).length===2 && Array.from(document.images).filter(x=>x.src.endsWith('.svg')).every(x=>x.complete&&x.naturalWidth>0)");
                if("true".equals(result))break;
                Thread.sleep(250);
            }
            assertEquals("Os dois SVGs devem aparecer no WebView", "true", result);
        }
    }
}
