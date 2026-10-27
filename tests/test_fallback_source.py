from pathlib import Path
root = Path(__file__).resolve().parents[1]
src = (root/'app/src/main/java/com/duobao/duology/MainActivity.java').read_text()
assert 'confirmManualHandoff()' in src
assert '章节 1' in src and '章节 2' in src
assert 'awaitingSaveForTransition = true;' in src
assert 'onChapterSaveFlushed' in src
assert 'onCanvasActivityDetected' in src
assert 'putBoolean("chapter1_finished", true).commit()' in src
assert src.index('putBoolean("chapter1_finished", true).commit()') < src.index('if (chapters.onEvent(1, DuologyStateMachine.Event.STORY_COMPLETE))')
print('PASS: optional manual continuation, atomic chapter preference, frame smoke logging (source checks)')
