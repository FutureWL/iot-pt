package com.iot.platform.knowledge.service;

import com.iot.platform.knowledge.dto.KnowledgeQuery;
import com.iot.platform.knowledge.entity.KnowledgeBase;
import com.iot.platform.knowledge.mapper.KnowledgeBaseMapper;
import com.iot.platform.knowledge.service.impl.KnowledgeServiceImpl;
import com.baomidou.mybatisplus.core.metadata.IPage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KnowledgeServiceImplTest {

    @Mock
    private KnowledgeBaseMapper mapper;

    @InjectMocks
    private KnowledgeServiceImpl service;

    @Test
    void pageShouldReturnEmptyWhenNoData() {
        IPage<KnowledgeBase> empty = new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(1, 10);
        empty.setRecords(Collections.emptyList());
        empty.setTotal(0);
        when(mapper.selectPage(any(), any())).thenReturn(empty);

        KnowledgeQuery query = new KnowledgeQuery();
        query.setCurrent(1);
        query.setSize(10);
        Map<String, Object> result = service.page(query);

        assertThat(result).isNotNull();
        // MyBatis-Plus Page 总数是 Long 类型
        assertThat(((Number) result.get("total")).longValue()).isEqualTo(0L);
        assertThat(result.get("records")).isInstanceOf(java.util.List.class);
    }

    @Test
    void pageShouldHandleNullParams() {
        IPage<KnowledgeBase> empty = new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(1, 10);
        empty.setRecords(Collections.emptyList());
        empty.setTotal(0);
        when(mapper.selectPage(any(), any())).thenReturn(empty);

        KnowledgeQuery query = new KnowledgeQuery();  // current/size 都为 null
        Map<String, Object> result = service.page(query);

        assertThat(result).isNotNull();
        // 走默认 1/10
        assertThat(((Number) result.get("current")).longValue()).isEqualTo(1L);
        assertThat(((Number) result.get("size")).longValue()).isEqualTo(10L);
    }
}