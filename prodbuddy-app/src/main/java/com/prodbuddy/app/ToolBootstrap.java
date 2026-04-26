package com.prodbuddy.app;

import com.prodbuddy.core.observation.ObservationTool;
import com.prodbuddy.core.system.SystemCatalogTool;
import com.prodbuddy.core.tool.Tool;
import com.prodbuddy.core.tool.ToolRegistry;
import com.prodbuddy.observation.RecordingSequenceLogger;
import com.prodbuddy.observation.Slf4jSequenceLogger;
import com.prodbuddy.orchestrator.RuleBasedToolRouter;
import com.prodbuddy.tools.codecontext.*;
import com.prodbuddy.tools.elasticsearch.ElasticsearchQueryBuilder;
import com.prodbuddy.tools.elasticsearch.ElasticsearchTool;
import com.prodbuddy.tools.http.GenericApiTool;
import com.prodbuddy.tools.http.HttpMethodSupport;
import com.prodbuddy.tools.kubectl.KubectlCommandBuilder;
import com.prodbuddy.tools.kubectl.KubectlTool;
import com.prodbuddy.tools.newrelic.NewRelicScenarioCatalog;
import com.prodbuddy.tools.newrelic.NewRelicTool;
import com.prodbuddy.tools.pdf.LocalOpenDataLoaderPdfAdapter;
import com.prodbuddy.tools.pdf.PdfTool;
import com.prodbuddy.tools.splunk.SplunkOperationGuard;
import com.prodbuddy.tools.splunk.SplunkTool;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public final class ToolBootstrap {

    public ToolRegistry createRegistry() {
        RuleBasedToolRouter router = new RuleBasedToolRouter();

        // 1. Initialize shared logger first so all tools (including SPI ones) use it
        RecordingSequenceLogger sharedLog = new RecordingSequenceLogger(
                new Slf4jSequenceLogger(ToolBootstrap.class));
        com.prodbuddy.observation.ObservationContext.setLogger(sharedLog);

        // 2. Discover simple tools via SPI
        ToolRegistry discovered = ToolRegistry.discover();
        List<Tool> allTools = new ArrayList<>(discovered.all());

        // 3. Manually wire complex tools
        allTools.addAll(createManualTools(sharedLog));

        // 3. System tool requires a circular ref to the registry
        AtomicReference<ToolRegistry> registryRef = new AtomicReference<>();
        Tool system = new SystemCatalogTool(registryRef::get, router);
        allTools.add(system);

        ToolRegistry finalRegistry = new ToolRegistry(allTools);
        registryRef.set(finalRegistry);
        return finalRegistry;
    }

    private List<Tool> createManualTools(RecordingSequenceLogger sharedLog) {
        Tool observation = new ObservationTool(sharedLog);
        Tool pdf = new PdfTool(new LocalOpenDataLoaderPdfAdapter());
        Tool elasticsearch = new ElasticsearchTool(new ElasticsearchQueryBuilder());
        
        Tool newRelic = new NewRelicTool(new NewRelicScenarioCatalog(), sharedLog);
        
        Tool splunk = new SplunkTool(new SplunkOperationGuard());
        Tool http = new GenericApiTool(new HttpMethodSupport());
        Tool kubectl = new KubectlTool(new KubectlCommandBuilder());
        Tool codeContext = new JavaCodeContextTool(
                new JavaProjectSummaryService(),
                new JavaCodeSearchService(),
                new JavaGraphExtractor(),
                new LocalGraphDbService()
        );
        return List.of(observation, pdf, elasticsearch, newRelic, splunk, http, kubectl, codeContext);
    }
}
