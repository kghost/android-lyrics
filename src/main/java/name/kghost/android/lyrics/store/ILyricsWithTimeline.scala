package name.kghost.android.lyrics.store

abstract class ILyricsWithTimeline extends ILyrics {
  def timeline: Seq[(Int, Int, String)]
}
