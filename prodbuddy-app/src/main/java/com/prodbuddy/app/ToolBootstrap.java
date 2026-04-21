package com.prodbuddy.app;

import com.prodbuddy.tools.splunk.SplunkTool;
import com.prodbuddy.tools.agent.AgentTool;
import com.prodbuddy.tools.json.JsonTool;
import com.prodbuddy.tools.json.JsonAnalyzer;
import com.prodbuddy.tools.git.GitTool;
import com.prodbuddy.core.system.SystemCatalogTool;
import com.prodbuddy.core.tool.Tool;
import com.prodbuddy.core.tool.ToolRegistry;
import com.prodbuddy.orchestrator.RuleBasedToolRouter;
import com.prodbuddy.tools.codecontext.JavaCodeContextTool;
import com.prodbuddy.tools.codecontext.JavaCodeSearchService;
import com.prodbuddy.tools.codecontext.JavaGraphExtractor;
import com.prodbuddy.tools.codecontext.JavaProjectSummaryService;
import com.prodbuddy.tools.codecontext.LocalGraphDbService;
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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public final class ToolBootstrap {

    public ToolRegistry createRegistry() {
        RuleBasedToolRouter router = new RuleBasedToolRouter();

        // 1. Discover simple tools via SPI
        ToolRegistry discovered = ToolRegistry.discover();
        List<Tool> allTools = new ArrayList<>(discovered.all());

        // 2. Manually wire complex tools
        allTools.addAll(createManualTools());

        // 3. System tool requires a circular ref to the registry
        AtomicReference<ToolRegistry> registryRef = new AtomicReference<>();
        Tool system = new SystemCatalogTool(registryRef::get, router);
        allTools.add(system);

        ToolRegistry finalRegistry = new ToolRegistry(allTools);
        registryRef.set(finalRegistry);
        return finalRegistry;
    }

    private List<Tool> createManualTools() {
        Tool pdf = new PdfTool(new LocalOpenDataLoaderPdfAdapter());
        Tool elasticsearch = new ElasticsearchTool(new ElasticsearchQueryBuilder());
        Tool newRelic = new NewRelicTool(new NewRelicScenarioCatalog());
        Tool splunk = new SplunkTool(new SplunkOperationGuard());
        Tool http = new GenericApiTool(new HttpMethodSupport());
        Tool kubectl = new KubectlTool(new KubectlCommandBuilder());
        Tool codeContext = new JavaCodeContextTool(
                new JavaProjectSummaryService(),
                new JavaCodeSearchService(),
                new JavaGraphExtractor(),
                new LocalGraphDbService()
        );
        return List.of(pdf, elasticsearch, newRelic, splunk, http, kubectl, codeContext);
    }
}
