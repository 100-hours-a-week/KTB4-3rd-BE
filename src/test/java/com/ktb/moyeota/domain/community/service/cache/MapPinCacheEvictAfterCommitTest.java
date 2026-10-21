package com.ktb.moyeota.domain.community.service.cache;

import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.ktb.moyeota.domain.community.event.MapPinChangedEvent;
import com.ktb.moyeota.domain.community.service.grid.MapGridConfig;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

@SpringJUnitConfig(MapPinCacheEvictAfterCommitTest.Config.class)
class MapPinCacheEvictAfterCommitTest {

    private static final MapPinChangedEvent GANGNAM_PIN =
            new MapPinChangedEvent(new BigDecimal("37.497942"), new BigDecimal("127.027621"));

    @MockitoBean
    private MapPinCellCache mapPinCellCache;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private TransactionTemplate transactionTemplate;

    @BeforeEach
    void setUp() {
        transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Test
    @DisplayName("커밋 전에는 지우지 않고, 커밋이 끝난 뒤 칸을 지운다")
    void evictsAfterCommit() {
        transactionTemplate.executeWithoutResult(status -> {
            eventPublisher.publishEvent(GANGNAM_PIN);
            verify(mapPinCellCache, never()).evictCells(anyCollection());
        });

        verify(mapPinCellCache).evictCells(anyCollection());
    }

    @Test
    @DisplayName("롤백되면 칸을 지우지 않는다")
    void keepsCacheOnRollback() {
        transactionTemplate.executeWithoutResult(status -> {
            eventPublisher.publishEvent(GANGNAM_PIN);
            status.setRollbackOnly();
        });

        verify(mapPinCellCache, never()).evictCells(anyCollection());
    }

    @Configuration
    @EnableTransactionManagement
    @Import({MapPinCacheEvictor.class, MapGridConfig.class})
    static class Config {

        @Bean
        PlatformTransactionManager transactionManager() {
            return new NoOpTransactionManager();
        }
    }

    static class NoOpTransactionManager extends AbstractPlatformTransactionManager {

        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
        }
    }
}
