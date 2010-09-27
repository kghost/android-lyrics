package name.kghost.android.lyrics.store

case class LyricsWithTimeline(artist: String, album: String, track: String, timeline: Seq[(Int, Int, String)]) extends ILyricsWithTimeline {
  override def lyrics: String = {
    val sb = new StringBuilder
    for ((_, _, content: String) <- timeline) sb.append(content).append('\n');
    sb.toString
  }
}
