import com.duobao.duology.StoryEndingDetector;

public class TestStoryEndingDetector {
    static int assertions = 0;
    static void yes(boolean cond, String msg) {
        assertions++;
        if (!cond) throw new AssertionError(msg);
    }
    public static void main(String[] args) {
        StoryEndingDetector detector = new StoryEndingDetector();
        yes(!detector.onEpilogueAcknowledged(), "No completion on normal exit");
        detector.onTextDisplayed("获得神器！看样子这是最终章。回主菜单");
        yes(!detector.onEpilogueAcknowledged(), "No completion on ordinary end of chapter");
        detector.onTextDisplayed("——剧终——");
        yes(detector.markersSeen() == 1, "Ending title matched");
        yes(!detector.onEpilogueAcknowledged(), "Do not skip the epilogue");
        detector.onTextDisplayed("虽然制止了敖蛟的阴谋，");
        detector.onTextDisplayed("但是他的野心并没破灭。\n觊觎着神器的敖蛟，");
        yes(detector.markersSeen() == 3, "First epilogue lines matched in order");
        yes(!detector.onEpilogueAcknowledged(), "No early switch");
        detector.onTextDisplayed("如今逃往了妖之国，");
        detector.onTextDisplayed("又将会引发什么变故……");
        yes(detector.awaitingAcknowledgement(), "Final epilogue detected");
        yes(detector.onEpilogueAcknowledged(), "Auto handoff requested on last acknowledge");
        yes(!detector.onEpilogueAcknowledged(), "Exactly once handoff");
        detector.reset();
        detector.onTextDisplayed("如今逃往了妖之国，又将会引发什么变故");
        detector.onTextDisplayed("虽然制止了敖蛟的阴谋，但是他的野心并没破灭");
        yes(!detector.onEpilogueAcknowledged(), "Out-of-order phrases should not match");
        detector.reset();
        detector.onTextDisplayed("——剧终——虽然制止了敖蛟的阴谋，但是他的野心并没破灭。觊觎着神器的敖蛟，如今逃往了妖之国，又将会引发什么变故……");
        yes(detector.onEpilogueAcknowledged(), "Can match a single rendered block");
        System.out.println("PASS: " + assertions + " story-ending assertions");
    }
}
