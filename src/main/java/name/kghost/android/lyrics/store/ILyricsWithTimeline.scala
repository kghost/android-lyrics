package name.kghost.android.lyrics.store

abstract class ILyricsWithTimeline extends ILyrics {
  def timeline: Seq[(Int, Int, String)]
  override def lyrics: String = {
    val sb = new StringBuilder
    for ((_, _, content: String) <- timeline) sb.append(content).append('\n');
    sb.toString
  }
}
