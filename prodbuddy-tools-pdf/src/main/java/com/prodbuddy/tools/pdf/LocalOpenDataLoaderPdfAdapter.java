package com.prodbuddy.tools.pdf;

import java.awt.Color;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.text.PDFTextStripper;

public final class LocalOpenDataLoaderPdfAdapter implements OpenDataLoaderPdfAdapter {

    @Override
    public Map<String, Object> extract(Path filePath) {
        if (!Files.exists(filePath)) {
            return Map.of("exists", false, "error", "File not found");
        }
        try (PDDocument document = Loader.loadPDF(filePath.toFile())) {
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(document);
            return Map.of(
                    "exists", true,
                    "content", text,
                    "pages", document.getNumberOfPages()
            );
        } catch (IOException e) {
            return Map.of("exists", true, "error", e.getMessage());
        }
    }

    @Override
    public Map<String, Object> create(Path filePath, String content) {
        try {
            if (Files.exists(filePath)) {
                Files.delete(filePath);
            }
            try (PDDocument document = new PDDocument()) {
                PDPage page = new PDPage(PDRectangle.A4);
                document.addPage(page);

                try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
                    renderContent(contentStream, content);
                }

                document.save(filePath.toFile());
                return Map.of(
                        "status", "created",
                        "file", filePath.toString(),
                        "size", Files.size(filePath)
                );
            }
        } catch (IOException e) {
            return Map.of("error", e.getMessage());
        }
    }

    private void renderContent(PDPageContentStream cs, String content) throws IOException {
        float y = initFontsPosition(cs);
        String sanitized = content.replace("\r", "").replace("\t", "    ");
        y = renderBody(cs, new PDType1Font(Standard14Fonts.FontName.HELVETICA), sanitized, y);
        cs.endText();
        renderChartIfPresent(cs, sanitized, y);
    }

    private float initFontsPosition(PDPageContentStream cs) throws IOException {
        cs.beginText();
        cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 16);
        cs.setLeading(25f);
        cs.newLineAtOffset(50, 800);
        cs.showText("ProdBuddy Diagnostic Report");
        cs.newLine();
        cs.newLine();
        return 750;
    }

    private void renderChartIfPresent(PDPageContentStream cs, String sanitized, float y) throws IOException {
        if (sanitized.contains("chart_data:")) {
            drawChart(cs, extractChartData(sanitized), y - 20);
        }
    }

    private float renderBody(PDPageContentStream cs, PDType1Font font, String sanitized, float yStart) throws IOException {
        float y = yStart;
        cs.setFont(font, 10);
        cs.setLeading(15f);
        for (String line : sanitized.split("\n")) {
            String safe = line.replaceAll("[^\\x20-\\x7E]", "");
            if (safe.isEmpty()) {
                cs.newLine();
            } else {
                renderIndentedLine(cs, safe);
                cs.newLine();
            }
            y -= 15f;
        }
        return y;
    }

    private void renderIndentedLine(PDPageContentStream cs, String line) throws IOException {
        try {
            cs.showText(line.length() > 95 ? line.substring(0, 95) : line);
        } catch (Exception e) {
            System.err.println("PDF Render Error: " + e.getMessage());
        }
    }

    private List<String> splitLine(String line, int limit) {
        List<String> result = new ArrayList<>();
        int start = 0;
        while (start < line.length()) {
            int end = Math.min(start + limit, line.length());
            result.add(line.substring(start, end));
            start = end;
        }
        return result;
    }

    private void drawChart(PDPageContentStream cs, List<Float> data, float yPosition) throws IOException {
        if (data.isEmpty()) return;
        float chartWidth = 500;
        float chartHeight = 150;
        float y = Math.max(chartHeight + 50, yPosition); // Guard against off-page
        
        drawChartDecoration(cs, y, chartWidth, chartHeight);
        
        float barWidth = Math.max(1.0f, (chartWidth / data.size()) * 0.8f);
        float spacing = (chartWidth / data.size()) * 0.2f;
        float maxVal = data.stream().max(Float::compare).orElse(1f);
        if (maxVal < 1f) maxVal = 1f;

        cs.setNonStrokingColor(Color.BLUE);
        float currentX = 50 + 5;
        for (float val : data) {
            float h = (val / maxVal) * (chartHeight - 30);
            if (val > 0 && h < 2f) h = 2f; // Min height for visibility
            if (h > 0) {
                cs.addRect(currentX, y - chartHeight + 10, barWidth, h);
                cs.fill();
            }
            currentX += barWidth + spacing;
        }
    }

    private void drawChartDecoration(PDPageContentStream cs, float y, float w, float h) throws IOException {
        cs.setNonStrokingColor(new Color(245, 245, 245)); // Very light gray background
        cs.addRect(50, y - h, w, h);
        cs.fill();

        cs.setLineWidth(0.5f);
        cs.setStrokingColor(Color.GRAY);
        cs.addRect(50, y - h, w, h);
        cs.stroke();
        
        cs.beginText();
        cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 8);
        cs.setNonStrokingColor(Color.BLACK);
        cs.newLineAtOffset(55, y - 12);
        cs.showText("NEW RELIC PERFORMANCE TRENDS (5-DAY WINDOW)");
        cs.endText();
    }
    

    private List<Float> extractChartData(String content) {
        List<Float> data = new ArrayList<>();
        int idx = content.indexOf("chart_data:");
        if (idx >= 0) {
            String sub = content.substring(idx + 11).trim();
            String[] parts = sub.split("[,\\s]+");
            for (String p : parts) {
                try {
                    data.add(Float.parseFloat(p));
                } catch (Exception e) {}
            }
        }
        return data;
    }
}
