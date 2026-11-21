package com.livebid.client.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** Quy tắc chữ viết tắt trên ảnh đại diện phải giống trang web ({@code initials} trong web/util.js). */
class UiThemeTest {

    @Test
    void initialsUseFirstLetterOfLastTwoWords() {
        assertEquals("CH", UiTheme.initials("Cao Công Hoè"));
        assertEquals("H", UiTheme.initials("Hoè"));
        assertEquals("ĐH", UiTheme.initials("đặng hùng"));
    }

    @Test
    void initialsSkipPunctuationAndEmptyNames() {
        assertEquals("HD", UiTheme.initials("Cao Công Hoè (Desktop)"));
        assertEquals("?", UiTheme.initials("   "));
        assertEquals("?", UiTheme.initials(null));
        assertEquals("?", UiTheme.initials("123 !!"));
    }
}
