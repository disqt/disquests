package com.disqt.disquests.client;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class UrlPolicyTest {

  @Test
  void httpAndHttpsAreOpenable() {
    assertTrue(UrlPolicy.isOpenable("https://disqt.com/minecraft/map#x"));
    assertTrue(UrlPolicy.isOpenable("http://example.com/a?b=c"));
  }

  @Test
  void bareProgramNamesAreNot() {
    assertFalse(UrlPolicy.isOpenable("calc.exe"));
    assertFalse(UrlPolicy.isOpenable("\\\\host\\share\\a.exe"));
  }

  @Test
  void otherSchemesAreNot() {
    assertFalse(UrlPolicy.isOpenable("file:///C:/Windows/notepad.exe"));
    assertFalse(UrlPolicy.isOpenable("search-ms:query=x"));
    assertFalse(UrlPolicy.isOpenable("javascript:alert(1)"));
  }

  @Test
  void nullEmptyAndMalformedAreNot() {
    assertFalse(UrlPolicy.isOpenable(null));
    assertFalse(UrlPolicy.isOpenable(""));
    assertFalse(UrlPolicy.isOpenable("https://x/a b"));
  }
}
