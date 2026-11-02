package cn.sixnine.filmlog;

public final class MemoTagsTest {
    public static void main(String[] args) {
        MemoTags tags = new MemoTags("Old 人像 · 人像 • ISO 200 • #Portrait • #Night • #Portrait");
        check(tags.freeText.equals("Old 人像 · 人像 • ISO 200"), "plain notes and ISO preserved");
        check(tags.memo(tags.freeText).equals("Old 人像 · 人像 • ISO 200 • #Portrait • #Night"), "canonical keys deduplicate");
        check(!tags.toggle("Portrait") && tags.toggle("Portrait"), "repeat click removes then re-adds");
        check(tags.memo("  Current note  ").equals("Current note • #Night • #Portrait"), "insertion order and free text");
        MemoTags restored = new MemoTags(tags.memo("Current note"));
        check(restored.contains("Night") && restored.contains("Portrait"), "save and restore");
        check(restored.freeText.equals("Current note"), "tags kept out of free editor");
        for (String key : MemoTags.KEYS) {
            MemoTags single = new MemoTags("");
            check(single.toggle(key) && single.memo("").equals("#" + key), "six keys round trip");
            check(!single.toggle(key) && single.memo("").isEmpty(), "second click clears");
        }
        String unknown = "#Unknown • #portrait • photo #Portrait • [Night] • 人像";
        MemoTags legacy = new MemoTags(unknown);
        check(legacy.freeText.equals(unknown) && legacy.memo(legacy.freeText).equals(unknown), "unknown and embedded tags not consumed");
        check(new MemoTags("").memo(" \n ").isEmpty(), "empty note");
        boolean rejected=false;try { tags.toggle("Unknown"); } catch (IllegalArgumentException expected) { rejected=true; }
        check(rejected, "unknown selection rejected");
        System.out.println("MemoTags: all checks passed");
    }
    private static void check(boolean pass, String message) { if (!pass) throw new AssertionError(message); }
}
