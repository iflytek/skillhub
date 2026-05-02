package com.iflytek.skillhub.search.mysql;

import java.util.List;
import org.junit.jupiter.api.Test;

class MysqlNoopSearchServicesTest {

    @Test
    void noOpIndexServiceShouldAcceptCallsWithoutFailing() {
        MysqlNoopSearchIndexService service = new MysqlNoopSearchIndexService();

        service.index(null);
        service.batchIndex(List.of());
        service.remove(1L);
    }

    @Test
    void noOpRebuildServiceShouldAcceptCallsWithoutFailing() {
        MysqlNoopSearchRebuildService service = new MysqlNoopSearchRebuildService();

        service.rebuildAll();
        service.rebuildByNamespace(1L);
        service.rebuildBySkill(1L);
    }
}
