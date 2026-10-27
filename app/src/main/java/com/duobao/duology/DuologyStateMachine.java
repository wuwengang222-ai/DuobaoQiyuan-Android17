package com.duobao.duology;

/** Pure Java controller; no heuristics based on quitting or selecting a menu. */
public final class DuologyStateMachine {
    public enum Event { GAME_EXIT, APP_BACKGROUND, SAVE_WRITTEN, STORY_COMPLETE }
    private int activeChapter = 1;
    private boolean chapterOneFinished = false;

    public int chapter() { return activeChapter; }
    public boolean chapterOneFinished() { return chapterOneFinished; }
    public void restore(boolean completed) {
        chapterOneFinished = completed;
        activeChapter = completed ? 2 : 1;
    }
    public boolean onEvent(int eventChapter, Event event) {
        // Return true ONLY for an authenticated completion event from the actual game runtime.
        if (eventChapter == 1 && activeChapter == 1 && event == Event.STORY_COMPLETE && !chapterOneFinished) {
            chapterOneFinished = true;
            activeChapter = 2;
            return true;
        }
        return false;
    }
}
