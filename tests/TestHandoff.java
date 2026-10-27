import com.duobao.duology.DuologyStateMachine;
public class TestHandoff {
 public static void main(String[] args) {
  DuologyStateMachine s = new DuologyStateMachine();
  require(s.chapter()==1,"initial");
  require(!s.onEvent(1,DuologyStateMachine.Event.GAME_EXIT),"exit must not unlock");
  require(!s.onEvent(1,DuologyStateMachine.Event.SAVE_WRITTEN),"save must not unlock");
  require(!s.onEvent(2,DuologyStateMachine.Event.STORY_COMPLETE),"wrong chapter");
  require(s.chapter()==1,"no early switch");
  require(s.onEvent(1,DuologyStateMachine.Event.STORY_COMPLETE),"true final event must switch");
  require(s.chapter()==2 && s.chapterOneFinished(),"chapter and progress");
  require(!s.onEvent(1,DuologyStateMachine.Event.STORY_COMPLETE),"cannot repeat");
  DuologyStateMachine resumed = new DuologyStateMachine();
  resumed.restore(s.chapterOneFinished());
  require(resumed.chapter()==2,"resume persistence");
  System.out.println("7 handoff tests PASS — logic only, not emulator compatibility or actual plot detection");
 }
 static void require(boolean value, String what) { if (!value) throw new AssertionError(what); }
}
