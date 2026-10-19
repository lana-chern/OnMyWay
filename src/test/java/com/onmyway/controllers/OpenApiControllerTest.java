package com.onmyway.controllers;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "omw.jwt.secret=MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIzNDU2Nzg5MDE=")
class OpenApiControllerTest {
    @Autowired
    MockMvc mockMvc;

    @Test
    void exposesOpenApiDocumentWithoutAuthenticationAndDescribesEndpointSecurity() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.info.title").value("OnMyWay API"))
                .andExpect(jsonPath("$.info.version").value("v1"))
                .andExpect(jsonPath("$.paths['/api/places'].get").exists())
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.type").value("http"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
                .andExpect(jsonPath("$.paths['/api/places'].get.security").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/cities'].get.security").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/places/{id}'].get.security").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/places'].post.security[0].bearerAuth").exists())
                .andExpect(jsonPath("$.paths['/api/places/{id}/manage'].get.security[0].bearerAuth").exists())
                .andExpect(jsonPath("$.paths['/api/places/{id}'].put.security[0].bearerAuth").exists())
                .andExpect(jsonPath("$.paths['/api/places/{id}'].delete.security[0].bearerAuth").exists())
                .andExpect(jsonPath("$.paths['/api/places/{id}/submit-for-moderation'].post.security[0].bearerAuth").exists())
                .andExpect(jsonPath("$.paths['/api/auth/me'].get.security[0].bearerAuth").exists())
                .andExpect(jsonPath("$.paths['/api/moderation/places/pending'].get.security[0].bearerAuth").exists())
                .andExpect(jsonPath("$.paths['/api/moderation/places/{id}/publish'].post.security[0].bearerAuth").exists())
                .andExpect(jsonPath("$.paths['/api/moderation/places/{id}/reject'].post.security[0].bearerAuth").exists())
                .andExpect(jsonPath("$.paths['/api/places'].get.parameters[0].name").value("cityId"))
                .andExpect(jsonPath("$.paths['/api/places'].get.parameters[0].description").value("ID of the city whose published places should be returned."))
                .andExpect(jsonPath("$.paths['/api/places'].get.parameters[1].description").value("Optional case-insensitive search text matched against place names."))
                .andExpect(jsonPath("$.paths['/api/places/{id}'].put.parameters[0].description").value("Unique ID of the place to update."))
                .andExpect(jsonPath("$.components.schemas.CreatePlaceRequest.properties.cityId.description").value("ID of the city where the place is located."))
                .andExpect(jsonPath("$.components.schemas.UpdatePlaceRequest.properties.version.description").value("Current version of the place. Must match the version last read by the client."))
                .andExpect(jsonPath("$.components.schemas.RejectPlaceRequest.properties.reason.description").value("Explanation of why the submission was rejected."));
    }

    @Test
    void swaggerUiRedirectsToItsHtmlEntryPointWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/swagger-ui.html"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", org.hamcrest.Matchers.containsString("/swagger-ui/index.html")));
    }

    @Test
    void servesSwaggerUiHtmlWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Swagger UI")));
    }
}
