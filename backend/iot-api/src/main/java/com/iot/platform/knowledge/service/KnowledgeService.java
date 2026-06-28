package com.iot.platform.knowledge.service;

import com.iot.platform.knowledge.dto.KnowledgeDTO;
import com.iot.platform.knowledge.dto.KnowledgeQuery;
import com.iot.platform.knowledge.vo.KnowledgeVO;

import java.util.Map;

public interface KnowledgeService {
    /** 分页查询 */
    Map<String, Object> page(KnowledgeQuery query);

    /** 详情 */
    KnowledgeVO detail(Long id);

    /** 创建 */
    Long create(KnowledgeDTO dto);

    /** 更新(乐观锁:版本号 +1) */
    void update(KnowledgeDTO dto);

    /** 删除 */
    void delete(Long id);
}