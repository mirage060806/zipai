package com.onrender.zipai.web;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

class PresentationPageControllerTest {
    @Test
    void bothPresentationUrlsResolveWithoutDatabaseDependencies() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(new PresentationPageController()).build();
        for (String url : new String[]{"/presentation", "/presentation/"}) {
            mvc.perform(get(url))
                .andExpect(status().isOk())
                .andExpect(view().name("presentation/index"));
        }
    }
}
