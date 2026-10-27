import com.duobao.duology.StoryEndingDetector;

/** Synthetic OCR TEXT samples, not camera/image/OCR-engine tests. */
public final class TestStoryEndingOCR {
    private static void eq(boolean value, String description) {
        if (!value) throw new AssertionError(description);
    }
    public static void main(String[] args) {
        StoryEndingDetector d = new StoryEndingDetector();
        d.onTextDisplayed("普通地图\n章节选择\n练功中");
        eq(!d.awaitingAcknowledgement(), "menus do not finish plot");
        d.onTextDisplayed("—— 剧 终 ——\n");
        eq(d.markersSeen() == 1, "OCR drops decoration and inserts spaces");
        d.onTextDisplayed("虽然，制止了敖蛟的阴谋！");
        d.onTextDisplayed("但是他的野心\n并没破灭");
        d.onTextDisplayed("如今逃往了\n妖之国，又将会引发 什么变故……");
        eq(d.awaitingAcknowledgement(), "OCR concatenates lines while preserving order");
        eq(d.onEpilogueAcknowledged(), "first acknowledgment triggers");
        eq(!d.onEpilogueAcknowledged(), "no repeated switch");
        d.reset();
        d.onTextDisplayed("又将会引发什么变故 虽然制止了敖蛟的阴谋");
        eq(d.markersSeen() == 0, "cannot jump from one epilogue fragment");
        d.onTextDisplayed("剧终");
        eq(d.markersSeen() == 1, "single marker not enough");
        eq(!d.onEpilogueAcknowledged(), "only ending title does not switch");
        System.out.println("PASS: 9 OCR-text normalization/state assertions (synthetic text only)");
    }
}
