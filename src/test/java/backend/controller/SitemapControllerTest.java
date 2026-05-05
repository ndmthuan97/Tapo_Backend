package backend.controller;

import backend.model.enums.ProductStatus;
import backend.repository.ProductRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;

import static org.mockito.BDDMockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * SitemapController Unit Tests — standaloneSetup.
 * Covers: SEO-001
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SitemapController — Unit Tests")
class SitemapControllerTest {

    @Mock ProductRepository productRepo;

    @InjectMocks SitemapController sitemapController;

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(sitemapController)
                .build();
    }

    // ── GET /sitemap.xml ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /sitemap.xml")
    class GetSitemap {

        @Test
        @DisplayName("SEO-001: sitemap trả về XML hợp lệ, chứa static URLs và chỉ sản phẩm ACTIVE")
        void getSitemap_returnsValidXml_withActiveProductsOnly() throws Exception {
            String prodId1 = UUID.randomUUID().toString();
            String prodId2 = UUID.randomUUID().toString();
            given(productRepo.findIdsByStatus(ProductStatus.ACTIVE))
                    .willReturn(List.of(prodId1, prodId2));

            mockMvc.perform(get("/sitemap.xml"))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_XML))
                    .andExpect(content().string(org.hamcrest.Matchers.containsString("<?xml version=\"1.0\"")))
                    .andExpect(content().string(org.hamcrest.Matchers.containsString("<urlset")))
                    .andExpect(content().string(org.hamcrest.Matchers.containsString("/products/" + prodId1)))
                    .andExpect(content().string(org.hamcrest.Matchers.containsString("/products/" + prodId2)))
                    .andExpect(content().string(org.hamcrest.Matchers.containsString("<changefreq>daily</changefreq>")))
                    .andExpect(content().string(org.hamcrest.Matchers.containsString("<priority>0.8</priority>")));
        }

        @Test
        @DisplayName("SEO-001b: sitemap chứa static pages (home, /products, /blog, /contact)")
        void getSitemap_containsStaticPages() throws Exception {
            given(productRepo.findIdsByStatus(ProductStatus.ACTIVE)).willReturn(List.of());

            mockMvc.perform(get("/sitemap.xml"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(org.hamcrest.Matchers.containsString("/products")))
                    .andExpect(content().string(org.hamcrest.Matchers.containsString("/blog")))
                    .andExpect(content().string(org.hamcrest.Matchers.containsString("/contact")));
        }

        @Test
        @DisplayName("SEO-001c: không có sản phẩm ACTIVE → sitemap chỉ có static pages")
        void getSitemap_noActiveProducts_onlyStaticPages() throws Exception {
            given(productRepo.findIdsByStatus(ProductStatus.ACTIVE)).willReturn(List.of());

            String xml = mockMvc.perform(get("/sitemap.xml"))
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();

            // XML should be well-formed (has opening and closing urlset)
            org.assertj.core.api.Assertions.assertThat(xml)
                    .contains("</urlset>")
                    .doesNotContain("/products/"); // no product URLs
        }
    }
}
