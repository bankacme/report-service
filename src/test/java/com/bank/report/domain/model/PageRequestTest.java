package com.bank.report.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class PageRequestTest {

    @Test
    void defaultsToTheFirstPageOf20() {
        PageRequest request = PageRequest.of(null, null, 100);

        assertThat(request).isEqualTo(new PageRequest(0, 20));
    }

    @Test
    void rejectsSizesOutOfRange() {
        assertThatThrownBy(() -> PageRequest.of(0, 101, 100)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PageRequest.of(0, 0, 100)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PageRequest.of(-1, 10, 100)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void theOffsetIsPageTimesSize() {
        assertThat(new PageRequest(2, 5).offset()).isEqualTo(10);
    }
}
