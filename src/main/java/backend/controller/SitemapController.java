package backend.controller;

import backend.model.enums.ProductStatus;
import backend.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.beans.factory.annotation.Value;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * SitemapController — B2-F12: SEO product sitemap.
 *
 * <p>Exposes {@code GET /sitemap.xml} for crawlers.
 * Lists all ACTIVE products with lastmod=now, changefreq=daily, priority=0.8.
 * Home, products listing, blog pages are also included as static entries.
 *
 * <p>java-pro: returns raw XML string via ResponseEntity — no JAXB overhead.
 * ProductRepository query is read-only projection (id only) for minimal DB load.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class SitemapController {

    private static final DateTimeFormatter W3C_DATE =
            DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneId.of("UTC"));

    private final ProductRepository productRepo;

    @Value("${app.base-url:https://tapo.store}")
    private String baseUrl;

    @GetMapping(value = "/sitemap.xml", produces = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<String> sitemap() {
        String today = W3C_DATE.format(Instant.now());

        // Static pages
        List<String[]> statics = List.of(
                new String[]{"", "1.0", "daily"},
                new String[]{"/products", "0.9", "daily"},
                new String[]{"/blog", "0.7", "weekly"},
                new String[]{"/contact", "0.5", "monthly"},
                new String[]{"/vouchers", "0.6", "daily"}
        );

        // Active product IDs
        List<String> productIds = productRepo.findIdsByStatus(ProductStatus.ACTIVE);

        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n");

        // Static entries
        for (String[] page : statics) {
            xml.append("  <url>\n");
            xml.append("    <loc>").append(baseUrl).append(page[0]).append("</loc>\n");
            xml.append("    <lastmod>").append(today).append("</lastmod>\n");
            xml.append("    <changefreq>").append(page[2]).append("</changefreq>\n");
            xml.append("    <priority>").append(page[1]).append("</priority>\n");
            xml.append("  </url>\n");
        }

        // Product pages
        for (String id : productIds) {
            xml.append("  <url>\n");
            xml.append("    <loc>").append(baseUrl).append("/products/").append(id).append("</loc>\n");
            xml.append("    <lastmod>").append(today).append("</lastmod>\n");
            xml.append("    <changefreq>daily</changefreq>\n");
            xml.append("    <priority>0.8</priority>\n");
            xml.append("  </url>\n");
        }

        xml.append("</urlset>");

        log.debug("Sitemap generated: {} static + {} product URLs", statics.size(), productIds.size());
        return ResponseEntity.ok(xml.toString());
    }
}
