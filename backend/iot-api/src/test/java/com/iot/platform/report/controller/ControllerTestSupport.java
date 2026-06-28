package com.iot.platform.report.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iot.platform.common.GlobalExceptionHandler;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

abstract class ControllerTestSupport {
    protected final ObjectMapper objectMapper = Jackson2ObjectMapperBuilder.json().build();
    protected MockMvc buildMockMvc(Object controller) {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        return MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }
    protected String toJson(Object v) throws Exception { return objectMapper.writeValueAsString(v); }
}
