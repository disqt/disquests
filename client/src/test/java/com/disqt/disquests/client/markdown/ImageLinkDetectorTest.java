package com.disqt.disquests.client.markdown;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ImageLinkDetectorTest {

  @AfterEach
  void reset() {
    ImageLinkDetector.setAllowHttpForTests(false);
  }

  @Test
  void httpsPngIsImage() {
    assertTrue(ImageLinkDetector.isImageUrl("https://i.imgur.com/a.png"));
  }

  @Test
  void extensionIsCaseInsensitiveAndQueryIgnored() {
    assertTrue(ImageLinkDetector.isImageUrl("https://cdn.discordapp.com/x/a.PNG?ex=1&is=2"));
    assertTrue(ImageLinkDetector.isImageUrl("https://x/y.JpEg"));
  }

  @Test
  void allFiveExtensions() {
    for (String ext : List.of("png", "jpg", "jpeg", "gif", "webp")) {
      assertTrue(ImageLinkDetector.isImageUrl("https://x/a." + ext), ext);
    }
  }

  @Test
  void nonImageExtensionIsNot() {
    assertFalse(ImageLinkDetector.isImageUrl("https://x/a.pdf"));
  }

  @Test
  void extensionInQueryOnlyIsNot() {
    assertFalse(ImageLinkDetector.isImageUrl("https://x/a?f=b.png"));
  }

  @Test
  void httpIsNotUnlessAllowed() {
    assertFalse(ImageLinkDetector.isImageUrl("http://127.0.0.1:8080/a.png"));
    ImageLinkDetector.setAllowHttpForTests(true);
    assertTrue(ImageLinkDetector.isImageUrl("http://127.0.0.1:8080/a.png"));
  }

  @Test
  void malformedUrlIsNot() {
    assertFalse(ImageLinkDetector.isImageUrl("https://x/a b.png"));
    assertFalse(ImageLinkDetector.isImageUrl(""));
    assertFalse(ImageLinkDetector.isImageUrl(null));
  }

  @Test
  void bareLinkRequiresTextEqualToUrl() {
    assertTrue(ImageLinkDetector.isBareImageLink("https://x/a.png", "https://x/a.png"));
    assertFalse(ImageLinkDetector.isBareImageLink("https://x/a.png", "caption"));
  }
}
