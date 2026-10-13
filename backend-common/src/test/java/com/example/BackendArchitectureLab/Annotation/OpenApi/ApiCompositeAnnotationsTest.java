package com.example.BackendArchitectureLab.Annotation.OpenApi;

import com.example.BackendArchitectureLab.Config.ApiBodyArgumentResolver;
import com.example.BackendArchitectureLab.Config.ApiPathArgumentResolver;
import com.example.BackendArchitectureLab.Config.ApiQueryArgumentResolver;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ApiCompositeAnnotationsTest {

    private MockMvc mockMvc;

    @RestController
    static class SampleController {

        @GetMapping("/test/path/{id}")
        public String testPath(@ApiPath(description = "測試ID", format = "uuid") String id) {
            return "ID:" + id;
        }

        @GetMapping("/test/query")
        public String testQuery(
                @ApiQuery(description = "關鍵字", defaultValue = "defaultKey") String keyword,
                @ApiQuery(description = "分頁大小", defaultValue = "20") int size
        ) {
            return "keyword:" + keyword + ",size:" + size;
        }

        @PostMapping("/test/body")
        public String testBody(@ApiBody(description = "測試 Payload") SamplePayload payload) {
            return "name:" + payload.getName();
        }
    }

    @ApiDto(description = "測試 Payload 物件")
    static class SamplePayload {
        @NotBlank
        @ApiField(description = "姓名", example = "Alice", required = true)
        private String name;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        LocalValidatorFactoryBean validatorFactoryBean = new LocalValidatorFactoryBean();
        validatorFactoryBean.afterPropertiesSet();

        mockMvc = MockMvcBuilders.standaloneSetup(new SampleController())
                .setCustomArgumentResolvers(
                        new ApiBodyArgumentResolver(objectMapper, validatorFactoryBean),
                        new ApiPathArgumentResolver(),
                        new ApiQueryArgumentResolver()
                )
                .build();
    }

    @Test
    void testApiPath_BindsPathVariableCorrectly() throws Exception {
        mockMvc.perform(get("/test/path/12345"))
                .andExpect(status().isOk())
                .andExpect(content().string("ID:12345"));
    }

    @Test
    void testApiQuery_BindsRequestParamAndDefaultValues() throws Exception {
        mockMvc.perform(get("/test/query"))
                .andExpect(status().isOk())
                .andExpect(content().string("keyword:defaultKey,size:20"));

        mockMvc.perform(get("/test/query?keyword=customKey&size=50"))
                .andExpect(status().isOk())
                .andExpect(content().string("keyword:customKey,size:50"));
    }

    @Test
    void testApiBody_BindsRequestBodyAndValidates() throws Exception {
        mockMvc.perform(post("/test/body")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Bob\"}"))
                .andExpect(status().isOk())
                .andExpect(content().string("name:Bob"));

        mockMvc.perform(post("/test/body")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest());
    }
}
