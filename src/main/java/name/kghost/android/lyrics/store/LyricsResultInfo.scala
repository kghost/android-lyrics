package name.kghost.android.lyrics.store

abstract class LyricsResultInfo() {
  def artist: String
  def album: String
  def track: String
  def getResult: ILyrics
}