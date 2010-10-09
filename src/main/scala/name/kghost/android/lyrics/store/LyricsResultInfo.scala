package name.kghost.android.lyrics.store

abstract class LyricsResultInfo {
  def provider: Int
  def hasTimeline: Boolean
  def artist: String
  def album: String
  def track: String
  def getResult: ILyrics
}
