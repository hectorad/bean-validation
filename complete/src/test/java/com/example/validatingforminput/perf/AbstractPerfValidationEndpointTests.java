package com.example.validatingforminput.perf;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

abstract class AbstractPerfValidationEndpointTests {

    @Autowired
    protected MockMvc mockMvc;

    protected void assertOk(String path, String body) throws Exception {
        mockMvc.perform(post(path).contentType(APPLICATION_JSON).content(body))
            .andExpect(status().isOk());
    }

    protected void assertBadRequest(String path, String body) throws Exception {
        mockMvc.perform(post(path).contentType(APPLICATION_JSON).content(body))
            .andExpect(status().isBadRequest());
    }
}
