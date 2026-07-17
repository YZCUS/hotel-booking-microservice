package com.hotel.search;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.EnableScheduling;

import static org.assertj.core.api.Assertions.assertThat;

class SearchServiceApplicationTest {

    @Test
    void enablesScheduledReconciliation() {
        assertThat(SearchServiceApplication.class.isAnnotationPresent(EnableScheduling.class)).isTrue();
    }
}
