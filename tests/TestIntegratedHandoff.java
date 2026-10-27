import com.duobao.duology.DuologyStateMachine;
import com.duobao.duology.StoryEndingDetector;

public class TestIntegratedHandoff {
    public static void main(String[] args) {
        DuologyStateMachine chapters = new DuologyStateMachine();
        StoryEndingDetector ending = new StoryEndingDetector();
        assert chapters.chapter() == 1;
        assert !chapters.onEvent(1, DuologyStateMachine.Event.GAME_EXIT);
        assert !ending.onEpilogueAcknowledged();
        ending.onTextDisplayed("——剧终——");
        ending.onTextDisplayed("虽然制止了敖蛟的阴谋，但是他的野心并没破灭。");
        assert !ending.onEpilogueAcknowledged();
        ending.onTextDisplayed("觊觎着神器的敖蛟，如今逃往了妖之国，又将会引发什么变故……");
        assert ending.onEpilogueAcknowledged();
        assert chapters.onEvent(1, DuologyStateMachine.Event.STORY_COMPLETE);
        assert chapters.chapter() == 2;
        assert !chapters.onEvent(1, DuologyStateMachine.Event.STORY_COMPLETE);
        System.out.println("PASS: integrated handoff state simulation");
    }
}
