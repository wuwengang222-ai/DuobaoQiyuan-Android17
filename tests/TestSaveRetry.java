import com.duobao.duology.StoryEndingDetector;
public class TestSaveRetry {
 public static void main(String[] args) {
   StoryEndingDetector d = new StoryEndingDetector();
   for (String v : new String[] {"剧终", "虽然制止了敖蛟的阴谋", "但是他的野心并没破灭", "如今逃往了妖之国", "又将会引发什么变故"}) d.onTextDisplayed(v);
   if (!d.onEpilogueAcknowledged()) throw new AssertionError("first confirmation rejected");
   if (d.onEpilogueAcknowledged()) throw new AssertionError("double handoff accepted");
   d.retryAfterSaveFailure();
   if (!d.onEpilogueAcknowledged()) throw new AssertionError("save failure cannot retry");
   System.out.println("PASS: failed save allows safe epilogue confirmation retry");
 }
}
