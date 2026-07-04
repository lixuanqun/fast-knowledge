package com.fast.knowledge.common;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class StringUtilsTest {

    @Test
    void shouldTruncateLongString() {
        assertThat(StringUtils.truncate("hello world", 5)).isEqualTo("hello");
    }

    @Test
    void shouldNotModifyShortString() {
        assertThat(StringUtils.truncate("hi", 10)).isEqualTo("hi");
    }

    @Test
    void shouldReturnExactLength() {
        assertThat(StringUtils.truncate("abc", 3)).isEqualTo("abc");
    }

    @Test
    void shouldReturnEmptyWhenEmpty() {
        assertThat(StringUtils.truncate("", 5)).isEqualTo("");
    }

    @Test
    void shouldReturnNullWhenNull() {
        assertThat(StringUtils.truncate(null, 10)).isNull();
    }
}
